(ns virt.core
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :as compojure :refer [GET POST ANY defroutes]]
            [ring.util.response :as resp]
            (ring.middleware [params :refer [wrap-params]]
                             [nested-params :refer [wrap-nested-params]]
                             [keyword-params :refer [wrap-keyword-params]]
                             [session :refer [wrap-session]]
                             [resource :refer [wrap-resource]]
                             [content-type :refer [wrap-content-type]])
            [aleph.http :refer :all]
            [aleph.formats :refer :all]
            [lamina.core :refer :all]
            [korma.core :as korma]
            [korma.db :as db]))


(def apps
  {:chat {:link "/chat"}})


(declare channels threads messages)

(defn geoFromText [lon lat]
  (str "ST_GeographyFromText('SRID=4326;POINT(" lon " " lat ")')"))

(defn get-channels [lon lat]
  (korma/select channels
    (korma/where
      (korma/raw (str "ST_DWithin(location," (geoFromText lon lat) ",200)")))
    (korma/order :id :DESC)))

(defn add-channel [channel-name lon lat]
  (korma/insert channels
    (korma/values
      {:name channel-name
       :location (korma/raw (geoFromText lon lat))})))

(defn get-threads [channel-id]
  (korma/select threads
    (korma/where {:channel_id channel-id})
    (korma/order :id :DESC)))

(defn add-chat-thread [channel-id thread-descr]
  (korma/insert threads
    (korma/values {:channel_id channel-id
                   :description thread-descr})))

(defn get-messages [channel-id thread-id]
  (korma/select messages
    (korma/where {:channel_id channel-id
                  :thread_id thread-id})
    (korma/order :id :ASC)))

(defn add-msg [channel-id thread-id msg]
  (korma/insert messages
    (korma/values {:channel_id channel-id
                   :thread_id thread-id
                   :message msg})))


(defn edn-response [body]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str body)})

(defn apps-handler [request]
  (edn-response apps))

(defn channels-handler [request]
  (edn-response
    (let [params (:params request)
          lon (Double/parseDouble (:lon params))
          lat (Double/parseDouble (:lat params))]
      (get-channels lon lat))))

(defn new-channel-handler [request]
  (edn-response
    (let [body (read-string (slurp (:body request)))
          geolocation (:geolocation body)]
      (add-channel (:channel-name body) (:lon geolocation) (:lat geolocation)))))

(defn chat-threads-handler [request]
  (edn-response
    (let [params (:route-params request)
          channel-id (Integer/parseInt (:channel-id params))]
      (get-threads channel-id))))

(defn new-chat-thread-handler [request]
  (edn-response
    (let [params (:route-params request)
          channel-id (Integer/parseInt (:channel-id params))
          body (read-string (slurp (:body request)))]
      (add-chat-thread channel-id (:thread-descr body)))))

(defn chat-messages-handler [request]
  (edn-response
    (let [params (:route-params request)
          channel-id (Integer/parseInt (:channel-id params))
          thread-id (Integer/parseInt (:thread-id params))]
      (get-messages channel-id thread-id))))

(defn chat-thread-ws-handler [ch request]
  (let [params (:route-params request)
        channel-id (Integer/parseInt (:channel-id params))
        thread-id (Integer/parseInt (:thread-id params))
        chat (named-channel
               (str thread-id)
               (fn [new-ch]
                 (receive-all new-ch
                   (fn [msg]
                     (let [[msg-type msg-data] (read-string msg)]
                       (case msg-type
                         :message (add-msg channel-id thread-id msg-data)))))))]
    (enqueue ch (pr-str [:initial (vec (get-messages channel-id thread-id))]))
    (siphon chat ch)
    (siphon ch chat)))

(defn serve-page [page]
  (-> (resp/resource-response (str page ".html") {:root "public"})
      (resp/content-type "text/html")))

(defroutes api-routes
  (GET "/apps" [] apps-handler)
  (GET "/channels" [] channels-handler)
  (POST "/channels" [] new-channel-handler)
  (GET "/chat/:channel-id/threads" [] chat-threads-handler)
  (POST "/chat/:channel-id/threads" [] new-chat-thread-handler)
  (GET "/chat/:channel-id/threads/:thread-id" [] chat-messages-handler)
  (GET ["/chat/:channel-id/threads/:thread-id/watch", :thread-id #"[0-9A-Za-z]+"] {}
       (wrap-aleph-handler chat-thread-ws-handler))
  ; TODO: aleph throwing exception
  (route/not-found "No such api path"))

(defroutes page-routes
  (GET "/chat*" [] (serve-page "chat"))
  (GET "/*" [] (serve-page "index")))

(defn -main [& args]
  (korma.db/defdb db (db/postgres {:db "virt"
                                   :user "postgres"
                                   :password "postgres"}))

  (korma/defentity channels
    (korma/transform
      (fn [c] (-> c
                  (assoc :app :chat)
                  (dissoc :location)
                  (clojure.set/rename-keys {:id :channel-id})))))

  (korma/defentity threads
    (korma/transform
      (fn [t] (clojure.set/rename-keys t {:id :thread-id
                                          :channel_id :channel-id}))))

  (korma/defentity messages
    (korma/transform
      (fn [m] (-> m
                  (clojure.set/rename-keys {:id :message-id
                                            :thread_id :thread-id
                                            :channel_id :channel-id})
                  :message))))

  (start-http-server
    (wrap-ring-handler
      (-> (compojure/routes (compojure/context "/api" [] api-routes)
                            page-routes)
          (wrap-session)
          (wrap-keyword-params)
          (wrap-nested-params)
          (wrap-params)
          (wrap-resource "public")
          (wrap-content-type)))
    {:port 3000 :websocket true}))
