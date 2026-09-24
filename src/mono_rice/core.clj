(ns mono-rice.core
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [mono-rice.deps :as rdeps]
            [mono-rice.fs :as rfs]
            [mono-rice.kde :as rkde]
            [mono-rice.layout.inspector :as insp]
            [mono-rice.layout.sanitizer :as san]
            [mono-rice.proc :refer [ask-confirm? command-exists? log-error log-info log-step log-success log-warn sh!]]
            [mono-rice.zen.mods :as zmods]
            [mono-rice.zen.policies :as zpol]
            [mono-rice.zen.profile :as zprof]))

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
  (println "  harvest       Scrape live $HOME configs back into repository with template sanitization")
  (println "  backup        Create timestamped backup snapshot of current configurations")
  (println "  verify        Verify system health, package dependencies, and layout integrity")
  (println "  dump-widgets  Pretty-print active containment and plasmoid tree")
  (println "  open-zen-mods Open Zen Browser mod install pages for one-click setup")
  (println)
  (println "Options:")
  (println "  -n, --dry-run        Simulate operations without making changes")
  (println "  -y, --yes            Non-interactive mode (answer yes to all prompts)")
  (println "  -s, --symlinks-only  Deploy symlinks and configurations only (skip package manager)")
  (println "  -d, --deps-only      Install package dependencies only")
  (println "      --no-backup      Skip backing up existing configuration files")
  (println "      --no-layout      Skip applying desktop and panel layout")
  (println "      --sddm           Install SDDM Monochrome theme")
  (println "      --plymouth       Install dotLock Plymouth boot splash theme")
  (println "      --open-zen-mods  Open Zen Mod pages after installation")
  (println "      --no-restart     Skip restarting plasmashell after layout deploy")
  (println "  -h, --help           Show this help message"))

;; -----------------------------------------------------------------------------
;; Layout Application
;; -----------------------------------------------------------------------------

(defn apply-panel-layout! [root & [{:keys [dry-run no-restart]}]]
  (log-step "Deploying Plasma Layout & Containment Configurations")
  (if dry-run
    (log-info "[DRY-RUN] Would stop plasmashell, deploy sanitized layout configs, and restart plasmashell.")
    (let [home            (rfs/home-dir)
          appletsrc-src   (fs/path root "plasma" ".config" "plasma-org.kde.plasma.desktop-appletsrc")
          plasmashell-src (fs/path root "plasma" ".config" "plasmashellrc")
          appletsrc-dest  (fs/path home ".config" "plasma-org.kde.plasma.desktop-appletsrc")
          plasmashell-dst (fs/path home ".config" "plasmashellrc")]

      (log-info "Stopping plasmashell...")
      (sh! ["systemctl" "--user" "stop" "plasma-plasmashell"] {:throw? false})
      (when (command-exists? "kquitapp6")
        (sh! ["kquitapp6" "plasmashell"] {:throw? false}))
      (Thread/sleep 1000)
      (when (zero? (:exit (sh! ["pgrep" "-x" "plasmashell"] {:throw? false})))
        (sh! ["killall" "-9" "plasmashell"] {:throw? false})
        (Thread/sleep 500))

      (log-info "Deploying layout configuration...")
      (when (fs/sym-link? appletsrc-dest) (fs/delete appletsrc-dest))
      (when (fs/sym-link? plasmashell-dst) (fs/delete plasmashell-dst))

      (if (fs/exists? appletsrc-src)
        (let [raw-template (slurp (str appletsrc-src))
              hydrated     (san/hydrate-install raw-template {:home home})]
          (spit (str appletsrc-dest) hydrated)
          (log-success "Hydrated and deployed:" (str appletsrc-dest)))
        (log-warn "Tracked appletsrc not found at:" (str appletsrc-src)))

      (if (fs/exists? plasmashell-src)
        (do
          (fs/copy plasmashell-src plasmashell-dst {:replace-existing true})
          (log-success "Deployed:" (str plasmashell-dst)))
        (log-warn "Tracked plasmashellrc not found at:" (str plasmashell-src)))

      (if no-restart
        (log-info "Skipping plasmashell restart (--no-restart).")
        (do
          (log-info "Rebuilding KDE service cache...")
          (when (command-exists? "kbuildsycoca6")
            (sh! ["kbuildsycoca6" "--noincremental"] {:throw? false}))
          (log-info "Restarting plasmashell...")
          (let [res (sh! ["systemctl" "--user" "start" "plasma-plasmashell"] {:throw? false})]
            (when-not (zero? (:exit res))
              (when (command-exists? "kstart")
                (sh! ["kstart" "plasmashell"] {:throw? false}))))
          (Thread/sleep 1000)
          (if (zero? (:exit (sh! ["pgrep" "-x" "plasmashell"] {:throw? false})))
            (log-success "plasmashell is running.")
            (log-warn "plasmashell did not restart automatically. Start via: systemctl --user start plasma-plasmashell"))))

      (log-success "Plasma layout deployed and active."))))

;; -----------------------------------------------------------------------------
;; Command Handlers
;; -----------------------------------------------------------------------------

(defn cmd-backup [opts]
  (let [manifest (load-manifest!)
        targets  (:backup-targets manifest)]
    (rfs/backup-configs! targets opts)))

(defn cmd-dump-widgets [_opts]
  (insp/dump-widgets))

(defn cmd-open-zen-mods [_opts]
  (let [manifest (load-manifest!)]
    (zmods/open-zen-mods! manifest)))

(defn cmd-verify [opts]
  (log-step "Verifying Rice Health & Configuration State")
  (let [manifest (load-manifest!)
        home     (rfs/home-dir)
        root     (rfs/repo-root)]
    ;; 1. System check
    (rkde/check-system! opts)

    ;; 2. Dependencies check
    (let [deps       (:dependencies manifest)
          miss-pac   (rdeps/missing-pacman-packages (:pacman deps))
          miss-aur   (rdeps/missing-aur-packages (:aur deps))]
      (if (empty? miss-pac)
        (log-success "All official pacman dependencies installed.")
        (log-warn "Missing pacman packages:" (str/join ", " miss-pac)))
      (if (empty? miss-aur)
        (log-success "All AUR extensions and packages installed.")
        (log-warn "Missing AUR packages:" (str/join ", " miss-aur))))

    ;; 3. Appletsrc widget verification
    (let [live-appletsrc (fs/path home ".config" "plasma-org.kde.plasma.desktop-appletsrc")]
      (if (fs/exists? live-appletsrc)
        (let [content (slurp (str live-appletsrc))
              v-res   (san/verify-widgets content)]
          (println)
          (println "Widget Verification:")
          (doseq [{:keys [plugin count ok?]} (:results v-res)]
            (println (format "  [%s] %s%s"
                             (if ok? "OK " "MISSING")
                             plugin
                             (if (pos? count) (str " (x" count ")") ""))))
          (if (:valid? v-res)
            (log-success "All required rice widgets are present in live layout.")
            (log-warn (str (count (:missing v-res)) " required widget(s) missing from live layout."))))
        (log-warn "Live plasma-org.kde.plasma.desktop-appletsrc not found.")))

    ;; 4. Symlinks check
    (log-success "Verification diagnostic completed.")))

(defn cmd-harvest [opts]
  (log-step "Starting Configuration Harvest Pass")
  (let [manifest (load-manifest!)
        home     (rfs/home-dir)
        root     (rfs/repo-root)]
    (when-not (ask-confirm? (str "Scrape active configs from " home " into repo?") opts)
      (log-warn "Harvest cancelled.")
      (System/exit 0))

    (let [harvest-file (fn [src-rel dst-rel]
                         (let [src (fs/path home src-rel)
                               dst (fs/path root dst-rel)]
                           (if (fs/exists? src)
                             (if (and (fs/sym-link? src)
                                      (= (str (fs/real-path src)) (str (fs/real-path dst))))
                               (log-info "Already symlinked to repo (no-op):" (str src-rel))
                               (do
                                 (rfs/ensure-dir! (fs/parent dst) opts)
                                 (if (not (:dry-run opts))
                                   (fs/copy src dst {:replace-existing true}))
                                 (log-success "Harvested:" (str src-rel) "->" (str dst-rel))))
                             (log-warn "Source not found (skipping):" (str src-rel)))))
          harvest-dir  (fn [src-rel dst-rel]
                         (let [src (fs/path home src-rel)
                               dst (fs/path root dst-rel)]
                           (if (fs/exists? src)
                             (do
                               (rfs/ensure-dir! dst opts)
                               (if (not (:dry-run opts))
                                 (fs/copy-tree src dst {:replace-existing true}))
                               (log-success "Harvested directory:" (str src-rel) "->" (str dst-rel)))
                             (log-warn "Source directory not found (skipping):" (str src-rel)))))]

      ;; Harvest Plasma Configs
      (harvest-file ".config/kdeglobals" "plasma/.config/kdeglobals")
      (harvest-file ".config/kwinrc" "plasma/.config/kwinrc")
      (harvest-file ".config/plasmashellrc" "plasma/.config/plasmashellrc")
      (harvest-file ".config/plasma-org.kde.plasma.desktop-appletsrc" "plasma/.config/plasma-org.kde.plasma.desktop-appletsrc")
      (harvest-file ".config/kglobalshortcutsrc" "plasma/.config/kglobalshortcutsrc")
      (harvest-file ".config/klassy/klassyrc" "plasma/.config/klassy/klassyrc")

      ;; Harvest Theme & Engine Configs
      (harvest-file ".config/Kvantum/kvantum.kvconfig" "kvantum/.config/Kvantum/kvantum.kvconfig")
      (harvest-dir ".config/Kvantum/PetrolalDark" "kvantum/.config/Kvantum/PetrolalDark")

      ;; Harvest Shell & Terminal Configs
      (harvest-file ".config/cava/config" "cava/.config/cava/config")
      (harvest-file ".zshrc" "zsh/.zshrc")
      (harvest-file ".config/zsh/aliases.zsh" "zsh/.config/zsh/aliases.zsh")
      (harvest-file ".config/fastfetch/config.jsonc" "fastfetch/.config/fastfetch/config.jsonc")
      (harvest-file ".config/starship.toml" "starship/.config/starship.toml")

      ;; Sanitization pass on repo configs
      (when-not (:dry-run opts)
        (log-step "Sanitizing Harvested Configurations")
        (let [appletsrc-path (fs/path root "plasma" ".config" "plasma-org.kde.plasma.desktop-appletsrc")
              plasmashell-p  (fs/path root "plasma" ".config" "plasmashellrc")]
          (when (fs/exists? appletsrc-path)
            (let [raw-content   (slurp (str appletsrc-path))
                  sanitized     (san/sanitize-harvest raw-content {:home home})
                  live-panel-ids (san/panel-containment-ids raw-content)]
              (spit (str appletsrc-path) sanitized)
              (log-success "Sanitized template placeholders in desktop-appletsrc.")

              (when (fs/exists? plasmashell-p)
                (let [plasmashell-raw (slurp (str plasmashell-p))
                      {:keys [text pruned]} (san/prune-stale-panels plasmashell-raw live-panel-ids)]
                  (spit (str plasmashell-p) text)
                  (log-success "Pruned" pruned "stale panel block(s) from plasmashellrc.")))

              (let [v-res (san/verify-widgets raw-content)]
                (if (:valid? v-res)
                  (log-success "Harvested layout contains all required rice widgets.")
                  (log-warn "Harvested layout is missing required widgets:"
                            (str/join ", " (map :plugin (:missing v-res))))))))))

      ;; Summary of git diff
      (log-step "Harvest Complete")
      (sh! ["git" "status" "-s"] {:throw? false}))))

(defn cmd-install [opts]
  (log-step "Starting Deployment of null-sector-plasma")
  (let [manifest (load-manifest!)
        root     (rfs/repo-root)]
    (rkde/check-system! opts)

    (if (:deps-only opts)
      (do
        (rdeps/install-all-deps! manifest opts)
        (log-step "Dependencies Installed Successfully!"))
      (do
        ;; 1. Dependencies
        (when-not (:symlinks-only opts)
          (rdeps/install-all-deps! manifest opts))

        ;; 2. Backup
        (when-not (:no-backup opts)
          (rfs/backup-configs! (:backup-targets manifest) opts))

        ;; 3. Clean broken symlinks
        (rfs/clean-broken-symlinks! opts)

        ;; 4. Deploy Components & Presets
        (rfs/deploy-components! root opts)

        ;; 5. Apply Modular Config Symlinks
        (rfs/apply-dotfile-symlinks! root opts)

        ;; 6. Apply KDE Look and Feel Settings
        (rkde/apply-theme! manifest opts)

        ;; 7. SDDM & Plymouth
        (when (or (:sddm opts) (not (:symlinks-only opts)))
          (rfs/install-sddm-theme! root opts))
        (when (or (:plymouth opts) (not (:symlinks-only opts)))
          (rfs/install-plymouth-theme! root opts))

        ;; 8. Zen Browser Configurations
        (zprof/link-profile-styles! root opts)
        (when-not (:symlinks-only opts)
          (zpol/configure-extensions! manifest opts))
        (zprof/configure-kwin-window-rule! opts)
        (zmods/seed-mods-registry! manifest opts)
        (zmods/decolorize-workspace-theme! opts)

        ;; 9. Deploy Plasma Panel Layout
        (if (:no-layout opts)
          (log-info "Skipping panel layout deployment (--no-layout).")
          (apply-panel-layout! root opts))

        ;; 10. Open Zen Mods if requested
        (when (:open-zen-mods opts)
          (zmods/open-zen-mods! manifest))

        (log-step "Installation Completed Successfully!")))))

(def cli-spec
  {:spec
   {:dry-run       {:alias :n :coerce :boolean :desc "Simulate installation without modifying files"}
    :yes           {:alias :y :coerce :boolean :desc "Non-interactive mode"}
    :symlinks-only {:alias :s :coerce :boolean :desc "Deploy symlinks only"}
    :deps-only     {:alias :d :coerce :boolean :desc "Install dependencies only"}
    :no-backup     {:coerce :boolean :desc "Skip backing up existing configs"}
    :no-layout     {:coerce :boolean :desc "Skip panel layout"}
    :sddm          {:coerce :boolean :desc "Install SDDM theme"}
    :plymouth      {:coerce :boolean :desc "Install Plymouth theme"}
    :open-zen-mods {:coerce :boolean :desc "Open Zen Mod pages"}
    :no-restart    {:coerce :boolean :desc "Skip restarting plasmashell"}
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
        "harvest"       (cmd-harvest opts)
        "verify"        (cmd-verify opts)
        "dump-widgets"  (cmd-dump-widgets opts)
        "open-zen-mods" (cmd-open-zen-mods opts)
        (do
          (log-error "Unknown command:" cmd)
          (print-help)
          (System/exit 1))))))
