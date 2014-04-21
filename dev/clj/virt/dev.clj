(ns virt.dev
  (:require [cljs.repl.browser]
            [clojure.tools.namespace.repl :only (refresh)]))

(defn start-cljs-repl []
  (cemerick.piggieback/cljs-repl
    :repl-env (cljs.repl.browser/repl-env :port 9000)))

#_(let [stop (atom (virt.core/-main))]
  (defn reload []
    @(@stop)
    (use 'virt.core :reload)
    (reset! stop (virt.core/-main))))
