(ns gb.docs
  (:require
   [system]
   [http]
   [clojure.string :as str]
   [clojure.java.io :as io]))


(defn get-title [file]
  (with-open [rdr (io/reader file)]
    (->> (line-seq rdr)
         (some (fn [l]
                 (when (str/starts-with? (str/trim l) "#")
                   (str/replace (str/trim l) #"\s*#+\s" "")))))))

(defn index-docs [context & [path]]
  (let [idx (->> (file-seq (io/file (or path "docs")))
                 (reduce (fn [acc f]
                           (let [path (.getPath f)]
                             (if (and (str/ends-with? path ".md")
                                      (not (str/starts-with? path ".git/")))
                               (assoc acc path {:title (or (get-title path) (str/replace (.getName f) #"\.md$" ""))
                                                :path path})
                               acc)))
                         {}))]
    (system/set-system-state context [::docs-idx] idx)
    idx))

(defn get-index [context]
  (system/get-system-state context [::docs-idx]))


(defn get-directory [file-path]
  (-> (io/file file-path)
      (.getParentFile)
      (.getPath)))

(defn normalize-path [base path]
  (let [base (or base "/")
        base (if (str/ends-with? base "/") base (get-directory base))
        path (if (str/ends-with? path "/") (str path "README.md") path)
        path (if (and (not (str/starts-with? path ".")) (not (str/starts-with? path "/"))) (str "./" path) path)
        path (if (str/starts-with? path ".")
               (-> (io/file base path)
                   (.getCanonicalPath))
               path)]
    (str/replace path #"^(/?\.\./)+" "/")))

(defn uri-to-path [uri]
  (let [uri (str/replace uri #".md$" "")
        uri (if (= "/" uri) "/README" uri)
        file-name (if (str/ends-with? uri "/")
                    (str "docs" uri "README.md")
                    (str "docs" uri ".md"))]
    file-name))


(defn resolve-href [context uri href]
  (let [furi (normalize-path uri href)
        path (uri-to-path furi)]
    (get (get-index context) path)))

(comment

  (def context gb/context)

  (index-docs context)

  (get-index context)


  (normalize-path "/getting-started/README.md" "../deployment-and-maintenance/deploy-aidbox/run-aidbox-on-managed-postgresql.md")
  (normalize-path "/" "/deployment-and-maintenance/deploy-aidbox/run-aidbox-on-managed-postgresql.md")

  (resolve-href context "/" "/deployment-and-maintenance/deploy-aidbox/run-aidbox-on-managed-postgresql.md")

  (resolve-href context "/" "/")

  (resolve-href context "/" "/ups")

  (normalize-path "/getting-started/README.md" "../deployment-and-maintenance/deploy-aidbox/run-aidbox-on-managed-postgresql.md")

  (normalize-path "/configuration/installation.md" "../../storage-1/aidboxdb-image/")

  (normalize-path "/overview/aidbox-user-portal/README.md" "licenses.md")


  )
