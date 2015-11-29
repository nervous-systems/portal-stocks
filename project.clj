(let [os (-> "os.name"
             System/getProperty
             clojure.string/lower-case
             (clojure.string/replace #"\s+" ""))
      arch (System/getProperty "os.arch")
      arch (if (= "amd64" arch) "x86_64" arch)
      javacpp-classifier (str os "-" arch)]

  (defproject io.nervous/portal-stocks "0.1.0-SNAPSHOT"
    :dependencies [[org.clojure/clojure    "1.7.0"]
                   [org.clojure/core.async "0.2.374"]
                   [camel-snake-kebab "0.3.2"]

                   [org.bytedeco/javacv "1.1"]
                   [org.bytedeco.javacpp-presets/ffmpeg
                    "2.8.1-1.1" :classifier ~javacpp-classifier]
                   [org.bytedeco.javacpp-presets/opencv
                    "3.0.0-1.1" :classifier ~javacpp-classifier]]))
