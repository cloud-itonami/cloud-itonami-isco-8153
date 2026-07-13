(ns sewing.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [sewing.actor :as actor]
            [sewing.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Garment Co"})
    (store/register-order! st {:order-id "O-1" :client-id "client-1"
                               :name "shirt-run-42"
                               :min-stitch-density 8
                               :max-stitch-density 12
                               :max-seam-deviation-mm 1.0})
    st))

(deftest commits-an-in-band-in-ceiling-sew-run
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-sew-run :stake :low
                 :order-id "O-1" :stitch-density 10 :seam-deviation-mm 0.5}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-out-of-band-sew-run
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-sew-run :stake :low
                 :order-id "O-1" :stitch-density 20 :seam-deviation-mm 0.5}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-needle-mechanism-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-needle-mechanism-operation :stake :low
                 :order-id "O-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
