(ns mono-rice.cmd.wallpaper
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [mono-rice.fs :as rfs]
            [mono-rice.kde :as kde]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh!]]))

(defn list-wallpapers []
  (let [root (rfs/repo-root)
        wp-dir (fs/path root "assets" "wallpapers")]
    (if (fs/exists? wp-dir)
      (->> (fs/list-dir wp-dir)
           (filter #(let [n (str %)] (or (str/ends-with? n ".png") (str/ends-with? n ".mp4"))))
           (mapv #(str (fs/file-name %))))
      [])))

(defn set-lockscreen-wallpaper! [wallpaper-file & [{:keys [dry-run]}]]
  (let [home (rfs/home-dir)
        is-video? (str/ends-with? wallpaper-file ".mp4")
        sys-file  (str "/usr/share/wallpapers/" wallpaper-file)
        video-path (if (fs/exists? sys-file)
                     (str "file://" sys-file)
                     (str "file://" (fs/path home ".local" "share" "wallpapers" wallpaper-file)))
        video-data [{:filename video-path
                     :enabled true
                     :duration 0
                     :customDuration 0
                     :playbackRate 0.0
                     :alternativePlaybackRate 0.0
                     :loop false
                     :dayNightPhase 4}]
        video-json (json/generate-string video-data)
        plugin (if is-video?
                 "luisbocanegra.smart.video.wallpaper.reborn"
                 "org.kde.image")]
    (kde/set-kconfig! {:file "kscreenlockerrc" :group "Greeter" :key "WallpaperPlugin" :value plugin :dry-run dry-run})
    (if is-video?
      (do
        (kde/set-kconfig! {:file "kscreenlockerrc" :group ["Greeter" "Wallpaper" plugin "General"] :key "VideoUrls" :value video-json :dry-run dry-run})
        (kde/set-kconfig! {:file "kscreenlockerrc" :group ["Greeter" "Wallpaper" plugin "General"] :key "LastVideo" :value video-path :dry-run dry-run}))
      (kde/set-kconfig! {:file "kscreenlockerrc" :group ["Greeter" "Wallpaper" plugin "General"] :key "Image" :value video-path :dry-run dry-run}))
    ;; Sync to system-level Plasma Login Manager
    (when-not dry-run
      (when (zero? (:exit (sh! ["sudo" "-n" "true"] {:throw? false})))
        (sh! ["kwriteconfig6" "--file" "/etc/plasmalogin.conf" "--group" "Greeter" "--key" "WallpaperPluginId" plugin] {:sudo true :throw? false})
        (if is-video?
          (do
            (sh! ["kwriteconfig6" "--file" "/etc/plasmalogin.conf" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "VideoUrls" video-json] {:sudo true :throw? false})
            (sh! ["kwriteconfig6" "--file" "/etc/plasmalogin.conf" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "LastVideo" video-path] {:sudo true :throw? false}))
          (sh! ["kwriteconfig6" "--file" "/etc/plasmalogin.conf" "--group" "Greeter" "--group" "Wallpaper" "--group" plugin "--group" "General" "--key" "Image" video-path] {:sudo true :throw? false}))
        (sh! ["chmod" "644" "/etc/plasmalogin.conf"] {:sudo true :throw? false})))))

(defn set-desktop-wallpaper! [wallpaper-file & [{:keys [dry-run]}]]
  (let [home (rfs/home-dir)
        is-video? (str/ends-with? wallpaper-file ".mp4")
        wp-path (str (fs/path home ".local" "share" "wallpapers" wallpaper-file))
        file-uri (str "file://" wp-path)]
    (if is-video?
      (let [video-data [{:filename file-uri
                         :enabled true
                         :duration 0
                         :customDuration 0
                         :playbackRate 0.0
                         :alternativePlaybackRate 0.0
                         :loop false
                         :dayNightPhase 4}]
            video-json (json/generate-string video-data)
            plugin "luisbocanegra.smart.video.wallpaper.reborn"
            script (str "var allDesktops = desktops();\n"
                        "for (var i = 0; i < allDesktops.length; i++) {\n"
                        "    var d = allDesktops[i];\n"
                        "    d.wallpaperPlugin = '" plugin "';\n"
                        "    d.currentConfigGroup = ['Wallpaper', '" plugin "', 'General'];\n"
                        "    d.writeConfig('FillMode', 2);\n"
                        "    d.writeConfig('MuteMode', 5);\n"
                        "    d.writeConfig('LastVideo', '" file-uri "');\n"
                        "    d.writeConfig('VideoUrls', '" (str/replace video-json "'" "\\'") "');\n"
                        "    d.reloadConfig();\n"
                        "}\n")]
        (if dry-run
          (log-info "[DRY-RUN] Would set desktop animated video wallpaper to:" wp-path)
          (do
            (when (command-exists? "qdbus6")
              (sh! ["qdbus6" "org.kde.plasmashell" "/PlasmaShell" "org.kde.PlasmaShell.evaluateScript" script] {:throw? false}))
            (log-success "Desktop animated video wallpaper set:" wallpaper-file))))
      (when (command-exists? "plasma-apply-wallpaperimage")
        (sh! ["plasma-apply-wallpaperimage" wp-path] {:dry-run dry-run :throw? false})))))

(defn apply-wallpaper! [wallpaper-name & [{:keys [dry-run desktop-only]}]]
  (let [all (list-wallpapers)
        target (first (filter #(str/includes? % wallpaper-name) all))]
    (if-not target
      (do
        (log-warn "Wallpaper not found matching:" wallpaper-name)
        (println "Available wallpapers:" (str/join ", " all))
        false)
      (do
        (log-step (str "Applying Wallpaper -> " target))
        (when-not desktop-only
          (set-lockscreen-wallpaper! target {:dry-run dry-run}))
        (set-desktop-wallpaper! target {:dry-run dry-run})
        (log-success (if desktop-only
                       (str "Desktop wallpaper updated to: " target " (Lockscreen untouched)")
                       (str "Wallpaper applied successfully to lockscreen and desktop: " target)))
        true))))

(defn run-wallpaper-cmd!
  "Handles wallpaper CLI subcommand: list, set <name>, or random."
  [args opts]
  (let [subcmd (first args)
        param  (second args)
        all    (list-wallpapers)]
    (case subcmd
      ("list" "ls" nil)
      (do
        (println "Available Rice Wallpapers (Static PNG & 4K Video):")
        (println "================================================")
        (doseq [wp all]
          (let [type-tag (if (str/ends-with? wp ".mp4") "[VIDEO 4K]" "[STATIC PNG]")]
            (println (format "  * %-14s %s" type-tag wp))))
        (println)
        (println "Apply wallpaper with: mono-rice wallpaper set <name>"))

      ("set" "apply")
      (if (str/blank? param)
        (do
          (log-warn "Please specify wallpaper filename or keyword.")
          (println "Usage: mono-rice wallpaper set <name> [--desktop-only]"))
        (apply-wallpaper! param opts))

      ("set-desktop" "desktop")
      (if (str/blank? param)
        (do
          (log-warn "Please specify wallpaper filename or keyword.")
          (println "Usage: mono-rice wallpaper set-desktop <name>"))
        (apply-wallpaper! param (assoc opts :desktop-only true)))

      ("set-lockscreen" "lockscreen")
      (if (str/blank? param)
        (do
          (log-warn "Please specify wallpaper filename or keyword.")
          (println "Usage: mono-rice wallpaper set-lockscreen <name>"))
        (let [target (first (filter #(str/includes? % param) all))]
          (if target
            (set-lockscreen-wallpaper! target opts)
            (log-warn "Wallpaper not found matching:" param))))

      ("random" "shuffle")
      (if (seq all)
        (let [choice (rand-nth all)]
          (log-info "Selected random wallpaper:" choice)
          (apply-wallpaper! choice opts))
        (log-warn "No wallpapers found in assets/wallpapers."))

      (do
        (log-warn "Unknown wallpaper command:" subcmd)
        (println "Usage:")
        (println "  mono-rice wallpaper list")
        (println "  mono-rice wallpaper set <name>")
        (println "  mono-rice wallpaper set-desktop <name>")
        (println "  mono-rice wallpaper set-lockscreen <name>")
        (println "  mono-rice wallpaper random")))))
