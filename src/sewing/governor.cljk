(ns sewing.governor
  "SewingOperationsGovernor — the independent safety/traceability
  layer named in this repository's README/business-model.md, gating
  the robot-dispensed physical work (fabric feeding, cut-piece
  sorting) an advisor may propose. The governor never dispatches
  hardware itself. Modeled on cloud-itonami-isco-4311's
  bookkeeping.governor. Sew-run twist: a proposed run's measured
  stitch density must fall inside the registered [min, max] band —
  stitch density is a spec band, not operator feel — and a proposed
  run's measured seam deviation is arithmetic comparison against the
  registered ceiling.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose (the
                           governor never dispatches hardware; it only
                           gates what the robot may execute).
    3. order basis          — a sew-run approval must cite a
                           REGISTERED order belonging to this client.
    4. stitch-density band  — the proposed measured stitch density
                           must fall inside the order's registered
                           [:min-stitch-density, :max-stitch-density]
                           band (a spec band, not operator feel).
    5. seam-deviation ceiling — the proposed measured seam deviation
                           must not exceed the order's registered
                           :max-seam-deviation-mm.
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    6. :op :approve-needle-mechanism-operation (no needle-mechanism
                           operation without the governor gate).
    7. :op :clear-quality-inspection-failure (quality-inspection-
                           failure clearance always requires human
                           sign-off).
    8. low confidence (< `confidence-floor`)."
  (:require [sewing.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:approve-needle-mechanism-operation
                                     :clear-quality-inspection-failure})

(defn- hard-violations [{:keys [request proposal]} client-record o]
  (let [{:keys [op stitch-density seam-deviation-mm]} proposal
        sew? (= :approve-sew-run op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor はハードウェアを直接起動しない）"})

      (and sew? (nil? o))
      (conj {:rule :unknown-order :detail "未登録 order への縫製承認は不可"})

      (and sew? o (not= (:client-id o) (:client-id request)))
      (conj {:rule :order-wrong-client :detail "order が別 client のもの"})

      (and sew? o (number? stitch-density)
           (or (< stitch-density (:min-stitch-density o))
               (> stitch-density (:max-stitch-density o))))
      (conj {:rule :stitch-density-out-of-band
             :detail (str "測定ステッチ密度 " stitch-density " が登録済み帯域 ["
                          (:min-stitch-density o) ", " (:max-stitch-density o)
                          "] の外（ステッチ密度は仕様帯域であってオペレータの感覚ではない）")})

      (and sew? o (number? seam-deviation-mm)
           (> seam-deviation-mm (:max-seam-deviation-mm o)))
      (conj {:rule :seam-deviation-exceeds-ceiling
             :detail (str "縫い目偏差 " seam-deviation-mm "mm > 登録済み上限 "
                          (:max-seam-deviation-mm o) "mm")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `sewing.store/Store`. Pure — never mutates the
  store, never dispatches the robot."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        o (some->> (:order-id proposal) (store/order store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record o)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
