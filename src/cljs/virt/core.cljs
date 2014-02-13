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
            [virt.utils :refer []])
  (:import [goog History]
           [goog.history Html5History]
           [goog.history EventType]))


(enable-console-print!)
(repl/connect "http://localhost:9000/repl")

(def app-state
  (atom {:channels {0x001 {:title "hi" :children #{0xAA0 0xAA1 0xAA2}}
                    0xAA0 {:title "hi1"}
                    0xAA1 {:title "hi2"}
                    0xAA2 {:title "hi3"}
                    0x002 {:title "howdy"}
                    0x003 {:title "how's it goin"}}
         :top-level-channels #{0x001 0x002 0x003}}))


(defn set-routes [owner]
  (defroute "/" []
    (om/set-state! owner :page :top))
  (defroute ":channel-id" [channel-id]
    (om/set-state! owner :page (cljs.reader/read-string channel-id))))

(def navigate
  (let [history (Html5History.)]
    (.setUseFragment history false)
    (.setEnabled history true)
    (events/listen history EventType.NAVIGATE
      (fn [e] (secretary/dispatch! (.-token e))))
    (fn [to]
      (case to
        :up (.setToken history "")
        (.setToken history to)))))


(defn list-item [id-item owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (let [id (first id-item)
            item (second id-item)
            href (str id)]
        (dom/a #js {:href href
                    :onClick (fn [e] (.preventDefault e) 
                                     (put! comm [:navigate href]))
                    :className "list-link"}
               (dom/li nil (:title item)))))))

(defn header [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm page]}]
      (dom/header nil
        (dom/div nil
          (if (not= page :top)
            (dom/button #js {:id "back-button"
                             :className "transparent-button"
                             :onClick #(put! comm [:navigate :up])}
                        "Back")))
        (dom/div nil
          (dom/div #js {:id "header-title"}
                   (if (= page :top)
                     "Virt"
                     (:title (get (:channels app) page)))))
        (dom/div nil
          (dom/button #js {:id "new-button"
                           :className "transparent-button"}
                      "New"))))))

(defn handle-event [msg value]
  (case msg
    :navigate (navigate value)
    nil))

(defn main [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:page :top})
    om/IWillMount
    (will-mount [_]
      (let [comm (chan)]
        (om/set-state! owner :comm comm)
        (go (while true
              (let [[msg value] (<! comm)]
                (handle-event msg value)))))
      (set-routes owner))
    om/IRenderState
    (render-state [_ {:keys [comm page]}]
      (dom/div nil
        (om/build header app {:init-state {:comm comm} :state {:page page}})
        (apply dom/ul #js {:className "virt-list"}
          (om/build-all list-item
            (let [cur-channels (if (= page :top)
                                 (:top-level-channels app)
                                 (:children (get (:channels app) page)))]
              (select-keys (:channels app) cur-channels))
            {:init-state {:comm comm}}))))))

(om/root app-state main (.getElementById js/document "content"))
