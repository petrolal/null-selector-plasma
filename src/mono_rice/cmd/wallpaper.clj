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
        video-path (str "file://" (fs/path home ".local" "share" "wallpapers" wallpaper-file))
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
      (kde/set-kconfig! {:file "kscreenlockerrc" :group ["Greeter" "Wallpaper" plugin "General"] :key "Image" :value video-path :dry-run dry-run}))))

(defn set-desktop-wallpaper! [wallpaper-file & [{:keys [dry-run]}]]
  (let [home (rfs/home-dir)
        wp-path (str (fs/path home ".local" "share" "wallpapers" wallpaper-file))]
    (when (and (command-exists? "plasma-apply-wallpaperimage")
               (str/ends-with? wallpaper-file ".png"))
      (sh! ["plasma-apply-wallpaperimage" wp-path] {:dry-run dry-run :throw? false}))))

(defn apply-wallpaper! [wallpaper-name & [{:keys [dry-run]}]]
  (let [all (list-wallpapers)
        target (first (filter #(str/includes? % wallpaper-name) all))]
    (if-not target
      (do
        (log-warn "Wallpaper not found matching:" wallpaper-name)
        (println "Available wallpapers:" (str/join ", " all))
        false)
      (do
        (log-step (str "Applying Wallpaper -> " target))
        (set-lockscreen-wallpaper! target {:dry-run dry-run})
        (set-desktop-wallpaper! target {:dry-run dry-run})
        (log-success "Wallpaper applied successfully to lockscreen and desktop:" target)
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
          (println "Usage: mono-rice wallpaper set <name>"))
        (apply-wallpaper! param opts))

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
        (println "  mono-rice wallpaper random")))))
