package de.spotifylyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Holt Lyrics von lrclib.net (kostenlos, kein API-Key). */
public class LrcLibClient {
    public record Result(String synced, String plain, boolean instrumental) {
        public boolean hasSynced() { return synced != null && !synced.isBlank(); }
        public boolean hasPlain() { return plain != null && !plain.isBlank(); }
    }

    private static final String BASE = "https://lrclib.net/api";
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Result fetch(String title, String artist, String album, long durationMs) {
        long durSec = Math.round(durationMs / 1000.0);
        try {
            // 1) exakter Treffer
            String url = BASE + "/get?track_name=" + enc(title) + "&artist_name=" + enc(artist)
                    + (album == null || album.isBlank() ? "" : "&album_name=" + enc(album))
                    + (durSec > 0 ? "&duration=" + durSec : "");
            JsonElement e = get(url);
            if (e != null && e.isJsonObject()) {
                Result r = toResult(e.getAsJsonObject());
                if (r.hasSynced() || r.instrumental()) return r;
            }

            // 2) Suche mit Originaltitel
            Result r = search(title, artist, durSec);
            if (r != null) return r;

            // 3) Suche mit bereinigtem Titel (ohne "(feat. ...)", "- Remastered" usw.)
            String clean = cleanTitle(title);
            if (!clean.equalsIgnoreCase(title)) {
                r = search(clean, artist, durSec);
                if (r != null) return r;
            }
        } catch (Exception ex) {
            SpotifyLyricsClient.LOGGER.warn("LRCLIB-Anfrage fehlgeschlagen: {}", ex.toString());
        }
        return null;
    }

    private Result search(String title, String artist, long durSec) throws Exception {
        String url = BASE + "/search?track_name=" + enc(title) + "&artist_name=" + enc(artist);
        JsonElement e = get(url);
        if (e == null || !e.isJsonArray()) return null;
        JsonArray arr = e.getAsJsonArray();
        Result best = null;
        long bestDiff = Long.MAX_VALUE;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            Result r = toResult(o);
            if (!r.hasSynced()) continue;
            long d = o.has("duration") && !o.get("duration").isJsonNull()
                    ? Math.abs(Math.round(o.get("duration").getAsDouble()) - durSec) : 999;
            if (d < bestDiff) { bestDiff = d; best = r; }
        }
        return best; // darf null sein
    }

    private static Result toResult(JsonObject o) {
        String synced = str(o, "syncedLyrics");
        String plain = str(o, "plainLyrics");
        boolean instr = o.has("instrumental") && !o.get("instrumental").isJsonNull()
                && o.get("instrumental").getAsBoolean();
        return new Result(synced, plain, instr);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private JsonElement get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "SpotifyLyricsMod/1.0 (Minecraft Fabric mod)")
                .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 404) return null;
        if (res.statusCode() != 200) throw new RuntimeException("HTTP " + res.statusCode());
        return JsonParser.parseString(res.body());
    }

    static String cleanTitle(String t) {
        return t.replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", "")
                .replaceAll("\\s+-\\s+.*$", "")
                .trim();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
