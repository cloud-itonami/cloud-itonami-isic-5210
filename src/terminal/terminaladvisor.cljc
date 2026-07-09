(ns terminal.terminaladvisor
  "TerminalAdvisor client -- the *contained intelligence node* for the
  terminal-storage actor.

  It normalizes tank intake, drafts a per-jurisdiction tank-overfill /
  tank-integrity / bonding-grounding evidence checklist, drafts the
  storage-commit action, and drafts the custody-transfer action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a *proposal*
  (with a rationale + the fields it cited), never a committed record or
  a real commit/transfer. Every output is censored downstream by
  `terminal.governor` before anything touches the SSoT, and
  `:storage/commit`/`:custody/transfer` proposals NEVER auto-commit at
  any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :storage/commit | :custody/transfer | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [terminal.facts :as facts]
            [terminal.registry :as registry]
            [terminal.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the tank id, product grade or jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "タンク記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :tank/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-receipt
  "Per-jurisdiction tank-overfill / tank-integrity / bonding-grounding
  evidence checklist draft. `:no-spec?` injects the failure mode we
  must defend against: proposing a checklist for a jurisdiction with NO
  official spec-basis in `terminal.facts` -- the Terminal Storage
  Governor must reject this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [w (store/terminal-stock db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction w))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "terminal.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :receipt-assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :receipt-assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- propose-commit
  "Draft the actual STORAGE-COMMIT action -- committing a confirmed
  petroleum receipt to a storage tank (the receipt becomes inventory
  under the tank's book-of-record). ALWAYS `:stake :storage/commit` --
  this is a REAL-WORLD act (an autonomous tank-gauging/valve robot
  physically commits the receipt by closing the receipt manifold and
  settling the tank gauge, or an operator does), never a draft the
  actor may auto-run. See README `Actuation`: no phase ever adds this
  op to a phase's `:auto` set (`terminal.phase`); the governor also
  always escalates on `:storage/commit`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [w (store/terminal-stock db subject)
        receipt-ok? (and w (true? (:receipt-confirmed? w)))
        within-ullage? (and w (not (registry/overfill-risk?
                                     (:volume-barrels w)
                                     (:planned-receipt-barrels w)
                                     (:ullage-barrels w))))
        integrity-ok? (and w (true? (:integrity-assessment-current? w)))
        bonding-ok? (and w (true? (:bonding-grounding-confirmed? w)))
        gauge-ok? (and w (true? (:gauge-verified? w)))]
    {:summary    (str subject " 向け在庫計上提案"
                      (when w (str " (tank=" (:tank-id w) ")")))
     :rationale  (if w
                   (str "receipt-confirmed?=" receipt-ok?
                        " within-ullage?=" within-ullage?
                        " integrity-current?=" integrity-ok?
                        " bonding-grounding?=" bonding-ok?
                        " gauge-verified?=" gauge-ok?)
                   "terminal-stockが見つかりません")
     :cites      (if w [subject] [])
     :effect     :tank/mark-committed
     :value      {:terminal-stock-id subject}
     :stake      :storage/commit
     :confidence (if (and receipt-ok? within-ullage? integrity-ok? bonding-ok? gauge-ok?)
                   0.9 0.3)}))

(defn- propose-transfer
  "Draft the actual CUSTODY-TRANSFER action -- transferring custody of
  a committed tank stock to the next custodian (pipeline batch out,
  tanker loading, refinery rundown handover). ALWAYS `:stake
  :custody/transfer` -- this is a REAL-WORLD act (real volume / real
  custody moves between terminal and next custodian), never a draft
  the actor may auto-run. See README `Actuation`: no phase ever adds
  this op to a phase's `:auto` set (`terminal.phase`); the governor
  also always escalates on `:custody/transfer`. Two independent layers
  agree, deliberately."
  [db {:keys [subject]}]
  (let [w (store/terminal-stock db subject)
        committed? (and w (:committed? w))
        bonding-ok? (and w (true? (:bonding-grounding-confirmed? w)))]
    {:summary    (str subject " 向け引渡提案"
                      (when w (str " (tank=" (:tank-id w) ")")))
     :rationale  (if w
                   (str "committed?=" committed?
                        " bonding-grounding?=" bonding-ok?)
                   "terminal-stockが見つかりません")
     :cites      (if w [subject] [])
     :effect     :tank/mark-transferred
     :value      {:terminal-stock-id subject}
     :stake      :custody/transfer
     :confidence (if (and committed? bonding-ok?) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :tank/intake       (normalize-intake db request)
    :receipt/verify    (verify-receipt db request)
    :storage/commit    (propose-commit db request)
    :custody/transfer  (propose-transfer db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは地域石油ターミナル/デポ事業者の在庫計上・引渡エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:tank/upsert|:receipt-assessment/set|:tank/mark-committed|"
       ":tank/mark-transferred) "
       ":stake(:storage/commit か :custody/transfer か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域のターミナル貯蔵要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"
       "受領確認・過充填リスク・タンク健全性・結束アースの状態を偽って報告してはいけません。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :receipt/verify   {:terminal-stock (store/terminal-stock st subject)}
    :storage/commit   {:terminal-stock (store/terminal-stock st subject)}
    :custody/transfer {:terminal-stock (store/terminal-stock st subject)}
    {:terminal-stock (store/terminal-stock st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Terminal Storage Governor
  escalates/holds -- an LLM hiccup can never auto-commit storage or
  auto-transfer custody."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :terminaladvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
