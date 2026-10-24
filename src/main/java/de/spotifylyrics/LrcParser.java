package de.spotifylyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Liest das .lrc-Format: "[mm:ss.xx] Text". */
public final class LrcParser {
    public record Line(long timeMs, String text) {}

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private LrcParser() {}

    public static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();
        if (lrc == null) return out;
        for (String raw : lrc.split("\\R")) {
            Matcher m = TIME.matcher(raw);
            List<Long> times = new ArrayList<>();
            int lastEnd = 0;
            while (m.find()) {
                if (m.start() != lastEnd) break; // Zeitstempel nur am Zeilenanfang
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                long ms = 0;
                if (m.group(3) != null) {
                    String frac = m.group(3);
                    ms = Long.parseLong(frac);
                    if (frac.length() == 1) ms *= 100;
                    else if (frac.length() == 2) ms *= 10;
                }
                times.add(min * 60_000 + sec * 1000 + ms);
                lastEnd = m.end();
            }
            if (times.isEmpty()) continue; // Tags wie [ar:...] oder Leerzeilen
            String text = raw.substring(lastEnd).trim();
            for (long t : times) out.add(new Line(t, text));
        }
        out.sort(Comparator.comparingLong(Line::timeMs));
        return out;
    }
}
