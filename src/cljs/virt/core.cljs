(ns virt.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]
            [virt.history :refer [listen-navigation set-history-path!]]
            [virt.router :refer [stack-to-path path-to-stack]]
            [virt.login :refer [mount-login]]
            [virt.header :refer [header]]
            [virt.home :as home]
            [virt.chat :as chat]))


(def app-state
  (atom {:virt.home home/app-state
         :virt.chat chat/app-state}))

(def routes
  [[["/" [#".*" :rest]] :virt.home/home]
   ["" [home/routes
        chat/routes]]])

(defn main [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:comm (chan)})
    om/IWillMount
    (will-mount [_]
      (let [nav-chan (listen-navigation)]
        (go (while true
          (om/set-state! owner [:page-stack] (path-to-stack routes (<! nav-chan))))))
      (let [comm (om/get-state owner :comm)]
        (go
          (while true
            (let [[msg msg-data] (<! comm)]
              (case msg
                ; TODO: remove/re-evaluate cursor derefs when upgrading to Om 0.8
                :navigate
                (let [page-stack (om/get-state owner [:page-stack])
                      [nav-type new-frame] msg-data
                      new-stack
                      (case nav-type
                        :back (pop page-stack)
                        :push
                        (let [[page page-params] new-frame]
                          (conj page-stack [page page-params])))]
                  (om/set-state! owner [:page-stack] new-stack)
                  (set-history-path! (stack-to-path routes new-stack)))
                nil))))))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (let [page-stack (om/get-state owner :page-stack)
            [page params] (peek page-stack)
            m {:init-state {:core-comm comm}}]
        (dom/div nil
          (dom/div #js {:id "header"}
            (om/build header
              {:title "Virt"
               :left-button {:show (not (= page :virt.home/home))
                             :text "Back"
                             :onClick #(put! comm [:navigate [:back]])}
               :right-button {:show (= page :virt.home/home)
                              :text "New"
                              :onClick #(put! comm [:navigate [:push [:virt.home/new]]])}}))
          (dom/div #js {:id "content"}
            (let [app (keyword (namespace page))
                  cursor {:page page :params params :data (get data app)}]
              (case app
                :virt.home (om/build home/main cursor m)
                :virt.chat (om/build chat/main cursor m)
                nil))))))))

(go
  ; TODO: do authentication after mounting and make login a page instead of a new root
  (let [response (<! (http/get "/api/session"))]
    (if (= (:body response) :no-active-session)
      (<! (mount-login))))
  (let [stack (path-to-stack routes (.. js/document -location -pathname))]
    ;(swap! app-state assoc :page-stack stack)
    (om/root main app-state {:target (.getElementById js/document "app")
                             :init-state {:page-stack stack}})))
