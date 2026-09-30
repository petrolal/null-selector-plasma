(ns mono-rice.registry
  "Declarative Command Registry for mono-rice CLI."
  (:require [clojure.string :as str]
            [mono-rice.cmd.audio :as cmd-audio]
            [mono-rice.cmd.backup :as cmd-backup]

            [mono-rice.cmd.boot :as cmd-boot]
            [mono-rice.cmd.bundle :as cmd-bundle]
            [mono-rice.cmd.completion :as cmd-comp]
            [mono-rice.cmd.daemon :as cmd-daemon]
            [mono-rice.cmd.diff :as cmd-diff]
            [mono-rice.cmd.doctor :as cmd-doctor]
            [mono-rice.cmd.events :as cmd-events]
            [mono-rice.cmd.fetch :as cmd-fetch]
            [mono-rice.cmd.harvest :as cmd-harvest]
            [mono-rice.cmd.install :as cmd-install]
            [mono-rice.cmd.kwin :as cmd-kwin]
            [mono-rice.cmd.memory :as cmd-memory]
            [mono-rice.cmd.panel :as cmd-panel]
            [mono-rice.cmd.plasmoid :as cmd-plasmoid]
            [mono-rice.cmd.profile :as cmd-profile]
            [mono-rice.cmd.rclone :as cmd-rclone]
            [mono-rice.cmd.rollback :as cmd-rollback]
            [mono-rice.cmd.shortcut :as cmd-shortcut]
            [mono-rice.cmd.sync :as cmd-sync]
            [mono-rice.cmd.theme :as cmd-theme]
            [mono-rice.cmd.tui :as cmd-tui]
            [mono-rice.cmd.vault :as cmd-vault]
            [mono-rice.cmd.verify :as cmd-verify]
            [mono-rice.cmd.wallpaper :as cmd-wallpaper]
            [mono-rice.cmd.watch :as cmd-watch]
            [mono-rice.cmd.zen :as cmd-zen]
            [mono-rice.layout.inspector :as insp]
            [mono-rice.zen.mods :as zmods]))

(defn- run-boot-cmd! [manifest args opts]
  (let [subcmd (first args)]
    (case subcmd
      "status"              (cmd-boot/status opts)
      "list"                (cmd-boot/list-themes opts)
      "apply-plasma-login"  (cmd-boot/apply-plasma-login opts)
      "apply-lockscreen"    (cmd-boot/apply-plasma-login opts)
      "preview-sddm"        (cmd-boot/preview-sddm opts)
      "apply-sddm"          (cmd-boot/apply-sddm opts)
      "apply-plymouth"      (cmd-boot/apply-plymouth (second args) opts)
      (cmd-boot/status opts))))

(defn- run-vault-cmd! [manifest args opts]
  (let [subcmd (first args)]
    (case subcmd
      "scan"     (cmd-vault/scan opts)
      "sanitize" (cmd-vault/sanitize opts)
      "restore"  (cmd-vault/restore opts)
      (cmd-vault/scan opts))))

(defn- run-event-cmd! [manifest args opts]
  (let [subcmd (first args)]
    (case subcmd
      "listen" (cmd-events/listen opts)
      "emit"   (cmd-events/emit (or (second args) "org.kde.KScreen") opts)
      (cmd-events/listen opts))))

(def command-registry
  {:tui           {:handler (fn [m a opts] (cmd-tui/run-tui! m opts))
                   :category :ui
                   :doc "Launch interactive Cyberpunk terminal dashboard menu"}
   :doctor        {:handler (fn [m a opts] (cmd-doctor/run-doctor-cmd! m a opts))
                   :category :system
                   :doc "Deep system diagnostic health checks and auto-repair (--fix)"}
   :install       {:handler (fn [m a opts] (cmd-install/install! m opts))
                   :category :lifecycle
                   :doc "Deploy and install complete sanguine-node-rice"}
   :harvest       {:handler (fn [m a opts] (cmd-harvest/harvest! m opts))
                   :category :lifecycle
                   :doc "Scrape live $HOME configs back into repository with template sanitization"}
   :backup        {:handler (fn [m a opts] (cmd-backup/backup! m opts))
                   :category :lifecycle
                   :doc "Create timestamped backup snapshot of current configurations"}
   :rollback      {:handler (fn [m a opts] (cmd-rollback/run-rollback-cmd! a opts))
                   :category :lifecycle
                   :doc "List and restore timestamped rollback configuration snapshots"}
   :diff          {:handler (fn [m a opts] (cmd-diff/run-diff-cmd! m a opts))
                   :category :lifecycle
                   :doc "Inspect line-by-line visual differences between repo and $HOME"}
   :verify        {:handler (fn [m a opts] (cmd-verify/verify! m opts))
                   :category :system
                   :doc "Verify system health, package dependencies, and layout integrity"}
   :theme         {:handler (fn [m a opts] (cmd-theme/run-theme-cmd! m a opts))
                   :category :ui
                   :doc "List or switch active theme profile (list | set <name>)"}
   :shortcut      {:handler (fn [m a opts] (cmd-shortcut/run-shortcut-cmd! m a opts))
                   :category :ui
                   :doc "Declarative Plasma shortcut and hotkey manager (list | apply | export)"}
   :zen           {:handler (fn [m a opts] (cmd-zen/run-zen-cmd! m a opts))
                   :category :ui
                   :doc "Zen Browser profile status, glass styling, and extension manager"}
   :panel         {:handler (fn [m a opts] (cmd-panel/run-panel-cmd! m a opts))
                   :category :ui
                   :doc "Panel Colorizer capsule preset selector and hot-reloader"}
   :kwin          {:handler (fn [m a opts] (cmd-kwin/run-kwin-cmd! m a opts))
                   :category :ui
                   :doc "Declarative window rules, translucency, and blur effects manager"}
   :fetch         {:handler (fn [m a opts] (cmd-fetch/run-fetch-cmd! a opts))
                   :category :ui
                   :doc "Fastfetch and terminal ASCII aesthetic logo synchronizer"}
   :profile       {:handler (fn [m a opts] (cmd-profile/run-profile-cmd! a opts))
                   :category :system
                   :doc "Hardware environment detector and form-factor profile tuner"}
   :rclone        {:handler (fn [m a opts] (cmd-rclone/run-rclone-cmd! m a opts))
                   :category :cloud
                   :doc "Google Drive cloud bisync & systemd timer manager (status | setup | sync)"}
   :sync          {:handler (fn [m a opts] (cmd-sync/run-sync-cmd! m a opts))
                   :category :cloud
                   :doc "Two-way remote Git synchronization and dotfile hub (status | pull | push)"}
   :memory        {:handler (fn [m a opts] (cmd-memory/run-memory-cmd! m a opts))
                   :category :system
                   :doc "System memory stability, ZRAM swap, systemd-oomd, and Baloo optimizer"}
   :boot          {:handler run-boot-cmd!
                   :category :system
                   :doc "SDDM display manager & Plymouth boot splash theme orchestrator"}
   :completion    {:handler (fn [m a opts] (cmd-comp/generate (first a) opts))
                   :category :system
                   :doc "Generate shell auto-completions (fish | zsh | bash) [--install]"}
   :vault         {:handler run-vault-cmd!
                   :category :security
                   :doc "Security audit, secret scanner & dotfile sanitizer (scan | sanitize | restore)"}
   :event         {:handler run-event-cmd!
                   :category :sentinel
                   :doc "DBus desktop event listener & dynamic display sentinel (listen | emit)"}
   :wallpaper     {:handler (fn [m a opts] (cmd-wallpaper/run-wallpaper-cmd! m a opts))
                   :category :ui
                   :doc "Manage and sync desktop and lockscreen 4K video wallpapers"}
   :audio         {:handler (fn [m a opts] (cmd-audio/run-audio-cmd! a opts))
                   :category :ui
                   :doc "Configure and switch CAVA / Kurve audio equalizer presets"}
   :watch         {:handler (fn [m a opts] (cmd-watch/run-watch-cmd! m opts))
                   :category :sentinel
                   :doc "Monitor configuration drift against repository templates"}
   :daemon        {:handler (fn [m a opts] (cmd-daemon/run-daemon-cmd! m a opts))
                   :category :sentinel
                   :doc "Background sentinel service and desktop notification manager"}
   :bundle        {:handler (fn [m a opts] (cmd-bundle/run-bundle-cmd! a opts))
                   :category :lifecycle
                   :doc "Export or import portable rice configuration archive (export | import)"}
   :plasmoid      {:handler (fn [m a opts] (cmd-plasmoid/run-plasmoid-cmd! a opts))
                   :category :ui
                   :doc "Manage KDE 6 desktop plasmoids (list | install <path> | remove <id>)"}
   :dump-widgets  {:handler (fn [m a opts] (insp/dump-widgets))
                   :category :ui
                   :doc "Pretty-print active containment and plasmoid tree"}
   :open-zen-mods {:handler (fn [m a opts] (zmods/open-zen-mods! m))
                   :category :ui
                   :doc "Open Zen Browser mod install pages for one-click setup"}})


(defn list-command-names []
  (map name (keys command-registry)))

(defn get-command [cmd-str]
  (when cmd-str
    (get command-registry (keyword (str/replace (str/lower-case (name cmd-str)) #"^:" "")))))
