(ns virt.home-dev
  (:require [virt.home]
            [clojure.browser.repl :as repl]))

(enable-console-print!)
(repl/connect "http://localhost:9000/repl")
