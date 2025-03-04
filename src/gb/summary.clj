(ns gb.summary
  (:require
   [uui]
   [clojure.string :as str]))

(defn count-whitespace [s] (count (re-find #"^\s*" s)))

(defn pop-last-child [stack]
  (if-let [lc (:last-child stack)] (dissoc (update stack :children (fn [x] (conj (or x []) lc))) :last-child) stack))

(defn parse-markdown-link [text]
  (if-let [match (re-find #"\[(.*?)\]\((.*?)\)" text)]
    (let [href (nth match 2)]
      [:a {:class "px-4 text-gray-500 block hover:bg-gray-50 py-1.5"
           :href (if (str/starts-with? href "http") href (str "/" href))
           :hx-target "#content"
           :hx-swap "outerHTML"}
       (uui/raw (nth match 1))])
    text))

(defn trim-l [l]
  (parse-markdown-link (str/replace (str/trim l) #"\s*\*\s*" "")))

(defn collect-children [x ls]
  (loop [[{i :i :as l} & ls :as pls] ls acc []]
    (if (nil? l)
      [acc nil]
      (if (> i x)
        (recur ls  (conj acc l))
        [acc pls]))))

(defn treefy [lines]
  (loop [[l & ls] lines
         acc []]
    (if (nil? l)
      acc
      (let [[chld ls] (collect-children (:i l) ls)
            l (if (seq chld) (assoc l :children (treefy chld)) l)]
        (recur ls (conj acc l))))))

(treefy
 (->> ["a" "  b" "  c" "x" " x1" " x2"]
  (mapv (fn [x] {:i (count-whitespace x) :l (str/trim x)})))
 )

(comment
  (parse-summary)

  )


(defn parse-summary []
  (let [sum (slurp "docs/SUMMARY.md")]
    (->>
     (loop [acc []
            cur nil
            [l & ls] (str/split sum #"\n")]
       (if (nil? l)
         (if cur (conj acc cur) acc)
         (if (str/starts-with? (str/trim l) "#")
           (if cur
             (recur (conj acc cur) {:title (str/replace l #"\<.*\>" "") :children []} ls)
             (recur acc {:title (str/replace l #"\<.*\>" "") :children []} ls))
           (recur acc (if (str/blank? l) cur (update cur :children conj l)) ls))))
     (mapv (fn [x] (update x :children (fn [chld]
                                        (->> chld
                                             (mapv (fn [x] {:i (count-whitespace x) :title (trim-l x)}))
                                             (treefy)))))))))

