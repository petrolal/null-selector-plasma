(ns mono-rice.zen.mods
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [mono-rice.proc :refer [command-exists? log-info log-success log-warn log-error log-step sh!]]
            [mono-rice.zen.profile :as zprof]))

(def catalog-url
  "https://raw.githubusercontent.com/zen-browser/theme-store/main/themes.json")

(defn seed-mods-registry! [manifest & [{:keys [dry-run]}]]
  (log-step "Seeding Zen Mods Registry")
  (let [profiles (zprof/list-profile-paths)]
    (cond
      (empty? profiles)
      (log-warn "No Zen profiles found; skipping mod registry seeding.")

      dry-run
      (log-info "[DRY-RUN] Would fetch mods catalog and seed zen-themes.json for" (count profiles) "profile(s).")

      :else
      (let [catalog (try
                      (let [resp (http/get catalog-url)]
                        (json/parse-string (:body resp) true))
                      (catch Exception e
                        (log-warn "Could not fetch Zen theme catalog:" (.getMessage e))
                        nil))]
        (if-not catalog
          (log-warn "Skipping mod registry seeding (catalog unavailable).")
          (let [required-mods (get-in manifest [:browser :mods])]
            (doseq [prof profiles]
              (let [registry-path (fs/path prof "zen-themes.json")
                    registry      (if (fs/exists? registry-path)
                                    (try
                                      (json/parse-string (slurp (str registry-path)) true)
                                      (catch Exception _ {}))
                                    {})
                    missing-mods  (filter (fn [{:keys [uuid]}]
                                            (not (contains? registry (keyword uuid))))
                                          required-mods)]
                (if (seq missing-mods)
                  (let [updated-registry (reduce (fn [acc {:keys [name uuid]}]
                                                   (let [uuid-k (keyword uuid)]
                                                     (if-let [entry (get catalog uuid-k)]
                                                       (assoc acc uuid-k (assoc entry :enabled true))
                                                       acc)))
                                                 registry
                                                 missing-mods)
                        added-names      (map :name missing-mods)]
                    (spit (str registry-path) (json/generate-string updated-registry {:pretty true}))
                    (log-success (str (fs/file-name prof) ": registered " (str/join ", " added-names))))
                  (log-info (str (fs/file-name prof) ": all required mods already registered in zen-themes.json")))))))))))

(defn decolorize-workspace-theme! [& [{:keys [dry-run]}]]
  (log-step "De-Colorizing Zen Workspace Gradients")
  (let [profiles (zprof/list-profile-paths)]
    (cond
      (empty? profiles)
      (log-warn "No Zen profiles found; skipping workspace gradient de-colorization.")

      (zero? (:exit (sh! ["pgrep" "-f" "zen-bin$"] {:throw? false})))
      (log-warn "Zen Browser is currently running; skipping session patching to avoid race conditions. Quit Zen and re-run to apply.")

      dry-run
      (log-info "[DRY-RUN] Would neutralize non-gray Zen workspace gradient colors across all profiles.")

      (not (command-exists? "python3"))
      (log-warn "python3 not found; skipping mozLz4 workspace gradient patch.")

      :else
      (doseq [prof profiles]
        (let [session-file (fs/path prof "zen-sessions.jsonlz4")]
          (when (fs/exists? session-file)
            (let [py-script "
import ctypes, json, struct, sys, shutil, os
path = sys.argv[1]
MAGIC = b'mozLz40\\x00'
with open(path, 'rb') as f:
    raw = f.read()
if raw[:8] != MAGIC:
    sys.exit(0)
size = struct.unpack('<I', raw[8:12])[0]
try:
    lz4 = ctypes.CDLL('liblz4.so.1')
except Exception:
    sys.exit(0)
out = ctypes.create_string_buffer(size)
ret = lz4.LZ4_decompress_safe(raw[12:], out, len(raw) - 12, size)
if ret != size:
    sys.exit(0)
data = json.loads(out.raw[:ret])
patched = False
for space in data.get('spaces', []):
    for dot in space.get('theme', {}).get('gradientColors', []):
        if dot.get('c') != [25, 25, 25] or dot.get('lightness') != '15':
            dot['c'] = [25, 25, 25]
            dot['lightness'] = '15'
            patched = True
if not patched:
    sys.exit(0)
backup = path + '.bak-pre-monochrome'
if not os.path.exists(backup):
    shutil.copy2(path, backup)
body = json.dumps(data, separators=(',', ':')).encode('utf-8')
bound = lz4.LZ4_compressBound(len(body))
buf = ctypes.create_string_buffer(bound)
lz4.LZ4_compress_default.restype = ctypes.c_int
comp_size = lz4.LZ4_compress_default(body, buf, len(body), bound)
with open(path, 'wb') as f:
    f.write(MAGIC + struct.pack('<I', len(body)) + buf.raw[:comp_size])
print('patched')
"]
              (let [res (sh! ["python3" "-c" py-script (str session-file)] {:throw? false})]
                (if (= (str/trim (:out res)) "patched")
                  (log-success "Zen workspace gradients de-colorized for profile:" (str (fs/file-name prof)))
                  (log-info "Zen workspace gradients already dark monochrome for profile:" (str (fs/file-name prof))))))))))))

(defn open-zen-mods! [manifest]
  (if-not (command-exists? "zen-browser")
    (log-error "zen-browser executable not found in PATH.")
    (let [mods (get-in manifest [:browser :mods])]
      (println (str "Opening " (count mods) " Zen Mod install pages. Click 'Install' on each tab."))
      (println "Install 'Transparent Zen' first and enable its 'Allow transparency on linux' option.")
      (doseq [{:keys [name uuid]} mods]
        (println "  ->" name (str "(https://zen-browser.app/mods/" uuid "/)"))
        (sh! ["zen-browser" (str "https://zen-browser.app/mods/" uuid "/")] {:throw? false})
        (Thread/sleep 300)))))
