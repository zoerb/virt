(ns virt.chat
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [put! <! >! chan]]
            [cljs.reader]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))


(def app-state {:messages []})

(defn leaf-chat [{:keys [params messages]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:should-scroll-to-bottom true})
    om/IWillMount
    (will-mount [_]
      (let [wsUri (str "ws://" window.location.host "/api/chat/" (:channel-id params) "/watch")
            ws (js/WebSocket. wsUri)
            comm (chan)]
        (set! (.-onmessage ws)
          (fn [e]
            (let [[msg-type msg-data] (cljs.reader/read-string (.-data e))]
              (case msg-type
                :initial (om/update! messages msg-data)
                :message (om/transact! messages #(conj % msg-data))))))
        (om/set-state! owner :comm comm)
        (go (while true
              (let [[msg data] (<! comm)]
                (case msg
                  :send-message (.send ws (pr-str [:message {:message data}]))
                  :close (.close ws)
                  nil))))))
    om/IDidMount
    (did-mount [_]
      (if (om/get-state owner :should-scroll-to-bottom)
        (.scrollIntoView (om/get-node owner "leaf-chat") false)))
    om/IWillUnmount
    (will-unmount [_]
      (go (>! (om/get-state owner :comm) [:close]))
      (om/update! messages []))
    om/IWillUpdate
    (will-update [_ _ _]
      (om/set-state-nr! owner
                        :should-scroll-to-bottom
                        (>= (+ (.-innerHeight js/window) (.-scrollY js/window))
                            (.-scrollHeight (.-body js/document)))))
    om/IDidUpdate
    (did-update [_ _ _]
      (if (om/get-state owner :should-scroll-to-bottom)
        (.scrollIntoView (om/get-node owner "leaf-chat") false)))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/div #js {:ref "leaf-chat" :className "leaf-chat"}
        (apply dom/ul #js {:className "message-list uncollapse-margins"}
          (om/build-all
            (fn [message owner]
              (reify
                om/IRender
                (render [_]
                  (dom/li nil
                    (dom/div nil (:username message))
                    (:message message)))))
            messages))
        (dom/form
          #js {:onSubmit
               (fn [e]
                 (.preventDefault e)
                 (let [message-input (om/get-node owner "message-input")
                       msg (.-value message-input)]
                   (if-not (empty? msg)
                     (do
                       #_(om/transact! messages #(conj % msg))
                       (put! (om/get-state owner :comm) [:send-message msg])
                       (set! (.-value message-input) "")))))}
          (dom/input #js {:ref "message-input"}))))))

(defn main [{:keys [page params data]} owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:id "content"}
        (om/build leaf-chat
                  {:params params
                   :messages (:messages data)})))))
