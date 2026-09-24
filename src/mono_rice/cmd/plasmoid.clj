(ns mono-rice.cmd.plasmoid
  (:require [clojure.string :as str]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh!]]))

(defn list-installed-plasmoids []
  (if (command-exists? "kpackagetool6")
    (let [{:keys [exit out]} (sh! ["kpackagetool6" "--type" "Plasma/Applet" "--list"] {:throw? false})]
      (if (zero? exit)
        (->> (str/split-lines out)
             (filter #(str/starts-with? % "org.") )
             vec)
        []))
    []))

(defn install-plasmoid! [target-path & [{:keys [dry-run]}]]
  (log-step (str "Installing Plasmoid -> " target-path))
  (if-not (command-exists? "kpackagetool6")
    (log-warn "kpackagetool6 not found on system.")
    (if dry-run
      (log-info "[DRY-RUN] Would run: kpackagetool6 --type Plasma/Applet --install" target-path)
      (let [{:keys [exit out err]} (sh! ["kpackagetool6" "--type" "Plasma/Applet" "--install" target-path] {:throw? false})]
        (if (zero? exit)
          (log-success "Plasmoid installed successfully.")
          (let [{:keys [exit]} (sh! ["kpackagetool6" "--type" "Plasma/Applet" "--upgrade" target-path] {:throw? false})]
            (if (zero? exit)
              (log-success "Plasmoid updated successfully.")
              (log-warn "Failed to install/upgrade plasmoid:" err))))))))

(defn remove-plasmoid! [plugin-id & [{:keys [dry-run]}]]
  (log-step (str "Removing Plasmoid -> " plugin-id))
  (if-not (command-exists? "kpackagetool6")
    (log-warn "kpackagetool6 not found on system.")
    (if dry-run
      (log-info "[DRY-RUN] Would run: kpackagetool6 --type Plasma/Applet --remove" plugin-id)
      (let [{:keys [exit out err]} (sh! ["kpackagetool6" "--type" "Plasma/Applet" "--remove" plugin-id] {:throw? false})]
        (if (zero? exit)
          (log-success "Plasmoid removed successfully.")
          (log-warn "Failed to remove plasmoid:" err))))))

(defn run-plasmoid-cmd!
  "Handles the plasmoid CLI subcommand: list, install <path>, or remove <id>."
  [args opts]
  (let [subcmd (first args)
        target (second args)]
    (case subcmd
      ("list" "ls" nil)
      (do
        (println "Installed User Plasmoids:")
        (println "========================")
        (let [plasmoids (list-installed-plasmoids)]
          (if (empty? plasmoids)
            (println "  (No user plasmoids listed via kpackagetool6)")
            (doseq [p plasmoids]
              (println "  *" p))))
        (println))

      ("install" "add")
      (if (str/blank? target)
        (do
          (log-warn "Please specify a plasmoid file or folder path.")
          (println "Usage: mono-rice plasmoid install <path>"))
        (install-plasmoid! target opts))

      ("remove" "rm")
      (if (str/blank? target)
        (do
          (log-warn "Please specify plugin id to remove.")
          (println "Usage: mono-rice plasmoid remove <plugin-id>"))
        (remove-plasmoid! target opts))

      (do
        (log-warn "Unknown plasmoid command:" subcmd)
        (println "Usage:")
        (println "  mono-rice plasmoid list")
        (println "  mono-rice plasmoid install <path>")
        (println "  mono-rice plasmoid remove <plugin-id>")))))
