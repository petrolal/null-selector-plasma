(ns mono-rice.zen.profile
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [mono-rice.fs :as rfs]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-step sh!]]))

(defn zen-config-dir []
  (fs/path (rfs/home-dir) ".config" "zen"))

(defn bootstrap-profile! [& [{:keys [dry-run]}]]
  (let [zen-dir (zen-config-dir)]
    (when (and (not (fs/exists? zen-dir))
               (command-exists? "zen-browser"))
      (log-info "No Zen Browser profile found; bootstrapping one non-interactively...")
      (if dry-run
        (log-info "[DRY-RUN] Would create directory" (str zen-dir) "and run: zen-browser --headless -CreateProfile default ...")
        (do
          (fs/create-dirs zen-dir)
          (sh! ["timeout" "--signal=KILL" "15" "zen-browser" "--headless" "-CreateProfile"
                (str "default " (fs/path zen-dir "default"))]
               {:throw? false})
          (if (fs/exists? (fs/path zen-dir "default"))
            (log-success "Created Zen profile:" (str (fs/path zen-dir "default")))
            (log-warn "Could not auto-create Zen profile; launch Zen once, then re-run.")))))))

(defn list-profile-paths []
  (let [zen-dir       (zen-config-dir)
        profiles-ini  (fs/path zen-dir "profiles.ini")]
    (if (fs/exists? profiles-ini)
      (let [lines (str/split-lines (slurp (str profiles-ini)))]
        (->> lines
             (filter #(str/starts-with? % "Path="))
             (map #(str/replace % #"^Path=" ""))
             (map #(fs/path zen-dir %))
             (filter fs/exists?)
             vec))
      [])))

(defn link-profile-styles! [repo-root & [{:keys [dry-run]}]]
  (bootstrap-profile! {:dry-run dry-run})
  (let [profiles   (list-profile-paths)
        chrome-css (fs/path repo-root "zen-browser" "userChrome.css")
        cont-css   (fs/path repo-root "zen-browser" "userContent.css")
        user-js    (fs/path repo-root "zen-browser" "user.js")]
    (if (empty? profiles)
      (log-warn "No Zen Browser profiles found in ~/.config/zen; skipping style symlinks.")
      (doseq [prof profiles]
        (let [chrome-dir (fs/path prof "chrome")
              prof-name  (fs/file-name prof)]
          (rfs/ensure-dir! chrome-dir {:dry-run dry-run})
          (when (fs/exists? chrome-css)
            (rfs/symlink! chrome-css (fs/path chrome-dir "userChrome.css") {:dry-run dry-run}))
          (when (fs/exists? cont-css)
            (rfs/symlink! cont-css (fs/path chrome-dir "userContent.css") {:dry-run dry-run}))
          (when (fs/exists? user-js)
            (rfs/symlink! user-js (fs/path prof "user.js") {:dry-run dry-run}))
          (log-success "Linked userChrome.css + userContent.css + user.js to Zen profile:" (str prof-name)))))))

(defn configure-kwin-window-rule! [& [{:keys [dry-run]}]]
  (log-step "Configuring KWin Window Rule for Zen Browser")
  (let [kwinrules-path (fs/path (rfs/home-dir) ".config" "kwinrulesrc")
        desc           "Zen Browser - Borderless for Transparency"]
    (if dry-run
      (log-info "[DRY-RUN] Would add borderless kwinrulesrc entry for zen-classed windows.")
      (let [content (if (fs/exists? kwinrules-path) (slurp (str kwinrules-path)) "")
            lines   (str/split-lines content)]
        (if (str/includes? content desc)
          (log-info "KWin window rule already present for Zen Browser; skipping.")
          (let [existing-indices (->> lines
                                      (keep (fn [line]
                                              (when-let [m (re-matches #"^\[(\d+)\]$" (str/trim line))]
                                                (Integer/parseInt (second m))))))
                next-idx         (if (seq existing-indices)
                                   (inc (apply max existing-indices))
                                   1)
                all-indices      (conj (vec (sort existing-indices)) next-idx)
                rule-block       (str "\n[" next-idx "]\n"
                                      "Description=" desc "\n"
                                      "wmclass=zen\n"
                                      "wmclassmatch=1\n"
                                      "wmclasscomplete=false\n"
                                      "types=1\n"
                                      "noborder=true\n"
                                      "noborderrule=2\n")
                new-content      (if (re-find #"(?m)^\[General\]" content)
                                   (-> content
                                       (str/replace #"(?m)^count=\d+" (str "count=" (count all-indices)))
                                       (str/replace #"(?m)^rules=[\d,]*" (str "rules=" (str/join "," all-indices)))
                                       (str rule-block))
                                   (str "[General]\ncount=" (count all-indices) "\nrules=" (str/join "," all-indices) "\n"
                                        content rule-block))]
            (spit (str kwinrules-path) new-content)
            (when (command-exists? "qdbus6")
              (sh! ["qdbus6" "org.kde.KWin" "/KWin" "reconfigure"] {:throw? false}))
            (log-success (str "KWin window rule added (rule " next-idx "): " desc))))))))
