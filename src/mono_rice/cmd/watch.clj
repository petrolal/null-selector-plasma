(ns mono-rice.cmd.watch
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.fs :as rfs]
            [mono-rice.proc :refer [log-info log-success log-warn log-step]]))

(defn- file-hash [path]
  (when (fs/exists? path)
    (try
      (let [digest (java.security.MessageDigest/getInstance "SHA-256")
            bytes  (fs/read-all-bytes path)]
        (format "%064x" (BigInteger. 1 (.digest digest bytes))))
      (catch Exception _ nil))))

(defn check-target-drift
  "Checks drift between repo tracked file and live home config."
  [repo-root home rel-path]
  (let [live-path (fs/path home rel-path)
        ;; Look for repo file in plasma/, kvantum/, fastfetch/, starship/, fish/, or root
        repo-candidates [(fs/path repo-root "plasma" rel-path)
                         (fs/path repo-root "kvantum" rel-path)
                         (fs/path repo-root "fastfetch" rel-path)
                         (fs/path repo-root "starship" rel-path)
                         (fs/path repo-root "fish" rel-path)
                         (fs/path repo-root rel-path)]
        repo-path (first (filter fs/exists? repo-candidates))]
    (cond
      (not (fs/exists? live-path))
      {:path rel-path :status :missing-live :live-path live-path :repo-path repo-path}

      (nil? repo-path)
      {:path rel-path :status :untracked-in-repo :live-path live-path :repo-path nil}

      :else
      (let [live-h (file-hash live-path)
            repo-h (file-hash repo-path)]
        (if (= live-h repo-h)
          {:path rel-path :status :synced :live-path live-path :repo-path repo-path}
          {:path rel-path :status :modified :live-path live-path :repo-path repo-path})))))

(defn inspect-all-targets
  "Evaluates drift state across all :backup-targets in the manifest."
  [manifest]
  (let [root (rfs/repo-root)
        home (fs/expand-home "~")
        targets (:backup-targets manifest)]
    (mapv #(check-target-drift root home %) targets)))

(defn print-drift-report [results]
  (println "Configuration Drift Sentinel Report:")
  (println "===================================")
  (doseq [{:keys [path status]} results]
    (case status
      :synced
      (println (str "  \u001b[32m[SYNCED]\u001b[0m   " path))

      :modified
      (println (str "  \u001b[33m[MODIFIED]\u001b[0m " path " (Live file differs from repo template)"))

      :missing-live
      (println (str "  \u001b[31m[MISSING]\u001b[0m  " path " (Not found in $HOME)"))

      :untracked-in-repo
      (println (str "  \u001b[34m[UNTRACKED]\u001b[0m" path " (Present in home, template not in repo)"))))
  (println))

(defn run-watch-cmd!
  "Runs drift watch once or continuously in an interval loop."
  [manifest opts]
  (let [once?     (or (:once opts) (:dry-run opts))
        interval  (or (:interval opts) 5)
        results   (inspect-all-targets manifest)]
    (log-step "Scanning Tracked Configurations for Drift")
    (print-drift-report results)
    (if once?
      results
      (do
        (log-info (format "Sentinel active. Polling every %d seconds (Press Ctrl+C to stop)..." interval))
        (loop [prev-results results]
          (Thread/sleep (* interval 1000))
          (let [curr-results (inspect-all-targets manifest)]
            (when (not= prev-results curr-results)
              (log-warn "Configuration drift detected at" (str (java.time.Instant/now)))
              (print-drift-report curr-results))
            (recur curr-results)))))))
