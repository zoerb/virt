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
    (render-state [_ {:keys [comm]}]
      (dom/header nil
        (dom/div nil
          (dom/button #js {:id "home-button"
                           :className "transparent-button"
                           :onClick #(set! (.-location js/window) "/")}
                      "Home"))
        (dom/div nil
          (dom/div #js {:id "header-title"} "Chat"))
        (dom/div nil
          (dom/button #js {:id "new-button"
                           :className "transparent-button"
                           :onClick #(put! comm [:navigate :new])}
                      "New"))))))

(defn leaf-channel [channel owner {:keys [channel-id]}]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [wsUri (str "ws://" window.location.host (str "/api/chat/" (om/get-shared owner :cosm-id) "/" channel-id))
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
      (dom/div #js {:className "leaf-channel"}
        (apply dom/ul #js {:className "virt-list"}
          (om/build-all
            (fn [message owner]
              (reify
                om/IRender
                (render [_] (dom/li nil message))))
            (:messages channel)))
        (dom/form
          #js {:onSubmit
               (fn [e]
                 (.preventDefault e)
                 (let [message-input (om/get-node owner "message-input")
                       msg (.-value message-input)]
                   (if-not (empty? msg)
                     (do
                       #_(om/transact! channel :messages
                                       #(conj % msg))
                       (put! (om/get-state owner :comm) [:send-message msg])
                       (set! (.-value message-input) "")))))}
          (dom/input #js {:ref "message-input"}))))))

(defn branch-channel [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm channel]}]
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

(defn new-channel [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/form #js {:className "new-channel"}
        (dom/input #js {:ref "new-channel-input" :placeholder "Title" :autoFocus true})
        (dom/button
          #js {:className "transparent-button"
               :onClick (fn [e]
                          (.preventDefault e)
                          (put! comm [:new-channel
                                      (.-value (om/get-node owner "new-channel-input"))]))}
          "Create")))))


(defn main [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:comm (chan)
       :page-stack []})
    om/IWillMount
    (will-mount [_]
      (let [cosm-id (om/get-shared owner :cosm-id)]
        (go (let [response (<! (http/get (str "/api/chat/" cosm-id)))]
              (om/update! app (:body response))))
        (let [comm (om/get-state owner :comm)]
          (go (while true
                (let [[msg value] (<! comm)]
                  (case msg
                    :navigate
                    (om/update-state! owner :page-stack #(conj % value))
                    :new-channel
                    (let [response
                          (<! (http/post "/api/chats"
                                         {:edn-params {:name value
                                                       :cosm-id cosm-id
                                                       :channel-id (last (butlast (om/get-state owner :page-stack)))}}))]
                      (om/update! app (:body response))
                      (om/update-state! owner :page-stack #(butlast %)))
                    nil)))))))
    om/IRenderState
    (render-state [_ {:keys [comm page-stack]}]
      (dom/div nil
        (dom/div #js {:id "header"}
          (om/build header app {:init-state {:comm comm}}))
        (dom/div #js {:id "content"}
          (let [page-id (last page-stack)
                m {:init-state {:comm comm}}]
            (case page-id
              :new (om/build new-channel app m)
              (let [cur-channel (get (:channels app)
                                     (or page-id (:root-channel app)))]
                (case (:node-type cur-channel)
                  :branch (om/build branch-channel app (assoc m :state {:channel cur-channel}))
                  :leaf (om/build leaf-channel cur-channel (assoc m :opts {:channel-id page-id}))
                  nil)))))))))

(om/root main app-state
         {:target (.getElementById js/document "app")
          :shared {:cosm-id (cljs.reader/read-string (virt.utils/parse-url-param "id"))}})
