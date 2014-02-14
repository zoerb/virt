(ns virt.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]]
                   [secretary.macros :refer [defroute]])
  (:require [goog.events :as events]
            [cljs.core.async :refer [put! <! >! chan timeout alts!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [secretary.core :as secretary]
            [cljs-http.client :as http]
            [clojure.browser.repl :as repl]
            [virt.utils :refer []]
            [virt.chat :refer []])
  (:import [goog History]
           [goog.history Html5History]
           [goog.history EventType]))


(enable-console-print!)
(repl/connect "http://localhost:9000/repl")

(def app-state
  (atom {:cosms {0x001 {:title "cosm1" :app 'virt.chat }
                 0x002 {:title "cosm2"}
                 0x003 {:title "cosm3"}}}))


(defn set-routes [owner]
  (defroute "/" []
    (om/set-state! owner :page :home))
  (defroute ":cosm-id" [cosm-id]
    (om/set-state! owner :page (cljs.reader/read-string cosm-id))))

(def navigate
  (let [history (Html5History.)]
    (.setUseFragment history false)
    (.setEnabled history true)
    (events/listen history EventType.NAVIGATE
      (fn [e] (secretary/dispatch! (.-token e))))
    (fn [to]
      (case to
        :home (.setToken history "")
        (.setToken history to)))))


(defn list-item [id-item owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (let [id (first id-item)
            item (second id-item)
            href (str id)]
        (dom/a #js {:href href
                    :onClick (fn [e] (.preventDefault e) 
                                     (put! comm [:join-cosm id]))
                    :className "list-link"}
               (dom/li nil (:title item)))))))

(defn header [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm page]}]
      (dom/header nil
        (dom/div nil
          (if (not= page :home)
            (dom/button #js {:id "home-button"
                             :className "transparent-button"
                             :onClick #(put! comm [:navigate :home])}
                        "Home")))
        (dom/div nil
          (dom/div #js {:id "header-title"}
                   (if (= page :home)
                     "Virt"
                     (:title (get (:cosms app) page)))))
        (dom/div nil
          (dom/button #js {:id "new-button"
                           :className "transparent-button"}
                      "New"))))))

(defn main [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:page :home})
    om/IWillMount
    (will-mount [_]
      (let [comm (chan)
            api-comm (chan)]
        (om/set-state! owner :comm comm)
        (om/set-state! owner :api-comm api-comm)
        (go (while true
              (let [[[msg value] port] (alts! [comm api-comm])]
                (case msg
                  :navigate (om/set-state! owner :page value)
                  :join-cosm (virt.chat/instantiate (.getElementById js/document "content") api-comm)
                  :set-header-text (println "set-header-text" value)
                  nil)))))
      (set-routes owner))
    om/IRenderState
    (render-state [_ {:keys [comm page]}]
      (dom/div nil
        (om/build header app {:init-state {:comm comm} :state {:page page}})
        (dom/div #js {:id "cosm-content"}
          (if (= page :home)
            (apply dom/ul #js {:className "virt-list"}
              (om/build-all list-item (:cosms app) {:init-state {:comm comm}}))
            (virt.chat/render)))))))

(om/root app-state main (.getElementById js/document "content"))
