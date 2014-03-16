(ns virt.chat
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [virt.utils :refer []]))


(def app-state
  (atom {:root-channel {:title "Chat" :node-type :branch :children #{0x001 0x002 0x003}}
         :channels {0x001 {:title "hi" :node-type :branch :children #{0xAA0 0xAA1 0xAA2}}
                    0xAA0 {:title "hi1" :node-type :leaf :messages ["o hullo there" "howdy"]}
                    0xAA1 {:title "hi2" :node-type :leaf}
                    0xAA2 {:title "hi3" :node-type :leaf}
                    0x002 {:title "howdy" :node-type :branch}
                    0x003 {:title "how's it goin" :node-type :branch}}}))


(defn leaf-channel [channel owner {:keys [id]}]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [wsUri (str "ws://" window.location.host (str "/api/watch/" id))
            ws (js/WebSocket. wsUri)
            comm (chan)]
        (set! (.-onmessage ws)
          (fn [e]
            ;(om/update! channel :messages (cljs.reader/read-string (.-data e)))
            (om/transact! channel :messages #(conj % (.-data e)))))
        (om/set-state! owner :comm comm)
        (go (while true
              (let [[msg value] (<! comm)]
                (case msg
                  :send-message (.send ws value)
                  :close (.close ws)
                  nil))))))
    om/IWillUnmount
    (will-unmount [_]
      (go (>! (om/get-state owner :comm) [:close])))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/div nil
        (apply dom/ul #js {:className "virt-list"}
          (om/build-all
            (fn [message owner]
              (reify
                om/IRender
                (render [_] (dom/li nil message))))
            (:messages channel)))
        (dom/form #js {:onSubmit (fn [e]
                                   (.preventDefault e)
                                   (let [msg (om/get-state owner :value)]
                                     #_(om/transact! channel :messages
                                       #(conj % msg))
                                     (put! (om/get-state owner :comm) [:send-message msg]))
                                   (om/set-state! owner :value ""))}
                  (dom/input #js {:onChange (fn [e] (om/set-state! owner :value (.-value (.-target e))))
                                  :value (om/get-state owner :value)}))))))

(defn branch-channel [app owner {:keys [channel]}]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (apply dom/ul #js {:className "virt-list"}
        (om/build-all
          (fn [id-item owner]
            (reify
              om/IRender
              (render [_]
                (let [id (first id-item)
                      item (second id-item)]
                  (dom/li #js {:onClick (fn [e] (put! comm [:navigate id]))}
                           (:title item))))))
          (select-keys (:channels app) (:children channel)))))))


(defn main [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:page-stack []})
    om/IWillMount
    (will-mount [_]
      (let [comm (chan)]
        (om/set-state! owner :comm comm)
        (go (while true
              (let [[msg value] (<! comm)]
                (case msg
                  :navigate (om/set-state! owner :page-stack (conj (om/get-state owner :page-stack) value))
                  :set-header-text (go (>! (om/get-shared owner :api-comm) [msg value]))
                  :message (.log js/console value)
                  nil))))))
    om/IRenderState
    (render-state [_ {:keys [comm page-stack]}]
      (dom/div nil
        (let [cur-channel-id (last page-stack)
              cur-channel (if (not cur-channel-id)
                            (:root-channel app)
                            (get (:channels app) cur-channel-id))
              m {:init-state {:comm comm}}]
          (case (:node-type cur-channel)
            :branch (om/build branch-channel app (assoc m :opts {:channel cur-channel}))
            :leaf (om/build leaf-channel cur-channel (assoc m :opts {:id cur-channel-id}))))))))

(defn attach [target comm]
  (om/root main app-state {:target target :shared {:api-comm comm}}))
