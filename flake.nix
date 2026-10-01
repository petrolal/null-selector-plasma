{
  description = "sanguine-node-rice - Monochrome Cyberpunk KDE Plasma 6 Rice Engine";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    let
      supportedSystems = [ "x86_64-linux" "aarch64-linux" ];
    in
    flake-utils.lib.eachSystem supportedSystems (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
        };

        ricePackages = with pkgs; [
          babashka
          git
          cmake
          extra-cmake-modules
          stow
          kdePackages.kvantum
          nerd-fonts.jetbrains-mono
          kdePackages.konsole
          kdePackages.dolphin
          kdePackages.kservice
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
      in
      {
        packages = {
          default = pkgs.stdenv.mkDerivation {
            pname = "sanguine-node-rice";
            version = "1.0.0";
            src = ./.;

            nativeBuildInputs = [ pkgs.makeWrapper ];
            buildInputs = [ pkgs.babashka ];

            installPhase = ''
              mkdir -p $out/bin $out/share/sanguine-node-rice
              cp -r . $out/share/sanguine-node-rice/
              makeWrapper ${pkgs.babashka}/bin/bb $out/bin/mono-rice \
                --add-flags "--config $out/share/sanguine-node-rice/bb.edn" \
                --run "cd $out/share/sanguine-node-rice"
            '';

            meta = with pkgs.lib; {
              description = "Monochrome Cyberpunk KDE Plasma 6 Rice Engine";
              homepage = "https://github.com/petrolal/sanguine-node-rice";
              license = licenses.mit;
              platforms = platforms.linux;
            };
          };

          plymouth-dotlock = pkgs.stdenv.mkDerivation {
            pname = "plymouth-theme-dotlock";
            version = "1.0.0";
            src = ./assets/plymouth-theme;

            installPhase = ''
              mkdir -p $out/share/plymouth/themes
              cp -r dotLock $out/share/plymouth/themes/
              cp -r dotLockG $out/share/plymouth/themes/
            '';
          };

          sddm-null-sector = pkgs.stdenv.mkDerivation {
            pname = "sddm-theme-null-sector";
            version = "1.0.0";
            src = ./assets/sddm-theme;

            installPhase = ''
              mkdir -p $out/share/sddm/themes/null-sector-sddm
              cp -r . $out/share/sddm/themes/null-sector-sddm/
            '';
          };
        };

        devShells.default = pkgs.mkShell {
          name = "sanguine-node-rice-dev";
          packages = ricePackages;
          shellHook = ''
            echo "=========================================================="
            echo " S A N G U I N E // N O D E - R I C E  (NixOS Environment)"
            echo "=========================================================="
            echo "Commands available:"
            echo "  * mono-rice [install | verify | theme | fetch | boot]"
            echo "  * bb test"
            echo ""
          '';
        };
      }) // {
        # NixOS System Configuration Module
        nixosModules.default = { config, lib, pkgs, ... }:
          with lib;
          let
            cfg = config.programs.sanguine-node-rice;
          in
          {
            options.programs.sanguine-node-rice = {
              enable = mkEnableOption "Enable sanguine-node-rice system configurations, Plymouth, SDDM, and tools";
              enablePlymouth = mkOption {
                type = types.bool;
                default = true;
                description = "Enable dotLock Plymouth boot splash theme";
              };
              enableSddm = mkOption {
                type = types.bool;
                default = true;
                description = "Enable null-sector-sddm theme for SDDM";
              };
            };

            config = mkIf cfg.enable {
              services.desktopManager.plasma6.enable = mkDefault true;
              services.displayManager.sddm = mkIf cfg.enableSddm {
                enable = mkDefault true;
                theme = "null-sector-sddm";
                package = pkgs.kdePackages.sddm;
                extraPackages = [ self.packages.${pkgs.system}.sddm-null-sector ];
              };

              boot.plymouth = mkIf cfg.enablePlymouth {
                enable = mkDefault true;
                theme = "dotLock";
                themePackages = [ self.packages.${pkgs.system}.plymouth-dotlock ];
              };

              fonts.packages = with pkgs; [
                nerd-fonts.jetbrains-mono
              ];

              environment.systemPackages = with pkgs; [
                babashka
                fastfetch
                starship
                fish
                cava
                kdePackages.kvantum
                ffmpeg
                rclone
                self.packages.${pkgs.system}.default
              ];
            };
          };

        nixosModules.sanguine-node-rice = self.nixosModules.default;

        # Home Manager Module
        homeManagerModules.default = { config, lib, pkgs, ... }:
          with lib;
          let
            cfg = config.programs.sanguine-node-rice;
          in
          {
            options.programs.sanguine-node-rice = {
              enable = mkEnableOption "Enable sanguine-node-rice user dotfiles and configurations";
            };

            config = mkIf cfg.enable {
              home.packages = with pkgs; [
                fastfetch
                starship
                fish
                cava
                kdePackages.kvantum
              ];

              xdg.configFile."fastfetch/config.jsonc".source = ./fastfetch/.config/fastfetch/config.jsonc;
              xdg.configFile."fastfetch/null_sector.txt".source = ./fastfetch/.config/fastfetch/null_sector.txt;
              xdg.configFile."fastfetch/arch.txt".source = ./fastfetch/.config/fastfetch/arch.txt;
              xdg.configFile."fastfetch/cachy.txt".source = ./fastfetch/.config/fastfetch/cachy.txt;
              xdg.configFile."fastfetch/nixos.txt".source = ./fastfetch/.config/fastfetch/nixos.txt;
              xdg.configFile."fastfetch/fedora.txt".source = ./fastfetch/.config/fastfetch/fedora.txt;
              xdg.configFile."starship.toml".source = ./starship/.config/starship.toml;
              xdg.configFile."fish/config.fish".source = ./fish/.config/fish/config.fish;
              xdg.configFile."cava/config".source = ./cava/.config/cava/config;
            };
          };

        homeManagerModules.sanguine-node-rice = self.homeManagerModules.default;
      };
}
