(ns virt.core
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer [GET POST defroutes]]
            [ring.util.response :as resp]
            [aleph.http :refer :all]
            [aleph.formats :refer :all]
            [lamina.core :refer :all]))


(def channels
  (ref {:channels {0x001 {:title "Channel 1"
                          :app :chat}
                   0x002 {:title "Channel 2"
                          :app :chat}
                   0x003 {:title "Channel 3"
                          :app :chat}}
        :apps {:chat {:link "/chat.html"}}}))

(def chat-threads
  (ref {0x001 {0x001 {:title "hi"}
               0x002 {:title "hi1"}
               0x003 {:title "how's it goin"}}
        0x002 {0x008 {:title "one"}
               0x009 {:title "another one!"}
               0x00A {:title "another nother one!"}}
        0x003 {}}))

(def chat-messages
  (ref {0x001 ["test"]
        0x002 ["1" "2" "3"]
        0x003 []
        0x008 []
        0x009 []
        0x00A ["works?" "works."]}))

(def next-id
  (let [id (atom 0x00B)]
    (fn [] (swap! id #(inc %)))))

(defn channels-handler [request]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str @channels)})

(defn chat-threads-handler [request]
  (let [params (:route-params request)
        channel-id (read-string (:channel-id params))]
    {:status 200
     :headers {"Content-Type" "application/edn"}
     :body (pr-str (get @chat-threads channel-id))}))

(defn chat-messages-handler [request]
  (let [params (:route-params request)
        thread-id (read-string (:thread-id params))]
    {:status 200
     :headers {"Content-Type" "application/edn"}
     :body (pr-str (get @chat-messages thread-id))}))

(defn new-chat-thread [channel-id thread-name]
  (let [new-id (next-id)]
    (dosync
      (alter chat-threads
        (fn [threads]
          (update-in threads [channel-id]
            #(conj % {new-id {:title thread-name}}))))
      (alter chat-messages
        #(conj % {new-id []}))))
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str (get @chat-threads channel-id))})

(defn new-chat-thread-handler [request]
  (let [body (read-string (slurp (:body request)))]
    (new-chat-thread (:channel-id body) (:thread-name body))))

(defn add-msg [thread-id msg]
  (dosync
    (alter chat-messages
      (fn [msgs]
        (update-in msgs [thread-id]
          #(conj % msg))))))

(defn chat-thread-ws-handler [ch request]
  (let [params (:route-params request)
        thread-id (Integer/parseInt (:thread-id params))
        chat (named-channel
               (str thread-id)
               (fn [new-ch] (receive-all new-ch #(add-msg thread-id %))))]
    (siphon chat ch)
    (siphon ch chat)))

(defroutes app-routes
  (route/resources "/")
  (GET "/api/channels" [] channels-handler)
  (GET "/api/chat/threads/:channel-id" [] chat-threads-handler)
  (POST "/api/chat/threads" [] new-chat-thread-handler)
  (GET "/api/chat/messages/:thread-id" [] chat-messages-handler)
  (GET ["/api/chat/ws/:thread-id", :thread-id #"[0-9A-Za-z]+"] {}
       (wrap-aleph-handler chat-thread-ws-handler))
  (GET "/*" {:keys [uri]} (resp/resource-response "index.html" {:root "public"}))
  (route/not-found "Page not found"))

(defn -main [& args]
  (start-http-server (wrap-ring-handler app-routes)
                     {:port 3000 :websocket true}))
