(ns virt.chat
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]
            [virt.history :refer [listen-navigation set-history-path!]]
            [virt.router :refer [stack-to-path path-to-stack]]))


(def app-state
  (atom {:page-stack []
         :threads {}
         :messages {}}))

(def home-route
  [["/chat/" [#"\d+" :channel-id] [#".*" :rest]] :home])

(def page-routes
  ["" {"/new" :new
       ["/" [#"\d+" :thread-id]] :thread}])

(def routes
  [home-route page-routes])

(defn header [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm show-new-button]}]
      (dom/header nil
        (dom/div nil
          (dom/button #js {:id "back-button"
                           :className "transparent-button"
                           :onClick #(put! comm [:navigate [:back]])}
                      "Back"))
        (dom/div nil
          (dom/div #js {:id "header-title"} "Chat"))
        (dom/div nil
          (if show-new-button
            (dom/button #js {:id "new-button"
                             :className "transparent-button"
                             :onClick #(put! comm [:navigate [:new]])}
                        "New")))))))

(defn leaf-chat [messages owner {:keys [channel-id thread-id]}]
  (reify
    om/IInitState
    (init-state [_]
      {:should-scroll-to-bottom true})
    om/IWillMount
    (will-mount [_]
      (let [wsUri (str "ws://" window.location.host "/api/chat/" channel-id "/threads/" thread-id "/watch")
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
                  :send-message (.send ws (pr-str [:message data]))
                  :close (.close ws)
                  nil))))))
    om/IDidMount
    (did-mount [_]
      (if (om/get-state owner :should-scroll-to-bottom)
        (.scrollIntoView (om/get-node owner "message-form") false)))
    om/IWillUnmount
    (will-unmount [_]
      (go (>! (om/get-state owner :comm) [:close])))
    om/IWillUpdate
    (will-update [_ _ _]
      (om/set-state-nr! owner
                        :should-scroll-to-bottom
                        (>= (+ (.-innerHeight js/window) (.-scrollY js/window))
                            (.-scrollHeight (.-body js/document)))))
    om/IDidUpdate
    (did-update [_ _ _]
      (if (om/get-state owner :should-scroll-to-bottom)
        (.scrollIntoView (om/get-node owner "message-form") false)))
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
          #js {:ref "message-form"
               :onSubmit
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
                (dom/li #js {:onClick (fn [e] (put! comm [:navigate [:thread thread]]))}
                        (:description thread)))))
          threads)))))

(defn new-thread [_ owner {:keys [channel-id]}]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/form #js {:className "new-thread-form"}
        (dom/input #js {:ref "new-thread-input" :placeholder "Title" :autoFocus true})
        (dom/button
          #js {:className "transparent-button"
               :onClick (fn [e]
                          (.preventDefault e)
                          (put! comm [:new-thread
                                      {:channel-id channel-id
                                       :descr (.-value (om/get-node owner "new-thread-input"))}]))}
          "Create")))))

(defn main [app owner {:keys [channel-id]}]
  (reify
    om/IInitState
    (init-state [_]
      {:comm (chan)})
    om/IWillMount
    (will-mount [_]
      (let [nav-chan (listen-navigation)]
        (go (while true
          (let [stack (path-to-stack routes (<! nav-chan))
                [page params] (peek stack)]
            (if (= page :thread)
              (om/update! app [:messages (:thread-id params)] []))
            (om/update! app [:page-stack] stack)))))
      (go (let [response (<! (http/get (str "/api/chat/" channel-id "/threads")))]
            (om/update! app [:threads] (:body response))))
      (let [comm (om/get-state owner :comm)]
        (go (while true
              (let [[msg data] (<! comm)]
                (case msg
                  ; TODO: remove/re-evaluate cursor derefs when upgrading to Om 0.8
                  :navigate
                  (let [page-stack (:page-stack @app)
                        [nav-type page-params] data
                        params (merge page-params (second (peek page-stack)))
                        new-stack
                        (case nav-type
                          :back (pop page-stack)
                          :new (conj page-stack [:new params])
                          :thread
                          (do
                            (om/update! app [:messages (:thread-id params)] [])
                            (conj page-stack [:thread params])))]
                    (if (empty? new-stack)
                      (set! (.-location js/window) "/")
                      (do
                        (om/update! app [:page-stack] new-stack)
                        (set-history-path! (stack-to-path routes new-stack)))))
                  :new-thread
                  (let [response
                        (<! (http/post (str "/api/chat/" (:channel-id data) "/threads")
                                       {:edn-params {:thread-descr (:descr data)}}))]
                    (om/transact! app [:threads] (fn [ts] (conj ts (:body response))))
                    ; TODO: new-thread should probably be on a different channel than navigate
                    (put! comm [:navigate [:back]]))
                  nil))))))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (let [page-stack (:page-stack app)
            [page params] (peek page-stack)
            m {:init-state {:comm comm}
               :opts params}]
        (dom/div nil
          (dom/div #js {:id "header"}
            (om/build header app (assoc m :state {:show-new-button (= page :home)})))
          (dom/div #js {:id "content"}
            (case page
              :home (om/build chat-root (:threads app) m)
              :new (om/build new-thread nil m)
              :thread (om/build leaf-chat
                                (get (:messages app) (:thread-id params))
                                m))))))))

(let [stack (path-to-stack routes (.. js/document -location -pathname))
      [_ home-params] (stack 0)
      [page params] (peek stack)]
  (if (= page :thread)
    (swap! app-state update-in [:messages] assoc (:thread-id params) []))
  (swap! app-state assoc :page-stack stack)
  (om/root main app-state {:target (.getElementById js/document "app")
                           :opts home-params}))
