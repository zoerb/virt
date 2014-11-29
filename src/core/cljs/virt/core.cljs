(ns virt.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [goog.events :as events]
            [cljs.core.async :as async :refer [put! <! >! chan timeout]]
            cljs.reader
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [secretary.core :as secretary :include-macros true :refer [defroute]]
            [cljs-http.client :as http])
  (:import [goog History]
           [goog.history Html5History]
           [goog.history EventType]))


(def app-state (atom {}))

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

(defn list-item [id-item owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (let [id (first id-item)
            item (second id-item)
            app (:app item)]
        (dom/li #js {:onClick (fn [e] (put! comm [:set-app {:app app :id id}]))}
          (dom/div #js {:className "name"} (:name item))
          (dom/div #js {:className "aux"} (name app)))))))

(defn channel-list [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (go
        (let [response (<! (http/get "/api/channels"))
              body (:body response)]
          (om/update! app body))))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/div #js {:id "channel-content"}
        (apply dom/ul #js {:className "virt-list"}
          (om/build-all list-item (:channels app) {:init-state {:comm comm}}))))))

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
       :page-id nil})
    om/IWillMount
    (will-mount [_]
      (let [comm (om/get-state owner :comm)]
        (go (while true
              (let [[msg data] (<! comm)]
                (case msg
                  :set-app
                  (let [channel-link (:link ((:app data) (:apps @app-state)))]
                    (set! (.-location js/window) (str channel-link "?id=" (:id data))))
                  :navigate
                  (case data
                    :new (om/set-state! owner :page-id data)
                    :back (om/set-state! owner :page-id nil))
                  :new-channel
                  (let [response
                        (<! (http/post "/api/channels"
                                       {:edn-params {:channel-name data}}))]
                    (om/update! app (:body response))
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
            (case page-id
              :new (om/build new-channel app m)
              nil (om/build channel-list app m))))))))

(om/root main app-state {:target (.getElementById js/document "app")})
