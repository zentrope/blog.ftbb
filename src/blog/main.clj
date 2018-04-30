;;
;; Copyright (c) 2018-present Keith Irwin
;;
;; This program is free software: you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published
;; by the Free Software Foundation, either version 3 of the License,
;; or (at your option) any later version.
;;
;; This program is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU General Public License for more details.
;;
;; You should have received a copy of the GNU General Public License
;; along with this program.  If not, see
;; <http://www.gnu.org/licenses/>.
;;

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
   (java.nio.file Files)
   (java.nio.file FileSystems)))

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

(def tag (format "%x" (System/currentTimeMillis)))

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
  (:out (shell/sh "/usr/local/bin/mmd" :in string)))

;;-----------------------------------------------------------------------------

(def link-header true)
(def unlink-header false)

(def title "Flipping the Bozo Bit")

(defn- mk-title
  [link?]
  (if link?
    [:a {:href "/"} title]
    title))

(defn- container
  [link? & body]
  (html5
   [:head
    [:title "Flipping the Bozo Bit"]
    [:meta {:charset "utf-8"}]
    [:meta {:http-quiv "X-UA-Compatible" :content "IE=edge"}]
    [:meta {:name "viewport" :content "width=device-width"}]
    [:link {:rel "alternate" :type "application/rss+xml" :title "RSS"
            :href (str "http://ftbb.tv/ftbb.rss?" tag)}]
    [:link {:rel "icon" :href (str "favicon.png" tag)}]
    (include-css (str "/style.css?" tag))]
   [:body
    [:header
     [:div.title (mk-title link?)]
     [:div.author "Keith Irwin & Christoph Neumann"]]
    [:section#container
     body]
    [:footer
     [:div.copyright
      "&copy; 2009-2018 Keith Irwin, Christoph Neumann. All rights reserved."]]]))

(defn- post-page
  [title date text]
  (container link-header
   [:article
    [:h1 title]
    [:section.date date]
    [:section.body text]]))

(def itunes-link
  [:a {:href "https://itunes.apple.com/us/podcast/flipping-the-bozo-bit/id683786673"} "iTunes"])

(def feed-link
  [:a {:href "http://ftbb.tv/feeds/rss.xml"} "feed"])

(defn- index-page
  [posts]
  (container unlink-header
   [:section.posts
    [:h1 "Contents"]
    [:div.contents
     (for [{:keys [slug title when]} posts]
       [:div.entry
        [:div.date when]
        [:div.text [:a {:href (str "post/" slug "/")} title]]])]
    [:h1 "Find us"]
    [:div.contents
     [:div.entry
      [:div.date "Store"]
      [:div.text itunes-link]]
     [:div.entry
      [:div.date "Reader"]
      [:div.text feed-link]]]]))

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

(defn- scoop-posts
  [texts]
  (->> (filter #(= (:type %) :post) texts)
       (sort-by :when)
       (reverse)))

;;-----------------------------------------------------------------------------

(defn -main
  [& args]
  (println "Running.")
  (when-not (.exists (io/file "/usr/local/bin/mmd"))
    (println "- You must install multimarkdown via homebrew for this to work.")
    (println "- Tell the developer to use an embedded MD engine. Sheesh.")
    (System/exit 1))

  (let [texts (load-index!)
        root (io/as-file "pub")
        posts (mapv #(assoc % :html (markdown! (:text %))) (scoop-posts texts))]
    ;;
    (when (.exists root)
      (delete-file-recursively! root))
    ;;
    (.mkdirs root)
    ;;
    (spit (io/file root  "index.html") (index-page posts))
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
