(ns virt.login
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [put! chan]]
            [cljs-http.client :as http]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [virt.header :refer [header]]))


(defn- login [_ owner {:keys [main-comm]}]
  (reify
    om/IInitState
    (init-state [_]
      {:comm (chan)})
    om/IWillMount
    (will-mount [_]
      (let [comm (om/get-state owner :comm)]
        (go
          (<! (http/post "/api/session" {:form-params {:username (<! comm)}}))
          (>! main-comm :done))))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/div nil
        (dom/div #js {:id "header"}
          (om/build header nil {:opts {:title "Virt"}}))
        (dom/div #js {:id "content"}
          (dom/form #js {:className "login-form"}
            (dom/input #js {:ref "username-input" :placeholder "Username" :autoFocus true})
            (dom/button
              #js {:className "transparent-button"
                   :onClick (fn [e]
                              (.preventDefault e)
                              (put! comm (.-value (om/get-node owner "username-input"))))}
              "Login")))))))

(defn mount-login []
  (let [comm (chan)]
    (om/root login nil {:target (.getElementById js/document "app")
                        :opts {:main-comm comm}})
    comm))
