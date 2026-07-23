(ns sewing.advisor
  "SewingAdvisor — the advisor named in this repository's README,
  proposing a production operation (approve a sew run, approve
  needle-mechanism operation, clear a quality-inspection failure)
  from a production order, pattern spec and quality standard.
  Swappable mock/llm; the advisor ONLY proposes — `sewing.governor`
  checks the stitch-density band and seam-deviation ceiling
  independently and always escalates needle-mechanism/quality-
  clearance decisions. Modeled on cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-sew-run|:approve-needle-mechanism-operation|:clear-quality-inspection-failure
               :effect :propose :order-id str :stitch-density number
               :seam-deviation-mm number :stake kw :confidence n
               :rationale str}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake order-id stitch-density seam-deviation-mm] :as request}]
  {:op op
   :effect :propose
   :order-id order-id
   :stitch-density stitch-density
   :seam-deviation-mm seam-deviation-mm
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a sewing-operations advisor. Given a request, propose an
   :op, the :order-id, :stitch-density and :seam-deviation-mm, an
   honest :confidence and a :stake. Never call an out-of-band stitch
   density or an over-ceiling seam deviation conforming — the
   governor checks both against the registered order record. Needle-
   mechanism and quality-clearance decisions always require human
   sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
