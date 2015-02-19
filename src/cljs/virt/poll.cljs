(ns virt.poll
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [put! <! >! chan]]
            [cljs.reader]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))


(def app-state {:messages []})

(defn main [{:keys [page params data]} owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:id "content"}
        "PERLS"))))
