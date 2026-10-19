# Glass

Launcher per Android TV in stile tvOS 26 (Liquid Glass). Java + View di sistema, nessuna dipendenza: APK ~100 KB.

## Installare / aggiornare

```bash
./install.sh                 # TV=192.168.0.26:5555 di default; TV=<ip>:5555 ./install.sh per un'altra
```

Compila, installa con `pm install`, concede la posizione (serve solo per mostrare il nome della rete Wi-Fi)
e apre Glass. Non tocca la Home: finché non la cambi, il tasto Home porta ancora a Projectivy.

Requisiti sul PC: JDK 17 in `~/opt/jdk-17*`, Android SDK in `~/Android/Sdk`, adb in `~/platform-tools`.

## Uso

| Telecomando | Effetto |
|---|---|
| Frecce | Muovono il focus; la griglia scorre una riga alla volta |
| OK | Apre l'app |
| OK tenuto premuto | Menu: Sposta, Nascondi / Mostra, Info app, Mostra app nascoste |
| Sposta → frecce → OK | Riordina (le icone oscillano); Indietro conferma anche lui |
| Su dalla prima riga | Pulsanti in alto: Ingressi (HDMI, TV), Wi-Fi, Impostazioni |
| Indietro / Home su Glass | Torna alla prima riga |

Ordine e app nascoste restano salvati nell'app (si perdono solo disinstallandola).

## Salvaschermo

Impostazioni della TV → Sistema → Salvaschermo → "Glass · Orologio", oppure:

```bash
adb shell settings put secure screensaver_components dev.matteo.glass/.ClockDream
adb shell settings delete secure screensaver_components     # torna a quello di Google
```

## Renderlo la Home

Non fatto di proposito. Quando vorrai, su questo firmware (Xiaomi V816, Google TV 14) servirà:
disattivare l'override Home di Projectivy (sue impostazioni), poi
`pm disable-user --user 0 com.google.android.tungsten.setupwraith` e
`pm disable-user --user 0 com.google.android.apps.tv.launcherx`
(vedi `~/cleanyourtv/examples/xiaomi-tv-a-pro-2026/debloat.txt` per il perché e il ripristino).

## File

| File | Cosa fa |
|---|---|
| `Home.java` | Activity: layout, focus e scorrimento, menu, sposta/nascondi, ingressi, rete |
| `Backdrop.java` | Sfondo aurora + alone dell'app a fuoco + copia sfocata per il vetro |
| `GlassFrame.java` | Pannello Liquid Glass (pulsanti, menu) |
| `Tile.java` | Banner dell'app: angoli arrotondati, zoom, riflesso, nome |
| `Grid.java` | Griglia a 5 colonne con animazione di riordino |
| `App.java` | Elenco app, ordine e app nascoste salvati, caricamento banner |
| `ClockDream.java` | Salvaschermo orologio |

Font: Inter (SIL OFL, `FONT-LICENSE-OFL.txt`), ridotto ai caratteri latini. Icone: Material Symbols Rounded (Apache 2.0).
