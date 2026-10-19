# Glass

Launcher per Android TV in stile tvOS 26 (Liquid Glass). Java + View di sistema, nessuna dipendenza: APK ~100 KB.

## Installare / aggiornare

```bash
./install.sh                 # TV=192.168.0.26:5555 di default; TV=<ip>:5555 ./install.sh per un'altra
```

Compila, installa con `pm install`, concede la posizione (serve solo per mostrare il nome della rete Wi-Fi)
e apre Glass. Non tocca la Home: finché non la cambi, il tasto Home porta ancora a Projectivy.

Requisiti sul PC: JDK 17 in `~/opt/jdk-17*`, Android SDK in `~/Android/Sdk`, adb in `~/platform-tools`.

## Home a righe

Dall'alto: **Preferite** (grandi), **Sorgenti** (HDMI con stato e TV antenna, sempre presente), poi
**Streaming e TV**, **Strumenti**, **Giochi**, **Altro**. Le righe vuote non si vedono. La riga a fuoco sale
sempre sotto il titolo (nome della riga + nome dell'app); quelle sopra spariscono, ora e pulsanti in alto
restano solo sulla prima.

Ogni app sta in una sola riga. Al primo avvio le app note partono nella riga scelta nella proposta di
design (`App.START`); le altre vanno in Streaming se dichiarano "video", in Giochi se dichiarano "gioco",
altrimenti in Altro. Da lì decidi tu con Sposta.

| Telecomando | Effetto |
|---|---|
| ←/→ | Scorre la riga |
| ↑/↓ | Riga sopra/sotto, sull'elemento più vicino; ↑ dalla prima riga va a Wi-Fi e Impostazioni |
| OK | Apre l'app, o passa all'ingresso HDMI / TV |
| OK tenuto premuto | Menu: Sposta, Nascondi / Mostra, Info app, Mostra app nascoste |
| Sposta → ←/→ | Riordina nella riga (le icone oscillano) |
| Sposta → ↑/↓ | Porta l'app nella riga sopra/sotto (cambia categoria) |
| Sposta → OK / Indietro | Conferma |
| Indietro / Home su Glass | Torna all'inizio della prima riga |

Righe, ordine e app nascoste restano salvati nell'app (si perdono solo disinstallandola).

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
| `Home.java` | Activity: righe, focus e scorrimento, menu, sposta/nascondi, riga Sorgenti, rete |
| `Row.java` | Una riga: nome, celle, scorrimento orizzontale, animazione di riordino |
| `Backdrop.java` | Sfondo aurora + alone dell'app a fuoco + copia sfocata per il vetro |
| `GlassFrame.java` | Pannello Liquid Glass (pulsanti, card delle sorgenti, menu) |
| `Tile.java` | Banner dell'app: angoli arrotondati, zoom, riflesso |
| `App.java` | Elenco app, riga di partenza, righe/ordine/nascoste salvati, caricamento banner |
| `ClockDream.java` | Salvaschermo orologio |

Design approvato: `/tmp/design-proposal/glass-home/final.html` (variante C · Righe).

Font: Inter (SIL OFL, `FONT-LICENSE-OFL.txt`), ridotto ai caratteri latini. Icone: Material Symbols Rounded (Apache 2.0).
