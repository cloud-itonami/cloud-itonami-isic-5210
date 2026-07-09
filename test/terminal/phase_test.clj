(ns terminal.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:storage/commit`/`:custody/transfer` must NEVER be a
  member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [terminal.phase :as phase]))

(deftest storage-commit-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in any future entry, auto-commits a real storage commit"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :storage/commit))
          (str "phase " n " must not auto-commit :storage/commit")))))

(deftest custody-transfer-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in any future entry, auto-commits a real custody transfer"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :custody/transfer))
          (str "phase " n " must not auto-commit :custody/transfer")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":tank/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:tank/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :tank/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :storage/commit} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :custody/transfer} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :tank/intake} :commit)))))
