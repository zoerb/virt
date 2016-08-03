(ns virt.core
  (:require [compojure.route :as route]
            [compojure.core :as compojure :refer [GET POST DELETE defroutes]]
            (ring.util [request :as req]
                       [response :as resp])
            (ring.middleware [params :refer [wrap-params]]
                             [nested-params :refer [wrap-nested-params]]
                             [keyword-params :refer [wrap-keyword-params]]
                             [session :refer [wrap-session]]
                             [resource :refer [wrap-resource]]
                             [content-type :refer [wrap-content-type]])
            [aleph.http :as http]
            [korma.core :as korma]
            [korma.db :as db]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [virt.utils :refer [edn-response]]
            [virt.chat :as chat]
            [virt.poll :as poll]))


(declare channels users)

(defn geoFromText [lon lat]
  (str "ST_GeographyFromText('SRID=4326;POINT(" lon " " lat ")')"))

(defn get-channels [lon lat]
  (korma/select channels
    (korma/where
      (korma/raw (str "ST_DWithin(location," (geoFromText lon lat) ",200)")))
    (korma/order :id :DESC)))

(defn add-channel [channel-name channel-type lon lat]
  (korma/insert channels
    (korma/values
      {:name channel-name
       :channel_type channel-type
       :location (korma/raw (geoFromText lon lat))})))

(defn get-user [{:keys [username]}]
  {:username username
   :roles #{::user}})

(defn add-user [username password]
  (korma/insert users
    (korma/values {:username username
                   :password (creds/hash-bcrypt password)})))

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
      (add-channel (:channel-name body) (:channel-type body) (:lon geolocation) (:lat geolocation)))))

(defn serve-page [page]
  (-> (resp/resource-response (str page ".html") {:root "public"})
      (resp/content-type "text/html")))

(defroutes api-routes
  (GET "/channels" [] channels-handler)
  (POST "/channels" [] new-channel-handler)

  ; TODO: aleph throwing exception
  (route/not-found "No such api path"))

(defroutes page-routes
  (GET "/*" [] (serve-page "index")))

(defn handle-session [request]
  (let [method (:request-method request)
        auth-data (friend/current-authentication)]
    (if auth-data
      (case method
        :post (edn-response auth-data)
        :get (edn-response auth-data))
      (edn-response :no-active-session))))

(defroutes session-routes
  (GET "/api/session" [] handle-session)
  (POST "/api/session" [] handle-session)
  (friend/logout (DELETE "/api/session" [] (resp/redirect "/"))))

(defn login-failed [request]
  (edn-response :login-failed 401))

(defn passwordless [{:keys [request-method params form-params] :as request}]
  (when (and (= (:login-uri (::friend/auth-config request)) (req/path-info request))
             (= :post request-method))
    (let [username (:username params)]
      (if (empty? username)
        (edn-response :invalid-username 400)
        (if-let [user-record ((:credential-fn (::friend/auth-config request))
                              (with-meta {:username username} {::friend/workflow :passwordless}))]
          (workflows/make-auth user-record
                               {::friend/workflow :passwordless
                                ::friend/redirect-on-auth? false})
          (login-failed request))))))


(defn -main [& args]
  ; TODO: put db credentials in local config
  (korma.db/defdb db (db/postgres {:db "virt"
                                   :user "postgres"
                                   :password "postgres"}))

  (korma/defentity channels
    (korma/transform
      (fn [c] (-> c
                  (dissoc :location)
                  (assoc :channel-type (keyword (:channel_type c)))
                  (dissoc :channel_type)
                  (clojure.set/rename-keys {:id :channel-id})))))

  (korma/defentity users
    (korma/transform #(assoc % :roles #{::user})))

  (chat/set-up-db)
  (poll/set-up-db)

  (http/start-http-server
    (http/wrap-ring-handler
      (-> (compojure/routes session-routes
                            (compojure/context "/api/chat" []
                              (friend/wrap-authorize chat/routes #{::user}))
                            (compojure/context "/api/poll" []
                              (friend/wrap-authorize poll/routes #{::user}))
                            (compojure/context "/api" []
                              (friend/wrap-authorize api-routes #{::user}))
                            page-routes)
          (friend/authenticate {:credential-fn get-user
                                :workflows [passwordless]
                                :login-uri "/api/session"})
          (wrap-session)
          (wrap-keyword-params)
          (wrap-nested-params)
          (wrap-params)
          (wrap-resource "public")
          (wrap-content-type)))
    {:port 3000 :websocket true}))
