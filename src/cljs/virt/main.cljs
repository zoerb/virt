(ns virt.main
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :as async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]
            [virt.history :refer [listen-navigation set-history-path!]]
            [virt.geolocation :refer [get-geolocation]]
            [virt.router :refer [stack-to-path path-to-stack]]
            [virt.login :refer [mount-login]]
            [virt.header :refer [header]]
            [virt.chat :as chat]))


(def app-state
  (atom {:page-stack []
         :apps {}
         :channels {}
         :geolocation nil
         :chat chat/app-state}))

(def routes
  [[["/" [#".*" :rest]] :home]
   ["" [["new" :new]
        chat/routes]]])

(defn loading [_ _]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:id "loading"} "Loading..."))))

(defn list-item [channel owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/li #js {:onClick (fn [e] (put! comm [:navigate [:virt.chat/home channel]]))}
        (dom/div #js {:className "name"} (:name channel))
        (dom/div #js {:className "aux"} (name (:app channel)))))))

(defn channel-list [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/div nil
        (apply dom/ul #js {:className "virt-list"}
          (om/build-all list-item app {:init-state {:comm comm}}))))))

(defn new-channel [_ owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/form #js {:className "new-channel-form"}
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
      {:comm (chan)})
    om/IWillMount
    (will-mount [_]
      (let [nav-chan (listen-navigation)]
        (go (while true
          (om/update! app [:page-stack] (path-to-stack routes (<! nav-chan))))))
      (let [comm (om/get-state owner :comm)]
        (go
          ; Load channels and apps in parallel (mostly for illustration purposes for now)
          (let [channels-loading
                (go
                  (let [loc (<! (get-geolocation))]
                    (om/update! app [:geolocation] loc)
                    (let [response (<! (http/get "/api/channels" {:query-params loc}))]
                      (om/update! app [:channels] (:body response)))))
                apps-loading
                (go
                  (let [response (<! (http/get "/api/apps"))]
                    (om/update! app [:apps] (:body response))))]
            ; Wait for data to load before continuing
            (while (some? (<! (async/merge [channels-loading apps-loading]))))
            (om/transact! app [:page-stack] #(pop %)))
          (om/set-state! owner :poll-interval
            (js/setInterval
              #(go
                 (let [loc (<! (get-geolocation))]
                   (om/update! app [:geolocation] loc)
                   (let [response (<! (http/get "/api/channels" {:query-params loc}))]
                     (om/update! app [:channels] (:body response)))))
              3000))
          (while true
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
                        (conj page-stack [nav-type params]))]
                  (om/update! app [:page-stack] new-stack)
                  (set-history-path! (stack-to-path routes new-stack)))
                :new-channel
                (let [response
                      (<! (http/post "/api/channels"
                                     {:edn-params {:channel-name data
                                                   :geolocation (:geolocation @app)}}))]
                  (om/transact! app [:channels] (fn [cs] (conj cs (:body response))))
                  (put! comm [:navigate [:back]]))
                nil))))))
    om/IWillUnmount
    (will-unmount [_]
      (js/clearInterval (om/get-state :poll-interval)))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (let [page-stack (:page-stack app)
            [page params] (peek page-stack)
            m {:init-state {:comm comm}
               :opts params}]
        (dom/div nil
          (dom/div #js {:id "header"}
            (om/build header app
              {:opts {:title "Virt"
                      :left-button {:show (not= page :home)
                                    :text "Back"
                                    :onClick #(put! comm [:navigate [:back]])}
                      :right-button {:show (= page :home)
                                     :text "New"
                                     :onClick #(put! comm [:navigate [:new]])}}}))
          (dom/div #js {:id "content"}
            (case page
              :loading (om/build loading nil)
              :new (om/build new-channel nil m)
              :home (om/build channel-list (:channels app) m)
              :virt.chat/home (om/build chat/main (:chat app) m))))))))

(go
  ; TODO: do authentication after mounting and make login a page instead of a new root
  (let [response (<! (http/get "/api/session"))]
    (if (= (:body response) :no-active-session)
      (<! (mount-login))))
  (let [page-stack (path-to-stack routes (.. js/document -location -pathname))
        stack (conj page-stack [:loading nil])]
    (swap! app-state assoc :page-stack stack)
    (om/root main app-state {:target (.getElementById js/document "app")})))
