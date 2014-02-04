(ns virt.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]]
                   [secretary.macros :refer [defroute]])
  (:require [goog.events :as events]
            [cljs.core.async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [secretary.core :as secretary]
            [cljs-http.client :as http]
            [clojure.browser.repl :as repl]
            [virt.utils :refer [find-in-vec find-all-in-vec]])
  (:import [goog History]
           [goog.history Html5History]
           [goog.history EventType]))


(enable-console-print!)
(repl/connect "http://localhost:9000/repl")

(def app-state
  (atom {:channels [{:id 0x001 :title "hi" :children [0xAA0 0xAA1 0xAA2]}
                    {:id 0xAA0 :title "hi1" :parent 0x001}
                    {:id 0xAA1 :title "hi2" :parent 0x001}
                    {:id 0xAA2 :title "hi3" :parent 0x001}
                    {:id 0x002 :title "howdy"}
                    {:id 0x003 :title "how's it goin"}]
         :top-level-channels [0x001 0x002 0x003]
         :current-channel nil}))


(defroute "/" []
  (swap! app-state assoc :current-channel nil))

(defroute ":channel-id" [channel-id]
  (println channel-id)
  (swap! app-state assoc :current-channel (cljs.reader/read-string channel-id)))

(def history (Html5History.))

(events/listen history EventType.NAVIGATE
  (fn [e] (secretary/dispatch! (.-token e))))

(.setUseFragment history false)
(.setEnabled history true)


(defn list-item [item owner]
  (reify
    om/IRender
    (render [_]
      (dom/a #js {:href (str "/" (:id item)) :className "list-link"} (dom/li nil (:title item))))))

(defn header [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/header nil
        (dom/div nil (dom/button #js {:id "back-button" :className "transparent-button" :onClick #(. js/history back)} "Back"))
        (dom/div nil (dom/div #js {:id "header-title"} (if-let [current-channel (:current-channel app)]
                                                         (:title (find-in-vec [:id] current-channel (:channels app)))
                                                         "Virt")))
        (dom/div nil (dom/button #js {:id "new-button" :className "transparent-button"} "New"))))))

(defn main [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
        (om/build header app)
        (let [list-items (if-let [current-channel (:current-channel app)]
                           (:children (find-in-vec [:id] current-channel (:channels app)))
                           (:top-level-channels app))]
          (dom/ul #js {:className "virt-list"}
            (om/build-all list-item (find-all-in-vec [:id] list-items (:channels app)))))))))

(defn virt-app [app owner]
  (reify
    om/IRender
    (render [_]
      (om/build main app))))

(om/root app-state virt-app (.getElementById js/document "content"))
