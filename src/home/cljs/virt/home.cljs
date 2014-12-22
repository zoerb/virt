(ns virt.home
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :as async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]
            [virt.history :refer [listen-navigation set-history-path!]]
            [virt.geolocation :refer [get-geolocation]]
            [virt.router :refer [stack-to-path path-to-stack]]))


(def app-state
  (atom {:page-stack []
         :apps {}
         :channels {}
         :geolocation nil}))

(def routes
  [[["/" [#".*" :rest]] :home]
   ["" {"new" :new}]])

(defn header [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm show-back-button show-new-button]}]
      (dom/header nil
        (dom/div nil
          (if show-back-button
            (dom/button #js {:id "back-button"
                             :className "transparent-button"
                             :onClick #(put! comm [:navigate [:back]])}
                        "Back")))
        (dom/div nil
          (dom/div #js {:id "header-title"} "Virt"))
        (dom/div nil
          (if show-new-button
            (dom/button #js {:id "new-button"
                             :className "transparent-button"
                             :onClick #(put! comm [:navigate [:new]])}
                        "New")))))))

(defn list-item [channel owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/li #js {:onClick (fn [e] (put! comm [:set-channel channel]))}
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
      {:comm (chan)
       :show-loading true})
    om/IWillMount
    (will-mount [_]
      (om/set-state! owner :show-loading true)
      (let [nav-chan (listen-navigation)]
        (go (while true
          (om/update! app [:page-stack] (path-to-stack routes (<! nav-chan))))))
      (let [comm
            (om/get-state owner :comm)
            channels-loading
            (go
              (let [loc (<! (get-geolocation))]
                (om/update! app [:geolocation] loc)
                (let [response (<! (http/get "/api/channels" {:query-params loc}))]
                  (om/update! app [:channels] (:body response))
                  (om/set-state! owner :show-loading false))))
            apps-loading
            (go
              (let [response (<! (http/get "/api/apps"))]
                (om/update! app [:apps] (:body response))))]
        (go
          ; Wait for data to load before registering callbacks
          (<! (async/merge [channels-loading apps-loading]))
          (while true
            (let [[msg data] (<! comm)]
              (case msg
                ; TODO: remove/re-evaluate cursor derefs when upgrading to Om 0.8
                :set-channel
                (let [channel-link (:link ((:app data) (:apps @app)))]
                  (set! (.-location js/window) (str channel-link "/" (:channel-id data))))
                :navigate
                (let [page-stack (:page-stack @app)
                      [nav-type page-params] data
                      params (merge page-params (second (peek page-stack)))
                      new-stack
                      (case nav-type
                        :back (pop page-stack)
                        :new (conj page-stack [:new params]))]
                  (do
                    (om/update! app [:page-stack] new-stack)
                    (set-history-path! (stack-to-path routes new-stack))))
                :new-channel
                (let [response
                      (<! (http/post "/api/channels"
                                     {:edn-params {:channel-name data
                                                   :geolocation (:geolocation @app)}}))]
                  (om/transact! app [:channels] (fn [cs] (conj cs (:body response))))
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
            (om/build header app (assoc m :state {:show-back-button (not= page :home)
                                                  :show-new-button (= page :home)})))
          (dom/div #js {:id "content"}
            (if (om/get-state owner :show-loading)
              (dom/div #js {:id "loading"} "Waiting for location...")
              (case page
                :new (om/build new-channel nil m)
                :home (om/build channel-list (:channels app) m)))))))))

(let [stack (path-to-stack routes (.. js/document -location -pathname))
      [_ home-params] (stack 0)]
  (swap! app-state assoc :page-stack stack)
  (om/root main app-state {:target (.getElementById js/document "app")
                           :opts home-params}))
