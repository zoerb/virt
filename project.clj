(defproject virt "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :source-paths ["src/core/clj"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2760"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [korma "0.4.0"]
                 [org.postgresql/postgresql "9.3-1102-jdbc4"]
                 [ring/ring-core "1.3.2"]
                 [javax.servlet/servlet-api "2.5"]
                 [compojure "1.3.1"]
                 [cljs-http "0.1.21"]
                 [aleph "0.3.2"]
                 [bidi "1.12.0"]
                 [org.omcljs/om "0.8.8"]
                 [com.cemerick/friend "0.2.1" :exclusions [org.clojure/core.cache]]]
  :plugins [[lein-cljsbuild "1.0.4"]]
  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.1.5"]
                                  [org.clojure/tools.namespace "0.2.7"]]
                   :source-paths ["dev/clj"]
                   :main virt.dev}
             :prod {:main virt.core}}
  :aliases {"build-dev" ["cljsbuild" "auto" "home-dev" "chat-dev"]
            "build-prod" ["cljsbuild" "once" "home-prod" "chat-prod"]}
  :cljsbuild {
    :builds {:home-dev
             {:source-paths ["src/home/cljs" "src/core/cljs" "dev/cljs"]
              :compiler {
                :main virt.home-dev
                :output-dir "resources/public/js/out/home"
                :output-to "resources/public/js/home.js"
                :asset-path "/js/out/home"
                :source-map true
                :optimizations :none}}
             :home-prod
             {:source-paths ["src/home/cljs" "src/core/cljs"]
              :compiler {
                :output-to "resources/public/js/home.js"
                :optimizations :advanced
                :pretty-print false
                :closure-warnings {:non-standard-jsdoc :off}}}
             :chat-dev
             {:source-paths ["src/chat/cljs" "src/core/cljs" "dev/cljs"]
              :compiler {
                :main virt.chat-dev
                :output-dir "resources/public/js/out/chat"
                :output-to "resources/public/js/chat.js"
                :asset-path "/js/out/chat"
                :source-map true
                :optimizations :none}}
             :chat-prod
             {:source-paths ["src/chat/cljs" "src/core/cljs"]
              :compiler {
                :output-to "resources/public/js/chat.js"
                :optimizations :advanced
                :pretty-print false
                :closure-warnings {:non-standard-jsdoc :off}}}}}
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]})
