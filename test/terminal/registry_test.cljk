(ns terminal.registry-test
  (:require [clojure.test :refer [deftest is]]
            [terminal.registry :as r]))

;; ----------------------------- overfill range-check pure function -----------------------------

(deftest overfill-risk-vs-ullage
  (is (not (r/overfill-risk? 200000 100000 300000)) "planned within ullage -> ok")
  (is (not (r/overfill-risk? 200000 300000 300000)) "planned exactly fills ullage -> ok")
  (is (r/overfill-risk? 200000 400000 300000) "planned exceeds ullage -> overfill")
  (is (r/overfill-risk? 0 1 0) "zero ullage, any receipt -> overfill")
  (is (r/overfill-risk? nil 100000 300000) "missing volume -> unsafe")
  (is (r/overfill-risk? 200000 nil 300000) "missing planned receipt -> unsafe")
  (is (r/overfill-risk? 200000 100000 nil) "missing ullage -> unsafe"))

;; ----------------------------- register-commit-record -----------------------------

(deftest commit-is-a-draft-not-a-real-commit
  (let [result (r/register-commit-record "tank-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest commit-assigns-commit-number
  (let [result (r/register-commit-record "tank-1" "JPN" 7)]
    (is (= (get result "commit_number") "JPN-COMMIT-000007"))
    (is (= (get-in result ["record" "terminal_stock_id"]) "tank-1"))
    (is (= (get-in result ["record" "kind"]) "storage-commit-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest commit-validation-rules
  (is (thrown? Exception (r/register-commit-record "" "JPN" 0)))
  (is (thrown? Exception (r/register-commit-record "tank-1" "" 0)))
  (is (thrown? Exception (r/register-commit-record "tank-1" "JPN" -1))))

;; ----------------------------- register-transfer-record -----------------------------

(deftest transfer-is-a-draft-not-a-real-transfer
  (let [result (r/register-transfer-record "tank-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest transfer-assigns-transfer-number
  (let [result (r/register-transfer-record "tank-1" "JPN" 7)]
    (is (= (get result "transfer_number") "JPN-TRANSFER-000007"))
    (is (= (get-in result ["record" "terminal_stock_id"]) "tank-1"))
    (is (= (get-in result ["record" "kind"]) "custody-transfer-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest transfer-validation-rules
  (is (thrown? Exception (r/register-transfer-record "" "JPN" 0)))
  (is (thrown? Exception (r/register-transfer-record "tank-1" "" 0)))
  (is (thrown? Exception (r/register-transfer-record "tank-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-commit-record "tank-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-commit-record "tank-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-COMMIT-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-COMMIT-000001" (get-in hist2 [1 "record_id"])))))
