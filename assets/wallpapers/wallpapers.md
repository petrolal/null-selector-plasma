# 🎥 Live Video Wallpapers

This rice is designed to use **Smart Video Wallpaper Reborn** (`plasma6-wallpapers-smart-video-wallpaper-reborn`) for animated desktop and lockscreen backgrounds.

### Desktop Live Wallpaper: Infernal Biopunk
- **File:** `assets/wallpapers/infernal_naked_girl_biopunk.mp4`
- **Static Fallback:** `assets/wallpapers/infernal_naked_girl_biopunk.png`
- **Target:** Plasma 6 Desktop animated live wallpaper

### Lockscreen & Login Screen Wallpaper: Infernal Eyes
- **File:** `assets/wallpapers/infernal_eyes.mp4`
- **Static Fallback:** `assets/wallpapers/infernal_eyes.png`
- **Target:** KDE Screen Locker (`kscreenlockerrc`), Plasma Login Manager (`plasmalogin.conf`), and SDDM (`theme.conf`)

### Legacy / Alternative Wallpapers
- **Digital Gaze:** `assets/wallpapers/digital-gaze.mp4` ([DesktopHut Digital Gaze](https://www.desktophut.com/digital-gaze-8642))
- **Synthwave Dreamwave Girl:** `assets/wallpapers/synthwave_dreamwave_girl.mp4`

### Automatic Download Command
To download the 4K live video wallpaper locally to `~/.local/share/wallpapers/`:
```bash
mkdir -p ~/.local/share/wallpapers
curl -L -o ~/.local/share/wallpapers/digital-gaze.mp4 "https://www.desktophut.com/files/bB8IrWshPO-Sequence04Htty2Prob4.mp4"
```
