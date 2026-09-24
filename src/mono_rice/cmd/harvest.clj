(ns mono-rice.cmd.harvest
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.fs :as rfs]
            [mono-rice.layout.sanitizer :as san]
            [mono-rice.proc :refer [ask-confirm? log-info log-step log-success log-warn sh!]]))

(defn harvest! [manifest opts]
  (log-step "Starting Configuration Harvest Pass")
  (let [home (rfs/home-dir)
        root (rfs/repo-root)]
    (when-not (ask-confirm? (str "Scrape active configs from " home " into repo?") opts)
      (log-warn "Harvest cancelled.")
      (System/exit 0))

    (let [harvest-file (fn [src-rel dst-rel]
                         (let [src (fs/path home src-rel)
                               dst (fs/path root dst-rel)]
                           (if (fs/exists? src)
                             (if (and (fs/sym-link? src)
                                      (= (str (fs/real-path src)) (str (fs/real-path dst))))
                               (log-info "Already symlinked to repo (no-op):" (str src-rel))
                               (do
                                 (rfs/ensure-dir! (fs/parent dst) opts)
                                 (if (not (:dry-run opts))
                                   (fs/copy src dst {:replace-existing true}))
                                 (log-success "Harvested:" (str src-rel) "->" (str dst-rel))))
                             (log-warn "Source not found (skipping):" (str src-rel)))))
          harvest-dir  (fn [src-rel dst-rel]
                         (let [src (fs/path home src-rel)
                               dst (fs/path root dst-rel)]
                           (if (fs/exists? src)
                             (do
                               (rfs/ensure-dir! dst opts)
                               (if (not (:dry-run opts))
                                 (fs/copy-tree src dst {:replace-existing true}))
                               (log-success "Harvested directory:" (str src-rel) "->" (str dst-rel)))
                             (log-warn "Source directory not found (skipping):" (str src-rel)))))]

      ;; Harvest Plasma Configs
      (harvest-file ".config/kdeglobals" "plasma/.config/kdeglobals")
      (harvest-file ".config/kwinrc" "plasma/.config/kwinrc")
      (harvest-file ".config/plasmashellrc" "plasma/.config/plasmashellrc")
      (harvest-file ".config/plasma-org.kde.plasma.desktop-appletsrc" "plasma/.config/plasma-org.kde.plasma.desktop-appletsrc")
      (harvest-file ".config/kglobalshortcutsrc" "plasma/.config/kglobalshortcutsrc")
      (harvest-file ".config/klassy/klassyrc" "plasma/.config/klassy/klassyrc")

      ;; Harvest Theme & Engine Configs
      (harvest-file ".config/Kvantum/kvantum.kvconfig" "kvantum/.config/Kvantum/kvantum.kvconfig")
      (harvest-dir ".config/Kvantum/PetrolalDark" "kvantum/.config/Kvantum/PetrolalDark")

      ;; Harvest Shell & Terminal Configs
      (harvest-file ".config/cava/config" "cava/.config/cava/config")
      (harvest-file ".zshrc" "zsh/.zshrc")
      (harvest-file ".config/zsh/aliases.zsh" "zsh/.config/zsh/aliases.zsh")
      (harvest-file ".config/fastfetch/config.jsonc" "fastfetch/.config/fastfetch/config.jsonc")
      (harvest-file ".config/starship.toml" "starship/.config/starship.toml")

      ;; Sanitization pass on repo configs
      (when-not (:dry-run opts)
        (log-step "Sanitizing Harvested Configurations")
        (let [appletsrc-path (fs/path root "plasma" ".config" "plasma-org.kde.plasma.desktop-appletsrc")
              plasmashell-p  (fs/path root "plasma" ".config" "plasmashellrc")]
          (when (fs/exists? appletsrc-path)
            (let [raw-content    (slurp (str appletsrc-path))
                  sanitized      (san/sanitize-harvest raw-content {:home home})
                  live-panel-ids (san/panel-containment-ids raw-content)]
              (spit (str appletsrc-path) sanitized)
              (log-success "Sanitized template placeholders in desktop-appletsrc.")

              (when (fs/exists? plasmashell-p)
                (let [plasmashell-raw (slurp (str plasmashell-p))
                      {:keys [text pruned]} (san/prune-stale-panels plasmashell-raw live-panel-ids)]
                  (spit (str plasmashell-p) text)
                  (log-success "Pruned" pruned "stale panel block(s) from plasmashellrc.")))

              (let [v-res (san/verify-widgets raw-content)]
                (if (:valid? v-res)
                  (log-success "Harvested layout contains all required rice widgets.")
                  (log-warn "Harvested layout is missing required widgets:"
                            (str/join ", " (map :plugin (:missing v-res))))))))))

      ;; Summary of git diff
      (log-step "Harvest Complete")
      (sh! ["git" "status" "-s"] {:throw? false}))))
