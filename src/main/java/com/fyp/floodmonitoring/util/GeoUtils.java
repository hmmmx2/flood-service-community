package com.fyp.floodmonitoring.util;

/**
 * Geographic utility methods.
 */
public final class GeoUtils {

    private GeoUtils() {}

    private static final double EARTH_RADIUS_KM = 6371.0;

    /** Reference point: Kuching city centre, Sarawak, Malaysia. */
    public static final double KUCHING_LAT = 1.5533;
    public static final double KUCHING_LON = 110.3592;

    /**
     * Calculates the great-circle distance between two geographic coordinates
     * using the Haversine formula.
     *
     * @return distance in kilometres, rounded to 1 decimal place
     */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLon / 2), 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double km = EARTH_RADIUS_KM * c;
        return Math.round(km * 10.0) / 10.0;
    }

    /**
     * Human-readable relative time, e.g. "5m ago", "2h ago", "3d ago".
     */
    public static String relativeTime(java.time.Instant instant) {
        long diffMs  = System.currentTimeMillis() - instant.toEpochMilli();
        long mins    = diffMs / 60_000;
        if (mins < 1)  return "just now";
        if (mins < 60) return mins + "m ago";
        long hours = mins / 60;
        if (hours < 24) return hours + "h ago";
        return (hours / 24) + "d ago";
    }

    /**
     * Human-readable relative time with full word units, e.g. "5 mins ago", "2 hours ago".
     */
    public static String relativeTimeFull(java.time.Instant instant) {
        long diffMs = System.currentTimeMillis() - instant.toEpochMilli();
        long mins   = diffMs / 60_000;
        if (mins < 1)  return "just now";
        if (mins < 60) return mins + " min" + (mins != 1 ? "s" : "") + " ago";
        long hours = mins / 60;
        if (hours < 24) return hours + " hour" + (hours != 1 ? "s" : "") + " ago";
        long days = hours / 24;
        return days + " day" + (days != 1 ? "s" : "") + " ago";
    }

    /** Format a large number with SI suffixes: 1200 → "1.2k", 1200000 → "1.2M". */
    public static String formatCount(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fk", n / 1_000.0);
        return String.valueOf(n);
    }
}
