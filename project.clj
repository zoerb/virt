(defproject virt "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :source-paths ["src/core/clj" "src/core/cljs" "src/chat/clj" "src/chat/cljs"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.reader "0.8.2"]
                 [org.clojure/clojurescript "0.0-2173"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [ring/ring-core "1.2.1"]
                 [javax.servlet/servlet-api "2.5"]
                 [compojure "1.1.6"]
                 [cljs-http "0.1.8"]
                 [aleph "0.3.2"]
                 [cheshire "5.3.1"]
                 [secretary "1.0.0"]
                 [om "0.6.2"]]
  :plugins [[lein-cljsbuild "1.0.2"]]
  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.1.2"]
                                  [org.clojure/tools.namespace "0.2.4"]]
                   :source-paths ["dev/clj" "dev/cljs"]
                   :injections [(require '[virt.dev :as dev])]}}
  :cljsbuild {
    :builds {:core-dev
             {:source-paths ["src/core/cljs" "dev/cljs"]
              :compiler {
                :output-to "resources/public/js/out/core/core.js"
                :source-map "resources/public/js/out/core/core.js.map"
                :output-dir "resources/public/js/out/core"
                :optimizations :none}}
             :core-prod
             {:source-paths ["src/core/cljs"]
              :compiler {
                :output-to "resources/public/js/core.js"
                :optimizations :advanced
                :pretty-print false
                :preamble ["react/react.min.js"]
                :externs ["react/externs/react.js"]
                :closure-warnings {:non-standard-jsdoc :off}}}
             :chat-dev
             {:source-paths ["src/chat/cljs" "dev/cljs"]
              :compiler {
                :output-to "resources/public/js/out/chat/chat.js"
                :source-map "resources/public/js/out/chat/chat.js.map"
                :output-dir "resources/public/js/out/chat"
                :optimizations :none}}
             :chat-prod
             {:source-paths ["src/chat/cljs"]
              :compiler {
                :output-to "resources/public/js/chat.js"
                :optimizations :advanced
                :pretty-print false
                :preamble ["react/react.min.js"]
                :externs ["react/externs/react.js"]
                :closure-warnings {:non-standard-jsdoc :off}}}}}
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
  :main virt.core)
