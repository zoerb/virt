(ns virt.core
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer [GET POST defroutes]]
            [ring.util.response :as resp]
            [aleph.http :refer :all]
            [lamina.core :refer :all]))


(def cosms
  (atom {:cosms {0x001 {:title "Cosm1"
                        :app :chat}
                 0x002 {:title "Cosm2"
                        :app :chat}
                 0x003 {:title "Cosm3"
                        :app :chat}}
         :apps {:chat {:link "/chat.html"}}}))

(def chat-data
  {0x001 (atom {:root-channel 0x001
                :channels
                {0x001 {:title "Chat" :node-type :branch :children [0x002 0x006 0x007]}
                 0x002 {:title "hi" :node-type :branch :children [0x003 0x004 0x005]}
                 0x003 {:title "hi1" :node-type :leaf :messages []}
                 0x004 {:title "hi2" :node-type :leaf}
                 0x005 {:title "hi3" :node-type :leaf}
                 0x006 {:title "howdy" :node-type :branch}
                 0x007 {:title "how's it goin" :node-type :branch}}})
   0x002 (atom {:root-channel 0x008
                :channels
                {0x008 {:title "Chat" :node-type :branch :children [0x009]}
                 0x009 {:title "one" :node-type :branch :children [0x00A]}
                 0x00A {:title "another one!" :node-type :branch :children [0x00B]}
                 0x00B {:title "another nother one!" :node-type :leaf :messages ["works?" "works."]}}})
   0x003 (atom {:root-channel {}
                :channels {}})})


(defn cosms-handler [request]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str @cosms)})

(defn chat-data-handler [request]
  (let [params (:route-params request)
        id (read-string (:id params))]
    {:status 200
     :headers {"Content-Type" "application/edn"}
     :body (pr-str @(get chat-data id))}))

(defn new-chat-handler [request]
  (let [params (:route-params request)]
    (println (:body request))
    {:status 200 :body "hi"}))

(defn add-msg [cosm-id channel-id msg]
  (swap! (get chat-data cosm-id)
         (fn [c] (update-in c [:channels channel-id :messages]
                   #(conj % msg)))))

(defn chat-handler [ch request]
  (let [params (:route-params request)
        cosm-id (Integer/parseInt (:cosm-id params))
        channel-id (Integer/parseInt (:channel-id params))
        chat (named-channel
               (str cosm-id "/" channel-id)
               (fn [new-ch] (receive-all new-ch #(add-msg cosm-id channel-id %))))]
    (siphon chat ch)
    (siphon ch chat)))

(defroutes app-routes
  (route/resources "/")
  (GET "/api/cosms" [] cosms-handler)
  (POST "/api/chats" [] new-chat-handler)
  (GET "/api/chat/:id" [] chat-data-handler)
  (GET ["/api/chat/:cosm-id/:channel-id", :cosm-id #"[0-9A-Za-z]+", :channel-id #"[0-9A-Za-z]+"] {}
       (wrap-aleph-handler chat-handler))
  (GET "/*" {:keys [uri]} (resp/resource-response "index.html" {:root "public"}))
  (route/not-found "Page not found"))

(defn -main [& args]
  (start-http-server (wrap-ring-handler app-routes)
                     {:port 3000 :websocket true}))
