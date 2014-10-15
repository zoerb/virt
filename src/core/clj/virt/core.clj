(ns virt.core
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer [GET POST defroutes]]
            [ring.util.response :as resp]
            [aleph.http :refer :all]
            [aleph.formats :refer :all]
            [lamina.core :refer :all]))


(def channels
  (atom {:channels {0x001 {:title "Channel 1"
                           :app :chat}
                    0x002 {:title "Channel 2"
                           :app :chat}
                    0x003 {:title "Channel 3"
                           :app :chat}}
         :apps {:chat {:link "/chat.html"}}}))

(def chats
  {0x001 (atom {:root-chat 0x001
                :chats
                {0x001 {:title "Chat" :node-type :branch :children [0x002 0x006 0x007]}
                 0x002 {:title "hi" :node-type :branch :children [0x003 0x004 0x005]}
                 0x003 {:title "hi1" :node-type :leaf :messages []}
                 0x004 {:title "hi2" :node-type :leaf}
                 0x005 {:title "hi3" :node-type :leaf}
                 0x006 {:title "howdy" :node-type :branch}
                 0x007 {:title "how's it goin" :node-type :branch}}})
   0x002 (atom {:root-chat 0x008
                :chats
                {0x008 {:title "Chat" :node-type :branch :children [0x009]}
                 0x009 {:title "one" :node-type :branch :children [0x00A]}
                 0x00A {:title "another one!" :node-type :branch :children [0x00B]}
                 0x00B {:title "another nother one!" :node-type :leaf :messages ["works?" "works."]}}})
   0x003 (atom {:root-chat {}
                :chats {}})})

(def next-id
  (let [id (atom 0x00B)]
    (fn [] (swap! id #(inc %)))))

(defn channels-handler [request]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str @channels)})

(defn chats-handler [request]
  (let [params (:route-params request)
        id (read-string (:id params))]
    {:status 200
     :headers {"Content-Type" "application/edn"}
     :body (pr-str @(get chats id))}))

(defn new-chat [channel-id chat-id chat-name]
  (let [new-id (next-id)]
    ; Add child
    (swap! (get chats channel-id)
           (fn [c]
             (update-in c [:chats (or chat-id (:root-chat c)) :children]
                        #(conj % new-id))))
    ; Add data structure
    (swap! (get chats channel-id)
           (fn [c]
             (update-in c [:chats]
                        #(conj % {new-id {:title chat-name :node-type :leaf :messages []}})))))
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str @(get chats channel-id))})

(defn new-chat-handler [request]
  (let [body (read-string (slurp (:body request)))]
    (new-chat (:channel-id body) (:chat-id body) (:name body))))

(defn add-msg [channel-id chat-id msg]
  (swap! (get chats channel-id)
         (fn [c] (update-in c [:chats chat-id :messages]
                   #(conj % msg)))))

(defn chat-handler [ch request]
  (let [params (:route-params request)
        channel-id (Integer/parseInt (:channel-id params))
        chat-id (Integer/parseInt (:chat-id params))
        chat (named-channel
               (str channel-id "/" chat-id)
               (fn [new-ch] (receive-all new-ch #(add-msg channel-id chat-id %))))]
    (siphon chat ch)
    (siphon ch chat)))

(defroutes app-routes
  (route/resources "/")
  (GET "/api/channels" [] channels-handler)
  (POST "/api/chats" [] new-chat-handler)
  (GET "/api/chat/:id" [] chats-handler)
  (GET ["/api/chat/:channel-id/:chat-id", :channel-id #"[0-9A-Za-z]+", :chat-id #"[0-9A-Za-z]+"] {}
       (wrap-aleph-handler chat-handler))
  (GET "/*" {:keys [uri]} (resp/resource-response "index.html" {:root "public"}))
  (route/not-found "Page not found"))

(defn -main [& args]
  (start-http-server (wrap-ring-handler app-routes)
                     {:port 3000 :websocket true}))
