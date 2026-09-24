(ns mono-rice.cmd.rollback
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.fs :as rfs]
            [mono-rice.proc :refer [ask-confirm? log-info log-success log-warn log-step]]))

(defn run-rollback-cmd!
  "Handles rollback CLI command: list or restore <snapshot>."
  [args opts]
  (let [subcmd (first args)
        target (second args)
        backups (rfs/list-backups)]
    (case subcmd
      ("list" "ls" nil)
      (do
        (println "Available Rollback Snapshots:")
        (println "=============================")
        (if (empty? backups)
          (println "  (No snapshots found in $HOME)")
          (doseq [[idx b] (map-indexed vector backups)]
            (let [bname (str (fs/file-name b))
                  files (fs/glob b "**")
                  fcount (count (filter #(not (fs/directory? %)) files))]
              (println (format "  [%d] %-35s (%d files)" (inc idx) bname fcount)))))
        (println)
        (println "Restore a snapshot with: mono-rice rollback restore <name-or-index>"))

      ("restore" "apply")
      (if (str/blank? target)
        (do
          (log-warn "Please specify a snapshot name or index to restore.")
          (println "Usage: mono-rice rollback restore <name-or-index>"))
        (let [selected (if (re-matches #"\d+" target)
                         (let [idx (dec (Integer/parseInt target))]
                           (get backups idx))
                         (first (filter #(str/includes? (str (fs/file-name %)) target) backups)))]
          (if-not selected
            (log-warn "Specified snapshot not found:" target)
            (if (or (:yes opts)
                    (:dry-run opts)
                    (ask-confirm? (str "Restore snapshot " (fs/file-name selected) "?") opts))
              (rfs/restore-backup! selected opts)
              (log-info "Rollback aborted by user."))))))))
