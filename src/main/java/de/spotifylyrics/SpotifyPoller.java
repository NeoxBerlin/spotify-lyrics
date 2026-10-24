package de.spotifylyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Fragt regelmaessig ab, was auf Spotify gerade laeuft. */
public class SpotifyPoller {
    public record Playback(String id, String title, String artist, String mainArtist, String album,
                           long durationMs, long progressMs, boolean playing, long fetchedNanos) {
        /** Geschaetzter aktueller Fortschritt (zwischen zwei Abfragen wird hochgezaehlt). */
        public long estimatedProgressMs() {
            if (!playing) return progressMs;
            long p = progressMs + (System.nanoTime() - fetchedNanos) / 1_000_000L;
            return durationMs > 0 ? Math.min(p, durationMs) : p;
        }
    }

    private final Config config;
    private final SpotifyAuth auth;
    private final LyricsManager lyrics;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SpotifyLyrics-Poller");
        t.setDaemon(true);
        return t;
    });

    private volatile Playback current;
    private volatile String status = "Starte...";
    private volatile boolean problem = false;
    private volatile long backoffUntilMs = 0;

    public SpotifyPoller(Config config, SpotifyAuth auth, LyricsManager lyrics) {
        this.config = config;
        this.auth = auth;
        this.lyrics = lyrics;
    }

    public Playback current() { return current; }
    public String status() { return status; }
    /** true, wenn der Status ein Problem beschreibt, das der Spieler beheben sollte. */
    public boolean hasProblem() { return problem; }

    private void ok(String s) { status = s; problem = false; }
    private void bad(String s) { status = s; problem = true; }

    public void start() {
        executor.scheduleWithFixedDelay(this::tick, 1000, Math.max(1000, config.pollIntervalMs), TimeUnit.MILLISECONDS);
    }

    private void tick() {
        try {
            if (System.currentTimeMillis() < backoffUntilMs) return;
            if (config.clientId == null || config.clientId.isBlank()) {
                bad("Trage deine Spotify Client-ID in config/spotify-lyrics.json ein");
                return;
            }
            String token = auth.getAccessToken();
            if (token == null) {
                bad(auth.isLoginRunning()
                        ? "Bitte im Browser bei Spotify anmelden..."
                        : "Nicht bei Spotify angemeldet");
                if (!auth.isLoginRunning()) auth.startLogin();
                return;
            }

            HttpRequest req = HttpRequest.newBuilder(
                            URI.create("https://api.spotify.com/v1/me/player/currently-playing?additional_types=track,episode"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + token)
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long fetched = System.nanoTime();

            switch (res.statusCode()) {
                case 200 -> parse(res.body(), fetched);
                case 204 -> { current = null; ok("Spotify spielt gerade nichts"); }
                case 401 -> { auth.invalidate(); ok("Token abgelaufen, erneuere..."); }
                case 429 -> {
                    long wait = res.headers().firstValue("Retry-After").map(Long::parseLong).orElse(5L);
                    backoffUntilMs = System.currentTimeMillis() + wait * 1000;
                    ok("Spotify-Limit erreicht, kurz warten...");
                }
                case 403 -> bad("Spotify verweigert den Zugriff (Account in der App-Nutzerliste eingetragen?)");
                default -> bad("Spotify-Fehler: HTTP " + res.statusCode());
            }
        } catch (Throwable t) {
            bad("Fehler: " + t.getClass().getSimpleName() + (t.getMessage() != null ? " - " + t.getMessage() : ""));
        }
    }

    private void parse(String body, long fetchedNanos) {
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        JsonElement itemEl = o.get("item");
        if (itemEl == null || itemEl.isJsonNull()) {
            current = null;
            ok("Kein Track (Werbung oder privater Modus?)");
            return;
        }
        JsonObject item = itemEl.getAsJsonObject();
        String id = item.has("id") && !item.get("id").isJsonNull() ? item.get("id").getAsString() : item.get("name").getAsString();
        String title = item.get("name").getAsString();

        StringBuilder artists = new StringBuilder();
        String main = "";
        if (item.has("artists") && item.get("artists").isJsonArray()) {
            JsonArray arr = item.getAsJsonArray("artists");
            for (int i = 0; i < arr.size(); i++) {
                String n = arr.get(i).getAsJsonObject().get("name").getAsString();
                if (i == 0) main = n;
                if (i > 0) artists.append(", ");
                artists.append(n);
            }
        } else if (item.has("show") && item.get("show").isJsonObject()) {
            main = item.getAsJsonObject("show").get("name").getAsString(); // Podcast
            artists.append(main);
        }
        String album = item.has("album") && item.get("album").isJsonObject()
                ? item.getAsJsonObject("album").get("name").getAsString() : "";
        long duration = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0;
        long progress = o.has("progress_ms") && !o.get("progress_ms").isJsonNull() ? o.get("progress_ms").getAsLong() : 0;
        boolean playing = o.has("is_playing") && o.get("is_playing").getAsBoolean();

        Playback p = new Playback(id, title, artists.toString(), main, album, duration, progress, playing, fetchedNanos);
        current = p;
        ok(playing ? "Spielt" : "Pausiert");
        boolean isTrack = !o.has("currently_playing_type") || "track".equals(o.get("currently_playing_type").getAsString());
        if (isTrack) lyrics.onTrack(p);
    }
}
