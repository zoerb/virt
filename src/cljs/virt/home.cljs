(ns virt.home
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]
            [virt.geolocation :refer [get-geolocation]]))


(def app-state
  {:channel-types {}
   :channels []
   :geolocation nil})

(defn loading [_ _]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:id "loading"} "Loading..."))))

(defn list-item [channel owner {:keys [channel-types]}]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/li #js {:onClick (fn [e] (put! comm [:navigate [:push [(:channel-type channel) :home channel]]]))}
        (dom/div #js {:className "name"} (:name channel))
        (dom/div #js {:className "aux"} (:name ((:channel-type channel) channel-types)))))))

(defn channel-list [{:keys [params data]} owner {:keys [channel-types]}]
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
          (om/build-all list-item (:channels data) {:init-state {:comm comm}
                                                    :opts {:channel-types channel-types}}))))))

(defn radio-selector [values owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected nil})
    om/IRenderState
    (render-state [_ {:keys [selected]}]
      (apply dom/div #js {:ref "channel-type" :id "radio-selector"}
        (map (fn [[id ch-type]]
               (dom/div #js {:className (str "radio-option" (if (= selected (name id)) " radio-option-selected"))
                             :value (name id)
                             :onClick #(om/set-state! owner :selected (name id))}
                 (dom/img #js {:src "img/check.png" :width 16 :height 16 :className "check"})
                 (dom/span nil
                   (:name ch-type))))
             values)))))

(defn new-channel [_ owner {:keys [channel-types]}]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/form #js {:className "full-width-form"}
        (dom/input #js {:ref "channel-name" :type "text" :placeholder "Title" :autoFocus true})
        (om/build radio-selector channel-types)
        (dom/button
          #js {:className "transparent-button"
               :onClick (fn [e]
                          (.preventDefault e)
                          (put! comm [:new-channel
                                      [(.-value (om/get-node owner "channel-name"))
                                       (.-value (om/get-node owner "channel-type"))]]))}
          "Create")))))

(defn main [{:keys [page params data]} owner opts]
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
                                     {:edn-params {:channel-name (first msg-data)
                                                   :channel-type (second msg-data)
                                                   :geolocation (:geolocation @data)}}))]
                  (om/transact! data [:channels] (fn [cs] (conj cs (:body response))))
                  (put! comm [:navigate [:back]]))
                (>! core-comm [msg msg-data])))))))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (let [m {:init-state {:comm comm}
               :opts opts}]
        (if (om/get-state owner :loading)
          (om/build loading nil)
          (case page
            :new (om/build new-channel nil m)
            :home (om/build channel-list {:params params :data data} m)))))))
