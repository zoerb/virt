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
  {0x001 (atom {0x001 {:title "hi" :messages ["test"]}
                0x002 {:title "hi1" :messages ["1" "2" "3"]}
                0x003 {:title "how's it goin" :messages []}})
   0x002 (atom {0x008 {:title "one" :messages []}
                0x009 {:title "another one!" :messages []}
                0x00A {:title "another nother one!" :messages ["works?" "works."]}})
   0x003 (atom {})})

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

(defn new-chat [channel-id chat-name]
  (let [new-id (next-id)]
    (swap! (get chats channel-id)
           #(conj % {new-id {:title chat-name :messages []}})))
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str @(get chats channel-id))})

(defn new-chat-handler [request]
  (let [body (read-string (slurp (:body request)))]
    (new-chat (:channel-id body) (:name body))))

(defn add-msg [channel-id chat-id msg]
  (swap! (get chats channel-id)
         (fn [c] (update-in c [chat-id :messages]
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
