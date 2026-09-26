(ns mono-rice.cmd.install
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.cmd.completion :as rcomp]
            [mono-rice.deps :as rdeps]
            [mono-rice.fs :as rfs]
            [mono-rice.kde :as rkde]
            [mono-rice.layout.sanitizer :as san]
            [mono-rice.proc :refer [ask-confirm? command-exists? log-info log-step log-success log-warn sh! sudo-validate!]]
            [mono-rice.zen.mods :as zmods]
            [mono-rice.zen.policies :as zpol]
            [mono-rice.zen.profile :as zprof]))

;; -----------------------------------------------------------------------------
;; Default Shell Configuration
;; -----------------------------------------------------------------------------

(defn- get-user-login-shell [user]
  (try
    (let [passwd (slurp "/etc/passwd")
          user-line (first (filter #(str/starts-with? % (str user ":")) (str/split-lines passwd)))]
      (when user-line
        (str/trim (last (str/split user-line #":")))))
    (catch Exception _ nil)))

(defn set-default-shell-fish! [& [{:keys [dry-run]}]]
  (log-step "Configuring Default Login Shell -> Fish")
  (if (command-exists? "fish")
    (let [user        (or (System/getenv "USER") (System/getProperty "user.name"))
          fish-bin    (str/trim (or (:out (sh! ["which" "fish"] {:throw? false})) "/usr/bin/fish"))
          login-shell (or (get-user-login-shell user) (System/getenv "SHELL"))]
      (if (and (not (str/blank? login-shell)) (str/ends-with? login-shell "fish"))
        (log-success "Fish is already your default login shell (" login-shell ").")
        (if dry-run
          (log-info "[DRY-RUN] Would set default login shell to:" fish-bin "for user:" user)
          (do
            (log-info "Requesting sudo authorization to set default shell for" user "to" fish-bin "...")
            (let [sudo-res (sh! ["chsh" "-s" fish-bin user] {:sudo true :inherit true :throw? false})]
              (if (zero? (:exit sudo-res))
                (log-success "Default login shell successfully set to Fish (" fish-bin ") for user" user ".")
                (let [user-res (sh! ["chsh" "-s" fish-bin] {:inherit true :throw? false})]
                  (if (zero? (:exit user-res))
                    (log-success "Default shell set to Fish (" fish-bin ").")
                    (log-warn "Could not change default shell automatically. Please run: chsh -s" fish-bin)))))))))
    (log-warn "Fish executable not detected in PATH. Skipping default shell configuration.")))

;; -----------------------------------------------------------------------------
;; Plasma Layout Application
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
;; Main Install Routine
;; -----------------------------------------------------------------------------

(defn install! [manifest opts]
  (log-step "Starting Deployment of null-sector-plasma")
  (let [root (rfs/repo-root)]
    (rkde/check-system! opts)
    (when-not (or (:dry-run opts) (:symlinks-only opts))
      (sudo-validate!))

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

        ;; 7. Login Manager & Plymouth
        (when (or (:sddm opts) (not (:symlinks-only opts)))
          (rfs/install-login-manager! root opts))
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

        ;; 10. Install Fish completions
        (rcomp/install-completion :fish opts)

        ;; 11. Configure Fish as default user shell
        (when-not (:no-shell-change opts)
          (set-default-shell-fish! opts))

        (log-step "Installation Completed Successfully!")
        (println)
        (log-info "Default login shell is configured to Fish (/usr/bin/fish).")
        (log-info "A system reboot or session logout is recommended to activate all environment changes.")
        (when-not (or (:dry-run opts) (:no-reboot opts))
          (if (:reboot opts)
            (do
              (log-info "Reboot requested via flag. Rebooting system now...")
              (sh! ["systemctl" "reboot"] {:throw? false}))
            (when (ask-confirm? "Would you like to reboot the system now to apply all session changes?" (assoc opts :default false))
              (log-info "Rebooting system...")
              (sh! ["systemctl" "reboot"] {:throw? false}))))))))
