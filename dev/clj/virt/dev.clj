(ns virt.dev
  (:require [virt.core]
            [cemerick.piggieback]
            [weasel.repl.websocket]
            [clojure.tools.namespace.repl :as ctnr]))

; Don't reload the virt.dev ns
;(ctnr/disable-reload!)

(defn start-cljs-repl []
  (cemerick.piggieback/cljs-repl
    :repl-env (weasel.repl.websocket/repl-env :ip "0.0.0.0" :port 9001)))

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
  (ctnr/refresh :after 'virt.dev/start))
