;; Copyright (c) 2020-2021 DINUM, Bastien Guerry <bastien.guerry@data.gouv.fr>
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
            [clojure.instant :as instant]
            [clj-rss.core :as rss]
            [clojure.java.shell :as sh]
            [clojure.data.json :as datajson])
  (:gen-class))

(defonce sill-url "https://git.sr.ht/~etalab/sill/blob/master/sill.csv")
(defonce sill-updates-url "https://git.sr.ht/~etalab/sill/blob/master/updates.csv")
(defonce sill-contributors-url "https://git.sr.ht/~etalab/sill/blob/master/contributeurs.csv")

(defonce wikidata-base-url "https://www.wikidata.org/wiki/Special:EntityData/")
(defonce wikidata-base-image-url "https://commons.wikimedia.org/wiki/File:")

;; Keywords to ignore
;; "parent"
;; "formats"
(def sill-mapping
  {
   :ID                :id
   :annees            :y
   :composant         :c
   :comptoir-du-libre :co
   :contexte-usage    :u
   :fonction          :f
   :groupe            :g
   :licence           :l
   :label             :la
   :nom               :i
   :parent            :p
   :public            :a
   :support           :su
   :secteur           :se
   :statut            :st
   :similaire-a       :si
   :version_min       :v-
   :version_max       :v+
   :wikidata          :w
   })

(def http-get-params {:cookie-policy :standard})

(defn sill-contributors-to-json
  "Spit sill-contributors.json from `sill-contributors-url`."
  []
  (spit "sill-contributors.json"
        (json/generate-string
         (try (semantic-csv/slurp-csv sill-contributors-url)
              (catch Exception _
                (println "Cannot reach SILL csv URL"))))))

(defn sill-updates-to-json
  "Spit sill-updates.json from `sill-updates-url`."
  []
  (spit "sill-updates.json"
        (json/generate-string
         (try (semantic-csv/slurp-csv sill-updates-url)
              (catch Exception _
                (println "Cannot reach SILL csv URL"))))))

(defn get-sill-entries
  "Get SILL from `sill-url`.
  This is used before wikidata data have been fetched."
  []
  (map #(clojure.set/rename-keys
         (select-keys % (keys sill-mapping))
         sill-mapping)
       (try (semantic-csv/slurp-csv sill-url)
            (catch Exception _
              (println "Cannot reach SILL csv URL")))))

(defn group-by-licenses-family [e]
  (or (re-find #"^AGPL" (:l e))
      (re-find #"^Apache" (:l e))
      (re-find #"^Artistic" (:l e))
      (re-find #"^BSD" (:l e))
      (re-find #"^CC" (:l e))
      (re-find #"^CECILL" (:l e))
      (re-find #"^CPA" (:l e))
      (re-find #"^EPL" (:l e))
      (re-find #"^EUPL" (:l e))
      (re-find #"^etalab" (:l e))
      (re-find #"^GPL-2" (:l e))
      (re-find #"^GPL-3" (:l e))
      (re-find #"^LGPL-2" (:l e))
      (re-find #"^MIT" (:l e))
      (re-find #"^MPL" (:l e))
      (re-find #"^OLDAP" (:l e))))

(defn get-years-count [entries]
  (let [y2018 (count (filter #(re-find #"2018" (:y %)) entries))
        y2019 (count (filter #(re-find #"2019" (:y %)) entries))
        y2020 (count (filter #(re-find #"2020" (:y %)) entries))
        y2021 (count (filter #(re-find #"2021" (:y %)) entries))]
    [["2018" y2018] ["2019" y2019] ["2020" y2020] ["2021" y2021]]))

(defn sill-stats [entries]
  (let [entries    (filter #(re-find #"2021" (:y %)) entries)
        by-license (group-by :l entries)
        by-group   (group-by :g entries)]
    (letfn [(cnt [m] (map (fn [[k v]] [k (count v)]) m))]
      {:total    (count entries)
       :years    (get-years-count entries)
       :licenses (cnt by-license)
       :group    (cnt by-group)})))

(defn- temp-json-file
  "Convert `clj-vega-spec` to json and store it as tmp file."
  [clj-vega-spec]
  (let [tmp-file (java.io.File/createTempFile "vega." ".json")]
    (.deleteOnExit tmp-file)
    (with-open [file (io/writer tmp-file)]
      (datajson/write clj-vega-spec file))
    (.getAbsolutePath tmp-file)))

(defn vega-licenses-spec [licenses]
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
                      :legend {:title "Licenses"}
                      :type   "nominal"
                      :scale  {:scheme "tableau20"}}}
   :width    1200
   :height   600
   :mark     {:type "bar" :tooltip {:content "data"}}})

(defn vega-licenses-chart! [entries]
  (let [entries
        (filter #(re-find #"2021" (:y %)) entries)
        spec (map (fn [[k v]]
                    (let [k (if (= "" k) "Unspecified" k)]
                      {:License k :Number (count v)}))
                  (clojure.set/rename-keys
                   (group-by group-by-licenses-family entries)
                   {nil "Unspecified"}))]
    (sh/sh "vl2svg" (temp-json-file (vega-licenses-spec spec))
           "sill-licenses.svg")))

(defn vega-years-spec [years]
  {:title    "Recommended free software solutions for the french public sector"
   :data     {:values years}
   :encoding {:x     {:field "year" :type "ordinal"
                      :axis  {:title      "Year"
                              :labelAngle 0}}
              :y     {:field "count" :type "quantitative"
                      :axis  {:title "Number of free software"}}
              :color {:field  "year"
                      :type   "nominal"
                      :legend {:title "Year"}
                      :scale  {:scheme "tableau20"}}}
   :width    1200
   :height   600
   :mark     {:type "bar" :tooltip {:content "data"}}})

(defn vega-years-chart! [entries]
  (let [years (map (fn [[a b]] {:year a :count b})
                   (get-years-count entries))]
    (sh/sh "vl2svg" (temp-json-file (vega-years-spec years))
           "sill-years.svg")))

(defn wd-get-data
  "For a wikidata entity, fetch data needed for the SILL."
  [entity]
  (when (not-empty entity)
    (-> (try (http/get (str wikidata-base-url entity ".json")
                       http-get-params)
             (catch Exception _
               (println
                (format "Cannot reach Wikidata url for %s" entity))))
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
                    (catch Exception _
                      (println
                       (str "Can't reach image url for " f))))]
    (let [metas (-> src h/parse h/as-hickory
                    (as-> s (hs/select (hs/tag "meta") s)))]
      (->> metas
           (map :attrs)
           (filter #(= (:property %) "og:image"))
           first
           :content))))

(defn wd-get-value [p claims]
  (let [pc      (p claims)
        pc-pref (filter #(= (:rank %) "preferred") pc)]
    (-> (if (not-empty pc-pref) (first pc-pref) (first pc))
        :mainsnak
        :datavalue
        :value)))

;; Other properties to consider:
;; - P178: developer
;; - P275: license
;; - P18: image
;; - P306: operating system (linux Q388, macosx Q14116, windows Q1406)
(defn sill-plus-wikidata
  "Spit sill.json by adding wikidata data."
  [entries]
  (for [entry entries]
    (-> (if-let [w (not-empty (:w entry))]
          (let [data       (wd-get-data w)
                claims     (:claims data)
                descs      (:descriptions data)
                logo-claim (wd-get-value :P154 claims)
                frama      (wd-get-value :P4107 claims)]
            (merge entry
                   {:logo    (when (not-empty logo-claim)
                               (wc-get-image-url-from-wm-filename logo-claim))
                    :website (wd-get-value :P856 claims)
                    :sources (wd-get-value :P1324 claims)
                    :doc     (wd-get-value :P2078 claims)
                    :frama   {:encoded-name (codec/form-encode frama "UTF-8")
                              :name         frama}
                    :fr-desc (when-let [d (:value (:fr descs))] (s/capitalize d))
                    :en-desc (when-let [d (:value (:en descs))] (s/capitalize d))}))
          entry)
        (dissoc :w))))

(defn sill-updates []
  (try (semantic-csv/slurp-csv sill-updates-url)
       (catch Exception e (println "Can't get latest SILL updates: " e))))

(defn make-rss-feed
  "Generate a RSS feed from `sill-updates`."
  []
  (->>
   (rss/channel-xml
    ;; FIXME: really hardcode title/description in french?
    {:title       "sill.etalab.gouv.fr - mises à jour du SILL"
     :link        "https://sill.etalab.gouv.fr/updates.xml"
     :description "Mises à jour du SILL"}
    (sort-by
     :pubDate
     (map (fn [item]
            (let [id   (:id item)
                  link (if (not (= id 0))
                         ;; FIXME: remove /fr/ ?
                         (format "https://sill.etalab.gouv.fr/fr/software?id=%s" id)
                         "https://sill.etalab.gouv.fr")]
              {:title       (format "%s - %s" (:logiciel item) (:type item))
               :link        link
               :description (:commentaire item)
               :author      "Etalab"
               :pubDate     (instant/read-instant-date
                             (str (first (re-find #"(\d+)-(\d+)-(\d+)" (:date item)))
                                  "T10:00:00Z"))}))
          (sill-updates))))
   (spit "updates.xml")))

(defn -main []
  (sill-contributors-to-json)
  (println "Updated sill-contributors.json")
  (sill-updates-to-json)
  (println "Updated sill-updates.json")
  (make-rss-feed)
  (println "Updated updates.xml")
  (let [entries (get-sill-entries)]
    (spit "sill-stats.json"
          (json/generate-string
           (sill-stats entries)))
    (println "Updated sill-stats.json")
    (vega-licenses-chart! entries)
    (println "Updated sill-licenses.svg")
    (vega-years-chart! entries)
    (println "Updated sill-years.svg")
    (spit "sill.json"
          (json/generate-string
           (sill-plus-wikidata entries)))
    (println "Updated sill.json")
    (System/exit 0)))
