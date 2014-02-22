(defproject virt "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :source-paths ["src/clj" "src/cljs"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.reader "0.8.2"]
                 [org.clojure/clojurescript "0.0-2156"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [ring/ring-core "1.2.1"]
                 [javax.servlet/servlet-api "2.5"]
                 [compojure "1.1.6"]
                 [cljs-http "0.1.2"]
                 [cheshire "5.3.1"]
                 [secretary "1.0.0"]
                 [om "0.5.0"]
                 [com.cemerick/piggieback "0.1.2"]]
  :plugins [[lein-cljsbuild "1.0.2"]
            [lein-ring "0.8.10"]]
  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src/cljs"]
              :compiler {
                :output-to "resources/public/js/virt.js"
                :source-map "resources/public/js/virt.js.map"
                :output-dir "resources/public/js/out"
                :optimizations :none
                :externs ["om/externs/react.js"]}}
             {:id "release"
              :source-paths ["src/cljs"]
              :compiler {
                :output-to "resources/public/js/virt.js"
                :optimizations :advanced
                :pretty-print false
                :output-wrapper false
                :preamble ["om/react.min.js"]
                :externs ["om/externs/react.js"]
                :closure-warnings
                {:non-standard-jsdoc :off}}}]}
  :ring {:handler virt.core/app}
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]})
