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
  (atom {0x001 {:channels
                {0x001 {:title "hi" :node-type :branch :children #{0xAA0 0xAA1 0xAA2}}
                 0xAA0 {:title "hi1" :node-type :leaf :messages []}
                 0xAA1 {:title "hi2" :node-type :leaf}
                 0xAA2 {:title "hi3" :node-type :leaf}
                 0x002 {:title "howdy" :node-type :branch}
                 0x003 {:title "how's it goin" :node-type :branch}}}
         0x002 {:channels
                {0x101 {:title "one" :node-type :branch :children #{0xBA0}}
                 0xBA0 {:title "another one!" :node-type :branch :children #{0xBA1}}
                 0xBA1 {:title "another nother one!" :node-type :leaf :messages ["works?" "works."]}}}
         0x003 {:channels {}}}))


(defn cosms-handler [request]
  {:status 200
   :headers {"Content-Type" "application/edn"}
   :body (pr-str @cosms)})

(defn chat-handler [ch cosm-id-str channel-id-str]
  (let [chat (named-channel (str cosm-id-str "/" channel-id-str) nil)
        cosm-id (Integer/parseInt cosm-id-str)
        channel-id (Integer/parseInt channel-id-str)]
    (enqueue ch (get-in @cosm-data [cosm-id channel-id]))
    (siphon chat ch)
    (siphon ch chat)))

(defn chat [ch request]
  (let [params (:route-params request)
        cosm-id (:cosm-id params)
        channel-id (:channel-id params)]
    (chat-handler ch cosm-id channel-id)))

(defroutes app-routes
  (route/resources "/")
  (GET ["/api/watch/:cosm-id/:channel-id", :cosm-id #"[0-9A-Za-z]+", :channel-id #"[0-9A-Za-z]+"] {}
       (wrap-aleph-handler chat))
  (GET "/api/cosms" [] cosms-handler)
  (GET "/*" {:keys [uri]} (resp/resource-response "index.html" {:root "public"}))
  (route/not-found "Page not found"))

(defn -main [& args]
  (start-http-server (wrap-ring-handler app-routes)
                     {:port 3000 :websocket true}))
