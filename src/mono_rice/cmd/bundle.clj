(ns mono-rice.cmd.bundle
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.fs :as rfs]
            [mono-rice.proc :refer [log-info log-success log-warn log-step sh!]]))

(defn export-bundle!
  "Creates a portable compressed archive bundle of the rice configurations and assets."
  [manifest output-path & [{:keys [dry-run]}]]
  (let [root (rfs/repo-root)
        out-file (or output-path
                     (str "null-sector-bundle-" (rfs/timestamp) ".tar.gz"))]
    (log-step (str "Exporting Rice Bundle -> " out-file))
    (if dry-run
      (do
        (log-info "[DRY-RUN] Would create archive:" out-file)
        out-file)
      (do
        (let [{:keys [exit out err]}
              (sh! ["tar" "--exclude=.git" "--exclude=.github" "-czf" out-file
                    "rice.edn" "plasma" "kvantum" "fastfetch" "starship" "fish" "cava" "assets"]
                   {:dir (str root) :throw? false})]
          (if (zero? exit)
            (do
              (log-success "Rice bundle successfully exported to:" out-file)
              out-file)
            (throw (ex-info (str "Failed to export bundle: " err) {:exit exit}))))))))

(defn import-bundle!
  "Imports and unpacks a rice archive bundle into target destination."
  [bundle-path target-dir & [{:keys [dry-run]}]]
  (let [bundle-p (fs/path bundle-path)
        dest-p   (fs/path (or target-dir (rfs/home-dir)))]
    (if-not (fs/exists? bundle-p)
      (log-warn "Specified bundle file not found:" (str bundle-p))
      (do
        (log-step (str "Importing Rice Bundle from -> " (str bundle-p)))
        (if dry-run
          (log-info "[DRY-RUN] Would unpack" (str bundle-p) "into" (str dest-p))
          (do
            (rfs/ensure-dir! dest-p)
            (let [{:keys [exit err]} (sh! ["tar" "-xzf" (str bundle-p) "-C" (str dest-p)] {:throw? false})]
              (if (zero? exit)
                (log-success "Rice bundle successfully imported into:" (str dest-p))
                (log-warn "Failed to unpack bundle:" err)))))))))

(defn run-bundle-cmd!
  "Handles the bundle CLI subcommand: export [file] or import <file> [dest]."
  [manifest args opts]
  (let [subcmd (first args)
        param1 (second args)
        param2 (nth args 2 nil)]
    (case subcmd
      ("export" "pack")
      (export-bundle! manifest param1 opts)

      ("import" "unpack")
      (if (str/blank? param1)
        (do
          (log-warn "Please specify bundle path to import.")
          (println "Usage: mono-rice bundle import <archive.tar.gz> [dest-dir]"))
        (import-bundle! param1 param2 opts))

      (do
        (log-warn "Unknown bundle action:" subcmd)
        (println "Usage:")
        (println "  mono-rice bundle export [output.tar.gz]")
        (println "  mono-rice bundle import <archive.tar.gz> [dest-dir]")))))
