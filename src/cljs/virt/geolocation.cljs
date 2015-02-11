(ns virt.geolocation
  (:require [cljs.core.async :as async :refer [put! chan]]))


(defn get-geolocation []
  (let [out (chan)]
    (.. js/navigator
      -geolocation
      (getCurrentPosition
        (fn [loc]
          (put! out {:lon (.. loc -coords -longitude)
                     :lat (.. loc -coords -latitude)}))))
    out))
