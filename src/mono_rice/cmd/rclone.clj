(ns mono-rice.cmd.rclone
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.fs :as rfs]
            [mono-rice.proc :refer [ask-confirm? command-exists? log-error log-info log-step log-success log-warn sh!]]))

;; -----------------------------------------------------------------------------
;; Default Configuration
;; -----------------------------------------------------------------------------

(def default-rclone-cfg
  {:remote           "gdrive"
   :type             "drive"
   :scope            "drive"
   :local-path       "GoogleDrive"
   :service-name     "rclone-bisync.service"
   :timer-name       "rclone-bisync.timer"
   :timer-interval   "*:0/5"
   :max-delete       15
   :conflict-resolve "newer"
   :log-path         ".cache/rclone-bisync.log"})

(defn get-rclone-cfg [manifest]
  (merge default-rclone-cfg (:rclone manifest)))

;; -----------------------------------------------------------------------------
;; Unit File Templates
;; -----------------------------------------------------------------------------

(defn service-unit-content
  [{:keys [remote local-path max-delete conflict-resolve log-path]}]
  (let [rclone-bin (if (command-exists? "rclone")
                     (str/trim (or (:out (sh! ["which" "rclone"] {:throw? false})) "/usr/bin/rclone"))
                     "/usr/bin/rclone")]
    (str "[Unit]\n"
         "Description=Rclone Google Drive Bisync\n"
         "After=network-online.target\n"
         "Wants=network-online.target\n\n"
         "[Service]\n"
         "Type=oneshot\n"
         "ExecStart=" rclone-bin " bisync %h/" local-path " " remote ": "
         "--resilient --max-delete " max-delete " --conflict-resolve " conflict-resolve
         " --log-file %h/" log-path " --log-level INFO\n")))

(defn timer-unit-content
  [{:keys [timer-interval]}]
  (str "[Unit]\n"
       "Description=Run Rclone Google Drive Bisync periodically\n\n"
       "[Timer]\n"
       "OnCalendar=" (or timer-interval "*:0/5") "\n"
       "Persistent=true\n\n"
       "[Install]\n"
       "WantedBy=timers.target\n"))

;; -----------------------------------------------------------------------------
;; Status & Detection
;; -----------------------------------------------------------------------------

(defn rclone-installed? []
  (command-exists? "rclone"))

(defn list-remotes []
  (if (rclone-installed?)
    (let [{:keys [exit out]} (sh! ["rclone" "listremotes"] {:throw? false})]
      (if (zero? exit)
        (->> (str/split-lines (or out ""))
             (map str/trim)
             (remove str/blank?)
             (map #(str/replace % #":$" ""))
             vec)
        []))
    []))

(defn remote-configured? [remote-name]
  (let [remotes (list-remotes)]
    (boolean (some #(= % remote-name) remotes))))

(defn timer-active? [timer-name]
  (if (command-exists? "systemctl")
    (let [{:keys [exit out]} (sh! ["systemctl" "--user" "is-active" timer-name] {:throw? false})]
      (and (zero? exit) (= (str/trim (or out "")) "active")))
    false))

(defn timer-enabled? [timer-name]
  (if (command-exists? "systemctl")
    (let [{:keys [exit out]} (sh! ["systemctl" "--user" "is-enabled" timer-name] {:throw? false})]
      (and (zero? exit) (= (str/trim (or out "")) "enabled")))
    false))

;; -----------------------------------------------------------------------------
;; Google Drive OAuth & Remote Setup
;; -----------------------------------------------------------------------------

(defn setup-gdrive-remote!
  "Configures Google Drive remote in rclone via interactive web browser OAuth authentication."
  [manifest & [{:keys [dry-run auto-yes force]}]]
  (let [cfg         (get-rclone-cfg manifest)
        remote-name (:remote cfg "gdrive")
        remote-type (:type cfg "drive")
        scope       (:scope cfg "drive")]
    (log-step (str "Configuring Google Drive Remote (" remote-name ":) via Web Browser Login"))
    (if-not (rclone-installed?)
      (do
        (log-warn "rclone is not installed. Please install rclone (e.g. sudo pacman -S rclone) first.")
        false)
      (if (and (remote-configured? remote-name) (not force))
        (do
          (log-success "Remote" (str remote-name ":") "is already configured and authorized in rclone.")
          true)
        (if dry-run
          (do
            (log-info "[DRY-RUN] Would launch browser OAuth flow: rclone config create" remote-name remote-type (str "scope=" scope) "config_is_local=true")
            true)
          (if (or auto-yes
                  (ask-confirm? (str "Authenticate and connect Google Drive (" remote-name ":) in your web browser now?")
                                {:auto-yes auto-yes :default true}))
            (do
              (log-info "Starting Google OAuth browser authentication...")
              (log-info "Your default web browser will open to log in to your Google Account and grant rclone access.")
              (let [res (sh! ["rclone" "config" "create" remote-name remote-type
                              (str "scope=" scope)
                              "config_is_local=true"]
                             {:inherit true :throw? false})]
                (if (and (zero? (:exit res)) (remote-configured? remote-name))
                  (do
                    (log-success "Google Drive remote (" (str remote-name ":") ") successfully authenticated and created!")
                    true)
                  (do
                    (log-warn "Google Drive authentication did not complete successfully.")
                    false))))
            (do
              (log-warn "Skipping Google Drive authentication. You can run 'mono-rice rclone setup' at any time.")
              false)))))))

;; -----------------------------------------------------------------------------
;; Service and Timer Deployment
;; -----------------------------------------------------------------------------

(defn deploy-service-and-timer!
  "Deploys systemd user service and timer for periodic rclone bisync."
  [manifest & [{:keys [dry-run]}]]
  (let [cfg          (get-rclone-cfg manifest)
        home         (rfs/home-dir)
        service-name (:service-name cfg "rclone-bisync.service")
        timer-name   (:timer-name cfg "rclone-bisync.timer")
        service-dir  (fs/path home ".config" "systemd" "user")
        service-dst  (fs/path service-dir service-name)
        timer-dst    (fs/path service-dir timer-name)
        local-sync   (fs/path home (:local-path cfg "GoogleDrive"))
        log-file     (fs/path home (:log-path cfg ".cache/rclone-bisync.log"))]
    (log-step "Deploying Rclone Google Drive Bisync Systemd User Units")
    (if dry-run
      (do
        (log-info "[DRY-RUN] Would ensure directory:" (str local-sync))
        (log-info "[DRY-RUN] Would write service unit to:" (str service-dst))
        (log-info "[DRY-RUN] Would write timer unit to:" (str timer-dst))
        (log-info "[DRY-RUN] Would run: systemctl --user daemon-reload && systemctl --user enable --now" timer-name)
        true)
      (do
        ;; Ensure local directories exist
        (rfs/ensure-dir! local-sync)
        (rfs/ensure-dir! (fs/parent log-file))
        (rfs/ensure-dir! service-dir)

        ;; Write unit files
        (spit (str service-dst) (service-unit-content cfg))
        (spit (str timer-dst) (timer-unit-content cfg))
        (log-success "Deployed service unit:" (str service-dst))
        (log-success "Deployed timer unit:" (str timer-dst))

        ;; Reload and enable timer
        (when (command-exists? "systemctl")
          (sh! ["systemctl" "--user" "daemon-reload"] {:throw? false})
          (let [res (sh! ["systemctl" "--user" "enable" "--now" timer-name] {:throw? false})]
            (if (zero? (:exit res))
              (log-success "Rclone bisync timer enabled and activated (" timer-name ").")
              (log-warn "Failed to enable rclone timer:" (:err res)))))
        true))))

;; -----------------------------------------------------------------------------
;; Bisync Actions
;; -----------------------------------------------------------------------------

(defn sync-now!
  "Triggers an immediate rclone bisync operation."
  [manifest & [{:keys [dry-run resync]}]]
  (let [cfg          (get-rclone-cfg manifest)
        home         (rfs/home-dir)
        remote       (str (:remote cfg "gdrive") ":")
        local-path   (str (fs/path home (:local-path cfg "GoogleDrive")))
        max-del      (str (:max-delete cfg 15))
        conflict     (str (:conflict-resolve cfg "newer"))
        log-path     (str (fs/path home (:log-path cfg ".cache/rclone-bisync.log")))]
    (log-step (format "Executing Rclone Google Drive Bisync (%s <-> %s)" local-path remote))
    (if-not (rclone-installed?)
      (log-warn "rclone is not installed.")
      (if-not (remote-configured? (:remote cfg "gdrive"))
        (log-warn "Remote" remote "is not configured. Run 'mono-rice rclone setup' first.")
        (let [cmd (cond-> ["rclone" "bisync" local-path remote
                           "--resilient"
                           "--max-delete" max-del
                           "--conflict-resolve" conflict
                           "--log-file" log-path
                           "--log-level" "INFO"
                           "-P"]
                    resync  (conj "--resync")
                    dry-run (conj "--dry-run"))]
          (if dry-run
            (do
              (log-info "[DRY-RUN] Would execute:" (str/join " " cmd))
              (log-success "Dry-run synchronization simulated."))
            (do
              (rfs/ensure-dir! local-path)
              (log-info "Running bisync command:" (str/join " " cmd))
              (let [res (sh! cmd {:inherit true :throw? false})]
                (if (zero? (:exit res))
                  (log-success "Rclone Google Drive synchronization completed successfully.")
                  (log-warn "Rclone bisync exited with code:" (:exit res) "(check log at:" log-path ")"))))))))))

(defn initial-setup!
  "Performs complete installation and first-time activation for Google Drive rclone bisync."
  [manifest & [{:keys [dry-run auto-yes no-prompt]} :as opts]]
  (log-step "Configuring Rclone Google Drive Cloud Synchronization")
  (let [cfg         (get-rclone-cfg manifest)
        remote-name (:remote cfg "gdrive")]
    (if-not (rclone-installed?)
      (log-warn "rclone package not detected in PATH. Skipping cloud synchronization setup.")
      (let [configured? (remote-configured? remote-name)]
        (if-not configured?
          (do
            (log-info "Google Drive remote (" (str remote-name ":") ") is not yet configured.")
            (let [auth-ok? (setup-gdrive-remote! manifest opts)]
              (when auth-ok?
                (deploy-service-and-timer! manifest opts)
                (when-not dry-run
                  (log-info "Performing initial bisync cache initialization (--resync)...")
                  (sync-now! manifest {:resync true})))))
          (do
            (log-success "Google Drive remote (" (str remote-name ":") ") is active.")
            (deploy-service-and-timer! manifest opts)))))))

;; -----------------------------------------------------------------------------
;; CLI Status & Entrypoint
;; -----------------------------------------------------------------------------

(defn status [manifest & [_opts]]
  (let [cfg          (get-rclone-cfg manifest)
        home         (rfs/home-dir)
        remote-name  (:remote cfg "gdrive")
        local-path   (fs/path home (:local-path cfg "GoogleDrive"))
        timer-name   (:timer-name cfg "rclone-bisync.timer")
        service-name (:service-name cfg "rclone-bisync.service")
        log-file     (fs/path home (:log-path cfg ".cache/rclone-bisync.log"))
        installed?   (rclone-installed?)
        configured?  (remote-configured? remote-name)
        t-active?    (timer-active? timer-name)
        t-enabled?   (timer-enabled? timer-name)]
    (println "Rclone Google Drive Bisync Status:")
    (println "==================================")
    (println (format "  * Rclone Binary Installed : %s" (if installed? "\u001b[32mYes\u001b[0m" "\u001b[31mNo\u001b[0m")))
    (println (format "  * Remote Configuration   : %s (%s:)"
                     (if configured? "\u001b[32mConfigured\u001b[0m" "\u001b[33mNot Configured\u001b[0m")
                     remote-name))
    (println (format "  * Local Directory        : %s (%s)"
                     (str local-path)
                     (if (fs/exists? local-path)
                       (str (count (fs/list-dir local-path)) " items")
                       "Not Created")))
    (println (format "  * Systemd Timer          : %s (%s)"
                     (if t-active? "\u001b[32mActive (Running)\u001b[0m" "\u001b[33mInactive\u001b[0m")
                     (if t-enabled? "Enabled" "Disabled")))
    (println (format "  * Sync Frequency         : %s" (:timer-interval cfg "*:0/5")))
    (println (format "  * Log File               : %s" (str log-file)))
    (println)
    (if (fs/exists? log-file)
      (do
        (println "Recent Log Output (Last 10 lines):")
        (println "-----------------------------------")
        (let [lines (str/split-lines (slurp (str log-file)))
              tail  (take-last 10 lines)]
          (doseq [l tail]
            (println " " l))
          (println)))
      (println "No log file found yet at:" (str log-file)))
    {:installed? installed?
     :configured? configured?
     :timer-active? t-active?
     :timer-enabled? t-enabled?}))

(defn enable-timer! [manifest opts]
  (let [cfg        (get-rclone-cfg manifest)
        timer-name (:timer-name cfg "rclone-bisync.timer")]
    (deploy-service-and-timer! manifest opts)
    (when (and (command-exists? "systemctl") (not (:dry-run opts)))
      (sh! ["systemctl" "--user" "enable" "--now" timer-name] {:throw? false})
      (log-success "Enabled and started" timer-name))))

(defn disable-timer! [manifest opts]
  (let [cfg        (get-rclone-cfg manifest)
        timer-name (:timer-name cfg "rclone-bisync.timer")]
    (if (:dry-run opts)
      (log-info "[DRY-RUN] Would disable and stop" timer-name)
      (when (command-exists? "systemctl")
        (sh! ["systemctl" "--user" "disable" "--now" timer-name] {:throw? false})
        (log-success "Disabled and stopped" timer-name)))))

(defn run-rclone-cmd!
  "Handles the rclone CLI subcommand: status, setup, sync, enable, disable."
  [manifest args opts]
  (let [subcmd (first args)]
    (case subcmd
      ("status" "info" nil)
      (status manifest opts)

      ("setup" "login" "auth" "configure")
      (do
        (setup-gdrive-remote! manifest (assoc opts :force true))
        (deploy-service-and-timer! manifest opts))

      ("sync" "bisync" "run")
      (sync-now! manifest (assoc opts :resync (boolean (some #{"--resync" "resync"} args))))

      ("resync")
      (sync-now! manifest (assoc opts :resync true))

      ("enable" "start")
      (enable-timer! manifest opts)

      ("disable" "stop")
      (disable-timer! manifest opts)

      (do
        (log-warn "Unknown rclone command:" subcmd)
        (println "Usage:")
        (println "  mono-rice rclone status          - Check cloud sync status, remote and timer")
        (println "  mono-rice rclone setup           - Launch Google Drive browser login & setup")
        (println "  mono-rice rclone sync [--resync] - Trigger immediate bidirectional sync")
        (println "  mono-rice rclone enable          - Enable and start systemd sync timer")
        (println "  mono-rice rclone disable         - Disable and stop systemd sync timer")))))
