(ns virt.chat-dev
  (:require [virt.chat]
            [clojure.browser.repl :as repl]))

(enable-console-print!)
(repl/connect "http://localhost:9000/repl")
