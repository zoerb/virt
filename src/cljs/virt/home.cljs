(ns virt.home
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]
            [virt.geolocation :refer [get-geolocation]]))


(def app-state
  {:channels []
   :geolocation nil})

(defn loading [_ _]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:id "loading"} "Loading..."))))

(defn list-item [channel owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/li #js {:onClick (fn [e] (put! comm [:navigate [:push [:virt.chat/home channel]]]))}
        (dom/div #js {:className "name"} (:name channel))
        (dom/div #js {:className "aux"} (name (:app channel)))))))

(defn channel-list [{:keys [params data]} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (om/set-state! owner :interval-chan
        (go
          (let [response (<! (http/get "/api/channels" {:query-params (:geolocation data)}))]
            (om/update! data [:channels] (:body response)))
          (js/setInterval
            #(go
               (let [response (<! (http/get "/api/channels" {:query-params (:geolocation data)}))]
                 (om/update! data [:channels] (:body response))))
            3000))))
    om/IWillUnmount
    (will-unmount [_]
      (go
        (js/clearInterval (<! (om/get-state owner :interval-chan)))))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/div nil
        (apply dom/ul #js {:className "virt-list"}
          (om/build-all list-item (:channels data) {:init-state {:comm comm}}))))))

(defn new-channel [_ owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/form #js {:className "full-width-form"}
        (dom/input #js {:ref "new-channel-input" :placeholder "Title" :autoFocus true})
        (dom/button
          #js {:className "transparent-button"
               :onClick (fn [e]
                          (.preventDefault e)
                          (put! comm [:new-channel
                                      (.-value (om/get-node owner "new-channel-input"))]))}
          "Create")))))

(defn main [{:keys [page params data]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:comm (chan)
       :loading false})
    om/IWillMount
    (will-mount [_]
      (when (empty? (:geolocation data))
        (om/set-state! owner :loading true))
      (go
        (let [loc (<! (get-geolocation))]
          (om/update! data [:geolocation] loc))
        (om/set-state! owner :loading false))
      (go
        (let [comm (om/get-state owner :comm)
              core-comm (om/get-state owner :core-comm)]
          (while true
            (let [[msg msg-data] (<! comm)]
              (case msg
                ; TODO: remove/re-evaluate cursor derefs when upgrading to Om 0.8
                :new-channel
                (let [response
                      (<! (http/post "/api/channels"
                                     {:edn-params {:channel-name msg-data
                                                   :geolocation (:geolocation @data)}}))]
                  (om/transact! data [:channels] (fn [cs] (conj cs (:body response))))
                  (put! comm [:navigate [:back]]))
                (>! core-comm [msg msg-data])))))))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (let [m {:init-state {:comm comm}}]
        (if (om/get-state owner :loading)
          (om/build loading nil)
          (case page
            ::new (om/build new-channel nil m)
            ::home (om/build channel-list {:params params :data data} m)))))))
