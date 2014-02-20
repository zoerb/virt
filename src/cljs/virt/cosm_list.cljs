(ns virt.cosm-list
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :refer [put! <! >! chan timeout alts!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]
            [virt.utils :refer []]))


(def app-state
  (atom {:cosms {0x001 {:title "cosm1" :app 'virt.chat }
                 0x002 {:title "cosm2"}
                 0x003 {:title "cosm3"}}}))


(defn list-item [id-item owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (let [id (first id-item)
            item (second id-item)]
        (dom/a #js {:onClick (fn [e] (.preventDefault e) 
                                     (put! (om/get-shared owner :api-comm) [:join-cosm id]))
                    :className "list-link"}
               (dom/li nil (:title item)))))))

(defn main [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [comm (chan)]
        (om/set-state! owner :comm comm)
        (go (while true
              (let [[msg value] (<! comm)]
                (case msg
                  ;:navigate (om/set-state! owner :page value)
                  ;:join-cosm (virt.chat/instantiate (.getElementById js/document "content") api-comm)
                  ;:set-header-text (println "set-header-text" value)
                  nil))))))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/div #js {:id "cosm-content"}
        (apply dom/ul #js {:className "virt-list"}
          (om/build-all list-item (:cosms app) {:init-state {:comm comm}}))))))

(defn attach [target comm]
  (om/root app-state {:api-comm comm} main target))
