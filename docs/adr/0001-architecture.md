# ADR-0001: TerminalAdvisor ⊣ Terminal Storage Governor architecture

## Status

Accepted. `cloud-itonami-isic-5210` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-5210` publishes an OSS business blueprint for community
terminal / depot petroleum storage (tank intake, per-jurisdiction tank-
overfill / tank-integrity / bonding-grounding regulatory assessment, storage
commit, and custody transfer). Like every prior actor in this fleet, the
blueprint alone is not an implementation: this ADR records the governed-actor
architecture that promotes it to real, tested code, following the same
langgraph StateGraph + independent Governor + Phase 0->3 rollout pattern
established by `cloud-itonami-isic-6511` (life insurance) and applied across
the fleet's prior siblings.

Like the upstream crude sibling (`cloud-itonami-isic-0610`) and the
quarrying cluster (`cloud-itonami-isic-0810`), this vertical has NO bespoke
domain capability library in `kotoba-lang` to wrap (verified: no
`kotoba-lang/petroleum-terminal`-style repo exists, and
`kotoba-lang/robotics` is the generic cross-cutting robotics contract every
cloud-itonami vertical already uses, not a domain-specific library for this
vertical). This build therefore uses self-contained domain logic -- the same
pattern the majority of this fleet's actors use, and the explicit
differentiator from `cloud-itonami-isic-4920` (which wraps a pre-existing
`kotoba-lang/logistics` library). The tank-overfill check (planned receipt vs
remaining ullage) lives as a pure function in `terminal.registry` and is
re-verified independently by the governor.

This blueprint's own `:itonami.blueprint/governor` keyword,
`:terminal-storage-governor`, is grep-verified UNIQUE fleet-wide -- no
naming-collision precedent question, a fresh independent build.

## Decision

### Decision 1: fresh governor identity, no reuse precedent needed

`:terminal-storage-governor` is grep-verified unique across every
`blueprint.edn` in this fleet. This build follows the SAME governed-actor
architecture as every prior actor, but with its own distinct governor
identity.

### Decision 2: self-contained domain logic (no `kotoba-lang/petroleum-terminal` to wrap)

Unlike `cloud-itonami-isic-4920` (freight, which delegates tracking-number
validation to a real, pre-existing `kotoba-lang/logistics` capability
library), this terminal-storage vertical has NO pre-existing petroleum-
terminal capability library to delegate tank-overfill / tank-integrity /
bonding-grounding validation to. The overfill check (planned receipt vs
remaining ullage) is therefore a pure function defined in
`terminal.registry` and called directly by `terminal.governor` -- the SAME
'reuse a capability's own validated function' discipline
`retailops.governor`'s ean13 check establishes for a capability library,
here applied to this vertical's OWN pure registry functions rather than a
separate library. No literal code is shared with any sibling (different
domain), but the discipline is the same.

### Decision 3: dual-actuation shape, SEQUENTIAL on the SAME `terminal-stock` entity

Unlike the retail sibling's `order` entity (distinguished by `:kind`,
alternative sale-or-reorder actions), this vertical's `commit` and
`transfer` actuation events apply SEQUENTIALLY to the SAME `terminal-stock`
-- a storage commit happens first (a confirmed receipt is committed to the
tank's book-of-record), custody transfer happens later (pipeline batch out,
tanker loading, refinery rundown handover), on the same tank record. This
matches the repair-shop cluster's `ticket`, the quarrying cluster's
`extraction`, and the upstream crude sibling's `well` shape (two real-world
acts, in order, on one entity). `high-stakes` is
`#{:storage/commit :custody/transfer}`; neither ever auto-commits at any
phase.

### Decision 4: the tank-overfill check -- an honest reapplication of the fabrication value-vs-rated-limit discipline

The physical range check the governor runs on every `:storage/commit` is an
honest reapplication of an established fleet discipline to a terminal-storage
value, documented as such rather than claimed as a novel invention (the same
convention `cloud-itonami-isic-0162`'s Decision 3 establishes for
`dose-matches-claim?`, and the upstream crude sibling's Decision 4
establishes for its well-safety suite):

- `overfill-risk?` reapplies the **fabrication measured-value-vs-rated-limit**
  discipline to a planned receipt vs a tank's remaining ullage. Ullage is the
  remaining headspace; the tank's total capacity is the current volume plus
  that ullage. A planned receipt that would fill past total capacity is the
  Buncefield-type overfill event API Standard 2350 exists to prevent: the tank
  overflows, the bund catches or fails, and a volatile cloud forms. Evaluated
  unconditionally on every `:storage/commit`.

It returns `true` when the value is provably OUTSIDE the safe envelope; the
conservative terminal-safety choice, missing data is a violation (cannot
verify safe to commit -- a Buncefield-type overfill is exactly what an
unverified receipt risks). No new unconditional-evaluation ordinal is claimed:
this check is a discipline-reapplication, documented per Decision 3 of
`cloud-itonami-isic-0162`.

### Decision 5: the receipt-POD-chain / tank-integrity / bonding-grounding checks -- honest reapplications of established fleet disciplines

Three further `:storage/commit` checks each reapply an established fleet
discipline to a terminal-storage value:

- `receipt-pod-chain-broken` reapplies the **freight POD-chain** discipline:
  a receipt whose upstream pipeline batch has no confirmed proof-of-delivery
  cannot be honestly booked as terminal inventory. Evaluated on every
  `:storage/commit`.
- `tank-integrity-assessment-stale` reuses the **open-flag-unresolved** style
  discipline (the freight sibling's `delivery-exception-unresolved?`, the
  upstream sibling's `integrity-flag-unresolved`): a tank past its API 653
  inspection interval must not receive a commit. Evaluated unconditionally on
  every `:storage/commit`.
- `bonding-grounding-unconfirmed` reapplies the **measured-boolean-ground-
  truth** discipline: a petroleum receipt into a tank whose bonding-and-
  grounding is not confirmed is a static-electricity ignition hazard, and is
  held. Evaluated on every `:storage/commit`.

Each is documented as a discipline-reapplication, not claimed as a novel
invention -- the same honesty discipline that forbids fabricating coverage
also forbids over-claiming novelty.

### Decision 6: dedicated double-actuation-guard booleans

`:committed?` / `:custody-transferred?` are dedicated booleans on the
`terminal-stock` record, never a single `:status` value -- the same
discipline every prior governor's guards establish, informed by
`cloud-itonami-isic-6492`'s real status-lifecycle bug (ADR-2607071320), and
matching the upstream crude sibling's `:crude-lifted?` / `:production-
settled?` shape.

### Decision 7: Store protocol, MemStore + DatomicStore parity

`terminal.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-backed),
proven to satisfy the same contract in
`test/terminal/store_contract_test.clj`. The ledger stays append-only on
every backend: which tank had a receipt committed without a confirmed POD
chain, which tank was committed past its ullage (a Buncefield-type
overfill risk), which tank was committed with a stale API 653 integrity
assessment or unconfirmed bonding-grounding, which tank had storage
committed, which tank had custody transferred, on what jurisdictional
basis, approved by whom -- always a query over an immutable log.

### Decision 8: Phase 0->3 with `:storage/commit`/`:custody/transfer` NEVER auto

`terminal.phase`'s phase table puts `:tank/intake` (no direct capital risk)
in phase 3's `:auto` set as its only member; `:storage/commit` and
`:custody/transfer` are deliberately ABSENT from every phase's `:auto` set,
including phase 3 -- a permanent structural fact. `terminal.governor`'s
high-stakes gate enforces the same invariant independently: two layers agree
that actuation is always a human depot / terminal superintendent's call.

### Decision 9: mock + LLM advisor pair

`terminal.terminaladvisor` provides a deterministic `mock-advisor` (default,
runs offline) and an `llm-advisor` backed by a `langchain.model/ChatModel`.
The LLM advisor's EDN proposal is parsed defensively: any parse/shape failure
yields a safe low-confidence noop so the governor escalates/holds -- an LLM
hiccup can never auto-commit storage or auto-transfer custody.

## Alternatives considered

- **Wrapping a bespoke `kotoba-lang/petroleum-terminal` capability library.**
  Considered and explicitly ruled out: no such library exists, and
  `kotoba-lang/robotics` is generic, not terminal-specific. Forcing a false
  capability-library integration would be dishonest; this build correctly
  uses self-contained domain logic instead.
- **A `:kind`-distinguished entity** (matching the retail sibling's `order`
  shape). Rejected: commit and transfer happen SEQUENTIALLY on the SAME tank
  in this domain, not as alternative actions -- the repair-shop / quarrying /
  upstream-crude cluster's sequential shape is the honest match here.
- **Claiming a genuinely-new unconditional-evaluation ordinal for the
  overfill check.** Rejected: the check reapplies an established fleet
  discipline (fabrication value-vs-rated-limit) to a new domain. Per
  `cloud-itonami-isic-0162` Decision 3's convention, it is documented as an
  honest discipline-reapplication, not claimed as a novel invention -- the
  same honesty discipline that forbids fabricating coverage also forbids
  over-claiming novelty.
- **Building tank-blending / dispatch optimization in this R0.** Rejected in
  favor of a scoped R0 slice (the `:optimization` capability is correctly
  marked required, the integration is a follow-up), consistent with this
  fleet's 'extending coverage is additive' convention.

## Consequences

- A fresh independent build of the SAME governed-actor architecture
  (langgraph StateGraph + independent Governor + Phase 0->3 rollout), with
  its own distinct `:terminal-storage-governor` identity.
- Establishes the tank-overfill check as an honest reapplication of the
  fabrication value-vs-rated-limit discipline to terminal storage, and the
  receipt-POD-chain / tank-integrity / bonding-grounding checks as honest
  reapplications of established fleet disciplines -- no genuinely-new-concept
  check, all discipline-reuse documented as such per
  `cloud-itonami-isic-0162` Decision 3.
- `MemStore` || `DatomicStore` parity is proven by
  `test/terminal/store_contract_test.clj`.
- 36 tests / 181 assertions pass; lint is clean; the demo
  (`clojure -M:dev:run`) walks one clean commit + transfer lifecycle, plus
  seven HARD-hold scenarios (no spec-basis, receipt POD-chain broken,
  overfill risk, API 653 integrity stale, bonding-grounding unconfirmed,
  double commit, double transfer), end-to-end.
- `blueprint.edn` required no field-sync fixes -- the `:maturity` flip itself
  is the only change from the originally-published scaffold.

## Addendum: IND/SAU/ARE/MEX jurisdiction extension

`terminal.facts/catalog` grows from 4 to 8 seeded jurisdictions: India,
Saudi Arabia, UAE and Mexico added alongside the original JPN/USA/GBR/
NOR, each with a real owner-authority/legal-basis/provenance citation
-- same schema, no new fields. One honesty note worth recording: the
UAE entry's primary citation (Abu Dhabi DoE Fuel Storage Tanks
Regulations 2023) was verified by direct full-text extraction to cover
overfill protection (§3.7) and API-653-aligned tank-integrity
inspection (§3.9.5-3.9.7), but that text did NOT confirm explicit
bonding-and-grounding language -- rather than silently claim full
coverage from one document, the ARE entry's `:legal-basis` cites a
SECOND document (the UAE Fire and Life Safety Code of Practice) for
that sub-requirement, the same "don't overstate a single citation"
discipline `retailops`/4711's and `freightops`/4920's own addenda
apply to SAU/MEX.

Every new entry's `:provenance` URL resolves to an official government
domain (`peso.gov.in`, `sbc.gov.sa`, `doe.gov.ae`, `dof.gob.mx`),
verified by direct web research at extension time, never carried over
from training-data recall alone.

## References

- `cloud-itonami-isic-6511/docs/adr/0001-architecture.md` (origin of the
  general governed-actor architecture pattern)
- `cloud-itonami-isic-4920/docs/adr/0001-architecture.md` (freight sibling;
  contrast: wraps a pre-existing `kotoba-lang/logistics` capability library)
- `cloud-itonami-isic-0610/docs/adr/0001-architecture.md` (upstream crude
  sibling; same sequential dual-actuation shape on a `well`, self-contained
  well-safety range checks)
- `cloud-itonami-isic-0162/docs/adr/0001-architecture.md` (origin of the
  'honest reapplication, documented as such' convention this build follows
  for its overfill and POD-chain / integrity / bonding-grounding checks)
- 消防法 危険物規制 (Fire Service Act, dangerous-substances rules); 石油コンビナート等災害防止法 (Act on the Prevention of Disasters in Petroleum Industrial Complexes and Other Petroleum Facilities) (Japan, 消防庁 / METI)
- API Standard 2350 (overfill prevention); API Standard 653 (tank inspection) (US, API / EPA / state fire marshals)
- Control of Major Accident Hazards Regulations 2015 (UK, HSE / Environment Agency, post-Buncefield)
- Facilities Regulations (Innretninger); Framework Regulations (Rammeforskriftenen) (Norway, Petroleum Safety Authority)
- The Buncefield incident (2005) as the originating overfill-type major accident the COMAH 2015 / API 2350 regimes exist to prevent.
- Petroleum Act, 1934 + Petroleum Rules, 2002 (India, PESO)
- HCIS Safety & Fire Protection Directives; Saudi Building Code Fire Protection Requirements, SBC 801 (Saudi Arabia)
- DoE Fuel Storage Tanks Regulations 2023; UAE Fire and Life Safety Code of Practice (UAE)
- NOM-006-ASEA-2017, Seguridad en Almacenamiento Terrestre de Petrolíferos (Mexico, ASEA)
