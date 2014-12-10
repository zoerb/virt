(defproject virt "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :source-paths ["src/core/clj"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.reader "0.8.2"]
                 [org.clojure/clojurescript "0.0-2277"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [korma "0.4.0"]
                 [org.postgresql/postgresql "9.3-1102-jdbc4"]
                 [ring/ring-core "1.3.2"]
                 [javax.servlet/servlet-api "2.5"]
                 [compojure "1.3.1"]
                 [cljs-http "0.1.21"]
                 [aleph "0.3.2"]
                 [secretary "1.1.1"]
                 [om "0.7.1"]]
  :plugins [[lein-cljsbuild "1.0.3"]]
  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.1.3"]
                                  [org.clojure/tools.namespace "0.2.7"]]
                   :source-paths ["dev/clj" "dev/cljs"]}}
  :aliases {"build-dev" ["cljsbuild" "auto" "home-dev" "chat-dev"]
            "build-prod" ["cljsbuild" "once" "home-prod" "chat-prod"]}
  :cljsbuild {
    :builds {:home-dev
             {:source-paths ["src/home/cljs" "src/core/cljs" "dev/cljs"]
              :compiler {
                :output-to "resources/public/js/out/home/home.js"
                :source-map "resources/public/js/out/home/home.js.map"
                :output-dir "resources/public/js/out/home"
                :optimizations :none}}
             :home-prod
             {:source-paths ["src/home/cljs" "src/core/cljs"]
              :compiler {
                :output-to "resources/public/js/home.js"
                :optimizations :advanced
                :pretty-print false
                :preamble ["react/react.min.js"]
                :externs ["react/externs/react.js"]
                :closure-warnings {:non-standard-jsdoc :off}}}
             :chat-dev
             {:source-paths ["src/chat/cljs" "src/core/cljs" "dev/cljs"]
              :compiler {
                :output-to "resources/public/js/out/chat/chat.js"
                :source-map "resources/public/js/out/chat/chat.js.map"
                :output-dir "resources/public/js/out/chat"
                :optimizations :none}}
             :chat-prod
             {:source-paths ["src/chat/cljs" "src/core/cljs"]
              :compiler {
                :output-to "resources/public/js/chat.js"
                :optimizations :advanced
                :pretty-print false
                :preamble ["react/react.min.js"]
                :externs ["react/externs/react.js"]
                :closure-warnings {:non-standard-jsdoc :off}}}}}
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
  :main virt.core)
