package org.avium.systemui.lockscreen.util;

import android.graphics.Color;
import android.icu.text.SimpleDateFormat;
import android.util.Log;
import java.util.Date;
import java.util.Locale;

public class LockscreenClockUtils {

    private static final String TAG = "AVIUM_LOCKSCREEN";
    
    private static final int DEFAULT_COLOR = Color.WHITE;

    public static String getCurrentTimeString(String pattern, Locale locale) {
        try {
            Date now = new Date();
            SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
            String timeString = format.format(now);
            Log.v(TAG, "getCurrentTimeString: pattern=" + pattern + ", result=" + timeString);
            return timeString;
        } catch (Exception e) {
            Log.e(TAG, "Error formatting time with pattern: " + pattern, e);
            return "";
        }
    }

    public static String getCurrentTimeString(String pattern) {
        return getCurrentTimeString(pattern, Locale.getDefault());
    }

    public static String getCurrentDateString() {
        return getCurrentTimeString("M月d日 EEEE", Locale.CHINESE);
    }

    public static int parseColor(String colorString) {
        if (colorString == null || colorString.trim().isEmpty()) {
            return DEFAULT_COLOR;
        }

        String color = colorString.trim().toLowerCase();

        try {
            if (color.startsWith("#")) {
                int parsed = Color.parseColor(color);
                return parsed;
            } else if (color.matches("^[0-9a-f]{6}$") || color.matches("^[0-9a-f]{8}$")) {
                String hexColor = "#" + color;
                int parsed = Color.parseColor(hexColor);
                return parsed;
            }

            switch (color) {
                case "red":
                    return Color.RED;
                case "green":
                    return Color.GREEN;
                case "blue":
                    return Color.BLUE;
                case "white":
                    return Color.WHITE;
                case "black":
                    return Color.BLACK;
                case "yellow":
                    return Color.YELLOW;
                case "cyan":
                    return Color.CYAN;
                case "magenta":
                    return Color.MAGENTA;
                case "gray":
                case "grey":
                    return Color.GRAY;
                default:
                    Log.w(TAG, "parseColor: unknown color name '" + color + "', using default white");
                    return DEFAULT_COLOR;
            }
        } catch (Exception e) {
            return DEFAULT_COLOR;
        }
    }

    public static boolean isValidColor(String colorString) {
        if (colorString == null || colorString.trim().isEmpty()) {
            return false;
        }

        String color = colorString.trim().toLowerCase();
        
        try {
            if (color.startsWith("#")) {
                Color.parseColor(color);
                return true;
            } else if (color.matches("^[0-9a-f]{6}$") || color.matches("^[0-9a-f]{8}$")) {
                Color.parseColor("#" + color);
                return true;
            }
            switch (color) {
                case "red":
                case "green":
                case "blue":
                case "white":
                case "black":
                case "yellow":
                case "cyan":
                case "magenta":
                case "gray":
                case "grey":
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static char getTimeDigitAt(String timeString, int position) {
        if (timeString == null || position < 0 || position >= timeString.length()) {
            return '0';
        }
        return timeString.charAt(position);
    }

    public static int charDigitToInt(char digitChar) {
        if (digitChar >= '0' && digitChar <= '9') {
            return digitChar - '0';
        }
        return 0;
    }
}