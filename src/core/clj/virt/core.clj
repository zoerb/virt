(ns virt.core
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :as compojure :refer [GET POST ANY defroutes]]
            (ring.util [request :as req]
                       [response :as resp])
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
            [korma.db :as db]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds]
                             [util :refer [gets]])
            [selmer.parser :refer [render-file]]
            [environ.core :refer [env]]))


(def apps
  {:chat {:link "/chat"}})


(declare channels threads messages users)

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
    (korma/fields :username :message)
    (korma/where {:channel_id channel-id
                  :thread_id thread-id})
    (korma/order :id :ASC)))

(defn add-msg [user channel-id thread-id msg]
  (korma/insert messages
    (korma/values {:username user
                   :channel_id channel-id
                   :thread_id thread-id
                   :message msg})))

(defn get-user [{:keys [username]}]
  {:username username
   :roles #{::user}})

(defn add-user [username password]
  (korma/insert users
    (korma/values {:username username
                   :password (creds/hash-bcrypt password)})))


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

(defn chat-thread-ws-handler [client-ch request]
  (let [params (:route-params request)
        channel-id (Integer/parseInt (:channel-id params))
        thread-id (Integer/parseInt (:thread-id params))
        broadcast-ch
        (named-channel
          (str channel-id "/" thread-id)
          (fn [new-ch]
            (receive-all new-ch
              (fn [[msg-type {:keys [username message]}]]
                (case msg-type
                  :message (add-msg username channel-id thread-id message))))))]
    (enqueue client-ch (pr-str [:initial (vec (get-messages channel-id thread-id))]))
    (siphon
      (map* #(pr-str %) broadcast-ch)
      client-ch)
    (let [username (:username (friend/current-authentication request))]
      (siphon
        (map*
          (fn [msg]
            (let [[msg-type msg-data] (read-string msg)]
              [msg-type (assoc msg-data :username username)]))
          client-ch)
        broadcast-ch))))

(defn serve-page [page]
  (-> (render-file (str "public/" page ".html") {:dev (env :dev?)})
      (resp/response)
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

(defroutes login-routes
  (GET "/login" [] (serve-page "login"))
  (friend/logout (ANY "/logout" [] (ring.util.response/redirect "/login"))))

(defn passwordless
  [& {:keys [login-uri credential-fn login-failure-handler redirect-on-auth?] :as form-config
      :or {redirect-on-auth? true}}]
  (fn [{:keys [request-method params form-params] :as request}]
    (when (and (= (gets :login-uri form-config (::friend/auth-config request)) (req/path-info request))
               (= :post request-method))
      (let [username (:username params)]
        (if-let [user-record (and (not (empty? username))
                                  ((gets :credential-fn form-config (::friend/auth-config request))
                                   (with-meta {:username username} {::friend/workflow :passwordless})))]
          (workflows/make-auth user-record
                               {::friend/workflow :passwordless
                                ::friend/redirect-on-auth? redirect-on-auth?})
          ((or (gets :login-failure-handler form-config (::friend/auth-config request)) #'workflows/interactive-login-redirect)
           (update-in request [::friend/auth-config] merge form-config)))))))

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
                                            :channel_id :channel-id})))))

  (korma/defentity users
    (korma/transform #(assoc % :roles #{::user})))

  (start-http-server
    (wrap-ring-handler
      (-> (compojure/routes login-routes
                            (compojure/context "/api" []
                              (friend/wrap-authorize api-routes #{::user}))
                            (friend/wrap-authorize page-routes #{::user}))
          (friend/authenticate {:credential-fn get-user
                                :workflows [(passwordless)]})
          (wrap-session)
          (wrap-keyword-params)
          (wrap-nested-params)
          (wrap-params)
          (wrap-resource "public")
          (wrap-content-type)))
    {:port 3000 :websocket true}))
