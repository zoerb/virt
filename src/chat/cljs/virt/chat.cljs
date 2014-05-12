(ns virt.chat
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [clojure.string :as string]
            [cljs.core.async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]))


(def app-state (atom {}))


(defn parse-url-param [param-name]
  (let [encoded-param-name (string/replace (js/encodeURI param-name) #"[\.\+\*]" "\\$&")
        re (js/RegExp (str "^(?:.*[&\\?]" encoded-param-name "(?:\\=([^&]*))?)?.*$") "i")]
    (js/decodeURI (.replace (.. js/window -location -search) re "$1"))))


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
                           :className "transparent-button"}
                      "New"))))))

(defn leaf-channel [channel owner {:keys [channel-id]}]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [wsUri (str "ws://" window.location.host (str "/api/watch/" (om/get-shared owner :cosm-id) "/" channel-id))
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
                 (let [msg (om/get-state owner :value)]
                   (if-not (empty? msg)
                     (do
                       #_(om/transact! channel :messages
                                       #(conj % msg))
                       (put! (om/get-state owner :comm) [:send-message msg])
                       (om/set-state! owner :value "")))))}
          (dom/input #js {:onChange (fn [e] (om/set-state! owner :value (.-value (.-target e))))
                          :value (om/get-state owner :value)}))))))

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


(defn main [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:page-stack []})
    om/IWillMount
    (will-mount [_]
      (go (let [response (<! (http/get (str "/api/cosm/" (om/get-shared owner :cosm-id))))]
            (om/update! app (:body response))))
      (let [comm (chan)]
        (om/set-state! owner :comm comm)
        (go (while true
              (let [[msg value] (<! comm)]
                (case msg
                  :navigate (om/set-state! owner :page-stack (conj (om/get-state owner :page-stack) value))
                  :message (.log js/console value)
                  nil))))))
    om/IRenderState
    (render-state [_ {:keys [comm page-stack]}]
      (dom/div nil
        (dom/div #js {:id "header"}
          (om/build header app))
        (dom/div #js {:id "content"}
          (let [cur-channel-id (last page-stack)
                cur-channel (if-not cur-channel-id
                              (:root-channel app)
                              (get (:channels app) cur-channel-id))
                m {:init-state {:comm comm}}]
            (case (:node-type cur-channel)
              :branch (om/build branch-channel app (assoc m :state {:channel cur-channel}))
              :leaf (om/build leaf-channel cur-channel (assoc m :opts {:channel-id cur-channel-id}))
              nil)))))))

(om/root main app-state
         {:target (.getElementById js/document "app")
          :shared {:cosm-id (parse-url-param "id")}})
