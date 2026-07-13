(ns sewing.store
  "SSoT for the ISCO-08 8153 independent sewing operations actor
  (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors section;
  README's 'Robotics premise' — a material-handling robot performs
  fabric feeding and cut-piece sorting under this advisor/governor
  pair, which never dispatches hardware itself). Modeled on
  cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered organization (:client-id, :name)
    order  — a registered production order {:order-id :client-id
             :name :min-stitch-density number :max-stitch-density
             number :max-seam-deviation-mm number}.
             `:min-stitch-density`/`:max-stitch-density` is the
             registered stitch-per-inch band a proposed sew run's
             measured density must fall inside (stitch density is a
             spec band, not operator feel); `:max-seam-deviation-mm`
             is the registered ceiling a proposed run's measured seam
             deviation must not exceed.
    record — a committed operating record (approved sew run) —
             written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (order [s order-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-order! [s o])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (order [_ order-id] (get-in @a [:orders order-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-order! [s o]
    (swap! a assoc-in [:orders (:order-id o)] o) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :orders {} :records [] :ledger []}
                                   seed)))))
