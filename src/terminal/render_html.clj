(ns terminal.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger seq 11): this repo's `docs/samples/operator-console.html`
  was HAND-AUTHORED (exactly one commit, no generator anywhere in the
  tree) despite looking polished -- the same failure mode
  `cloud-itonami-isic-2910`'s old hand-pasted transcript had before a
  prior iteration replaced it with a real generator. This namespace
  drives the REAL actor stack (`terminal.operation` -> `terminal.governor`
  -> `terminal.store`) through a scenario adapted from this repo's own
  `terminal.sim` demo driver (`clojure -M:dev:run`, confirmed by
  actually running it before this file was written -- unlike
  `cloud-itonami-isic-851`'s `schoolops.sim`, this repo's own sim driver
  uses ids that DO match `terminal.store/demo-data`'s seeded tanks
  exactly (tank-1..tank-6), and every disposition it produces (auto-
  commit / escalate+approve / HARD hold, and the exact `:rule` on each
  hold) matches `terminal.governor`'s own documented checks precisely,
  so it was safe to reuse rather than author from scratch) and rendered
  deterministically -- no invented numbers, no timestamps in the page
  content, byte-identical across reruns against the same seed (verified
  by diffing two consecutive runs before shipping).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [terminal.store :as store]
            [terminal.registry :as registry]
            [terminal.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness (unchanged across every repo
;; in this cluster -- do not rewrite, only copy) -----------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :depot-superintendent :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through the same scenario `terminal.sim`
  (`clojure -M:dev:run`) exercises, using ONLY real tank ids from
  `terminal.store/demo-data`:

  tank-1 (JPN, clean) walks the full clean lifecycle: `:tank/intake` is
  a phase-3, no-capital-risk auto-commit (governor clean, `:tank/intake`
  is the ONLY op in phase 3's `:auto` set); `:receipt/verify` (JPN has a
  real spec-basis in `terminal.facts`) ALWAYS escalates (not auto-
  eligible at any phase) and is approved by a human depot superintendent;
  `:storage/commit` and `:custody/transfer` -- the two REAL-WORLD
  actuation events this actor performs (a confirmed receipt becomes
  inventory under the tank's book-of-record / custody moves to the next
  custodian) -- ALSO ALWAYS escalate (the governor's own `high-stakes`
  gate AND `terminal.phase`'s permanently-empty auto-set for these two
  ops agree, independently, that actuation is never auto, at any phase)
  and are each approved, producing one draft storage-commit record
  (`JPN-COMMIT-000000`) and one draft custody-transfer record
  (`JPN-TRANSFER-000000`).

  Then five DISTINCT HARD-hold reasons, none of which ever reach a
  human (a human approver cannot override a HARD violation), each tank
  isolating exactly one failure mode:
    - tank-2 (jurisdiction ATL, not in `terminal.facts/catalog`):
      `:receipt/verify` HARD-holds on `:no-spec-basis` -- the advisor
      may not invent a jurisdiction's tank-overfill/tank-integrity/
      bonding-grounding requirements.
    - tank-3 (`:receipt-confirmed? false` in the seed data): assessed
      first (clean escalate+approve, so evidence is on file and the
      hold below is isolated to the POD-chain check alone), then
      `:storage/commit` HARD-holds on `:receipt-pod-chain-broken` --
      the governor independently verifies the prior pipeline batch's
      proof-of-delivery was confirmed.
    - tank-4 (`:planned-receipt-barrels` 400000 > `:ullage-barrels`
      300000): `:storage/commit` HARD-holds on `:overfill-risk` -- the
      Buncefield-type overfill check (`terminal.registry/overfill-risk?`)
      independently recomputes planned receipt vs remaining ullage.
    - tank-5 (`:integrity-assessment-current? false`): `:storage/commit`
      HARD-holds on `:tank-integrity-assessment-stale` -- an API 653
      inspection interval that has lapsed.
    - tank-6 (`:bonding-grounding-confirmed? false`): `:storage/commit`
      HARD-holds on `:bonding-grounding-unconfirmed` -- unconfirmed
      static-electricity ignition control.

  Finally, tank-1 is re-proposed for `:storage/commit` and
  `:custody/transfer` a SECOND time, HARD-holding on `:already-commit`
  and `:already-transfer` -- the double-actuation guards, off the
  dedicated `:committed?`/`:custody-transferred?` booleans.

  Returns the resulting store -- every field `render` below reads is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]

    ;; tank-1: clean directory-normalization patch -- phase-3 auto-commit,
    ;; no capital risk yet.
    (exec! actor "tk1-intake" {:op :tank/intake :subject "tank-1"
                                :patch {:id "tank-1" :tank-id "T-101"}})

    ;; tank-1: per-jurisdiction evidence-checklist draft (JPN has a real
    ;; spec-basis) -- ALWAYS escalates, approved by a human.
    (exec! actor "tk1-verify" {:op :receipt/verify :subject "tank-1"})
    (approve! actor "tk1-verify")

    ;; tank-1: REAL storage commit (a confirmed receipt becomes inventory,
    ;; actuation) -- ALWAYS escalates regardless of phase or confidence,
    ;; approved by a human depot superintendent.
    (exec! actor "tk1-commit" {:op :storage/commit :subject "tank-1"})
    (approve! actor "tk1-commit")

    ;; tank-1: REAL custody transfer (actuation) -- ALWAYS escalates,
    ;; approved by a human.
    (exec! actor "tk1-transfer" {:op :custody/transfer :subject "tank-1"})
    (approve! actor "tk1-transfer")

    ;; tank-2 (ATL): no official spec-basis in terminal.facts -> HARD
    ;; hold on :no-spec-basis, never reaches a human.
    (exec! actor "tk2-verify" {:op :receipt/verify :subject "tank-2"})

    ;; tank-3: verify JPN first (clean escalate+approve) so evidence is
    ;; on file and the POD-chain hold below is isolated.
    (exec! actor "tk3-verify" {:op :receipt/verify :subject "tank-3"})
    (approve! actor "tk3-verify")

    ;; tank-3: receipt-confirmed? false -> HARD hold on
    ;; :receipt-pod-chain-broken, never reaches a human.
    (exec! actor "tk3-commit" {:op :storage/commit :subject "tank-3"})

    ;; tank-4: verify JPN first (clean escalate+approve).
    (exec! actor "tk4-verify" {:op :receipt/verify :subject "tank-4"})
    (approve! actor "tk4-verify")

    ;; tank-4: planned-receipt-barrels (400000) exceeds ullage-barrels
    ;; (300000) -> HARD hold on :overfill-risk (Buncefield-type), never
    ;; reaches a human.
    (exec! actor "tk4-commit" {:op :storage/commit :subject "tank-4"})

    ;; tank-5: verify JPN first (clean escalate+approve).
    (exec! actor "tk5-verify" {:op :receipt/verify :subject "tank-5"})
    (approve! actor "tk5-verify")

    ;; tank-5: integrity-assessment-current? false -> HARD hold on
    ;; :tank-integrity-assessment-stale, never reaches a human.
    (exec! actor "tk5-commit" {:op :storage/commit :subject "tank-5"})

    ;; tank-6: verify JPN first (clean escalate+approve).
    (exec! actor "tk6-verify" {:op :receipt/verify :subject "tank-6"})
    (approve! actor "tk6-verify")

    ;; tank-6: bonding-grounding-confirmed? false -> HARD hold on
    ;; :bonding-grounding-unconfirmed, never reaches a human.
    (exec! actor "tk6-commit" {:op :storage/commit :subject "tank-6"})

    ;; tank-1 AGAIN: double-commit -> HARD hold on :already-commit.
    (exec! actor "tk1-commit-again" {:op :storage/commit :subject "tank-1"})

    ;; tank-1 AGAIN: double-transfer -> HARD hold on :already-transfer.
    (exec! actor "tk1-transfer-again" {:op :custody/transfer :subject "tank-1"})

    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- status-cell [ledger subject-id]
  (let [f (last-fact-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- tank-row [ledger {:keys [id tank-id product-grade jurisdiction volume-barrels
                                 ullage-barrels planned-receipt-barrels
                                 committed? custody-transferred?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td class=\"num\">%s</td><td class=\"num\">%s</td><td class=\"num\">%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc tank-id) (esc product-grade) (esc jurisdiction)
          (esc volume-barrels) (esc ullage-barrels) (esc planned-receipt-barrels)
          (if committed? "<span class=\"ok\">committed</span>" "<span class=\"muted\">not committed</span>")
          (if custody-transferred? "<span class=\"ok\">transferred</span>" "<span class=\"muted\">not transferred</span>")
          (status-cell ledger id)))

(defn- ok-err [ok?] (if ok? "<span class=\"ok\">&#10003;</span>" "<span class=\"err\">&#10007;</span>"))

(defn- checklist-row [{:keys [id tank-id jurisdiction receipt-confirmed? volume-barrels
                               planned-receipt-barrels ullage-barrels
                               integrity-assessment-current? bonding-grounding-confirmed?]}]
  (let [overfill? (registry/overfill-risk? volume-barrels planned-receipt-barrels ullage-barrels)]
    (format "        <tr><td>%s (%s, %s)</td><td>%s</td><td>%s (%s / %s bbl)</td><td>%s</td><td>%s</td></tr>"
            (esc id) (esc tank-id) (esc jurisdiction)
            (ok-err (true? receipt-confirmed?))
            (ok-err (not overfill?)) (esc planned-receipt-barrels) (esc ullage-barrels)
            (ok-err (true? integrity-assessment-current?))
            (ok-err (true? bonding-grounding-confirmed?)))))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) %)) (str/join ", "))
                    (some-> disposition name) ""))))

(defn- record-row [prefix {:strs [record_id terminal_stock_id jurisdiction immutable]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc prefix) (esc record_id) (esc terminal_stock_id) (esc jurisdiction)
          (if immutable "<span class=\"ok\">immutable draft</span>" "<span class=\"muted\">n/a</span>")))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract
  ;; (`terminal.governor`/`terminal.phase`) -- documentation of fixed
  ;; behavior, not runtime telemetry, so it is legitimately hand-
  ;; described rather than derived from a live run.
  ["        <tr><td><code>:tank/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when clean, no capital risk yet -- the ONLY auto-eligible op in this domain</span></td></tr>"
   "        <tr><td><code>:receipt/verify</code></td><td><span class=\"warn\">ALWAYS human approval when clean &middot; jurisdiction spec-basis independently checked against <code>terminal.facts</code>, never fabricated -- <span class=\"critical\">HARD hold</span> if the jurisdiction has none</span></td></tr>"
   "        <tr><td><code>:storage/commit</code></td><td><span class=\"warn\">ALWAYS human approval (real-world act, actuation) &middot; receipt POD-chain, overfill risk (Buncefield-type, <code>terminal.registry/overfill-risk?</code>), API 653 tank-integrity, bonding-grounding and evidence-completeness independently re-verified &middot; double-commit guard enforced &middot; never auto at any phase</span></td></tr>"
   "        <tr><td><code>:custody/transfer</code></td><td><span class=\"warn\">ALWAYS human approval (real-world act, actuation) &middot; evidence-completeness independently re-verified &middot; double-transfer guard enforced &middot; never auto at any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        tanks (store/all-terminal-stocks db)
        tank-rows (str/join "\n" (map (partial tank-row ledger) tanks))
        checklist-rows (str/join "\n" (map checklist-row tanks))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        commit-rows (str/join "\n" (map (partial record-row "storage-commit") (store/act1-history db)))
        transfer-rows (str/join "\n" (map (partial record-row "custody-transfer") (store/act2-history db)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-5210 &middot; warehousing and storage (petroleum terminal)</title><style>"
   (jp-go-dds.skin/dds+skin)
   "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Storage (Terminal / Depot) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · storage commit/custody transfer always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Tanks</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>terminal.store</code> via <code>terminal.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>ID</th><th>Tank</th><th>Product</th><th>Jurisdiction</th><th>Volume (bbl)</th><th>Ullage (bbl)</th><th>Planned receipt (bbl)</th><th>Storage commit</th><th>Custody transfer</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     tank-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Tank safety validation (Terminal Storage Governor, independently re-verified)</h2>\n"
     "    <p class=\"muted\">Receipt POD-chain, Buncefield-type overfill (planned receipt vs remaining ullage), API 653 tank-integrity assessment and bonding-grounding — recomputed here from the same store fields the governor itself reads, not trusted from the advisor.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Tank</th><th>Receipt POD confirmed</th><th>Overfill risk clear</th><th>API 653 integrity current</th><th>Bonding-grounding confirmed</th></tr></thead>\n"
     "      <tbody>\n"
     checklist-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Draft storage-commit / custody-transfer records</h2>\n"
     "    <p class=\"muted\">Unsigned drafts only — the operator's own act of signing is outside this actor's authority (see README <code>Actuation</code>).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Kind</th><th>Record id</th><th>Tank</th><th>Jurisdiction</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     commit-rows (when (and (seq commit-rows) (seq transfer-rows)) "\n")
     transfer-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Terminal Storage Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden by a human approver. Jurisdiction spec-basis, receipt POD-chain, overfill risk, tank-integrity assessment, bonding-grounding and evidence completeness are independently recomputed, never trusted from the advisor's proposal; a real storage commit or custody transfer is always a human depot superintendent's call, at every rollout phase.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/act1-history db)) "storage-commit drafts,"
             (count (store/act2-history db)) "custody-transfer drafts )")))
