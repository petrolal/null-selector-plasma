{ pkgs ? import <nixpkgs> { config.allowUnfree = true; } }:

pkgs.stdenv.mkDerivation {
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
}
