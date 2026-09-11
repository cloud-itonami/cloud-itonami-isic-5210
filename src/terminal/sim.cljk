(ns terminal.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean tank through
  intake -> receipt verification -> storage commit (escalate/
  approve/commit) -> custody transfer (escalate/approve/commit),
  then shows HARD-hold scenarios: a jurisdiction with no spec-basis,
  a receipt whose upstream POD chain is unconfirmed, a planned receipt
  that would overfill the tank (Buncefield-type), a tank past its API
  653 integrity-assessment interval, a tank with bonding-grounding
  unconfirmed, a double commit, and a double transfer.

  Like every sibling actor's new checks, this actor's terminal-storage
  checks (`receipt-pod-chain-broken?`, `overfill-risk?`,
  `tank-integrity-assessment-stale?`, `bonding-grounding-unconfirmed?`)
  are evaluated directly at `:storage/commit` time rather than via a
  separate screening op -- a real storage-commit decision validates the
  POD chain, ullage headroom, tank integrity and bonding-grounding at
  the point of the act itself, not as a discrete pre-screening
  ceremony. Each check is still exercised directly and independently
  below, one tank per HARD-hold scenario, following the SAME 'exercise
  the failure mode directly, never only via a happy-path actuation'
  discipline `parksafety`'s ADR-2607071922 Decision 5 and every sibling
  since establish."
  (:require [langgraph.graph :as g]
            [terminal.store :as store]
            [terminal.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :depot-superintendent :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== tank/intake tank-1 (JPN, clean) ==")
    (println (exec-op actor "t1" {:op :tank/intake :subject "tank-1"
                                  :patch {:id "tank-1" :tank-id "T-101"}} operator))

    (println "== receipt/verify tank-1 (escalates -- human approves) ==")
    (println (exec-op actor "t2" {:op :receipt/verify :subject "tank-1"} operator))
    (println (approve! actor "t2"))

    (println "== storage/commit tank-1 (always escalates -- :storage/commit) ==")
    (let [r (exec-op actor "t3" {:op :storage/commit :subject "tank-1"} operator)]
      (println r)
      (println "-- human depot superintendent approves --")
      (println (approve! actor "t3")))

    (println "== custody/transfer tank-1 (always escalates -- :custody/transfer) ==")
    (let [r (exec-op actor "t4" {:op :custody/transfer :subject "tank-1"} operator)]
      (println r)
      (println "-- human depot superintendent approves --")
      (println (approve! actor "t4")))

    (println "== receipt/verify tank-2 (no spec-basis -> HARD hold) ==")
    (println (exec-op actor "t5" {:op :receipt/verify :subject "tank-2"} operator))

    (println "== receipt/verify tank-3 (escalates -- human approves; sets up the POD-chain test) ==")
    (println (exec-op actor "t6" {:op :receipt/verify :subject "tank-3"} operator))
    (println (approve! actor "t6"))

    (println "== storage/commit tank-3 (receipt POD-chain broken -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :storage/commit :subject "tank-3"} operator))

    (println "== receipt/verify tank-4 (escalates -- human approves; sets up the overfill test) ==")
    (println (exec-op actor "t8" {:op :receipt/verify :subject "tank-4"} operator))
    (println (approve! actor "t8"))

    (println "== storage/commit tank-4 (planned receipt exceeds ullage -> HARD hold) ==")
    (println (exec-op actor "t9" {:op :storage/commit :subject "tank-4"} operator))

    (println "== receipt/verify tank-5 (escalates -- human approves; sets up the integrity test) ==")
    (println (exec-op actor "t10" {:op :receipt/verify :subject "tank-5"} operator))
    (println (approve! actor "t10"))

    (println "== storage/commit tank-5 (API 653 integrity assessment stale -> HARD hold) ==")
    (println (exec-op actor "t11" {:op :storage/commit :subject "tank-5"} operator))

    (println "== receipt/verify tank-6 (escalates -- human approves; sets up the bonding-grounding test) ==")
    (println (exec-op actor "t12" {:op :receipt/verify :subject "tank-6"} operator))
    (println (approve! actor "t12"))

    (println "== storage/commit tank-6 (bonding-grounding unconfirmed -> HARD hold) ==")
    (println (exec-op actor "t13" {:op :storage/commit :subject "tank-6"} operator))

    (println "== storage/commit tank-1 AGAIN (double-commit -> HARD hold) ==")
    (println (exec-op actor "t14" {:op :storage/commit :subject "tank-1"} operator))

    (println "== custody/transfer tank-1 AGAIN (double-transfer -> HARD hold) ==")
    (println (exec-op actor "t15" {:op :custody/transfer :subject "tank-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft storage-commit records ==")
    (doseq [r (store/act1-history db)] (println r))

    (println "== draft custody-transfer records ==")
    (doseq [r (store/act2-history db)] (println r))))
