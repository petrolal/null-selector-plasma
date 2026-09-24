# Maintainer: petrolal <petrolal@github.com>
pkgname=null-sector-plasma-git
pkgver=1.0.0.r0.g$(git rev-parse --short HEAD 2>/dev/null || echo "0000000")
pkgrel=1
pkgdesc="Automated cyberpunk & NieR: Automata KDE Plasma 6 desktop rice engine"
arch=('any')
url="https://github.com/petrolal/null-sector-plasma"
license=('GPL-3.0-or-later')
depends=(
    'babashka'
    'plasma-desktop'
    'kwriteconfig6'
    'stow'
    'kvantum'
    'konsole'
    'ttf-jetbrains-mono-nerd'
)
optdepends=(
    'cava: Audio visualizer'
    'starship: Shell prompt'
    'fastfetch: System information fetch'
    'yamis-icon-theme-git: Monochrome icon theme'
    'bibata-cursor-git: Bibata cursor theme'
    'plasma6-applets-panel-colorizer: Dynamic panel blur & coloring'
    'plasma6-applets-kurve-git: Rounded panel corners'
    'plasma6-applets-kde-control-station: Quick settings control station'
    'plasma6-wallpapers-smart-video-wallpaper-reborn: Video wallpaper engine'
    'kwin-effects-better-blur-dx: Translucent window blur effect'
    'zen-browser-bin: Translucent browser profile'
)
source=("git+https://github.com/petrolal/null-sector-plasma.git")
md5sums=('SKIP')

pkgver() {
    cd "${srcdir}/${pkgname%-git}" 2>/dev/null || cd "${srcdir}/null-sector-plasma"
    printf "1.0.0.r%s.g%s" "$(git rev-list --count HEAD)" "$(git rev-parse --short HEAD)"
}

package() {
    cd "${srcdir}/${pkgname%-git}" 2>/dev/null || cd "${srcdir}/null-sector-plasma"

    # Install repository payload into /usr/share/null-sector-plasma
    install -dm755 "${pkgdir}/usr/share/null-sector-plasma"
    cp -r * "${pkgdir}/usr/share/null-sector-plasma/"

    # Install binary launcher to /usr/bin/mono-rice
    install -dm755 "${pkgdir}/usr/bin"
    cat << 'EOF' > "${pkgdir}/usr/bin/mono-rice"
#!/usr/bin/env bash
REPO_DIR="/usr/share/null-sector-plasma"
if [ -d "$HOME/.null-sector-plasma" ]; then
    REPO_DIR="$HOME/.null-sector-plasma"
elif [ -d "$PWD/src/mono_rice" ] && [ -f "$PWD/rice.edn" ]; then
    REPO_DIR="$PWD"
fi
exec bb --config "${REPO_DIR}/bb.edn" -m mono-rice.core "$@"
EOF
    chmod 755 "${pkgdir}/usr/bin/mono-rice"
}
