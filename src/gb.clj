(ns gb
  (:require
   [system]
   [http]
   [ring.util.response :as resp]
   [cheshire.core]
   [uui]
   [markdown]
   [clojure.string :as str]
   [uui.heroicons :as ico]
   [gb.summary]))


(defn read-file [context uri]
  (let [uri (str/replace uri #".md$" "")
        uri (if (= "/" uri) "/README" uri)
        file-name (if (str/ends-with? uri "/")
                    (str "docs" uri "README.md")
                    (str "docs" uri ".md"))
        content (slurp file-name)
        content* (if (str/starts-with? content "---")
                   (last (str/split content #"---\n" 4))
                   content)]
    (try
      (markdown/parse-md context content*)
      (catch Exception e
        [:div {:role "alert"}
         (.getMessage e)
         [:pre (pr-str e)]
         [:pre content*]]))))

(defn render-menu [items & [open]]
  (if (:children items)
    [:details {:class "" :open open}
     [:summary {:class "flex items-center"} [:div {:class "flex-1"} (:title items)] (ico/chevron-right "chevron size-5 text-gray-400")]
     [:div {:class "ml-4 border-l border-gray-200"}
      (map render-menu (:children items))]]
    [:div {:class ""} (:title items)]))

(defn menu []
  (for [it (gb.summary/parse-summary)]
    [:div
     [:div {:class "pl-4 mt-6 mb-2"} [:b (:title it)]]
     (for [ch (:children it)]
       (render-menu ch))]))

(defn
  ^{:http {:path "/:path*"}}
  render-file
  [context request]
  (if (str/includes? (:uri request) ".gitbook")
    (let [path (http/url-decode (second (str/split (:uri request) #"\.gitbook")))]
      (resp/file-response (str "./docs/.gitbook" path)))
    (let [content  [:div#content.uui- {:class "m-x-auto flex-1 py-6 px-12  h-screen overflow-auto"}
                    [:div.gitbook (try (read-file context (:uri request))
                                       (catch Exception e
                                         [:div {:role "alert"}
                                          (.getMessage e)
                                          [:pre (pr-str e)]]))]]]
      (if (uui/hx-target request)
        (uui/response content)
        (uui/boost-response
         context request
         [:div {:class "flex items-top"}
          [:script {:src "/static/tabs.js"}]
          [:div.nav {:class "px-6 py-6 w-80 text-sm h-screen overflow-auto bg-gray-50 shadow-md"}
           (menu)]
          content])))))

(system/defmanifest
  {:description "gb"
   :deps ["http"]
   :config {:history {:type "boolean"}}})

(system/defstart
  [context config]
  (http/register-ns-endpoints context *ns*)
  (http/register-endpoint context {:path "/" :method :get :fn #'render-file})
  {})

(def default-config
  {:services ["http" "uui" "gb"]
   :http {:port 8081}})

(comment
  (def context (system/start-system default-config))

  (system/stop-system context)


  )

