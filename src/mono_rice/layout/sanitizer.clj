(ns mono-rice.layout.sanitizer
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.proc :refer [command-exists? log-info log-warn log-step log-success sh!]]))

(def home-placeholder "{{HOME}}")
(def activity-placeholder "{{ACTIVITY_ID}}")
(def uuid-regex #"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

(def required-plugins
  ["luisbocanegra.panel.colorizer"
   "org.kde.plasma.systemmonitor.net"
   "org.kde.plasma.systemmonitor.cpu"
   "org.kde.plasma.systemmonitor.memory"
   "org.kde.plasma.systemmonitor.diskusage"
   "org.kde.plasma.panelspacer"
   "org.kde.plasma.catwalkEnhanced"
   "org.kde.plasma.digitalclock"
   "KdeControlStation"
   "org.kde.plasma.kickoff"
   "org.kde.plasma.icontasks"
   "org.kde.plasma.pager"
   "org.kde.plasma.systemtray"
   "org.kde.plasma.trash"
   "luisbocanegra.audio.visualizer"
   "org.kde.plasma.binaryclock"
   "com.axzoros.yorhahud"
   "org.kde.olib.thermalmonitor"])

(defn query-current-activity []
  (when (command-exists? "qdbus6")
    (try
      (let [{:keys [exit out]} (sh! ["qdbus6" "org.kde.ActivityManager"
                                     "/ActivityManager/Activities" "CurrentActivity"]
                                    {:throw? false})]
        (let [act (str/trim (or out ""))]
          (when (and (zero? exit) (re-matches uuid-regex act))
            act)))
      (catch Exception _ nil))))

(defn panel-containment-ids
  "Returns set of containment ID strings for containments that are panels (plugin=org.kde.panel)."
  [appletsrc-text]
  (let [matches (re-seq #"(?ms)^\[Containments\]\[(\d+)\](.*?)(?=\n\[|\z)" appletsrc-text)]
    (reduce (fn [ids [_ cid body]]
              (if (re-find #"(?m)^plugin=org\.kde\.panel$" body)
                (conj ids cid)
                ids))
            #{}
            matches)))

(defn prune-stale-panels
  "Prunes [PlasmaViews][Panel N] blocks not in live-panel-ids and [Updates] blocks."
  [plasmashellrc-text live-panel-ids]
  (let [lines      (str/split-lines plasmashellrc-text)
        updates-re #"^\[Updates\]$"
        panel-re   #"^\[PlasmaViews\]\[Panel (\d+)\](.*)$"]
    (loop [remaining lines
           out       []
           skipping? false
           pruned    0]
      (if (empty? remaining)
        {:text   (-> (str/join "\n" out)
                     (str/replace #"\n{3,}" "\n\n")
                     (str/trim)
                     (str "\n"))
         :pruned pruned}
        (let [line (first remaining)]
          (cond
            ;; Panel section header
            (re-matches panel-re line)
            (let [cid (second (re-matches panel-re line))
                  skip? (not (contains? live-panel-ids cid))]
              (if skip?
                (recur (rest remaining) out true (inc pruned))
                (recur (rest remaining) (conj out line) false pruned)))

            ;; Updates section header
            (re-matches updates-re line)
            (recur (rest remaining) out true pruned)

            ;; New section header starts while skipping
            (and skipping? (str/starts-with? line "["))
            (recur (rest remaining) (conj out line) false pruned)

            ;; Inside skipped block
            skipping?
            (recur (rest remaining) out true pruned)

            ;; Normal line
            :else
            (recur (rest remaining) (conj out line) false pruned)))))))

(defn sanitize-harvest
  "Replaces home path and dynamic activity UUIDs with template placeholders, removes screenMapping."
  [content {:keys [home]}]
  (let [home-str (str/replace (str (or home (fs/expand-home "~"))) #"/$" "")
        ;; Remove transient machine screen mappings
        filtered (-> content
                     (str/replace #"(?m)^screenMapping=.*\n?" "")
                     (str/replace #"(?m)^itemsOnDisabledScreens=.*\n?" ""))
        ;; Replace home directory
        with-home (str/replace filtered home-str home-placeholder)
        ;; Replace activity IDs
        with-act  (str/replace with-home
                               (re-pattern (str "activityId=" (str uuid-regex)))
                               (str "activityId=" activity-placeholder))]
    with-act))

(defn hydrate-install
  "Hydrates template placeholders with live home directory and activity UUID."
  [content {:keys [home activity-id]}]
  (let [home-str (str/replace (str (or home (fs/expand-home "~"))) #"/$" "")
        act-id   (or activity-id (query-current-activity) "")]
    (-> content
        (str/replace home-placeholder home-str)
        (str/replace activity-placeholder act-id))))

(defn verify-widgets
  "Verifies all expected rice widgets are present in appletsrc text."
  [content]
  (let [results (mapv (fn [plugin]
                        (let [matches (re-seq (re-pattern (str "(?m)^plugin=" (java.util.regex.Pattern/quote plugin) "$")) content)
                              cnt     (count matches)]
                          {:plugin plugin
                           :count  cnt
                           :ok?    (pos? cnt)}))
                      required-plugins)
        missing (filterv (complement :ok?) results)]
    {:results results
     :missing missing
     :valid?  (empty? missing)}))
