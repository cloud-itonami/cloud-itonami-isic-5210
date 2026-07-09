# Operator Guide

## First Deployment
1. Register operators, terminals, tanks, and depot superintendents.
2. Import tank, gauge, and custody history.
3. Seed the per-jurisdiction spec-basis catalog (`terminal.facts`) for the
   jurisdictions you actually operate in, citing real official sources only.
4. Run read-only spec-basis validation per jurisdiction.
5. Configure exception escalation and custody-transfer accounts.
6. Publish a dry-run transfer and audit export.

## Minimum Terminal Controls
- spec-basis validation before any verification, commit, or transfer
- full tank-overfill / tank-integrity / bonding-grounding evidence (tank
  inspection record, overfill-prevention system test, bonding-grounding
  confirmation) before any commit
- receipt-POD-chain, overfill/ullage, API 653 integrity and bonding-grounding
  checks before any commit
- exception escalation gate
- audit export for every commit, transfer, and hold
- backup manual commit and custody-transfer process

## A Day in the Life: Intake → Verify → Commit → Transfer → Audit

Community Terminal / Depot Petroleum Storage (ISIC 5210,
`cloud-itonami-isic-5210`) runs on the same intake / advise / govern / decide /
commit-or-hold loop as every itonami blueprint, but here the loop is concrete:
a regional terminal needs to bring a tank (say, an onshore depot tank T-101 in
a JPN-regulated terminal) from intake through receipt verification to a storage
commit and a custody transfer. Walking through one tank, end to end:

1. **Intake.** The operator books the tank through `:forms`: tank id, product
   grade, jurisdiction, and the tank's own physical record (current volume,
   tank level %, ullage / remaining headspace, planned receipt volume, whether
   the prior pipeline batch POD is confirmed, whether the API 653 integrity
   assessment is current, whether the gauge is verified, whether bonding-
   grounding is confirmed). This creates a terminal-stock record at
   `:tank/intake` status. The TerminalAdvisor only normalizes the patch; it
   does not invent the tank id, product grade, jurisdiction, or any physical
   value.
2. **Verify.** The TerminalAdvisor drafts a per-jurisdiction tank-overfill /
   tank-integrity / bonding-grounding evidence checklist (`:receipt/verify`)
   from `terminal.facts`, citing the jurisdiction's official spec-basis (owner
   authority, legal basis, provenance) and listing the required evidence (tank
   inspection record at an API 653-equivalent interval, overfill-prevention
   system test, bonding-grounding confirmation). The
   `:terminal-storage-governor` sign-off gate must clear: it checks the
   jurisdiction actually has an official spec-basis on file (never invent one).
   A jurisdiction with no spec-basis is a HARD hold at the governor node -- it
   never even reaches a human. This verification always escalates to a human
   for approval; it is never auto.
3. **Commit.** Before the receipt can be committed to inventory, the
   `:terminal-storage-governor` sign-off gate runs the full HARD check set
   against the tank's own ground truth: the spec-basis exists, the evidence
   checklist is complete, the prior pipeline batch POD is confirmed, the
   planned receipt fits the remaining ullage, the API 653 integrity assessment
   is current, bonding-grounding is confirmed, and the tank has not already
   been committed. Any failure is a HARD hold that a human cannot override. If
   every check is clean, the proposal STILL always escalates to a human depot
   superintendent -- a `:storage/commit` never auto-commits at any phase. On
   approval, the commit record is drafted (`<JURISDICTION>-COMMIT-000001`) and
   the tank's `:committed?` flag is set.
4. **Transfer.** Once the receipt has actually been committed to inventory,
   custody is transferred (`:custody/transfer`) to the next custodian (pipeline
   batch out, tanker loading, refinery rundown handover). The governor re-
   checks the spec-basis, the evidence completeness, and that this tank's
   custody has not already been transferred. As with the commit, a clean
   transfer STILL always escalates to a human depot superintendent --
   `:custody/transfer` never auto-commits. On approval the transfer record is
   drafted (`<JURISDICTION>-TRANSFER-000001`) and the tank's
   `:custody-transferred?` flag is set.
5. **Audit.** The verification, the commit sign-off, the commit record, the
   transfer sign-off, and the transfer record are all appended to the
   `:audit-ledger` -- immutable and exportable, so a custody or inventory
   dispute can be traced back to the exact spec-basis citation, evidence
   checklist, and superintendent sign-off that authorized the commit and
   transfer. If something is wrong with the tank (an overfill near-miss, an
   integrity concern, a bonding-grounding failure), that gets raised as an
   exception and routed through the escalation gate instead of being silently
   suppressed -- a commit for that tank then waits on governor sign-off of the
   exception's resolution.

Any deviation from this loop is exactly what the Trust Controls in
`docs/business-model.md` exist to catch: a tank verified against a fabricated
spec-basis, a commit started with incomplete evidence or past the ullage, an
exception suppressed to force a commit through, or a transfer posted without a
human sign-off.

## Feel the Decision Gate: `clojure -M:dev:run`

This vertical has no companion playable prototype yet (unlike the freight
sibling's `itonami/freight-dispatch` game). The fastest hands-on way to feel
why the `:terminal-storage-governor` gate exists is the bundled demo, which
walks one clean tank through intake → verify → commit → transfer (each
commit/transfer pausing for human approval) and then exercises every HARD-hold
failure mode in isolation:

- a jurisdiction with no official spec-basis → HOLD (`:no-spec-basis`),
- an unconfirmed receipt POD chain → HOLD (`:receipt-pod-chain-broken`),
- a planned receipt that exceeds the tank's ullage → HOLD (`:overfill-risk`),
- a tank past its API 653 inspection interval → HOLD
  (`:tank-integrity-assessment-stale`),
- bonding-grounding unconfirmed → HOLD (`:bonding-grounding-unconfirmed`),
- a double commit of the same tank → HOLD (`:already-commit`),
- a double transfer of the same tank → HOLD (`:already-transfer`).

Each HOLD settles at the governor node and never reaches a human approver --
the same failure mode the audit ledger is built to catch and the minimum
terminal controls above are built to prevent. It is not a substitute for those
controls, but it is the fastest way for a new operator (or a reviewer) to
feel, hands-on, why the gate exists before touching a real deployment.

## Certification
Certified operators must prove spec-basis-grounded verification, evidence-backed
commit readiness (receipt POD chain, overfill/ullage, API 653 integrity,
bonding-grounding), and human review for every commit- and transfer-affecting
action.
