package memorygame.common;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared visual rules. Player order must be the server's game order, never score order. */
public final class GameColors {
    // Keep matched cards distinct from the default purple card backs.
    private static final Color PLAYER_PINK = new Color(0xBE185D);
    public static final Color UNASSIGNED = PLAYER_PINK;
    private static final List<Color> PLAYERS = List.of(
            PLAYER_PINK, AppTheme.SECONDARY, AppTheme.ORANGE, AppTheme.BLUE);

    private GameColors() {}

    public static Map<String, Color> forPlayers(List<String> players) {
        Map<String, Color> colors = new LinkedHashMap<>();
        for (int i = 0; i < players.size(); i++) colors.put(players.get(i), PLAYERS.get(i % PLAYERS.size()));
        return colors;
    }

    public static Color forValue(int value) {
        return AppTheme.SURFACE;
    }

    public static Color textOn(Color background) {
        double luminance = luminance(background);
        double lightContrast = 1.05 / (luminance + .05);
        double darkContrast = (luminance + .05) / (luminance(AppTheme.TEXT) + .05);
        return darkContrast >= lightContrast ? AppTheme.TEXT : Color.WHITE;
    }

    private static double luminance(Color color) {
        return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen())
                + .0722 * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
