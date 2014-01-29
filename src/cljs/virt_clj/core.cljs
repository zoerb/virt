(ns virt-clj.core
    (:require-macros [cljs.core.async.macros :refer [go alt!]]
                     [secretary.macros :refer [defroute]])
    (:require [goog.events :as events]
              [cljs.core.async :refer [put! <! >! chan timeout]]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [secretary.core :as secretary]
              [cljs-http.client :as http]
              [clojure.browser.repl :as repl])
    (:import [goog History]
             [goog.history EventType]))


(enable-console-print!)
(repl/connect "http://localhost:9000/repl")

(def app-state
  (atom {:cosms [{:id 1 :text "hi"} {:id 2 :text "howdy"} {:id 3 :text "how's it goin"}]
         :current-title {:text "THE jjjjjj TITLE"}}))


;(defroute "/" [] (swap! app-state assoc :current-title {:text "title"}))

;(defroute "/:cosm-id" [cosm-id] (swap! app-state assoc :showing (keyword filter)))

(def history (History.))

(events/listen history EventType.NAVIGATE
  (fn [e] (secretary/dispatch! (.-token e))))

(.setEnabled history true)


(defn list-item [item owner]
  (reify
    om/IRender
    (render [_]
      (dom/li nil (:text item)))))

(defn header [current-title owner]
  (reify
    om/IRender
    (render [_]
      (dom/header nil
        (dom/div nil (dom/button #js {:id "back-button" :className "transparent-button"} "Back"))
        (dom/div nil (dom/div #js {:id "header-title"} (:text current-title)))
        (dom/div nil (dom/button #js {:id "new-button" :className "transparent-button"} "New"))))))

(defn main [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
        (om/build header (:current-title app))
        (dom/ul #js {:id "cosms-list" :className "virt-list"}
          (om/build-all list-item (:cosms app)))))))

(defn virt-clj-app [app owner]
  (reify
    om/IRender
    (render [_]
      (om/build main app))))

(om/root app-state virt-clj-app (.getElementById js/document "content"))
