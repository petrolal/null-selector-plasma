(ns mono-rice.schema
  "Declarative schema validator for rice.edn manifest."
  (:require [clojure.string :as str]))

(defn- validate-required-keys [m req-keys path]
  (let [missing (remove #(contains? m %) req-keys)]
    (when (seq missing)
      [(str "Missing required keys at " path ": " (str/join ", " missing))])))

(defn validate-manifest
  "Validates rice.edn map structure and returns a vector of error strings (empty if valid)."
  [manifest]
  (let [errors (atom [])]
    (when-not (map? manifest)
      (swap! errors conj "Manifest must be an EDN map."))

    ;; Top-level required keys
    (when-let [errs (validate-required-keys manifest [:rice/name :system :theme :dependencies] "root")]
      (swap! errors concat errs))

    ;; System checks
    (let [system (:system manifest)]
      (when (map? system)
        (when-not (contains? system :desktop)
          (swap! errors conj "Missing required key :desktop in :system"))
        (when-not (contains? system :compatibility)
          (swap! errors conj "Missing required key :compatibility in :system"))))

    ;; Theme checks
    (let [theme (:theme manifest)]
      (when (map? theme)
        (when-not (contains? theme :color-scheme)
          (swap! errors conj "Missing :color-scheme in :theme"))
        (when-not (contains? theme :fonts)
          (swap! errors conj "Missing :fonts map in :theme"))))

    ;; Dependencies checks
    (let [deps (:dependencies manifest)]
      (when (map? deps)
        (when-not (vector? (:pacman deps))
          (swap! errors conj ":dependencies :pacman must be a vector of package strings"))
        (when (and (contains? deps :nix) (not (vector? (:nix deps))))
          (swap! errors conj ":dependencies :nix must be a vector of package strings"))))

    @errors))

(defn validate!
  "Validates manifest and throws ex-info if invalid, or returns manifest."
  [manifest]
  (let [errors (validate-manifest manifest)]
    (if (empty? errors)
      manifest
      (throw (ex-info (str "Invalid rice.edn manifest:\n" (str/join "\n" (map #(str "  - " %) errors)))
                      {:errors errors})))))
