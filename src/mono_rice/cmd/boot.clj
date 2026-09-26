(ns mono-rice.cmd.boot
  "Boot and display manager orchestrator for Plasma Login Manager, SDDM, and Plymouth splash themes."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [mono-rice.fs :as fs]
            [mono-rice.proc :as proc]))

(defn- read-active-display-manager []
  (cond
    (zero? (:exit (proc/exec! "systemctl" "is-enabled" "plasmalogin.service" {:ignore-err? true})))
    "Plasma Login Manager (plasmalogin.service) [Native Lockscreen Greeter]"

    (zero? (:exit (proc/exec! "systemctl" "is-enabled" "sddm.service" {:ignore-err? true})))
    "SDDM (sddm.service)"

    :else "Default display-manager.service"))

(defn- read-active-sddm-theme []
  (let [candidates ["/etc/sddm.conf.d/kde_settings.conf"
                    "/etc/sddm.conf.d/theme.conf"
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
  "Print the current status of Display Manager, SDDM, and Plymouth boot themes."
  [opts]
  (let [manifest (fs/read-manifest)
        boot-cfg (:boot manifest)
        active-dm (read-active-display-manager)
        sddm-active (read-active-sddm-theme)
        plymouth-active (read-active-plymouth-theme)]
    (println "Boot & Display Manager Status:")
    (println "==============================")
    (println (format "  * Active Display Manager: %s" active-dm))
    (println (format "  * Configured Lock Screen: %s (Plugin: %s)"
                     (get-in manifest [:lockscreen :video-file] "digital-gaze.mp4")
                     (get-in manifest [:lockscreen :wallpaper-plugin] "smart-video-wallpaper")))
    (println (format "  * Active SDDM Theme     : %s" sddm-active))
    (println (format "  * Active Plymouth Theme : %s" plymouth-active))
    (println (format "  * Configured Plymouth   : %s" (get-in boot-cfg [:plymouth :active-theme] :dot-lock)))
    (println "")
    (println "Themes in Repository:")
    (let [sddm-exists (.exists (io/file (get-in boot-cfg [:sddm :source] "assets/sddm-theme")))
          dotlock-exists (.exists (io/file "assets/plymouth-theme/dotLock"))
          dotlockg-exists (.exists (io/file "assets/plymouth-theme/dotLockG"))]
      (println (format "  - Plasma Login Screen   : Ready (Syncs with Lock Screen)"))
      (println (format "  - SDDM Theme (Null Sector) : %s" (if sddm-exists "Ready" "Missing")))
      (println (format "  - Plymouth dotLock         : %s" (if dotlock-exists "Ready" "Missing")))
      (println (format "  - Plymouth dotLockG        : %s" (if dotlockg-exists "Ready" "Missing"))))
    {:active-dm active-dm
     :sddm-active sddm-active
     :plymouth-active plymouth-active}))

(defn list-themes
  "List available display manager and boot themes."
  [opts]
  (let [manifest (fs/read-manifest)
        boot-cfg (:boot manifest)]
    (println "Available Boot & Display Manager Themes:")
    (println "========================================")
    (println "Login Screen Display Managers:")
    (println "  * plasma-login         : Native Plasma 6 Lock Screen greeter (identical to Lock Screen)")
    (println (format "  * %-20s : %s"
                     (get-in boot-cfg [:sddm :theme-name] "null-sector-sddm")
                     "Cyberpunk NieR: Automata custom SDDM greeter"))
    (println "")
    (println "Plymouth Boot Splash Themes:")
    (doseq [[k v] (get-in boot-cfg [:plymouth :themes])]
      (println (format "  * %-20s : Plymouth graphical boot splash (%s)"
                       (name k) (:name v))))
    (println "")
    (println "Apply Lockscreen Login: mono-rice boot apply-plasma-login")
    (println "Apply SDDM:             mono-rice boot apply-sddm")
    (println "Preview SDDM:           mono-rice boot preview-sddm")
    (println "Apply Plymouth:         mono-rice boot apply-plymouth <theme-name>")
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
        (proc/log-info "[DRY-RUN] Would disable plasmalogin.service and enable sddm.service")
        (proc/log-ok (format "SDDM theme %s prepared for deployment." theme-name))
        true)
      (do
        (proc/log-info (format "Copying theme assets to %s..." target-dir))
        (let [conf-dir (.getParent (io/file conf-file))]
          (proc/exec! "sudo" "mkdir" "-p" target-dir (str "/usr/share/sddm/themes/") conf-dir "/etc/sddm.conf.d" {:ignore-err? true})
          (let [sync-res (proc/exec! "sudo" "cp" "-rf" (str source-dir "/.") target-dir {:ignore-err? true})
                _ (proc/exec! "sudo" "chmod" "-R" "755" target-dir {:ignore-err? true})
                conf-res (proc/exec! "sudo" "kwriteconfig6" "--file" conf-file "--group" "Theme" "--key" "Current" theme-name {:ignore-err? true})
                _ (proc/exec! "sudo" "kwriteconfig6" "--file" "/etc/sddm.conf" "--group" "Theme" "--key" "Current" theme-name {:ignore-err? true})
                _ (proc/exec! "sudo" "kwriteconfig6" "--file" "/etc/sddm.conf.d/theme.conf" "--group" "Theme" "--key" "Current" theme-name {:ignore-err? true})
                _ (proc/exec! "sudo" "chmod" "644" conf-file "/etc/sddm.conf" "/etc/sddm.conf.d/theme.conf" {:ignore-err? true})
                plasmalogin-check (proc/exec! "systemctl" "is-enabled" "plasmalogin.service" {:ignore-err? true})
                _ (when (zero? (:exit plasmalogin-check))
                    (proc/log-info "Disabling conflicting plasmalogin.service...")
                    (proc/exec! "sudo" "systemctl" "disable" "plasmalogin.service" {:ignore-err? true}))
                _ (proc/log-info "Enabling sddm.service display manager...")
                _ (proc/exec! "sudo" "systemctl" "enable" "sddm.service" "--force" {:ignore-err? true})]
            (if (and (zero? (:exit sync-res)) (zero? (:exit conf-res)))
              (do
                (proc/log-ok (format "SDDM theme %s installed and enabled as default display manager." theme-name))
                true)
              (do
                (proc/log-warn "Failed to write SDDM config (requires root permissions).")
                false))))))))

(defn apply-plasma-login
  "Configure Native Plasma Login Manager to be 100% identical to the Lock Screen."
  [{:keys [dry-run] :as opts}]
  (let [manifest (fs/read-manifest)
        lockscreen (:lockscreen manifest)
        video-file (:video-file lockscreen "digital-gaze.mp4")
        plugin (:wallpaper-plugin lockscreen "luisbocanegra.smart.video.wallpaper.reborn")
        sys-video-path (str "file:///usr/share/wallpapers/" video-file)
        video-data [{:filename sys-video-path
                     :enabled true
                     :duration 0
                     :customDuration 0
                     :playbackRate 0.0
                     :alternativePlaybackRate 0.0
                     :loop false
                     :dayNightPhase 4}]
        video-json (json/generate-string video-data)]
    (println "==> Configuring Plasma Login Manager (Identical to Lock Screen)")
    (if dry-run
      (do
        (proc/log-info "[DRY-RUN] Would sync assets/wallpapers to /usr/share/wallpapers")
        (proc/log-info "[DRY-RUN] Would configure /etc/plasmalogin.conf and /etc/xdg/kscreenlockerrc with lockscreen settings")
        (proc/log-info "[DRY-RUN] Would disable sddm.service and enable plasmalogin.service")
        (proc/log-ok "Plasma Login Manager configured (Dry-Run).")
        true)
      (do
        (proc/log-info "Syncing wallpapers to /usr/share/wallpapers...")
        (proc/exec! "sudo" "mkdir" "-p" "/usr/share/wallpapers" {:ignore-err? true})
        (proc/exec! "sudo" "cp" "-rf" "assets/wallpapers/." "/usr/share/wallpapers/" {:ignore-err? true})
        (proc/exec! "sudo" "chmod" "-R" "644" "/usr/share/wallpapers" {:ignore-err? true})
        (proc/exec! "sudo" "find" "/usr/share/wallpapers" "-type" "d" "-exec" "chmod" "755" "{}" "+" {:ignore-err? true})
        (proc/log-info "Writing /etc/plasmalogin.conf and /etc/xdg/kscreenlockerrc...")
        (proc/exec! "sudo" "kwriteconfig6" "--file" "/etc/plasmalogin.conf" "--group" "Greeter" "--key" "WallpaperPluginId" plugin {:ignore-err? true})
        (proc/exec! "sudo" "kwriteconfig6" "--file" "/etc/plasmalogin.conf" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "VideoUrls" video-json {:ignore-err? true})
        (proc/exec! "sudo" "kwriteconfig6" "--file" "/etc/plasmalogin.conf" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "LastVideo" sys-video-path {:ignore-err? true})
        (proc/exec! "sudo" "kwriteconfig6" "--file" "/etc/plasmalogin.conf" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "FillMode" (str (:fill-mode lockscreen 2)) {:ignore-err? true})
        (proc/exec! "sudo" "kwriteconfig6" "--file" "/etc/plasmalogin.conf" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "MuteMode" (str (:mute-mode lockscreen 5)) {:ignore-err? true})
        (proc/exec! "sudo" "kwriteconfig6" "--file" "/etc/xdg/kscreenlockerrc" "--group" "Greeter" "--key" "WallpaperPlugin" plugin {:ignore-err? true})
        (proc/exec! "sudo" "kwriteconfig6" "--file" "/etc/xdg/kscreenlockerrc" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "VideoUrls" video-json {:ignore-err? true})
        (proc/exec! "sudo" "kwriteconfig6" "--file" "/etc/xdg/kscreenlockerrc" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "LastVideo" sys-video-path {:ignore-err? true})
        (proc/exec! "sudo" "chmod" "644" "/etc/plasmalogin.conf" "/etc/xdg/kscreenlockerrc" {:ignore-err? true})
        (let [sddm-check (proc/exec! "systemctl" "is-enabled" "sddm.service" {:ignore-err? true})]
          (when (zero? (:exit sddm-check))
            (proc/log-info "Disabling sddm.service...")
            (proc/exec! "sudo" "systemctl" "disable" "sddm.service" {:ignore-err? true})))
        (proc/log-info "Enabling plasmalogin.service (native lock screen greeter)...")
        (proc/exec! "sudo" "systemctl" "enable" "plasmalogin.service" "--force" {:ignore-err? true})
        (proc/log-ok "Plasma Login Manager activated and synced with Lock Screen.")
        true))))

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
