package de.spotifylyrics;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.loader.api.FabricLoader;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Spotify-Login per OAuth "Authorization Code with PKCE" (kein Client Secret noetig). */
public class SpotifyAuth {
    public static final String REDIRECT_URI = "http://127.0.0.1:8888/callback";
    private static final String SCOPES = "user-read-currently-playing user-read-playback-state";

    private final Config config;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Path tokenFile = FabricLoader.getInstance().getConfigDir().resolve("spotify-lyrics-token.json");
    private final Gson gson = new Gson();

    private volatile String accessToken;
    private volatile long expiresAtMs;
    private volatile String refreshToken;
    private volatile boolean loginRunning;
    private HttpServer server;

    public SpotifyAuth(Config config) {
        this.config = config;
        loadRefreshToken();
    }

    public boolean isLoggedIn() { return refreshToken != null; }
    public boolean isLoginRunning() { return loginRunning; }

    /** Liefert ein gueltiges Access Token (erneuert es bei Bedarf) oder null, falls nicht eingeloggt. */
    public synchronized String getAccessToken() throws Exception {
        if (refreshToken == null) return null;
        if (accessToken == null || System.currentTimeMillis() > expiresAtMs - 30_000) refresh();
        return accessToken;
    }

    /** Erzwingt beim naechsten Aufruf ein neues Token (z. B. nach HTTP 401). */
    public synchronized void invalidate() { accessToken = null; }

    // ------------------------------------------------------------------ Login

    public synchronized void startLogin() {
        if (loginRunning) return;
        if (config.clientId == null || config.clientId.isBlank()) return;
        try {
            final String verifier = randomUrlSafe(64);
            final String challenge = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            final String state = randomUrlSafe(16);

            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8888), 0);
            server.createContext("/callback", ex -> {
                Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
                String msg;
                try {
                    if (q.containsKey("error")) throw new RuntimeException("Spotify: " + q.get("error"));
                    if (!state.equals(q.get("state"))) throw new RuntimeException("Ungueltiger State");
                    exchangeCode(q.get("code"), verifier);
                    msg = "Fertig! Du bist mit Spotify verbunden. Du kannst dieses Fenster schliessen und zu Minecraft zurueckkehren.";
                } catch (Exception e) {
                    SpotifyLyricsClient.LOGGER.error("Spotify-Login fehlgeschlagen", e);
                    msg = "Login fehlgeschlagen: " + e.getMessage();
                }
                byte[] body = ("<html><head><meta charset=\"utf-8\"></head><body style=\"font-family:sans-serif;text-align:center;margin-top:20vh\"><h2>"
                        + msg + "</h2></body></html>").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
                stopServerLater();
            });
            server.start();
            loginRunning = true;

            String url = "https://accounts.spotify.com/authorize"
                    + "?client_id=" + enc(config.clientId)
                    + "&response_type=code"
                    + "&redirect_uri=" + enc(REDIRECT_URI)
                    + "&scope=" + enc(SCOPES)
                    + "&code_challenge_method=S256"
                    + "&code_challenge=" + challenge
                    + "&state=" + state;
            SpotifyLyricsClient.LOGGER.info("Spotify-Login: {}", url);
            openBrowser(url);
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.error("Konnte Login nicht starten (Port 8888 belegt?)", e);
            stopServerLater();
        }
    }

    private void stopServerLater() {
        Thread t = new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            synchronized (SpotifyAuth.this) {
                if (server != null) { server.stop(0); server = null; }
                loginRunning = false;
            }
        }, "SpotifyLyrics-AuthStop");
        t.setDaemon(true);
        t.start();
    }

    private void exchangeCode(String code, String verifier) throws Exception {
        if (code == null) throw new RuntimeException("Kein Code erhalten");
        JsonObject o = postForm("grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&client_id=" + enc(config.clientId)
                + "&code_verifier=" + enc(verifier));
        applyTokenResponse(o);
    }

    private void refresh() throws Exception {
        try {
            JsonObject o = postForm("grant_type=refresh_token"
                    + "&refresh_token=" + enc(refreshToken)
                    + "&client_id=" + enc(config.clientId));
            applyTokenResponse(o);
        } catch (InvalidGrant e) {
            SpotifyLyricsClient.LOGGER.warn("Refresh-Token ungueltig, bitte neu einloggen");
            refreshToken = null;
            accessToken = null;
            try { Files.deleteIfExists(tokenFile); } catch (Exception ignored) {}
            startLogin();
        }
    }

    private void applyTokenResponse(JsonObject o) {
        accessToken = o.get("access_token").getAsString();
        long expiresIn = o.has("expires_in") ? o.get("expires_in").getAsLong() : 3600;
        expiresAtMs = System.currentTimeMillis() + expiresIn * 1000;
        if (o.has("refresh_token") && !o.get("refresh_token").isJsonNull()) {
            refreshToken = o.get("refresh_token").getAsString();
            saveRefreshToken();
        }
    }

    private static class InvalidGrant extends Exception {}

    private JsonObject postForm(String form) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 400 && res.body().contains("invalid_grant")) throw new InvalidGrant();
        if (res.statusCode() != 200) throw new RuntimeException("Token-Anfrage: HTTP " + res.statusCode() + " " + res.body());
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    // ------------------------------------------------------------ Hilfsfunktionen

    private void loadRefreshToken() {
        try {
            if (Files.exists(tokenFile)) {
                JsonObject o = JsonParser.parseString(Files.readString(tokenFile, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("refresh_token")) refreshToken = o.get("refresh_token").getAsString();
            }
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.warn("Token-Datei nicht lesbar", e);
        }
    }

    private void saveRefreshToken() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("refresh_token", refreshToken);
            Files.writeString(tokenFile, gson.toJson(o), StandardCharsets.UTF_8);
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.warn("Token konnte nicht gespeichert werden", e);
        }
    }

    private static String randomUrlSafe(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            if (i < 0) continue;
            m.put(URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8),
                  URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private static void openBrowser(String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.warn("Browser konnte nicht geoeffnet werden - bitte den Link aus der Logdatei kopieren.", e);
        }
    }
}
