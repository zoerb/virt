(ns virt.core
  (:use compojure.core
        lamina.core
        aleph.http)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer [GET POST defroutes]]
            [ring.util.response :as resp]))


(defn chat-init [ch]
  (receive-all ch #(println "message: " %)))

(defn chat-handler [ch room]
  (let [chat (named-channel room chat-init)]
    (siphon chat ch)
    (siphon ch chat)))

(defn chat [ch request]
  (let [params (:route-params request)
        room (:room params)]
    (chat-handler ch room)))

(defroutes app-routes
  (route/resources "/")
  (GET ["/api/watch/:room", :room #"[a-zA-Z]+"] {}
       (wrap-aleph-handler chat))
  (GET "/*" {:keys [uri]} (resp/resource-response "index.html" {:root "public"}))
  (route/not-found "Page not found"))

(defn -main [& args]
  (start-http-server (wrap-ring-handler app-routes)
                     {:host "localhost" :port 3000 :websocket true}))
