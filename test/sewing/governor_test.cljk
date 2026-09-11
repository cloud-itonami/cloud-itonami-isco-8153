(ns sewing.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [sewing.store :as store]
            [sewing.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Garment Co"})
    (store/register-order! st {:order-id "O-1" :client-id "client-1"
                               :name "shirt-run-42"
                               :min-stitch-density 8
                               :max-stitch-density 12
                               :max-seam-deviation-mm 1.0})
    st))

(defn- sew-run [density deviation]
  {:op :approve-sew-run :effect :propose :order-id "O-1"
   :stitch-density density :seam-deviation-mm deviation :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-band-and-ceiling
  (let [st (fresh-store)
        v (governor/check req {} (sew-run 10 0.5) st)]
    (is (:ok? v))))

(deftest ok-at-exact-band-and-ceiling-edges
  (testing "the stitch-density band and seam-deviation ceiling boundaries are inclusive"
    (let [st (fresh-store)]
      (is (:ok? (governor/check req {} (sew-run 8 1.0) st)))
      (is (:ok? (governor/check req {} (sew-run 12 1.0) st))))))

(deftest hard-on-stitch-density-out-of-band
  (testing "stitch density is a spec band, not operator feel"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (sew-run 20 0.5) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :stitch-density-out-of-band (:rule %)) (:violations v))))))

(deftest hard-on-seam-deviation-exceeds-ceiling
  (let [st (fresh-store)
        v (governor/check req {} (assoc (sew-run 10 3.0) :confidence 0.99) st)]
    (is (:hard? v))
    (is (some #(= :seam-deviation-exceeds-ceiling (:rule %)) (:violations v)))))

(deftest hard-on-unknown-order
  (let [st (fresh-store)
        v (governor/check req {} (assoc (sew-run 10 0.5) :order-id "O-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-order (:rule %)) (:violations v)))))

(deftest hard-on-foreign-order
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (sew-run 10 0.5) st)]
      (is (:hard? v))
      (is (some #(= :order-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (sew-run 10 0.5) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (sew-run 10 0.5) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-needle-mechanism-operation-even-at-high-confidence
  (testing "no needle-mechanism operation without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-needle-mechanism-operation :effect :propose
                                    :order-id "O-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-quality-clearance-even-at-high-confidence
  (testing "quality-inspection-failure clearance always requires human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} {:op :clear-quality-inspection-failure :effect :propose
                                    :order-id "O-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (sew-run 10 0.5) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
