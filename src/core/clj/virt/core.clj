(ns virt.core
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer [GET POST defroutes]]
            [ring.util.response :as resp]
            [ring.middleware.params :as params]
            [aleph.http :refer :all]
            [aleph.formats :refer :all]
            [lamina.core :refer :all]
            [korma.core :as korma]
            [korma.db :as db]))


(def apps
  {:chat {:link "/chat.html"}})

(declare channels threads messages)

(defn edn-response [body]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str body)})

(defn apps-handler [request]
  (edn-response apps))

(defn get-channels [lon lat]
  (korma/select channels
    (korma/where
      (korma/raw (str "ST_DWithin("
                      "  location,"
                      "  ST_GeographyFromText('SRID=4326;POINT(" lon " " lat ")'),"
                      "  200"
                      ")")))))

(defn channels-handler [request]
  (edn-response
    (let [params (:params request)
          lon (read-string (get params "lon"))
          lat (read-string (get params "lat"))]
      (get-channels lon lat))))

(defn new-channel [channel-name]
  (korma/insert channels
    (korma/values {:name channel-name})))

(defn new-channel-handler [request]
  (edn-response
    (let [body (read-string (slurp (:body request)))]
      (new-channel (:channel-name body)))))

(defn get-threads [channel-id]
  (korma/select threads
    (korma/where {:channel_id channel-id})))

(defn chat-threads-handler [request]
  (edn-response
    (let [params (:route-params request)
          channel-id (read-string (:channel-id params))]
      (get-threads channel-id))))

(defn new-chat-thread [channel-id thread-descr]
  (korma/insert threads
    (korma/values {:channel_id channel-id
                   :description thread-descr})))

(defn new-chat-thread-handler [request]
  (edn-response
    (let [body (read-string (slurp (:body request)))]
      (new-chat-thread (:channel-id body) (:thread-descr body)))))

(defn get-messages [thread-id]
  (korma/select messages
    (korma/where {:thread_id thread-id})))

(defn chat-messages-handler [request]
  (edn-response
    (let [params (:route-params request)
          thread-id (read-string (:thread-id params))]
      (get-messages thread-id))))

(defn add-msg [thread-id msg]
  (korma/insert messages
    (korma/values {:thread_id thread-id
                   :message msg})))

(defn chat-thread-ws-handler [ch request]
  (let [params (:route-params request)
        thread-id (Integer/parseInt (:thread-id params))
        chat (named-channel
               (str thread-id)
               (fn [new-ch] (receive-all new-ch #(add-msg thread-id %))))]
    (apply enqueue ch (get-messages thread-id))
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
  (korma.db/defdb db (db/postgres {:db "virt"
                                   :user "postgres"
                                   :password "postgres"}))

  (korma/defentity channels
    (korma/table :channels)
    (korma/transform
      (fn [cs] (-> cs
                   (assoc :app :chat)
                   (dissoc :location)))))

  (korma/defentity threads
    (korma/table :threads))

  (korma/defentity messages
    (korma/table :messages)
    (korma/transform
      (fn [m] (:message m))))

  (start-http-server (wrap-ring-handler (params/wrap-params app-routes))
                     {:port 3000 :websocket true}))
