(ns virt.cosm-list
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :refer [put! <! >! chan timeout alts!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [virt.utils :refer []]))


(def app-state
  (atom {:cosms {0x001 {:title "cosm1" :app :chat}
                 0x002 {:title "cosm2" :app :chat}
                 0x003 {:title "cosm3" :app :chat}}}))


(defn list-item [id-item owner]
  (reify
    om/IRender
    (render [_]
      (let [id (first id-item)
            item (second id-item)
            api-comm (om/get-shared owner :api-comm)
            app (:app item)
            title (:title item)]
        (dom/a #js {:onClick (fn [e] (.preventDefault e) 
                                     (put! api-comm [:set-app app])
                                     (put! api-comm [:set-header-text title]))
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
                  ;:join-cosm (virt.chat/attach (.getElementById js/document "content") api-comm)
                  ;:set-header-text (println "set-header-text" value)
                  nil))))))
    om/IRenderState
    (render-state [_ {:keys [comm]}]
      (dom/div #js {:id "cosm-content"}
        (apply dom/ul #js {:className "virt-list"}
          (om/build-all list-item (:cosms app) {:init-state {:comm comm}}))))))

(defn attach [target comm]
  (om/root main app-state {:target target :shared {:api-comm comm}})
  (go (>! comm [:set-header-text "Virt"])))
