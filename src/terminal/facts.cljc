(ns terminal.facts
  "Per-jurisdiction terminal-storage regulatory catalog -- the
  G2-style spec-basis table the Terminal Storage Governor checks every
  `:receipt/verify` proposal against ('did the advisor cite an OFFICIAL
  public source for this jurisdiction's tank-overfill / tank-integrity /
  bonding-and-grounding requirements, or did it invent one?').

  Each entry below is a REAL jurisdiction with a REAL terminal-storage
  safety regime: Japan's Fire and Hazardous-Materials jurisdiction
  (消防法 dangerous-substances rules and the Petroleum Complex Disaster
  Prevention Act), the US API 2350 overfill-prevention standard and API
  653 tank-inspection standard enforced with EPA / state fire marshals,
  the UK COMAH Regulations 2015 (the post-Buncefield control-of-major-
  accident-hazards regime enforced by HSE and the Environment Agency),
  and the Norwegian Petroleum Safety Authority's Facilities and
  Framework Regulations. The required-evidence set (tank inspection
  record at an API 653 equivalent interval, overfill-prevention system
  test, bonding-and-grounding confirmation) mirrors the tank-gauging,
  overfill-control and static-electricity evidence a regulator actually
  demands before a petroleum receipt is committed to a storage tank; the
  Buncefield-type overfill event and the API 653 inspection interval are
  the two physical facts the governor's overfill-risk and tank-integrity-
  assessment-stale checks are ultimately grounded in.

  Also seeded: India's Petroleum and Explosives Safety Organisation
  (PESO) under the Petroleum Rules 2002 (tank/vehicle bonding-and-
  earthing during loading/unloading, dip-rod/overfill safety
  procedure); Saudi Arabia's High Commission for Industrial Security
  (HCIS) Safety & Fire Protection Directives plus the Saudi Building
  Code Fire Protection Requirements (SBC 801, storage-tank hot-work/
  separation provisions); the UAE's Department of Energy - Abu Dhabi
  Fuel Storage Tanks Regulations 2023 (overfill protection §3.7,
  API-653-aligned tank-integrity inspection §3.9.5-3.9.7 -- this
  regulation's own extracted text did NOT confirm explicit bonding/
  grounding language, so that sub-requirement is grounded instead in
  the complementary UAE Fire and Life Safety Code of Practice, cited
  honestly as a second document rather than folded silently into the
  first); and Mexico's ASEA (Agencia Nacional de Seguridad Industrial y
  de Protección al Medio Ambiente del Sector Hidrocarburos) under
  NOM-006-ASEA-2017 (storage-terminal mechanical-integrity/overfill/
  grounding, API 653/570-aligned).

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` is the terminal-storage
  evidence set (tank inspection record at an API 653-equivalent interval,
  overfill-prevention system test, bonding-grounding confirmation);
  `:legal-basis` / `:owner-authority` / `:provenance` are the G2 citation
  the governor requires before any `:receipt/verify` proposal can commit."
  {"JPN" {:name "JPN"
          :owner-authority "消防庁 / 経済産業省"
          :legal-basis "消防法 危険物規制; 石油コンビナート等災害防止法"
          :provenance "https://www.fdma.go.jp/"
          :required-evidence ["tank inspection record (API 653 equivalent)"
                              "overfill-prevention system test"
                              "bonding-grounding confirmation"]}
   "USA" {:name "USA"
          :owner-authority "API / EPA / state fire marshals"
          :legal-basis "API Standard 2350 (overfill prevention); API 653 (tank inspection)"
          :provenance "https://www.api.org/"
          :required-evidence ["tank inspection record (API 653 equivalent)"
                              "overfill-prevention system test"
                              "bonding-grounding confirmation"]}
   "GBR" {:name "GBR"
          :owner-authority "HSE / Environment Agency"
          :legal-basis "COMAH Regulations 2015 (post-Buncefield)"
          :provenance "https://www.hse.gov.uk/comah/"
          :required-evidence ["tank inspection record (API 653 equivalent)"
                              "overfill-prevention system test"
                              "bonding-grounding confirmation"]}
   "NOR" {:name "NOR"
          :owner-authority "Petroleum Safety Authority Norway (PSA)"
          :legal-basis "Facilities Regulations; Framework Regulations"
          :provenance "https://www.ptil.no/en/regulations/"
          :required-evidence ["tank inspection record (API 653 equivalent)"
                              "overfill-prevention system test"
                              "bonding-grounding confirmation"]}
   "IND" {:name "IND"
          :owner-authority "Petroleum and Explosives Safety Organisation (PESO), Department for Promotion of Industry and Internal Trade (DPIIT)"
          :legal-basis "Petroleum Act, 1934 + Petroleum Rules, 2002 (tank/vehicle bonding-and-earthing, dip-rod/overfill safety procedure)"
          :provenance "https://www.peso.gov.in/en/petroleum-rules-2002"
          :required-evidence ["tank inspection record (API 653 equivalent)"
                              "overfill-prevention system test"
                              "bonding-grounding confirmation"]}
   "SAU" {:name "SAU"
          :owner-authority "الهيئة العليا للأمن الصناعي (High Commission for Industrial Security, HCIS), Ministry of Interior / Saudi Building Code National Committee"
          :legal-basis "HCIS Safety & Fire Protection Directives (SAF-01, storage-tank venting/overfill/bonding-grounding requirements); Saudi Building Code Fire Protection Requirements (SBC 801)"
          :provenance "https://sbc.gov.sa/En/BC/Pages/BuildingCode/BCDetails.aspx?codeId=801&year=2024"
          :required-evidence ["tank inspection record (API 653 equivalent)"
                              "overfill-prevention system test"
                              "bonding-grounding confirmation"]}
   "ARE" {:name "ARE"
          :owner-authority "Department of Energy - Abu Dhabi (DoE) / Dubai Civil Defence"
          :legal-basis "DoE Fuel Storage Tanks Regulations 2023 (Doc. DoE/ED/R01/005) -- overfill protection and API-653-aligned tank-integrity inspection; UAE Fire and Life Safety Code of Practice (bonding-and-grounding/fire-protection provisions for flammable-liquid storage)"
          :provenance "https://www.doe.gov.ae/-/media/Project/DOE/Department-Of-Energy/Media-Center-Publications/2023_08_01-DoE-Fuel-Storage-Tanks-Regulations--QMS-Final.pdf"
          :required-evidence ["tank inspection record (API 653 equivalent)"
                              "overfill-prevention system test"
                              "bonding-grounding confirmation"]}
   "MEX" {:name "MEX"
          :owner-authority "Agencia Nacional de Seguridad Industrial y de Protección al Medio Ambiente del Sector Hidrocarburos (ASEA)"
          :legal-basis "NOM-006-ASEA-2017, Seguridad en Almacenamiento Terrestre de Petrolíferos"
          :provenance "https://www.dof.gob.mx/nota_detalle.php?codigo=5533266&fecha=27/07/2018"
          :required-evidence ["tank inspection record (API 653 equivalent)"
                              "overfill-prevention system test"
                              "bonding-grounding confirmation"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to commit a receipt,
  commit storage or transfer custody on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry. Never report a missing jurisdiction
  as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-5210 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `terminal.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))

;; ─────────── Downstream Cross-Actor Handoff (optional, isic-5210 -> e.g. isic-5224/isic-5229) ───────────
;;
;; `:custody/transfer` proposals MAY OPTIONALLY carry a `:handoff` record
;; under the proposal's `:value` when this terminal actor transfers custody
;; of a committed tank stock to a downstream custodian (pipeline batch out,
;; tanker loading, refinery rundown handover -- e.g. cloud-itonami-isic-5224
;; cargo-handling or cloud-itonami-isic-5229 downstream). Reuses the SAME
;; `:handoff/*` wire shape the fleet's other `:handoff`-linked actors already
;; use (ADR-2607177600 / ADR-2800000500 / ADR-2800000800 / ADR-2800000700).
;; A `:handoff` here is OPTIONAL, not required: this actor's custody-
;; transfer proposals worked before this field existed and keep working
;; unchanged with no `:handoff` attached at all.
;;
;;   {:handoff/id "..."
;;    :handoff/source-actor "cloud-itonami-isic-5210"
;;    :handoff/batch-id "..."
;;    :handoff/product-type-id :some/keyword-or-string
;;    :handoff/quantity-kg 120.5
;;    :handoff/dispatched-at-iso "..."
;;    :handoff/cold-chain-temp-min-c 2.0     ; OPTIONAL, pass-through only
;;    :handoff/cold-chain-temp-max-c 10.0    ; OPTIONAL, pass-through only
;;    :handoff/unspsc-code "..."             ; OPTIONAL, pass-through only
;;    :handoff/gtin "..."                    ; OPTIONAL, pass-through only
;;    :handoff/carrier-actor "cloud-itonami-isic-4920"        ; OPTIONAL, pass-through only
;;    :handoff/carrier-tracking-ref "..."}                    ; OPTIONAL, pass-through only

(defn handoff-record-well-formed?
  "Positive-sense convenience predicate: does `handoff` carry every
  REQUIRED `:handoff/*` field (id/source-actor/batch-id/product-type-id/
  quantity-kg/dispatched-at-iso) with a plausible value (quantity-kg a
  positive number, the string fields non-blank)? Never validates the
  OPTIONAL cold-chain/unspsc/gtin/carrier fields."
  [handoff]
  (boolean
   (and (map? handoff)
        (seq (:handoff/id handoff))
        (seq (:handoff/source-actor handoff))
        (seq (:handoff/batch-id handoff))
        (some? (:handoff/product-type-id handoff))
        (number? (:handoff/quantity-kg handoff))
        (pos? (:handoff/quantity-kg handoff))
        (seq (:handoff/dispatched-at-iso handoff)))))
