# ADR-0002 — the receiving half: a receipt's POD gets an attribution

- **Status**: accepted
- **Date**: 2026-08-07
- **Extends**: ADR-2800000700 (carrier-actor / carrier-tracking-ref), ADR-2607177600 (the `:handoff/*` wire shape), ADR-2800002100 (this actor as sender)

## Context

This actor already had a handoff surface — but only the **sending** half. A
`:custody/transfer` proposal may attach a `:handoff` naming this terminal as
`:handoff/source-actor`, for a downstream custodian to pick up.

Nothing existed for the other direction, and that left a real hole.

HARD check 3, `receipt-pod-chain-broken`, refuses a `:storage/commit` whose
upstream proof-of-delivery is unconfirmed. It is a good check. But it reads
**one boolean**, `:receipt-confirmed?`, and that boolean answers *"was the
delivery confirmed?"* with a self-assertion that records nothing about **which
carrier, which leg, or which tracking reference** it rests on. A receipt becomes
inventory under the tank's book-of-record on the strength of an unattributed
flag, and an auditor asking "confirmed by whom, against what?" has nowhere to
look.

Meanwhile the fleet already had the machinery. `cloud-itonami-isic-4920`
independently logs a `:transport-leg/log` against a
`:handoff/carrier-tracking-ref` — a third-party confirmation that it physically
carried a leg (ADR-2800000700). The reference existed; this end just never
recorded which one it was relying on.

## Decision

**Add `:receipt/handoff` to the tank record** — the same `:handoff/*` wire
shape, naming who shipped it (`:handoff/source-actor`), who carried it
(`:handoff/carrier-actor`) and which leg (`:handoff/carrier-tracking-ref`).

It does **not** replace `:receipt-confirmed?` and does not make the POD check
stricter or looser. It makes the claim *checkable*.

**Two HARD checks, both firing only when a handoff is actually present:**

| rule | refuses |
|---|---|
| `:inbound-handoff-malformed` | not the `:handoff/*` wire shape |
| `:inbound-handoff-untraceable` | well-formed, but no `:handoff/carrier-tracking-ref` — no way to say *which* leg the receipt rests on |

The second one is the point of the ADR. An attribution you cannot follow is not
an attribution; it is decoration. This is the same reasoning 4920's own check 8
applies to the carrier side of the identical reference.

**One SOFT signal**: `:inbound-carrier-unknown` — well-formed and traceable, but
the carrier is not on this terminal's roster. This escalates and never holds. A
terminal cannot know every haulier in the world, so an unregistered one is not
grounds to refuse; but a receipt attributed to one is exactly the claim an
operator should look at before it becomes inventory. `governor/check` gains a
`:soft-violations` key for it — additive, so existing consumers reading
`:hard?` / `:escalate?` are unaffected. This mirrors `cloud-itonami-isic-5229`'s
`:storage-handoff-suspect`, which is this same actor-pair seen from the far end.

**Absence is never a violation.** Every tank predates this field, and a pipeline
receipt from a directly connected refinery has no carrier leg at all.

**Scope**: `:receipt/verify` and `:storage/commit` only. There is no point
holding a `:tank/intake` over the attribution of a delivery nobody has claimed
yet.

## The corroboration is by reference, never by call

This actor **never queries 4920**. Zero shared code, zero shared store, zero API
call — the same asymmetric-optional design every cross-actor reference in this
fleet uses. Each side validates its own half:

- **4920's half**: did the leg really happen? Answered on 4920's ledger, against
  its own measured transport conditions.
- **this actor's half**: is the attribution it was handed well-formed and
  followable?

Neither side can verify the other's half, and neither pretends to.

## Verification

- **54 tests / 275 assertions, 0 failures** (was 41/218). The pre-existing 41
  still pass; only the demo-set membership assertion moved (6 tanks → 9).
- **Mutation-tested.** Seven independent breaks, each red, green on revert:

  | break | result |
  |---|---|
  | untraceable check disabled | 4 failures |
  | malformed check disabled | 3 failures |
  | unknown-carrier soft signal disabled | 3 failures |
  | carrier roster accepts anything | 5 failures |
  | `inbound-handoff-traceable?` always true | 7 failures |
  | Datomic field spec drops the blob | 7 failures |
  | blob stored without `ls/enc` | 2 errors |

- **The Datomic backend carries the field.** `:receipt/handoff` is a nested map,
  so the hand-rolled codec gained blob support and `store_contract_test` asserts
  the round trip on **both** backends, field for field, including that a keyword
  `:handoff/product-type-id` survives as a keyword.

  This is not incidental. A governor check that reads a field one backend
  silently drops is a check that stops firing when the SSoT is swapped, and
  nothing says so — the SSoT becomes a switch that turns a compliance check off.
  That exact bug was found live in `cloud-itonami-isic-5229` on 2026-08-06,
  where `:shipment/handoff` was missing from its field spec and had made
  ADR-2800002100's gate dead on Datomic since the day it was written.

## Honest limits

- **This does not verify the delivery happened.** It verifies that the claim
  about the delivery names a carrier and a leg. Whether that leg exists is
  4920's half, on 4920's ledger, and reaching it is a human's walk — see the
  営み OS `:carry` edge, not an automatic call.
- **The carrier roster has one member.** `cloud-itonami-isic-4920` is the only
  carrier actor this fleet currently has. The roster is a set precisely so that
  adding the second one is a one-line change, but today "registered carrier"
  means exactly one company.
- **`:receipt-confirmed?` is still a self-asserted boolean.** Nothing here makes
  it derive from the handoff. Wiring the boolean itself to a corroborated leg
  would change check 3's meaning and is a separate decision; this ADR only
  ensures that when someone asserts it, the assertion carries an address.
- **No quantity reconciliation.** `:handoff/quantity-kg` is not compared against
  `:planned-receipt-barrels`. Crude oil mass-to-volume depends on grade and
  temperature, and inventing a conversion here would produce a check that is
  precise and wrong.
