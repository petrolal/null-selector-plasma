(ns mono-rice.cmd.audio
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.fs :as rfs]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh!]]))

(def audio-presets
  {:dense-cyberpunk
   {:name "Dense Cyberpunk Spectrum"
    :desc "High-density 64-bar fast spectrum with tight spacing"
    :framerate 60 :bars 64 :bar_width 2 :bar_spacing 1 :integral 85 :monstercat 1 :gravity 120}

   :minimal-monochrome
   {:name "Minimal Monochrome Standard"
    :desc "Clean 32-bar wide spacing aesthetic for Kurve visualizer"
    :framerate 60 :bars 32 :bar_width 3 :bar_spacing 4 :integral 75 :monstercat 1 :gravity 100}

   :high-fps-reactive
   {:name "Ultra Reactive 90FPS"
    :desc "90 FPS zero-smoothing raw responsive audio spectrum"
    :framerate 90 :bars 48 :bar_width 2 :bar_spacing 2 :integral 60 :monstercat 0 :gravity 150}

   :ambient-waves
   {:name "Ambient Slow Waves"
    :desc "Low-gravity smooth rolling wave visualizer"
    :framerate 30 :bars 48 :bar_width 2 :bar_spacing 3 :integral 95 :monstercat 1 :gravity 60}})

(defn render-cava-config [cfg]
  (str "# ==============================================================================\n"
       "# CAVA Configuration: null-sector-plasma (" (:name cfg) ")\n"
       "# ==============================================================================\n\n"
       "[general]\n"
       "framerate = " (:framerate cfg) "\n"
       "bars = " (:bars cfg) "\n"
       "bar_width = " (:bar_width cfg) "\n"
       "bar_spacing = " (:bar_spacing cfg) "\n\n"
       "[input]\n"
       "method = pipewire\n"
       "source = auto\n\n"
       "[output]\n"
       "method = raw\n"
       "raw_target = /dev/stdout\n"
       "data_format = ascii\n"
       "ascii_max_range = 32\n\n"
       "[smoothing]\n"
       "integral = " (:integral cfg) "\n"
       "monstercat = " (:monstercat cfg) "\n"
       "waves = 0\n"
       "gravity = " (:gravity cfg) "\n"))

(defn apply-audio-preset! [preset-key & [{:keys [dry-run]}]]
  (let [k (if (keyword? preset-key) preset-key (keyword (str/replace (str preset-key) #"^:" "")))
        cfg (get audio-presets k)]
    (if-not cfg
      (do
        (log-warn "Unknown audio preset:" preset-key)
        (println "Available presets:")
        (doseq [[pk pv] audio-presets]
          (println (format "  * %-20s : %s" (name pk) (:desc pv))))
        false)
      (do
        (log-step (str "Applying Audio Equalizer Preset -> " (:name cfg)))
        (let [root (rfs/repo-root)
              home (rfs/home-dir)
              cava-repo (fs/path root "cava" ".config" "cava" "config")
              cava-live (fs/path home ".config" "cava" "config")
              rendered  (render-cava-config cfg)]
          (if dry-run
            (log-info "[DRY-RUN] Would update CAVA config at" (str cava-repo) "and" (str cava-live))
            (do
              (spit (str cava-repo) rendered)
              (when (and (fs/exists? cava-live) (not (fs/sym-link? cava-live)))
                (spit (str cava-live) rendered))
              (when (command-exists? "pkill")
                (sh! ["pkill" "-USR2" "cava"] {:throw? false}))))
          (log-success "Audio equalizer preset active:" (:name cfg))
          true)))))

(defn run-audio-cmd!
  "Handles audio CLI subcommand: list, preset <name>."
  [args opts]
  (let [subcmd (first args)
        param  (second args)]
    (case subcmd
      ("list" "ls" nil)
      (do
        (println "Available Audio Equalizer / CAVA Presets:")
        (println "========================================")
        (doseq [[k v] audio-presets]
          (println (format "  * %-22s [%d bars @ %dfps] - %s"
                           (name k)
                           (:bars v)
                           (:framerate v)
                           (:desc v))))
        (println)
        (println "Apply preset with: mono-rice audio preset <name>"))

      ("preset" "set" "apply")
      (if (str/blank? param)
        (do
          (log-warn "Please specify an audio preset name.")
          (println "Usage: mono-rice audio preset <name>"))
        (apply-audio-preset! param opts))

      (if (get audio-presets (keyword subcmd))
        (apply-audio-preset! subcmd opts)
        (do
          (log-warn "Unknown audio action or preset:" subcmd)
          (println "Usage: mono-rice audio [list | preset <name>]"))))))
