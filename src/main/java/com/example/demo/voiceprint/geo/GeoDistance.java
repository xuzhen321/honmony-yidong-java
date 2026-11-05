package com.example.demo.voiceprint.geo;

/**
 * 经纬度球面距离（Haversine）。
 *
 * <p>坐标统一使用 WGS84，地球半径取 6371000 米。打卡是否在范围内以后端计算结果为准，
 * 客户端本地算的距离只用于界面提示。</p>
 */
public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private GeoDistance() {
    }

    /**
     * 两点之间的球面距离，单位米。
     *
     * @param latitude1  第一个点纬度（度）
     * @param longitude1 第一个点经度（度）
     * @param latitude2  第二个点纬度（度）
     * @param longitude2 第二个点经度（度）
     */
    public static double haversineMeters(double latitude1, double longitude1,
                                         double latitude2, double longitude2) {
        double lat1 = Math.toRadians(latitude1);
        double lat2 = Math.toRadians(latitude2);
        double deltaLat = Math.toRadians(latitude2 - latitude1);
        double deltaLon = Math.toRadians(longitude2 - longitude1);

        double sinLat = Math.sin(deltaLat / 2);
        double sinLon = Math.sin(deltaLon / 2);
        double a = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
