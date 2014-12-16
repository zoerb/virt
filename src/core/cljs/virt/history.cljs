(ns virt.history
  (:require [cljs.core.async :as async :refer [put! chan]]))


(defn listen-navigation []
  (let [nav-chan (chan)]
    (set! (.-onpopstate js/window)
      (fn [_]
        (put! nav-chan (.. js/document -location -pathname))))
    nav-chan))

(defn set-history-path! [path] (.pushState js/history nil path path))
