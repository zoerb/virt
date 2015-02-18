(ns virt.router
  (:require [bidi.bidi :as bidi]
            [cljs.reader]))


; [[:home/home {}]]                              <=> "/"
; [[:home/home {}] [:home/new {}]]               <=> "/new"
; [[:home/home {}] [:chat/home {:channel-id 1}]] <=> "/chat/1"

; Hack - should be converted when matching route
(defn- convert-if-number [string]
  (if-let [match (re-find #"^\d+$" string)]
    (cljs.reader/read-string match)
    string))

(defn frame-to-path [routes [page params]]
  (apply bidi/path-for routes page (apply concat params)))

(defn stack-to-path [routes stack]
  (frame-to-path routes (last stack)))

(defn- path-to-frame [routes path]
  (let [match (bidi/match-route routes path)
        params (:route-params match)
        converted-params (into {} (map (fn [[k v]] [k (convert-if-number v)])) params)]
    [(:handler match) converted-params]))

(defn path-to-stack [routes path]
  (let [home-frame [:virt.home/home]
        path-frame (path-to-frame routes (.. js/document -location -pathname))
        stack (if (= (first path-frame) (first home-frame))
                [home-frame]
                [home-frame path-frame])]
    stack))
