(ns virt.chat
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]
            virt.utils))


(def app-state (atom {}))


(defn header [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm show-new-button]}]
      (dom/header nil
        (dom/div nil
          (dom/button #js {:id "home-button"
                           :className "transparent-button"
                           :onClick #(set! (.-location js/window) "/")}
                      "Home"))
        (dom/div nil
          (dom/div #js {:id "header-title"} "Chat"))
        (dom/div nil
          (if show-new-button
            (dom/button #js {:id "new-button"
                             :className "transparent-button"
                             :onClick #(put! comm [:navigate :new])}
                        "New")))))))

(defn leaf-chat [chat owner {:keys [chat-id]}]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [wsUri (str "ws://" window.location.host (str "/api/chat/" (om/get-shared owner :channel-id) "/" chat-id))
            ws (js/WebSocket. wsUri)
            comm (chan)]
        (set! (.-onmessage ws)
          (fn [e]
            ;(om/update! chat :messages (cljs.reader/read-string (.-data e)))
            (om/transact! chat :messages #(conj % (.-data e)))))
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
      (dom/div #js {:className "leaf-chat"}
        (apply dom/ul #js {:className "virt-list"}
          (om/build-all
            (fn [message owner]
              (reify
                om/IRender
                (render [_] (dom/li nil message))))
            (:messages chat)))
        (dom/form
          #js {:onSubmit
               (fn [e]
                 (.preventDefault e)
                 (let [message-input (om/get-node owner "message-input")
                       msg (.-value message-input)]
                   (if-not (empty? msg)
                     (do
                       #_(om/transact! chat :messages
                                       #(conj % msg))
                       (put! (om/get-state owner :comm) [:send-message msg])
                       (set! (.-value message-input) "")))))}
          (dom/input #js {:ref "message-input"}))))))

(defn chat-root [app owner]
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
          app)))))

(defn new-chat [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/form #js {:className "new-chat"}
        (dom/input #js {:ref "new-chat-input" :placeholder "Title" :autoFocus true})
        (dom/button
          #js {:className "transparent-button"
               :onClick (fn [e]
                          (.preventDefault e)
                          (put! comm [:new-chat
                                      (.-value (om/get-node owner "new-chat-input"))]))}
          "Create")))))


(defn main [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:comm (chan)
       :page-id nil})
    om/IWillMount
    (will-mount [_]
      (let [channel-id (om/get-shared owner :channel-id)]
        (go (let [response (<! (http/get (str "/api/chat/" channel-id)))]
              (om/update! app (:body response))))
        (let [comm (om/get-state owner :comm)]
          (go (while true
                (let [[msg value] (<! comm)]
                  (case msg
                    :navigate
                    (om/set-state! owner :page-id value)
                    :new-chat
                    (let [response
                          (<! (http/post "/api/chats"
                                         {:edn-params {:name value
                                                       :channel-id channel-id}}))]
                      (om/update! app (:body response))
                      (om/set-state! owner :page-id nil))
                    nil)))))))
    om/IRenderState
    (render-state [_ {:keys [comm page-id]}]
      (let [m {:init-state {:comm comm}}]
        (dom/div nil
          (dom/div #js {:id "header"}
            (om/build header app (assoc m :state {:show-new-button (= page-id nil)})))
          (dom/div #js {:id "content"}
            (case page-id
              :new (om/build new-chat app m)
              nil (om/build chat-root app m)
              (om/build leaf-chat
                        (get app page-id)
                        (assoc m :opts {:chat-id page-id})))))))))

(om/root main app-state
         {:target (.getElementById js/document "app")
          :shared {:channel-id (cljs.reader/read-string (virt.utils/parse-url-param "id"))}})
