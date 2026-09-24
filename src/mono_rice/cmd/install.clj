(ns mono-rice.cmd.install
  (:require [babashka.fs :as fs]
            [mono-rice.deps :as rdeps]
            [mono-rice.fs :as rfs]
            [mono-rice.kde :as rkde]
            [mono-rice.layout.sanitizer :as san]
            [mono-rice.proc :refer [command-exists? log-info log-step log-success log-warn sh!]]
            [mono-rice.zen.mods :as zmods]
            [mono-rice.zen.policies :as zpol]
            [mono-rice.zen.profile :as zprof]))

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
