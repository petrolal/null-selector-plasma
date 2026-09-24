(ns mono-rice.cmd.diff
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.fs :as rfs]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh!]]))

(defn find-repo-template [repo-root rel-path]
  (let [candidates [(fs/path repo-root "plasma" rel-path)
                    (fs/path repo-root "kvantum" rel-path)
                    (fs/path repo-root "fastfetch" rel-path)
                    (fs/path repo-root "starship" rel-path)
                    (fs/path repo-root "zsh" rel-path)
                    (fs/path repo-root rel-path)]]
    (first (filter fs/exists? candidates))))

(defn diff-file [repo-root home rel-path]
  (let [live-path (fs/path home rel-path)
        repo-path (find-repo-template repo-root rel-path)]
    (cond
      (not (fs/exists? live-path))
      (println (str "\u001b[31m[MISSING LIVE]\u001b[0m " rel-path))

      (nil? repo-path)
      (println (str "\u001b[34m[UNTRACKED IN REPO]\u001b[0m " rel-path))

      :else
      (if (command-exists? "diff")
        (let [{:keys [exit out]} (sh! ["diff" "-u" "--color=always" (str repo-path) (str live-path)] {:throw? false})]
          (if (zero? exit)
            (println (str "\u001b[32m[IDENTICAL]\u001b[0m " rel-path))
            (do
              (println (str "\u001b[33m[DIFF]\u001b[0m " rel-path " (Repo Template <---> Live $HOME)"))
              (println out))))
        (let [repo-lines (str/split-lines (slurp (str repo-path)))
              live-lines (str/split-lines (slurp (str live-path)))]
          (if (= repo-lines live-lines)
            (println (str "\u001b[32m[IDENTICAL]\u001b[0m " rel-path))
            (println (str "\u001b[33m[MODIFIED]\u001b[0m " rel-path))))))))

(defn run-diff-cmd!
  "Executes visual diff between repo templates and live configuration files."
  [manifest args opts]
  (let [root (rfs/repo-root)
        home (fs/expand-home "~")
        target (first args)]
    (log-step "Computing Visual Differences (Repo <-> Live $HOME)")
    (if (and target (not (str/blank? target)))
      (diff-file root home target)
      (doseq [t (:backup-targets manifest)]
        (diff-file root home t)))
    (println)))
