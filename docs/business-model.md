# Business Model: Community Terminal / Depot Petroleum Storage

## Classification
- Repository: `cloud-itonami-isic-5210`
- ISIC Rev.5: `5210` — storage (terminal / depot)
- Domain: `midstream/terminal-storage`
- Social impact: depot safety, environmental protection, transparency
- Governor: `:terminal-storage-governor`
- License: AGPL-3.0-or-later

## Scope
This actor covers tank intake through per-jurisdiction tank-overfill /
tank-integrity / bonding-grounding regulatory assessment, storage commit
(committing a confirmed petroleum receipt to a storage tank, so the receipt
becomes inventory under the tank's book-of-record), and custody transfer
(handing a committed stock to the next custodian -- pipeline batch out, tanker
loading, or refinery rundown handover) for a community terminal or depot
operator. It does **not**, by itself, hold any operating authority required to
run a terminal-storage business in a given jurisdiction, perform the actual
physical tank-gauging or valve operation, or judge terminal logistics
scheduling (tank-blending and dispatch optimization is a follow-up slice, not
this R0). Whoever deploys a live instance supplies the jurisdiction-specific
operating authority, the real tank-gauging / valve-robot and SCADA /
custody-transfer integrations, and bears that jurisdiction's liability -- the
software supplies the governed, spec-cited, audited execution scaffold so the
operator does not have to build the compliance layer from scratch.

## Customer
- regional and community terminal and depot operators
- independent tank-farm operators leaving closed SCADA / tank-farm-AI SaaS
- pipeline-terminal interface operators running community depots
- custodians and regulators who need an auditable, spec-cited tank record

## Offer
- tank intake and directory management
- per-jurisdiction tank-overfill / tank-integrity / bonding-grounding regulatory
  assessment with an official spec-basis citation
- storage commit gated on full evidence, a confirmed receipt POD chain, a
  receipt within ullage, a current API 653 integrity assessment and confirmed
  bonding-grounding
- custody transfer with double-transfer prevention
- evidence checklisting (tank inspection record, overfill-prevention system
  test, bonding-grounding confirmation)
- exception workflows
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per operator / terminal
- support retainer with SLA
- SCADA and custody-transfer integration

## The `:terminal-storage-governor` Decision Rule

This blueprint's `:itonami.blueprint/governor` is `:terminal-storage-governor`.
It is the single authority that stands between "a receipt could be committed
to a tank" and "a receipt is allowed to be committed," and between "custody
could be transferred" and "it is allowed to transfer." Every rule it enforces
is traceable to the domain (Community Terminal / Depot Petroleum Storage, ISIC
5210) and to the three `:social-impact` tags in `blueprint.edn` (`:safety`,
`:environmental-protection`, `:transparency`).

This is the rule the companion contract test (`test/terminal/governor_contract_
test.clj`) encodes end-to-end: the TerminalAdvisor never commits storage or
transfers custody of a tank the Terminal Storage Governor would reject,
`:storage/commit` and `:custody/transfer` NEVER auto-commit at any phase,
`:tank/intake` (no direct capital risk) MAY auto-commit when clean, and every
decision (commit OR hold) leaves exactly one ledger fact.

**Authorizes a storage commit (`:storage/commit`) or custody transfer
(`:custody/transfer`) only when ALL of the following hold:**

1. **An official spec-basis citation exists for the jurisdiction** -- the
   governor will not authorize any `:receipt/verify`, `:storage/commit`, or
   `:custody/transfer` proposal whose jurisdiction has no entry in the
   `terminal.facts` catalog (`:no-spec-basis`). This is the direct enforcement
   of `:transparency`: a jurisdiction whose tank-overfill / tank-integrity /
   bonding-grounding requirements cannot be traced to an OFFICIAL public source
   is never guessed. The advisor must not fabricate a jurisdiction's
   requirements.
2. **The jurisdiction's required evidence is fully on file** -- for a commit or
   transfer the tank's jurisdiction must have been assessed with a complete
   tank-overfill / tank-integrity / bonding-grounding evidence checklist on
   record: the tank inspection record (API 653 equivalent), the overfill-
   prevention system test, and the bonding-grounding confirmation
   (`:evidence-incomplete`). This protects `:safety` and `:environmental-
   protection`: a tank that cannot prove overfill-control and integrity
   readiness never receives a commit.
3. **The prior pipeline batch's proof-of-delivery (POD) is confirmed** -- the
   governor INDEPENDENTLY re-verifies the tank's own `:receipt-confirmed?` flag
   before committing the receipt to inventory: a receipt whose upstream batch
   has no confirmed delivery cannot be honestly booked as terminal inventory
   (`:receipt-pod-chain-broken`, the freight POD-chain discipline).
4. **The planned receipt fits the tank's remaining ullage** -- the governor
   INDEPENDENTLY re-verifies the planned receipt against the tank's remaining
   ullage via the pure function `terminal.registry/overfill-risk?` (the
   fabrication measured-value-vs-rated-limit discipline, applied to tank
   overfill). A receipt larger than the remaining ullage is the Buncefield-type
   overfill event API Standard 2350 exists to prevent (`:overfill-risk`).
5. **The tank's API 653 integrity assessment is current** -- the governor
   INDEPENDENTLY re-verifies the tank's `:integrity-assessment-current?` flag
   (API 653 inspection interval) before a commit. A tank past its integrity-
   assessment interval must not receive a commit (`:tank-integrity-assessment-
   stale`).
6. **Bonding-and-grounding is confirmed** -- the governor INDEPENDENTLY
   re-verifies the tank's `:bonding-grounding-confirmed?` flag before a commit.
   A petroleum receipt into an ungrounded tank is a static-electricity ignition
   hazard (`:bonding-grounding-unconfirmed`).
7. **The tank has not already been committed, and custody has not already been
   transferred** -- a double commit of the same tank is refused off a dedicated
   `:committed?` fact, and a double transfer off a dedicated
   `:custody-transferred?` fact (never a `:status` value), the double-actuation
   guard every sibling actor in this fleet enforces (`:already-commit` /
   `:already-transfer`).

**Rejects (HOLD, un-overridable, never even reaches a human) when any of the
above fail.** A proposal with no spec-basis, incomplete evidence, an unconfirmed
receipt POD chain, a planned receipt that exceeds the ullage, a stale API 653
integrity assessment, unconfirmed bonding-grounding, or a double commit /
transfer is held at the governor node -- a human approver cannot override
these, by construction.

**Always escalates to a human (never auto-commits) for `:storage/commit` and
`:custody/transfer`**, even when every check above is clean. Committing a
petroleum receipt to a storage tank (the receipt becomes inventory under the
tank's book-of-record) and transferring custody to the next custodian (real
volume / real custody moving between terminal and next custodian) are the two
real-world actuation events this actor performs; both are always a human depot
/ terminal superintendent's call. This is enforced by TWO independent layers
that agree on purpose: the governor's confidence / actuation SOFT gate (a
`:storage/commit` / `:custody/transfer` stake always escalates) and
`terminal.phase`'s phase table, which never puts either op in any phase's
`:auto` set. The `:environmental-protection` tag is enforced upstream of the
governor, in the receipt-verification evidence step -- the governor's job is
commit/transfer authorization integrity, not terminal-logistics optimization.

## Required Technologies

`blueprint.edn`'s `:itonami.blueprint/required-technologies` for this business,
and what each one is actually load-bearing for here (not a generic capability
list):

| Technology | What it is FOR in Community Terminal / Depot Petroleum Storage |
|---|---|
| `:robotics` | The autonomous tank-gauging / valve robot that performs the physical custody transfer (and the receipt manifold operation). The governor never dispatches hardware itself: a commit- or transfer-clearing action must have cleared the same sign-off a human depot superintendent would need (see Robotics Premise). |
| `:identity` | Terminal-operator, depot-superintendent, and custodian identity plus role-based access, so the governor's sign-off is tied to *who* authorized a commit or transfer, not just *that* someone did. |
| `:forms` | Structured intake for tank booking, per-jurisdiction evidence capture (tank inspection record, overfill-prevention system test, bonding-grounding confirmation), and exception submission -- the data the Decision Rule above actually evaluates comes in through these forms. |
| `:dmn` | Encodes the `:terminal-storage-governor` Decision Rule itself (spec-basis, evidence completeness, the receipt-POD-chain, overfill, integrity, bonding-grounding checks, the double-actuation guards, the actuation gate) as an evaluable decision table rather than code buried in application logic -- this is what makes the governor auditable and swappable per-deployment. |
| `:bpmn` | Orchestrates the intake -> verify -> commit -> transfer -> audit loop end-to-end (see `docs/operator-guide.md`) across tank intake, receipt verification, storage commit, and custody transfer, including the exception escalation gate. |
| `:audit-ledger` | The immutable record of every verification, commit, transfer, exception, and hold -- this is what "an auditable, spec-cited tank record for every commit and transfer" (Trust Controls, below) actually means in practice, and the evidence an operator needs if a commit or transfer is later disputed by a custodian or regulator. |
| `:optimization` | Tank-blending and dispatch optimization -- selects the dispatch schedule for a terminal. This R0 build deliberately scopes optimization OUT (see README `Business-process coverage`); the capability is correctly marked required, the integration is a follow-up slice. |

There is NO bespoke `:petroleum-terminal` capability library in this stack
(unlike the freight sibling's `:logistics`): the tank-overfill check (planned
receipt vs remaining ullage) is a self-contained pure function in
`terminal.registry`, on top of the generic robotics/identity/forms/dmn/bpmn/
audit-ledger stack (see Capability layer).

## Trust Controls
- a jurisdiction with no official spec-basis can never be verified, committed,
  or transferred against
- a commit never starts with incomplete tank-overfill / tank-integrity /
  bonding-grounding evidence
- a commit never starts with an unconfirmed receipt POD chain, a planned receipt
  past the ullage, a stale API 653 integrity assessment, or unconfirmed
  bonding-grounding
- the same tank can never be committed or transferred twice
- a commit or transfer never auto-commits; both always need a human depot /
  terminal superintendent
- every commit and transfer (commit OR hold) leaves exactly one immutable
  ledger fact
- tank-gauging and custody data stays outside Git

## Implementation notes (`:implemented`)

The Decision Rule above is implemented faithfully by `terminal.governor` as six
numbered HARD checks plus two double-actuation guards (a human approver cannot
override any of them) plus one SOFT gate:

- `spec-basis-violations` -- the spec-basis check above, evaluated on every
  `:receipt/verify`, `:storage/commit`, and `:custody/transfer`
  (`:no-spec-basis`).
- `evidence-incomplete-violations` -- the evidence-completeness check above,
  for `:storage/commit` / `:custody/transfer` (`:evidence-incomplete`).
- `receipt-pod-chain-broken-violations` -- the POD-chain check above, the
  freight POD-chain discipline reapplied to a terminal receipt; evaluated on
  every `:storage/commit` (`:receipt-pod-chain-broken`).
- `overfill-risk-violations` -- the ullage check above, an honest reapplication
  of the fabrication measured-value-vs-rated-limit discipline to tank overfill;
  evaluated unconditionally on every `:storage/commit` (`:overfill-risk`).
- `tank-integrity-assessment-stale-violations` -- the API 653 inspection-
  interval check above; evaluated unconditionally on every `:storage/commit`
  (`:tank-integrity-assessment-stale`).
- `bonding-grounding-unconfirmed-violations` -- the bonding-grounding check
  above (static-electricity ignition control); evaluated on every
  `:storage/commit` (`:bonding-grounding-unconfirmed`).
- `already-commit-violations` / `already-transfer-violations` -- the double-
  actuation guards above, off dedicated `:committed?` / `:custody-transferred?`
  booleans (never a `:status` value), the same discipline every sibling
  governor's guards establish (`:already-commit` / `:already-transfer`).
- the confidence floor / actuation SOFT gate -- low confidence, OR a
  `:storage/commit` / `:custody/transfer` stake, escalates to a human; and
  `terminal.phase` independently never auto-commits either op at any phase.

`:storage/commit` and `:custody/transfer` are the two real-world actuation
events (`#{:storage/commit :custody/transfer}`), applied SEQUENTIALLY to the
SAME tank (commit first, transfer later) rather than the retail sibling's
`:kind`-distinguished alternative-action shape -- the same sequential
dual-actuation shape the repair-shop and quarrying clusters use. Neither ever
auto-commits at any phase. Tank-blending and dispatch optimization (the
`:optimization` line above) is a follow-up slice, not in this R0 build -- see
README `Business-process coverage`.

## Capability layer

Unlike `cloud-itonami-isic-4920` (which wraps a pre-existing bespoke
capability library `kotoba-lang/logistics`), this vertical is SELF-CONTAINED:
there is no `kotoba-lang/petroleum-terminal` to delegate tank-overfill /
tank-integrity / bonding-grounding validation to. The overfill check (planned
receipt vs remaining ullage) lives as a pure function in `terminal.registry`
and is re-verified independently by the governor, rather than wrapping an
external capability library's own validated function -- the same 'reuse a
capability's own validated function' discipline, here applied to this
vertical's OWN pure registry functions.

## Jurisdiction coverage (honest)

`terminal.facts/catalog` currently seeds 4 jurisdictions with an official
spec-basis, each a REAL regime: Japan (消防庁 Fire and Hazardous-Materials
agency and 経済産業省 METI, under the 消防法 dangerous-substances rules and the
石油コンビナート等災害防止法 Petroleum Complex Disaster Prevention Act), the
United States (API Standard 2350 overfill prevention and API 653 tank
inspection, with EPA / state fire marshals), the United Kingdom (HSE and the
Environment Agency under the COMAH Regulations 2015, the post-Buncefield
control-of-major-accident-hazards regime), and Norway (the Petroleum Safety
Authority's Facilities and Framework Regulations). The API 653 tank-inspection
interval and the Buncefield-type overfill event are the two physical facts the
governor's tank-integrity-assessment-stale and overfill-risk checks are
ultimately grounded in. This is a starting catalog to prove the governor
contract end-to-end, not a claim of global coverage (4 of ~194 jurisdictions
worldwide). Adding a jurisdiction is additive: one map entry in
`terminal.facts/catalog`, citing a real official source -- never fabricate a
jurisdiction's requirements to make coverage look bigger.

## Maturity

`:implemented` -- `TerminalAdvisor` + `Terminal Storage Governor` run as real,
tested code (`clojure -M:dev:test`: 36 tests / 181 assertions, 0 failures;
lint clean), promoted from the originally-published `:blueprint`-tier
scaffold, following the SAME governed-actor architecture as the other prior
actors across this fleet, with its own distinct, independently-named governor
and its own self-contained tank-overfill check. See
`docs/adr/0001-architecture.md` for the history and design.

## Robotics Premise

`blueprint.edn` sets `:itonami.blueprint/robotics true`. In this domain an
autonomous tank-gauging / valve robot performs the physical custody transfer
(and the receipt manifold operation), under the actor, gated by the
independent **Terminal Storage Governor**. The governor never dispatches
hardware itself: a commit- or transfer-clearing action must have cleared the
same sign-off a human depot superintendent would need. A robot may turn the
valve, but only after the governor (every HARD check clean) and a human
superintendent both agree it is safe to -- the same
operating-state-machine-gated-by-governor premise every cloud-itonami vertical
restates (ADR-2607011000): the blueprint declares `:robotics true`, the README
names the robot that performs the physical act, and the Terminal Storage
Governor is the independent gate that robot's command must pass.
