(ns terminal.registry
  "Pure-function storage-commit + custody-transfer record construction
  -- an append-only terminal book-of-record draft -- AND the pure
  tank-overfill range-check function the Terminal Storage Governor calls
  to re-verify a tank's own physical ground truth before any storage
  commit.

  Unlike `freightops`/4920's own registry (which delegates tracking-
  number validation to a real, pre-existing bespoke capability library
  `kotoba-lang/logistics`), this terminal-storage vertical has NO
  pre-existing capability library to wrap -- there is no 'kotoba-lang/
  petroleum-terminal' to call. So this namespace is self-contained: the
  overfill-risk check (planned receipt vs remaining ullage) is a pure
  function defined HERE, not delegated. The actor layer adds the
  governed proposal/approval loop on top; the governor calls this same
  pure function to INDEPENDENTLY re-verify the tank's own recorded
  values before any real-world storage commit, rather than trusting the
  advisor's self-reported confidence.

  Like every sibling actor's registry, there is no single international
  reference-number standard for a storage-commit or custody-transfer
  record -- every operator/jurisdiction assigns its own reference
  format. This namespace does NOT invent one beyond a jurisdiction-
  scoped sequence number; it validates the record's required fields,
  the same honest, non-fabricating discipline `terminal.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real tank-gauging / SCADA / custody-transfer system. It
  builds the RECORD an operator would keep, not the act of committing a
  receipt to a storage tank or transferring custody itself (that is
  `terminal.operation`'s `:storage/commit`/`:custody/transfer`, always
  human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- tank-overfill range check (pure) -----------------------------
;;
;; The Terminal Storage Governor calls this to INDEPENDENTLY re-verify the
;; tank's own recorded physical values before authorizing a storage commit.
;; Returns true when the planned receipt would overfill the tank -- the
;; conservative terminal-safety choice, matching the measured-value-vs-
;; rated-limit discipline of the fabrication siblings: a receipt that
;; cannot be certified to fit the remaining ullage is treated as a
;; violation, not as 'unknown therefore ok'. Missing data -> violation
;; (cannot verify safe to commit -- a Buncefield-type overfill is exactly
;; what an unverified receipt risks).

(defn overfill-risk?
  "Planned receipt vs remaining tank ullage -- the fabrication 'measured
  value exceeds rated limit' pattern, applied to tank overfill. Ullage is
  the remaining headspace in the tank; the tank's total capacity is the
  current volume plus that ullage. A planned receipt that would fill past
  total capacity (i.e. a receipt larger than the remaining ullage) is the
  Buncefield-type overfill event API Standard 2350 exists to prevent:
  the tank overflows, the bund catches or fails, and a volatile cloud
  forms. Missing any value -> unsafe (cannot verify the receipt fits
  before opening the receipt valve)."
  [volume-barrels planned-receipt-barrels ullage-barrels]
  (cond
    (or (nil? volume-barrels) (nil? planned-receipt-barrels) (nil? ullage-barrels)) true
    (> (+ volume-barrels planned-receipt-barrels) (+ volume-barrels ullage-barrels)) true
    :else false))

;; ----------------------------- record construction -----------------------------

(defn register-commit-record
  "Validate + construct the STORAGE-COMMIT registration DRAFT -- the
  operator's own legal act of committing a confirmed petroleum receipt
  to a storage tank (the receipt is now inventory in the tank's book-of-
  record, no longer a pending receipt). Pure function -- does not touch
  any real tank-gauging or SCADA system; it builds the RECORD an
  operator would keep. `terminal.governor` independently re-verifies the
  tank's own POD-chain (receipt-confirmed), overfill-risk (planned
  receipt vs ullage), tank-integrity-assessment-current, bonding-
  grounding ground truth, and blocks a double-commit of the same tank,
  before this is ever allowed to commit."
  [terminal-stock-id jurisdiction sequence]
  (when-not (and terminal-stock-id (not= terminal-stock-id ""))
    (throw (ex-info "storage-commit: terminal_stock_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "storage-commit: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "storage-commit: sequence must be >= 0" {})))
  (let [commit-number (str (str/upper-case jurisdiction) "-COMMIT-" (zero-pad sequence 6))
        record {"record_id" commit-number
                "kind" "storage-commit-draft"
                "terminal_stock_id" terminal-stock-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "commit_number" commit-number
     "certificate" (unsigned-certificate "StorageCommit" commit-number commit-number)}))

(defn register-transfer-record
  "Validate + construct the CUSTODY-TRANSFER registration DRAFT -- the
  operator's own legal act of transferring custody of a committed tank
  stock to the next custodian (pipeline batch out, tanker loading, or
  refinery rundown handover). Pure function -- does not touch any real
  custody-transfer / ticketing system; it builds the RECORD an operator
  would keep. `terminal.governor` independently re-verifies the tank's
  own evidence completeness and blocks a double-transfer of the same
  tank, before this is ever allowed to commit."
  [terminal-stock-id jurisdiction sequence]
  (when-not (and terminal-stock-id (not= terminal-stock-id ""))
    (throw (ex-info "custody-transfer: terminal_stock_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "custody-transfer: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "custody-transfer: sequence must be >= 0" {})))
  (let [transfer-number (str (str/upper-case jurisdiction) "-TRANSFER-" (zero-pad sequence 6))
        record {"record_id" transfer-number
                "kind" "custody-transfer-draft"
                "terminal_stock_id" terminal-stock-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "transfer_number" transfer-number
     "certificate" (unsigned-certificate "CustodyTransfer" transfer-number transfer-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
