(ns virt-clj.core
    (:require-macros [cljs.core.async.macros :refer [go alt!]]
                     [secretary.macros :refer [defroute]])
    (:require [goog.events :as events]
              [cljs.core.async :refer [put! <! >! chan timeout]]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [secretary.core :as secretary]
              [cljs-http.client :as http])
    (:import [goog History]
             [goog.history EventType]))


(enable-console-print!)

(def app-state
  (atom {:cosms [{:id 1 :text "hi"} {:id 2 :text "howdy"} {:id 3 :text "how's it goin"}]}))


(defroute "/" [] (swap! app-state assoc :showing :all))

(defroute "/:filter" [filter] (swap! app-state assoc :showing (keyword filter)))

(def history (History.))

(events/listen history EventType.NAVIGATE
  (fn [e] (secretary/dispatch! (.-token e))))

(.setEnabled history true)


(defn list-item-component [item owner]
  (reify
    om/IRender
    (render [_]
      (dom/li nil (:text item)))))

(defn main [{:keys [cosms] :as app} owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
        (dom/div #js {:id "header"}
          (dom/button #js {:id "back-button" :className "transparent-button"} "Back"))
        (dom/ul #js {:id "cosms-list" :className "virt-list"}
          (om/build-all list-item-component cosms))))))

(defn virt-clj-app [{:keys [cosms] :as app} owner]
  (reify
    om/IRender
    (render [_]
      (om/build main app))))

(om/root app-state virt-clj-app (.getElementById js/document "content"))
