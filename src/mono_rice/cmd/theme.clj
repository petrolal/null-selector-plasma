(ns mono-rice.cmd.theme
  (:require [clojure.string :as str]
            [mono-rice.proc :refer [log-info log-success log-warn log-step]]
            [mono-rice.theme :as theme]))

(defn run-theme-cmd!
  "Handles the theme CLI subcommand: list, set <profile>, or current."
  [manifest args opts]
  (let [subcmd (first args)
        param  (second args)]
    (case subcmd
      ("list" "ls" nil)
      (do
        (println "Available Theme Profiles:")
        (println "========================")
        (doseq [[k prof] (theme/list-profiles manifest)]
          (println (format "  * %-18s [%s] - %s"
                           (name k)
                           (:color-scheme prof)
                           (:description prof))))
        (println)
        (println "Switch theme using: mono-rice theme set <profile-name>"))

      ("set" "apply")
      (if (str/blank? param)
        (do
          (log-warn "Please specify a theme profile to set.")
          (println "Usage: mono-rice theme set <profile-name>"))
        (theme/switch-theme! manifest param opts))

      (if (theme/get-profile manifest subcmd)
        (theme/switch-theme! manifest subcmd opts)
        (do
          (log-warn (str "Unknown theme subcommand or profile: " subcmd))
          (println "Usage: mono-rice theme [list | set <name>]"))))))
