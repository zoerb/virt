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
                           :className "transparent-button"}
                      "New"))))))

(defn list-item [id-item owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (let [id (first id-item)
            item (second id-item)
            app (:app item)]
        (dom/li #js {:onClick (fn [e] (put! comm [:set-app {:app app :id id}]))}
                (:title item))))))

(defn cosm-list [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/div #js {:id "cosm-content"}
        (apply dom/ul #js {:className "virt-list"}
          (om/build-all list-item (:cosms app) {:init-state {:comm comm}}))))))

(defn main [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [comm (chan)]
        (om/set-state! owner :comm comm)
        (go (while true
              (let [[msg data] (<! comm)]
                (case msg
                  :set-app
                  (let [cosm-link (:link ((:app data) (:apps @app-state)))]
                    (set! (.-location js/window) (str cosm-link "?id=" (:id data))))
                  nil))))
        (set-up-history comm)))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/div nil
        (dom/div #js {:id "header"}
          (om/build header app {:init-state {:comm comm}}))
        (dom/div #js {:id "content"}
          (om/build cosm-list app {:init-state {:comm comm}}))))))

(defn init-page []
  (go
    (let [response (<! (http/get "/api/cosms"))
          body (:body response)]
      (reset! app-state body)
      (om/root main app-state {:target (.getElementById js/document "app")}))))

(init-page)
