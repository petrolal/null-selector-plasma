(ns mono-rice.cmd.daemon
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.cmd.watch :as watch]
            [mono-rice.fs :as rfs]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh!]]))

(defn service-unit-content [mono-rice-bin]
  (str "[Unit]\n"
       "Description=Null Sector Plasma Configuration Sentinel Daemon\n"
       "After=plasma-workspace.target\n\n"
       "[Service]\n"
       "Type=simple\n"
       "ExecStart=" mono-rice-bin " daemon start --interval 10\n"
       "Restart=always\n"
       "RestartSec=5s\n\n"
       "[Install]\n"
       "WantedBy=default.target\n"))

(defn install-systemd-service! [& [{:keys [dry-run]}]]
  (log-step "Installing Null Sector Systemd User Service")
  (let [home        (rfs/home-dir)
        service-dir (fs/path home ".config" "systemd" "user")
        service-file (fs/path service-dir "null-sector-sentinel.service")
        root        (rfs/repo-root)
        mono-bin    (str (fs/path root "mono-rice"))]
    (if dry-run
      (log-info "[DRY-RUN] Would create systemd service at:" (str service-file))
      (do
        (rfs/ensure-dir! service-dir)
        (spit (str service-file) (service-unit-content mono-bin))
        (when (command-exists? "systemctl")
          (sh! ["systemctl" "--user" "daemon-reload"] {:throw? false})
          (sh! ["systemctl" "--user" "enable" "--now" "null-sector-sentinel.service"] {:throw? false}))
        (log-success "Systemd user service installed and enabled:" (str service-file))))))

(defn uninstall-systemd-service! [& [{:keys [dry-run]}]]
  (log-step "Uninstalling Null Sector Systemd User Service")
  (let [home         (rfs/home-dir)
        service-file (fs/path home ".config" "systemd" "user" "null-sector-sentinel.service")]
    (if dry-run
      (log-info "[DRY-RUN] Would disable and remove:" (str service-file))
      (do
        (when (command-exists? "systemctl")
          (sh! ["systemctl" "--user" "disable" "--now" "null-sector-sentinel.service"] {:throw? false}))
        (when (fs/exists? service-file)
          (fs/delete service-file))
        (when (command-exists? "systemctl")
          (sh! ["systemctl" "--user" "daemon-reload"] {:throw? false}))
        (log-success "Systemd service removed successfully.")))))

(defn send-notification! [title message]
  (when (command-exists? "notify-send")
    (sh! ["notify-send" "-u" "normal" "-a" "Null-Sector-Rice" title message] {:throw? false})))

(defn start-daemon-loop! [manifest opts]
  (let [interval (or (:interval opts) 10)]
    (log-step (format "Starting Null Sector Sentinel Daemon (Interval: %ds)" interval))
    (send-notification! "Null Sector Rice Sentinel" "Configuration drift monitor active.")
    (if (:dry-run opts)
      (log-info "[DRY-RUN] Daemon loop started and simulated.")
      (loop [prev-results (watch/inspect-all-targets manifest)]
        (Thread/sleep (* interval 1000))
        (let [curr-results (watch/inspect-all-targets manifest)
              modified     (filter #(= (:status %) :modified) curr-results)]
          (when (not= prev-results curr-results)
            (when (seq modified)
              (let [mod-names (str/join ", " (map :path modified))]
                (log-warn "Configuration drift detected:" mod-names)
                (send-notification! "Config Drift Detected"
                                    (str "Modified configs: " mod-names)))))
          (recur curr-results))))))

(defn run-daemon-cmd!
  "Handles the daemon CLI subcommand."
  [manifest args opts]
  (let [subcmd (first args)]
    (case subcmd
      ("start" "run" nil)
      (start-daemon-loop! manifest opts)

      ("install-service" "enable")
      (install-systemd-service! opts)

      ("uninstall-service" "disable")
      (uninstall-systemd-service! opts)

      ("status")
      (if (command-exists? "systemctl")
        (let [{:keys [out]} (sh! ["systemctl" "--user" "status" "null-sector-sentinel.service"] {:throw? false})]
          (println out))
        (log-warn "systemctl not available."))

      (do
        (log-warn "Unknown daemon command:" subcmd)
        (println "Usage:")
        (println "  mono-rice daemon start [--interval <sec>]")
        (println "  mono-rice daemon install-service")
        (println "  mono-rice daemon uninstall-service")
        (println "  mono-rice daemon status")))))
