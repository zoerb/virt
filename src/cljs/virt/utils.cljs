(ns virt.utils)

(defn in?
  [seq elm]
  (some #(= elm %) seq))
