(ns mono-rice.zen.policies
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh! ask-confirm?]]))

(def candidate-policy-paths
  ["/opt/zen-browser-bin/distribution/policies.json"
   "/opt/zen-browser/distribution/policies.json"
   "/usr/lib/zen-browser/distribution/policies.json"])

(defn find-policy-path []
  (first (filter fs/exists? candidate-policy-paths)))

(defn configure-extensions! [manifest & [{:keys [dry-run auto-yes]}]]
  (log-step "Force-Installing Zen Browser Extensions (Bonjourr, Dark Reader, Zen Internet)")
  (if-not (command-exists? "zen-browser")
    (log-warn "Zen Browser not found in PATH; skipping enterprise extension policies.")
    (if-let [policies-path (find-policy-path)]
      (if dry-run
        (log-info "[DRY-RUN] Would configure enterprise policies in:" policies-path)
        (if (or auto-yes
                (zero? (:exit (sh! ["sudo" "-n" "true"] {:throw? false})))
                (ask-confirm? (str "Force-install extensions system-wide (requires sudo, edits " policies-path ")?") {:auto-yes auto-yes}))
          (let [existing-data (try
                                (json/parse-string (slurp policies-path) true)
                                (catch Exception _ {}))
                extensions    (get-in manifest [:browser :extensions])
                ext-settings  (reduce (fn [acc ext]
                                        (assoc acc (keyword (:id ext))
                                               {:installation_mode "force_installed"
                                                :install_url       (:url ext)}))
                                      (or (get-in existing-data [:policies :ExtensionSettings]) {})
                                      extensions)
                updated-data  (-> existing-data
                                  (assoc-in [:policies :ExtensionSettings] ext-settings)
                                  (assoc-in [:policies :Preferences :toolkit.legacyUserProfileCustomizations.stylesheets]
                                            {:Value true :Status "user"}))
                tmp-file      (fs/create-temp-file {:prefix "zen_policies_" :suffix ".json"})]
            (spit (str tmp-file) (json/generate-string updated-data {:pretty true}))
            (sh! ["cp" (str tmp-file) policies-path] {:sudo true})
            (fs/delete tmp-file)
            (log-success "Enterprise policies updated in:" policies-path))
          (log-warn "Skipped Zen extension installation (sudo access declined).")))
      (log-warn "Could not locate Zen distribution/policies.json; skipping enterprise extension configuration."))))
