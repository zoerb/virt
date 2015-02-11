(ns virt.chat
  (:require [compojure.core :refer [GET defroutes]]
            [aleph.http :as http]
            [lamina.core :as lamina]
            [korma.core :as korma]
            [cemerick.friend :as friend]
            [virt.utils :refer [edn-response]]))


(declare messages)

(defn get-messages [channel-id]
  (korma/select messages
    (korma/fields :username :message)
    (korma/where {:channel_id channel-id})
    (korma/order :id :ASC)))

(defn add-msg [user channel-id msg]
  (korma/insert messages
    (korma/values {:username user
                   :channel_id channel-id
                   :message msg})))

(defn chat-ws-handler [client-ch request]
  (let [params (:route-params request)
        channel-id (Integer/parseInt (:channel-id params))
        broadcast-ch
        (lamina/named-channel
          (str channel-id)
          (fn [new-ch]
            (lamina/receive-all new-ch
              (fn [[msg-type {:keys [username message]}]]
                (case msg-type
                  :message (add-msg username channel-id message))))))]
    (lamina/enqueue client-ch (pr-str [:initial (vec (get-messages channel-id))]))
    (lamina/siphon
      (lamina/map* #(pr-str %) broadcast-ch)
      client-ch)
    (let [username (:username (friend/current-authentication request))]
      (lamina/siphon
        (lamina/map*
          (fn [msg]
            (let [[msg-type msg-data] (read-string msg)]
              [msg-type (assoc msg-data :username username)]))
          client-ch)
        broadcast-ch))))

(defroutes routes
  (GET "/:channel-id/watch" [] (http/wrap-aleph-handler chat-ws-handler)))

(defn set-up-db []
  (korma/defentity messages
    (korma/transform
      (fn [m] (-> m (clojure.set/rename-keys {:id :message-id
                                              :channel_id :channel-id}))))))
