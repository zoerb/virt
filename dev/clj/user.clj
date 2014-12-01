(ns user
  (:require [virt.core]
            [cljs.repl.browser]
            [cemerick.piggieback]
            [clojure.tools.namespace.repl :as ctnr]))

; Don't reload the user ns
(ctnr/disable-reload!)

(defn start-cljs-repl []
  (cemerick.piggieback/cljs-repl
    :repl-env (cljs.repl.browser/repl-env :port 9000)))

(def system
  {:server (atom nil)})

(defn start []
  (reset! (:server system) (virt.core/-main)))

(defn stop []
  (let [handle (:server system)]
    (when @handle
      (@handle)
      (reset! handle nil))))

(defn reset []
  (stop)
  (ctnr/refresh :after 'user/start))
