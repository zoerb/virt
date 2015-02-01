(ns virt.components
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))


(defn header [app owner {:keys [title left-button right-button]}]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/header nil
        (dom/div nil
          (if (and left-button (:show left-button))
            (dom/button #js {:className "transparent-button"
                             :onClick (:onClick left-button)}
                        (:text left-button))))
        (dom/div nil
          (dom/div #js {:id "header-title"} title))
        (dom/div nil
          (if (and right-button (:show right-button))
            (dom/button #js {:className "transparent-button"
                             :onClick (:onClick right-button)}
                        (:text right-button))))))))
