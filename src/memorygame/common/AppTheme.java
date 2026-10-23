package memorygame.common;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

/** Candy Garden palette shared by the client and server dashboard. */
public final class AppTheme {
    public static final Color BACKGROUND = new Color(0xFFF8EE);
    public static final Color SURFACE = new Color(0xFFFFFF);
    public static final Color INSET = new Color(0xF5F0FF);
    public static final Color BORDER = new Color(0xDDD6FE);
    public static final Color TEXT = new Color(0x24324B);
    public static final Color MUTED = new Color(0x596579);
    public static final Color PRIMARY = new Color(0x6D28D9);
    public static final Color SECONDARY = new Color(0x0F766E);
    public static final Color GOLD = new Color(0xFBBF24);
    public static final Color GOLD_TEXT = new Color(0x855500);
    public static final Color ORANGE = new Color(0xFB923C);
    public static final Color ORANGE_TEXT = new Color(0xA8410B);
    public static final Color BLUE = new Color(0x2563EB);
    public static final Color DANGER = new Color(0xBE123C);
    public static final Color SELECTED = new Color(0xEDE9FE);
    public static final Color SUCCESS_BG = new Color(0xCCFBF1);
    public static final Color WARNING_BG = new Color(0xFEF3C7);
    public static final Color DANGER_BG = new Color(0xFFE4E6);
    public static final Color ON_ACCENT = Color.WHITE;

    /** Apply before constructing either application's Swing components. */
    public static void install() {
        for (String component : new String[]{"Panel", "OptionPane", "Viewport", "ScrollPane"}) {
            color(component + ".background", BACKGROUND);
            color(component + ".foreground", TEXT);
        }
        for (String component : new String[]{"TextField", "PasswordField", "TextArea", "TextPane",
                "EditorPane", "List", "Table", "ComboBox", "Spinner"}) {
            color(component + ".background", SURFACE);
            color(component + ".foreground", TEXT);
            color(component + ".selectionBackground", SELECTED);
            color(component + ".selectionForeground", TEXT);
            color(component + ".inactiveForeground", MUTED);
            color(component + ".caretForeground", PRIMARY);
        }
        for (String component : new String[]{"Button", "ToggleButton", "CheckBox", "RadioButton",
                "Label", "TableHeader", "TabbedPane"}) {
            color(component + ".background", INSET);
            color(component + ".foreground", TEXT);
            color(component + ".disabledText", MUTED);
            color(component + ".focus", PRIMARY);
        }
        color("TabbedPane.selected", SELECTED);
        color("ProgressBar.background", INSET);
        color("ProgressBar.foreground", PRIMARY);
        color("ProgressBar.selectionBackground", TEXT);
        color("ProgressBar.selectionForeground", ON_ACCENT);
        color("ToolTip.background", SURFACE);
        color("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER));
        color("ScrollBar.track", INSET);
        color("ScrollBar.thumb", BORDER);
        color("ScrollBar.thumbDarkShadow", MUTED);
        color("Separator.foreground", BORDER);
        color("Table.gridColor", BORDER);
        UIManager.put("List.focusCellHighlightBorder", BorderFactory.createLineBorder(PRIMARY));
    }

    private static void color(String key, Color value) {
        UIManager.put(key, new ColorUIResource(value));
    }

    public static String hex(Color color) {
        return String.format("#%06X", color.getRGB() & 0xFFFFFF);
    }

    private AppTheme() {}
}
