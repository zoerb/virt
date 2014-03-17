(ns virt.core
  (:use compojure.core
        lamina.core
        aleph.http)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer [GET POST defroutes]]
            [ring.util.response :as resp]))


(def app-state
  (atom {0x001 {:title "Cosm1"
                :app :chat
                :channels
                {0x001 {:title "hi" :node-type :branch :children #{0xAA0 0xAA1 0xAA2}}
                 0xAA0 {:title "hi1" :node-type :leaf :messages []}
                 0xAA1 {:title "hi2" :node-type :leaf}
                 0xAA2 {:title "hi3" :node-type :leaf}
                 0x002 {:title "howdy" :node-type :branch}
                 0x003 {:title "how's it goin" :node-type :branch}}}
         0x002 {:title "Cosm2"
                :app :chat
                :channels
                {0x101 {:title "one" :node-type :branch :children #{0xBA0}}
                 0xBA0 {:title "another one!" :node-type :branch :children #{0xBA1}}
                 0xBA1 {:title "another nother one!" :node-type :leaf :messages ["works?" "works."]}}}
         0x003 {:title "Cosm3"
                :app :chat
                :channels {}}}))


(defn chat-handler [ch id]
  (let [chat (named-channel id nil)]
    (enqueue ch "hi")
    (siphon chat ch)
    (siphon ch chat)))

(defn chat [ch request]
  (let [params (:route-params request)
        id (:id params)]
    (chat-handler ch id)))

(defroutes app-routes
  (route/resources "/")
  (GET ["/api/watch/:id", :id #"[0-9A-Za-z]+"] {}
       (wrap-aleph-handler chat))
  (GET "/*" {:keys [uri]} (resp/resource-response "index.html" {:root "public"}))
  (route/not-found "Page not found"))

(defn -main [& args]
  (start-http-server (wrap-ring-handler app-routes)
                     {:port 3000 :websocket true}))
