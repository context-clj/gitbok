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
   [clojure.java.io :as io]
   [gb.summary]
   [gb.docs]))

(defn read-file [context uri]
  (let [file-name (gb.docs/uri-to-path uri)
        content (slurp file-name)
        content* (if (str/starts-with? content "---")
                   (last (str/split content #"---\n" 3))
                   content)]
    (try
      (markdown/parse-md (assoc context :uri uri) content*)
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
  [:div 
   [:a {:href "/admin/broken" :class "block px-5 py-1"} "Broken Links"]
   (for [it (gb.summary/parse-summary)]
     [:div
      [:div {:class "pl-4 mt-6 mb-2"} [:b (:title it)]]
      (for [ch (:children it)]
        (render-menu ch))])])

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


(defn collect-broken-links [context]
  (->> (gb.docs/get-index context)
       (sort-by #(:path (second %)))
       (map (fn [[_ x]]
              (let [broken (markdown/broken-links
                            (assoc context :uri (str/replace (:path x) #"^docs" ""))
                            (slurp (:path x)))]
                (assoc x :broken-links broken))))))

(defn broken-links-view [context request]
  (let [content  [::div#content.gitbook {:class "p-6"}
                  (for [x (collect-broken-links context)]
                    (when-not (empty? (:broken-links x))
                      [:details {:class "w-full py-1"}
                       [:summary {:class "w-full border-b border-gray-200 py-1 flex items-center space-x-4 cursor-pointer"}
                        [:b {:class "flex-1"} (uui/raw (:title x))]
                        [:a {:class "" :href (str/replace (:path x) #"^docs" "")} "link"]
                        [:b {:class "text-red-500"} "(" (count (:broken-links x)) ")"]]
                       [:div {:class "py-6 mb-4"}
                        [:table.uui
                         [:thead [:tr [:th "Title"] [:th "href"]]]
                         [:tbody
                          (for [bl (:broken-links x)]
                            [:tr
                             [:td (uui/raw (last bl))]
                             [:td
                              [:a {:href (first bl)}
                               [:span {:class "text-red-500"} (first bl)]]]])]]]]))]]
    (if (uui/hx-target request)
      (uui/response content)
      (uui/boost-response
       context request
       [:div {:class "flex items-top"}
        [:script {:src "/static/tabs.js"}]
        [:div.nav {:class "px-6 py-6 w-80 text-sm h-screen overflow-auto bg-gray-50 shadow-md"}
         (menu)]
        content]))))

(system/defstart
  [context config]
  (http/register-ns-endpoints context *ns*)
  (http/register-endpoint context {:path "/" :method :get :fn #'render-file})
  (http/register-endpoint context {:path "/admin/broken" :method :get :fn #'broken-links-view})
  (gb.docs/index-docs context)
  {})


(def default-config
  {:services ["http" "uui" "gb"]
   :http {:port 8081}})



(comment
  (def context (system/start-system default-config))

  (system/stop-system context)

  )

