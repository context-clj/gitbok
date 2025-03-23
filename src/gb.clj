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
   [gb.notebooks]
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
   [:a {:href "/notebooks" :class "block px-5 py-1"} "Notebooks"]
   (for [it (gb.summary/parse-summary)]
     [:div
      [:div {:class "pl-4 mt-4 mb-2"} [:b (:title it)]]
      (for [ch (:children it)]
        (render-menu ch))])])

(defn layout [context request content]
  (if (uui/hx-target request)
    (uui/response  [:div#content {:class "m-x-auto flex-1 py-6 px-12  h-screen overflow-auto"} content])
    (uui/boost-response
     context request
     [:div {:class "flex items-top"}
      [:script {:src "/static/tabs.js"}]
      [:div.nav {:class "px-6 py-6 w-80 text-sm h-screen overflow-auto bg-gray-50 shadow-md"}
       (menu)]
      [:div#content {:class "m-x-auto flex-1 py-6 px-12  h-screen overflow-auto"} content]])))

(defn
  ^{:http {:path "/:path*"}}
  render-file
  [context request]
  (if (str/includes? (:uri request) ".gitbook")
    (let [path (http/url-decode (second (str/split (:uri request) #"\.gitbook")))]
      (resp/file-response (str "./docs/.gitbook" path)))
    (layout
     context request
     [:div.gitbook
      (try (read-file context (:uri request))
           (catch Exception e
             [:div {:role "alert"}
              (.getMessage e)
              [:pre (pr-str e)]]))])))


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
  (layout
   context request
   [:div
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
                 [:span {:class "text-red-500"} (first bl)]]]])]]]]))]))

(defn notebooks [context request]
  (let [notebooks (->> (gb.notebooks/get-notebooks context)
                       :result
                       (sort-by :name))]
    (layout
     context request
     [:div.uui-
      [:h1 {:class "text-2xl py-3 border-b font-bold" }"Notebooks"]
      (for [n notebooks]
        [:div {:class "flex space-x-4 items-top py-2"}
         [:div {:class "flex-1"}
          [:a   {:class "text-sky-600" :href (str "/notebooks/" (:id n))} (:name n)]
          [:div {:class "text-sm text-gray-500"} (:description n)]]
         [:div {:class "flex space-x-2 items-top"}
          (for [tg  (:value (:tags n))]
            [:span {:class "py-1 px-2 text-xs border border-gray-200 bg-gray-50 rounded-md"} tg])]])])))

(def cell-res-c "-mt-px text-sm bg-gray-50 border border-gray-300 py-2 px-4 hover:bg-gray-50 cursor-pointer")
(def cell-code-c "text-xs mt-4 bg-gray-50 p-4 border border-gray-300 font-mono")

(defn hl-sql [txt]
  (when txt
    (-> txt
        (str/replace #"(?i)\b(truncate|where|=|create|replace|and|SELECT|FROM|explain|analyze|select|from|limit|group by|order by|as|join|drop|view|table|cascade|desc|asc|\:\:|-\>|#\>\>|or|and|between|not|in)\b" "<b>$1</b>"))))

(defn hl-http [txt]
  (when txt
    (-> txt
        (str/replace #"(POST|GET|PUT|DELETE|PATCH)" "<b>$1</b>")
        ;; (str/replace #"(\?|\&)" "<b>$1</b>")
        (str/replace #"(?m)^([^:]*):" "<b>$1</b><span class=\"text-gray-400\">:</span>")
        )))

(defn show-notebook [context {{id :id} :route-params :as request}]
  (let [nb (gb.notebooks/get-notebook context id)]
    (layout
     context request
     [:div
      (uui/breadcramp
       ["/notebooks" "notebooks"]
       ["#" (:name nb)])

      [:h1 {:class "text-3xl font-bold py-3 border-b"} (:name nb)]
      (for [cell (:cells nb)]
        (cond
          (= "markdown" (:type cell))
          [:div.gitbook {:class "my-6"}
           (if-let [v (:result cell)] (uui/raw v) (markdown/parse-md context (:value cell)))]


          (= "rest" (:type cell))
          [:div {:class "my-6"}
           [:pre {:class cell-code-c} (uui/raw (hl-http (:value cell)))]
           (when-let [res (:result cell)]
             [:details
              [:summary {:class cell-res-c}
               [:b (:status res)]
               [:span {:class "mx-4"} (:content-type (:headers res))]]
              [:pre {:class "-mt-px text-xs bg-gray-50 p-4 border border-gray-300"} (:body res)]])]

          (= "sql" (:type cell))
          [:div {:class "my-6"}
           [:pre {:class cell-code-c} (uui/raw (hl-sql (:value cell)))]
           (when-let [res (:result cell)]
             [:details
              [:summary {:class cell-res-c} "Result " [:b (:duration res) "ms"]]
              [:pre {:class "-mt-px text-xs bg-gray-50 p-4 border border-gray-300"}
               (for [res (:result res)]
                 [:div
                  (if-not (sequential? (:data res))
                    [:pre (pr-str (:data res))]
                    (let [rows (:data res)
                          cols (keys (first rows))]
                      [:div.gitbook
                       [:table.uui {:class "text-xs"}
                        [:thead
                         [:tr (for [c cols] [:th (name c)])]]
                        [:tbody
                         (for [row (:data res)]
                           [:tr
                            (for [c cols] [:td (str (get row c))])])]]]))])]])]


          :else
          [:div {:class "my-6"}
           [:b (:type cell)]
           [:pre {:class "text-xs mt-4 bg-gray-100 p-4 rounded-md"} (:value cell)]
           [:pre {:class "text-xs mt-4 bg-gray-100 p-4 rounded-md"} (:result cell)]]

              )
        )
      ])))

(system/defstart
  [context config]
  (http/register-ns-endpoints context *ns*)
  (http/register-endpoint context {:path "/" :method :get :fn #'render-file})
  (http/register-endpoint context {:path "/admin/broken" :method :get :fn #'broken-links-view})
  (http/register-endpoint context {:path "/notebooks" :method :get :fn #'notebooks})
  (http/register-endpoint context {:path "/notebooks/:id" :method :get :fn #'show-notebook})
  (gb.docs/index-docs context)
  {})


(def default-config
  {:services ["http" "uui" "gb"]
   :http {:port 8081}})



(comment
  (require '[system.dev :as dev])

  (dev/update-libs)

  (def context (system/start-system default-config))

  (system/stop-system context)

  )

