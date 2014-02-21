(ns virt.chat
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [virt.utils :refer []]))


(def app-state
  (atom {:channels {0x001 {:title "hi" :children #{0xAA0 0xAA1 0xAA2}}
                    0xAA0 {:title "hi1"}
                    0xAA1 {:title "hi2"}
                    0xAA2 {:title "hi3"}
                    0x002 {:title "howdy"}
                    0x003 {:title "how's it goin"}}
         :top-level-channels #{0x001 0x002 0x003}}))


(defn list-item [id-item owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (let [id (first id-item)
            item (second id-item)]
        (dom/a #js {:onClick (fn [e] (.preventDefault e)
                                     (put! comm [:navigate id]))
                    :className "list-link"}
               (dom/li nil (:title item)))))))

(defn main [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:page :main})
    om/IWillMount
    (will-mount [_]
      (let [comm (chan)]
        (om/set-state! owner :comm comm)
        (go (while true
              (let [[msg value] (<! comm)]
                (case msg
                  :navigate (om/set-state! owner :page value)
                  :set-header-text (go (>! (om/get-shared owner :api-comm) [msg value]))
                  nil))))))
    om/IRenderState
    (render-state [_ {:keys [comm page]}]
      (dom/div nil
        (apply dom/ul #js {:className "virt-list"}
          (om/build-all list-item
            (let [cur-channels (if (= page :main)
                                 (:top-level-channels app)
                                 (:children (get (:channels app) page)))]
              (select-keys (:channels app) cur-channels))
            {:init-state {:comm comm}}))))))

(defn attach [target comm]
  (om/root main app-state {:target target :shared {:api-comm comm}}))
