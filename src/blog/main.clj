(ns blog.main
  (:gen-class)
  (:refer-clojure :exclude [replace])
  (:require
   [clojure.edn :as edn :only [read-string]]
   [clojure.string :as string :refer [replace]]
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.data.xml :refer [indent-str sexp-as-element]]
   [hiccup.page :refer [html5 include-css]])
  (:import
   java.nio.file.Files
   java.nio.file.FileSystems))

;;-----------------------------------------------------------------------------

(def ^:private rfc822
  (java.text.SimpleDateFormat. "EEE, dd MMM yyyy HH:mm:ss ZZZZ"))

(def ^:private mdate
  (java.text.SimpleDateFormat. "yyyy-MM-dd"))

(defn- ndate
  []
  (.format rfc822 (java.util.Date.)))

(defn- fdate
  [d]
  (->> (.parse mdate d)
       (.format rfc822)))

;;-----------------------------------------------------------------------------

(defn- symlink?
  [f]
  (-> (FileSystems/getDefault)
      (.getPath (.getAbsolutePath f) (into-array [""]))
      (Files/isSymbolicLink)))

(defn- delete-file-recursively!
  [f]
  (let [f (io/file f)]
    (if (and (.isDirectory f)
             (not (symlink? f)))
      (doseq [child (.listFiles f)]
        (delete-file-recursively! child)))
    (io/delete-file f)))

(defn- copy-dir!
  [from to]
  (let [[top & files] (file-seq from)
        root-path (str (.getPath top) "/")]
    (doseq [f files]
      (let [dest (io/file to (replace (.getPath f) root-path ""))]
        (.mkdirs (.getParentFile dest))
        (when (.isFile f)
          (println " - " (.getPath dest))
          (io/copy f dest))))))

(defn- markdown!
  [string]
  (:out (shell/sh "/usr/local/bin/mmd" "--notes" "--smart" :in string)))

;;-----------------------------------------------------------------------------

(defn- container
  [& body]
  (html5
   [:head
    [:title "Flipping the Bozo Bit"]
    [:meta {:charset "utf-8"}]
    [:meta {:http-quiv "X-UA-Compatible" :content "IE=edge"}]
    [:meta {:name "viewport" :content "width=device-width"}]
    [:link {:rel "alternate" :type "application/rss+xml" :title "RSS"
            :href "http://ftbb.tv/ftbb.rss"}]
    [:link {:rel "icon" :href "favicon.png"}]
    (include-css
     "http://fonts.googleapis.com/css?family=Quattrocento:400,700&subset=latin,latin-ext")
    (include-css "/style.css")]
   [:body
    [:header
     [:div.title [:a {:href "/"} "Flipping the Bozo Bit"]]
     [:div.author "Keith Irwin & Christoph Neumann"]]
    [:section#container
     body]
    [:footer
     [:div.copyright
      "&copy; 2009-2015 Keith Irwin, Christoph Neumann. All rights reserved."]]]))

(defn- post-page
  [title date text]
  (container
   [:article
    [:h1 title]
    [:section.date date]
    [:section.body text]]))

(defn- index-page
  [posts pages]
  (container
   [:section.posts
    [:h1 "Contents"]
    [:ul
     (for [{:keys [slug title when]} posts]
       [:li
        [:span.date when]
        " "
        [:span.link [:a {:href (str "post/" slug "/")} title]]])]]
   (for [p pages]
     [:section.page
      [:h1 (:title p)]
      (:html p)])))

(defn- rss-feed
  [posts]
  (clojure.string/replace
   (indent-str
    (sexp-as-element
     [:rss {:version "2.0"
            :xmlns:itunes "http://www.itunes.com/dtds/podcast-1.0.dtd"}
      [:channel
       [:title "Flipping the Bozo Bit"]
       [:link "http://ftbb.tv"]
       [:lastBuildDate (ndate)]
       [:language "en-us"]
       [:generator "Zentrope Static Site Generator"]
       [:itunes:author "Keith Irwin, Christoph Neumann"]
       [:itunes:subtitle "Non-traditional ideas for the traditionalist developer."]
       [:itunes:summary (str "A conversational podcast questioning everything "
                             "there is to question about software development.")]
       [:description (str "A conversational podcast questioning everything "
                          "there is to question about software development.")]
       [:itunes:explicit "no"]
       [:itunes:keywords (str "tech,software,functional,distributed,geek,nerd,"
                              "development,keith,irwin,christoph,neumann")]
       [:itunes:owner
        [:itunes:name "contact@flippingthebozobit.tv"]
        [:itunes:email "contact@flippingthebozobit.tv"]]
       [:copyright "2013-2015, Keith Irwin and Christoph Neumann"]
       [:itunes:image {:href "http://ftbb.tv/pix/podcastcover.png"}]
       [:itunes:category {:text "Technology"}
        [:itunes:category {:text "Software How-To"}]]
       [:pubDate (ndate)]
       [:ttl 1800]
       (for [p (reverse (sort-by :when posts))]
         [:item
          [:title (:title p)]
          [:link (str "http://ftbb.tv/post/" (:slug p) "/")]
          [:guid (str "http://ftbb.tv/post/" (:slug p) "/")]
          [:pubDate (fdate (:when p))]
          [:description [:-cdata (:html p)]]

          [:enclosure {:url (str "http://ftbb.tv/" (:media-url p))
                       :length (:media-length p)
                       :type "audio/mpeg"}]

          [:itunes:author "Keith Irwin &amp; Christoph Neumann"]
          [:itunes:subtitle (:summary p)]
          [:itunes:summary (:summary p)]
          [:itunes:keywords (:tags p)]
          [:itunes:image {:href "http://ftbb.tv/pix/podcastcover.png"}]
          [:itunes:duration (:media-duration p)]])]]))
   "><" ">\n<"))

;;-----------------------------------------------------------------------------

(defn- load-text!
  [f]
  (loop [headers []
         lines (string/split (slurp f) #"\n")]
    (if (empty? (string/trim (first lines)))
      (into (read-string (string/join " " headers))
            {:text (string/join "\n" (rest lines))})
      (recur (conj headers (first lines)) (next lines)))))

(defn- resource-file-seq
  [place]
  (->> (file-seq (io/as-file (io/resource place)))
       (filter (fn [f] (.isFile f)))))

(defn- load-index!
  []
  (->> (resource-file-seq "posts")
       (mapv load-text!)))

(defn- scoop-pages
  [index]
  (let [pages (filter #(= (:type %) :page) index)]
    (map #(select-keys % [:title :text]) pages)))

(defn- scoop-posts
  [texts]
  (->> (filter #(= (:type %) :post) texts)
       (sort-by :when)
       (reverse)))

;;-----------------------------------------------------------------------------

(defn -main
  [& args]
  (println "Running.")

  (let [texts (load-index!)
        root (io/as-file "pub")
        posts (mapv #(assoc % :html (markdown! (:text %))) (scoop-posts texts))
        pages (mapv #(assoc % :html (markdown! (:text %))) (scoop-pages texts))]
    ;;
    (when (.exists root)
      (delete-file-recursively! root))
    ;;
    (.mkdirs root)
    ;;
    (spit (io/file root  "index.html") (index-page posts pages))
    ;;
    (copy-dir! (io/file (io/resource "assets"))
               root)
    ;;
    (let [feed-dir (io/file root)]
      (.mkdirs feed-dir)
      (spit (io/file feed-dir "ftbb.rss")
            (rss-feed posts)))
    ;;
    (doseq [post posts]
      (let [html (post-page (:title post) (:when post) (:html post))
            loc (io/as-file (str "pub/post/" (:slug post)))
            place (io/file loc "index.html")]
        (.mkdirs loc)
        (println " - " (.getPath place))
        (spit place html))))

  (println "Done.")
  (System/exit 0))
