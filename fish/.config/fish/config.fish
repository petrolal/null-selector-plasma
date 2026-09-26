# ==============================================================================
# Fish Shell Configuration
# Rice: null-sector-plasma
# ==============================================================================

# Suppress default fish greeting
set -g fish_greeting

# Environment Defaults
set -gx EDITOR emacs
set -gx VISUAL emacs
set -gx PAGER less
set -gx LESS -R

# Path configurations
fish_add_path -g -p $HOME/hellmacs/bin $HOME/.local/bin

# SDKMAN Support (if present)
if test -d "$HOME/.sdkman"
    set -gx SDKMAN_DIR "$HOME/.sdkman"
end

# Starship Prompt Initialization
if type -q starship
    starship init fish | source
end

# Fastfetch Banner (Interactive login shell only, avoiding duplicate banners in subshells)
if status is-interactive
    if not set -q FASTFETCH_RUN
        set -gx FASTFETCH_RUN 1
        if type -q fastfetch
            fastfetch
        end
    end
end
