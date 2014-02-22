(ns virt.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [goog.events :as events]
            [cljs.core.async :refer [put! <! >! chan timeout]]
            [cljs.reader]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [secretary.core :as secretary :include-macros true :refer [defroute]]
            [virt.utils :refer []]
            [virt.dev]
            [virt.cosm-list]
            [virt.chat])
  (:import [goog History]
           [goog.history Html5History]
           [goog.history EventType]))


(def app-state
  (atom {:cosms {0x001 {:title "cosm1" :app 'virt.chat}
                 0x002 {:title "cosm2" :app 'virt.chat}
                 0x003 {:title "cosm3" :app 'virt.chat}}}))

(def parse-url
  (do
    (defroute "/" []
      :home)
    (defroute "/:cosm-id" [cosm-id]
      (cljs.reader/read-string cosm-id))
    (fn [path]
      (secretary/dispatch! path))))

(defn set-up-history [comm]
  (let [history (Html5History.)]
    (.setUseFragment history false)
    (.setEnabled history true)
    (defn set-history-token [token] (.setToken history token))
    (events/listen history EventType.NAVIGATE
      (fn [e] (put! comm [:history-change (.-token e)])))))



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
        ;(virt.cosm-list/attach (.getElementById js/document "content") comm)
        (go (while true
              (let [[msg value] (<! comm)]
                (println msg)
                (case msg
                  :navigate (om/set-state! owner :page value)
                  :join-cosm (virt.chat/attach (.getElementById js/document "content") comm)
                  ;:set-header-text (println "set-header-text" value)
                  nil))))
        (set-up-history comm)
        (set-history-token "hi")))
    om/IRenderState
    (render-state [_ {:keys [comm page]}]
      (dom/div nil
        (om/build header app {:init-state {:comm comm} :state {:page page}})
        (om/build virt.cosm-list/main app {:init-state {:comm comm} :state {:page page}})))))


(om/root main app-state {:target (.getElementById js/document "header")})
