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
            RankingPanel empty = new RankingPanel("nobody", List.of(), () -> {});
            if (!hasText(empty, "Chưa có dữ liệu xếp hạng")
                    || !hasText(empty, "Tài khoản của bạn chưa có trong bảng xếp hạng.")) throw new AssertionError("Empty state");
        });
        System.out.println("RANKING_PANEL_OK backendRanks winRate self empty");
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
