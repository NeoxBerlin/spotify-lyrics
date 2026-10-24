package de.spotifylyrics;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Zeichnet die Spotify-Karte (Cover, Titel, Fortschritt, Lyrics) oben links. */
public final class LyricsHud {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFF8E8E93;
    private static final int GREEN = 0xFF1DB954; // Spotify-Gruen
    private static final int RED = 0xFFFF6B6B;
    private static final int PANEL = 0xCC111114;

    private static final int PAD = 6;
    private static final int COVER_CELL = 2;
    private static final int COVER_SIZE = CoverArt.N * COVER_CELL; // 64
    private static final int ROW = 10;

    private LyricsHud() {}

    public static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Config cfg = SpotifyLyricsClient.CONFIG;
        if (cfg == null || !cfg.enabled) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        SpotifyPoller poller = SpotifyLyricsClient.POLLER;
        LyricsManager lyrics = SpotifyLyricsClient.LYRICS;
        SpotifyPoller.Playback p = poller.current();

        int x0 = cfg.posX;
        int y0 = cfg.posY;
        int w = Math.max(160, cfg.cardWidth);

        // Nichts laeuft: nur bei Problemen eine kleine Hinweis-Karte zeigen
        if (p == null) {
            if (poller.hasProblem()) {
                List<String> rows = wrap(font, poller.status(), w - 2 * PAD);
                int h = 2 * PAD + rows.size() * ROW;
                panel(g, x0, y0, w, h);
                int y = y0 + PAD;
                for (String r : rows) {
                    g.text(font, r, x0 + PAD, y, RED, true);
                    y += ROW;
                }
            }
            return;
        }

        int lyricsH = 4 * ROW;
        int h = PAD + COVER_SIZE + 6 + 1 + 5 + lyricsH + PAD;
        panel(g, x0, y0, w, h);

        // ---------------------------------------------------------- Kopfbereich
        int coverX = x0 + PAD;
        int coverY = y0 + PAD;
        int textX = x0 + PAD;
        if (cfg.showCover) {
            drawCover(g, font, p, coverX, coverY);
            textX = coverX + COVER_SIZE + 8;
        }
        int textW = x0 + w - PAD - textX;

        g.text(font, fit(font, p.title(), textW), textX, coverY + 3, WHITE, true);
        g.text(font, fit(font, p.artist(), textW), textX, coverY + 14, GRAY, false);

        if (cfg.showProgress) {
            long pos = Math.min(p.estimatedProgressMs(), Math.max(1, p.durationMs()));
            double frac = p.durationMs() > 0 ? (double) pos / p.durationMs() : 0;
            int barY = coverY + 40;
            g.fill(textX, barY, textX + textW, barY + 3, 0x44FFFFFF);
            g.fill(textX, barY, textX + (int) (textW * frac), barY + 3, GREEN);
            g.text(font, formatTime(pos), textX, barY + 7, GRAY, false);
            String total = formatTime(p.durationMs());
            g.text(font, total, textX + textW - font.width(total), barY + 7, GRAY, false);
        }

        // ------------------------------------------------------------- Trennlinie
        int divY = coverY + COVER_SIZE + 6;
        g.fill(x0 + PAD, divY, x0 + w - PAD, divY + 1, 0x22FFFFFF);

        // ---------------------------------------------------------------- Lyrics
        int lx = x0 + PAD;
        int lw = w - 2 * PAD;
        int top = divY + 1 + 5;

        switch (lyrics.state()) {
            case NONE, LOADING -> g.text(font, "Lade Lyrics...", lx, top + ROW, GRAY, false);
            case NOT_FOUND -> g.text(font, "Keine Lyrics gefunden", lx, top + ROW, GRAY, false);
            case INSTRUMENTAL -> g.text(font, "\u266A Instrumental \u266A", lx, top + ROW, GRAY, false);
            case PLAIN_ONLY -> g.text(font, fit(font, "Nur unsynchronisierte Lyrics verf\u00FCgbar", lw), lx, top + ROW, GRAY, false);
            case SYNCED -> {
                List<LrcParser.Line> lines = lyrics.lines();
                long pos = p.estimatedProgressMs() + cfg.offsetMs;
                int idx = lyrics.indexAt(pos);

                if (cfg.showSurroundingLines && idx - 1 >= 0) {
                    g.text(font, fit(font, lineText(lines, idx - 1), lw), lx, top, GRAY, false);
                }
                if (idx < 0) {
                    g.text(font, "\u266A", lx, top + ROW, WHITE, true);
                } else {
                    drawCurrent(g, font, cfg, lines, idx, pos, lx, top + ROW, lw);
                }
                if (cfg.showSurroundingLines && idx + 1 < lines.size()) {
                    g.text(font, fit(font, lineText(lines, idx + 1), lw), lx, top + 3 * ROW, GRAY, false);
                }
            }
        }
    }

    /** Aktuelle Zeile (max. 2 Reihen). Woerter leuchten nacheinander auf. */
    private static void drawCurrent(GuiGraphicsExtractor g, Font font, Config cfg, List<LrcParser.Line> lines,
                                    int idx, long pos, int x, int y, int maxW) {
        String text = lineText(lines, idx);
        String[] words = text.trim().split(" +");

        int sung = 0;
        if (cfg.wordByWord) {
            long start = lines.get(idx).timeMs();
            long next = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : start + 4000;
            long dur = Math.max(600, Math.min(7000, (long) ((next - start) * 0.85)));
            double f = Math.max(0, Math.min(1.0, (pos - start) / (double) dur));
            int total = 0;
            for (String wd : words) total += wd.length() + 1;
            int cum = 0;
            for (int i = 0; i < words.length; i++) {
                if (f * total >= cum) sung = i + 1;
                cum += words[i].length() + 1;
            }
        }

        int cx = 0;
        int row = 0;
        for (int i = 0; i < words.length; i++) {
            int ww = font.width(words[i]);
            if (cx > 0 && cx + ww > maxW) {
                row++;
                cx = 0;
            }
            if (row >= 2) break; // laengere Zeilen werden abgeschnitten
            int color = (cfg.wordByWord && i < sung) ? GREEN : WHITE;
            g.text(font, words[i], x + cx, y + row * ROW, color, true);
            cx += ww + font.width(" ");
        }
    }

    private static void drawCover(GuiGraphicsExtractor g, Font font, SpotifyPoller.Playback p, int x, int y) {
        int[] px = SpotifyLyricsClient.COVER.get(p.coverUrl());
        if (px == null) {
            g.fill(x, y, x + COVER_SIZE, y + COVER_SIZE, 0xFF2A2A2E);
            g.text(font, "\u266A", x + COVER_SIZE / 2 - 3, y + COVER_SIZE / 2 - 4, GRAY, false);
            return;
        }
        int n = CoverArt.N;
        for (int cy = 0; cy < n; cy++) {
            for (int cx = 0; cx < n; cx++) {
                int sx = x + cx * COVER_CELL;
                int sy = y + cy * COVER_CELL;
                g.fill(sx, sy, sx + COVER_CELL, sy + COVER_CELL, px[cy * n + cx]);
            }
        }
    }

    /** Dunkle Karte mit leicht abgerundeten Ecken und gruener Akzentlinie links. */
    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, PANEL);
        g.fill(x, y + 1, x + w, y + h - 1, PANEL);
        g.fill(x, y + 5, x + 2, y + h - 5, GREEN);
    }

    private static String lineText(List<LrcParser.Line> lines, int i) {
        String t = lines.get(i).text();
        return t.isBlank() ? "\u266A" : t;
    }

    private static String formatTime(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    /** Kuerzt Text mit "..." auf die gegebene Breite. */
    private static String fit(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        String e = "...";
        while (!s.isEmpty() && font.width(s + e) > maxW) s = s.substring(0, s.length() - 1);
        return s + e;
    }

    private static List<String> wrap(Font font, String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String cand = cur.isEmpty() ? word : cur + " " + word;
            if (!cur.isEmpty() && font.width(cand) > maxW) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(cand);
            }
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }
}
