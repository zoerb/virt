(ns virt.utils
  (:require [clojure.string :as string]))

(defn parse-url-param [param-name]
  (let [encoded-param-name (string/replace (js/encodeURI param-name) #"[\.\+\*]" "\\$&")
        re (js/RegExp (str "^(?:.*[&\\?]" encoded-param-name "(?:\\=([^&]*))?)?.*$") "i")]
    (js/decodeURI (.replace (.. js/window -location -search) re "$1"))))
