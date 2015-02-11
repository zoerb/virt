(ns virt.utils)

(defn edn-response
  ([body]
    (edn-response body 200))
  ([body status]
    {:status status
     :headers {"Content-Type" "application/edn"}
     :body (pr-str body)}))
