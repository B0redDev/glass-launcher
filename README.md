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

### Signing key

Builds are signed with the build machine's Android debug keystore (`~/.android/debug.keystore`), and Android
installs an update only over an app signed with the same certificate. The certificate's SHA-256 fingerprint,
to check a key with `keytool -list -v -keystore <file> -storepass android`:

```
02:BE:39:B6:93:AF:3F:7A:14:E1:82:FA:7E:48:E3:81:02:FF:51:6C:DF:DA:9B:9E:C8:06:3C:0A:87:FB:9B:7D
```

Keep a copy of that file outside the build machine, and never in this repository.

If `install.sh` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, restore the original keystore to
`~/.android/debug.keystore` and run it again. **Do not uninstall Glass to get past it**: that erases the rows,
their order, the hidden apps and the theme (unless `install.sh` has a copy of them, see below).

### Layout backup

Glass keeps a plain-text copy of its layout (rows and their order, hidden apps, theme) in
`/sdcard/Android/data/io.github.b0reddev.glass/files/layout.txt`, rewritten whenever the layout changes.
Before every install, `install.sh` copies it to `backups/<tv-ip>-layout.txt` on the PC; after a reinstall that
left the TV without it, `install.sh` pushes it back and Glass imports it on its first start. A normal update
never imports it. `backups/` is git-ignored: it lists your apps.

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
| ←/→ | Scroll the row; it stops at either end instead of jumping to another row |
| ↑/↓ | Row above/below, on the closest item; ↑ from the first row reaches the buttons at the top right |
| OK | Open the app, or switch to the HDMI input / aerial |
| Hold OK | Menu: Move, Hide / Show, App info, Show hidden apps |
| Move → ←/→ | Reorder within the row (the tiles wiggle) |
| Move → ↑/↓ | Move the app to the row above/below (changes its category) |
| Move → OK / Back | Done |
| Back / Home while on Glass | Back to the start of the first row |

Rows, order and hidden apps are saved in the app's preferences (lost only when uninstalling it).

## Top bar

Icons only, updated live as the network changes:

- **VPN** (shown while online, when an app on the TV provides a VPN): a solid green badge with a shield while a
  VPN is up, solid red with a crossed shield while it is down, as CleanYourTV's badge; readable on any
  backdrop. OK opens the VPN app; with several, a menu to pick one.
- **Network**: Wi-Fi, Ethernet or offline. OK opens the Wi-Fi settings.
- **Gear**: Glass's settings (below).

## Settings and themes

The gear button opens Glass's settings panel: the theme, and a shortcut to Android's own settings.

- **Light**: aurora wallpaper tinted by the focused app; focus zooms the tile and turns buttons white. The tint
  and the glass are darkened enough for white text to stay readable (WCAG AA) over bright banners.
- **Dark** (after Nuvio OS): near-black with one cold light, no app colour; focus is a blue ring.
- **Automatic** (after Free My TV): Light from sunrise, Dark from sunset. The times are worked out on the TV for
  the main city of its time zone (IANA `zone.tab` coordinates), with no network and no location permission; the
  panel shows the next switch. It happens live, and again on returning Home after standby.

The choice applies immediately and is saved; the clock screensaver follows the look it starts with.

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
| `Theme.java` | The light and dark themes and the saved choice; every view reads the current one while drawing |
| `Sun.java` | Sunrise and sunset for the Automatic theme (tested in `src/test`) |
| `Swatch.java` | Theme preview in the settings panel |

Fonts: Inter (SIL Open Font License, `FONT-LICENSE-OFL.txt`), subset to Latin. Icons: Material Symbols
Rounded (Apache 2.0, `ICONS-LICENSE-APACHE.txt`).

## License

MIT, see [`LICENSE`](LICENSE). The bundled font and icons keep their own licenses (above).
