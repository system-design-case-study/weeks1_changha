package com.systemdesigncasestudy.weeks1changha.geo;

import java.util.HashSet;
import java.util.Set;

public final class GeohashUtils {

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
    private static final int[] BITS = {16, 8, 4, 2, 1};

    private GeohashUtils() {
    }

    public static String encode(double latitude, double longitude, int precision) {
        double[] latRange = {-90d, 90d};
        double[] lonRange = {-180d, 180d};

        StringBuilder geohash = new StringBuilder();
        boolean evenBit = true;
        int bit = 0;
        int ch = 0;

        while (geohash.length() < precision) {
            if (evenBit) {
                double mid = (lonRange[0] + lonRange[1]) / 2;
                if (longitude >= mid) {
                    ch |= BITS[bit];
                    lonRange[0] = mid;
                } else {
                    lonRange[1] = mid;
                }
            } else {
                double mid = (latRange[0] + latRange[1]) / 2;
                if (latitude >= mid) {
                    ch |= BITS[bit];
                    latRange[0] = mid;
                } else {
                    latRange[1] = mid;
                }
            }

            evenBit = !evenBit;
            if (bit < 4) {
                bit++;
            } else {
                geohash.append(BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }

        return geohash.toString();
    }

    public static Set<String> centerAndNeighbors(double latitude, double longitude, int precision) {
        int totalBits = precision * 5;
        int lonBits = (totalBits + 1) / 2;
        int latBits = totalBits / 2;

        double lonDelta = 360d / Math.pow(2, lonBits);
        double latDelta = 180d / Math.pow(2, latBits);

        Set<String> result = new HashSet<>();
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                double neighborLat = clampLatitude(latitude + dy * latDelta);
                double neighborLon = wrapLongitude(longitude + dx * lonDelta);
                result.add(encode(neighborLat, neighborLon, precision));
            }
        }

        return result;
    }

    private static double clampLatitude(double latitude) {
        return Math.max(-89.999999d, Math.min(89.999999d, latitude));
    }

    private static double wrapLongitude(double longitude) {
        double wrapped = longitude;
        while (wrapped < -180d) {
            wrapped += 360d;
        }
        while (wrapped >= 180d) {
            wrapped -= 360d;
        }
        return wrapped;
    }
}
