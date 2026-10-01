(ns mono-rice.deps
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh!]]))

;; -----------------------------------------------------------------------------
;; AUR Helper & Package Detection
;; -----------------------------------------------------------------------------

(defn detect-aur-helper []
  (cond
    (command-exists? "yay") "yay"
    (command-exists? "paru") "paru"
    :else nil))

(defn installed-pacman? [pkg]
  (let [res (sh! ["pacman" "-Qi" (str pkg)] {:throw? false})]
    (zero? (:exit res))))

(defn installed-aur? [pkg]
  (let [base-name (str/replace pkg #"(-git|-bin)$" "")
        candidates [pkg
                    base-name
                    (str base-name "-git")
                    (str base-name "-bin")]
        check (fn [p]
                (let [res (sh! ["pacman" "-Q" p] {:throw? false})]
                  (zero? (:exit res))))]
    (boolean (some check candidates))))

(defn missing-pacman-packages [packages]
  (filterv (complement installed-pacman?) packages))

(defn missing-aur-packages [packages]
  (filterv (complement installed-aur?) packages))

;; -----------------------------------------------------------------------------
;; NixOS / Nix Package Detection
;; -----------------------------------------------------------------------------

(def nix-binary-map
  {"babashka" "bb"
   "git" "git"
   "cmake" "cmake"
   "stow" "stow"
   "kvantum" "kvantummanager"
   "kdePackages.kvantum" "kvantummanager"
   "kdePackages.konsole" "konsole"
   "cava" "cava"
   "ffmpeg" "ffmpeg"
   "starship" "starship"
   "fastfetch" "fastfetch"
   "fish" "fish"
   "kdePackages.dolphin" "dolphin"
   "discord" "discord"
   "emacs" "emacs"
   "kdePackages.kdenlive" "kdenlive"
   "kdePackages.gwenview" "gwenview"
   "haruna" "haruna"
   "rclone" "rclone"})

(defn missing-nix-packages [packages]
  (filterv (fn [pkg]
             (if-let [bin (get nix-binary-map pkg)]
               (not (command-exists? bin))
               false))
           packages))

(defn install-nix-deps! [packages & [{:keys [dry-run]}]]
  (log-step "Resolving Nix / NixOS Dependencies")
  (let [missing (missing-nix-packages packages)]
    (if (seq missing)
      (do
        (log-warn "Missing Nix tools in current environment:" (str/join ", " missing))
        (log-info "To provide dependencies declaratively, include the sanguine-node-rice NixOS/Home Manager module,")
        (log-info "or run inside the provided development shell: nix develop (or nix-shell)")
        (if dry-run
          (log-info "[DRY-RUN] Missing Nix tools detected:" (str/join " " missing))
          (when (command-exists? "nix-env")
            (log-info "You can also install tools imperatively to your user profile via: nix-env -iA" (str/join " " missing)))))
      (log-success "All core CLI and desktop tools are available in current Nix environment."))))

;; -----------------------------------------------------------------------------
;; Package Installation
;; -----------------------------------------------------------------------------

(defn install-pacman-deps! [packages & [{:keys [dry-run]}]]
  (log-step "Resolving Core Official Pacman Dependencies")
  (let [missing (missing-pacman-packages packages)]
    (if (seq missing)
      (do
        (log-info "Installing missing pacman packages:" (str/join " " missing))
        (if dry-run
          (log-info "[DRY-RUN] Would run: sudo pacman -S --needed --noconfirm" (str/join " " missing))
          (sh! (into ["pacman" "-S" "--needed" "--noconfirm"] missing) {:sudo true}))
        (log-success "Official pacman dependencies installed."))
      (log-success "All official pacman packages already installed."))))

(defn install-aur-deps! [packages & [{:keys [dry-run]}]]
  (log-step "Resolving AUR Enhancements & Plasma 6 Extensions")
  (let [aur-helper (detect-aur-helper)
        ;; Check session type for better-blur-dx vs better-blur-dx-x11
        session-type (System/getenv "XDG_SESSION_TYPE")
        blur-pkg     (if (= session-type "x11")
                       "kwin-effects-better-blur-dx-x11"
                       "kwin-effects-better-blur-dx")
        ;; Replace generic blur reference with session-specific package
        packages     (mapv (fn [p] (if (str/starts-with? p "kwin-effects-better-blur-dx") blur-pkg p)) packages)
        missing      (missing-aur-packages packages)]
    (if (seq missing)
      (do
        (log-info "Missing AUR packages:" (str/join " " missing))
        (if dry-run
          (log-info "[DRY-RUN] Would install via AUR helper (" (or aur-helper "none") "):" (str/join " " missing))
          (let [helper (or aur-helper
                           (do
                             (log-info "No AUR helper detected. Bootstrapping 'yay' from pacman/AUR...")
                             (sh! ["pacman" "-S" "--needed" "--noconfirm" "yay"] {:sudo true :throw? false})
                             (if (command-exists? "yay") "yay" nil)))]
            (if helper
              (do
                ;; Remove conflicting kwin-effects-forceblur-git if installing better-blur-dx
                (when (and (some #(str/includes? % "better-blur-dx") missing)
                           (installed-pacman? "kwin-effects-forceblur-git"))
                  (log-info "Removing superseded kwin-effects-forceblur-git (conflicts with better-blur-dx)...")
                  (sh! ["pacman" "-R" "--noconfirm" "kwin-effects-forceblur-git"] {:sudo true :throw? false}))
                (sh! (into [helper "-S" "--needed" "--noconfirm"] missing) {:throw? false})
                (log-success "AUR packages installed."))
              (log-warn "Could not auto-install AUR helper. Please install yay or paru manually, then re-run.")))))
      (log-success "All AUR enhancements & Plasma extensions are installed."))))

(defn install-git-deps! [git-deps & [{:keys [dry-run]}]]
  (log-step "Resolving Git Theme Repositories")
  (doseq [{:keys [name url target]} git-deps]
    (let [target-path (fs/path (fs/expand-home "~") target)]
      (if (fs/exists? target-path)
        (log-success "Git component already present:" name (str "(" target-path ")"))
        (do
          (log-info "Cloning git component:" name "from" url)
          (if dry-run
            (log-info "[DRY-RUN] Would clone" url "to" (str target-path))
            (let [tmp-dir (fs/create-temp-dir {:prefix "mono_rice_git_"})]
              (try
                (sh! ["git" "clone" "--depth" "1" url (str tmp-dir)])
                (fs/create-dirs (fs/parent target-path))
                (fs/copy-tree (fs/path tmp-dir "a2n.kuro") target-path {:replace-existing true})
                (log-success "Installed git component:" name)
                (finally
                  (fs/delete-tree tmp-dir))))))))))

(defn install-all-deps! [manifest & [opts]]
  (let [deps   (:dependencies manifest)
        nixos? (or (fs/exists? "/etc/NIXOS")
                   (try (and (fs/exists? "/etc/os-release")
                             (str/includes? (str/lower-case (slurp "/etc/os-release")) "id=nixos"))
                        (catch Exception _ false)))]
    (if nixos?
      (do
        (install-nix-deps! (or (:nix deps) []) opts)
        (install-git-deps! (:git deps) opts))
      (do
        (install-pacman-deps! (:pacman deps) opts)
        (install-aur-deps! (:aur deps) opts)
        (install-git-deps! (:git deps) opts)))))
