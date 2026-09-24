(ns mono-rice.cmd.tui
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.cmd.backup :as cmd-backup]
            [mono-rice.cmd.bundle :as cmd-bundle]
            [mono-rice.cmd.diff :as cmd-diff]
            [mono-rice.cmd.harvest :as cmd-harvest]
            [mono-rice.cmd.install :as cmd-install]
            [mono-rice.cmd.plasmoid :as cmd-plasmoid]
            [mono-rice.cmd.rollback :as cmd-rollback]
            [mono-rice.cmd.theme :as cmd-theme]
            [mono-rice.cmd.verify :as cmd-verify]
            [mono-rice.cmd.watch :as cmd-watch]
            [mono-rice.fs :as rfs]
            [mono-rice.proc :refer [log-info log-success log-warn log-step]]))

(def banner
  "
  \u001b[36m╔═══════════════════════════════════════════════════════════════╗
  ║    \u001b[1;37m█▄░█ █░█ █░░ █░░ ▄▄ █▀ █▀▀ █▀▀ ▀█▀ █▀█ █▀█   █▀█ █░░ ▄▀█ █▀ █▀▄▀█ ▄▀█\u001b[0;36m    ║
  ║    \u001b[1;37m█░▀█ █▄█ █▄▄ █▄▄ ░░ ▄█ ██▄ █▄▄ ░█░ █▄█ █▀▄   █▀▀ █▄▄ █▀█ ▄█ █░▀░█ █▀█\u001b[0;36m    ║
  ║                                                               ║
  ║       \u001b[33mAutomated • Declarative • NieR Cyberpunk KDE 6 Rice\u001b[0;36m       ║
  ╚═══════════════════════════════════════════════════════════════╝\u001b[0m")

(defn print-dashboard [manifest]
  (println banner)
  (let [theme-name (get-in manifest [:theme :color-scheme] "Monochrome")
        backups    (count (rfs/list-backups))
        desktop    (or (System/getenv "XDG_CURRENT_DESKTOP") "KDE Plasma 6")]
    (println (format "  \u001b[1;32m● System:\u001b[0m %-20s \u001b[1;35m● Theme:\u001b[0m %-18s \u001b[1;33m● Backups:\u001b[0m %d"
                     desktop theme-name backups))
    (println "  ─────────────────────────────────────────────────────────────")
    (println "  \u001b[1;37m[1]\u001b[0m Deploy / Install Rice           \u001b[1;37m[7]\u001b[0m Inspect Visual Diffs")
    (println "  \u001b[1;37m[2]\u001b[0m Harvest Active Configs          \u001b[1;37m[8]\u001b[0m Manage Rollback Snapshots")
    (println "  \u001b[1;37m[3]\u001b[0m Switch Theme Profile            \u001b[1;37m[9]\u001b[0m Manage Plasmoid Applets")
    (println "  \u001b[1;37m[4]\u001b[0m Check Configuration Drift      \u001b[1;37m[10]\u001b[0m Export / Import Rice Bundle")
    (println "  \u001b[1;37m[5]\u001b[0m Verify System & Widgets        \u001b[1;37m[11]\u001b[0m Wallpaper & Video Sync")
    (println "  \u001b[1;37m[6]\u001b[0m Create Rollback Snapshot       \u001b[1;37m[12]\u001b[0m Audio Visualizer Presets")
    (println "  \u001b[1;31m[0]\u001b[0m Exit Console")
    (println "  ─────────────────────────────────────────────────────────────")))

(defn run-tui!
  "Launches the interactive TUI menu."
  [manifest & [{:keys [dry-run]}]]
  (if dry-run
    (do
      (print-dashboard manifest)
      (println "[DRY-RUN] TUI dashboard rendered successfully.")
      true)
    (loop []
      (print-dashboard manifest)
      (print "  Enter selection (0-12) > ")
      (flush)
      (let [input (str/trim (or (read-line) "0"))]
        (case input
          "1"  (do (cmd-install/install! manifest {}) (recur))
          "2"  (do (cmd-harvest/harvest! manifest {}) (recur))
          "3"  (do (cmd-theme/run-theme-cmd! manifest ["list"] {}) (recur))
          "4"  (do (cmd-watch/run-watch-cmd! manifest {:once true}) (recur))
          "5"  (do (cmd-verify/verify! manifest {}) (recur))
          "6"  (do (cmd-backup/backup! manifest {}) (recur))
          "7"  (do (cmd-diff/run-diff-cmd! manifest [] {}) (recur))
          "8"  (do (cmd-rollback/run-rollback-cmd! ["list"] {}) (recur))
          "9"  (do (cmd-plasmoid/run-plasmoid-cmd! ["list"] {}) (recur))
          "10" (do (cmd-bundle/run-bundle-cmd! manifest ["export"] {}) (recur))
          ("0" "q" "exit") (println "\nExiting null-sector console.")
          (do
            (log-warn "Invalid selection:" input)
            (recur)))))))
