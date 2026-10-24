# Spotify Lyrics – Fabric-Mod für Minecraft 26.2

Zeigt die Lyrics des Songs, den du gerade auf Spotify hörst, live im Spiel an (unten mittig, über der Hotbar).
Nur Client-Seite – funktioniert in Singleplayer und auf jedem Server.

## 1. Spotify-App anlegen (einmalig, ca. 2 Minuten)

1. Gehe auf https://developer.spotify.com/dashboard und melde dich an.
2. **Create app** → beliebiger Name und Beschreibung.
3. Bei **Redirect URI** exakt eintragen: `http://127.0.0.1:8888/callback`
4. Bei **Which API/SDKs are you planning to use?** → **Web API** ankreuzen. Speichern.
5. In den App-Einstellungen die **Client ID** kopieren.
   (Falls dein Spotify-Account nicht der App-Besitzer ist: unter *User Management* deine Spotify-E-Mail eintragen.)

## 2. Mod bauen

### Variante A – ohne Installation, über GitHub (am einfachsten)
1. Neues, leeres Repository auf github.com anlegen.
2. Den kompletten Inhalt dieses Ordners hochladen (Ordner `.github` nicht vergessen).
3. Reiter **Actions** → Workflow **build** → nach dem Durchlauf unter *Artifacts* `spotify-lyrics-mod` herunterladen.
4. In der ZIP liegt die fertige `spotify-lyrics-1.0.0.jar`.

### Variante B – lokal
Voraussetzung: JDK 25 und Gradle 9.5+ (oder in IntelliJ als Gradle-Projekt öffnen).
```
gradle build
```
Die Jar liegt danach in `build/libs/`.

## 3. Installieren

1. Fabric Loader (≥ 0.19.3) und **Fabric API** für Minecraft 26.2 installieren (Java 25).
2. `spotify-lyrics-1.0.0.jar` in den `mods`-Ordner legen.
3. Minecraft starten → einmal beenden → `config/spotify-lyrics.json` öffnen, bei `clientId` die Client ID eintragen.
4. Minecraft erneut starten. Beim ersten Mal öffnet sich der Browser zur Spotify-Anmeldung. Danach läuft alles automatisch.

## Bedienung

- **L** blendet die Lyrics ein/aus (in den Steuerungs-Einstellungen änderbar).
- Lyrics kommen von https://lrclib.net. Nicht jeder Song hat dort synchronisierte Lyrics.

## Einstellungen (`config/spotify-lyrics.json`)

| Option | Bedeutung |
|---|---|
| `clientId` | Client ID deiner Spotify-App |
| `enabled` | Karte anzeigen |
| `posX`, `posY` | Position der Karte (Pixel vom linken/oberen Rand, Standard 6/6 = oben links) |
| `cardWidth` | Breite der Karte |
| `showCover` | Albumcover anzeigen |
| `showProgress` | Fortschrittsbalken mit Zeit anzeigen |
| `showSurroundingLines` | vorherige/nächste Zeile abgedunkelt mitanzeigen |
| `wordByWord` | Wörter der aktuellen Zeile nacheinander grün aufleuchten lassen |
| `offsetMs` | Timing verschieben (positiv = Zeilen früher) |
| `pollMs` | Abfrageintervall bei Spotify (min. 500, Standard 1000) |

Hinweis: Öffnest du mit F3 das Debug-Menü, überdeckt es die Karte oben links. Mit `posX`/`posY` kannst du sie verschieben.

## Fehlersuche

- **Browser öffnet sich nicht:** den Link `https://accounts.spotify.com/authorize?...` aus `logs/latest.log` kopieren.
- **Port 8888 belegt:** anderes Programm beenden (Redirect-URI muss genau so bleiben).
- **Build-Fehler wegen Versionen:** in `gradle.properties` `loom_version`, `loader_version` und `fabric_api_version` auf die aktuellen Werte für 26.2 setzen (siehe https://fabricmc.net/develop).
