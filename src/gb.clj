(ns gb
  (:require
   [system]
   [http]
   [cheshire.core]
   [uui]
   [markdown.core]
   [clojure.string :as str]
   [uui.heroicons :as ico]
   [selmer.parser]
   [gb.summary]))

(system/defmanifest
  {:description "gb"
   :deps ["http"]
   :config {:history {:type "boolean"}}})

(system/defstart
  [context config]
  {})

(def default-config
  {:services ["http" "uui"]
   :http {:port 8081}})

(defn md [s]
  (uui/raw (markdown.core/md-to-html-string s)))

(defn render-menu [items & [open]]
  (if (:children items)
    [:details {:class "" :open open}
     [:summary {:class "flex items-center"} [:div {:class "flex-1"} (:title items)] (ico/chevron-right "chevron size-5 text-gray-400")]
     [:div {:class "ml-4 border-l border-gray-200"}
      (map render-menu (:children items))]]
    [:div {:class ""} (:title items)]))

(defn read-file [uri]
  (let [uri (str/replace uri #".md$" "")
        uri (if (= "/" uri) "/README" uri)
        file-name (str "docs" uri ".md")
        content (slurp file-name)
        content* (last (str/split content #"---\n" 4))]
    (try
      (->
       (markdown.core/md-to-html-string content*)
       (str)
       (selmer.parser/render {})
       (uui/raw ))
      (catch Exception e
        [:div {:role "alert"}
         (.getMessage e)
         [:pre content*]]))))

(selmer.parser/add-tag!
 :content-ref
 (fn [args context-map content]
   (str "<div role=\"content-ref\" " (str/join " " args) " >" (:content (:content-ref content))  "</div>"))
 :else :endcontent-ref)

(selmer.parser/add-tag!
 :hint
 (fn [args context-map content]
   (str "<div role=\"hint\" " (str/join " " args) " >" (:content (:hint content))  "</div>"))
 :else :endhint)

(selmer.parser/add-tag!
 :tabs
 (fn [args context-map content]
   (str "<div class=\"bg-red-100\" role=\"hint\" " (str/join " " args) " >" (:content (:tabs content))  "</div>"))
 :else :endtabs)

(selmer.parser/add-tag!
 :tab
 (fn [args context-map content]
   (str "<div  class=\"bg-blue-100\" role=\"tab\" " (str/join " " args) " >" (:content (:tab content))  "</div>"))
 :else :endtab)



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
  (let [content  [:div#content.uui- {:class "m-x-auto flex-1 py-6 px-8  h-screen overflow-auto"}
                  [:div.gitbook (read-file (:uri request))]]]
    (if (uui/hx-target request)
      (uui/response content)
      (uui/boost-response
       context request
       [:div {:class "flex items-top"}
        [:div.nav {:class "px-6 py-6 w-100 text-sm h-screen overflow-auto bg-gray-50 shadow-md"}
         (menu)]
        content]))))




(comment
  (def context (system/start-system default-config))

  (system/stop-system context)

  (http/register-ns-endpoints context *ns*)
  (http/register-endpoint context {:path "/" :method :get :fn #'render-file})


  (def tpl 
    "
{% content-ref url=\"../deployment-and-maintenance/deploy-aidbox/run-aidbox-on-managed-postgresql.md\" %}
[run-aidbox-on-managed-postgresql.md](../deployment-and-maintenance/deploy-aidbox/run-aidbox-on-managed-postgresql.md)
{% endcontent-ref %}
")

  (require '[selmer.parser])


  (str (md tpl))

  (selmer.parser/render (str (md tpl)) {})


  )

