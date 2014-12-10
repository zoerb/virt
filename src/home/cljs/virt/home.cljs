(ns virt.home
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [goog.events :as events]
            [cljs.core.async :as async :refer [put! <! >! chan timeout]]
            cljs.reader
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [secretary.core :as secretary :include-macros true :refer [defroute]]
            [cljs-http.client :as http]
            [virt.utils :as utils])
  (:import [goog History]
           [goog.history Html5History]
           [goog.history EventType]))


(def app-state
  (atom {:apps {}
         :channels {}
         :geolocation nil}))

(def parse-path
  (do
    (defroute "/" []
      :home)
    (defroute "/:channel-id" [channel-id]
      (cljs.reader/read-string channel-id))
    (fn [path]
      (secretary/dispatch! path))))

(defn set-up-history [comm]
  (let [history (Html5History.)]
    (.setUseFragment history false)
    (.setEnabled history true)
    (defn set-history-token [token] (.setToken history token))
    (events/listen history EventType.NAVIGATE
      (fn [e] (put! comm [:history-change (.-token e)])))))


(def content-target (.getElementById js/document "content"))

(defn header [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/header nil
        (dom/div nil)
        (dom/div nil
          (dom/div #js {:id "header-title"} "Virt"))
        (dom/div nil
          (dom/button #js {:id "new-button"
                           :className "transparent-button"
                           :onClick #(put! comm [:navigate :new])}
                      "New"))))))

(defn list-item [channel owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/li #js {:onClick (fn [e] (put! comm [:set-channel channel]))}
        (dom/div #js {:className "name"} (:name channel))
        (dom/div #js {:className "aux"} (name (:app channel)))))))

(defn channel-list [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/div nil
        (apply dom/ul #js {:className "virt-list"}
          (om/build-all list-item app {:init-state {:comm comm}}))))))

(defn new-channel [channels owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/form #js {:className "new-channel"}
        (dom/input #js {:ref "new-channel-input" :placeholder "Title" :autoFocus true})
        (dom/button
          #js {:className "transparent-button"
               :onClick (fn [e]
                          (.preventDefault e)
                          (put! comm [:new-channel
                                      (.-value (om/get-node owner "new-channel-input"))]))}
          "Create")))))

(defn main [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:comm (chan)
       :page-id nil
       :show-loading true})
    om/IWillMount
    (will-mount [_]
      (om/set-state! owner :show-loading true)
      (let [comm
            (om/get-state owner :comm)
            channels-loading
            (go
              (let [loc (<! (utils/get-geolocation))]
                (om/update! app [:geolocation] loc)
                (let [response (<! (http/get "/api/channels" {:query-params loc}))]
                  (om/update! app [:channels] (:body response))
                  (om/set-state! owner :show-loading false))))
            apps-loading
            (go
              (let [response (<! (http/get "/api/apps"))]
                (om/update! app [:apps] (:body response))))]
        (go
          ; Wait for data to load before registering callbacks
          (<! (async/merge [channels-loading apps-loading]))
          (while true
            (let [[msg data] (<! comm)]
              (case msg
                :set-channel
                (let [channel-link (:link ((:app data) (:apps @app)))]
                  (set! (.-location js/window) (str channel-link "?id=" (:id data))))
                :navigate
                (case data
                  :new (om/set-state! owner :page-id data)
                  :back (om/set-state! owner :page-id nil))
                :new-channel
                (let [response
                      (<! (http/post "/api/channels"
                                     {:edn-params {:channel-name data
                                                   :geolocation (:geolocation @app)}}))]
                  (om/transact! app [:channels] (fn [cs] (conj cs (:body response))))
                  (om/set-state! owner :page-id nil))
                nil))))
        (set-up-history comm)))
    om/IRenderState
    (render-state [_ {:keys [comm page-id]}]
      (let [m {:init-state {:comm comm}}]
        (dom/div nil
          (dom/div #js {:id "header"}
            (om/build header app m))
          (dom/div #js {:id "content"}
            (if (om/get-state owner :show-loading)
              (dom/div #js {:id "loading"} "Waiting for location...")
              (case page-id
                :new (om/build new-channel app m)
                nil (om/build channel-list (:channels app) m)))))))))

(om/root main app-state {:target (.getElementById js/document "app")})
