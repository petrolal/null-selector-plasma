(ns mono-rice.cmd.verify
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.deps :as rdeps]
            [mono-rice.fs :as rfs]
            [mono-rice.kde :as rkde]
            [mono-rice.layout.sanitizer :as san]
            [mono-rice.proc :refer [log-info log-step log-success log-warn]]))

(defn verify-symlinks [root home]
  (let [expected-links [[(fs/path home ".config" "kdeglobals") (fs/path root "plasma" ".config" "kdeglobals")]
                        [(fs/path home ".config" "kwinrc") (fs/path root "plasma" ".config" "kwinrc")]
                        [(fs/path home ".config" "kglobalshortcutsrc") (fs/path root "plasma" ".config" "kglobalshortcutsrc")]
                        [(fs/path home ".config" "fastfetch" "config.jsonc") (fs/path root "fastfetch" ".config" "fastfetch" "config.jsonc")]
                        [(fs/path home ".config" "starship.toml") (fs/path root "starship" ".config" "starship.toml")]
                        [(fs/path home ".config" "cava" "config") (fs/path root "cava" ".config" "cava" "config")]
                        [(fs/path home ".config" "fish" "config.fish") (fs/path root "fish" ".config" "fish" "config.fish")]
                        [(fs/path home ".config" "fish" "conf.d" "aliases.fish") (fs/path root "fish" ".config" "fish" "conf.d" "aliases.fish")]]]
    (println)
    (println "Symlink Integrity Verification:")
    (doseq [[link target] expected-links]
      (if (fs/exists? target)
        (if (fs/sym-link? link)
          (let [real (try (str (fs/real-path link)) (catch Exception _ "broken"))]
            (if (= real (str (fs/real-path target)))
              (println (format "  [OK ] %s -> %s" (fs/file-name link) target))
              (println (format "  [WARN] %s points to %s (expected %s)" (fs/file-name link) real target))))
          (println (format "  [MISSING] %s is not symlinked" link)))
        (println (format "  [SKIP] Repo target missing: %s" target))))))

(defn verify! [manifest opts]
  (log-step "Verifying Rice Health & Configuration State")
  (let [home (rfs/home-dir)
        root (rfs/repo-root)]
    ;; 1. System check
    (rkde/check-system! opts)

    ;; 2. Dependencies check
    (let [deps     (:dependencies manifest)
          miss-pac (rdeps/missing-pacman-packages (:pacman deps))
          miss-aur (rdeps/missing-aur-packages (:aur deps))]
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
    (verify-symlinks root home)

    (log-success "Verification diagnostic completed.")))
