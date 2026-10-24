package de.spotifylyrics;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Zeichnet die Lyrics unten mittig ueber der Hotbar. */
public final class LyricsHud {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFF9E9E9E;
    private static final int GREEN = 0xFF1DB954; // Spotify-Gruen
    private static final int RED = 0xFFFF6B6B;

    private record Row(String text, int color) {}

    private LyricsHud() {}

    public static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Config cfg = SpotifyLyricsClient.CONFIG;
        if (cfg == null || !cfg.enabled) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int maxW = Math.max(100, cfg.maxWidth);
        List<Row> rows = new ArrayList<>();

        SpotifyPoller poller = SpotifyLyricsClient.POLLER;
        LyricsManager lyrics = SpotifyLyricsClient.LYRICS;
        SpotifyPoller.Playback p = poller.current();

        if (p == null) {
            if (poller.hasProblem()) addWrapped(rows, font, poller.status(), maxW, RED);
        } else {
            if (cfg.showTitle) {
                addWrapped(rows, font, "\u266A " + p.title() + (p.artist().isBlank() ? "" : " \u2013 " + p.artist()), maxW, GREEN);
            }
            switch (lyrics.state()) {
                case NONE, LOADING -> addWrapped(rows, font, "Lade Lyrics...", maxW, GRAY);
                case NOT_FOUND -> addWrapped(rows, font, "Keine Lyrics gefunden", maxW, GRAY);
                case INSTRUMENTAL -> addWrapped(rows, font, "\u266A Instrumental \u266A", maxW, GRAY);
                case PLAIN_ONLY -> addWrapped(rows, font, "Nur unsynchronisierte Lyrics verf\u00FCgbar", maxW, GRAY);
                case SYNCED -> {
                    List<LrcParser.Line> lines = lyrics.lines();
                    int idx = lyrics.indexAt(p.estimatedProgressMs() + cfg.offsetMs);
                    if (cfg.showSurroundingLines && idx - 1 >= 0) addWrapped(rows, font, lineText(lines, idx - 1), maxW, GRAY);
                    addWrapped(rows, font, idx < 0 ? "\u266A" : lineText(lines, idx), maxW, WHITE);
                    if (cfg.showSurroundingLines && idx + 1 < lines.size()) addWrapped(rows, font, lineText(lines, idx + 1), maxW, GRAY);
                }
            }
        }
        if (rows.isEmpty()) return;

        int lineH = font.lineHeight + 1;
        int textW = 0;
        for (Row r : rows) textW = Math.max(textW, font.width(r.text()));
        int boxW = textW + 12;
        int boxH = rows.size() * lineH + 8;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int x0 = (screenW - boxW) / 2;
        int y0 = screenH - cfg.bottomMargin - boxH;

        g.fill(x0, y0, x0 + boxW, y0 + boxH, 0x90000000);
        int y = y0 + 4;
        for (Row r : rows) {
            int x = (screenW - font.width(r.text())) / 2;
            g.text(font, r.text(), x, y, r.color(), true);
            y += lineH;
        }
    }

    private static String lineText(List<LrcParser.Line> lines, int i) {
        String t = lines.get(i).text();
        return t.isBlank() ? "\u266A" : t;
    }

    /** Bricht lange Zeilen an Wortgrenzen um. */
    private static void addWrapped(List<Row> out, Font font, String text, int maxW, int color) {
        if (font.width(text) <= maxW) {
            out.add(new Row(text, color));
            return;
        }
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = cur.isEmpty() ? word : cur + " " + word;
            if (!cur.isEmpty() && font.width(candidate) > maxW) {
                out.add(new Row(cur.toString(), color));
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(candidate);
            }
        }
        if (!cur.isEmpty()) out.add(new Row(cur.toString(), color));
    }
}
