# ==============================================================================
# Zsh Aliases & Helpers
# Rice: null-sector-plasma
# ==============================================================================

# File listing (eza if available, otherwise ls with colors)
if command -v eza >/dev/null 2>&1; then
    alias ls='eza --icons --group-directories-first'
    alias ll='eza -lh --icons --group-directories-first'
    alias la='eza -lah --icons --group-directories-first'
    alias lt='eza --tree --level=2 --icons'
else
    alias ls='ls --color=auto --group-directories-first'
    alias ll='ls -lh --color=auto'
    alias la='ls -lah --color=auto'
fi

# File reading (bat if available, otherwise cat)
if command -v bat >/dev/null 2>&1; then
    alias cat='bat --paging=never --style=plain'
    alias batdiff='git diff --name-only --relative | xargs bat --diff'
fi

# Package Management shortcuts
alias update='sudo pacman -Syu'
if command -v yay >/dev/null 2>&1; then
    alias yup='yay -Syu'
elif command -v paru >/dev/null 2>&1; then
    alias pup='paru -Syu'
fi

# Git shortcuts
alias gs='git status -sb'
alias ga='git add'
alias gc='git commit -m'
alias gp='git push'
alias gl='git log --oneline --graph --decorate -n 15'
alias gd='git diff'

# System & Rice Maintenance
alias ff='fastfetch'
alias reload-plasma='systemctl --user restart plasma-plasmashell.service 2>/dev/null || (kquitapp6 plasmashell 2>/dev/null; kstart plasmashell >/dev/null 2>&1 &)'
alias reload-kwin='qdbus6 org.kde.KWin /KWin reconfigure'
alias font-refresh='fc-cache -fv'
