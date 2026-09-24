(ns mono-rice.proc
  (:require [babashka.process :as p]
            [clojure.string :as str]))

;; -----------------------------------------------------------------------------
;; ANSI Colors & Logging
;; -----------------------------------------------------------------------------

(def ^:dynamic *colors*
  (not (or (System/getenv "NO_COLOR")
           (nil? (System/console)))))

(def color-codes
  {:bold   "\033[1m"
   :reset  "\033[0m"
   :cyan   "\033[0;36m"
   :green  "\033[0;32m"
   :yellow "\033[0;33m"
   :red    "\033[0;31m"
   :blue   "\033[0;34m"
   :purple "\033[0;35m"})

(defn colorize [k text]
  (if *colors*
    (str (get color-codes k "") text (get color-codes :reset ""))
    text))

(defn log-info [& args]
  (println (colorize :cyan "[INFO]") (str/join " " args)))

(defn log-success [& args]
  (println (colorize :green "[OK]") (str/join " " args)))

(defn log-warn [& args]
  (println (colorize :yellow "[WARN]") (str/join " " args)))

(defn log-error [& args]
  (binding [*out* *err*]
    (println (colorize :red "[ERROR]") (str/join " " args))))

(defn log-step [& args]
  (println)
  (println (colorize :bold (colorize :purple (str "==> " (str/join " " args))))))

;; -----------------------------------------------------------------------------
;; Process Utilities
;; -----------------------------------------------------------------------------

(defn command-exists? [cmd]
  (let [{:keys [exit]} (apply p/shell {:continue true :out :string :err :string} ["which" (name cmd)])]
    (zero? exit)))

(defn ask-confirm?
  "Prompts user for yes/no confirmation. Returns true on yes or if auto-yes."
  ([prompt] (ask-confirm? prompt {}))
  ([prompt {:keys [auto-yes yes]}]
   (if (or auto-yes yes)
     true
     (if (nil? (System/console))
       true
       (do
         (print (colorize :yellow (str prompt " [y/N]: ")))
         (flush)
         (let [resp (read-line)]
           (boolean (re-matches #"^[yY]([eE][sS])?$" (str/trim (or resp ""))))))))))

(defn sh!
  "Executes a system process with optional :sudo, :dry-run, :dir, :env, :throw?, :out, :err.
   Returns a map with :exit, :out, :err."
  [cmd & [{:keys [sudo dry-run dir env throw? out err]
           :or   {throw? true out :string err :string}}]]
  (let [cmd-vec   (if (sequential? cmd) (mapv str cmd) (vec (str/split (str cmd) #"\s+")))
        final-cmd (cond->> cmd-vec
                    sudo (into ["sudo"]))
        cmd-str   (str/join " " final-cmd)]
    (if dry-run
      (do
        (log-info "[DRY-RUN] Would run:" cmd-str)
        {:exit 0 :out "" :err ""})
      (let [result (apply p/shell {:dir dir
                                   :env env
                                   :out out
                                   :err err
                                   :continue true}
                          final-cmd)]
        (if (and (not (zero? (:exit result))) throw?)
          (throw (ex-info (str "Command failed: " cmd-str)
                          {:exit (:exit result)
                           :err  (:err result)
                           :cmd  final-cmd}))
          result)))))
