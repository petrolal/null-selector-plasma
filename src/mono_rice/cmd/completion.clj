(ns mono-rice.cmd.completion
  "Shell auto-completion generator for Zsh, Bash, and Fish shells."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [mono-rice.fs :as fs]
            [mono-rice.proc :as proc]))

(def commands
  [{:cmd "install"       :desc "Full idempotent deployment of configs, themes, and extensions"}
   {:cmd "harvest"       :desc "Scrape live configurations from $HOME into repo tree"}
   {:cmd "backup"        :desc "Create timestamped configuration snapshot in ~/.config/mono-rice/backups"}
   {:cmd "rollback"      :desc "Inspect snapshots and restore live configuration states"}
   {:cmd "diff"          :desc "Visual line-by-line diff between repository templates and live $HOME"}
   {:cmd "verify"        :desc "Validate system requirements, dependencies, and plasmoid layouts"}
   {:cmd "theme"         :desc "Inspect, preview, and dynamically switch complete desktop theme profiles"}
   {:cmd "shortcut"      :desc "Declarative KDE hotkeys and application shortcut orchestrator"}
   {:cmd "zen"           :desc "Zen Browser enterprise policies, mods, and translucency manager"}
   {:cmd "panel"         :desc "Panel Colorizer segmented capsule presets manager"}
   {:cmd "kwin"          :desc "KWin window rules, transparency, borderless, and blur tuning"}
   {:cmd "fetch"         :desc "Fastfetch ASCII aesthetic logo selector"}
   {:cmd "profile"       :desc "Hardware profiler and form factor optimization tuner"}
   {:cmd "sync"          :desc "Remote Git dotfile hub for pull/push synchronization"}
   {:cmd "boot"          :desc "SDDM display manager & Plymouth boot splash theme orchestrator"}
   {:cmd "wallpaper"     :desc "Wallpaper & 4K video lockscreen wallpaper synchronizer"}
   {:cmd "audio"         :desc "CAVA audio visualizer equalizer presets manager"}
   {:cmd "watch"         :desc "Configuration drift sentinel daemon"}
   {:cmd "daemon"        :desc "Manage systemd user service for background drift monitoring"}
   {:cmd "bundle"        :desc "Export or import portable compressed dotfile bundles"}
   {:cmd "plasmoid"      :desc "Inspect and install custom KDE Plasma 6 desktop applets"}
   {:cmd "doctor"        :desc "Deep diagnostic health checks & auto-repair engine"}
   {:cmd "tui"           :desc "Launch interactive Cyberpunk terminal dashboard"}
   {:cmd "completion"    :desc "Generate shell auto-completions (zsh, bash, fish)"}
   {:cmd "vault"         :desc "Security audit, secret scanner & dotfile sanitizer"}
   {:cmd "event"         :desc "DBus desktop event listener & dynamic display sentinel"}
   {:cmd "dump-widgets"  :desc "Dump active desktop applets containment tree"}
   {:cmd "open-zen-mods" :desc "Open all Zen Mod install URLs in browser"}])

(defn generate-zsh-completion []
  (let [entries (map (fn [{:keys [cmd desc]}]
                       (format "    '%s:%s'" cmd (str/replace desc "'" "\\'")))
                     commands)]
    (str/join
     "\n"
     ["#compdef mono-rice bb"
      ""
      "_mono_rice() {"
      "  local curcontext=\"$curcontext\" state line"
      "  typeset -A opt_args"
      ""
      "  _arguments -C \\"
      "    '(-n --dry-run)'{-n,--dry-run}'[Simulate execution without modifying system]' \\"
      "    '(-v --verbose)'{-v,--verbose}'[Enable verbose telemetry logging]' \\"
      "    '(-h --help)'{-h,--help}'[Display help information]' \\"
      "    '1: :->command' \\"
      "    '*: :->args'"
      ""
      "  case $state in"
      "    command)"
      "      local -a subcommands"
      "      subcommands=("
      (str/join "\n" entries)
      "      )"
      "      _describe -t subcommands 'mono-rice commands' subcommands"
      "      ;;"
      "    args)"
      "      case $words[2] in"
      "        theme)"
      "          _values 'theme subcommands' 'list' 'set' 'apply' 'preview'"
      "          ;;"
      "        boot)"
      "          _values 'boot subcommands' 'status' 'list' 'preview-sddm' 'apply-sddm' 'apply-plymouth'"
      "          ;;"
      "        panel)"
      "          _values 'panel subcommands' 'list' 'set' 'reload'"
      "          ;;"
      "        shortcut)"
      "          _values 'shortcut subcommands' 'list' 'apply' 'export'"
      "          ;;"
      "        zen)"
      "          _values 'zen subcommands' 'status' 'sync-css' 'list-mods' 'open-mods' 'inject-policies'"
      "          ;;"
      "        kwin)"
      "          _values 'kwin subcommands' 'rules' 'apply-rules' 'set-blur'"
      "          ;;"
      "        fetch)"
      "          _values 'fetch subcommands' 'list' 'set' 'preview'"
      "          ;;"
      "        profile)"
      "          _values 'profile subcommands' 'detect' 'apply'"
      "          ;;"
      "        sync)"
      "          _values 'sync subcommands' 'status' 'pull' 'push'"
      "          ;;"
      "        vault)"
      "          _values 'vault subcommands' 'scan' 'sanitize' 'restore'"
      "          ;;"
      "        event)"
      "          _values 'event subcommands' 'listen' 'emit'"
      "          ;;"
      "        rollback)"
      "          _values 'rollback subcommands' 'list' 'restore' 'purge'"
      "          ;;"
      "      esac"
      "      ;;"
      "  esac"
      "}"
      ""
      "_mono_rice \"$@\""])))

(defn generate-bash-completion []
  (let [cmds (str/join " " (map :cmd commands))]
    (str/join
     "\n"
     ["#!/usr/bin/env bash"
      "# Bash completion for mono-rice"
      ""
      "_mono_rice_completions() {"
      "  local cur prev opts"
      "  COMPREPLY=()"
      "  cur=\"${COMP_WORDS[COMP_CWORD]}\""
      "  prev=\"${COMP_WORDS[COMP_CWORD-1]}\""
      (format "  opts=\"%s --dry-run --verbose --help\"" cmds)
      ""
      "  case \"${prev}\" in"
      "    theme)"
      "      COMPREPLY=( $(compgen -W \"list set apply preview\" -- ${cur}) )"
      "      return 0 ;;"
      "    boot)"
      "      COMPREPLY=( $(compgen -W \"status list preview-sddm apply-sddm apply-plymouth\" -- ${cur}) )"
      "      return 0 ;;"
      "    panel)"
      "      COMPREPLY=( $(compgen -W \"list set reload\" -- ${cur}) )"
      "      return 0 ;;"
      "    vault)"
      "      COMPREPLY=( $(compgen -W \"scan sanitize restore\" -- ${cur}) )"
      "      return 0 ;;"
      "    sync)"
      "      COMPREPLY=( $(compgen -W \"status pull push\" -- ${cur}) )"
      "      return 0 ;;"
      "    *)"
      "      ;;"
      "  esac"
      ""
      "  if [[ ${COMP_CWORD} -eq 1 ]] ; then"
      "    COMPREPLY=( $(compgen -W \"${opts}\" -- ${cur}) )"
      "    return 0"
      "  fi"
      "}"
      ""
      "complete -F _mono_rice_completions mono-rice"
      "complete -F _mono_rice_completions bb"])))

(defn generate-fish-completion []
  (let [entries (map (fn [{:keys [cmd desc]}]
                       (format "complete -c mono-rice -n '__fish_use_subcommand' -a '%s' -d '%s'"
                               cmd (str/replace desc "'" "\\'")))
                     commands)]
    (str/join
     "\n"
     (concat
      ["# Fish completion for mono-rice"
       "function __fish_mono_rice_needs_command"
       "  set cmd (commandline -opc)"
       "  if [ (count $cmd) -eq 1 ]"
       "    return 0"
       "  end"
       "  return 1"
       "end"
       ""]
      entries
      ["complete -c mono-rice -s n -l dry-run -d 'Preview execution without changing system'"
       "complete -c mono-rice -s v -l verbose -d 'Enable verbose telemetry logging'"
       "complete -c mono-rice -s h -l help -d 'Show help'"]))))

(defn install-completion
  "Install shell completion file to appropriate user path."
  [shell {:keys [dry-run] :as opts}]
  (let [target-info (case (keyword shell)
                      :zsh  {:path "~/.zsh/completions/_mono-rice" :content (generate-zsh-completion)}
                      :bash {:path "~/.bash_completion.d/mono-rice" :content (generate-bash-completion)}
                      :fish {:path "~/.config/fish/completions/mono-rice.fish" :content (generate-fish-completion)}
                      nil)]
    (if-not target-info
      (do
        (proc/log-warn (format "Unsupported shell: %s. Choose: zsh, bash, fish" shell))
        false)
      (let [target-path (fs/expand-home (:path target-info))
            parent-dir (.getParent (io/file target-path))]
        (println (format "==> Installing %s completion -> %s" (str/upper-case (name shell)) target-path))
        (if dry-run
          (do
            (proc/log-info (format "[DRY-RUN] Would write completion script to %s" target-path))
            true)
          (do
            (.mkdirs (io/file parent-dir))
            (spit target-path (:content target-info))
            (proc/log-ok (format "%s completion script installed." (str/upper-case (name shell))))
            true))))))

(defn generate
  "Print or install auto-completion scripts for shell."
  [shell-str {:keys [install dry-run] :as opts}]
  (let [shell (keyword (or shell-str "zsh"))]
    (if install
      (install-completion shell opts)
      (case shell
        :zsh  (do (println (generate-zsh-completion)) true)
        :bash (do (println (generate-bash-completion)) true)
        :fish (do (println (generate-fish-completion)) true)
        (do
          (proc/log-warn (format "Unknown shell: %s. Use zsh, bash, or fish" shell-str))
          false)))))
