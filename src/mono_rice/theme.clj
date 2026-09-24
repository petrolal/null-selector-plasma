(ns mono-rice.theme
  (:require [clojure.string :as str]
            [mono-rice.kde :as kde]
            [mono-rice.proc :refer [log-info log-success log-warn log-step sh!]]))

(defn list-profiles
  "Returns the available theme profile keys and metadata from the manifest."
  [manifest]
  (or (:theme-profiles manifest)
      {:monochrome-dark (:theme manifest)}))

(defn get-profile
  "Resolves a profile keyword or string to its configuration map."
  [manifest profile-name]
  (let [profiles (list-profiles manifest)
        k (cond
            (keyword? profile-name) profile-name
            (string? profile-name)  (keyword (str/replace (str/lower-case profile-name) #"^:" ""))
            :else nil)]
    (get profiles k)))

(defn switch-theme!
  "Switches system theme to the specified profile name dynamically."
  [manifest profile-name & [{:keys [dry-run]}]]
  (let [profile (get-profile manifest profile-name)]
    (if-not profile
      (do
        (log-warn (str "Unknown theme profile: " profile-name))
        (println "Available profiles:")
        (doseq [[k v] (list-profiles manifest)]
          (println (format "  - %-18s : %s" (name k) (:name v))))
        false)
      (do
        (log-step (str "Switching Theme Profile -> " (or (:name profile) (name profile-name))))
        (let [composite-manifest (assoc manifest :theme (merge (:theme manifest) profile))]
          (kde/apply-theme! composite-manifest {:dry-run dry-run})
          (log-success (str "Successfully applied theme: " (or (:name profile) (name profile-name))))
          true)))))
