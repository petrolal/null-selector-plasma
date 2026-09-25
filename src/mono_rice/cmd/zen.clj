(ns mono-rice.cmd.zen
  (:require [babashka.fs :as fs]
            [mono-rice.fs :as rfs]
            [mono-rice.proc :refer [log-info log-success log-warn log-step]]
            [mono-rice.zen.mods :as zmods]
            [mono-rice.zen.policies :as zpolicies]
            [mono-rice.zen.profile :as zprofile]))

(defn zen-status [manifest]
  (log-step "Inspecting Zen Browser Integration Status")
  (let [root     (rfs/repo-root)
        profiles (zprofile/list-profile-paths)
        exts     (get-in manifest [:browser :extensions] [])
        mods     (get-in manifest [:browser :mods] [])]
    (println "Discovered Zen Profiles:")
    (if (empty? profiles)
      (println "  \u001b[33m(No active Zen Browser profiles detected in ~/.config/zen)\u001b[0m")
      (doseq [p profiles]
        (let [chrome-dir (fs/path p "chrome")
              css-file   (fs/path chrome-dir "userChrome.css")
              linked?    (and (fs/exists? css-file) (fs/sym-link? css-file))]
          (println (format "  * Profile: %-35s [CSS Linked: %s]"
                           (str (fs/file-name p))
                           (if linked? "\u001b[32mYes\u001b[0m" "\u001b[31mNo\u001b[0m"))))))
    (println)
    (println (format "Configured Enterprise Extensions (%d total):" (count exts)))
    (doseq [e exts]
      (println (format "  * %-20s : %s" (:name e) (:id e))))
    (println)
    (println (format "Configured Zen Mods (%d total):" (count mods)))
    (doseq [m mods]
      (println (format "  * %-28s [UUID: %s]" (:name m) (:uuid m))))
    (println)
    {:profiles profiles :extensions exts :mods mods}))

(defn run-zen-cmd!
  "Handles the zen CLI subcommand: status, sync-css, install-extensions, or open-mods."
  [manifest args opts]
  (let [subcmd (first args)
        root   (rfs/repo-root)]
    (case subcmd
      ("status" "info" nil)
      (zen-status manifest)

      ("sync-css" "css")
      (do
        (log-step "Deploying Zen Glass userChrome.css, userContent.css & user.js")
        (zprofile/link-profile-styles! root opts)
        (log-success "Zen styling synchronized."))

      ("install-extensions" "extensions")
      (do
        (log-step "Deploying Enterprise Extensions via policies.json")
        (zpolicies/configure-extensions! manifest opts)
        (log-success "Enterprise extensions registered."))

      ("open-mods" "mods")
      (zmods/open-zen-mods! manifest)

      (do
        (log-warn "Unknown zen command:" subcmd)
        (println "Usage:")
        (println "  mono-rice zen status")
        (println "  mono-rice zen sync-css")
        (println "  mono-rice zen install-extensions")
        (println "  mono-rice zen open-mods")))))
