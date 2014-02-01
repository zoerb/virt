(ns virt-clj.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]]
                   [secretary.macros :refer [defroute]])
  (:require [goog.events :as events]
            [cljs.core.async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [secretary.core :as secretary]
            [cljs-http.client :as http]
            [clojure.browser.repl :as repl]
            [virt-clj.utils :refer [find-in-vec]])
  (:import [goog History]
           [goog.history Html5History]
           [goog.history EventType]))


(enable-console-print!)
(repl/connect "http://localhost:9000/repl")

(def app-state
  (atom {:cosms [{:id 1 :title "hi"}
                 {:id 2 :title "howdy" :owner {:name "ms"}}
                 {:id 3 :title "how's it goin" :owner {:name "mr"}}]
         :page-title ""}))


(defroute "/" []
  (swap! app-state assoc :page-title "Virt"))

(defroute ":cosm-id" [cosm-id]
  (swap! app-state
         assoc :page-title (:title (find-in-vec [:id]
                                                (cljs.reader/read-string cosm-id)
                                                (:cosms @app-state)))))

(def history (Html5History.))

(events/listen history EventType.NAVIGATE
  (fn [e]
    (.log js/console (.-token e))
    (secretary/dispatch! (.-token e))))

(.setUseFragment history false)
(.setEnabled history true)


(defn list-item [item owner]
  (reify
    om/IRender
    (render [_]
      (dom/li nil (:title item)))))

(defn header [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/header nil
        (dom/div nil (dom/button #js {:id "back-button" :className "transparent-button"} "Back"))
        (dom/div nil (dom/div #js {:id "header-title"} (:page-title app)))
        (dom/div nil (dom/button #js {:id "new-button" :className "transparent-button"} "New"))))))

(defn main [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
        (om/build header app)
        (dom/ul #js {:id "cosms-list" :className "virt-list"}
          (om/build-all list-item (:cosms app)))))))

(defn virt-clj-app [app owner]
  (reify
    om/IRender
    (render [_]
      (om/build main app))))

(om/root app-state virt-clj-app (.getElementById js/document "content"))
