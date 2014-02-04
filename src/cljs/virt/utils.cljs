(ns virt.utils)

(defn find-in-vec [cursor value coll]
  (first (filter #(= (get-in % cursor)
                     value)
                 coll)))

(defn in?
  [seq elm]
  (some #(= elm %) seq))

(defn find-all-in-vec [cursor values coll]
  (filter #(in? values (get-in % cursor))
          coll))
