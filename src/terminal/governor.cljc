(ns terminal.governor
  "Terminal Storage Governor -- the independent compliance layer that
  earns the TerminalAdvisor the right to commit. The LLM has no notion
  of jurisdictional tank-overfill / tank-integrity / bonding-and-
  grounding law, whether a planned receipt actually fits a tank's
  remaining ullage (a Buncefield-type overfill risk), whether the tank's
  API 653 inspection interval is actually current, whether the prior
  pipeline batch's proof-of-delivery (POD) chain was actually confirmed,
  whether bonding-and-grounding was actually confirmed before receipt,
  or when an act stops being a draft and becomes a real-world storage
  commit or custody transfer, so this MUST be a separate system able to
  *reject* a proposal and fall back to HOLD.

  Unlike `freightops`/4920's own governor (built on TOP of a real,
  pre-existing bespoke capability library `kotoba-lang/logistics`),
  this terminal-storage vertical has NO pre-existing petroleum-terminal
  capability library to delegate to -- so the overfill-risk check
  (planned receipt vs remaining ullage) is a pure function defined in
  `terminal.registry` and called directly here, the SAME 'reuse a
  capability library's own validated function' discipline
  `retailops.governor`'s ean13 check establishes, here applied to this
  vertical's OWN pure registry functions rather than a separate library.

  `:itonami.blueprint/governor` is `:terminal-storage-governor`,
  grep-verified UNIQUE fleet-wide -- no naming-collision precedent
  question, a fresh independent build following the SAME governed-
  actor architecture (langgraph StateGraph + independent Governor +
  Phase 0->3 rollout) established by `cloud-itonami-isic-6511`.

  Seven checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them. The confidence/actuation gate (item 8 below) is
  SOFT: it asks a human to look (low confidence / actuation), and the
  human may approve -- but see `terminal.phase`: for `:stake :storage/commit`/
  `:custody/transfer` (a real commit or transfer) NO phase ever allows
  auto-commit either. Two independent layers agree that actuation is
  always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source
                                       (`terminal.facts`), or invent one?
    2. Evidence incomplete         -- for `:storage/commit`/`:custody/
                                       transfer`, has the jurisdiction
                                       actually been assessed with a
                                       full tank-overfill / tank-integrity
                                       / bonding-grounding evidence
                                       checklist on file?
    3. Receipt POD-chain broken    -- for `:storage/commit`, INDEPENDENTLY
                                       verify the prior pipeline batch's
                                       proof-of-delivery (POD) was
                                       confirmed before committing the
                                       receipt to inventory (freight
                                       POD-chain discipline).
    4. Overfill risk               -- for `:storage/commit`, INDEPENDENTLY
                                       verify the planned receipt fits the
                                       tank's remaining ullage via
                                       `terminal.registry/overfill-risk?`
                                       (the fabrication measured-value-vs-
                                       rated-limit discipline) -- a
                                       Buncefield-type overfill, evaluated
                                       UNCONDITIONALLY.
    5. Tank-integrity assessment
       stale                       -- for `:storage/commit`, INDEPENDENTLY
                                       verify the tank's API 653 inspection
                                       interval is current -- evaluated
                                       UNCONDITIONALLY.
    6. Bonding-grounding
       unconfirmed                 -- for `:storage/commit`, INDEPENDENTLY
                                       verify bonding-and-grounding was
                                       confirmed before receipt/transfer
                                       (static-electricity ignition
                                       control).
    7b. INBOUND receipt handoff -- a tank record carrying an OPTIONAL
       `:receipt/handoff` (terminal.facts) that is PRESENT but
       malformed, or present and well-formed but with no
       `:handoff/carrier-tracking-ref` to make the delivery claim
       checkable against the carrier's own leg record. Absence is
       never a violation. This is the RECEIVING half of the same
       reference whose sending half is item 7 below: check 3 refuses a
       commit whose POD is unconfirmed, and this makes the POD's
       attribution auditable instead of an unattributed boolean.
    7. `:custody/transfer` carrying an OPTIONAL `:handoff` record
       (downstream to e.g. cloud-itonami-isic-5224 or
       cloud-itonami-isic-5229) that IS present but malformed --
       absence of `:handoff` is never itself a violation, only a
       present-but-malformed one is (ADR-2800002100).
    8. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:storage/commit`/
                                       `:custody/transfer` (REAL acts)
                                       -> escalate.

  ONE SOFT signal, `:soft-violations` (never a hold, escalates only):
  an inbound `:receipt/handoff` that is well-formed AND traceable but
  names a `:handoff/carrier-actor` this terminal does not have on its
  roster (`terminal.facts/inbound-carrier-actors`). A terminal cannot
  know every haulier in the world, so an unregistered carrier is not
  grounds to refuse -- but a receipt attributed to one is exactly the
  claim an operator should look at before it becomes inventory. This
  mirrors `cloud-itonami-isic-5229`'s `:storage-handoff-suspect`
  (ADR-2800002100), which is the same actor-pair reference seen from
  the other end.

  Two more guards, double-commit/double-transfer prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-commit-violations`/
  `already-transfer-violations` refuse to commit/transfer the SAME tank
  twice, off dedicated `:committed?`/`:custody-transferred?` facts
  (never a `:status` value) -- the SAME 'check a dedicated boolean,
  not status' discipline every prior governor's guards establish,
  informed by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320)."
  (:require [terminal.facts :as facts]
            [terminal.registry :as registry]
            [terminal.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Committing a petroleum receipt to a storage tank (the receipt becomes
  inventory under the tank's book-of-record) and transferring custody
  of a committed stock to the next custodian (pipeline batch out,
  tanker loading, refinery rundown handover) are the two real-world
  actuation events this actor performs -- a two-member set, matching
  every sibling's own dual-actuation shape."
  #{:storage/commit :custody/transfer})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:receipt/verify` (or `:storage/commit`/`:custody/transfer`)
  proposal with no spec-basis citation is a HARD violation -- never
  invent a jurisdiction's tank-overfill / tank-integrity / bonding-
  grounding requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:receipt/verify :storage/commit :custody/transfer} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:storage/commit`/`:custody/transfer`, the jurisdiction's required
  tank-inspection / overfill-prevention-system-test / bonding-grounding
  evidence must actually be satisfied -- do not trust the advisor's
  self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:storage/commit :custody/transfer} op)
    (let [w (store/terminal-stock st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction w) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(API 653タンク検査/過充填防止システム試験/結束・アース確認等)が充足していない状態での提案"}]))))

(defn- receipt-pod-chain-broken-violations
  "For `:storage/commit`, INDEPENDENTLY verify the prior pipeline batch's
  proof-of-delivery (POD) was confirmed before committing the receipt to
  inventory -- freight POD-chain discipline: a receipt whose upstream
  batch has no confirmed delivery cannot be honestly booked as terminal
  inventory."
  [{:keys [op subject]} st]
  (when (= op :storage/commit)
    (let [w (store/terminal-stock st subject)]
      (when (not (true? (:receipt-confirmed? w)))
        [{:rule :receipt-pod-chain-broken
          :detail (str subject " は上流パイプラインバッチのPOD(着荷確認)が未確認 -- 受領を在庫に計上する提案は進められない")}]))))

(defn- overfill-risk-violations
  "For `:storage/commit`, INDEPENDENTLY verify the planned receipt fits
  the tank's remaining ullage via `terminal.registry/overfill-risk?`
  (the fabrication measured-value-vs-rated-limit discipline) -- a
  Buncefield-type overfill. Evaluated UNCONDITIONALLY (every commit
  must fit the tank)."
  [{:keys [op subject]} st]
  (when (= op :storage/commit)
    (let [w (store/terminal-stock st subject)]
      (when (registry/overfill-risk?
             (:volume-barrels w)
             (:planned-receipt-barrels w)
             (:ullage-barrels w))
        [{:rule :overfill-risk
          :detail (str subject " は計画受領量(" (:planned-receipt-barrels w)
                      " bbl)が残余アレージ(" (:ullage-barrels w) " bbl)を超過 -- "
                      "Buncefield型過充填リスクのため在庫計上提案は進められない")}]))))

(defn- tank-integrity-assessment-stale-violations
  "For `:storage/commit`, INDEPENDENTLY verify the tank's API 653
  inspection interval is current -- a tank past its integrity-
  assessment interval must not receive a commit. Evaluated
  UNCONDITIONALLY."
  [{:keys [op subject]} st]
  (when (= op :storage/commit)
    (let [w (store/terminal-stock st subject)]
      (when (not (true? (:integrity-assessment-current? w)))
        [{:rule :tank-integrity-assessment-stale
          :detail (str subject " はAPI 653タンク健全性評価の有効期限が切れている -- 在庫計上提案は進められない")}]))))

(defn- bonding-grounding-unconfirmed-violations
  "For `:storage/commit`, INDEPENDENTLY verify bonding-and-grounding was
  confirmed before receipt/transfer -- static-electricity ignition
  control (a petroleum receipt into an ungrounded tank is an ignition
  hazard)."
  [{:keys [op subject]} st]
  (when (= op :storage/commit)
    (let [w (store/terminal-stock st subject)]
      (when (not (true? (:bonding-grounding-confirmed? w)))
        [{:rule :bonding-grounding-unconfirmed
          :detail (str subject " は結束・アース(bonding/grounding)が未確認 -- 静電気着火リスクのため在庫計上提案は進められない")}]))))

(defn- inbound-handoff-violations
  "HARD, but ONLY when the target tank actually carries a
  `:receipt/handoff`. Its ABSENCE is never a violation -- a pipeline
  receipt from a directly connected refinery has no carrier leg at all,
  and every tank predates this field.

  Two distinct failures, reported separately so the operator is told
  which one to fix:

    :inbound-handoff-malformed   -- not the `:handoff/*` wire shape
    :inbound-handoff-untraceable -- well-formed, but no
                                    `:handoff/carrier-tracking-ref`, so
                                    there is no way to say WHICH leg the
                                    receipt rests on. Same reasoning
                                    `cloud-itonami-isic-4920`'s own
                                    check 8 uses for the carrier side of
                                    this identical reference.

  Evaluated for the two acts that put the receipt beyond recall
  (`:receipt/verify` reads it, `:storage/commit` books it) -- there is
  no point holding a `:tank/intake` over the attribution of a delivery
  nobody has claimed yet."
  [{:keys [op subject]} st]
  (when (contains? #{:receipt/verify :storage/commit} op)
    (let [w (store/terminal-stock st subject)
          handoff (:receipt/handoff w)]
      (when (some? handoff)
        (cond
          (not (facts/handoff-record-well-formed? handoff))
          [{:rule :inbound-handoff-malformed
            :detail (str subject " の:receipt/handoffが必須フィールド"
                         "(id/source-actor/batch-id/product-type-id/quantity-kg/dispatched-at-iso)"
                         "を欠く、または数量が正の数でない")}]

          (not (facts/inbound-handoff-traceable? handoff))
          [{:rule :inbound-handoff-untraceable
            :detail (str subject " の:receipt/handoffに:handoff/carrier-tracking-refが無い -- "
                         "どの輸送レグに基づく受領なのか特定できないため、"
                         "POD(着荷確認)の主張を検証できない")}])))))

(defn- inbound-carrier-unknown-escalation
  "SOFT -- the handoff is well-formed and traceable, but names a carrier
  outside `terminal.facts/inbound-carrier-actors`. Escalates, never
  holds."
  [{:keys [op subject]} st]
  (when (contains? #{:receipt/verify :storage/commit} op)
    (let [w (store/terminal-stock st subject)
          handoff (:receipt/handoff w)]
      (when (and (some? handoff)
                 (facts/handoff-record-well-formed? handoff)
                 (facts/inbound-handoff-traceable? handoff)
                 (not (facts/inbound-carrier-known?
                       (:handoff/carrier-actor handoff))))
        [{:rule :inbound-carrier-unknown
          :detail (str subject " の受領は未登録の運送事業者 "
                       (pr-str (:handoff/carrier-actor handoff))
                       " に帰属している -- 拒否はしないが、在庫計上の前に人が見るべき")}]))))

(defn- already-commit-violations
  "For `:storage/commit`, refuses to commit the SAME tank twice, off a
  dedicated `:committed?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :storage/commit)
    (when (store/terminal-stock-already-committed? st subject)
      [{:rule :already-commit
        :detail (str subject " は既に在庫計上済み")}])))

(defn- already-transfer-violations
  "For `:custody/transfer`, refuses to transfer custody of the SAME tank
  twice, off a dedicated `:custody-transferred?` fact (never a `:status`
  value)."
  [{:keys [op subject]} st]
  (when (= op :custody/transfer)
    (when (store/terminal-stock-already-transferred? st subject)
      [{:rule :already-transfer
        :detail (str subject " は既に引渡済み")}])))

(defn- handoff-malformed-violations
  "HARD, but ONLY when a :handoff map is actually present under the
  proposal's :value -- its ABSENCE is never itself a violation, since
  attaching a :handoff to a :custody/transfer proposal is entirely
  optional (per ADR-2800002100)."
  [{:keys [op]} proposal]
  (when (= op :custody/transfer)
    (let [handoff (get-in proposal [:value :handoff])]
      (when (and (some? handoff) (not (facts/handoff-record-well-formed? handoff)))
        [{:rule :handoff-malformed
          :detail "custody/transfer提案に添付された:handoffレコードが必須フィールド(id/source-actor/batch-id/product-type-id/quantity-kg/dispatched-at-iso)を欠く、または数量が正の数でない"}]))))

(defn check
  "Censors a TerminalAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :soft-violations [..]
  :confidence c :escalate? bool :high-stakes? bool :hard? bool}.

  `:soft-violations` is an additional, independently-detected concern
  that is NOT grounds for a hold but DOES force `:escalate?` true even
  when every hard check passes -- same effect as low confidence, kept in
  a separate key so `:violations` keeps its original meaning."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (receipt-pod-chain-broken-violations request st)
                           (overfill-risk-violations request st)
                           (tank-integrity-assessment-stale-violations request st)
                           (bonding-grounding-unconfirmed-violations request st)
                           (already-commit-violations request st)
                           (already-transfer-violations request st)
                           (handoff-malformed-violations request proposal)
                           (inbound-handoff-violations request st)))
        soft (into [] (inbound-carrier-unknown-escalation request st))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))
        soft? (boolean (seq soft))]
    {:ok?             (and (not hard?) (not low?) (not stakes?) (not soft?))
     :violations      hard
     :soft-violations soft
     :confidence      conf
     :hard?           hard?
     :escalate?       (and (not hard?) (or low? stakes? soft?))
     :high-stakes?    stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
