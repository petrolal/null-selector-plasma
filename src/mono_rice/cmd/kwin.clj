(ns mono-rice.cmd.kwin
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.kde :as kde]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh!]]))

(def default-window-rules
  [{:id "zen-transparency"
    :desc "Zen Browser Translucency"
    :match-class "zen"
    :noborder true
    :opacity 100}
   {:id "konsole-transparency"
    :desc "Konsole Terminal Glass"
    :match-class "org.kde.konsole"
    :noborder false
    :opacity 90}
   {:id "discord-translucency"
    :desc "Discord Cyberpunk Frame"
    :match-class "discord"
    :noborder false
    :opacity 95}
   {:id "spotify-translucency"
    :desc "Spotify Translucent Player"
    :match-class "spotify"
    :noborder false
    :opacity 95}])

(defn apply-window-rules! [manifest & [{:keys [dry-run]}]]
  (log-step "Deploying Declarative KWin Window Rules")
  (doseq [{:keys [id desc match-class noborder opacity]} default-window-rules]
    (let [group (str "Rule-" id)]
      (kde/set-kconfig! {:file "kwinrulesrc" :group group :key "Description" :value desc :dry-run dry-run})
      (kde/set-kconfig! {:file "kwinrulesrc" :group group :key "wmclass" :value match-class :dry-run dry-run})
      (kde/set-kconfig! {:file "kwinrulesrc" :group group :key "wmclassmatch" :value "1" :type :int :dry-run dry-run})
      (when noborder
        (kde/set-kconfig! {:file "kwinrulesrc" :group group :key "noborder" :value "true" :type :bool :dry-run dry-run})
        (kde/set-kconfig! {:file "kwinrulesrc" :group group :key "noborderrule" :value "2" :type :int :dry-run dry-run}))
      (when (< opacity 100)
        (kde/set-kconfig! {:file "kwinrulesrc" :group group :key "opacityactive" :value (str opacity) :type :int :dry-run dry-run})
        (kde/set-kconfig! {:file "kwinrulesrc" :group group :key "opacityactiverule" :value "2" :type :int :dry-run dry-run}))))
  (when (command-exists? "qdbus6")
    (sh! ["qdbus6" "org.kde.KWin" "/KWin" "reconfigure"] {:dry-run dry-run :throw? false}))
  (log-success "KWin window rules deployed and active."))

(defn set-blur-strength! [strength & [{:keys [dry-run]}]]
  (log-step (str "Tuning KWin Blur Shader Strength -> " strength))
  (let [str-val (str strength)]
    (kde/set-kconfig! {:file "kwinrc" :group "Effect-better-blur-dx" :key "BlurStrength" :value str-val :dry-run dry-run})
    (kde/set-kconfig! {:file "kwinrc" :group "Effect-forceblur" :key "BlurStrength" :value str-val :dry-run dry-run})
    (when (command-exists? "qdbus6")
      (sh! ["qdbus6" "org.kde.KWin" "/KWin" "reconfigure"] {:dry-run dry-run :throw? false}))
    (log-success "KWin blur strength updated to:" str-val)))

(defn run-kwin-cmd!
  "Handles the kwin CLI subcommand."
  [manifest args opts]
  (let [subcmd (first args)
        param  (second args)]
    (case subcmd
      ("rules" "list-rules" nil)
      (do
        (println "Declarative KWin Window Rules:")
        (println "==============================")
        (doseq [{:keys [id desc match-class opacity noborder]} default-window-rules]
          (println (format "  * %-22s [%s] - Opacity: %d%%, Borderless: %s"
                           desc match-class opacity (if noborder "Yes" "No"))))
        (println)
        (println "Apply rules with: mono-rice kwin apply-rules"))

      ("apply-rules" "deploy")
      (apply-window-rules! manifest opts)

      ("set-blur" "blur")
      (if (str/blank? param)
        (do
          (log-warn "Please specify blur strength value (1-10).")
          (println "Usage: mono-rice kwin set-blur <strength>"))
        (set-blur-strength! param opts))

      (do
        (log-warn "Unknown kwin command:" subcmd)
        (println "Usage:")
        (println "  mono-rice kwin rules")
        (println "  mono-rice kwin apply-rules")
        (println "  mono-rice kwin set-blur <strength>")))))
