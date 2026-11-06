package com.top.engagement_detector.tracking;

import com.top.engagement_detector.vision.FaceBox;
import com.top.engagement_detector.vision.FaceObservation;

import java.util.ArrayList;
import java.util.List;

/** Short-lived geometric associations, not identity recognition. Owned by one analysis worker. */
public final class FaceTracker {
    public static final class TrackedFace {
        public final int id;
        public final FaceObservation observation;

        private TrackedFace(int id, FaceObservation observation) {
            this.id = id;
            this.observation = observation;
        }
    }

    private static final class Track {
        int id;
        FaceBox box;
        long lastSeen;
    }

    private final List<Track> tracks = new ArrayList<>();
    private final long lifetimeMs;
    private int nextId = 1;
    private long lastTimestamp = -1;

    public FaceTracker(long lifetimeMs) {
        this.lifetimeMs = lifetimeMs;
    }

    public List<TrackedFace> update(long timestamp, List<FaceObservation> faces) {
        if (timestamp <= lastTimestamp) {
            tracks.clear();
        }
        lastTimestamp = timestamp;
        tracks.removeIf(track -> timestamp - track.lastSeen > lifetimeMs);
        int n = faces.size(), m = tracks.size();
        double[][] costs = new double[n][m];
        int[] bestTrack = new int[n];
        int[] bestFace = new int[m];
        boolean ambiguous = false;
        for (int i = 0; i < n; i++) {
            double best = Double.POSITIVE_INFINITY, second = best;
            bestTrack[i] = -1;
            for (int j = 0; j < m; j++) {
                double cost = associationCost(faces.get(i).box, tracks.get(j).box);
                costs[i][j] = cost;
                if (cost < best) {
                    second = best;
                    best = cost;
                    bestTrack[i] = j;
                } else {
                    second = Math.min(second, cost);
                }
            }
            ambiguous |= Double.isFinite(second) && second - best < 0.15;
        }
        for (int j = 0; j < m; j++) {
            double best = Double.POSITIVE_INFINITY, second = best;
            bestFace[j] = -1;
            for (int i = 0; i < n; i++) {
                double cost = costs[i][j];
                if (cost < best) {
                    second = best;
                    best = cost;
                    bestFace[j] = i;
                } else {
                    second = Math.min(second, cost);
                }
            }
            ambiguous |= Double.isFinite(second) && second - best < 0.15;
        }
        // Never let an uncertain crossing transfer one visitor's accumulated dwell to another.
        if (ambiguous) {
            tracks.clear();
        }
        List<Track> previous = new ArrayList<>(tracks);
        List<TrackedFace> output = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int match = bestTrack[i];
            Track track;
            if (!ambiguous && match >= 0 && bestFace[match] == i) {
                track = previous.get(match);
            } else {
                track = new Track();
                track.id = nextId++;
                tracks.add(track);
            }
            track.box = faces.get(i).box;
            track.lastSeen = timestamp;
            output.add(new TrackedFace(track.id, faces.get(i)));
        }
        return output;
    }

    private static double associationCost(FaceBox a, FaceBox b) {
        double ratio = a.area() / b.area();
        double distance = Math.hypot(a.centerX() - b.centerX(), a.centerY() - b.centerY())
                / Math.max(0.02, (Math.hypot(a.width(), a.height())
                + Math.hypot(b.width(), b.height())) / 2);
        if (ratio < 0.4 || ratio > 2.5 || distance > 0.9) {
            return Double.POSITIVE_INFINITY;
        }
        return distance + 0.3 * Math.abs(Math.log(ratio));
    }
}
