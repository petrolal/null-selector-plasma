# ==============================================================================
# Fish Aliases & Helpers
# Rice: null-sector-plasma
# ==============================================================================

# File listing (eza if available, otherwise ls with colors)
if type -q eza
    alias ls='eza --icons --group-directories-first'
    alias ll='eza -lh --icons --group-directories-first'
    alias la='eza -lah --icons --group-directories-first'
    alias lt='eza --tree --level=2 --icons'
else
    alias ls='ls --color=auto --group-directories-first'
    alias ll='ls -lh --color=auto'
    alias la='ls -lah --color=auto'
end

# File reading (bat if available, otherwise cat)
if type -q bat
    alias cat='bat --paging=never --style=plain'
    alias batdiff='git diff --name-only --relative | xargs bat --diff'
end

# Package Management shortcuts
alias update='sudo pacman -Syu'
if type -q yay
    alias yup='yay -Syu'
else if type -q paru
    alias pup='paru -Syu'
end

# Git shortcuts
alias gs='git status -sb'
alias ga='git add'
alias gc='git commit -m'
alias gp='git push'
alias gl='git log --oneline --graph --decorate -n 15'
alias gd='git diff'

# System & Rice Maintenance
alias ff='fastfetch'
function reload-plasma -d "Reload KDE Plasma Shell"
    if not systemctl --user restart plasma-plasmashell.service 2>/dev/null
        kquitapp6 plasmashell 2>/dev/null
        kstart plasmashell >/dev/null 2>&1 &
    end
end
alias reload-kwin='qdbus6 org.kde.KWin /KWin reconfigure'
alias font-refresh='fc-cache -fv'
