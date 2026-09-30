(ns mono-rice.cmd.memory
  "Memory, ZRAM swap, systemd-oomd userspace OOM killer, and Baloo indexing optimizer."
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [mono-rice.fs :as rfs]
            [mono-rice.kde :as kde]
            [mono-rice.proc :refer [ask-confirm? command-exists? log-info log-step log-success log-warn sh! sudo-authenticated?]]))

(defn get-memory-cfg [manifest]
  (get manifest :memory
       {:zram {:enabled true
               :zram-size "ram / 2"
               :compression-algorithm "zstd"
               :swap-priority 100
               :config-file "/etc/systemd/zram-generator.conf"}
        :oomd {:enabled true
               :service-name "systemd-oomd.service"}
        :baloo {:indexing-file-content false
                :only-basic-indexing true
                :exclude-hidden true}}))

;; -----------------------------------------------------------------------------
;; Diagnostic Status Checkers
;; -----------------------------------------------------------------------------

(defn read-meminfo []
  (try
    (let [{:keys [out]} (sh! ["cat" "/proc/meminfo"] {:throw? false})
          lines (str/split-lines (or out ""))]
      (reduce (fn [acc line]
                (let [[k v] (str/split line #":\s+")]
                  (if (and k v)
                    (assoc acc k (str/trim (str/replace v #"\s*kB$" "")))
                    acc)))
              {}
              lines))
    (catch Exception _ {})))

(defn check-zram-status []
  (let [zram-conf-file "/etc/systemd/zram-generator.conf"
        conf-exists? (fs/exists? zram-conf-file)
        conf-content (when conf-exists? (try (slurp zram-conf-file) (catch Exception _ "")))
        {:keys [out]} (sh! ["cat" "/proc/swaps"] {:throw? false})
        swaps-content (or out "")
        zram-in-swaps? (str/includes? swaps-content "zram")
        has-half-ram-size? (and conf-content (re-find #"(?i)zram-size\s*=\s*ram\s*/\s*2" conf-content))]
    (cond
      (and conf-exists? zram-in-swaps? has-half-ram-size?)
      {:check "ZRAM Compressed Swap" :status :ok :message "Active (50% RAM allocation with zstd)"}

      (and conf-exists? zram-in-swaps?)
      {:check "ZRAM Compressed Swap" :status :ok :message "Active (Configured in zram-generator.conf)"}

      conf-exists?
      {:check "ZRAM Compressed Swap" :status :warn :message "Config present but /dev/zram0 not active in swaps"}

      :else
      {:check "ZRAM Compressed Swap" :status :warn :message "Not configured (Risk of hard system freezes under high RAM load)"})))

(defn check-oomd-status []
  (if (command-exists? "systemctl")
    (let [active? (zero? (:exit (sh! ["systemctl" "is-active" "systemd-oomd.service"] {:throw? false})))
          enabled? (zero? (:exit (sh! ["systemctl" "is-enabled" "systemd-oomd.service"] {:throw? false})))]
      (cond
        (and active? enabled?)
        {:check "systemd-oomd (OOM Killer)" :status :ok :message "Active & Enabled (Protects desktop from freezing)"}

        active?
        {:check "systemd-oomd (OOM Killer)" :status :ok :message "Active (Not enabled on boot)"}

        enabled?
        {:check "systemd-oomd (OOM Killer)" :status :warn :message "Enabled but currently inactive"}

        :else
        {:check "systemd-oomd (OOM Killer)" :status :warn :message "Disabled / Inactive (Hard lockups can occur on memory pressure)"}))
    {:check "systemd-oomd (OOM Killer)" :status :warn :message "systemctl not available"}))

(defn check-baloo-status []
  (let [baloo-file (fs/path (rfs/home-dir) ".config" "baloofilerc")]
    (if (fs/exists? baloo-file)
      (try
        (let [content (slurp (str baloo-file))
              no-content-indexing? (or (str/includes? content "indexing-file-content=false")
                                       (str/includes? content "only basic indexing=true"))
              disabled? (str/includes? content "indexing=false")]
          (cond
            disabled?
            {:check "Baloo File Indexer" :status :ok :message "Indexing disabled"}

            no-content-indexing?
            {:check "Baloo File Indexer" :status :ok :message "Tuned (Content indexing disabled to prevent memory leaks)"}

            :else
            {:check "Baloo File Indexer" :status :warn :message "Full content indexing enabled (Can consume excessive RAM/CPU)"}))
        (catch Exception _
          {:check "Baloo File Indexer" :status :warn :message "Unable to read ~/.config/baloofilerc"}))
      {:check "Baloo File Indexer" :status :warn :message "Default unoptimized configuration"})))

;; -----------------------------------------------------------------------------
;; Configuration Enforcers
;; -----------------------------------------------------------------------------

(defn configure-zram!
  "Deploys optimized /etc/systemd/zram-generator.conf (50% RAM zstd swap) and activates it."
  [manifest & [{:keys [dry-run auto-yes yes]}]]
  (log-step "Configuring ZRAM Compressed Swap (High-Performance Memory Buffer)")
  (let [cfg (:zram (get-memory-cfg manifest))
        zram-file (or (:config-file cfg) "/etc/systemd/zram-generator.conf")
        zram-size (or (:zram-size cfg) "ram / 2")
        algo (or (:compression-algorithm cfg) "zstd")
        priority (or (:swap-priority cfg) 100)
        target-content (str "[zram0]\n"
                            "zram-size = " zram-size "\n"
                            "compression-algorithm = " algo "\n"
                            "swap-priority = " priority "\n")]
    (if dry-run
      (do
        (log-info "[DRY-RUN] Would write ZRAM configuration to:" zram-file)
        (log-info "[DRY-RUN] Content:\n" (str/trim target-content))
        (log-info "[DRY-RUN] Would restart systemd-zram-setup@zram0.service via sudo"))
      (if (or auto-yes yes
              (sudo-authenticated?)
              (ask-confirm? "Configure ZRAM 50% RAM swap in /etc/systemd/zram-generator.conf (requires sudo)?"
                            {:auto-yes (or auto-yes yes)}))
        (let [tmp-file (str (fs/create-temp-file {:prefix "zram_gen_" :suffix ".conf"}))]
          (spit tmp-file target-content)
          (sh! ["mkdir" "-p" "/etc/systemd"] {:sudo true :throw? false})
          (sh! ["cp" tmp-file zram-file] {:sudo true})
          (sh! ["chmod" "644" zram-file] {:sudo true :throw? false})
          (fs/delete tmp-file)
          (log-success "Saved ZRAM configuration to:" zram-file)
          (log-info "Activating ZRAM swap service...")
          (sh! ["systemctl" "daemon-reload"] {:sudo true :throw? false})
          (let [res (sh! ["systemctl" "restart" "systemd-zram-setup@zram0.service"] {:sudo true :throw? false})]
            (if (zero? (:exit res))
              (log-success "ZRAM swap service restarted and active.")
              (log-warn "Could not restart systemd-zram-setup@zram0.service immediately; will activate on next boot."))))
        (log-warn "Skipped ZRAM configuration (sudo access declined).")))))

(defn configure-oomd!
  "Enables and starts systemd-oomd to protect against kernel unresponsiveness and desktop freezes."
  [manifest & [{:keys [dry-run auto-yes yes]}]]
  (log-step "Configuring systemd-oomd Userspace Out-Of-Memory Daemon")
  (if dry-run
    (log-info "[DRY-RUN] Would enable and start systemd-oomd.service via: sudo systemctl enable --now systemd-oomd")
    (if (or auto-yes yes
            (sudo-authenticated?)
            (ask-confirm? "Enable systemd-oomd daemon to prevent desktop freezing (requires sudo)?"
                          {:auto-yes (or auto-yes yes)}))
      (let [res (sh! ["systemctl" "enable" "--now" "systemd-oomd.service"] {:sudo true :throw? false})]
        (if (zero? (:exit res))
          (log-success "systemd-oomd is now enabled and active.")
          (log-warn "Failed to enable systemd-oomd.service.")))
      (log-warn "Skipped systemd-oomd configuration (sudo access declined)."))))


(defn tune-baloo!
  "Configures KDE Baloo file search to prevent memory exhaustion and high CPU spikes."
  [manifest & [{:keys [dry-run]}]]
  (log-step "Tuning KDE Baloo File Indexer (Limiting Memory Footprint)")
  (let [home (rfs/home-dir)
        baloo-path (fs/path home ".config" "baloofilerc")]
    (if dry-run
      (log-info "[DRY-RUN] Would configure" (str baloo-path) "to disable full content indexing and exclude hidden folders")
      (do
        (rfs/ensure-dir! (fs/path home ".config"))
        (when (command-exists? "kwriteconfig6")
          (kde/set-kconfig! {:file "baloofilerc" :group "Basic Settings" :key "indexing-file-content" :value "false" :type :bool :dry-run dry-run})
          (kde/set-kconfig! {:file "baloofilerc" :group "General" :key "only basic indexing" :value "true" :type :bool :dry-run dry-run})
          (kde/set-kconfig! {:file "baloofilerc" :group "General" :key "exclude hidden folders" :value "true" :type :bool :dry-run dry-run})
          (kde/set-kconfig! {:file "baloofilerc" :group "General" :key "first run" :value "false" :type :bool :dry-run dry-run})
          (kde/set-kconfig! {:file "baloofilerc" :group "General" :key "dbVersion" :value "2" :dry-run dry-run}))
        ;; If baloofilerc still doesn't exist or kwriteconfig6 unavailable, write directly
        (when-not (fs/exists? baloo-path)
          (spit (str baloo-path)
                (str "[Basic Settings]\n"
                     "indexing-file-content=false\n\n"
                     "[General]\n"
                     "dbVersion=2\n"
                     "exclude hidden folders=true\n"
                     "first run=false\n"
                     "only basic indexing=true\n")))
        (log-success "KDE Baloo indexer configured for lightweight filename-only indexing.")))))

(defn apply-memory-tuning!
  "Applies complete system memory stability optimizations: ZRAM, systemd-oomd, and Baloo tuning."
  [manifest & [opts]]
  (log-step "Deploying System Memory & Anti-Freeze Stability Suite")
  (configure-zram! manifest opts)
  (configure-oomd! manifest opts)
  (tune-baloo! manifest opts)
  (log-success "Memory stability tuning applied successfully."))

;; -----------------------------------------------------------------------------
;; CLI Subcommand & Status
;; -----------------------------------------------------------------------------

(defn status
  "Prints live memory, swap, ZRAM, OOM daemon, and Baloo indexing diagnostics."
  [opts]
  (let [mem (read-meminfo)
        total-kb (parse-long (get mem "MemTotal" "0"))
        avail-kb (parse-long (get mem "MemAvailable" "0"))
        swap-total-kb (parse-long (get mem "SwapTotal" "0"))
        swap-free-kb (parse-long (get mem "SwapFree" "0"))
        to-gb (fn [kb] (format "%.1f GB" (/ (double kb) 1048576.0)))
        zram-stat (check-zram-status)
        oomd-stat (check-oomd-status)
        baloo-stat (check-baloo-status)]
    (println "System Memory & Stability Status:")
    (println "==================================")
    (println (format "  * Physical RAM   : %s Total | %s Available" (to-gb total-kb) (to-gb avail-kb)))
    (println (format "  * Swap Space     : %s Total | %s Free" (to-gb swap-total-kb) (to-gb swap-free-kb)))
    (println (format "  * ZRAM Swap      : %s" (:message zram-stat)))
    (println (format "  * systemd-oomd   : %s" (:message oomd-stat)))
    (println (format "  * Baloo Indexer  : %s" (:message baloo-stat)))
    (println)
    {:ram-total (to-gb total-kb)
     :swap-total (to-gb swap-total-kb)
     :zram zram-stat
     :oomd oomd-stat
     :baloo baloo-stat}))

(defn run-memory-cmd!
  "Handles the 'memory' CLI subcommand."
  [manifest args opts]
  (let [subcmd (first args)]
    (case subcmd
      ("apply" "install" "tune")
      (apply-memory-tuning! manifest opts)

      "zram"
      (configure-zram! manifest opts)

      "oomd"
      (configure-oomd! manifest opts)

      "baloo"
      (tune-baloo! manifest opts)

      ;; Default -> status
      (do
        (status opts)
        (println "Available commands:")
        (println "  mono-rice memory apply   - Apply all ZRAM, systemd-oomd, and Baloo memory fixes")
        (println "  mono-rice memory zram    - Configure and restart ZRAM swap (50% RAM)")
        (println "  mono-rice memory oomd    - Enable systemd-oomd userspace OOM killer")
        (println "  mono-rice memory baloo   - Tune KDE Baloo file indexing footprint")))))
