(ns terminal.facts-test
  (:require [clojure.test :refer [deftest is]]
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
