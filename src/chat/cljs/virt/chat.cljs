(ns virt.chat
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]))


(def app-state
  (atom {:threads {}
         :messages {}}))


(defn header [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm show-new-button]}]
      (dom/header nil
        (dom/div nil
          (dom/button #js {:id "back-button"
                           :className "transparent-button"
                           :onClick #(put! comm [:navigate :back])}
                      "Back"))
        (dom/div nil
          (dom/div #js {:id "header-title"} "Chat"))
        (dom/div nil
          (if show-new-button
            (dom/button #js {:id "new-button"
                             :className "transparent-button"
                             :onClick #(put! comm [:navigate :new])}
                        "New")))))))

(defn leaf-chat [messages owner {:keys [thread-id]}]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [wsUri (str "ws://" window.location.host (str "/api/chat/ws/" thread-id))
            ws (js/WebSocket. wsUri)
            comm (chan)]
        (set! (.-onmessage ws)
          (fn [e]
            (om/transact! messages #(conj % (.-data e)))))
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

(defn chat-root [threads owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (apply dom/ul #js {:className "virt-list"}
        (om/build-all
          (fn [thread owner]
            (reify
              om/IRender
              (render [_]
                (dom/li #js {:onClick (fn [e] (put! comm [:navigate (:id thread)]))}
                        (:description thread)))))
          threads)))))

(defn new-thread [threads owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/form #js {:className "new-thread"}
        (dom/input #js {:ref "new-thread-input" :placeholder "Title" :autoFocus true})
        (dom/button
          #js {:className "transparent-button"
               :onClick (fn [e]
                          (.preventDefault e)
                          (put! comm [:new-thread
                                      (.-value (om/get-node owner "new-thread-input"))]))}
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
        (go (let [response (<! (http/get (str "/api/chat/threads/" channel-id)))]
              (om/update! app [:threads] (:body response))))
        (let [comm (om/get-state owner :comm)]
          (go (while true
                (let [[msg value] (<! comm)]
                  (case msg
                    :navigate
                    (case value
                      :new (om/set-state! owner :page-id value)
                      :back (if (= (om/get-state owner :page-id) nil)
                              (set! (.-location js/window) "/")
                              (om/set-state! owner :page-id nil))
                      (do
                        (om/update! app [:messages value] [])
                        (om/set-state! owner :page-id value)))
                    :new-thread
                    (let [response
                          (<! (http/post "/api/chat/threads"
                                         {:edn-params {:channel-id channel-id
                                                       :thread-descr value}}))]
                      (om/update! app [:threads] (:body response))
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
              :new (om/build new-thread (:threads app) m)
              nil (om/build chat-root (:threads app) m)
              (om/build leaf-chat
                        (get (:messages app) page-id)
                        (assoc m :opts {:thread-id page-id})))))))))

(om/root main app-state
         {:target (.getElementById js/document "app")
          :shared {:channel-id (-> js/window
                                   .-location
                                   http/parse-url
                                   :query-params
                                   :id
                                   cljs.reader/read-string)}})
