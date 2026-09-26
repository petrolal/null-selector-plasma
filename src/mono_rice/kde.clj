(ns mono-rice.kde
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh! ask-confirm?]]))

;; -----------------------------------------------------------------------------
;; System Compatibility Check
;; -----------------------------------------------------------------------------

(defn check-system! [& [{:keys [auto-yes]}]]
  (log-step "Verifying Host System Compatibility")
  (let [arch-release?    (fs/exists? "/etc/arch-release")
        cachyos-release? (fs/exists? "/etc/cachyos-release")
        desktop          (System/getenv "XDG_CURRENT_DESKTOP")
        session          (System/getenv "DESKTOP_SESSION")]
    (if (or arch-release? cachyos-release?)
      (log-success "Arch Linux / CachyOS base system detected.")
      (do
        (log-warn "This installer is tailored for Arch Linux and CachyOS.")
        (when-not (ask-confirm? "Proceed anyway?" {:auto-yes auto-yes})
          (throw (ex-info "Installation aborted by user." {})))))
    (if (or (and desktop (str/includes? (str/lower-case desktop) "kde"))
            (and session (str/includes? (str/lower-case session) "plasma")))
      (log-success "KDE Plasma session active.")
      (log-warn "Current desktop does not appear to be running KDE Plasma."))))

;; -----------------------------------------------------------------------------
;; KWriteConfig6 Wrapper
;; -----------------------------------------------------------------------------

(defn set-kconfig!
  "Invokes kwriteconfig6 with typed key-value pairs.
   Supports single group string or vector of group names."
  [{:keys [file group key value type sudo dry-run]}]
  (when (command-exists? "kwriteconfig6")
    (let [groups (cond
                   (vector? group) group
                   (string? group) [group]
                   :else [])
          group-args (mapcat #(list "--group" %) groups)
          args (concat ["kwriteconfig6" "--file" file]
                       group-args
                       (when type ["--type" (name type)])
                       ["--key" (str key) (str value)])]
      (sh! args {:sudo sudo :dry-run dry-run :throw? false}))))

;; -----------------------------------------------------------------------------
;; Theme & Application Configuration
;; -----------------------------------------------------------------------------

(defn apply-theme!
  "Applies KDE Plasma colors, dark mode, fonts, cursors, shortcuts, and default applications."
  [manifest & [{:keys [dry-run]}]]
  (log-step "Applying KDE Plasma Look-and-Feel Settings")
  (let [{:keys [theme terminal editor file-manager browser lockscreen]} manifest
        home (fs/expand-home "~")]

    ;; 1. Apply Colorscheme & Cursor Theme
    (if (command-exists? "plasma-apply-colorscheme")
      (let [res (sh! ["plasma-apply-colorscheme" (:color-scheme theme)] {:dry-run dry-run :throw? false})]
        (when (and (not dry-run) (not (zero? (:exit res))))
          (sh! ["plasma-apply-colorscheme" (:fallback-scheme theme)] {:dry-run dry-run :throw? false})))
      (log-warn "plasma-apply-colorscheme not found."))

    (if (command-exists? "plasma-apply-cursortheme")
      (sh! ["plasma-apply-cursortheme" (:cursor-theme theme)] {:dry-run dry-run :throw? false})
      (log-warn "plasma-apply-cursortheme not found."))

    ;; 2. GTK Dark Theme Portal
    (when (command-exists? "gsettings")
      (sh! ["gsettings" "set" "org.gnome.desktop.interface" "color-scheme" (:gtk-color-scheme theme)]
           {:dry-run dry-run :throw? false})
      (sh! ["gsettings" "set" "org.gnome.desktop.interface" "gtk-theme" (:gtk-theme theme)]
           {:dry-run dry-run :throw? false}))

    ;; 3. KDE Globals, Fonts, & Styles via kwriteconfig6
    (when (command-exists? "kwriteconfig6")
      ;; Colors & Styles
      (set-kconfig! {:file "kdeglobals" :group "General" :key "ColorScheme" :value (:color-scheme theme) :dry-run dry-run})
      (set-kconfig! {:file "kdeglobals" :group "General" :key "Name" :value (:color-scheme theme) :dry-run dry-run})
      (set-kconfig! {:file "kdeglobals" :group "KDE" :key "LookAndFeelPackage" :value (:look-and-feel theme) :dry-run dry-run})
      (set-kconfig! {:file "kdeglobals" :group "KDE" :key "colorScheme" :value (:color-scheme theme) :dry-run dry-run})
      (set-kconfig! {:file "kdeglobals" :group "KDE" :key "widgetStyle" :value (:widget-style theme) :dry-run dry-run})
      (set-kconfig! {:file "kdeglobals" :group "General" :key "widgetStyle" :value (:widget-style theme) :dry-run dry-run})
      (set-kconfig! {:file "kdeglobals" :group "Icons" :key "Theme" :value (:icon-theme theme) :dry-run dry-run})
      (set-kconfig! {:file "kdeglobals" :group "Mouse" :key "cursorTheme" :value (:cursor-theme theme) :dry-run dry-run})
      (set-kconfig! {:file "ksplashrc" :group "KSplash" :key "Theme" :value (:splash-theme theme) :dry-run dry-run})

      ;; Fonts
      (doseq [[font-key font-val] (:fonts theme)]
        (set-kconfig! {:file "kdeglobals" :group "General" :key (name font-key) :value font-val :dry-run dry-run}))

      ;; Terminal Default & Service
      (set-kconfig! {:file "kdeglobals" :group "General" :key "TerminalApplication" :value (:default terminal) :dry-run dry-run})
      (set-kconfig! {:file "kdeglobals" :group "General" :key "TerminalService" :value (:service terminal) :dry-run dry-run})

      ;; Global Shortcuts
      (let [shortcut-tab (str/join "\t" (:shortcuts terminal))
            shortcut-val (str shortcut-tab "," shortcut-tab ",Konsole")]
        (set-kconfig! {:file "kglobalshortcutsrc" :group ["services" (:service terminal)] :key "_launch" :value shortcut-val :dry-run dry-run})
        (set-kconfig! {:file "kglobalshortcutsrc" :group ["services" "cool-retro-term.desktop"] :key "_launch" :value "none,none,Cool Retro Term" :dry-run dry-run})
        (set-kconfig! {:file "kglobalshortcutsrc" :group "kwin" :key "Edit Tiles" :value "none,none,Toggle Tiles Editor" :dry-run dry-run})
        (sh! ["systemctl" "--user" "restart" "plasma-kglobalaccel"] {:dry-run dry-run :throw? false}))

      ;; Default Browser / File Manager / Editor
      (when (command-exists? "xdg-settings")
        (sh! ["xdg-settings" "set" "default-web-browser" (:default browser)] {:dry-run dry-run :throw? false}))
      (set-kconfig! {:file "mimeapps.list" :group "Default Applications" :key (:mime file-manager) :value (:default file-manager) :dry-run dry-run})
      (set-kconfig! {:file "mimeapps.list" :group "Default Applications" :key (:mime editor) :value (:default editor) :dry-run dry-run})
      (when (command-exists? "xdg-mime")
        (sh! ["xdg-mime" "default" (:default editor) (:mime editor)] {:dry-run dry-run :throw? false}))

      ;; KWin Blur for Zen Browser
      (let [blur-cfg (:kwin-blur browser)
            better-blur-pkg? (or (zero? (:exit (sh! ["pacman" "-Qq" "kwin-effects-better-blur-dx"] {:throw? false})))
                                 (zero? (:exit (sh! ["pacman" "-Qq" "kwin-effects-better-blur-dx-x11"] {:throw? false})))
                                 (zero? (:exit (sh! ["pacman" "-Qq" "kwin-effects-better-blur-dx-git"] {:throw? false}))))
            forceblur-pkg?   (or (zero? (:exit (sh! ["pacman" "-Qq" "kwin-effects-forceblur-git"] {:throw? false})))
                                 (zero? (:exit (sh! ["pacman" "-Qq" "kwin-effects-forceblur"] {:throw? false}))))]
        (cond
          better-blur-pkg?
          (do
            (set-kconfig! {:file "kwinrc" :group "Plugins" :key "forceblurEnabled" :value "false" :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Plugins" :key "better_blur_dxEnabled" :value "true" :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Effect-better-blur-dx" :key "WindowClasses" :value (str/join "," (:classes blur-cfg)) :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Effect-better-blur-dx" :key "BlurMatching" :value "true" :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Effect-better-blur-dx" :key "BlurNonMatching" :value "false" :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Effect-better-blur-dx" :key "BlurStrength" :value (str (:strength blur-cfg)) :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Effect-better-blur-dx" :key "NoiseStrength" :value (str (:noise blur-cfg)) :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Effect-better-blur-dx" :key "Brightness" :value (str (:brightness blur-cfg)) :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Effect-better-blur-dx" :key "Saturation" :value (str (:saturation blur-cfg)) :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Effect-better-blur-dx" :key "Contrast" :value (str (:contrast blur-cfg)) :dry-run dry-run}))

          forceblur-pkg?
          (do
            (log-warn "kwin-effects-better-blur-dx not installed; keeping forceblur active as fallback.")
            (set-kconfig! {:file "kwinrc" :group "Plugins" :key "forceblurEnabled" :value "true" :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Effect-forceblur" :key "MatchingClasses" :value (str/join "," (:classes blur-cfg)) :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Effect-forceblur" :key "BlurStrength" :value (str (:strength blur-cfg)) :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Effect-forceblur" :key "NoiseStrength" :value (str (:noise blur-cfg)) :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Effect-forceblur" :key "Brightness" :value (str (:brightness blur-cfg)) :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Effect-forceblur" :key "Saturation" :value (str (:saturation blur-cfg)) :dry-run dry-run})
            (set-kconfig! {:file "kwinrc" :group "Effect-forceblur" :key "Contrast" :value (str (:contrast blur-cfg)) :dry-run dry-run}))

          :else
          (log-warn "Neither kwin-effects-better-blur-dx nor forceblur found; skipping blur effect config.")))

      ;; Lockscreen Video Wallpaper & Boot Login Screen Sync
      (let [user-video-path (str "file://" (fs/path home ".local" "share" "wallpapers" (:video-file lockscreen)))
            sys-video-path  (str "file:///usr/share/wallpapers/" (:video-file lockscreen))
            video-path      (if (fs/exists? (str "/usr/share/wallpapers/" (:video-file lockscreen)))
                              sys-video-path
                              user-video-path)
            video-data [{:filename video-path
                         :enabled true
                         :duration 0
                         :customDuration 0
                         :playbackRate 0.0
                         :alternativePlaybackRate 0.0
                         :loop false
                         :dayNightPhase 4}]
            video-json (json/generate-string video-data)
            plugin     (:wallpaper-plugin lockscreen)]
        ;; 1. User kscreenlockerrc
        (set-kconfig! {:file "kscreenlockerrc" :group "Greeter" :key "WallpaperPlugin" :value plugin :dry-run dry-run})
        (set-kconfig! {:file "kscreenlockerrc" :group ["Greeter" "Wallpaper" plugin "General"] :key "VideoUrls" :value video-json :dry-run dry-run})
        (set-kconfig! {:file "kscreenlockerrc" :group ["Greeter" "Wallpaper" plugin "General"] :key "LastVideo" :value video-path :dry-run dry-run})
        (set-kconfig! {:file "kscreenlockerrc" :group ["Greeter" "Wallpaper" plugin "General"] :key "FillMode" :value (:fill-mode lockscreen) :type :int :dry-run dry-run})
        (set-kconfig! {:file "kscreenlockerrc" :group ["Greeter" "Wallpaper" plugin "General"] :key "MuteMode" :value (:mute-mode lockscreen) :type :int :dry-run dry-run})

        ;; 2. System-wide Plasma Login Manager & XDG Lockscreen Greeter Sync
        (if dry-run
          (log-info "[DRY-RUN] Would sync lockscreen wallpaper to /etc/plasmalogin.conf and /etc/xdg/kscreenlockerrc")
          (when (zero? (:exit (sh! ["sudo" "-n" "true"] {:throw? false})))
            (sh! ["kwriteconfig6" "--file" "/etc/plasmalogin.conf" "--group" "Greeter" "--key" "WallpaperPluginId" plugin] {:sudo true :throw? false})
            (sh! ["kwriteconfig6" "--file" "/etc/plasmalogin.conf" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "VideoUrls" video-json] {:sudo true :throw? false})
            (sh! ["kwriteconfig6" "--file" "/etc/plasmalogin.conf" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "LastVideo" sys-video-path] {:sudo true :throw? false})
            (sh! ["kwriteconfig6" "--file" "/etc/plasmalogin.conf" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "FillMode" (str (:fill-mode lockscreen))] {:sudo true :throw? false})
            (sh! ["kwriteconfig6" "--file" "/etc/plasmalogin.conf" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "MuteMode" (str (:mute-mode lockscreen))] {:sudo true :throw? false})
            (sh! ["kwriteconfig6" "--file" "/etc/xdg/kscreenlockerrc" "--group" "Greeter" "--key" "WallpaperPlugin" plugin] {:sudo true :throw? false})
            (sh! ["kwriteconfig6" "--file" "/etc/xdg/kscreenlockerrc" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "VideoUrls" video-json] {:sudo true :throw? false})
            (sh! ["kwriteconfig6" "--file" "/etc/xdg/kscreenlockerrc" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "LastVideo" sys-video-path] {:sudo true :throw? false})
            (sh! ["chmod" "644" "/etc/plasmalogin.conf" "/etc/xdg/kscreenlockerrc"] {:sudo true :throw? false}))))

      ;; Reconfigure KWin
      (when (command-exists? "qdbus6")
        (sh! ["qdbus6" "org.kde.KWin" "/KWin" "reconfigure"] {:dry-run dry-run :throw? false}))

      (log-success "KDE system settings registered and active."))))
