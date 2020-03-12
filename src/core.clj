;; Copyright (c) 2020 DINUM, Bastien Guerry <bastien.guerry@data.gouv.fr>
;; SPDX-License-Identifier: EPL-2.0
;; License-Filename: LICENSE

(ns core
  (:require [cheshire.core :as json]
            [semantic-csv.core :as semantic-csv]
            [clj-http.lite.client :as http]
            [ring.util.codec :as codec]
            [clojure.string :as s]
            [clojure.set]
            [hickory.core :as h]
            [hickory.select :as hs]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.data.json :as datajson])
  (:gen-class))

(defonce sill-url "https://raw.githubusercontent.com/DISIC/sill/master/2020/sill-2020.csv")
(defonce wikidata-base-url "https://www.wikidata.org/wiki/Special:EntityData/")
(defonce wikidata-base-image-url "https://commons.wikimedia.org/wiki/File:")
(defonce sill-contributors-url "https://raw.githubusercontent.com/DISIC/sill/master/2020/sill-2020-contributeurs.csv")

(defn- temp-json-file
  "Convert `clj-vega-spec` to json and store it as tmp file."
  [clj-vega-spec]
  (let [tmp-file (java.io.File/createTempFile "vega." ".json")]
    (.deleteOnExit tmp-file)
    (with-open [file (io/writer tmp-file)]
      (datajson/write clj-vega-spec file))
    (.getAbsolutePath tmp-file)))

(defn vega-spec [licenses]
  {:title    "License distribution of recommended free software for the french public sector"
   :data     {:values licenses}
   :encoding {:x     {:field "Number" :type "quantitative"
                      :axis  {:title "Number of software"}}
              :y     {:field "License" :type "ordinal" :sort "-x"
                      :axis  {:title         false
                              :labelLimit    200
                              :offset        10
                              :maxExtent     100
                              :labelFontSize 15
                              :labelAlign    "right"}}
              :color {:field  "License"
                      :legend false
                      :type   "nominal"
                      :title  "Licenses"
                      :scale  {:scheme "tableau20"}}}
   :width    1200
   :height   600
   :mark     {:type "bar"}})

;; Keywords to ignore
;; "parent"
;; "formats"
(def sill-mapping {:statut            :s
                   :fonction          :f
                   :contexte-usage    :u
                   :comptoir-du-libre :co
                   :similaire-a       :si
                   :licence           :l
                   :ID                :id
                   :secteur           :se
                   :composant         :c
                   :version           :v
                   :wikidata          :w
                   :nom               :i
                   :groupe            :g
                   :annees            :y})

(def http-get-params {:cookie-policy :standard})

(defn sill-contributors-to-json
  "Spit sill-contributors.json from `sill-contributors-url`."
  []
  (spit "sill-contributors.json"
        (json/generate-string
         (try (semantic-csv/slurp-csv sill-contributors-url)
              (catch Exception e
                (println "Cannot reach SILL csv URL"))))))

(defn get-sill-entries
  "Get SILL from `sill-url`.
  This is used before wikidata data have been fetched."
  []
  (map #(clojure.set/rename-keys
         (select-keys % (keys sill-mapping))
         sill-mapping)
       (try (semantic-csv/slurp-csv sill-url)
            (catch Exception e
              (println "Cannot reach SILL csv URL")))))

(defn sill-stats [entries]
  (let [entries-2020 (filter #(re-find #"2020" (:y %)) entries)
        by-license   (group-by :l entries-2020)
        by-status    (group-by :s entries-2020)
        by-group     (group-by :g entries-2020)]
    {:total    (count entries-2020)
     :licenses (map (fn [[k v]] [k (count v)])
                    by-license)
     :status   (map (fn [[k v]] [k (count v)])
                    by-status)
     :group    (map (fn [[k v]] [k (count v)])
                    by-group)}))

(defn entries-group-fn [e]
  (or (re-find #"^GPL-3" (:l e))
      (re-find #"^GPL-2" (:l e))
      (re-find #"^LGPL-2" (:l e))
      (re-find #"^AGPL" (:l e))
      (re-find #"^Apache" (:l e))
      (re-find #"^BSD" (:l e))
      (re-find #"^MPL" (:l e))
      (re-find #"^MIT" (:l e))
      (re-find #"^EPL" (:l e))
      (re-find #"^Artistic" (:l e))
      (re-find #"^CPA" (:l e))
      (re-find #"^CC" (:l e))
      (re-find #"^CECILL" (:l e))))

(defn vega-chart! [entries]
  (let [entries-2020 (filter #(re-find #"2020" (:y %)) entries)
        spec         (map (fn [[k v]]
                            (let [k (if (= "" k) "Unspecified" k)]
                              {:License k :Number (count v)}))
                          (clojure.set/rename-keys
                           (group-by entries-group-fn entries-2020)
                           {nil "Unspecified"}))]
    (sh/sh "vl2svg" (temp-json-file (vega-spec spec))
           "sill-licenses.svg")))

(defn wd-get-data
  "For a wikidata entity, fetch data needed for the SILL."
  [entity]
  (when (not-empty entity)
    (-> (try (http/get (str wikidata-base-url entity ".json")
                       http-get-params)
             (catch Exception e
               (println "Cannot reach Wikidata url")))
        :body
        (json/parse-string true)
        :entities
        (as-> e ((keyword entity) e)) ;; kentity
        (select-keys [:claims :descriptions]))))

(defn wc-get-image-url-from-wm-filename
  "From a filename f, get the image url."
  [f]
  (if-let [src (try (:body (http/get
                            (str wikidata-base-image-url
                                 (codec/url-encode f "UTF-8"))
                            http-get-params))
                    (catch Exception e
                      (println
                       (str "Can't reach image url for " f))))]
    (let [metas (-> src h/parse h/as-hickory
                    (as-> s (hs/select (hs/tag "meta") s)))]
      (->> metas
           (map :attrs)
           (filter #(= (:property %) "og:image"))
           first
           :content))))

(defn wd-get-first-value
  "Get the first value of list of claims for property p."
  [p claims]
  (:value (:datavalue (:mainsnak (first (p claims))))))

;; Other properties to consider:
;; - P178: developer
;; - P275: license
;; - P18: image
;; - P306: operating system (linux Q388, macosx Q14116, windows Q1406)
(defn sill-plus-wikidata [entries]
  "Spit sill.json by adding wikidata data."
  []
  (for [entry entries]
    (-> (if-let [w (not-empty (:w entry))]
          (let [data       (wd-get-data w)
                claims     (:claims data)
                descs      (:descriptions data)
                logo-claim (wd-get-first-value :P154 claims)
                frama      (wd-get-first-value :P4107 claims)]
            (merge entry
                   {:logo    (when (not-empty logo-claim)
                               (wc-get-image-url-from-wm-filename logo-claim))
                    :website (wd-get-first-value :P856 claims)
                    :sources (wd-get-first-value :P1324 claims)
                    :doc     (wd-get-first-value :P2078 claims)
                    :frama   {:encoded-name (codec/form-encode frama "UTF-8")
                              :name         frama}
                    :fr-desc (if-let [d (:value (:fr descs))] (s/capitalize d))
                    :en-desc (if-let [d (:value (:en descs))] (s/capitalize d))}))
          entry)
        (dissoc :w))))

(defn -main [& args]
  (sill-contributors-to-json)
  (println "Updated sill-contributors.json")
  (let [entries (get-sill-entries)]
    (spit "sill-stats.json"
          (json/generate-string
           (sill-stats entries)))
    (println "Updated sill-stats.json")
    (vega-chart! entries)
    (println "Updated sill-licenses.svg")
    (spit "sill.json"
          (json/generate-string
           (sill-plus-wikidata entries)))
    (println "Updated sill.json")
    (System/exit 0)))

;; (-main)
