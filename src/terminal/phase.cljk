(ns terminal.phase
  "Phase 0->3 staged rollout for the terminal-storage actor.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- tank intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-verify  -- adds receipt-verification writes,
                                 still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:tank/intake` (no capital risk
                                 yet) may auto-commit. `:storage/commit`/
                                 `:custody/transfer` NEVER auto-commit,
                                 at any phase.

  `:storage/commit`/`:custody/transfer` are deliberately ABSENT from
  every phase's `:auto` set, including phase 3 -- a permanent structural
  fact, not a rollout milestone still to come. Committing a petroleum
  receipt to a storage tank (the receipt becomes inventory under the
  tank's book-of-record) and transferring custody of a committed stock
  to the next custodian (pipeline batch out, tanker loading, refinery
  rundown handover) are the two real-world physical / legal acts this
  actor performs; both are always a human terminal operator's call.
  `terminal.governor`'s `:storage/commit`/`:custody/transfer` high-stakes
  gate enforces the same invariant independently -- two layers, not one,
  agree on this. Like every prior sibling's phase 3 `:auto` set, this
  domain has only ONE member (`:tank/intake`) -- no separate no-capital-
  risk 'file' lifecycle distinct from the tank itself.")

(def read-ops  #{})
(def write-ops #{:tank/intake :receipt/verify :storage/commit :custody/transfer})

;; NOTE the invariant: `:storage/commit`/`:custody/transfer` are members of
;; `write-ops` (governor-gated like any write) but are NEVER members of
;; any phase's `:auto` set below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                  :auto #{}}
   1 {:label "assisted-intake"  :writes #{:tank/intake}                                      :auto #{}}
   2 {:label "assisted-verify"  :writes #{:tank/intake :receipt/verify}                      :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:tank/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:storage/commit`/`:custody/transfer` are never auto-eligible at any
    phase, so they always escalate once the governor clears them (or hold
    if the governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Terminal Storage Governor verdict to a base disposition before
  the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
