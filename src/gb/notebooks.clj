(ns gb.notebooks
  (:require [org.httpkit.client :as http-client]
            [cheshire.core :as json]))

(defn get-notebooks [context]
  (-> @(http-client/post (str "https://aidbox.app" "/rpc")
                         {:body (json/generate-string {:method 'aidbox.portal/published-notebooks})
                          :headers {"content-type" "application/json"}})
      :body
      (json/parse-string keyword)))

(defn get-notebook [context id]
  (-> @(http-client/get (str "https://aidbox.app/PublishedNotebook/" id) {:headers {"content-type" "application/json"}})
      :body
      (json/parse-string keyword)))

(comment

  (def context gb/context)

  (get-notebooks context)
  (get-notebook context "c657dbba-2f64-4477-b274-4e3bb5da47eb")

  )
