(ns mono-rice.cmd.events
  "DBus desktop event listener & dynamic display sentinel."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [mono-rice.fs :as fs]
            [mono-rice.proc :as proc]
            [mono-rice.layout.scaling :as scaling]
            [mono-rice.cmd.panel :as panel]))

(defn handle-event
  "Process a detected DBus desktop event."
  [event-line {:keys [dry-run] :as opts}]
  (cond
    (str/includes? event-line "org.kde.KScreen")
    (do
      (proc/log-info "[EVENT] Screen configuration change detected via DBus (org.kde.KScreen)")
      (if dry-run
        (proc/log-info "[DRY-RUN] Would re-calculate display scaling and reload panels")
        (do
          (try
            (let [res (scaling/detect-screen-resolution)]
              (proc/log-info (format "Detected display resolution: %dx%d (Scale: %.2f)"
                                     (:width res) (:height res) (scaling/calculate-scale-factor res))))
            (panel/reload-panels! opts)
            (catch Exception e
              (proc/log-warn (format "Error reloading layout on screen change: %s" (.getMessage e)))))))
      :screen-change)

    (and (str/includes? event-line "org.freedesktop.ScreenSaver")
         (str/includes? event-line "boolean true"))
    (do
      (proc/log-info "[EVENT] Screen lock activated (org.freedesktop.ScreenSaver)")
      :screen-locked)

    (and (str/includes? event-line "org.freedesktop.ScreenSaver")
         (str/includes? event-line "boolean false"))
    (do
      (proc/log-info "[EVENT] Screen unlock detected (org.freedesktop.ScreenSaver)")
      :screen-unlocked)

    (str/includes? event-line "org.freedesktop.UPower")
    (do
      (proc/log-info "[EVENT] Power state transition detected (org.freedesktop.UPower)")
      :power-transition)

    :else
    :unhandled))

(defn emit
  "Emit a simulated or test DBus signal."
  [signal-name {:keys [dry-run] :as opts}]
  (println (format "==> Emitting Desktop DBus Signal -> %s" signal-name))
  (if dry-run
    (do
      (proc/log-info (format "[DRY-RUN] Would emit DBus signal %s" signal-name))
      (handle-event signal-name opts)
      true)
    (let [cmd (cond
                (proc/command-exists? "qdbus6")
                ["qdbus6" "org.kde.KScreen" "/KScreen" "reconfigure"]
                (proc/command-exists? "dbus-send")
                ["dbus-send" "--session" "--type=signal" "/org/kde/KScreen" "org.kde.KScreen.configChanged"]
                :else nil)]
      (if cmd
        (do
          (apply proc/exec! (concat cmd [{:ignore-err? true}]))
          (proc/log-ok (format "DBus signal %s dispatched." signal-name))
          true)
        (do
          (proc/log-warn "Neither qdbus6 nor dbus-send available on system.")
          false)))))

(defn listen
  "Listen for live desktop DBus events and dynamically adapt plasma panels & scaling."
  [{:keys [once dry-run] :as opts}]
  (println "DBus Desktop Event Monitor & Dynamic Sentinel:")
  (println "===============================================")
  (if dry-run
    (do
      (proc/log-info "[DRY-RUN] Simulating DBus monitor loop for org.kde.KScreen and org.freedesktop.ScreenSaver")
      (handle-event "signal path=/org/kde/KScreen; interface=org.kde.KScreen; member=configChanged" opts)
      (handle-event "signal path=/ScreenSaver; interface=org.freedesktop.ScreenSaver; member=ActiveChanged boolean true" opts)
      (proc/log-ok "DBus event listener simulated successfully.")
      true)
    (if-not (proc/command-exists? "dbus-monitor")
      (do
        (proc/log-warn "dbus-monitor binary not found. Cannot start live event listener.")
        false)
      (do
        (proc/log-info "Starting dbus-monitor background stream (Press Ctrl+C to stop)...")
        (let [filter-arg "type='signal',interface='org.kde.KScreen'"]
          (try
            (let [p (.exec (Runtime/getRuntime) (into-array String ["dbus-monitor" "--session" filter-arg]))
                  reader (io/reader (.getInputStream p))]
              (doseq [line (line-seq reader)]
                (handle-event line opts)
                (when once (throw (Exception. "Single event cycle complete"))))
              true)
            (catch Exception e
              (proc/log-info (format "DBus listener stopped: %s" (.getMessage e)))
              true)))))))
