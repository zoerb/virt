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

(defn chat-handler [ch id]
  (let [chat (named-channel id chat-init)]
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
                     {:host "localhost" :port 3000 :websocket true}))
