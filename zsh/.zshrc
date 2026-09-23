# ==============================================================================
# Zsh Configuration
# Rice: null-sector-plasma
# ==============================================================================

# Ensure ~/.cache/zsh exists for clean history storage
[[ ! -d "${XDG_CACHE_HOME:-$HOME/.cache}/zsh" ]] && mkdir -p "${XDG_CACHE_HOME:-$HOME/.cache}/zsh"

# History Configuration
HISTFILE="${XDG_CACHE_HOME:-$HOME/.cache}/zsh/history"
HISTSIZE=50000
SAVEHIST=50000
setopt EXTENDED_HISTORY          # Write the history file in the ':start:elapsed;command' format
setopt HIST_EXPIRE_DUPS_FIRST    # Expire duplicate entries first when trimming history
setopt HIST_IGNORE_DUPS          # Do not record an event that was just recorded again
setopt HIST_IGNORE_ALL_DUPS      # Delete old event if new event is a duplicate
setopt HIST_FIND_NO_DUPS         # Do not display a line previously found
setopt HIST_IGNORE_SPACE         # Do not record an event starting with a space
setopt HIST_SAVE_NO_DUPS         # Do not write duplicate events to history
setopt SHARE_HISTORY             # Share history between all sessions

# General Zsh Options
setopt AUTO_CD                   # Type directory name to cd
setopt AUTO_PUSHD                # Push the current directory visited on the stack
setopt PUSHD_IGNORE_DUPS         # Do not store duplicates in the stack
setopt INTERACTIVE_COMMENTS      # Allow comments even in interactive shells
setopt NO_BEEP                   # Disable bell

# Completion System
autoload -Uz compinit
zstyle ':completion:*' menu select
zstyle ':completion:*' matcher-list 'm:{a-zA-Z}={A-Za-z}' 'r:|=*' 'l:|=* r:|=*'
zstyle ':completion:*' list-colors "${(s.:.)LS_COLORS}"
compinit -d "${XDG_CACHE_HOME:-$HOME/.cache}/zsh/zcompdump-$ZSH_VERSION"

# Environment Defaults
export EDITOR="emacs"
export VISUAL="emacs"
export PAGER="less"
export LESS="-R"

# Plugins (Arch Linux package locations)
# zsh-syntax-highlighting
if [[ -f /usr/share/zsh/plugins/zsh-syntax-highlighting/zsh-syntax-highlighting.zsh ]]; then
    source /usr/share/zsh/plugins/zsh-syntax-highlighting/zsh-syntax-highlighting.zsh
elif [[ -f /usr/share/zsh-syntax-highlighting/zsh-syntax-highlighting.zsh ]]; then
    source /usr/share/zsh-syntax-highlighting/zsh-syntax-highlighting.zsh
fi

# zsh-autosuggestions
if [[ -f /usr/share/zsh/plugins/zsh-autosuggestions/zsh-autosuggestions.zsh ]]; then
    source /usr/share/zsh/plugins/zsh-autosuggestions/zsh-autosuggestions.zsh
elif [[ -f /usr/share/zsh-autosuggestions/zsh-autosuggestions.zsh ]]; then
    source /usr/share/zsh-autosuggestions/zsh-autosuggestions.zsh
fi

# Load Aliases
[[ -f "$HOME/.config/zsh/aliases.zsh" ]] && source "$HOME/.config/zsh/aliases.zsh"

# Starship Prompt Initialization
if command -v starship >/dev/null 2>&1; then
    eval "$(starship init zsh)"
fi

# Fastfetch Banner (Interactive login shell only, avoiding tmux/subshell spam)
if [[ -o interactive ]] && command -v fastfetch >/dev/null 2>&1; then
    if [[ -z "$FASTFETCH_RUN" ]]; then
        export FASTFETCH_RUN=1
        fastfetch
    fi
fi

#THIS MUST BE AT THE END OF THE FILE FOR SDKMAN TO WORK!!!
export SDKMAN_DIR="$HOME/.sdkman"
[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"

export PATH="$HOME/.local/bin:$PATH"
