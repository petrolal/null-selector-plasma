(ns mono-rice.cmd.fetch
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [mono-rice.fs :as rfs]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh!]]))

(def ascii-presets
  {:nier-automata
   {:name "NieR: Automata YoRHa"
    :desc "YoRHa military insignia emblem"
    :art "
      /\\
     /  \\
    / /\\ \\
   / /  \\ \\
  /_/ /\\ \\_\\
    \\ \\/ /
     \\  /
      \\/
    [ YoRHa ]"}

   :null-sector
   {:name "Null Sector Plasma"
    :desc "Monochrome cyberpunk geometric glyph"
    :art "
   ┌─────────┐
   │ █ █ █ █ │
   │ █ ▄▄▄ █ │
   │ █ █ █ █ │
   │ █ ▀▀▀ █ │
   └─────────┘
  [ NULL-SECTOR ]"}

   :cyberpunk-skull
   {:name "Cyberpunk Glitch Skull"
    :desc "Cybernetic skull telemetry icon"
    :art "
     .-'''-.
    / _   _ \\
   / (o) (o) \\
  |     _     |
   \\  '---'  /
    '-.....-'
   { CYBERPUNK }"}

   :minimal-arch
   {:name "Minimal Arch Monochrome"
    :desc "Minimal high-contrast Arch Linux logo"
    :art "
      /\\
     /  \\
    /\\   \\
   /      \\
  /   ,,   \\
 /   |  |  -\\
/_-''    ''-_/"}})

(defn list-fetch-presets []
  (println "Available Fastfetch ASCII Presets:")
  (println "==================================")
  (doseq [[k v] ascii-presets]
    (println (format "  * %-18s : %s" (name k) (:desc v))))
  (println)
  (println "Preview with: mono-rice fetch preview <name>")
  (println "Apply with:   mono-rice fetch set <name>"))

(defn preview-preset [preset-key]
  (let [k (if (keyword? preset-key) preset-key (keyword (str/replace (str preset-key) #"^:" "")))
        preset (get ascii-presets k)]
    (if-not preset
      (log-warn "Unknown fastfetch preset:" preset-key)
      (do
        (println (str "\u001b[1;36m=== " (:name preset) " ===\u001b[0m"))
        (println (:art preset))
        (println)))))

(defn apply-fetch-preset! [preset-key & [{:keys [dry-run]}]]
  (let [k (if (keyword? preset-key) preset-key (keyword (str/replace (str preset-key) #"^:" "")))
        preset (get ascii-presets k)]
    (if-not preset
      (do
        (log-warn "Unknown fastfetch preset:" preset-key)
        (list-fetch-presets)
        false)
      (do
        (log-step (str "Applying Fastfetch Preset -> " (:name preset)))
        (let [root (rfs/repo-root)
              art-file (fs/path root "fastfetch" ".config" "fastfetch" "logo.txt")]
          (if dry-run
            (log-info "[DRY-RUN] Would write ASCII art to" (str art-file))
            (spit (str art-file) (:art preset)))
          (log-success "Fastfetch logo updated to:" (:name preset))
          true)))))

(defn run-fetch-cmd!
  "Handles the fetch CLI subcommand."
  [args opts]
  (let [subcmd (first args)
        param  (second args)]
    (case subcmd
      ("list" "ls" nil)
      (list-fetch-presets)

      ("preview" "view")
      (if (str/blank? param)
        (do
          (log-warn "Please specify preset name to preview.")
          (println "Usage: mono-rice fetch preview <preset>"))
        (preview-preset param))

      ("set" "apply")
      (if (str/blank? param)
        (do
          (log-warn "Please specify preset name to apply.")
          (println "Usage: mono-rice fetch set <preset>"))
        (apply-fetch-preset! param opts))

      (if (get ascii-presets (keyword subcmd))
        (apply-fetch-preset! subcmd opts)
        (do
          (log-warn "Unknown fetch command or preset:" subcmd)
          (println "Usage: mono-rice fetch [list | preview <preset> | set <preset>]"))))))
