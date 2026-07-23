(ns terminal.store
  "SSoT for the terminal-storage actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/terminal/store_contract_test.clj), which is the whole point:
  the actor, the Terminal Storage Governor and the audit ledger never
  know which SSoT they run on.

  Unlike `retailops`/4711's own `order` entity (distinguished by
  `:kind`), this vertical's `commit` and `transfer` actuation events
  apply SEQUENTIALLY to the SAME `terminal-stock` -- a storage commit
  happens first (a confirmed receipt is committed to the tank's book-of-
  record), custody transfer happens later, on the same tank record. This
  matches the repair-shop cluster's own `ticket` shape more closely (two
  real-world acts, in order, on one entity), with dedicated double-
  actuation-guard booleans (`:committed?`/`:custody-transferred?`, never
  a `:status` value).

  The ledger stays append-only on every backend: 'which tank had a
  receipt committed without a confirmed POD chain, which tank was
  committed past its ullage (a Buncefield-type overfill risk), which
  tank was committed with a stale API 653 integrity assessment or an
  unconfirmed bonding-grounding, which tank had storage committed,
  which tank had custody transferred, on what jurisdictional basis,
  approved by whom' is always a query over an immutable log -- the
  audit trail a regulator, a custodian, or an operator trusting a
  terminal-storage actor needs, and the evidence an operator needs if
  a commit or a transfer is later disputed."
  (:require [terminal.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (terminal-stock [s id])
  (all-terminal-stocks [s])
  (assessment-of [s terminal-stock-id] "committed receipt-verification assessment (checklist), or nil")
  (ledger [s])
  (act1-history [s] "the append-only storage-commit history (terminal.registry drafts)")
  (act2-history [s] "the append-only custody-transfer history (terminal.registry drafts)")
  (next-commit-sequence [s jurisdiction] "next commit-number sequence for a jurisdiction")
  (next-transfer-sequence [s jurisdiction] "next transfer-number sequence for a jurisdiction")
  (terminal-stock-already-committed? [s terminal-stock-id] "has storage already been committed for this tank?")
  (terminal-stock-already-transferred? [s terminal-stock-id] "has custody of this tank already been transferred?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-terminal-stocks [s terminal-stocks] "replace/seed the tank directory (map id->terminal-stock)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained terminal-stock set covering both actuation
  lifecycles (commit, transfer) plus the governor's own terminal-storage
  checks, so the actor + tests run offline. Each violation tank isolates
  exactly ONE failure mode (the rest stay clean) following the 'exercise
  the failure mode directly, never only via a happy-path actuation'
  discipline every sibling governor's demo data establishes."
  []
  {:terminal-stocks
   {"tank-1" {:id "tank-1" :tank-id "T-101" :product-grade "crude-light"
              :volume-barrels 200000 :receipt-confirmed? true
              :tank-level-pct 40.0 :ullage-barrels 300000
              :planned-receipt-barrels 100000
              :integrity-assessment-current? true :gauge-verified? true
              :bonding-grounding-confirmed? true
              :committed? false :custody-transferred? false
              :jurisdiction "JPN" :status :intake}
    "tank-2" {:id "tank-2" :tank-id "T-201" :product-grade "crude-light"
              :volume-barrels 200000 :receipt-confirmed? true
              :tank-level-pct 40.0 :ullage-barrels 300000
              :planned-receipt-barrels 100000
              :integrity-assessment-current? true :gauge-verified? true
              :bonding-grounding-confirmed? true
              :committed? false :custody-transferred? false
              :jurisdiction "ATL" :status :intake}
    "tank-3" {:id "tank-3" :tank-id "T-301" :product-grade "crude-light"
              :volume-barrels 200000 :receipt-confirmed? false
              :tank-level-pct 40.0 :ullage-barrels 300000
              :planned-receipt-barrels 100000
              :integrity-assessment-current? true :gauge-verified? true
              :bonding-grounding-confirmed? true
              :committed? false :custody-transferred? false
              :jurisdiction "JPN" :status :intake}
    "tank-4" {:id "tank-4" :tank-id "T-401" :product-grade "crude-light"
              :volume-barrels 200000 :receipt-confirmed? true
              :tank-level-pct 40.0 :ullage-barrels 300000
              :planned-receipt-barrels 400000
              :integrity-assessment-current? true :gauge-verified? true
              :bonding-grounding-confirmed? true
              :committed? false :custody-transferred? false
              :jurisdiction "JPN" :status :intake}
    "tank-5" {:id "tank-5" :tank-id "T-501" :product-grade "crude-light"
              :volume-barrels 200000 :receipt-confirmed? true
              :tank-level-pct 40.0 :ullage-barrels 300000
              :planned-receipt-barrels 100000
              :integrity-assessment-current? false :gauge-verified? true
              :bonding-grounding-confirmed? true
              :committed? false :custody-transferred? false
              :jurisdiction "JPN" :status :intake}
    "tank-6" {:id "tank-6" :tank-id "T-601" :product-grade "crude-light"
              :volume-barrels 200000 :receipt-confirmed? true
              :tank-level-pct 40.0 :ullage-barrels 300000
              :planned-receipt-barrels 100000
              :integrity-assessment-current? true :gauge-verified? true
              :bonding-grounding-confirmed? false
              :committed? false :custody-transferred? false
              :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- commit-stock!
  "Backend-agnostic `:tank/mark-committed` -- looks up the terminal-stock
  via the protocol and drafts the storage-commit record, and returns
  {:result .. :tank-patch ..} for the caller to persist."
  [s terminal-stock-id]
  (let [w (terminal-stock s terminal-stock-id)
        seq-n (next-commit-sequence s (:jurisdiction w))
        result (registry/register-commit-record terminal-stock-id (:jurisdiction w) seq-n)]
    {:result result
     :tank-patch {:committed? true
                  :commit-number (get result "commit_number")}}))

(defn- transfer-stock!
  "Backend-agnostic `:tank/mark-transferred` -- looks up the terminal-stock
  via the protocol and drafts the custody-transfer record, and returns
  {:result .. :tank-patch ..} for the caller to persist."
  [s terminal-stock-id]
  (let [w (terminal-stock s terminal-stock-id)
        seq-n (next-transfer-sequence s (:jurisdiction w))
        result (registry/register-transfer-record terminal-stock-id (:jurisdiction w) seq-n)]
    {:result result
     :tank-patch {:custody-transferred? true
                  :transfer-number (get result "transfer_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (terminal-stock [_ id] (get-in @a [:terminal-stocks id]))
  (all-terminal-stocks [_] (sort-by :id (vals (:terminal-stocks @a))))
  (assessment-of [_ terminal-stock-id] (get-in @a [:assessments terminal-stock-id]))
  (ledger [_] (:ledger @a))
  (act1-history [_] (:commits @a))
  (act2-history [_] (:transfers @a))
  (next-commit-sequence [_ jurisdiction] (get-in @a [:commit-sequences jurisdiction] 0))
  (next-transfer-sequence [_ jurisdiction] (get-in @a [:transfer-sequences jurisdiction] 0))
  (terminal-stock-already-committed? [_ terminal-stock-id] (boolean (get-in @a [:terminal-stocks terminal-stock-id :committed?])))
  (terminal-stock-already-transferred? [_ terminal-stock-id] (boolean (get-in @a [:terminal-stocks terminal-stock-id :custody-transferred?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :tank/upsert
      (swap! a update-in [:terminal-stocks (:id value)] merge value)

      :receipt-assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :tank/mark-committed
      (let [terminal-stock-id (first path)
            {:keys [result tank-patch]} (commit-stock! s terminal-stock-id)
            jurisdiction (:jurisdiction (terminal-stock s terminal-stock-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:commit-sequences jurisdiction] (fnil inc 0))
                       (update-in [:terminal-stocks terminal-stock-id] merge tank-patch)
                       (update :commits registry/append result))))
        result)

      :tank/mark-transferred
      (let [terminal-stock-id (first path)
            {:keys [result tank-patch]} (transfer-stock! s terminal-stock-id)
            jurisdiction (:jurisdiction (terminal-stock s terminal-stock-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:transfer-sequences jurisdiction] (fnil inc 0))
                       (update-in [:terminal-stocks terminal-stock-id] merge tank-patch)
                       (update :transfers registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-terminal-stocks [s terminal-stocks] (when (seq terminal-stocks) (swap! a assoc :terminal-stocks terminal-stocks)) s))

(defn seed-db
  "A MemStore seeded with the demo terminal-stock set. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :commit-sequences {} :commits []
                           :transfer-sequences {} :transfers []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment payloads, ledger facts, commit/
  transfer records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:terminal-stock/id             {:db/unique :db.unique/identity}
   :assessment/terminal-stock-id  {:db/unique :db.unique/identity}
   :ledger/seq                    {:db/unique :db.unique/identity}
   :commit/seq                    {:db/unique :db.unique/identity}
   :transfer/seq                  {:db/unique :db.unique/identity}
   :commit-sequence/jurisdiction  {:db/unique :db.unique/identity}
   :transfer-sequence/jurisdiction {:db/unique :db.unique/identity}})

;; Every terminal-stock field is stored as its own Datomic attr so a governor
;; pull reads the exact ground truth (no blob decode). Boolean fields
;; are coerced on read so a missing attr reads back as false (parity
;; with MemStore). [field-key tx-attr boolean?]
(def ^:private terminal-stock-fields
  [[:id :terminal-stock/id false]
   [:tank-id :terminal-stock/tank-id false]
   [:product-grade :terminal-stock/product-grade false]
   [:volume-barrels :terminal-stock/volume-barrels false]
   [:receipt-confirmed? :terminal-stock/receipt-confirmed? true]
   [:tank-level-pct :terminal-stock/tank-level-pct false]
   [:ullage-barrels :terminal-stock/ullage-barrels false]
   [:planned-receipt-barrels :terminal-stock/planned-receipt-barrels false]
   [:integrity-assessment-current? :terminal-stock/integrity-assessment-current? true]
   [:gauge-verified? :terminal-stock/gauge-verified? true]
   [:bonding-grounding-confirmed? :terminal-stock/bonding-grounding-confirmed? true]
   [:committed? :terminal-stock/committed? true]
   [:custody-transferred? :terminal-stock/custody-transferred? true]
   [:jurisdiction :terminal-stock/jurisdiction false]
   [:status :terminal-stock/status false]
   [:commit-number :terminal-stock/commit-number false]
   [:transfer-number :terminal-stock/transfer-number false]])

(defn- terminal-stock->tx [w]
  (reduce (fn [tx [k attr _bool?]]
            (let [v (get w k)]
              (cond-> tx (some? v) (assoc attr v))))
          {:terminal-stock/id (:id w)}
          terminal-stock-fields))

(def ^:private terminal-stock-pull (mapv second terminal-stock-fields))

(defn- pull->terminal-stock [m]
  (when (:terminal-stock/id m)
    (reduce (fn [w [k attr bool?]]
              (let [v (get m attr)]
                (cond
                  bool?        (assoc w k (boolean v))
                  (some? v)    (assoc w k v)
                  :else        w)))
            {:id (:terminal-stock/id m)}
            terminal-stock-fields)))

(defrecord DatomicStore [conn]
  Store
  (terminal-stock [_ id]
    (pull->terminal-stock (d/pull (d/db conn) terminal-stock-pull [:terminal-stock/id id])))
  (all-terminal-stocks [_]
    (->> (d/q '[:find [?id ...] :where [?e :terminal-stock/id ?id]] (d/db conn))
         (map #(pull->terminal-stock (d/pull (d/db conn) terminal-stock-pull [:terminal-stock/id %])))
         (sort-by :id)))
  (assessment-of [_ terminal-stock-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?tsid
                :where [?a :assessment/terminal-stock-id ?tsid] [?a :assessment/payload ?p]]
              (d/db conn) terminal-stock-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (act1-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :commit/seq ?s] [?e :commit/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (act2-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :transfer/seq ?s] [?e :transfer/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (next-commit-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :commit-sequence/jurisdiction ?j] [?e :commit-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-transfer-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :transfer-sequence/jurisdiction ?j] [?e :transfer-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (terminal-stock-already-committed? [s terminal-stock-id]
    (boolean (:committed? (terminal-stock s terminal-stock-id))))
  (terminal-stock-already-transferred? [s terminal-stock-id]
    (boolean (:custody-transferred? (terminal-stock s terminal-stock-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :tank/upsert
      (d/transact! conn [(terminal-stock->tx value)])

      :receipt-assessment/set
      (d/transact! conn [{:assessment/terminal-stock-id (first path) :assessment/payload (ls/enc payload)}])

      :tank/mark-committed
      (let [terminal-stock-id (first path)
            {:keys [result tank-patch]} (commit-stock! s terminal-stock-id)
            jurisdiction (:jurisdiction (terminal-stock s terminal-stock-id))
            next-n (inc (next-commit-sequence s jurisdiction))]
        (d/transact! conn
                     [(terminal-stock->tx (assoc tank-patch :id terminal-stock-id))
                      {:commit-sequence/jurisdiction jurisdiction :commit-sequence/next next-n}
                      {:commit/seq (count (act1-history s)) :commit/record (ls/enc (get result "record"))}])
        result)

      :tank/mark-transferred
      (let [terminal-stock-id (first path)
            {:keys [result tank-patch]} (transfer-stock! s terminal-stock-id)
            jurisdiction (:jurisdiction (terminal-stock s terminal-stock-id))
            next-n (inc (next-transfer-sequence s jurisdiction))]
        (d/transact! conn
                     [(terminal-stock->tx (assoc tank-patch :id terminal-stock-id))
                      {:transfer-sequence/jurisdiction jurisdiction :transfer-sequence/next next-n}
                      {:transfer/seq (count (act2-history s)) :transfer/record (ls/enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (ls/enc fact)}])
    fact)
  (with-terminal-stocks [s terminal-stocks]
    (when (seq terminal-stocks) (d/transact! conn (mapv terminal-stock->tx (vals terminal-stocks)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:terminal-stocks ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [terminal-stocks]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-terminal-stocks s terminal-stocks))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo terminal-stock set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
