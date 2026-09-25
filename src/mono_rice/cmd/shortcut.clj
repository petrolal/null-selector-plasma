(ns mono-rice.cmd.shortcut
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [mono-rice.kde :as kde]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh!]]))

(defn list-shortcuts [manifest]
  (println "Declarative Plasma & Rice Shortcuts:")
  (println "=====================================")
  (let [scs (:shortcuts manifest)]
    (doseq [[k v] scs]
      (let [key-str (if (vector? v) (str/join " | " v) (str v))]
        (println (format "  * %-16s : %s" (name k) key-str))))
    (println)
    (println "Apply shortcuts with: mono-rice shortcut apply")
    scs))

(defn apply-shortcuts! [manifest & [{:keys [dry-run]}]]
  (log-step "Applying Declarative System Shortcuts")
  (let [term-service (get-in manifest [:terminal :service] "org.kde.konsole.desktop")
        term-keys    (get-in manifest [:shortcuts :terminal] ["Ctrl+Alt+T" "Meta+Return"])
        term-str     (str (str/join "\t" term-keys) "," (str/join "\t" term-keys) ",Konsole")]

    ;; 1. Terminal Launcher
    (kde/set-kconfig! {:file "kglobalshortcutsrc" :group ["services" term-service] :key "_launch" :value term-str :dry-run dry-run})
    (kde/set-kconfig! {:file "kglobalshortcutsrc" :group ["services" "cool-retro-term.desktop"] :key "_launch" :value "none,none,Cool Retro Term" :dry-run dry-run})

    ;; 2. KWin Tiles Editor
    (kde/set-kconfig! {:file "kglobalshortcutsrc" :group "kwin" :key "Edit Tiles" :value "none,none,Toggle Tiles Editor" :dry-run dry-run})

    ;; 3. Restart Global Shortcuts Daemon
    (when (command-exists? "systemctl")
      (sh! ["systemctl" "--user" "restart" "plasma-kglobalaccel"] {:dry-run dry-run :throw? false}))

    (log-success "System shortcuts applied and daemon restarted.")))

(defn export-shortcuts [manifest output-path & [{:keys [dry-run]}]]
  (let [out-file (or output-path "shortcuts-export.json")
        scs      (:shortcuts manifest)
        json-str (json/generate-string scs {:pretty true})]
    (log-step (str "Exporting Shortcuts -> " out-file))
    (if dry-run
      (do
        (log-info "[DRY-RUN] Would write shortcut schema to" out-file)
        out-file)
      (do
        (spit out-file json-str)
        (log-success "Shortcuts exported successfully to:" out-file)
        out-file))))

(defn run-shortcut-cmd!
  "Handles the shortcut CLI subcommand."
  [manifest args opts]
  (let [subcmd (first args)
        param  (second args)]
    (case subcmd
      ("list" "ls" nil)
      (list-shortcuts manifest)

      ("apply" "set")
      (apply-shortcuts! manifest opts)

      ("export" "dump")
      (export-shortcuts manifest param opts)

      (do
        (log-warn "Unknown shortcut command:" subcmd)
        (println "Usage:")
        (println "  mono-rice shortcut list")
        (println "  mono-rice shortcut apply")
        (println "  mono-rice shortcut export [file.json]")))))
