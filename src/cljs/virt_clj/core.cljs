(ns virt-clj.core
    (:require-macros [cljs.core.async.macros :refer [go alt!]])
    (:require [goog.events :as events]
              [cljs.core.async :refer [put! <! >! chan timeout]]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
              [cljs-http.client :as http]
              [virt-clj.utils :refer [guid]]))


(enable-console-print!)

(def app-state
  (atom {:things []}))

(defn virt-clj-app [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
               (dom/h1 nil "virt-clj is working!")))))

(om/root app-state virt-clj-app (.getElementById js/document "content"))
