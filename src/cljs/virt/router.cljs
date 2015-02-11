(ns virt.router
  (:require [bidi.bidi :as bidi]
            [cljs.reader]))


; [[:home {}]]                              <=> "/"
; [[:home {}] [:new {}]]                    <=> "/new"
; [[:home {}] [:chat/home {:channel-id 1}]] <=> "/chat/1"

; Hack - should be converted when matching route
(defn- convert-if-number [string]
  (if-let [match (re-find #"^\d+$" string)]
    (cljs.reader/read-string match)
    string))

(defn stack-to-path [routes stack]
  (loop [stack (seq stack)
         routes (seq routes)
         path ""]
    (if (empty? stack)
      path
      (let [[page-type params] (first stack)]
        (recur
          (rest stack)
          (rest routes)
          (str path
               (apply bidi/path-for
                      (first routes)
                      page-type
                      (apply concat (assoc params :rest "")))))))))

(defn path-to-stack [routes path]
  (loop [path path
         routes routes
         prev-params {}
         stack []]
    (if (empty? path)
      stack
      (let [match (bidi/match-route (first routes) path)
            params (:route-params match)
            converted-params (map (fn [[k v]] [k (convert-if-number v)]) (dissoc params :rest))
            all-params (merge prev-params converted-params)]
        (recur
          (:rest params)
          (rest routes)
          all-params
          (conj stack [(:handler match) all-params]))))))
