# 🌐 Zen Browser Monochrome Transparency Setup

To replicate the translucent, blurred glass look for Zen Browser:

### 1. userChrome.css
Place `userChrome.css` inside your Zen Browser profile directory under `chrome/`:
```css
#browser {background-color: #40404066;}
```
*(Automated by `install.sh`)*

### 2. KWin Force Blur
Ensure KWin blur or `kwin-effects-forceblur` is active:
- Add `zen` to the forced blur window classes list in System Settings -> Window Management -> Desktop Effects -> Blur.
- Blur strength: 4, Noise strength: 5, Brightness: 25%, Saturation: 0%, Contrast: 105%.

### 3. Recommended Zen Mods & Extensions
- **Mods (Zen Theme Store):**
  - Transparent Zen (v1.17.16) - enable "Allow transparency" and "Allow transparency on linux"
  - Animations Plus
  - Audio Indicator Enhanced
  - Better Find Bar
  - Floating History & Floating Status Bar
  - Load Bar
- **Firefox Extensions:**
  - **Bonjourr:** Minimalist new tab page with greeting & search bar
  - **Dark Reader:** Forces dark theme across all websites
  - **Zen Internet:** Works with Dark Reader and Transparent Zen for webpage blur
