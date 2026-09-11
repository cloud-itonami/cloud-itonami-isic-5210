(ns terminal.governor-contract-test
  "The governor contract as executable tests. The single invariant
  under test:

    TerminalAdvisor never commits storage or transfers custody of a
    tank the Terminal Storage Governor would reject, `:storage/commit`/
    `:custody/transfer` NEVER auto-commit at any phase, `:tank/intake`
    (no direct capital risk) MAY auto-commit when clean, and every
    decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [terminal.store :as store]
            [terminal.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :depot-superintendent :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through receipt verify -> approve, leaving an
  assessment on file. Uses distinct thread-ids per call site by
  suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :receipt/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :tank/intake :subject "tank-1"
                   :patch {:id "tank-1" :tank-id "T-101"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "T-101" (:tank-id (store/terminal-stock db "tank-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest receipt-verify-always-needs-approval
  (testing "receipt verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :receipt/verify :subject "tank-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "tank-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a receipt/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :receipt/verify :subject "tank-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "tank-2")) "no assessment written"))))

(deftest storage-commit-without-assessment-is-held
  (testing "storage/commit before any receipt verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :storage/commit :subject "tank-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest receipt-pod-chain-broken-is-held-and-unoverridable
  (testing "an unconfirmed receipt POD chain -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "tank-3")
          res (exec-op actor "t5" {:op :storage/commit :subject "tank-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:receipt-pod-chain-broken} (-> (store/ledger db) last :basis)))
      (is (empty? (store/act1-history db))))))

(deftest overfill-risk-is-held-and-unoverridable
  (testing "a planned receipt that exceeds the tank's ullage -> HOLD, and never reaches request-approval -- a Buncefield-type overfill (the fabrication measured-value-vs-rated-limit discipline applied to tank ullage)"
    (let [[db actor] (fresh)
          _ (verify! actor "t6pre" "tank-4")
          res (exec-op actor "t6" {:op :storage/commit :subject "tank-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:overfill-risk} (-> (store/ledger db) last :basis)))
      (is (empty? (store/act1-history db))))))

(deftest tank-integrity-assessment-stale-is-held-and-unoverridable
  (testing "a tank past its API 653 inspection interval -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "tank-5")
          res (exec-op actor "t7" {:op :storage/commit :subject "tank-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:tank-integrity-assessment-stale} (-> (store/ledger db) last :basis)))
      (is (empty? (store/act1-history db))))))

(deftest bonding-grounding-unconfirmed-is-held-and-unoverridable
  (testing "bonding-grounding unconfirmed before receipt -> HOLD, and never reaches request-approval -- static-electricity ignition control"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "tank-6")
          res (exec-op actor "t8" {:op :storage/commit :subject "tank-6"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:bonding-grounding-unconfirmed} (-> (store/ledger db) last :basis)))
      (is (empty? (store/act1-history db))))))

(deftest storage-commit-always-escalates-then-human-decides
  (testing "a clean, fully-verified, POD-confirmed, within-ullage, integrity-current, bonding-grounding-confirmed tank still ALWAYS interrupts for human approval -- :storage/commit is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "tank-1")
          r1 (exec-op actor "t9" {:op :storage/commit :subject "tank-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, commit record drafted"
        (let [r2 (approve! actor "t9")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:committed? (store/terminal-stock db "tank-1"))))
          (is (= 1 (count (store/act1-history db))) "one draft commit record"))))))

(deftest custody-transfer-always-escalates-then-human-decides
  (testing "a clean, fully-verified, already-committed tank still ALWAYS interrupts for human approval -- :custody/transfer is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "tank-1")
          _ (exec-op actor "t10commit" {:op :storage/commit :subject "tank-1"} operator)
          _ (approve! actor "t10commit")
          r1 (exec-op actor "t10" {:op :custody/transfer :subject "tank-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, transfer record drafted"
        (let [r2 (approve! actor "t10")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:custody-transferred? (store/terminal-stock db "tank-1"))))
          (is (= 1 (count (store/act2-history db))) "one draft transfer record"))))))

(deftest storage-commit-double-commit-is-held
  (testing "committing the same tank twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t11pre" "tank-1")
          _ (exec-op actor "t11a" {:op :storage/commit :subject "tank-1"} operator)
          _ (approve! actor "t11a")
          res (exec-op actor "t11" {:op :storage/commit :subject "tank-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-commit} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/act1-history db))) "still only the one earlier commit"))))

(deftest custody-transfer-double-transfer-is-held
  (testing "transferring custody of the same tank twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t12pre" "tank-1")
          _ (exec-op actor "t12commit" {:op :storage/commit :subject "tank-1"} operator)
          _ (approve! actor "t12commit")
          _ (exec-op actor "t12a" {:op :custody/transfer :subject "tank-1"} operator)
          _ (approve! actor "t12a")
          res (exec-op actor "t12" {:op :custody/transfer :subject "tank-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-transfer} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/act2-history db))) "still only the one earlier transfer"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :tank/intake :subject "tank-1"
                          :patch {:id "tank-1" :tank-id "T-101"}} operator)
      (exec-op actor "b" {:op :receipt/verify :subject "tank-2"} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
