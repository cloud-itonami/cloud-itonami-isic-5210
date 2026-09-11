(ns terminal.inbound-handoff-test
  "The RECEIVING half of the cross-actor handoff reference: a tank whose
  receipt is attributed to a carrier's leg (`terminal.facts`, inbound
  section).

  The point of the design under test is narrow and worth stating: HARD
  check 3 (`receipt-pod-chain-broken`) already refuses a commit whose
  upstream proof-of-delivery is unconfirmed, but it reads ONE
  unattributed boolean. These checks do not make that check stricter or
  looser -- they make the attribution behind it CHECKABLE. So every test
  below is paired: the failure fires, and the clean case does not."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.set]
            [terminal.governor :as governor]
            [terminal.facts :as facts]
            [terminal.store :as store]))

(def ctx {:actor-id "op-1" :actor-role :depot-superintendent :phase 3})

(defn- proposal-for [op]
  {:summary "s" :rationale "r"
   :cites ["JPN"]
   :effect :noop
   :stake (when (= op :storage/commit) :storage/commit)
   :value {}
   :confidence 0.9})

(defn- verdict [op subject]
  (governor/check {:op op :subject subject} ctx (proposal-for op) (store/seed-db)))

(defn- rules [v] (set (map :rule (:violations v))))

(def inbound-rules
  "Only the rules this namespace is about. `:storage/commit` legitimately
  raises OTHER violations (e.g. `:evidence-incomplete`, since the demo
  tanks carry no committed assessment), and asserting 'no violations at
  all' would make these tests fail for reasons that have nothing to do
  with the handoff attribution under test."
  #{:inbound-handoff-malformed :inbound-handoff-untraceable})

(defn- inbound [v] (clojure.set/intersection (rules v) inbound-rules))
(defn- soft-rules [v] (set (map :rule (:soft-violations v))))

;; ---------------------------------------------------------------- facts

(deftest carrier-roster-is-positive-sense
  (is (true? (facts/inbound-carrier-known? "cloud-itonami-isic-4920")))
  (testing "absence is never silently treated as known"
    (is (false? (facts/inbound-carrier-known? nil)))
    (is (false? (facts/inbound-carrier-known? "")))
    (is (false? (facts/inbound-carrier-known? "com-example-unregistered-haulier")))))

(deftest traceability-needs-the-tracking-ref
  (is (true? (facts/inbound-handoff-traceable? {:handoff/carrier-tracking-ref "CTR-1"})))
  (is (false? (facts/inbound-handoff-traceable? {})))
  (is (false? (facts/inbound-handoff-traceable? {:handoff/carrier-tracking-ref ""})))
  (is (false? (facts/inbound-handoff-traceable? {:handoff/carrier-actor "cloud-itonami-isic-4920"}))
      "naming the carrier without the leg is not traceability"))

;; ---------------------------------------------------------------- absence

(deftest absence-of-an-inbound-handoff-is-never-a-violation
  (testing "every tank predates this field; a pipeline receipt has no carrier leg"
    (doseq [op [:receipt/verify :storage/commit]]
      (let [v (verdict op "tank-1")]
        (is (empty? (inbound v)) (str op " on a tank with no handoff"))
        (is (empty? (soft-rules v)))))
    (testing ":receipt/verify on a clean tank raises nothing at all"
      (is (empty? (rules (verdict :receipt/verify "tank-1")))))))

;; ---------------------------------------------------------------- happy path

(deftest a-registered-traceable-handoff-passes
  (doseq [op [:receipt/verify :storage/commit]]
    (let [v (verdict op "tank-7")]
      (is (empty? (inbound v)) (str op ": " (rules v)))
      (is (empty? (soft-rules v)))))
  (testing ":receipt/verify on the happy-path tank raises nothing at all"
    (is (empty? (rules (verdict :receipt/verify "tank-7"))))))

;; ---------------------------------------------------------------- HARD

(deftest an-untraceable-handoff-is-refused
  (testing "well-formed, but no :handoff/carrier-tracking-ref -- there is no way
            to say WHICH leg the receipt rests on"
    (doseq [op [:receipt/verify :storage/commit]]
      (let [v (verdict op "tank-9")]
        (is (contains? (rules v) :inbound-handoff-untraceable))
        (is (true? (:hard? v)))))))

(deftest a-malformed-handoff-is-refused
  (let [db (store/seed-db)]
    (store/commit-record! db {:effect :tank/upsert
                              :path ["tank-1"]
                              :value {:id "tank-1"
                                      :receipt/handoff {:handoff/id "broken"}}})
    (let [v (governor/check {:op :receipt/verify :subject "tank-1"} ctx
                            (proposal-for :receipt/verify) db)]
      (is (contains? (rules v) :inbound-handoff-malformed))
      (testing "malformed is reported INSTEAD of untraceable -- one fix at a time"
        (is (not (contains? (rules v) :inbound-handoff-untraceable)))))))

(deftest a-non-positive-quantity-is-malformed
  (let [db (store/seed-db)]
    (store/commit-record! db {:effect :tank/upsert
                              :path ["tank-1"]
                              :value {:id "tank-1"
                                      :receipt/handoff
                                      {:handoff/id "h" :handoff/source-actor "a"
                                       :handoff/batch-id "b" :handoff/product-type-id :p
                                       :handoff/quantity-kg 0
                                       :handoff/dispatched-at-iso "2026-08-07T00:00:00Z"
                                       :handoff/carrier-tracking-ref "CTR"}}})
    (is (contains? (rules (governor/check {:op :receipt/verify :subject "tank-1"} ctx
                                          (proposal-for :receipt/verify) db))
                   :inbound-handoff-malformed))))

;; ---------------------------------------------------------------- SOFT

(deftest an-unregistered-carrier-escalates-but-never-holds
  (testing "a terminal cannot know every haulier in the world -- but a receipt
            attributed to an unregistered one is exactly what an operator
            should look at before it becomes inventory"
    (let [v (verdict :receipt/verify "tank-8")]
      (is (false? (:hard? v)))
      (is (empty? (rules v)))
      (is (contains? (soft-rules v) :inbound-carrier-unknown))
      (is (true? (:escalate? v)))
      (is (false? (:ok? v))))))

(deftest the-soft-signal-does-not-fire-when-the-carrier-is-registered
  (is (empty? (soft-rules (verdict :receipt/verify "tank-7")))))

;; ---------------------------------------------------------------- scope

(deftest intake-is-not-gated-on-an-undelivered-attribution
  (testing "there is no point holding a :tank/intake over the attribution of a
            delivery nobody has claimed yet"
    (let [v (verdict :tank/intake "tank-9")]
      (is (not (contains? (rules v) :inbound-handoff-untraceable))))))

(deftest the-checks-read-the-store-not-the-proposal
  (testing "a proposal cannot launder an attribution it does not have --
            the governor re-derives the handoff from the tank record"
    (let [db (store/seed-db)
          lying (assoc (proposal-for :receipt/verify)
                       :value {:receipt/handoff
                               {:handoff/id "nice" :handoff/source-actor "a"
                                :handoff/batch-id "b" :handoff/product-type-id :p
                                :handoff/quantity-kg 1.0
                                :handoff/dispatched-at-iso "2026-08-07T00:00:00Z"
                                :handoff/carrier-actor "cloud-itonami-isic-4920"
                                :handoff/carrier-tracking-ref "CTR-FAKE"}})
          v (governor/check {:op :receipt/verify :subject "tank-9"} ctx lying db)]
      (is (contains? (rules v) :inbound-handoff-untraceable)
          "tank-9's own record is still untraceable, whatever the proposal claims"))))

;; ---------------------------------------------------------------- shape

(deftest check-still-returns-its-original-keys
  (testing ":soft-violations is additive -- existing consumers read :hard?/:escalate?"
    (let [v (verdict :receipt/verify "tank-1")]
      (doseq [k [:ok? :violations :confidence :hard? :escalate? :high-stakes?]]
        (is (contains? v k) (str k " must survive"))))))
