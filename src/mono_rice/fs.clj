(ns mono-rice.fs
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh! ask-confirm?]]))

(defn home-dir []
  (fs/expand-home "~"))

(defn expand-home [path]
  (str (fs/expand-home (str path))))

(defn repo-root []
  (fs/path (or (System/getenv "MONO_RICE_ROOT")
               (System/getProperty "user.dir"))))

(defn read-manifest []
  (let [root (repo-root)
        manifest-path (fs/path root "rice.edn")]
    (if (fs/exists? manifest-path)
      (edn/read-string (slurp (str manifest-path)))
      {})))

(defn timestamp []
  (let [fmt (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd_HHmmss")
        now (java.time.LocalDateTime/now)]
    (.format now fmt)))

;; -----------------------------------------------------------------------------
;; Symlink & Directory Helpers
;; -----------------------------------------------------------------------------

(defn ensure-dir! [path & [{:keys [dry-run]}]]
  (if dry-run
    (log-info "[DRY-RUN] Would create directory:" (str path))
    (when-not (fs/exists? path)
      (fs/create-dirs path))))

(defn symlink!
  "Creates or updates a symlink from target to link-path.
   Removes existing symlink or file if present at link-path."
  [target link-path & [{:keys [dry-run]}]]
  (let [target-p (fs/path target)
        link-p   (fs/path link-path)]
    (if dry-run
      (log-info "[DRY-RUN] Would link:" (str link-p) "->" (str target-p))
      (do
        (ensure-dir! (fs/parent link-p))
        (when (or (fs/sym-link? link-p) (fs/exists? link-p))
          (if (fs/directory? link-p)
            (fs/delete-tree link-p)
            (fs/delete link-p)))
        (fs/create-sym-link link-p target-p)
        (log-success "Linked:" (str (fs/file-name link-p)) "->" (str target-p))))))

(defn link-dir-children!
  "Symlinks all direct children from src-dir into dest-dir."
  [src-dir dest-dir & [{:keys [dry-run filter-fn]}]]
  (let [src-p  (fs/path src-dir)
        dest-p (fs/path dest-dir)]
    (when (fs/exists? src-p)
      (ensure-dir! dest-p {:dry-run dry-run})
      (let [children (fs/list-dir src-p)
            filtered (if filter-fn (filter filter-fn children) children)]
        (doseq [child filtered]
          (let [child-name (str (fs/file-name child))
                dest-child (fs/path dest-p child-name)]
            (if dry-run
              (log-info "[DRY-RUN] Would link child:" (str dest-child) "->" (str child))
              (do
                (when (or (fs/sym-link? dest-child) (fs/exists? dest-child))
                  (if (fs/directory? dest-child)
                    (fs/delete-tree dest-child)
                    (fs/delete dest-child)))
                (fs/create-sym-link dest-child child)
                (log-success "Linked:" child-name "->" (str child))))))))))

;; -----------------------------------------------------------------------------
;; Backup Engine
;; -----------------------------------------------------------------------------

(defn backup-configs!
  "Creates a timestamped backup directory in $HOME and copies all non-symlink target configs."
  [targets & [{:keys [dry-run dest-dir]}]]
  (let [home        (home-dir)
        backup-name (str ".config_backup_mono_" (timestamp))
        backup-dir  (or dest-dir (fs/path home backup-name))]
    (log-step (str "Backing Up Existing Configurations to: " (str backup-dir)))
    (if dry-run
      (do
        (log-info "[DRY-RUN] Would backup" (count targets) "targets to" (str backup-dir))
        backup-dir)
      (do
        (ensure-dir! backup-dir)
        (doseq [t targets]
          (let [target-path (if (fs/relative? t) (fs/path home t) (fs/path t))]
            (when (and (fs/exists? target-path) (not (fs/sym-link? target-path)))
              (let [rel-path (fs/relativize home target-path)
                    dest     (fs/path backup-dir rel-path)]
                (ensure-dir! (fs/parent dest))
                (if (fs/directory? target-path)
                  (fs/copy-tree target-path dest {:replace-existing true})
                  (fs/copy target-path dest {:replace-existing true}))
                (log-info "Backed up:" (str rel-path))))))
        (log-success "Backup complete.")
        backup-dir))))

(defn list-backups
  "Discovers and returns all available configuration backup snapshots in $HOME sorted newest first."
  []
  (let [home (home-dir)]
    (if (fs/exists? home)
      (->> (fs/list-dir home)
           (filter #(and (fs/directory? %)
                         (str/starts-with? (str (fs/file-name %)) ".config_backup_mono_")))
           (sort-by str)
           reverse
           vec)
      [])))

(defn restore-backup!
  "Restores all configuration files from the specified backup snapshot back to $HOME."
  [backup-path & [{:keys [dry-run]}]]
  (let [home (home-dir)
        backup-p (fs/path backup-path)]
    (if-not (fs/exists? backup-p)
      (do
        (log-warn "Backup snapshot not found:" (str backup-p))
        false)
      (do
        (log-step (str "Restoring Snapshot -> " (str backup-p)))
        (let [files (fs/glob backup-p "**")]
          (doseq [f files]
            (when (not (fs/directory? f))
              (let [rel-path (fs/relativize backup-p f)
                    dest     (fs/path home rel-path)]
                (if dry-run
                  (log-info "[DRY-RUN] Would restore:" (str rel-path) "->" (str dest))
                  (do
                    (ensure-dir! (fs/parent dest))
                    (when (fs/sym-link? dest)
                      (fs/delete dest))
                    (fs/copy f dest {:replace-existing true})
                    (log-info "Restored:" (str rel-path)))))))
          (log-success "Snapshot restoration complete.")
          true)))))

;; -----------------------------------------------------------------------------
;; Broken Symlink Cleaner
;; -----------------------------------------------------------------------------

(defn clean-broken-symlinks!
  "Cleans broken/dangling symlinks in ~/.config and ~/.local/share up to depth 3."
  [& [{:keys [dry-run]}]]
  (log-step "Cleaning Legacy / Broken Symlinks")
  (let [home       (home-dir)
        scan-roots [(fs/path home ".config")
                    (fs/path home ".local" "share")
                    (fs/path home)]
        ignored?   (fn [p]
                     (let [s (str p)]
                       (or (str/includes? s "SingletonLock")
                           (str/includes? s "SingletonCookie")
                           (str/includes? s ".steampath"))))]
    (if dry-run
      (log-info "[DRY-RUN] Would check and remove broken symlinks in ~/.config and ~/.local/share")
      (let [broken-links (atom [])]
        (doseq [root scan-roots]
          (when (fs/exists? root)
            (doseq [p (fs/glob root "**" {:max-depth 3})]
              (when (and (fs/sym-link? p)
                         (not (fs/exists? p))
                         (not (ignored? p)))
                (swap! broken-links conj p)
                (log-info "Removing dangling symlink:" (str p))
                (fs/delete p)))))
        (log-success "Broken symlinks cleaned." (str "(" (count @broken-links) " removed)"))))))

;; -----------------------------------------------------------------------------
;; Deploy Rice Components, Plasmoids, Presets, Wallpapers & Modular Dotfiles
;; -----------------------------------------------------------------------------

(defn deploy-components! [root & [{:keys [dry-run]}]]
  (log-step "Deploying Rice Components, Plasmoids & Themes")
  (let [home (home-dir)]
    ;; 1. Bundled Plasmoids
    (link-dir-children! (fs/path root "plasma" ".local" "share" "plasma" "plasmoids")
                        (fs/path home ".local" "share" "plasma" "plasmoids")
                        {:dry-run dry-run})

    ;; 2. Colorschemes, Desktop themes, Aurorae themes
    (link-dir-children! (fs/path root "plasma" ".local" "share" "color-schemes")
                        (fs/path home ".local" "share" "color-schemes")
                        {:dry-run dry-run})
    (link-dir-children! (fs/path root "plasma" ".local" "share" "plasma" "desktoptheme")
                        (fs/path home ".local" "share" "plasma" "desktoptheme")
                        {:dry-run dry-run})
    (link-dir-children! (fs/path root "plasma" ".local" "share" "aurorae" "themes")
                        (fs/path home ".local" "share" "aurorae" "themes")
                        {:dry-run dry-run})

    ;; 3. Panel Colorizer Presets
    (link-dir-children! (fs/path root "plasma" ".config" "panel-colorizer" "presets")
                        (fs/path home ".config" "panel-colorizer" "presets")
                        {:dry-run dry-run})

    ;; 4. Wallpapers (.png, .mp4)
    (link-dir-children! (fs/path root "assets" "wallpapers")
                        (fs/path home ".local" "share" "wallpapers")
                        {:dry-run dry-run
                         :filter-fn (fn [p] (let [s (str p)]
                                              (or (str/ends-with? s ".png")
                                                  (str/ends-with? s ".mp4"))))})

    ;; 5. YAMIS Icon Fallback
    (let [yamis-sys (fs/path "/usr/share/icons/yet-another-monochrome-icon-set")]
      (when (fs/exists? yamis-sys)
        (symlink! yamis-sys (fs/path home ".local" "share" "icons" "YAMIS") {:dry-run dry-run})
        (symlink! yamis-sys (fs/path home ".local" "share" "icons" "yet-another-monochrome-icon-set") {:dry-run dry-run})))

    (log-success "Components, plasmoids, presets and wallpapers deployed.")))

(defn apply-dotfile-symlinks! [root & [{:keys [dry-run]}]]
  (log-step "Deploying Modular Configuration Symlinks")
  (let [home (home-dir)]
    ;; Core Plasma Configs
    (symlink! (fs/path root "plasma" ".config" "kdeglobals")
              (fs/path home ".config" "kdeglobals") {:dry-run dry-run})
    (symlink! (fs/path root "plasma" ".config" "kglobalshortcutsrc")
              (fs/path home ".config" "kglobalshortcutsrc") {:dry-run dry-run})
    (symlink! (fs/path root "plasma" ".config" "kwinrc")
              (fs/path home ".config" "kwinrc") {:dry-run dry-run})
    (when (fs/exists? (fs/path root "plasma" ".config" "kscreenlockerrc"))
      (symlink! (fs/path root "plasma" ".config" "kscreenlockerrc")
                (fs/path home ".config" "kscreenlockerrc") {:dry-run dry-run}))
    (when (fs/exists? (fs/path root "plasma" ".config" "klassy" "klassyrc"))
      (symlink! (fs/path root "plasma" ".config" "klassy" "klassyrc")
                (fs/path home ".config" "klassy" "klassyrc") {:dry-run dry-run}))
    (when (fs/exists? (fs/path root "plasma" ".config" "plasma-workspace" "env" "rice-env.sh"))
      (symlink! (fs/path root "plasma" ".config" "plasma-workspace" "env" "rice-env.sh")
                (fs/path home ".config" "plasma-workspace" "env" "rice-env.sh") {:dry-run dry-run}))

    ;; Fastfetch
    (when (fs/exists? (fs/path root "fastfetch" ".config" "fastfetch" "config.jsonc"))
      (symlink! (fs/path root "fastfetch" ".config" "fastfetch" "config.jsonc")
                (fs/path home ".config" "fastfetch" "config.jsonc") {:dry-run dry-run}))

    ;; Starship
    (when (fs/exists? (fs/path root "starship" ".config" "starship.toml"))
      (symlink! (fs/path root "starship" ".config" "starship.toml")
                (fs/path home ".config" "starship.toml") {:dry-run dry-run}))

    ;; CAVA
    (when (fs/exists? (fs/path root "cava" ".config" "cava" "config"))
      (symlink! (fs/path root "cava" ".config" "cava" "config")
                (fs/path home ".config" "cava" "config") {:dry-run dry-run}))

    ;; Fish
    (when (fs/exists? (fs/path root "fish" ".config" "fish" "config.fish"))
      (symlink! (fs/path root "fish" ".config" "fish" "config.fish")
                (fs/path home ".config" "fish" "config.fish") {:dry-run dry-run}))
    (when (fs/exists? (fs/path root "fish" ".config" "fish" "conf.d" "aliases.fish"))
      (symlink! (fs/path root "fish" ".config" "fish" "conf.d" "aliases.fish")
                (fs/path home ".config" "fish" "conf.d" "aliases.fish") {:dry-run dry-run}))

    ;; Kvantum
    (link-dir-children! (fs/path root "kvantum" ".config" "Kvantum")
                        (fs/path home ".config" "Kvantum")
                        {:dry-run dry-run})

    (log-success "All configuration symlinks applied successfully.")))

;; -----------------------------------------------------------------------------
;; SDDM and Plymouth Installers
;; -----------------------------------------------------------------------------

(defn install-sddm-theme! [root & [{:keys [dry-run auto-yes]}]]
  (log-step "Installing Monochrome SDDM Login Theme")
  (let [src-dir (fs/path root "assets" "sddm-theme")]
    (if (fs/exists? src-dir)
      (if dry-run
        (log-info "[DRY-RUN] Would install SDDM theme to /usr/share/sddm/themes/monochrome")
        (if (or auto-yes
                (zero? (:exit (sh! ["sudo" "-n" "true"] {:throw? false})))
                (ask-confirm? "Install SDDM Monochrome Theme (requires sudo)?" {:auto-yes auto-yes}))
          (do
            (sh! ["mkdir" "-p" "/usr/share/sddm/themes/monochrome" "/etc/sddm.conf.d"] {:sudo true})
            (sh! ["cp" "-rf" (str (fs/path src-dir "*")) "/usr/share/sddm/themes/monochrome/"] {:sudo true :throw? false})
            (sh! ["kwriteconfig6" "--file" "/etc/sddm.conf" "--group" "Theme" "--key" "Current" "monochrome"] {:sudo true :throw? false})
            (sh! ["kwriteconfig6" "--file" "/etc/sddm.conf.d/theme.conf" "--group" "Theme" "--key" "Current" "monochrome"] {:sudo true :throw? false})
            (log-success "SDDM Monochrome theme installed and set as default."))
          (log-warn "Skipped SDDM installation (sudo access declined).")))
      (log-warn "SDDM theme directory not found at assets/sddm-theme"))))

(defn install-plymouth-theme! [root & [{:keys [dry-run auto-yes]}]]
  (log-step "Installing dotLock Plymouth Boot Splash Theme")
  (let [src-dir (fs/path root "assets" "plymouth-theme" "dotLock")]
    (if (fs/exists? src-dir)
      (if dry-run
        (log-info "[DRY-RUN] Would install dotLock Plymouth theme to /usr/share/plymouth/themes/dotLock")
        (if (or auto-yes
                (zero? (:exit (sh! ["sudo" "-n" "true"] {:throw? false})))
                (ask-confirm? "Install dotLock Plymouth Boot Splash (requires sudo)?" {:auto-yes auto-yes}))
          (do
            (sh! ["mkdir" "-p" "/usr/share/plymouth/themes/dotLock" "/etc/plymouth"] {:sudo true})
            (sh! ["cp" "-rf" (str (fs/path src-dir "*")) "/usr/share/plymouth/themes/dotLock/"] {:sudo true :throw? false})
            (when (command-exists? "plymouth-set-default-theme")
              (sh! ["plymouth-set-default-theme" "dotLock"] {:sudo true :throw? false}))
            (sh! ["kwriteconfig6" "--file" "/etc/plymouth/plymouthd.conf" "--group" "Daemon" "--key" "Theme" "dotLock"] {:sudo true :throw? false})
            (log-success "dotLock Plymouth theme installed and configured."))
          (log-warn "Skipped Plymouth installation (sudo access declined).")))
      (log-warn "Plymouth theme directory not found at assets/plymouth-theme/dotLock"))))
