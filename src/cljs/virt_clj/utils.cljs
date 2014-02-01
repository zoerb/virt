(ns virt-clj.utils)

(defn find-in-vec [cursor value coll]
  (first (filter #(= (get-in % cursor)
                     value)
                 coll)))
