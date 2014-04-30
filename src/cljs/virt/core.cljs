(ns virt.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [goog.events :as events]
            [cljs.core.async :as async :refer [put! <! >! chan timeout]]
            cljs.reader
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [secretary.core :as secretary :include-macros true :refer [defroute]]
            [cljs-http.client :as http]
            virt.cosm-list)
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
    (render-state [_ {:keys [comm title show-home-button]}]
      (dom/header nil
        (dom/div nil
          (if show-home-button
            (dom/button #js {:id "home-button"
                             :className "transparent-button"
                             :onClick #(put! comm [:set-app {:app :home}])}
                        "Home")))
        (dom/div nil
          (dom/div #js {:id "header-title"} title))
        (dom/div nil
          (dom/button #js {:id "new-button"
                           :className "transparent-button"}
                      "New"))))))

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
                  (case (:app data)
                    :home
                    (do
                      (virt.cosm-list/attach content-target app-state comm)
                      (om/set-state! owner :show-home-button false))
                    :chat
                    (do
                      (virt.chat/attach content-target (:id data) comm)
                      (om/set-state! owner :show-home-button true)))
                  ;:set-header-text
                  ;(om/set-state! owner :title value)
                  nil))))
        (set-up-history comm)
        (go (>! comm [:set-app {:app :home}]))))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (om/build header app {:init-state {:comm comm}
                            :state {:title (om/get-state owner :title)
                                    :show-home-button (om/get-state owner :show-home-button)}}))))


(defn add-script [comm src]
  (let [head (aget (.getElementsByTagName js/document "head") 0)
        script (.createElement js/document "script")]
    (set! (.-type script) "text/javascript")
    (set! (.-src script) src)
    (set! (.-onload script) #(async/close! comm))
    ; Append to head or elsewhere?
    (.appendChild head script)))

(defn init-page []
  (go
    (let [response (<! (http/get "/api/cosms"))
          body (:body response)]
      ; Wait for all scripts to load
      (while (<!
               (async/merge
                 (doall
                   (for [app (:apps body)]
                     (let [c (chan)]
                       (add-script c (:src (second app)))
                       c))))))
      (reset! app-state body)
      ; TODO: load main app immediately, but wait for scripts before allowing cosms to be entered
      (om/root main app-state {:target (.getElementById js/document "header")}))))

(init-page)
