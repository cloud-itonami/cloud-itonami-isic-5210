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
