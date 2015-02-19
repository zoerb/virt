(ns virt.poll
  (:require [compojure.core :refer [GET defroutes]]
            [aleph.http :as http]
            [lamina.core :as lamina]
            [korma.core :as korma]
            [cemerick.friend :as friend]
            [virt.utils :refer [edn-response]]))


#_(declare messages)

#_(defn get-messages [channel-id]
  (korma/select messages
    (korma/fields :username :message)
    (korma/where {:channel_id channel-id})
    (korma/order :id :ASC)))

#_(defn add-msg [user channel-id msg]
  (korma/insert messages
    (korma/values {:username user
                   :channel_id channel-id
                   :message msg})))

(defroutes routes
  #_(GET "/:channel-id/watch" [] (http/wrap-aleph-handler chat-ws-handler)))

(defn set-up-db []
  #_(korma/defentity messages
    (korma/transform
      (fn [m] (-> m (clojure.set/rename-keys {:id :message-id
                                              :channel_id :channel-id}))))))
