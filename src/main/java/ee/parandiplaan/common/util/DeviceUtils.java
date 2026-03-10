package ee.parandiplaan.common.util;

public final class DeviceUtils {

    private DeviceUtils() {}

    public static String parseDeviceLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Tundmatu seade";
        }

        String browser = parseBrowser(userAgent);
        String os = parseOs(userAgent);

        if (browser != null && os != null) {
            return browser + ", " + os;
        }
        if (browser != null) {
            return browser;
        }
        if (os != null) {
            return os;
        }
        return "Tundmatu seade";
    }

    private static String parseBrowser(String ua) {
        if (ua.contains("Edg/") || ua.contains("Edge/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera/")) return "Opera";
        if (ua.contains("Chrome/") && !ua.contains("Edg/")) return "Chrome";
        if (ua.contains("Safari/") && !ua.contains("Chrome/")) return "Safari";
        if (ua.contains("Firefox/")) return "Firefox";
        return null;
    }

    private static String parseOs(String ua) {
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Macintosh") || ua.contains("Mac OS")) return "macOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Linux")) return "Linux";
        return null;
    }
}
