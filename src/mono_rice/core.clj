(ns mono-rice.core
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [clojure.edn :as edn]
            [mono-rice.fs :as rfs]
            [mono-rice.kde :as rkde]
            [mono-rice.proc :refer [log-error log-info log-step log-success log-warn sh!]]))

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
  (println "  install       Deploy and install complete null-sector-plasma rice")
  (println "  harvest       Scrape live $HOME configs back into repository")
  (println "  backup        Create timestamped backup snapshot of current configurations")
  (println "  verify        Verify configuration drift and required rice components")
  (println "  dump-widgets  Pretty-print active containment/plasmoid tree")
  (println "  open-zen-mods Open Zen Browser mod install pages")
  (println)
  (println "Options:")
  (println "  -n, --dry-run        Simulate operations without making changes")
  (println "  -y, --yes            Non-interactive mode (answer yes to all prompts)")
  (println "  -s, --symlinks-only  Deploy symlinks and configurations only")
  (println "  -d, --deps-only      Install package dependencies only")
  (println "      --no-backup      Skip backing up existing configuration files")
  (println "      --no-layout      Skip applying desktop and panel layout")
  (println "  -h, --help           Show this help message"))

(defn cmd-backup [opts]
  (let [manifest (load-manifest!)
        targets  (:backup-targets manifest)]
    (rfs/backup-configs! targets opts)))

(defn cmd-install [opts]
  (log-step "Starting Deployment of null-sector-plasma")
  (let [manifest (load-manifest!)]
    (rkde/check-system! opts)
    (when-not (:no-backup opts)
      (rfs/backup-configs! (:backup-targets manifest) opts))
    (rfs/clean-broken-symlinks! opts)
    (rkde/apply-theme! manifest opts)
    (log-step "Phase 1 Foundation Operations Completed Successfully!")))

(def cli-spec
  {:spec
   {:dry-run       {:alias :n :coerce :boolean :desc "Simulate installation without modifying files"}
    :yes           {:alias :y :coerce :boolean :desc "Non-interactive mode"}
    :symlinks-only {:alias :s :coerce :boolean :desc "Deploy symlinks only"}
    :deps-only     {:alias :d :coerce :boolean :desc "Install dependencies only"}
    :no-backup     {:coerce :boolean :desc "Skip backing up existing configs"}
    :no-layout     {:coerce :boolean :desc "Skip panel layout"}
    :help          {:alias :h :coerce :boolean :desc "Show help"}}})

(defn -main [& args]
  (let [args-vec (vec args)
        cmd      (first args-vec)
        rest-args (subvec args-vec (if (empty? args-vec) 0 1))
        opts     (cli/parse-opts rest-args cli-spec)]
    (if (or (:help opts) (empty? args-vec) (#{"-h" "--help" "help"} cmd))
      (print-help)
      (case cmd
        "install"       (cmd-install opts)
        "backup"        (cmd-backup opts)
        "harvest"       (log-info "Harvest command will be fully integrated in subsequent phases.")
        "verify"        (log-info "Verify command will be fully integrated in subsequent phases.")
        "dump-widgets"  (log-info "Dump widgets command will be integrated in subsequent phases.")
        "open-zen-mods" (log-info "Open Zen mods command will be integrated in subsequent phases.")
        (do
          (log-error "Unknown command:" cmd)
          (print-help)
          (System/exit 1))))))
