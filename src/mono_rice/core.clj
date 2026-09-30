(ns mono-rice.core
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [clojure.edn :as edn]
            [mono-rice.fs :as rfs]
            [mono-rice.proc :refer [log-error]]
            [mono-rice.registry :as reg]
            [mono-rice.schema :as schema]))

(defn load-manifest! []
  (let [root (rfs/repo-root)
        manifest-path (fs/path root "rice.edn")]
    (if (fs/exists? manifest-path)
      (let [manifest (edn/read-string (slurp (str manifest-path)))]
        (schema/validate! manifest))
      (throw (ex-info (str "Manifest not found: " manifest-path) {})))))

(defn print-help []
  (println "Usage: mono-rice <command> [options]")
  (println)
  (println "Commands:")
  (doseq [[k v] reg/command-registry]
    (println (format "  %-14s %s" (name k) (:doc v))))
  (println)
  (println "Options:")
  (println "  -n, --dry-run        Simulate operations without making changes")
  (println "  -y, --yes            Non-interactive mode (answer yes to all prompts)")
  (println "  -s, --symlinks-only  Deploy symlinks and configurations only (skip package manager)")
  (println "  -d, --deps-only      Install package dependencies only")
  (println "      --fix            Automatically remediate detected issues in doctor command")
  (println "      --install        Install generated shell completions directly")
  (println "      --once           Run watch sentinel once and exit immediately")
  (println "      --interval <sec> Set polling interval for watch/daemon (default: 10)")
  (println "      --no-backup      Skip backing up existing configuration files")
  (println "      --no-layout      Skip applying desktop and panel layout")
  (println "      --no-rclone      Skip configuring rclone Google Drive cloud sync")
  (println "      --no-memory      Skip system memory, ZRAM, and OOM stability tuning")
  (println "      --sddm           Install SDDM Monochrome theme")
  (println "      --plymouth       Install dotLock Plymouth boot splash theme")
  (println "      --open-zen-mods  Open Zen Mod pages after installation")
  (println "      --no-restart     Skip restarting plasmashell after layout deploy")
  (println "      --no-shell-change Skip automatically setting Fish as default shell")
  (println "      --reboot         Reboot system immediately after installation")
  (println "      --no-reboot      Do not prompt or reboot after installation")
  (println "  -h, --help           Show this help message"))

(def cli-spec
  {:spec
   {:dry-run       {:alias :n :coerce :boolean :desc "Simulate installation without modifying files"}
    :yes           {:alias :y :coerce :boolean :desc "Non-interactive mode"}
    :symlinks-only {:alias :s :coerce :boolean :desc "Deploy symlinks only"}
    :deps-only     {:alias :d :coerce :boolean :desc "Install dependencies only"}
    :fix           {:alias :f :coerce :boolean :desc "Auto-repair detected issues"}
    :install       {:coerce :boolean :desc "Install completions to user path"}
    :once          {:coerce :boolean :desc "Run once and exit"}
    :interval      {:coerce :int :desc "Interval in seconds"}
    :no-backup     {:coerce :boolean :desc "Skip backing up existing configs"}
    :no-layout     {:coerce :boolean :desc "Skip panel layout"}
    :no-rclone     {:coerce :boolean :desc "Skip rclone cloud sync setup"}
    :no-memory     {:coerce :boolean :desc "Skip memory, ZRAM, and OOM tuning"}
    :sddm          {:coerce :boolean :desc "Install SDDM theme"}
    :plymouth      {:coerce :boolean :desc "Install Plymouth theme"}
    :open-zen-mods    {:coerce :boolean :desc "Open Zen Mod pages"}
    :no-restart       {:coerce :boolean :desc "Skip restarting plasmashell"}
    :no-shell-change  {:coerce :boolean :desc "Skip setting default shell to fish"}
    :reboot           {:coerce :boolean :desc "Reboot immediately after installation"}
    :no-reboot        {:coerce :boolean :desc "Skip reboot prompt after installation"}
    :help             {:alias :h :coerce :boolean :desc "Show help"}}})

(defn -main [& args]
  (let [args-vec  (vec args)
        cmd       (first args-vec)
        rest-args (subvec args-vec (if (empty? args-vec) 0 1))
        opts      (cli/parse-opts rest-args cli-spec)]
    (if (or (:help opts) (empty? args-vec) (#{"-h" "--help" "help"} cmd))
      (print-help)
      (if-let [entry (reg/get-command cmd)]
        (let [manifest (load-manifest!)]
          ((:handler entry) manifest rest-args opts))
        (do
          (log-error "Unknown command:" cmd)
          (print-help)
          (System/exit 1))))))

