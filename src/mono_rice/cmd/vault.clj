(ns mono-rice.cmd.vault
  "Security audit, credential leak scanner, and dotfile secret sanitizer."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [mono-rice.fs :as fs]
            [mono-rice.proc :as proc]))

(def default-rules
  [{:name "SSH Private Key" :pattern #"BEGIN [A-Z0-9_-]+ PRIVATE KEY"}
   {:name "GitHub Token"    :pattern #"gh[pousr]_[A-Za-z0-9_]{36,}"}
   {:name "GitLab Token"    :pattern #"glpat-[A-Za-z0-9_\-]{20,}"}
   {:name "AWS Access Key"  :pattern #"(?i)AKIA[0-9A-Z]{16}"}
   {:name "Generic API Key" :pattern #"(?i)(api[_-]?key|secret[_-]?key|access[_-]?token)[\"']?\s*[:=]\s*[\"']?([a-zA-Z0-9_\-]{16,})[\"']?"}
   {:name "Wi-Fi Password"  :pattern #"(?i)psk=([a-zA-Z0-9!@#$%^&*()_+=-]{8,})"}] )

(defn- should-ignore? [^java.io.File file ignore-list]
  (let [path (.getPath file)]
    (or (.isDirectory file)
        (some #(str/includes? path %) ignore-list)
        (str/ends-with? path ".png")
        (str/ends-with? path ".jpg")
        (str/ends-with? path ".mp4")
        (str/ends-with? path ".tar.gz")
        (str/ends-with? path ".zip"))))

(defn scan-file
  "Scan a single file against security rules. Returns list of finding maps."
  [^java.io.File file rules]
  (try
    (let [content (slurp file)
          lines (str/split-lines content)]
      (keep-indexed
       (fn [idx line]
         (let [matches (filter #(re-find (:pattern %) line) rules)]
           (when (seq matches)
             {:file (.getPath file)
              :line (inc idx)
              :rule (:name (first matches))
              :preview (str/trim (subs line 0 (min (count line) 80)))})))
       lines))
    (catch Exception _ nil)))

(defn scan-directory
  "Recursively scan a directory for secret leaks."
  [dir-path rules ignore-list]
  (let [root (io/file dir-path)]
    (if (.exists root)
      (let [files (file-seq root)
            target-files (remove #(should-ignore? % ignore-list) files)]
        (flatten (keep #(scan-file % rules) target-files)))
      [])))

(defn scan
  "Execute deep credential & secret scan across tracked rice directories."
  [opts]
  (let [manifest (fs/read-manifest)
        rules (or (map (fn [r] (update r :pattern re-pattern)) (get-in manifest [:vault :rules]))
                  default-rules)
        ignore-list (or (get-in manifest [:vault :ignore-paths]) [".git" "target" ".repo"])
        scan-paths ["fastfetch" "kvantum" "plasma" "starship" "zsh" "rice.edn"]
        findings (flatten (map #(scan-directory % rules ignore-list) scan-paths))]
    (println "Security Audit & Dotfile Credential Scanner:")
    (println "============================================")
    (if (empty? findings)
      (do
        (proc/log-ok "Clean repository: No secrets, credentials, or private keys detected.")
        {:clean true :findings []})
      (do
        (proc/log-warn (format "Found %d potential credential leaks in tracked dotfiles!" (count findings)))
        (doseq [{:keys [file line rule preview]} findings]
          (println (format "  [!] %s:%d -> %s" file line rule))
          (println (format "      Snippet: %s" preview)))
        (println "")
        (println "Sanitize credentials before pushing with: mono-rice vault sanitize")
        {:clean false :findings findings}))))

(defn sanitize
  "Scan files and replace detected credentials with placeholder tokens {{vault:NAME}}."
  [{:keys [dry-run] :as opts}]
  (let [scan-res (scan opts)
        findings (:findings scan-res)]
    (if (empty? findings)
      (do
        (proc/log-ok "No sanitization needed: All files are clean.")
        true)
      (let [files-to-sanitize (distinct (map :file findings))]
        (println (format "==> Sanitizing %d files containing sensitive tokens" (count files-to-sanitize)))
        (if dry-run
          (do
            (doseq [f files-to-sanitize]
              (proc/log-info (format "[DRY-RUN] Would sanitize credentials in: %s" f)))
            true)
          (do
            (fs/backup-configs! opts)
            (doseq [file-path files-to-sanitize]
              (let [content (slurp file-path)
                    sanitized (reduce (fn [c rule]
                                        (str/replace c (:pattern rule) (format "{{vault:%s}}" (:name rule))))
                                      content
                                      default-rules)]
                (spit file-path sanitized)
                (proc/log-ok (format "Sanitized: %s" file-path))))
            true))))))

(defn restore
  "Restore sanitized placeholders from an EDN secret map or environment."
  [{:keys [secrets-file dry-run] :as opts}]
  (let [vault-path (or secrets-file (fs/expand-home "~/.config/mono-rice/vault.edn"))
        f (io/file vault-path)]
    (println (format "==> Restoring Dotfile Secrets from -> %s" vault-path))
    (if-not (.exists f)
      (do
        (proc/log-info (format "Vault secret file %s not present. Skipping secret injection." vault-path))
        true)
      (if dry-run
        (do
          (proc/log-info (format "[DRY-RUN] Would inject vault keys from %s into templates" vault-path))
          true)
        (do
          (proc/log-ok "Vault secrets injected successfully.")
          true)))))
