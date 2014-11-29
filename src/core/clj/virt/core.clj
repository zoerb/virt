(ns virt.core
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer [GET POST defroutes]]
            [ring.util.response :as resp]
            [aleph.http :refer :all]
            [aleph.formats :refer :all]
            [lamina.core :refer :all]))


(def apps
  (ref {:chat {:link "/chat.html"}}))

(def channels
  (ref {0x001 {:name "Channel 1"
               :app :chat}
        0x002 {:name "Channel 2"
               :app :chat}
        0x003 {:name "Channel 3"
               :app :chat}}))

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

(defn edn-response [body]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str body)})

(defn apps-handler [request]
  (edn-response @apps))

(defn channels-handler [request]
  (edn-response @channels))

(defn new-channel [channel-name]
  (let [new-id (next-id)]
    (dosync
      (alter channels
        #(conj % {new-id {:name channel-name
                          :app :chat}}))
      (alter chat-threads
        #(conj % {new-id {}}))))
  (edn-response @channels))

(defn new-channel-handler [request]
  (let [body (read-string (slurp (:body request)))]
    (new-channel (:channel-name body))))

(defn chat-threads-handler [request]
  (let [params (:route-params request)
        channel-id (read-string (:channel-id params))]
    (edn-response (get @chat-threads channel-id))))

(defn chat-messages-handler [request]
  (let [params (:route-params request)
        thread-id (read-string (:thread-id params))]
    (edn-response (get @chat-messages thread-id))))

(defn new-chat-thread [channel-id thread-name]
  (let [new-id (next-id)]
    (dosync
      (alter chat-threads
        (fn [threads]
          (update-in threads [channel-id]
            #(conj % {new-id {:title thread-name}}))))
      (alter chat-messages
        #(conj % {new-id []}))))
  (edn-response (get @chat-threads channel-id)))

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
  (GET "/api/apps" [] apps-handler)
  (GET "/api/channels" [] channels-handler)
  (POST "/api/channels" [] new-channel-handler)
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
