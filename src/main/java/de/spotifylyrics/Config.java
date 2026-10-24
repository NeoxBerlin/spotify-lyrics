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
    public boolean showSurroundingLines = true;
    public boolean showTitle = true;
    /** Verschiebt die Lyrics zeitlich. Positiv = Zeilen erscheinen frueher. */
    public int offsetMs = 0;
    /** Abstand vom unteren Bildrand (ueber der Hotbar). */
    public int bottomMargin = 70;
    /** Maximale Breite der Lyrics-Box in GUI-Pixeln. */
    public int maxWidth = 320;
    /** Wie oft Spotify abgefragt wird (ms). Dazwischen wird der Fortschritt berechnet. */
    public int pollIntervalMs = 2000;

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
        if (cfg.pollIntervalMs < 1000) cfg.pollIntervalMs = 1000;
        cfg.save(); // schreibt fehlende Felder / legt Datei beim ersten Start an
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
