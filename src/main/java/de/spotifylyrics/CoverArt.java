package de.spotifylyrics;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Laedt das Albumcover und verkleinert es auf N x N Farbwerte.
 * Gezeichnet wird es spaeter als feines Raster aus Rechtecken (ohne Minecraft-Texturen).
 */
public class CoverArt {
    public static final int N = 32;

    private volatile String url = "";
    private volatile int[] pixels;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SpotifyLyrics-Cover");
        t.setDaemon(true);
        return t;
    });

    /** Liefert die Pixel (N*N, ARGB) oder null, solange das Bild noch laedt. */
    public int[] get(String u) {
        if (u == null || u.isEmpty()) return null;
        if (!u.equals(url)) {
            url = u;
            pixels = null;
            executor.submit(() -> load(u));
        }
        return pixels;
    }

    private void load(String u) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) return;
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(res.body()));
            if (img == null) return;

            int side = Math.min(img.getWidth(), img.getHeight());
            int offX = (img.getWidth() - side) / 2;
            int offY = (img.getHeight() - side) / 2;
            int[] out = new int[N * N];
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    int sx0 = offX + x * side / N;
                    int sx1 = Math.max(sx0 + 1, offX + (x + 1) * side / N);
                    int sy0 = offY + y * side / N;
                    int sy1 = Math.max(sy0 + 1, offY + (y + 1) * side / N);
                    long r = 0, g = 0, b = 0;
                    int count = 0;
                    for (int sy = sy0; sy < sy1; sy++) {
                        for (int sx = sx0; sx < sx1; sx++) {
                            int c = img.getRGB(Math.min(sx, img.getWidth() - 1), Math.min(sy, img.getHeight() - 1));
                            r += (c >> 16) & 0xFF;
                            g += (c >> 8) & 0xFF;
                            b += c & 0xFF;
                            count++;
                        }
                    }
                    out[y * N + x] = 0xFF000000 | ((int) (r / count) << 16) | ((int) (g / count) << 8) | (int) (b / count);
                }
            }
            if (u.equals(url)) pixels = out; // nur uebernehmen, wenn der Song noch derselbe ist
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.warn("Cover konnte nicht geladen werden: {}", e.toString());
        }
    }
}
