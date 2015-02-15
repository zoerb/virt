(defproject virt "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :source-paths ["src/clj"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2850"]
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
                                  [org.clojure/tools.namespace "0.2.7"]
                                  [weasel "0.6.0-SNAPSHOT"]]
                   :source-paths ["dev/clj"]
                   :main virt.dev
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}
             :prod {:main virt.core}}
  :aliases {"build-dev" ["cljsbuild" "auto" "dev"]
            "build-prod" ["cljsbuild" "once" "prod"]
            "run-prod" ["with-profiles" "-dev,+prod" "run"]}
  :cljsbuild {
    :builds {:dev
             {:source-paths ["src/cljs" "dev/cljs"]
              :compiler {
                :main virt.dev
                :output-dir "resources/public/js/out"
                :output-to "resources/public/js/main.js"
                :asset-path "/js/out"
                :source-map true
                :optimizations :none}}
             :prod
             {:source-paths ["src/cljs"]
              :compiler {
                :output-to "resources/public/js/main.js"
                :optimizations :advanced
                :pretty-print false
                :closure-warnings {:non-standard-jsdoc :off}}}}}
  :clean-targets ^{:protect false} [:target-path "resources/public/js"])
