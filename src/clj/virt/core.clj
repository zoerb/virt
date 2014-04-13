(ns virt.core
  (:use compojure.core
        lamina.core
        aleph.http)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer [GET POST defroutes]]
            [ring.util.response :as resp]))


(def cosms
  (atom {0x001 {:title "Cosm1"
                :app :chat}
         0x002 {:title "Cosm2"
                :app :chat}
         0x003 {:title "Cosm3"
                :app :chat}}))

(def cosm-data
  (atom {0x001 {:root-channel {:title "Chat" :node-type :branch :children #{0x001 0x002 0x003}}
                :channels
                {0x001 {:title "hi" :node-type :branch :children #{0xAA0 0xAA1 0xAA2}}
                 0xAA0 {:title "hi1" :node-type :leaf :messages []}
                 0xAA1 {:title "hi2" :node-type :leaf}
                 0xAA2 {:title "hi3" :node-type :leaf}
                 0x002 {:title "howdy" :node-type :branch}
                 0x003 {:title "how's it goin" :node-type :branch}}}
         0x002 {:root-channel {:title "Chat" :node-type :branch :children #{0x101}}
                :channels
                {0x101 {:title "one" :node-type :branch :children #{0xBA0}}
                 0xBA0 {:title "another one!" :node-type :branch :children #{0xBA1}}
                 0xBA1 {:title "another nother one!" :node-type :leaf :messages ["works?" "works."]}}}
         0x003 {:root-channel {}
                :channels {}}}))


(defn cosms-handler [request]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str @cosms)})

(defn cosm-data-handler [request]
  (let [params (:route-params request)
        id (read-string (:id params))]
    {:status 200
     :headers {"Content-Type" "application/edn"}
     :body (pr-str (get-in @cosm-data [id]))}))

(defn chat-handler [ch request]
  (let [params (:route-params request)
        cosm-id (Integer/parseInt (:cosm-id params))
        channel-id (Integer/parseInt (:channel-id params))
        chat (named-channel (str cosm-id "/" channel-id) nil)]
    ;(enqueue ch (print (get-in @cosm-data [cosm-id :channels channel-id :messages])))
    (siphon chat ch)
    (siphon ch chat)))

(defroutes app-routes
  (route/resources "/")
  (GET ["/api/watch/:cosm-id/:channel-id", :cosm-id #"[0-9A-Za-z]+", :channel-id #"[0-9A-Za-z]+"] {}
       (wrap-aleph-handler chat-handler))
  (GET "/api/cosms" [] cosms-handler)
  (GET "/api/cosm/:id" [] cosm-data-handler)
  (GET "/*" {:keys [uri]} (resp/resource-response "index.html" {:root "public"}))
  (route/not-found "Page not found"))

(defn -main [& args]
  (start-http-server (wrap-ring-handler app-routes)
                     {:port 3000 :websocket true}))
