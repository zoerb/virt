(ns virt.dev
  (:require [virt.core]
            [weasel.repl :as repl]))

(when-not (repl/alive?)
  (repl/connect "ws://localhost:9001" :print :console))
