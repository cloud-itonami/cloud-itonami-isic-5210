(ns terminal.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [terminal.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest all-eight-seeded-jurisdictions-have-a-legal-basis
  ;; every seeded terminal-storage jurisdiction actually has a real
  ;; official legal-basis reported honestly here
  (doseq [iso3 ["JPN" "USA" "GBR" "NOR" "IND" "SAU" "ARE" "MEX"]]
    (is (some? (:legal-basis (facts/spec-basis iso3))) (str iso3 " legal-basis"))
    (is (string? (:legal-basis (facts/spec-basis iso3))) (str iso3 " legal-basis is a string"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

;; ───────── Downstream Cross-Actor Handoff (optional, isic-5210 -> e.g. isic-5224/isic-5229) ─────────

(def ^:private well-formed-handoff
  {:handoff/id "h-1"
   :handoff/source-actor "cloud-itonami-isic-5210"
   :handoff/batch-id "batch-1"
   :handoff/product-type-id :crude-light
   :handoff/quantity-kg 120.5
   :handoff/dispatched-at-iso "2026-07-24T00:00:00Z"})

(deftest handoff-record-well-formed-test
  (testing "complete handoff passes"
    (is (true? (facts/handoff-record-well-formed? well-formed-handoff))))

  (testing "missing :handoff/id fails"
    (is (false? (facts/handoff-record-well-formed? (dissoc well-formed-handoff :handoff/id)))))

  (testing "missing :handoff/source-actor fails"
    (is (false? (facts/handoff-record-well-formed? (dissoc well-formed-handoff :handoff/source-actor)))))

  (testing "missing :handoff/batch-id fails"
    (is (false? (facts/handoff-record-well-formed? (dissoc well-formed-handoff :handoff/batch-id)))))

  (testing "missing :handoff/product-type-id fails"
    (is (false? (facts/handoff-record-well-formed? (dissoc well-formed-handoff :handoff/product-type-id)))))

  (testing "missing :handoff/quantity-kg fails"
    (is (false? (facts/handoff-record-well-formed? (dissoc well-formed-handoff :handoff/quantity-kg)))))

  (testing "missing :handoff/dispatched-at-iso fails"
    (is (false? (facts/handoff-record-well-formed? (dissoc well-formed-handoff :handoff/dispatched-at-iso)))))

  (testing "non-positive quantity fails"
    (is (false? (facts/handoff-record-well-formed? (assoc well-formed-handoff :handoff/quantity-kg 0)))))

  (testing "negative quantity fails"
    (is (false? (facts/handoff-record-well-formed? (assoc well-formed-handoff :handoff/quantity-kg -5.0)))))

  (testing "blank batch-id fails"
    (is (false? (facts/handoff-record-well-formed? (assoc well-formed-handoff :handoff/batch-id "")))))

  (testing "nil handoff fails"
    (is (false? (facts/handoff-record-well-formed? nil))))

  (testing "non-map handoff fails"
    (is (false? (facts/handoff-record-well-formed? "not-a-map"))))

  (testing "OPTIONAL pass-through fields never affect well-formedness"
    (is (true? (facts/handoff-record-well-formed?
                (assoc well-formed-handoff
                       :handoff/cold-chain-temp-min-c 2.0
                       :handoff/cold-chain-temp-max-c 10.0
                       :handoff/unspsc-code "12345678"
                       :handoff/gtin "0211075000011"
                       :handoff/carrier-actor "cloud-itonami-isic-4920"
                       :handoff/carrier-tracking-ref "trk-1"))))))
