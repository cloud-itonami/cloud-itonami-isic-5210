(ns terminal.governor-test
  "Unit-level tests for the Terminal Storage Governor's checks, direct
  calls to the (private) check fns and to `governor/check` -- the same
  idiom `meatprocessing.governor-test` (cloud-itonami-isic-1010) uses
  for its own `:coordinate-shipment` -> `:handoff` egress check.

  Scoped to the NEW `handoff-malformed-violations` check only (7th HARD
  check, ADR-2800002100) -- the existing 6 HARD checks + 2 double-
  actuation guards are already covered end-to-end by
  `governor-contract-test`."
  (:require [clojure.test :refer [deftest is testing]]
            [terminal.governor :as governor]
            [terminal.store :as store]
            [terminal.facts :as facts]))

(def ^:private well-formed-handoff
  {:handoff/id "h-1"
   :handoff/source-actor "cloud-itonami-isic-5210"
   :handoff/batch-id "batch-1"
   :handoff/product-type-id :crude-light
   :handoff/quantity-kg 120.5
   :handoff/dispatched-at-iso "2026-07-24T00:00:00Z"})

;; ----------------------------- handoff-malformed-violations (unit) -----------------------------

(deftest handoff-malformed-violations-test
  (testing "no :handoff at all -> no violation (attachment is optional)"
    (let [request {:op :custody/transfer}
          proposal {:value {}}
          violations (#'governor/handoff-malformed-violations request proposal)]
      (is (empty? violations))))

  (testing "well-formed :handoff -> no violation"
    (let [request {:op :custody/transfer}
          proposal {:value {:handoff well-formed-handoff}}
          violations (#'governor/handoff-malformed-violations request proposal)]
      (is (empty? violations))))

  (testing "malformed :handoff (missing quantity-kg) -> hard violation"
    (let [request {:op :custody/transfer}
          proposal {:value {:handoff (dissoc well-formed-handoff :handoff/quantity-kg)}}
          violations (#'governor/handoff-malformed-violations request proposal)]
      (is (seq violations))
      (is (= :handoff-malformed (-> violations first :rule)))))

  (testing "malformed :handoff (missing batch-id) -> hard violation"
    (let [request {:op :custody/transfer}
          proposal {:value {:handoff (dissoc well-formed-handoff :handoff/batch-id)}}
          violations (#'governor/handoff-malformed-violations request proposal)]
      (is (seq violations))
      (is (= :handoff-malformed (-> violations first :rule)))))

  (testing "malformed :handoff (missing source-actor) -> hard violation"
    (let [request {:op :custody/transfer}
          proposal {:value {:handoff (dissoc well-formed-handoff :handoff/source-actor)}}
          violations (#'governor/handoff-malformed-violations request proposal)]
      (is (seq violations))
      (is (= :handoff-malformed (-> violations first :rule)))))

  (testing "malformed :handoff (non-positive quantity) -> hard violation"
    (let [request {:op :custody/transfer}
          proposal {:value {:handoff (assoc well-formed-handoff :handoff/quantity-kg 0)}}
          violations (#'governor/handoff-malformed-violations request proposal)]
      (is (seq violations))
      (is (= :handoff-malformed (-> violations first :rule)))))

  (testing "nil :handoff under :value -> no violation (same as absent)"
    (let [request {:op :custody/transfer}
          proposal {:value {:handoff nil}}
          violations (#'governor/handoff-malformed-violations request proposal)]
      (is (empty? violations))))

  (testing "only applies to :custody/transfer -- :storage/commit is untouched"
    (let [request {:op :storage/commit}
          proposal {:value {:handoff (dissoc well-formed-handoff :handoff/quantity-kg)}}
          violations (#'governor/handoff-malformed-violations request proposal)]
      (is (empty? violations)))))

;; ----------------------------- full check pipeline -----------------------------

(defn- store-with-clean-transfer-conditions
  "A seeded MemStore where `tank-1`'s :custody/transfer proposal is clean
  on every OTHER check (spec-basis cited, evidence assessment on file,
  not already transferred) -- isolates the handoff-malformed check as
  the ONLY variable in the full `governor/check` pipeline."
  []
  (let [db (store/seed-db)]
    (store/commit-record! db {:effect :receipt-assessment/set
                               :path ["tank-1"]
                               :value {:jurisdiction "JPN" :checklist (facts/evidence-checklist "JPN")}
                               :payload {:jurisdiction "JPN" :checklist (facts/evidence-checklist "JPN")}})
    db))

(deftest check-with-malformed-handoff-is-hard-violation
  (testing "full check pipeline holds on a malformed but present :handoff"
    (let [st (store-with-clean-transfer-conditions)
          request {:op :custody/transfer :subject "tank-1" :stake :custody/transfer}
          proposal {:value {:handoff (dissoc well-formed-handoff :handoff/quantity-kg)}
                    :cites ["tank-1"] :confidence 0.9}
          context {:actor-id "test"}
          verdict (governor/check request context proposal st)]
      (is (true? (:hard? verdict)))
      (is (some #(= (:rule %) :handoff-malformed) (:violations verdict))))))

(deftest check-with-well-formed-handoff-is-no-violation
  (testing "full check pipeline is clean when a :handoff IS present and well-formed"
    (let [st (store-with-clean-transfer-conditions)
          request {:op :custody/transfer :subject "tank-1" :stake :custody/transfer}
          proposal {:value {:handoff well-formed-handoff}
                    :cites ["tank-1"] :confidence 0.9}
          context {:actor-id "test"}
          verdict (governor/check request context proposal st)]
      (is (not (some #(= (:rule %) :handoff-malformed) (:violations verdict)))))))

(deftest check-with-absent-handoff-is-no-violation
  (testing "full check pipeline is clean when NO :handoff is attached at all -- optional"
    (let [st (store-with-clean-transfer-conditions)
          request {:op :custody/transfer :subject "tank-1" :stake :custody/transfer}
          proposal {:value {} :cites ["tank-1"] :confidence 0.9}
          context {:actor-id "test"}
          verdict (governor/check request context proposal st)]
      (is (not (some #(= (:rule %) :handoff-malformed) (:violations verdict)))))))
