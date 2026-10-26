package memorygame.client;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public final class RankingPanelTest {
    public static void main(String[] args) throws Exception {
        Map<String,Object> row = Map.of("rank", 7, "username", "player", "rating", 12, "wins", 6, "games", 23, "winRate", 26.086956);
        RankingPanel.Entry entry = RankingPanel.Entry.from(row);
        if (entry.rank() != 7 || !entry.rateText().equals("26,1%")) throw new AssertionError("Backend rank/rate changed");
        SwingUtilities.invokeAndWait(() -> {
            RankingPanel view = new RankingPanel("PLAYER", List.of(row), () -> {});
            JTable table = findTable(view);
            if (table == null || !table.getValueAt(0,0).equals(7) || !table.getValueAt(0,1).equals("player  [Bạn]")
                    || !table.getValueAt(0,2).equals(12) || !table.getValueAt(0,3).equals("6/23")
                    || !table.getValueAt(0,4).equals("26,1%")) throw new AssertionError("Ranking data mismatch");
            for (int column = 0; column < table.getColumnCount(); column++) {
                JLabel header = (JLabel) table.getTableHeader().getDefaultRenderer()
                        .getTableCellRendererComponent(table, table.getColumnName(column), false, false, -1, column);
                JLabel cell = (JLabel) table.prepareRenderer(table.getCellRenderer(0, column), 0, column);
                int expected = column == 1 ? SwingConstants.LEFT : SwingConstants.CENTER;
                if (header.getHorizontalAlignment() != expected || cell.getHorizontalAlignment() != expected
                        || header.getInsets().left != cell.getInsets().left
                        || header.getInsets().right != cell.getInsets().right) {
                    throw new AssertionError("Header/data alignment mismatch in column " + column);
                }
            }
            if (!hasText(view, "Hạng của bạn: 7   •   Điểm: 12")) throw new AssertionError("Self summary");
            List<Map<String, Object>> sample = List.of(
                    Map.of("rank", 1, "username", "pointsLeader", "rating", 30, "wins", 6, "games", 20, "winRate", 30.0),
                    Map.of("rank", 2, "username", "player", "rating", 20, "wins", 8, "games", 10, "winRate", 80.0),
                    Map.of("rank", 3, "username", "tie", "rating", 10, "wins", 4, "games", 5, "winRate", 80.0),
                    Map.of("rank", 4, "username", "new", "rating", 0, "wins", 0, "games", 0, "winRate", 0.0));
            RankingPanel sortable = new RankingPanel("player", sample, () -> {});
            findCombo(sortable).setSelectedIndex(1);
            JTable sorted = findTable(sortable);
            if (!sorted.getValueAt(0, 1).equals("player  [Bạn]")
                    || !sorted.getValueAt(1, 1).equals("tie") || !sorted.getValueAt(2, 1).equals("pointsLeader")
                    || !sorted.getValueAt(0, 2).equals(20) || !sorted.getValueAt(0, 4).equals("80,0%")
                    || !hasText(sortable, "Hạng của bạn: 1   •   Điểm: 20"))
                throw new AssertionError("Win-rate sorting or self rank mismatch");
            if (!hasPodium(sortable, "Hạng 1: player,")) throw new AssertionError("Podium not reordered");
            findCombo(sortable).setSelectedIndex(0);
            if (!findTable(sortable).getValueAt(0, 1).equals("pointsLeader")
                    || !hasText(sortable, "Hạng của bạn: 2   •   Điểm: 20"))
                throw new AssertionError("Score ranking not restored");
            RankingPanel empty = new RankingPanel("nobody", List.of(), () -> {});
            findCombo(empty).setSelectedIndex(1);
            if (!hasText(empty, "Chưa có dữ liệu xếp hạng")
                    || !hasText(empty, "Tài khoản của bạn chưa có trong bảng xếp hạng.")) throw new AssertionError("Empty state");
        });
        System.out.println("RANKING_PANEL_OK backendRanks winRate self empty");
    }
    private static JComboBox<?> findCombo(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JComboBox<?> combo) return combo;
            if (child instanceof Container nested) { JComboBox<?> combo = findCombo(nested); if (combo != null) return combo; }
        }
        return null;
    }
    private static boolean hasPodium(Container parent, String prefix) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JComponent component && component.getToolTipText() != null
                    && component.getToolTipText().startsWith(prefix)) return true;
            if (child instanceof Container nested && hasPodium(nested, prefix)) return true;
        }
        return false;
    }
    private static JTable findTable(Container parent) {
        for(Component child:parent.getComponents()) {
            if(child instanceof JTable table)return table;
            if(child instanceof Container nested){JTable table=findTable(nested);if(table!=null)return table;}
        }
        return null;
    }
    private static boolean hasText(Container parent,String value) {
        for(Component child:parent.getComponents()) {
            if(child instanceof JLabel label && value.equals(label.getText()))return true;
            if(child instanceof Container nested && hasText(nested,value))return true;
        }
        return false;
    }
}
