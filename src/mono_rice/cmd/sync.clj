(ns mono-rice.cmd.sync
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.cmd.backup :as backup]
            [mono-rice.fs :as rfs]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh!]]))

(defn check-git-status []
  (let [root (rfs/repo-root)]
    (log-step "Inspecting Git Repository Rice Synchronization Status")
    (if-not (command-exists? "git")
      (log-warn "Git executable not found.")
      (let [{:keys [out err exit]} (sh! ["git" "status" "--short"] {:dir (str root) :throw? false})
            branch-info (sh! ["git" "branch" "-vv"] {:dir (str root) :throw? false})]
        (println "Branch Information:")
        (println (:out branch-info))
        (println "Working Tree Status:")
        (if (str/blank? out)
          (println "  \u001b[32m● Working tree clean. Repository in sync.\u001b[0m")
          (println out))
        (println)
        {:clean? (str/blank? out) :out out}))))

(defn sync-pull! [manifest & [{:keys [dry-run]}]]
  (log-step "Pulling Upstream Rice Updates")
  (let [root (rfs/repo-root)]
    (if dry-run
      (log-info "[DRY-RUN] Would create safety backup and run: git pull --rebase")
      (do
        (backup/backup! manifest {})
        (let [{:keys [exit out err]} (sh! ["git" "pull" "--rebase"] {:dir (str root) :throw? false})]
          (if (zero? exit)
            (do
              (println out)
              (log-success "Upstream rice updates pulled and rebased successfully."))
            (log-warn "Git pull encountered issues:" err)))))))

(defn sync-push! [manifest & [{:keys [dry-run message]}]]
  (log-step "Committing and Pushing Rice Changes to Remote")
  (let [root (rfs/repo-root)
        commit-msg (or message (str "chore(rice): update dotfiles " (rfs/timestamp)))]
    (if dry-run
      (log-info "[DRY-RUN] Would stage changes, commit with" commit-msg "and push upstream")
      (do
        (sh! ["git" "add" "-u"] {:dir (str root)})
        (let [{:keys [exit err]} (sh! ["git" "commit" "-m" commit-msg] {:dir (str root) :throw? false})]
          (if (zero? exit)
            (let [push-res (sh! ["git" "push"] {:dir (str root) :throw? false})]
              (if (zero? (:exit push-res))
                (log-success "Changes committed and pushed successfully.")
                (log-warn "Git push failed:" (:err push-res))))
            (log-info "No changes to commit or working tree clean.")))))))

(defn run-sync-cmd!
  "Handles the sync CLI subcommand: status, pull, or push."
  [manifest args opts]
  (let [subcmd (first args)
        msg    (second args)]
    (case subcmd
      ("status" "info" nil)
      (check-git-status)

      ("pull" "fetch" "update")
      (sync-pull! manifest opts)

      ("push" "commit")
      (sync-push! manifest (assoc opts :message msg))

      (do
        (log-warn "Unknown sync command:" subcmd)
        (println "Usage:")
        (println "  mono-rice sync status")
        (println "  mono-rice sync pull")
        (println "  mono-rice sync push [commit-message]")))))
