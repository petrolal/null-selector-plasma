(ns mono-rice.core
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [clojure.edn :as edn]
            [mono-rice.cmd.audio :as cmd-audio]
            [mono-rice.cmd.backup :as cmd-backup]
            [mono-rice.cmd.bundle :as cmd-bundle]
            [mono-rice.cmd.daemon :as cmd-daemon]
            [mono-rice.cmd.diff :as cmd-diff]
            [mono-rice.cmd.harvest :as cmd-harvest]
            [mono-rice.cmd.install :as cmd-install]
            [mono-rice.cmd.plasmoid :as cmd-plasmoid]
            [mono-rice.cmd.rollback :as cmd-rollback]
            [mono-rice.cmd.theme :as cmd-theme]
            [mono-rice.cmd.tui :as cmd-tui]
            [mono-rice.cmd.verify :as cmd-verify]
            [mono-rice.cmd.wallpaper :as cmd-wallpaper]
            [mono-rice.cmd.watch :as cmd-watch]
            [mono-rice.fs :as rfs]
            [mono-rice.layout.inspector :as insp]
            [mono-rice.proc :refer [log-error]]
            [mono-rice.zen.mods :as zmods]))

(defn load-manifest! []
  (let [root (rfs/repo-root)
        manifest-path (fs/path root "rice.edn")]
    (if (fs/exists? manifest-path)
      (edn/read-string (slurp (str manifest-path)))
      (throw (ex-info (str "Manifest not found: " manifest-path) {})))))

(defn print-help []
  (println "Usage: mono-rice <command> [options]")
  (println)
  (println "Commands:")
  (println "  tui           Launch interactive Cyberpunk terminal dashboard menu")
  (println "  install       Deploy and install complete null-sector-plasma rice")
  (println "  harvest       Scrape live $HOME configs back into repository with template sanitization")
  (println "  backup        Create timestamped backup snapshot of current configurations")
  (println "  rollback      List and restore timestamped rollback configuration snapshots")
  (println "  diff          Inspect line-by-line visual differences between repo and $HOME")
  (println "  verify        Verify system health, package dependencies, and layout integrity")
  (println "  theme         List or switch active theme profile (list | set <name>)")
  (println "  wallpaper     Manage and sync desktop and lockscreen 4K video wallpapers")
  (println "  audio         Configure and switch CAVA / Kurve audio equalizer presets")
  (println "  watch         Monitor configuration drift against repository templates")
  (println "  daemon        Background sentinel service and desktop notification manager")
  (println "  bundle        Export or import portable rice configuration archive (export | import)")
  (println "  plasmoid      Manage KDE 6 desktop plasmoids (list | install <path> | remove <id>)")
  (println "  dump-widgets  Pretty-print active containment and plasmoid tree")
  (println "  open-zen-mods Open Zen Browser mod install pages for one-click setup")
  (println)
  (println "Options:")
  (println "  -n, --dry-run        Simulate operations without making changes")
  (println "  -y, --yes            Non-interactive mode (answer yes to all prompts)")
  (println "  -s, --symlinks-only  Deploy symlinks and configurations only (skip package manager)")
  (println "  -d, --deps-only      Install package dependencies only")
  (println "      --once           Run watch sentinel once and exit immediately")
  (println "      --interval <sec> Set polling interval for watch/daemon (default: 10)")
  (println "      --no-backup      Skip backing up existing configuration files")
  (println "      --no-layout      Skip applying desktop and panel layout")
  (println "      --sddm           Install SDDM Monochrome theme")
  (println "      --plymouth       Install dotLock Plymouth boot splash theme")
  (println "      --open-zen-mods  Open Zen Mod pages after installation")
  (println "      --no-restart     Skip restarting plasmashell after layout deploy")
  (println "  -h, --help           Show this help message"))

(def cli-spec
  {:spec
   {:dry-run       {:alias :n :coerce :boolean :desc "Simulate installation without modifying files"}
    :yes           {:alias :y :coerce :boolean :desc "Non-interactive mode"}
    :symlinks-only {:alias :s :coerce :boolean :desc "Deploy symlinks only"}
    :deps-only     {:alias :d :coerce :boolean :desc "Install dependencies only"}
    :once          {:coerce :boolean :desc "Run once and exit"}
    :interval      {:coerce :int :desc "Interval in seconds"}
    :no-backup     {:coerce :boolean :desc "Skip backing up existing configs"}
    :no-layout     {:coerce :boolean :desc "Skip panel layout"}
    :sddm          {:coerce :boolean :desc "Install SDDM theme"}
    :plymouth      {:coerce :boolean :desc "Install Plymouth theme"}
    :open-zen-mods {:coerce :boolean :desc "Open Zen Mod pages"}
    :no-restart    {:coerce :boolean :desc "Skip restarting plasmashell"}
    :help          {:alias :h :coerce :boolean :desc "Show help"}}})

(defn -main [& args]
  (let [args-vec  (vec args)
        cmd       (first args-vec)
        rest-args (subvec args-vec (if (empty? args-vec) 0 1))
        opts      (cli/parse-opts rest-args cli-spec)]
    (if (or (:help opts) (empty? args-vec) (#{"-h" "--help" "help"} cmd))
      (print-help)
      (let [manifest (load-manifest!)]
        (case cmd
          "tui"           (cmd-tui/run-tui! manifest opts)
          "install"       (cmd-install/install! manifest opts)
          "backup"        (cmd-backup/backup! manifest opts)
          "rollback"      (cmd-rollback/run-rollback-cmd! rest-args opts)
          "diff"          (cmd-diff/run-diff-cmd! manifest rest-args opts)
          "harvest"       (cmd-harvest/harvest! manifest opts)
          "verify"        (cmd-verify/verify! manifest opts)
          "theme"         (cmd-theme/run-theme-cmd! manifest rest-args opts)
          "wallpaper"     (cmd-wallpaper/run-wallpaper-cmd! rest-args opts)
          "audio"         (cmd-audio/run-audio-cmd! rest-args opts)
          "watch"         (cmd-watch/run-watch-cmd! manifest opts)
          "daemon"        (cmd-daemon/run-daemon-cmd! manifest rest-args opts)
          "bundle"        (cmd-bundle/run-bundle-cmd! manifest rest-args opts)
          "plasmoid"      (cmd-plasmoid/run-plasmoid-cmd! rest-args opts)
          "dump-widgets"  (insp/dump-widgets)
          "open-zen-mods" (zmods/open-zen-mods! manifest)
          (do
            (log-error "Unknown command:" cmd)
            (print-help)
            (System/exit 1)))))))
