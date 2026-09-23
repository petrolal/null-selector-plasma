(ns mono-rice.fs
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.proc :refer [log-info log-success log-warn log-step sh!]]))

(defn home-dir []
  (fs/expand-home "~"))

(defn repo-root []
  (fs/path (or (System/getenv "MONO_RICE_ROOT")
               (System/getProperty "user.dir"))))

(defn timestamp []
  (let [fmt (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd_HHmmss")
        now (java.time.LocalDateTime/now)]
    (.format now fmt)))

;; -----------------------------------------------------------------------------
;; Symlink & File Utilities
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
          (fs/delete link-p))
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
