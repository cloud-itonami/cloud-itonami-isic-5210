(ns terminal.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [terminal.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "JPN" (:jurisdiction (store/terminal-stock s "tank-1"))))
      (is (= "T-101" (:tank-id (store/terminal-stock s "tank-1"))))
      (is (= 200000 (:volume-barrels (store/terminal-stock s "tank-1"))))
      (is (= "ATL" (:jurisdiction (store/terminal-stock s "tank-2"))))
      (is (false? (:receipt-confirmed? (store/terminal-stock s "tank-3"))) "tank-3 POD-chain broken")
      (is (= 400000 (:planned-receipt-barrels (store/terminal-stock s "tank-4"))) "tank-4 overfill")
      (is (false? (:integrity-assessment-current? (store/terminal-stock s "tank-5"))) "tank-5 integrity stale")
      (is (false? (:bonding-grounding-confirmed? (store/terminal-stock s "tank-6"))) "tank-6 bonding unconfirmed")
      (is (false? (:committed? (store/terminal-stock s "tank-1"))))
      (is (false? (:custody-transferred? (store/terminal-stock s "tank-1"))))
      (is (= ["tank-1" "tank-2" "tank-3" "tank-4" "tank-5" "tank-6"]
             (mapv :id (store/all-terminal-stocks s))))
      (is (nil? (store/assessment-of s "tank-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/act1-history s)))
      (is (= [] (store/act2-history s)))
      (is (zero? (store/next-commit-sequence s "JPN")))
      (is (zero? (store/next-transfer-sequence s "JPN")))
      (is (false? (store/terminal-stock-already-committed? s "tank-1")))
      (is (false? (store/terminal-stock-already-transferred? s "tank-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :tank/upsert
                                 :value {:id "tank-1" :tank-id "T-999"}})
        (is (= "T-999" (:tank-id (store/terminal-stock s "tank-1"))))
        (is (= "JPN" (:jurisdiction (store/terminal-stock s "tank-1"))) "unrelated field preserved"))
      (testing "receipt-assessment payloads commit and read back"
        (store/commit-record! s {:effect :receipt-assessment/set :path ["tank-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "tank-1"))))
      (testing "storage commit drafts a record and advances the commit sequence"
        (store/commit-record! s {:effect :tank/mark-committed :path ["tank-1"]})
        (is (= "JPN-COMMIT-000000" (get (first (store/act1-history s)) "record_id")))
        (is (= "storage-commit-draft" (get (first (store/act1-history s)) "kind")))
        (is (true? (:committed? (store/terminal-stock s "tank-1"))))
        (is (= 1 (count (store/act1-history s))))
        (is (= 1 (store/next-commit-sequence s "JPN")))
        (is (true? (store/terminal-stock-already-committed? s "tank-1"))))
      (testing "custody transfer drafts a record and advances the transfer sequence"
        (store/commit-record! s {:effect :tank/mark-transferred :path ["tank-1"]})
        (is (= "JPN-TRANSFER-000000" (get (first (store/act2-history s)) "record_id")))
        (is (= "custody-transfer-draft" (get (first (store/act2-history s)) "kind")))
        (is (true? (:custody-transferred? (store/terminal-stock s "tank-1"))))
        (is (= 1 (count (store/act2-history s))))
        (is (= 1 (store/next-transfer-sequence s "JPN")))
        (is (true? (store/terminal-stock-already-transferred? s "tank-1"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/terminal-stock s "nope")))
    (is (= [] (store/all-terminal-stocks s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/act1-history s)))
    (is (= [] (store/act2-history s)))
    (is (zero? (store/next-commit-sequence s "JPN")))
    (is (zero? (store/next-transfer-sequence s "JPN")))
    (store/with-terminal-stocks s {"x" {:id "x" :tank-id "T-X" :product-grade "crude-light"
                                        :volume-barrels 200000 :receipt-confirmed? true
                                        :tank-level-pct 40.0 :ullage-barrels 300000
                                        :planned-receipt-barrels 100000
                                        :integrity-assessment-current? true :gauge-verified? true
                                        :bonding-grounding-confirmed? true
                                        :committed? false :custody-transferred? false
                                        :jurisdiction "JPN" :status :intake}})
    (is (= "T-X" (:tank-id (store/terminal-stock s "x"))))))
