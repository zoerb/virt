(ns virt.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]
            [virt.header :refer [header]]
            [virt.home :as home]
            [virt.chat :as chat]
            [virt.poll :as poll]))


(def state
  (atom {:home virt.home/app-state}))

(def channel-types
  (atom {}))

(defn add-channel-type [id app-name app-state render-func]
  (swap! state assoc id app-state)
  (swap! channel-types
         assoc id {:name app-name
                   :render-func render-func}))

(add-channel-type :chat "Chat" virt.chat/app-state virt.chat/main)
(add-channel-type :poll "Poll" virt.poll/app-state virt.poll/main)

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
          (dom/input #js {:ref "username-input" :type "text" :placeholder "Username" :autoFocus true})
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
      (let [comm (om/get-state owner :comm)]
        (go
          (while true
            (let [[msg msg-data] (<! comm)]
              (case msg
                :navigate
                (let [page-stack (om/get-state owner [:page-stack])
                      [nav-type new-frame] msg-data
                      new-stack
                      (case nav-type
                        :back (pop page-stack)
                        :push (conj page-stack new-frame))]
                  (om/set-state! owner [:page-stack] new-stack))
                nil))))))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (let [page-stack (om/get-state owner :page-stack)
            [app page params] (peek page-stack)
            m {:init-state {:core-comm comm}}]
        (dom/div nil
          (dom/div #js {:id "header"}
            (om/build header
              {:title "Virt"
               :left-button {:show true #_(not (or (= app :home) (= page :core)))
                             :text "Back"
                             :onClick #(put! comm [:navigate [:back]])}
               :right-button {:show (= app :home)
                              :text "New"
                              :onClick #(put! comm [:navigate [:push [:home :new]]])}}))
          (dom/div #js {:id "content"}
            (let [cursor {:page page :params params :data (get data app)}]
              (case app
                :core (om/build login nil m)
                :home (om/build virt.home/main
                                cursor
                                (assoc m :opts {:channel-types @channel-types}))
                (om/build (:render-func (get @channel-types app)) cursor m)))))))))

(go
  (let [initial-stack [[:home :home]]
        response (<! (http/get "/api/session"))
        stack (if (= (:body response) :no-active-session)
                (conj initial-stack [:core :login])
                initial-stack)]
  (om/root main state {:target (.getElementById js/document "app")
                       :init-state {:page-stack stack}})))
