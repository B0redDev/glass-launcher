# Glass

An Android TV launcher in the style of tvOS 26 (Liquid Glass). Plain Java and framework Views with no
dependencies, so the APK is about 100 KB.

Built and tested on a Xiaomi MiTV-MOEU0 (Google TV 14, 1920×1080, 2.4 GB RAM). English UI with an Italian
translation.

## Install / update

```bash
TV=<tv-ip>:5555 ./install.sh
```

The script builds a release APK, installs it with `pm install` over ADB and opens Glass. It does not change
the TV's Home: until you do, the Home button still goes to the current launcher.

Requirements: ADB debugging enabled on the TV, JDK 17 (`JAVA_HOME`, default `~/opt/jdk-17*`), Android SDK
platform 34 (`ANDROID_HOME`, default `~/Android/Sdk`), `adb` (`ADB`, default `~/platform-tools/adb`).

## Home in rows

Top to bottom: **Favourites** (large), **Inputs** (HDMI ports with live status, plus the aerial tuner;
always shown), then **Streaming & TV**, **Tools**, **Games** and **Other**. Empty rows are hidden. The focused
row always moves under the title (row name + app name); the rows above it fade away, and the clock and
buttons are only shown on the first row.

Each app sits in exactly one row. On first launch, known apps start in the row listed in `App.START`; any
other app goes to Streaming & TV if it declares the "video" category, to Games if it declares "game", and to
Other otherwise. After that you decide with Move.

| Remote | Action |
|---|---|
| ←/→ | Scroll the row |
| ↑/↓ | Row above/below, on the closest item; ↑ from the first row reaches the network and settings buttons |
| OK | Open the app, or switch to the HDMI input / aerial |
| Hold OK | Menu: Move, Hide / Show, App info, Show hidden apps |
| Move → ←/→ | Reorder within the row (the tiles wiggle) |
| Move → ↑/↓ | Move the app to the row above/below (changes its category) |
| Move → OK / Back | Done |
| Back / Home while on Glass | Back to the start of the first row |

Rows, order and hidden apps are saved in the app's preferences (lost only when uninstalling it).

## Settings and themes

The gear button opens Glass's settings panel: the theme, and a shortcut to Android's own settings.

- **Light**: aurora wallpaper tinted by the focused app; focus zooms the tile and turns buttons white.
- **Dark** (after Nuvio OS): near-black with one cold light, no app colour; focus is a blue ring.

The choice applies immediately and is saved; the clock screensaver follows it too.

## Screensaver

TV Settings → System → Screensaver → "Glass · Clock", or:

```bash
adb shell settings put secure screensaver_components io.github.b0reddev.glass/.ClockDream
adb shell settings delete secure screensaver_components     # back to Google's
```

## Making it the Home app

Not done by the install script on purpose. On Google TV 14 the Home intent is resolved by priority and the
stock launcher wins, so you need to disable it, plus the setup wizard, which otherwise takes over Home and
crash-loops the system:

```bash
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

`pm enable` on both undoes it. If another launcher overrides Home through an accessibility service (e.g.
Projectivy), turn that off in its settings first. Background: [CleanYourTV's notes for a similar
Xiaomi](https://github.com/Gathin23/cleanyourtv/blob/main/examples/xiaomi-tv-a-pro-2026/debloat.txt).

## Source

| File | What it does |
|---|---|
| `Home.java` | Activity: rows, focus and scrolling, menus, move/hide, inputs row, network pill |
| `Row.java` | One row: name, cells, horizontal scroll, reorder animation |
| `Backdrop.java` | Aurora background + ambience of the focused app + blurred copy that glass panels sample |
| `GlassFrame.java` | Liquid Glass panel (buttons, input cards, menus) |
| `Tile.java` | App banner: rounded corners, focus zoom, gloss sweep |
| `App.java` | Installed apps, starting rows, saved rows/order/hidden apps, banner loading |
| `ClockDream.java` | Clock screensaver |
| `Theme.java` | The light and dark themes; every view reads the current one while drawing |
| `Swatch.java` | Theme preview in the settings panel |

Fonts: Inter (SIL Open Font License, `FONT-LICENSE-OFL.txt`), subset to Latin. Icons: Material Symbols
Rounded (Apache 2.0, `ICONS-LICENSE-APACHE.txt`).

## License

MIT, see [`LICENSE`](LICENSE). The bundled font and icons keep their own licenses (above).
