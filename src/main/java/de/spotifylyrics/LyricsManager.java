package de.spotifylyrics;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Laedt Lyrics zum aktuellen Track und liefert die passende Zeile zum Zeitpunkt. */
public class LyricsManager {
    public enum State { NONE, LOADING, SYNCED, PLAIN_ONLY, INSTRUMENTAL, NOT_FOUND }

    private volatile String trackId = null;
    private volatile State state = State.NONE;
    private volatile List<LrcParser.Line> lines = List.of();

    private final LrcLibClient client = new LrcLibClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SpotifyLyrics-Loader");
        t.setDaemon(true);
        return t;
    });

    public State state() { return state; }
    public List<LrcParser.Line> lines() { return lines; }

    /** Wird vom Poller bei jeder Abfrage aufgerufen. */
    public void onTrack(SpotifyPoller.Playback p) {
        if (p == null) return;
        if (p.id().equals(trackId)) return;
        trackId = p.id();
        state = State.LOADING;
        lines = List.of();
        final String id = p.id();
        executor.submit(() -> load(id, p));
    }

    private void load(String id, SpotifyPoller.Playback p) {
        LrcLibClient.Result r = client.fetch(p.title(), p.mainArtist(), p.album(), p.durationMs());
        if (!id.equals(trackId)) return; // Song wurde inzwischen gewechselt
        if (r == null) {
            state = State.NOT_FOUND;
        } else if (r.instrumental()) {
            state = State.INSTRUMENTAL;
        } else if (r.hasSynced()) {
            List<LrcParser.Line> parsed = LrcParser.parse(r.synced());
            if (parsed.isEmpty()) {
                state = r.hasPlain() ? State.PLAIN_ONLY : State.NOT_FOUND;
            } else {
                lines = parsed;
                state = State.SYNCED;
            }
        } else {
            state = r.hasPlain() ? State.PLAIN_ONLY : State.NOT_FOUND;
        }
    }

    /** Index der Zeile, die zum Zeitpunkt positionMs gesungen wird (-1 = noch vor der ersten Zeile). */
    public int indexAt(long positionMs) {
        List<LrcParser.Line> l = lines;
        int lo = 0, hi = l.size() - 1, res = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (l.get(mid).timeMs() <= positionMs) { res = mid; lo = mid + 1; }
            else hi = mid - 1;
        }
        return res;
    }
}
