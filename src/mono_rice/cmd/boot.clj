(ns mono-rice.cmd.boot
  "Boot and display manager orchestrator for SDDM and Plymouth splash themes."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [mono-rice.fs :as fs]
            [mono-rice.proc :as proc]))

(defn- read-active-sddm-theme []
  (let [candidates ["/etc/sddm.conf.d/kde_settings.conf"
                    "/etc/sddm.conf.d/sddm.conf"
                    "/etc/sddm.conf"]]
    (or (some (fn [p]
                (let [f (io/file p)]
                  (when (.exists f)
                    (try
                      (let [content (slurp f)]
                        (second (re-find #"(?m)^\[Theme\][\s\S]*?^Current\s*=\s*([^\r\n]+)" content)))
                      (catch Exception _ nil)))))
              candidates)
        "breeze")))

(defn- read-active-plymouth-theme []
  (let [conf (io/file "/etc/plymouth/plymouthd.conf")]
    (if (.exists conf)
      (try
        (let [content (slurp conf)]
          (or (second (re-find #"(?m)^Theme\s*=\s*([^\r\n]+)" content))
              "unknown"))
        (catch Exception _ "unknown"))
      "unknown")))

(defn status
  "Print the current status of SDDM and Plymouth boot themes."
  [opts]
  (let [manifest (fs/read-manifest)
        boot-cfg (:boot manifest)
        sddm-active (read-active-sddm-theme)
        plymouth-active (read-active-plymouth-theme)]
    (println "Boot & Display Manager Status:")
    (println "==============================")
    (println (format "  * Active SDDM Theme     : %s" sddm-active))
    (println (format "  * Configured SDDM       : %s (Source: %s)"
                     (get-in boot-cfg [:sddm :theme-name] "null-sector-sddm")
                     (get-in boot-cfg [:sddm :source] "assets/sddm-theme")))
    (println (format "  * Active Plymouth Theme : %s" plymouth-active))
    (println (format "  * Configured Plymouth   : %s" (get-in boot-cfg [:plymouth :active-theme] :dot-lock)))
    (println "")
    (println "Themes in Repository:")
    (let [sddm-exists (.exists (io/file (get-in boot-cfg [:sddm :source] "assets/sddm-theme")))
          dotlock-exists (.exists (io/file "assets/plymouth-theme/dotLock"))
          dotlockg-exists (.exists (io/file "assets/plymouth-theme/dotLockG"))]
      (println (format "  - SDDM Theme (Null Sector) : %s" (if sddm-exists "Ready" "Missing")))
      (println (format "  - Plymouth dotLock         : %s" (if dotlock-exists "Ready" "Missing")))
      (println (format "  - Plymouth dotLockG        : %s" (if dotlockg-exists "Ready" "Missing"))))
    {:sddm-active sddm-active
     :plymouth-active plymouth-active}))

(defn list-themes
  "List available SDDM and Plymouth boot themes."
  [opts]
  (let [manifest (fs/read-manifest)
        boot-cfg (:boot manifest)]
    (println "Available Boot & Display Manager Themes:")
    (println "========================================")
    (println "SDDM Display Manager Themes:")
    (println (format "  * %-20s : %s"
                     (get-in boot-cfg [:sddm :theme-name] "null-sector-sddm")
                     "Cyberpunk NieR: Automata custom SDDM greeter"))
    (println "")
    (println "Plymouth Boot Splash Themes:")
    (doseq [[k v] (get-in boot-cfg [:plymouth :themes])]
      (println (format "  * %-20s : Plymouth graphical boot splash (%s)"
                       (name k) (:name v))))
    (println "")
    (println "Preview SDDM:     mono-rice boot preview-sddm")
    (println "Apply SDDM:       mono-rice boot apply-sddm")
    (println "Apply Plymouth:   mono-rice boot apply-plymouth <theme-name>")
    true))

(defn preview-sddm
  "Run the SDDM greeter in test mode for visual validation."
  [{:keys [dry-run theme-path] :as opts}]
  (let [manifest (fs/read-manifest)
        source-path (or theme-path
                        (get-in manifest [:boot :sddm :source] "assets/sddm-theme"))
        abs-source (fs/expand-home source-path)
        cmd (if (proc/command-exists? "sddm-greeter-qt6")
              ["sddm-greeter-qt6" "--test-mode" "--theme" abs-source]
              ["sddm-greeter" "--test-mode" "--theme" abs-source])]
    (println (format "==> Previewing SDDM Theme from -> %s" abs-source))
    (if dry-run
      (do
        (proc/log-info (format "[DRY-RUN] Would execute: %s" (str/join " " cmd)))
        true)
      (do
        (let [res (apply proc/exec! (concat cmd [{:ignore-err? true}]))]
          (if (zero? (:exit res))
            (proc/log-ok "SDDM preview launched successfully.")
            (proc/log-warn (format "SDDM preview exited with code %d (or missing sddm-greeter binary)" (:exit res))))
          true)))))

(defn apply-sddm
  "Install and activate the Null Sector SDDM theme."
  [{:keys [dry-run] :as opts}]
  (let [manifest (fs/read-manifest)
        boot-cfg (:boot manifest)
        source-dir (fs/expand-home (get-in boot-cfg [:sddm :source] "assets/sddm-theme"))
        theme-name (get-in boot-cfg [:sddm :theme-name] "null-sector-sddm")
        target-dir (str "/usr/share/sddm/themes/" theme-name)
        conf-file (get-in boot-cfg [:sddm :config] "/etc/sddm.conf.d/kde_settings.conf")]
    (println (format "==> Deploying SDDM Theme -> %s" theme-name))
    (if dry-run
      (do
        (proc/log-info (format "[DRY-RUN] Would copy %s -> %s (with sudo/pkexec)" source-dir target-dir))
        (proc/log-info (format "[DRY-RUN] Would update %s setting [Theme] Current=%s" conf-file theme-name))
        (proc/log-ok (format "SDDM theme %s prepared for deployment." theme-name))
        true)
      (do
        (proc/log-info (format "Copying theme assets to %s..." target-dir))
        (let [cp-res (proc/exec! "sudo" "mkdir" "-p" (str "/usr/share/sddm/themes/") {:ignore-err? true})
              sync-res (proc/exec! "sudo" "cp" "-rf" source-dir target-dir {:ignore-err? true})
              conf-dir (.getParent (io/file conf-file))
              _ (proc/exec! "sudo" "mkdir" "-p" conf-dir {:ignore-err? true})
              conf-res (proc/exec! "sudo" "kwriteconfig6" "--file" conf-file "--group" "Theme" "--key" "Current" theme-name {:ignore-err? true})]
          (if (and (zero? (:exit sync-res)) (zero? (:exit conf-res)))
            (do
              (proc/log-ok (format "SDDM theme %s installed and activated." theme-name))
              true)
            (do
              (proc/log-warn "Failed to write SDDM config (requires root permissions).")
              false)))))))

(defn apply-plymouth
  "Install and activate Plymouth boot splash theme."
  [theme-key {:keys [dry-run] :as opts}]
  (let [manifest (fs/read-manifest)
        themes (get-in manifest [:boot :plymouth :themes])
        selected-key (or (keyword theme-key)
                         (get-in manifest [:boot :plymouth :active-theme] :dot-lock))
        theme-info (get themes selected-key)]
    (if-not theme-info
      (do
        (proc/log-warn (format "Unknown Plymouth theme: %s. Available: %s"
                               (name selected-key)
                               (str/join ", " (map name (keys themes)))))
        false)
      (let [theme-name (:name theme-info)
            source-dir (fs/expand-home (:source theme-info))
            target-dir (str "/usr/share/plymouth/themes/" theme-name)]
        (println (format "==> Deploying Plymouth Splash Theme -> %s (%s)" (name selected-key) theme-name))
        (if dry-run
          (do
            (proc/log-info (format "[DRY-RUN] Would copy %s -> %s" source-dir target-dir))
            (proc/log-info (format "[DRY-RUN] Would run: plymouth-set-default-theme -R %s" theme-name))
            (proc/log-ok (format "Plymouth theme %s validated." theme-name))
            true)
          (do
            (proc/log-info (format "Copying plymouth assets to %s..." target-dir))
            (proc/exec! "sudo" "mkdir" "-p" "/usr/share/plymouth/themes/" {:ignore-err? true})
            (let [sync-res (proc/exec! "sudo" "cp" "-rf" source-dir target-dir {:ignore-err? true})
                  set-res (proc/exec! "sudo" "plymouth-set-default-theme" "-R" theme-name {:ignore-err? true})]
              (if (zero? (:exit set-res))
                (do
                  (proc/log-ok (format "Plymouth theme %s applied and initramfs rebuilt." theme-name))
                  true)
                (do
                  (proc/log-warn (format "Failed to activate Plymouth theme %s (or plymouth missing)." theme-name))
                  false)))))))))
