package de.spotifylyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Einstellungen, gespeichert in config/spotify-lyrics.json */
public class Config {
    /** Client ID deiner Spotify-App (developer.spotify.com/dashboard). */
    public String clientId = "";
    public boolean enabled = true;

    /** Position der Karte (GUI-Pixel vom linken/oberen Bildrand). */
    public int posX = 6;
    public int posY = 6;
    /** Breite der Karte. */
    public int cardWidth = 230;

    public boolean showCover = true;
    public boolean showProgress = true;
    /** Vorherige und naechste Zeile abgedunkelt anzeigen. */
    public boolean showSurroundingLines = true;
    /** Woerter der aktuellen Zeile nacheinander gruen aufleuchten lassen. */
    public boolean wordByWord = true;

    /** Verschiebt die Lyrics zeitlich. Positiv = Zeilen erscheinen frueher. */
    public int offsetMs = 0;
    /** Wie oft Spotify abgefragt wird (ms). Dazwischen wird der Fortschritt berechnet. */
    public int pollMs = 1000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("spotify-lyrics.json");
    }

    public static Config load() {
        Path p = path();
        Config cfg = new Config();
        try {
            if (Files.exists(p)) {
                Config loaded = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), Config.class);
                if (loaded != null) cfg = loaded;
            }
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.warn("Konnte Config nicht lesen, nutze Standardwerte", e);
        }
        if (cfg.pollMs < 500) cfg.pollMs = 500;
        if (cfg.cardWidth < 160) cfg.cardWidth = 160;
        cfg.save(); // schreibt neue Felder / legt Datei beim ersten Start an
        return cfg;
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.warn("Konnte Config nicht speichern", e);
        }
    }
}
