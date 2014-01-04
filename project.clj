(defproject virt-clj "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :source-paths ["src/clj" "src/cljs"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2127"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [compojure "1.1.6"]]
  :plugins [[lein-cljsbuild "1.0.1"]
            [lein-ring "0.8.8"]]
  :cljsbuild {
    :builds [{:source-paths ["src/cljs"]
              :compiler {
                :output-dir "resources/public/js"
                :output-to "resources/public/js/virt-clj.js"
                :source-map "resources/public/js/virt-clj.cljs"
                :optimizations :whitespace
                :pretty-print true}}]}
  :ring {:handler virt-clj.core/handler})
