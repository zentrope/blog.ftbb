(defproject blog "3"
  :description "A static blog site maker-upper."
  :url "https://github.com/zentrope/blog.ftbb"
  :license {:name "EPL" :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0-alpha4"]
                 [org.clojure/data.xml "0.2.0-alpha5"]
                 [hiccup "2.0.0-alpha1"]]
  :clean-targets ["target" "pub"]
  :min-lein-version "2.8.1"
  :profiles {:uberjar {:aot :all}}
  :plugins [[lein-ancient "0.6.15"]]
  :main ^:skip-aot blog.main)
