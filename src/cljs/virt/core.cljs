(ns virt.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :refer [put! <! >! chan timeout alts!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]
            [virt.utils :refer []]
            [virt.dev]
            [virt.cosm-list]))


(def app-state (atom {}))


(defn header [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm page]}]
      (dom/header nil
        (dom/div nil
          (if (not= (:location page) :home)
            (dom/button #js {:id "home-button"
                             :className "transparent-button"
                             :onClick #(put! comm [:navigate :home])}
                        "Home")))
        (dom/div nil
          (dom/div #js {:id "header-title"} (:title page)))
        (dom/div nil
          (dom/button #js {:id "new-button"
                           :className "transparent-button"}
                      "New"))))))

(defn main [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:page {:location :home
              :title "Virt"}})
    om/IWillMount
    (will-mount [_]
      (let [comm (chan)]
        (om/set-state! owner :comm comm)
        (virt.cosm-list/attach (.getElementById js/document "content") comm)
        (go (while true
              (let [[msg value] (<! comm)]
                (println msg)
                (case msg
                  :navigate (om/set-state! owner :page value)
                  :join-cosm (virt.chat/attach (.getElementById js/document "content") comm)
                  ;:set-header-text (println "set-header-text" value)
                  nil))))))
    om/IRenderState
    (render-state [_ {:keys [comm page]}]
      (om/build header app {:init-state {:comm comm} :state {:page page}}))))


(om/root main app-state {:target (.getElementById js/document "header")})
