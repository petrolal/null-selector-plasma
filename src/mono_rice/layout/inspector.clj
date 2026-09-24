(ns mono-rice.layout.inspector
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [mono-rice.fs :as rfs]))

(defn parse-ini-sections [text]
  (let [lines (str/split-lines text)]
    (loop [remaining lines
           current-section nil
           sections {}]
      (if (empty? remaining)
        sections
        (let [line (str/trim (first remaining))]
          (cond
            (or (empty? line) (str/starts-with? line "#") (str/starts-with? line ";"))
            (recur (rest remaining) current-section sections)

            (and (str/starts-with? line "[") (str/ends-with? line "]"))
            (let [sec (subs line 1 (dec (count line)))]
              (recur (rest remaining) sec (update sections sec #(or % {}))))

            (and current-section (str/includes? line "="))
            (let [idx (str/index-of line "=")
                  k   (subs line 0 idx)
                  v   (subs line (inc idx))]
              (recur (rest remaining) current-section
                     (assoc-in sections [current-section k] v)))

            :else
            (recur (rest remaining) current-section sections)))))))

(defn dump-widgets [& [appletsrc-path]]
  (let [path (or appletsrc-path
                 (fs/path (rfs/home-dir) ".config" "plasma-org.kde.plasma.desktop-appletsrc"))]
    (if-not (fs/exists? path)
      (println (str "Error: " path " not found."))
      (let [raw-sections (parse-ini-sections (slurp (str path)))
            containments (atom {})
            applets      (atom {})]

        (doseq [[sec data] raw-sections]
          (cond
            ;; Containment root: Containments][X
            (re-matches #"^Containments\]\[(\d+)$" sec)
            (let [cid (second (re-matches #"^Containments\]\[(\d+)$" sec))]
              (swap! containments assoc cid data))

            ;; Applet root: Containments][X][Applets][Y
            (re-matches #"^Containments\]\[(\d+)\]\[Applets\]\[(\d+)$" sec)
            (let [[_ cid aid] (re-matches #"^Containments\]\[(\d+)\]\[Applets\]\[(\d+)$" sec)]
              (swap! applets update-in [cid aid] #(assoc (or % {:config {}}) :plugin (get data "plugin" "unknown"))))

            ;; Applet subgroups: Containments][X][Applets][Y][SubGroup...
            (re-matches #"^Containments\]\[(\d+)\]\[Applets\]\[(\d+)\]\[(.*)$" sec)
            (let [[_ cid aid sub] (re-matches #"^Containments\]\[(\d+)\]\[Applets\]\[(\d+)\]\[(.*)$" sec)]
              (swap! applets update-in [cid aid] #(or % {:plugin "unknown" :config {}}))
              (swap! applets assoc-in [cid aid :config sub] data))))

        (println (apply str (repeat 80 "=")))
        (println "KDE PLASMA 6 WIDGET CONFIGURATION & POSITION DUMP")
        (println (apply str (repeat 80 "=")))

        (let [loc-names {"1" "TOP" "2" "RIGHT" "3" "BOTTOM" "4" "LEFT"}]
          (doseq [[cid cdata] (sort-by #(Long/parseLong (first %)) @containments)]
            (let [c-plugin (get cdata "plugin" "unknown")]
              (cond
                (= c-plugin "org.kde.panel")
                (let [loc   (get loc-names (get cdata "location" "3") "BOTTOM")
                      order (filter seq (str/split (get cdata "appletorder" "") #";"))]
                  (println (format "\n[PANEL ID: %s] Position: %s | Thickness: %spx"
                                   cid loc (get cdata "thickness" "default")))
                  (println (format "Applet Order: %s" (if (seq order) (vec order) "Natural")))
                  (println (apply str (repeat 80 "-"))))

                (= c-plugin "org.kde.desktopcontainment")
                (do
                  (println (format "\n[DESKTOP CANVAS ID: %s] Screen: %s"
                                   cid (get cdata "lastscreen" "0")))
                  (println (apply str (repeat 80 "-"))))

                :else nil)

              (let [cid-applets (get @applets cid {})]
                (if (empty? cid-applets)
                  (println "  (No applets registered)")
                  (doseq [[aid adata] (sort-by #(Long/parseLong (first %)) cid-applets)]
                    (let [plugin   (:plugin adata)
                          cfg      (:config adata)
                          geom-cfg (get cfg "Geometry" {})
                          x        (get geom-cfg "x" "auto")
                          y        (get geom-cfg "y" "auto")
                          w        (get geom-cfg "width" "auto")
                          h        (get geom-cfg "height" "auto")]
                      (println (format "\n  • Applet ID: %s" aid))
                      (println (format "    Plugin  : %s" plugin))
                      (when (seq geom-cfg)
                        (println (format "    Position: X=%s, Y=%s | Size: %sx%s" x y w h)))
                      (doseq [[sub-name sub-data] cfg]
                        (when-not (= sub-name "Geometry")
                          (let [cleaned-sub (str/replace sub-name "][" " -> ")]
                            (println (format "    [%s]" cleaned-sub))
                            (doseq [[k v] sub-data]
                              (if (and (> (count v) 90)
                                       (or (str/starts-with? v "{") (str/starts-with? v "[")))
                                (try
                                  (let [parsed (json/parse-string v)]
                                    (println (format "      %s: [JSON Object with %d keys]" k (count parsed))))
                                  (catch Exception _
                                    (println (format "      %s: %s" k v))))
                                (println (format "      %s: %s" k v))))))))))))))))))
