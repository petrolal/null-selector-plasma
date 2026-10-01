{ pkgs ? import <nixpkgs> { config.allowUnfree = true; } }:

pkgs.mkShell {
  name = "sanguine-node-rice-shell";

  buildInputs = with pkgs; [
    babashka
    git
    cmake
    extra-cmake-modules
    stow
    kdePackages.kvantum
    nerd-fonts.jetbrains-mono
    kdePackages.konsole
    kdePackages.dolphin
    cava
    ffmpeg
    starship
    fastfetch
    fish
    discord
    emacs
    kdePackages.kdenlive
    kdePackages.gwenview
    haruna
    rclone
  ];

  shellHook = ''
    echo "=========================================================="
    echo " S A N G U I N E // N O D E - R I C E  (Nix Shell)"
    echo "=========================================================="
    echo "Run './mono-rice install' or 'bb test' to manage the rice."
    echo ""
  '';
}
