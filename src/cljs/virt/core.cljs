(ns virt.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]
            [virt.history :refer [listen-navigation set-history-path!]]
            [virt.router :refer [stack-to-path path-to-stack]]
            [virt.header :refer [header]]
            [virt.home :as home]
            [virt.chat :as chat]))


(def app-state
  (atom {:virt.home home/app-state
         :virt.chat chat/app-state}))

(def routes
  [[["/" [#".*" :rest]] :virt.home/home]
   ["" [["login" :virt.core/login]
        home/routes
        chat/routes]]])

(defn login [_ owner]
  (reify
    om/IInitState
    (init-state [_]
      {:comm (chan)})
    om/IWillMount
    (will-mount [_]
      (let [comm (om/get-state owner :comm)]
        (go
          (<! (http/post "/api/session" {:form-params {:username (<! comm)}}))
          (>! (om/get-state owner :core-comm) [:navigate [:back]]))))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/div #js {:id "content"}
        (dom/form #js {:className "full-width-form"}
          (dom/input #js {:ref "username-input" :placeholder "Username" :autoFocus true})
          (dom/button
            #js {:className "transparent-button"
                 :onClick (fn [e]
                            (.preventDefault e)
                            (put! comm (.-value (om/get-node owner "username-input"))))}
            "Login"))))))

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
               :left-button {:show (not (or (= page :virt.home/home) (= page :virt.core/login)))
                             :text "Back"
                             :onClick #(put! comm [:navigate [:back]])}
               :right-button {:show (= page :virt.home/home)
                              :text "New"
                              :onClick #(put! comm [:navigate [:push [:virt.home/new]]])}}))
          (dom/div #js {:id "content"}
            (let [app (keyword (namespace page))
                  cursor {:page page :params params :data (get data app)}]
              (case app
                :virt.core (om/build login nil m)
                :virt.home (om/build home/main cursor m)
                :virt.chat (om/build chat/main cursor m)
                nil))))))))

(go
  (let [path-stack (path-to-stack routes (.. js/document -location -pathname))
        response (<! (http/get "/api/session"))
        stack (if (= (:body response) :no-active-session)
                (conj path-stack [:virt.core/login])
                path-stack)]
  (om/root main app-state {:target (.getElementById js/document "app")
                           :init-state {:page-stack stack}})))
