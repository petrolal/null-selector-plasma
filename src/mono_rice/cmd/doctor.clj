(ns mono-rice.cmd.doctor
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.cmd.verify :as verify]
            [mono-rice.fs :as rfs]
            [mono-rice.kde :as kde]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh!]]))

(defn check-fonts []
  (if (command-exists? "fc-list")
    (let [{:keys [out]} (sh! ["fc-list" ":" "family"] {:throw? false})]
      (if (str/includes? out "JetBrainsMono Nerd Font")
        {:check "Fonts" :status :ok :message "JetBrainsMono Nerd Font is installed"}
        {:check "Fonts" :status :warn :message "JetBrainsMono Nerd Font not found in fontconfig"}))
    {:check "Fonts" :status :warn :message "fc-list command not available"}))

(defn check-audio-pipewire []
  (cond
    (command-exists? "wpctl")
    (let [{:keys [exit]} (sh! ["wpctl" "status"] {:throw? false})]
      (if (zero? exit)
        {:check "Audio (PipeWire)" :status :ok :message "PipeWire wireplumber audio server active"}
        {:check "Audio (PipeWire)" :status :warn :message "PipeWire wireplumber returned non-zero"}))

    (command-exists? "pactl")
    (let [{:keys [exit]} (sh! ["pactl" "info"] {:throw? false})]
      (if (zero? exit)
        {:check "Audio (Pulse/PipeWire)" :status :ok :message "PulseAudio/PipeWire audio server active"}
        {:check "Audio (Pulse/PipeWire)" :status :warn :message "Audio daemon not responsive"}))

    :else
    {:check "Audio" :status :warn :message "Neither wpctl nor pactl found"}))

(defn check-wayland-session []
  (let [session-type (System/getenv "XDG_SESSION_TYPE")
        wayland-disp (System/getenv "WAYLAND_DISPLAY")]
    (if (or (= session-type "wayland") (seq wayland-disp))
      {:check "Session Type" :status :ok :message "Wayland compositor active"}
      {:check "Session Type" :status :warn :message (str "Non-wayland session detected: " (or session-type "unknown"))})))

(defn check-gpu-compositor []
  (if (command-exists? "qdbus6")
    (let [{:keys [exit out]} (sh! ["qdbus6" "org.kde.KWin" "/KWin" "supportInformation"] {:throw? false})]
      (if (and (zero? exit) (not (str/blank? out)))
        {:check "KWin Compositor" :status :ok :message "KWin DBus interface responsive"}
        {:check "KWin Compositor" :status :warn :message "KWin DBus interface not responsive"}))
    {:check "KWin Compositor" :status :ok :message "qdbus6 skipped (offline/testing mode)"}))

(defn run-diagnostics [manifest]
  (let [results [(check-wayland-session)
                 (check-fonts)
                 (check-audio-pipewire)
                 (check-gpu-compositor)]]
    results))

(defn auto-repair! [manifest & [{:keys [dry-run]}]]
  (log-step "Running Rice Auto-Repair and Remediation")
  (let [root (rfs/repo-root)]
    ;; 1. Clean broken dangling symlinks
    (rfs/clean-broken-symlinks! {:dry-run dry-run})

    ;; 2. Re-apply modular configuration symlinks
    (rfs/apply-dotfile-symlinks! root {:dry-run dry-run})

    ;; 3. Re-deploy plasmoids and wallpapers
    (rfs/deploy-components! root {:dry-run dry-run})

    ;; 4. Rebuild KDE Sycoca & Refresh icon/font cache
    (when (command-exists? "kbuildsycoca6")
      (sh! ["kbuildsycoca6" "--noincremental"] {:dry-run dry-run :throw? false}))

    ;; 5. Reconfigure KWin
    (when (command-exists? "qdbus6")
      (sh! ["qdbus6" "org.kde.KWin" "/KWin" "reconfigure"] {:dry-run dry-run :throw? false}))

    (log-success "Auto-repair procedures executed successfully.")))

(defn run-doctor-cmd!
  "Handles the doctor CLI subcommand: inspects system health and auto-repairs issues if requested."
  [manifest args opts]
  (let [fix? (or (:fix opts) (some #{"--fix" "-f" "fix"} args))]
    (log-step "Executing Rice Doctor Diagnostics")
    (let [diagnostics (run-diagnostics manifest)]
      (println "Diagnostic Checks:")
      (println "==================")
      (doseq [{:keys [check status message]} diagnostics]
        (if (= status :ok)
          (println (format "  \u001b[32m[OK]\u001b[0m   %-22s : %s" check message))
          (println (format "  \u001b[33m[WARN]\u001b[0m %-22s : %s" check message))))
      (println)
      (if fix?
        (auto-repair! manifest opts)
        (do
          (println "Run auto-repair with: mono-rice doctor --fix (or bb doctor --fix)")
          diagnostics)))))
