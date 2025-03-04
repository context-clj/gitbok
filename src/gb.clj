(ns gb
  (:require
   [system]
   [http]
   [ring.util.response :as resp]
   [cheshire.core]
   [uui]
   [markdown.core]
   [clojure.string :as str]
   [uui.heroicons :as ico]
   [selmer.parser]
   [gb.summary]))



(defn md [s]
  (uui/raw (markdown.core/md-to-html-string s)))


(selmer.parser/add-tag!
 :content-ref (fn [args context-map content]
                (str "<div role=\"content-ref\" " (str/join " " args) " >" (:content (:content-ref content))  "</div>"))
 :else :endcontent-ref)

(selmer.parser/add-tag!
 :hint (fn [args context-map content]
         (str "<div role=\"hint\" " (str/join " " args) " >" (:content (:hint content))  "</div>"))
 :else :endhint)

;; {% embed url="https://youtu.be/N_ZkebvqM24" %}

(selmer.parser/add-tag!
 :embed (fn [args context-map]
          (str "<pre> Embed:" (pr-str args) "</pre>")))

(selmer.parser/add-tag!
 :tabs (fn [args context-map content]
         (str "<div class=\"bg-red-100\" role=\"hint\" " (str/join " " args) " >" (:content (:tabs content))  "</div>"))
 :else :endtabs)

(selmer.parser/add-tag!
 :tab (fn [args context-map content]
        (str "<div  class=\"bg-blue-100\" role=\"tab\" " (str/join " " args) " >" (:content (:tab content))  "</div>"))
 :else :endtab)


(defn resolve-link [context uri href]
  (println :resolve uri href))

(defn read-file [context uri]
  (let [uri (str/replace uri #".md$" "")
        uri (if (= "/" uri) "/README" uri)
        file-name (str "docs" uri ".md")
        content (slurp file-name)
        content* (last (str/split content #"---\n" 4))
        resolve-link (fn [x] (resolve-link context uri x))]
    (try
      (->
       (markdown.core/md-to-html-string content* :resolve-link resolve-link)
       (str)
       (selmer.parser/render {})
       (uui/raw ))
      (catch Exception e
        [:div {:role "alert"}
         (.getMessage e)
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
    (let [content  [:div#content.uui- {:class "m-x-auto flex-1 py-6 px-8  h-screen overflow-auto"}
                    [:div.gitbook (read-file context (:uri request))]]]
      (if (uui/hx-target request)
        (uui/response content)
        (uui/boost-response
         context request
         [:div {:class "flex items-top"}
          [:div.nav {:class "px-6 py-6 w-100 text-sm h-screen overflow-auto bg-gray-50 shadow-md"}
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

  (def tpl
    "
{% content-ref url=\"../deployment-and-maintenance/deploy-aidbox/run-aidbox-on-managed-postgresql.md\" %}
[run-aidbox-on-managed-postgresql.md](../deployment-and-maintenance/deploy-aidbox/run-aidbox-on-managed-postgresql.md)
{% endcontent-ref %}
")

  (require '[selmer.parser])

  (md "![a](b)")
  (md "[a](b)")

  (str (md tpl))

  (selmer.parser/render (str (md tpl)) {})


  )

