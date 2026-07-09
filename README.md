# cloud-itonami-isic-5210

Open Business Blueprint for **ISIC Rev.5 5210**: Storage (terminal /
depot) -- tank intake, per-jurisdiction tank-overfill / tank-integrity /
bonding-grounding regulatory assessment, storage commit, and custody
transfer for a terminal operator.

This repository publishes a terminal-storage actor -- tank intake,
per-jurisdiction terminal-storage regulatory assessment, storage commit
and custody transfer -- as an OSS business that any qualified operator
can fork, deploy, run, improve and sell, so a regional terminal or
depot operator never surrenders tank-gauging and custody-accounting
data to a closed SCADA / tank-farm-AI SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **TerminalAdvisor ⊣
Terminal Storage Governor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:terminal-storage-governor`,
is a UNIQUE keyword fleet-wide (grep-verified: no other blueprint
declares it) -- a fresh, independent build.

**Unlike `cloud-itonami-isic-4920` (which wraps a pre-existing bespoke
capability library `kotoba-lang/logistics`), this vertical is
SELF-CONTAINED**: there is no `kotoba-lang/petroleum-terminal` to
delegate tank-overfill / tank-integrity / bonding-grounding validation
to, so the overfill-risk check lives as a pure function in
`terminal.registry` and is re-verified independently by the governor,
rather than wrapping an external capability library's own validated
function.

> **Why an actor layer at all?** An LLM is great at drafting a tank
> summary, normalizing records, and reading a tank gauge -- but it has
> **no notion of which jurisdiction's tank-overfill / tank-integrity /
> bonding-grounding law is official, no license to commit a petroleum
> receipt to a storage tank or transfer custody to the next custodian,
> and no way to know on its own whether a planned receipt actually fits
> the tank's remaining ullage, whether the tank's API 653 inspection
> interval is actually current, whether the prior pipeline batch's
> proof-of-delivery was actually confirmed, or whether bonding-and-
> grounding was actually confirmed before receipt**. Letting it commit
> storage or transfer custody directly invites fabricated regulatory
> citations, a Buncefield-type overfill, a receipt booked against a
> broken POD chain, and a static-electricity ignition -- exposing the
> depot and the crew to a real tank-farm fire and the operator to real
> liability, for whoever runs it. This project seals the
> TerminalAdvisor into a single node and wraps it with an independent
> **Terminal Storage Governor**, a human **approval workflow**, and an
> immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers tank intake through tank-overfill / tank-integrity /
bonding-grounding regulatory assessment, storage commit and custody
transfer. It does **not**, by itself, hold any operating authority
required to run a terminal-storage business in a given jurisdiction,
and it does not claim to. It also does not perform the actual physical
tank-gauging / valve operation itself, or judge terminal logistics
scheduling -- tank-blending and dispatch optimization (the blueprint's
own `:optimization` technology) is a follow-up slice, not in this R0.
Whoever deploys and operates a live instance (a qualified depot /
terminal superintendent) supplies any jurisdiction-specific operating
authority, the real tank-gauging / valve-robot dispatch integration
and the real SCADA / custody-transfer integrations, and bears that
jurisdiction's liability -- the software supplies the governed,
spec-cited, audited execution scaffold so that operator does not have
to build the compliance layer from scratch.

### Actuation

**Committing a petroleum receipt to a storage tank and transferring
custody to the next custodian are never autonomous, at any phase, by
construction.** Two independent layers enforce this
(`terminal.governor`'s `:storage/commit`/`:custody/transfer` high-stakes
gate and `terminal.phase`'s phase table, which never puts either op in
any phase's `:auto` set) -- see `terminal.phase`'s docstring and
`test/terminal/phase_test.clj`'s `storage-commit-never-auto-at-any-
phase`/`custody-transfer-never-auto-at-any-phase`. The actor may draft,
check and recommend; a human depot / terminal superintendent is always
the one who actually commits a receipt to inventory or transfers
custody. Grounded in terminal-safety doctrine (the same discipline
every regulator in `terminal.facts` codifies: a real storage commit
and a real custody transfer are human sign-off acts) -- a genuine
DUAL-actuation shape, applied SEQUENTIALLY to the SAME tank (commit
first, transfer later), unlike `retailops`/4711's own `:kind`-
distinguished alternative-action shape.

## The core contract

```
tank intake + jurisdiction facts (terminal.facts, spec-cited)
        |
        v
   ┌───────────────────────┐   proposal      ┌───────────────────────┐
   │ TerminalAdvisor       │ ─────────────▶ │ Terminal Storage       │  (independent system)
   │ (sealed)              │  + citations    │ Governor spec-basis ·  │
   └───────────────────────┘                 │ evidence-incomplete ·  │
          │                 commit ◀┼ receipt-pod-chain-broken│
          │                         │ overfill-risk (NEW, ullage)│
    record + ledger        escalate ┼ tank-integrity-assessment-│
          │              (ALWAYS for│ stale (NEW, API 653) ·    │
          │       :storage/commit/  │ bonding-grounding-        │
          │       :custody/         │ unconfirmed (NEW) ·       │
          │       transfer)         │ already-commit ·          │
          ▼                          │ already-transfer          │
      human approval                 └───────────────────────┘
```

**The TerminalAdvisor never commits storage or transfers custody of a
tank the Terminal Storage Governor would reject, and never does so
without a human sign-off.** Hard violations (fabricated regulatory
requirements; unsupported evidence; an unconfirmed receipt POD chain;
a planned receipt that exceeds the tank's ullage; a stale API 653
integrity assessment; unconfirmed bonding-grounding; a double commit/
transfer) force **hold** and *cannot* be approved past; a clean commit/
transfer proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean commit + transfer lifecycle, plus seven HARD-hold cases, through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here an autonomous tank-gauging /
valve robot performs the physical custody transfer (and the receipt
manifold operation) under the actor, gated by the independent
**Terminal Storage Governor**. The governor never dispatches hardware
itself: a commit-clearing or transfer-clearing action must have cleared
the same sign-off a human depot superintendent would need. This
restates the fleet-wide robotics premise three ways (ADR-2607011000):
the blueprint declares `:robotics true`, the README names the robot
that performs the physical act, and the Terminal Storage Governor is
the independent gate that robot's command must pass -- a robot may turn
the valve, but only after the governor and a human superintendent both
agree it is safe to.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Terminal Storage Governor, commit/transfer draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`5210`). Unlike the freight sibling, this vertical is NOT backed by a
separate bespoke domain capability lib: the tank-overfill check
(planned receipt vs remaining ullage) is a self-contained pure function
in `terminal.registry`, on top of the generic
robotics/identity/forms/dmn/bpmn/audit-ledger stack.

## Layout

| File | Role |
|---|---|
| `src/terminal/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + commit AND transfer history (dual history). The double-actuation guard checks dedicated `:committed?`/`:custody-transferred?` booleans rather than a `:status` value |
| `src/terminal/registry.cljc` | Commit/transfer draft records, plus the self-contained tank-overfill range-check pure function (`overfill-risk?`) the governor re-verifies against -- no external capability library to delegate to |
| `src/terminal/facts.cljc` | Per-jurisdiction tank-overfill / tank-integrity / bonding-grounding catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/terminal/terminaladvisor.cljc` | **TerminalAdvisor** -- `mock-advisor` ‖ `llm-advisor`; intake/receipt-verification/commit/transfer proposals |
| `src/terminal/governor.cljc` | **Terminal Storage Governor** -- 6 HARD checks (spec-basis · evidence-incomplete · receipt-pod-chain-broken · overfill-risk, the fabrication value-vs-rated-limit discipline · tank-integrity-assessment-stale · bonding-grounding-unconfirmed) + 2 double-actuation guards + 1 soft (confidence/actuation gate) |
| `src/terminal/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (commit/transfer always human; tank intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/terminal/operation.cljc` | **OperationActor** -- langgraph StateGraph |
| `src/terminal/sim.cljc` | demo driver |
| `test/terminal/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers tank intake through tank-overfill / tank-integrity /
bonding-grounding regulatory assessment, storage commit and custody
transfer -- the core governed lifecycle:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Tank intake + per-jurisdiction evidence checklisting, HARD-gated on an official spec-basis citation (`:tank/intake`/`:receipt/verify`) | Real SCADA/tank-gauging-robot integration, tank-blending and dispatch optimization |
| Storage commit, HARD-gated on full evidence, a confirmed receipt POD chain, a planned receipt within ullage, a current API 653 integrity assessment, confirmed bonding-grounding, plus a double-commit guard (`:storage/commit`) | |
| Custody transfer, HARD-gated on full evidence and no double-transfer (`:custody/transfer`) | |
| Immutable audit ledger for every intake/verification/commit/transfer decision | |

Extending coverage is additive: add the next gate (e.g. a vapor-recovery
or tank-routing check) as its own governed op with its own HARD checks
and tests, following the SAME "an independent governor re-verifies
against the actor's own records before any real-world act" pattern this
repo's flagship ops already establish.

## Jurisdiction coverage (honest)

`terminal.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `terminal.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, NOR) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `terminal.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `TerminalAdvisor` + `Terminal Storage Governor` run
as real, tested code (see `Run` above), promoted from the
originally-published `:blueprint`-tier scaffold, following the SAME
governed-actor architecture as the other prior actors across this
fleet, with its own distinct, independently-named governor and its own
self-contained tank-overfill check. See `docs/adr/0001-architecture.md`
for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
