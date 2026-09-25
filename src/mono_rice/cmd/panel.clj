(ns mono-rice.cmd.panel
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.fs :as rfs]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh!]]))

(defn list-available-presets [manifest]
  (let [root (rfs/repo-root)
        preset-dir (fs/path root "plasma" ".config" "panel-colorizer" "presets")
        files (if (fs/exists? preset-dir)
                (->> (fs/list-dir preset-dir)
                     (mapv #(str (fs/file-name %))))
                [])
        declared (:panel-presets manifest)]
    (println "Available Panel Colorizer Capsule Presets:")
    (println "=========================================")
    (doseq [[k v] declared]
      (println (format "  * %-16s : %s" (name k) (:name v))))
    (println)
    (println "Discovered Preset Files on Disk:")
    (doseq [f files]
      (println (format "  - %s" f)))
    (println)
    (println "Activate a preset with: mono-rice panel set <preset-name>")
    {:declared declared :files files}))

(defn reload-panels! [& [{:keys [dry-run]}]]
  (log-step "Reloading Plasma Panels & Colorizer Extensions")
  (if dry-run
    (log-info "[DRY-RUN] Would restart plasma shell and reload panel extensions")
    (do
      (when (command-exists? "qdbus6")
        (sh! ["qdbus6" "org.kde.plasmashell" "/PlasmaShell" "refreshCurrentShell"] {:throw? false}))
      (when (command-exists? "systemctl")
        (sh! ["systemctl" "--user" "restart" "plasma-plasmashell"] {:throw? false}))
      (log-success "Panel refresh signaled."))))

(defn apply-panel-preset! [preset-name & [{:keys [dry-run]}]]
  (log-step (str "Activating Panel Colorizer Preset -> " preset-name))
  (let [root (rfs/repo-root)
        home (rfs/home-dir)
        target-file (fs/path root "plasma" ".config" "panel-colorizer" "presets" preset-name)
        live-file   (fs/path home ".config" "panel-colorizer" "presets" preset-name)]
    (if-not (or (fs/exists? target-file) (fs/exists? live-file))
      (do
        (log-warn "Preset file not found:" preset-name)
        false)
      (do
        (if dry-run
          (log-info "[DRY-RUN] Would activate preset" preset-name "and reload panel colorizer")
          (reload-panels! {:dry-run dry-run}))
        (log-success "Panel Colorizer preset activated:" preset-name)
        true))))

(defn run-panel-cmd!
  "Handles the panel CLI subcommand."
  [manifest args opts]
  (let [subcmd (first args)
        param  (second args)]
    (case subcmd
      ("list" "ls" nil)
      (list-available-presets manifest)

      ("set" "apply")
      (if (str/blank? param)
        (do
          (log-warn "Please specify preset name to activate.")
          (println "Usage: mono-rice panel set <preset-name>"))
        (apply-panel-preset! param opts))

      ("reload" "refresh")
      (reload-panels! opts)

      (if (apply-panel-preset! subcmd opts)
        true
        (do
          (log-warn "Unknown panel command:" subcmd)
          (println "Usage:")
          (println "  mono-rice panel list")
          (println "  mono-rice panel set <preset-name>")
          (println "  mono-rice panel reload"))))))
