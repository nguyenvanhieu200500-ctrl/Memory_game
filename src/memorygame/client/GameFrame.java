package memorygame.client;

import memorygame.common.AppTheme;

import memorygame.common.Protocol.Message;
import memorygame.common.GameColors;
import memorygame.common.Protocol;
import memorygame.common.ChatPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Path2D;
import java.io.Serializable;
import java.util.List;
import java.util.*;

public final class GameFrame extends JFrame {
    private static final SoundManager AUDIO = new SoundManager();
    private static final Color BACKGROUND = AppTheme.BACKGROUND;
    private static final Color PANEL = AppTheme.SURFACE;
    private static final Color FIELD = AppTheme.SURFACE;
    private static final Color BORDER = AppTheme.BORDER;
    private static final Color MUTED = AppTheme.MUTED;
    private static final Color ACCENT = AppTheme.PRIMARY;
    private static final Color VIOLET = AppTheme.PRIMARY;
    private static final Color VIOLET_LIGHT = AppTheme.PRIMARY;
    private static final Color CORAL = AppTheme.ORANGE_TEXT;
    private static final Color CORAL_LIGHT = AppTheme.ORANGE_TEXT;
    private static final Color SUCCESS = AppTheme.SECONDARY;
    private static final Color WARNING = AppTheme.GOLD_TEXT;
    private static final Color TEXT = AppTheme.TEXT;
    private static final Color INFO_BG = AppTheme.SELECTED;
    private static final Color SUCCESS_BG = AppTheme.SUCCESS_BG;
    private static final Color WARNING_BG = AppTheme.WARNING_BG;
    private static final Color DANGER_BG = AppTheme.DANGER_BG;

    private final GameClient client = new GameClient();
    private ChatPanel chatPanel;
    private final CardLayout pages = new CardLayout();
    private final JPanel root = new JPanel(pages);

    private JTextField hostField;
    private JTextField portField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JLabel loginStatus;
    private JButton loginButton;
    private JTextField registerHostField;
    private JTextField registerPortField;
    private JTextField registerUsernameField;
    private JPasswordField registerPasswordField;
    private JPasswordField registerConfirmField;
    private JLabel registerStatus;
    private JButton registerButton;
    private Protocol.Type pendingAuthType = Protocol.Type.LOGIN;
    private boolean authenticated;

    private final DefaultListModel<PlayerItem> lobbyModel = new DefaultListModel<>();
    private final JList<PlayerItem> lobbyList = new JList<>(lobbyModel);
    private JLabel lobbyTitle;
    private final Map<String, Icon> lobbyAvatars = new HashMap<>();
    private ProfilePanel profilePanel;
    private JDialog profileDialog;
    private String pendingPassword;
    private boolean profileRequested;
    private JLabel lobbyStats;
    private JLabel lobbyWins;
    private int currentWins;
    private JLabel lobbyNotice;
    private JLabel connectionBadge;
    private JComboBox<String> boardSizeCombo;
    private JComboBox<String> playerCountCombo;
    private JComboBox<String> lobbyThemeCombo;
    private final DefaultComboBoxModel<String> cardThemeModel = new DefaultComboBoxModel<>(
            Arrays.stream(CardTheme.values()).map(CardTheme::toString).toArray(String[]::new));
    private JLabel onlineCountLabel;
    private JButton findMatchButton;
    private JButton cancelQueueButton;
    private final Set<String> friends = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private WhisperHistory whisperHistory = new WhisperHistory();
    private String whisperScope = "";
    private JDialog conversationsWindow;
    private ConversationsPanel conversationsPanel;
    private ChatNotificationButton conversationsButton;
    private final UnreadMessages unreadMessages = new UnreadMessages();
    private final Map<String, ChatPanel> whispers = new HashMap<>();
    private final Map<String, JDialog> whisperWindows = new HashMap<>();
    private JButton createRoomButton;
    private JButton joinRoomButton;
    private JButton shopButton;
    private JButton logoutButton;
    private JButton musicToggleButton;
    private JButton effectsToggleButton;
    private JLabel walletLabel;
    private String selfStatus = "IDLE";

    private final DefaultListModel<ShopItem> shopModel = new DefaultListModel<>();
    private final JList<ShopItem> shopList = new JList<>(shopModel);
    private JLabel shopBalance;
    private JLabel shopSelectionTitle;
    private JLabel shopSelectionDescription;
    private JButton shopPrimaryButton;
    private CardButton shopCardPreview;
    private String shopPreviewTheme = "theme_classic";
    private int coins;
    private int currentRating;
    private String equippedTheme = "theme_classic";
    private String equippedTitle = "";
    private Color cardBackColor = AppTheme.PRIMARY;

    private final DefaultListModel<RoomPlayerItem> roomModel = new DefaultListModel<>();
    private final JList<RoomPlayerItem> roomList = new JList<>(roomModel);
    private JLabel roomTitle;
    private JLabel roomMemberCount;
    private RoomInvitePanel roomInvites;
    private JLabel roomWallet;
    private JPanel roomMemberGrid;
    private JLabel roomHost;
    private JLabel roomStatusLabel;
    private JButton readyButton;
    private JButton startButton;
    private JButton kickButton;
    private JButton copyRoomCodeButton;

    private JPanel boardPanel;
    private JButton[] cards = new JButton[0];
    private int[] cardStates = new int[0];
    private int[] cardValues = new int[0];
    private CardTheme cardTheme = CardTheme.FRUIT;
    private String[] cardOwners = new String[0];
    private final Map<String, Color> playerColors = new LinkedHashMap<>();
    private int boardSize = 8;
    private int roomCapacity = 4;
    private JComboBox<String> gameThemeCombo;
    private int totalPairs = 32;
    private JLabel gameTurn;
    private JLabel gameTimer;
    private JProgressBar turnProgress;
    private JLabel gameScores;
    private JLabel gamePairs;
    private long deadline;
    private String currentPlayer = "";
    private final javax.swing.Timer countdown;
    private final javax.swing.Timer visualEffectsTimer;
    private double visualEffectPhase;

    private String username = "";
    private String roomCode = "";
    private String roomHostName = "";
    private boolean selfReady;
    private boolean postGame;
    private boolean selfReplay;
    private long replayDeadline;
    private boolean roomAllReady;
    private boolean roomAllOnline;
    private int roomPlayerCount;
    private boolean matchmakingRoom;
    private int matchmakingTargetPlayers;
    private long roomStartCountdownDeadline;

    private String savedHost = "";
    private int savedPort;
    private String savedUsername = "";
    private String savedPassword = "";
    private boolean closing;
    private boolean reconnecting;
    private volatile boolean reconnectAttemptRunning;
    private int reconnectAttempts;
    private javax.swing.Timer reconnectTimer;

    public GameFrame() {
        super("Game lật thẻ Online");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 720));
        setSize(1240, 820);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                closing = true;
                if (reconnectTimer != null) reconnectTimer.stop();
                if (visualEffectsTimer != null) visualEffectsTimer.stop();
                AUDIO.close();
                client.close();
                dispose();
            }
        });

        root.add(buildLoginPanel(), "login");
        root.add(buildRegisterPanel(), "register");
        root.add(buildLobbyPanel(), "lobby");
        root.add(buildShopPanel(), "shop");
        root.add(buildRoomPanel(), "room");
        root.add(buildGamePanel(), "game");
        setContentPane(root);
        pages.show(root, "login");

        countdown = new javax.swing.Timer(200, e -> updateCountdown());
        countdown.start();
        visualEffectsTimer = new javax.swing.Timer(40, e -> updateCardThemeEffects());
        visualEffectsTimer.start();
    }

    private JPanel buildLoginPanel() {
        LoginPanel loginPanel = new LoginPanel(this::connectAndLogin, () -> {
            registerHostField.setText(hostField.getText());
            registerPortField.setText(portField.getText());
            registerUsernameField.setText(usernameField.getText());
            registerStatus.setText("Tên tài khoản 3-16 ký tự; mật khẩu ít nhất 6 ký tự.");
            pages.show(root, "register");
        });
        hostField = loginPanel.host;
        portField = loginPanel.port;
        usernameField = loginPanel.username;
        passwordField = loginPanel.password;
        loginButton = loginPanel.login;
        loginStatus = loginPanel.status;
        return loginPanel;
    }

    private JPanel buildRegisterPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(AppTheme.BACKGROUND);
        JPanel form = LoginPanel.authSurface(new GridBagLayout());
        form.setBorder(new EmptyBorder(34, 44, 34, 44));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2; c.insets = new Insets(6, 8, 18, 8);
        JLabel title = label("TẠO TÀI KHOẢN", 27, Font.BOLD);
        title.setForeground(LoginPanel.INK);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        form.add(title, c);

        registerHostField = field("localhost");
        registerPortField = field("12345");
        registerUsernameField = field("");
        registerPasswordField = new JPasswordField();
        registerConfirmField = new JPasswordField();
        for (JTextField input : new JTextField[]{registerHostField, registerPortField,
                registerUsernameField, registerPasswordField, registerConfirmField}) {
            LoginPanel.styleRegistrationInput(input);
        }
        addFormRow(form, c, 1, "Máy chủ", registerHostField);
        addFormRow(form, c, 2, "Cổng", registerPortField);
        addFormRow(form, c, 3, "Tài khoản", registerUsernameField);
        addFormRow(form, c, 4, "Mật khẩu", registerPasswordField);
        addFormRow(form, c, 5, "Nhập lại", registerConfirmField);

        registerButton = new LoginPanel.NeonButton("Đăng ký", true);
        registerButton.addActionListener(e -> AUDIO.playClick());
        registerButton.addActionListener(e -> registerAccount());
        c.gridx = 0; c.gridy = 6; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(18, 8, 6, 8);
        form.add(registerButton, c);

        registerStatus = label("Tên tài khoản 3-16 ký tự; mật khẩu ít nhất 6 ký tự.", 12, Font.PLAIN);
        registerStatus.setForeground(LoginPanel.MUTED);
        c.gridy = 7; c.insets = new Insets(6, 8, 0, 8);
        form.add(registerStatus, c);

        JButton back = new LoginPanel.NeonButton("Quay lại đăng nhập", false);
        back.addActionListener(e -> AUDIO.playClick());
        back.addActionListener(e -> pages.show(root, "login"));
        c.gridy = 8; c.insets = new Insets(12, 8, 0, 8);
        form.add(back, c);

        for (Component component : form.getComponents()) {
            if (component instanceof JLabel label && label != registerStatus) label.setForeground(LoginPanel.INK);
        }
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        form.setPreferredSize(new Dimension(500, 605));
        center.add(form);
        outer.add(center, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildLobbyPanel() {
        JPanel page = LobbyStyle.surface(new BorderLayout(14, 14), 0);
        page.setBackground(AppTheme.BACKGROUND);
        page.setBorder(new EmptyBorder(16, 22, 16, 22));

        JPanel header = lobbyPanel(new BorderLayout(0, 10));
        JPanel toolbar = lobbyPanel(new BorderLayout(14, 0));
        JPanel brand = lobbyPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        JLabel logo = LobbyStyle.logo();
        logo.setForeground(LoginPanel.CYAN);
        brand.add(logo);
        brand.add(label("Sảnh chờ", 21, Font.BOLD));
        connectionBadge = label("● Đã kết nối", 12, Font.BOLD);
        connectionBadge.setForeground(AppTheme.SECONDARY);
        brand.add(connectionBadge);
        toolbar.add(brand);
        JPanel welcome = LobbyStyle.surface(new BorderLayout(12, 4), 2);
        welcome.setBorder(new EmptyBorder(16, 24, 18, 24));
        JPanel identity = lobbyPanel(new GridLayout(2, 1, 0, 4));
        lobbyTitle = label("Chào bạn!", 28, Font.BOLD);
        lobbyTitle.setForeground(LobbyStyle.PURPLE);
        identity.add(lobbyTitle);
        lobbyStats = label("Đang tải hồ sơ…", 13, Font.PLAIN);
        lobbyStats.setForeground(LoginPanel.MUTED);
        lobbyStats.setIcon(LobbyStyle.achievementIcon(false));
        lobbyStats.setIconTextGap(7);
        lobbyWins = label("Thắng: 0", 13, Font.PLAIN);
        lobbyWins.setForeground(LoginPanel.MUTED);
        lobbyWins.setIcon(LobbyStyle.achievementIcon(true));
        lobbyWins.setIconTextGap(7);
        JPanel statsRow = lobbyPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statsRow.add(lobbyStats);
        statsRow.add(Box.createHorizontalStrut(22));
        statsRow.add(lobbyWins);
        identity.add(statsRow);
        welcome.add(identity);
        welcome.add(new LobbyArtwork(true, false, true), BorderLayout.EAST);
        walletLabel = label("◆ 0 xu", 14, Font.BOLD);
        walletLabel.setForeground(AppTheme.GOLD_TEXT);
        walletLabel.setBorder(new EmptyBorder(10, 14, 10, 14));
        shopButton = illustratedLobbyButton("Cửa hàng", true);
        shopButton.setPreferredSize(new Dimension(120, 38));
        shopButton.addActionListener(e -> send(Message.of(Protocol.Type.SHOP_REQUEST)));
        logoutButton = illustratedLobbyButton("Đăng xuất", false);
        logoutButton.setPreferredSize(new Dimension(125, 38));
        logoutButton.addActionListener(e -> logout());
        JPanel accountActions = lobbyPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JPanel wallet = LobbyStyle.surface(new BorderLayout(), 1);
        wallet.add(walletLabel);
        accountActions.add(wallet);
        JButton personal = illustratedLobbyButton("Hồ sơ", false);
        personal.setPreferredSize(new Dimension(82, 38));
        personal.addActionListener(e -> { profileRequested = true; send(Message.of(Protocol.Type.PROFILE_REQUEST)); });
        accountActions.add(personal);
        accountActions.add(shopButton);
        accountActions.add(logoutButton);
        toolbar.add(accountActions, BorderLayout.EAST);
        header.add(toolbar, BorderLayout.NORTH);
        header.add(welcome);

        lobbyNotice = label("Bạn đang rảnh — có thể ghép trận, tạo phòng hoặc nhận lời mời.", 14, Font.BOLD);
        lobbyNotice.setOpaque(false);
        setBanner(lobbyNotice, lobbyNotice.getText(), INFO_BG, AppTheme.PRIMARY);

        page.add(header, BorderLayout.NORTH);

        JPanel playerSection = LobbyStyle.surface(new BorderLayout(10, 12), 1);
        playerSection.setBorder(new EmptyBorder(14, 14, 14, 14));
        JPanel playerHeader = lobbyPanel(new BorderLayout());
        playerHeader.setOpaque(false);
        playerHeader.add(label("Người chơi online", 18, Font.BOLD), BorderLayout.WEST);
        onlineCountLabel = label("0 người", 13, Font.BOLD);
        onlineCountLabel.setForeground(AppTheme.MUTED);
        JPanel playerHeaderActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        playerHeaderActions.setOpaque(false); playerHeaderActions.add(onlineCountLabel);
        conversationsButton = new ChatNotificationButton(this::showConversations);
        playerHeaderActions.add(conversationsButton);
        playerHeader.add(playerHeaderActions, BorderLayout.EAST);
        playerSection.add(playerHeader, BorderLayout.NORTH);

        lobbyList.setBackground(AppTheme.SURFACE);
        lobbyList.setForeground(TEXT);
        lobbyList.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        lobbyList.setSelectionBackground(AppTheme.PRIMARY);
        lobbyList.setFixedCellHeight(72);
        lobbyList.setOpaque(false);
        lobbyList.setCellRenderer(new PlayerRenderer());
        lobbyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lobbyList.setToolTipText("Bấm vào người chơi để kết bạn hoặc thì thầm; bấm + để mời vào phòng.");
        lobbyList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int index = lobbyList.locationToIndex(e.getPoint());
                if (index < 0) return;
                Rectangle bounds = lobbyList.getCellBounds(index, index);
                if (bounds == null || !bounds.contains(e.getPoint())) return;
                PlayerItem player = lobbyModel.get(index);
                if (player.username.equalsIgnoreCase(username)) return;
                if (e.getX() >= bounds.x + bounds.width - 56) invitePlayer(player);
                else showPlayerMenu(player, bounds.x + 12, bounds.y + bounds.height);
            }
        });
        lobbyList.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "playerMenu");
        lobbyList.getActionMap().put("playerMenu", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                int index = lobbyList.getSelectedIndex();
                if (index < 0) return;
                Rectangle bounds = lobbyList.getCellBounds(index, index);
                showPlayerMenu(lobbyModel.get(index), bounds.x + 12, bounds.y + bounds.height);
            }
        });
        lobbyList.getInputMap().put(KeyStroke.getKeyStroke("ADD"), "invitePlayer");
        lobbyList.getActionMap().put("invitePlayer", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { invitePlayer(lobbyList.getSelectedValue()); }
        });
        lobbyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshLobbyActions();
        });
        JScrollPane scroll = new JScrollPane(lobbyList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        JPanel invitation = lobbyPanel(new BorderLayout(0, 4));
        invitation.add(new LobbyArtwork(false, true, true));
        JLabel hint = label("Bấm vào người chơi để trò chuyện · Bấm + để mời vào phòng.", 12, Font.PLAIN);
        hint.setForeground(LoginPanel.MUTED);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        invitation.add(hint, BorderLayout.SOUTH);
        JPanel people = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                // Paint decoration behind the full-height list, without taking layout or mouse space.
                int height = Math.min(280, getHeight());
                if (height <= 0 || getWidth() <= 0) return;
                invitation.setSize(getWidth(), height);
                invitation.doLayout();
                Graphics2D g = (Graphics2D) graphics.create(0, getHeight() - height, getWidth(), height);
                g.setComposite(AlphaComposite.SrcOver.derive(0.45f));
                invitation.printAll(g);
                g.dispose();
            }
        };
        people.setOpaque(false);
        people.add(scroll, BorderLayout.CENTER);
        playerSection.add(people, BorderLayout.CENTER);

        JPanel actionColumn = lobbyPanel(new BorderLayout(0, 10));
        actionColumn.setPreferredSize(new Dimension(340, 10));

        JPanel quickMatch = LobbyStyle.surface(new GridBagLayout(), 1);
        quickMatch.setBorder(new EmptyBorder(14, 16, 14, 16));
        GridBagConstraints q = new GridBagConstraints();
        q.gridx = 0; q.gridy = 0; q.gridwidth = 2; q.weightx = 1; q.fill = GridBagConstraints.HORIZONTAL;
        q.insets = new Insets(0, 0, 10, 0);
        JLabel quickMatchTitle = label("Ghép nhanh", 19, Font.BOLD);
        quickMatchTitle.setForeground(LobbyStyle.PURPLE);
        JPanel quickHeading = lobbyPanel(new BorderLayout(8, 0));
        JPanel quickText = lobbyPanel(new BorderLayout(0, 8));
        quickText.add(quickMatchTitle, BorderLayout.CENTER);
        JLabel quickHint = label("Tìm trận phù hợp và chơi ngay!", 11, Font.PLAIN);
        quickHint.setForeground(MUTED);
        quickText.add(quickHint, BorderLayout.SOUTH);
        quickHeading.add(quickText);
        quickHeading.add(new JLabel(LobbyStyle.cat(68)), BorderLayout.EAST);
        quickMatch.add(quickHeading, q);
        q.gridwidth = 1; q.gridy++; q.weightx = 0; q.insets = new Insets(4, 0, 4, 10);
        quickMatch.add(label("Số người", 13, Font.BOLD), q);
        playerCountCombo = styledCombo(new String[]{"2 người", "3 người", "4 người"});
        q.gridx = 1; q.weightx = 1; q.insets = new Insets(4, 0, 4, 0);
        quickMatch.add(playerCountCombo, q);
        q.gridx = 0; q.gridy++; q.weightx = 0; q.insets = new Insets(4, 0, 4, 10);
        quickMatch.add(label("Kích thước", 13, Font.BOLD), q);
        boardSizeCombo = styledCombo(new String[]{"4 × 4 — Nhanh", "6 × 6 — Vừa", "8 × 8 — Thử thách"});
        q.gridx = 1; q.weightx = 1; q.insets = new Insets(4, 0, 4, 0);
        quickMatch.add(boardSizeCombo, q);
        q.gridx = 0; q.gridy++; q.weightx = 0; q.insets = new Insets(4, 0, 4, 10);
        JLabel themeLabel = label("Chủ đề", 13, Font.BOLD);
        quickMatch.add(themeLabel, q);
        lobbyThemeCombo = themeCombo();
        themeLabel.setLabelFor(lobbyThemeCombo);
        q.gridx = 1; q.weightx = 1; q.insets = new Insets(4, 0, 4, 0);
        quickMatch.add(lobbyThemeCombo, q);
        findMatchButton = illustratedLobbyButton("▶  Tìm trận", true);
        findMatchButton.addActionListener(e -> joinQueue(playerCountCombo.getSelectedIndex() + 2));
        q.gridx = 0; q.gridy++; q.gridwidth = 2; q.insets = new Insets(12, 0, 0, 0);
        quickMatch.add(findMatchButton, q);
        cancelQueueButton = illustratedLobbyButton("Hủy tìm trận", false);
        cancelQueueButton.addActionListener(e -> {
            selfStatus = "IDLE";
            refreshLobbyActions();
            send(Message.of(Protocol.Type.QUEUE_LEAVE));
        });
        q.gridy++;
        quickMatch.add(cancelQueueButton, q);

        JPanel privateRoom = LobbyStyle.surface(new BorderLayout(0, 10), 1);
        privateRoom.setBorder(new EmptyBorder(16, 18, 16, 18));
        JLabel privateRoomTitle = label("Phòng riêng", 19, Font.BOLD);
        privateRoomTitle.setForeground(LobbyStyle.PURPLE);
        privateRoom.add(privateRoomTitle, BorderLayout.NORTH);
        JLabel roomHint = label("Tạo mới hoặc tham gia bằng mã phòng.", 12, Font.PLAIN);
        roomHint.setForeground(MUTED);
        privateRoom.add(roomHint, BorderLayout.CENTER);
        createRoomButton = illustratedLobbyButton("+ Tạo phòng", false);
        createRoomButton.putClientProperty("gold", true);
        createRoomButton.addActionListener(e -> showCreateRoomDialog());
        joinRoomButton = illustratedLobbyButton("Nhập mã phòng", false);
        joinRoomButton.addActionListener(e -> {
            String code = showRoomCodeDialog();
            if (code != null) send(Message.of(Protocol.Type.ROOM_JOIN).with("code", code));
        });
        JPanel privateActions = lobbyPanel(new GridLayout(1, 2, 8, 0));
        privateActions.add(createRoomButton);
        privateActions.add(joinRoomButton);
        privateRoom.add(privateActions, BorderLayout.SOUTH);

        JPanel profile = LobbyStyle.surface(new BorderLayout(12, 0), 5);
        profile.setBorder(new EmptyBorder(12, 18, 14, 18));
        JLabel profileTitle = label("Hồ sơ & thành tích", 18, Font.BOLD);
        profileTitle.setForeground(Color.WHITE);
        profile.add(profileTitle, BorderLayout.WEST);
        JButton ranking = illustratedLobbyButton("Bảng xếp hạng", false);
        JButton history = illustratedLobbyButton("Lịch sử trận đấu", false);
        musicToggleButton = illustratedLobbyButton("Nhạc: Bật", false);
        effectsToggleButton = illustratedLobbyButton("SFX: Bật", false);
        ranking.addActionListener(e -> send(Message.of(Protocol.Type.RANKING_REQUEST)));
        history.addActionListener(e -> send(Message.of(Protocol.Type.HISTORY_REQUEST)));
        musicToggleButton.addActionListener(e -> {
            AUDIO.setMusicEnabled(!AUDIO.isMusicEnabled());
            if (AUDIO.isMusicEnabled()) AUDIO.startLobbyMusic();
            refreshAudioButtons();
        });
        effectsToggleButton.addActionListener(e -> {
            AUDIO.setEffectsEnabled(!AUDIO.isEffectsEnabled());
            refreshAudioButtons();
        });
        JPanel profileActions = lobbyPanel(new GridLayout(1, 4, 8, 0));
        profileActions.add(ranking);
        profileActions.add(history);
        profileActions.add(musicToggleButton);
        profileActions.add(effectsToggleButton);
        profile.add(profileActions);
        page.add(profile, BorderLayout.SOUTH);
        refreshAudioButtons();
        actionColumn.add(quickMatch, BorderLayout.CENTER);
        actionColumn.add(privateRoom, BorderLayout.SOUTH);

        JPanel content = lobbyPanel(new BorderLayout(16, 0));
        JPanel leftColumn = lobbyPanel(new BorderLayout(0, 12));
        JPanel noticeSurface = LobbyStyle.surface(new BorderLayout(), 3);
        noticeSurface.add(lobbyNotice);
        leftColumn.add(noticeSurface, BorderLayout.NORTH);
        leftColumn.add(playerSection);
        content.add(leftColumn, BorderLayout.CENTER);
        JScrollPane actionScroll = new JScrollPane(actionColumn,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        actionScroll.setBorder(BorderFactory.createEmptyBorder());
        actionScroll.setOpaque(false);
        actionScroll.getViewport().setOpaque(false);
        actionScroll.setPreferredSize(new Dimension(350, 10));
        actionScroll.getVerticalScrollBar().setUnitIncrement(18);
        actionColumn.setPreferredSize(new Dimension(330, 440));
        content.add(actionScroll, BorderLayout.EAST);
        page.add(content, BorderLayout.CENTER);
        refreshLobbyActions();
        return page;
    }

    private JPanel buildRoomPanel() {
        JPanel page = new JPanel(new BorderLayout(12, 12));
        page.setBackground(AppTheme.BACKGROUND);
        page.setBorder(new EmptyBorder(18, 22, 18, 22));
        JPanel top = lobbyPanel(new BorderLayout(0, 12));
        JPanel toolbar = lobbyPanel(new BorderLayout());
        JLabel brand = label("MEMORY GAME   |   Phòng chờ", 24, Font.BOLD);
        brand.setForeground(LoginPanel.CYAN);
        toolbar.add(brand);
        roomWallet = label("◆ 0 xu", 14, Font.BOLD);
        roomWallet.setForeground(AppTheme.GOLD_TEXT);
        roomWallet.setBorder(new EmptyBorder(8, 16, 8, 16));
        toolbar.add(roomWallet, BorderLayout.EAST);
        top.add(toolbar, BorderLayout.NORTH);
        JPanel header = LoginPanel.authSurface(new BorderLayout(12, 8));
        header.setBorder(new EmptyBorder(14, 18, 14, 18));
        JPanel roomIdentity = lobbyPanel(new GridLayout(2, 1, 0, 6));
        roomTitle = label("Phòng", 22, Font.BOLD);
        roomTitle.setForeground(LoginPanel.CYAN);
        roomHost = label("Đang tải thông tin phòng…", 13, Font.PLAIN);
        roomHost.setForeground(LoginPanel.MUTED);
        roomIdentity.add(roomTitle);
        roomIdentity.add(roomHost);
        header.add(roomIdentity);
        copyRoomCodeButton = lobbyButton("Sao chép mã", false);
        copyRoomCodeButton.setPreferredSize(new Dimension(160, 42));
        copyRoomCodeButton.addActionListener(e -> {
            if (!roomCode.isBlank()) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(roomCode), null);
                showInfo("Đã sao chép mã phòng: " + roomCode);
            }
        });
        header.add(copyRoomCodeButton, BorderLayout.EAST);
        roomStatusLabel = label("Đang chờ người chơi sẵn sàng", 13, Font.BOLD);
        setBanner(roomStatusLabel, roomStatusLabel.getText(), INFO_BG, AppTheme.PRIMARY);
        header.add(roomStatusLabel, BorderLayout.SOUTH);
        top.add(header);
        page.add(top, BorderLayout.NORTH);

        JPanel memberSection = LoginPanel.authSurface(new BorderLayout(0, 12));
        memberSection.setBorder(new EmptyBorder(16, 16, 16, 16));
        roomMemberCount = label("Thành viên  •  0/4", 19, Font.BOLD);
        memberSection.add(roomMemberCount, BorderLayout.NORTH);
        roomMemberGrid = lobbyPanel(new GridLayout(2, 2, 10, 10));
        memberSection.add(roomMemberGrid);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshRoomActions();
        });
        rebuildRoomMembers();

        roomInvites = new RoomInvitePanel(target -> {
            if (client.isConnected()) {
                send(Message.of(Protocol.Type.INVITE_SEND).with("username", target));
            } else showInfo("Chưa kết nối tới server.");
        }, this::openWhisper, this::showConversations);
        JPanel content = lobbyPanel(new BorderLayout(12, 0));
        content.add(memberSection);
        content.add(roomInvites, BorderLayout.EAST);
        page.add(content);

        JPanel footer = LoginPanel.authSurface(new BorderLayout(0, 10));
        footer.setBorder(new EmptyBorder(12, 14, 12, 14));
        JPanel actions = lobbyPanel(new GridLayout(1, 4, 10, 0));
        readyButton = lobbyButton("Sẵn sàng", true);
        startButton = lobbyButton("Bắt đầu", false);
        kickButton = lobbyButton("Mời ra khỏi phòng", false);
        JButton leave = lobbyButton("Rời phòng", false);
        readyButton.addActionListener(e -> {
            if (postGame) send(Message.of(Protocol.Type.PLAY_AGAIN));
            else send(Message.of(Protocol.Type.ROOM_READY).with("ready", !selfReady));
        });
        startButton.addActionListener(e -> send(Message.of(Protocol.Type.ROOM_START)));
        kickButton.addActionListener(e -> {
            RoomPlayerItem selected = roomList.getSelectedValue();
            if (selected != null) send(Message.of(Protocol.Type.ROOM_KICK).with("username", selected.username));
        });
        leave.addActionListener(e -> send(Message.of(Protocol.Type.ROOM_LEAVE)));
        actions.add(readyButton); actions.add(startButton); actions.add(kickButton); actions.add(leave);
        footer.add(actions);
        JLabel hint = label("Tất cả người chơi cần sẵn sàng trước khi bắt đầu.", 12, Font.PLAIN);
        hint.setForeground(LoginPanel.MUTED);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        footer.add(hint, BorderLayout.SOUTH);
        page.add(footer, BorderLayout.SOUTH);
        return page;
    }

    private void rebuildRoomMembers() {
        roomMemberGrid.removeAll();
        int capacity = matchmakingRoom ? Math.max(2, Math.min(4, matchmakingTargetPlayers)) : roomCapacity;
        roomMemberCount.setText("Thành viên  •  " + roomModel.size() + "/" + capacity);
        for (int i = 0; i < capacity; i++) {
            final int index = i;
            RoomPlayerItem member = i < roomModel.size() ? roomModel.get(i) : null;
            String state = member == null ? "Đang chờ người chơi" : !member.online ? "Mất kết nối"
                    : member.postGame ? (member.replay ? "Đã chọn chơi lại" : "Chưa chọn chơi lại")
                    : member.ready ? "Sẵn sàng" : "Chưa sẵn sàng";
            RoomMemberCard card = new RoomMemberCard(member == null ? "" : member.username,
                    member != null && member.host, member != null && member.username.equalsIgnoreCase(username),
                    state, member != null && member.online && (member.ready || member.replay),
                    member == null || member.title.isBlank() ? "" : titleName(member.title));
            card.setEnabled(member != null);
            card.setSelected(roomList.getSelectedIndex() == i && member != null);
            card.addActionListener(e -> roomList.setSelectedIndex(index));
            roomMemberGrid.add(card);
        }
        roomMemberGrid.revalidate();
        roomMemberGrid.repaint();
    }

    private JPanel buildShopPanel() {
        JPanel page = panel(new BorderLayout(16, 16));
        page.setBorder(new EmptyBorder(20, 22, 20, 22));

        JPanel header = panel(new BorderLayout(12, 0));
        JPanel identity = panel(new GridLayout(0, 1, 0, 2));
        JLabel shopTitle = label("Cửa hàng", 24, Font.BOLD);
        shopTitle.setForeground(VIOLET_LIGHT);
        identity.add(shopTitle);
        JLabel subtitle = label("Vật phẩm trang trí — không ảnh hưởng luật chơi", 13, Font.PLAIN);
        subtitle.setForeground(MUTED);
        identity.add(subtitle);
        header.add(identity, BorderLayout.WEST);
        shopBalance = label("◆ 0 xu", 16, Font.BOLD);
        shopBalance.setForeground(AppTheme.GOLD_TEXT);
        shopBalance.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(9, 14, 9, 14)));
        header.add(shopBalance, BorderLayout.EAST);
        page.add(header, BorderLayout.NORTH);

        JPanel catalog = new RoundedPanel(new BorderLayout(0, 10));
        catalog.setBorder(new EmptyBorder(14, 14, 14, 14));
        catalog.add(label("Danh mục vật phẩm", 17, Font.BOLD), BorderLayout.NORTH);
        shopList.setBackground(PANEL);
        shopList.setForeground(TEXT);
        shopList.setFixedCellHeight(72);
        shopList.setSelectionBackground(AppTheme.SELECTED);
        shopList.setCellRenderer(new ShopRenderer());
        shopList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshShopSelection();
        });
        JScrollPane catalogScroll = new JScrollPane(shopList);
        catalogScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        catalog.add(catalogScroll, BorderLayout.CENTER);

        JPanel detail = new RoundedPanel(new BorderLayout(12, 12));
        detail.setPreferredSize(new Dimension(360, 10));
        detail.setBorder(new EmptyBorder(22, 22, 22, 22));
        JPanel detailText = panel(new GridLayout(0, 1, 0, 10));
        detailText.setOpaque(false);
        JLabel type = label("VẬT PHẨM ĐANG CHỌN", 12, Font.BOLD);
        type.setForeground(AppTheme.MUTED);
        shopSelectionTitle = label("Chọn một vật phẩm", 20, Font.BOLD);
        shopSelectionDescription = label("Xem mô tả, giá và trạng thái sở hữu tại đây.", 14, Font.PLAIN);
        shopSelectionDescription.setForeground(MUTED);
        detailText.add(type);
        detailText.add(shopSelectionTitle);
        detailText.add(shopSelectionDescription);
        detail.add(detailText, BorderLayout.NORTH);
        JPanel previewArea = new ArenaBoardPanel(new GridBagLayout());
        previewArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(16, 16, 16, 16)));
        shopCardPreview = new CardButton("?");
        shopCardPreview.setFont(new Font("Segoe UI", Font.BOLD, 44));
        shopCardPreview.setPreferredSize(new Dimension(150, 190));
        shopCardPreview.setEnabled(false);
        previewArea.add(shopCardPreview);
        detail.add(previewArea, BorderLayout.CENTER);
        shopPrimaryButton = button("Chọn vật phẩm", ACCENT);
        shopPrimaryButton.setEnabled(false);
        shopPrimaryButton.addActionListener(e -> {
            ShopItem selected = shopList.getSelectedValue();
            if (selected == null) return;
            Protocol.Type typeRequest = selected.owned ? Protocol.Type.SHOP_EQUIP : Protocol.Type.SHOP_BUY;
            send(Message.of(typeRequest).with("productId", selected.id));
        });
        detail.add(shopPrimaryButton, BorderLayout.SOUTH);

        JPanel content = panel(new BorderLayout(14, 0));
        content.add(catalog, BorderLayout.CENTER);
        content.add(detail, BorderLayout.EAST);
        page.add(content, BorderLayout.CENTER);

        JButton back = button("Quay lại Lobby", AppTheme.MUTED);
        back.addActionListener(e -> pages.show(root, "lobby"));
        JPanel footer = panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        back.setPreferredSize(new Dimension(160, 42));
        footer.add(back);
        page.add(footer, BorderLayout.SOUTH);
        return page;
    }

    private JPanel buildGamePanel() {
        JPanel page = panel(new BorderLayout(16, 16));
        page.setBorder(new EmptyBorder(18, 20, 18, 20));
        JPanel header = new RoundedPanel(new BorderLayout(12, 8));
        header.setBorder(new EmptyBorder(12, 16, 12, 16));
        gameTurn = label("Lượt:", 18, Font.BOLD);
        gameTimer = label("15 giây", 18, Font.BOLD);
        gameTurn.setForeground(VIOLET_LIGHT);
        gameTimer.setForeground(CORAL_LIGHT);
        gameTimer.setHorizontalAlignment(SwingConstants.RIGHT);
        header.add(gameTurn, BorderLayout.WEST);
        header.add(gameTimer, BorderLayout.EAST);
        turnProgress = new JProgressBar(0, 150);
        turnProgress.setUI(new javax.swing.plaf.basic.BasicProgressBarUI());
        turnProgress.setValue(150);
        turnProgress.setStringPainted(false);
        turnProgress.setForeground(AppTheme.ORANGE);
        turnProgress.setBackground(FIELD);
        turnProgress.setBorderPainted(false);
        turnProgress.setPreferredSize(new Dimension(10, 8));
        header.add(turnProgress, BorderLayout.SOUTH);
        page.add(header, BorderLayout.NORTH);

        boardPanel = new ArenaBoardPanel(new GridLayout(8, 8, 7, 7));
        boardPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        setupBoard(8);
        JPanel boardSurface = new RoundedPanel(new BorderLayout());
        boardSurface.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(VIOLET, 2), new EmptyBorder(8, 8, 8, 8)));
        boardSurface.add(boardPanel, BorderLayout.CENTER);
        page.add(boardSurface, BorderLayout.CENTER);

        JPanel sidebar = new RoundedPanel(new BorderLayout(10, 10));
        sidebar.setBorder(new EmptyBorder(18, 18, 18, 18));
        sidebar.setPreferredSize(new Dimension(300, 10));
        JPanel matchInfo = panel(new GridLayout(0, 1, 5, 5));
        matchInfo.setOpaque(false);
        JLabel matchInfoTitle = label("Thông tin trận", 19, Font.BOLD);
        matchInfoTitle.setForeground(VIOLET_LIGHT);
        matchInfo.add(matchInfoTitle);
        gamePairs = label("Đã ghép: 0/8", 15, Font.BOLD);
        gamePairs.setForeground(CORAL_LIGHT);
        gameScores = label("Điểm:", 18, Font.PLAIN);
        gameScores.setVerticalAlignment(SwingConstants.TOP);
        matchInfo.add(gamePairs);
        matchInfo.add(gameScores);
        matchInfo.add(label("Chủ đề thẻ", 14, Font.BOLD));
        gameThemeCombo = themeCombo();
        matchInfo.add(gameThemeCombo);
        sidebar.add(matchInfo, BorderLayout.NORTH);

        JPanel rules = panel(new BorderLayout());
        rules.setOpaque(false);
        JPanel ruleList = panel(new GridLayout(0, 1, 0, 4));
        ruleList.setOpaque(false);
        JLabel rulesTitle = label("Luật chơi", 17, Font.BOLD);
        rulesTitle.setForeground(AppTheme.PRIMARY);
        ruleList.add(rulesTitle);
        ruleList.add(gameRule("1", "Mỗi lượt chọn 2 thẻ đang úp trong 15 giây."));
        ruleList.add(gameRule("2", "Hai thẻ giống nhau: +1 điểm và tiếp tục lượt."));
        ruleList.add(gameRule("3", "Hai thẻ khác nhau: úp lại sau 2 giây và chuyển lượt."));
        ruleList.add(gameRule("4", "Trận kết thúc khi tìm hết các cặp; người nhiều điểm nhất thắng."));
        ruleList.add(gameRule("5", "Vòng màu trên cặp thẻ cho biết người đã ghép đúng."));
        rules.add(ruleList, BorderLayout.NORTH);
        chatPanel = new ChatPanel(text -> send(Message.of(Protocol.Type.CHAT_SEND).with("text", text)));
        chatPanel.useTranscriptAvatars(name -> chatAvatar(name, 24));
        JTabbedPane matchTabs = new JTabbedPane();
        matchTabs.setBackground(AppTheme.INSET);
        matchTabs.setForeground(TEXT);
        matchTabs.addTab("Chat phòng", chatPanel);
        matchTabs.addTab("Luật chơi", rules);
        sidebar.add(matchTabs, BorderLayout.CENTER);

        JButton quit = button("Bỏ cuộc và về Lobby", AppTheme.DANGER);
        quit.addActionListener(e -> {
            int answer = showActionDialog("Xác nhận bỏ cuộc",
                    "Bạn sẽ bị tính thua và trở về Lobby. Hành động này không thể hoàn tác.", null,
                    new String[]{"Bỏ cuộc", "Tiếp tục chơi"},
                    new Color[]{AppTheme.DANGER, AppTheme.MUTED}, 460, 220);
            if (answer == 0) send(Message.of(Protocol.Type.BACK_LOBBY));
        });
        sidebar.add(quit, BorderLayout.SOUTH);
        page.add(sidebar, BorderLayout.EAST);
        return page;
    }

    private void connectAndLogin() {
        savedHost = hostField.getText().trim();
        savedUsername = usernameField.getText().trim();
        savedPassword = new String(passwordField.getPassword());
        try {
            savedPort = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            loginStatus.setText("Cổng không hợp lệ.");
            return;
        }
        if (savedHost.isBlank() || savedUsername.isBlank() || savedPassword.length() < 4) {
            loginStatus.setText("Hãy nhập máy chủ, tài khoản và mật khẩu từ 4 ký tự.");
            return;
        }
        pendingAuthType = Protocol.Type.LOGIN;
        authenticated = false;
        reconnecting = false;
        if (reconnectTimer != null) reconnectTimer.stop();
        loginButton.setEnabled(false);
        loginStatus.setText("Đang kết nối...");
        openConnection(false);
    }

    private void registerAccount() {
        String password = new String(registerPasswordField.getPassword());
        String confirmation = new String(registerConfirmField.getPassword());
        if (!password.equals(confirmation)) {
            registerStatus.setText("Mật khẩu nhập lại chưa khớp.");
            registerStatus.setForeground(AppTheme.DANGER);
            return;
        }
        savedHost = registerHostField.getText().trim();
        savedUsername = registerUsernameField.getText().trim();
        savedPassword = password;
        try {
            savedPort = Integer.parseInt(registerPortField.getText().trim());
        } catch (NumberFormatException e) {
            registerStatus.setText("Cổng kết nối không hợp lệ.");
            registerStatus.setForeground(AppTheme.DANGER);
            return;
        }
        if (savedHost.isBlank() || !savedUsername.matches("[A-Za-z0-9_]{3,16}") || password.length() < 6) {
            registerStatus.setText("Kiểm tra máy chủ, tên tài khoản và mật khẩu tối thiểu 6 ký tự.");
            registerStatus.setForeground(AppTheme.DANGER);
            return;
        }
        pendingAuthType = Protocol.Type.REGISTER;
        authenticated = false;
        registerButton.setEnabled(false);
        registerStatus.setForeground(MUTED);
        registerStatus.setText("Đang tạo tài khoản...");
        openConnection(false);
    }

    private void openConnection(boolean automatic) {
        if (reconnectAttemptRunning || closing) return;
        reconnectAttemptRunning = true;
        Protocol.Type authType = automatic ? Protocol.Type.LOGIN : pendingAuthType;
        new Thread(() -> {
            try {
                client.connect(savedHost, savedPort,
                        message -> SwingUtilities.invokeLater(() -> handle(message)),
                        reason -> SwingUtilities.invokeLater(() -> disconnected(reason)));
                client.send(Message.of(authType)
                        .with("username", savedUsername).with("password", savedPassword));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (automatic) {
                        loginStatus.setText("Chưa nối lại được (lần " + reconnectAttempts + "): " + e.getMessage());
                    } else {
                        loginButton.setEnabled(true);
                        registerButton.setEnabled(true);
                        JLabel target = authType == Protocol.Type.REGISTER ? registerStatus : loginStatus;
                        target.setText("Không kết nối được: " + e.getMessage());
                        target.setForeground(AppTheme.DANGER);
                    }
                });
            } finally {
                reconnectAttemptRunning = false;
            }
        }, automatic ? "reconnect-thread" : "connect-thread").start();
    }

    private void handle(Message message) {
        switch (message.type()) {
            case LOGIN_OK -> {
                String nextScope = savedHost.toLowerCase(Locale.ROOT) + ":" + savedPort + "/" + message.string("username").toLowerCase(Locale.ROOT);
                if (!whisperScope.equals(nextScope)) {
                    resetWhispers(); friends.clear(); roomInvites.updateFriends(friends); whisperScope = nextScope;
                    try {
                        whisperHistory = new WhisperHistory(java.nio.file.Path.of(System.getProperty("user.home"), ".memory-game-online", "chats"),
                                savedHost, savedPort, message.string("username"));
                    } catch (java.io.IOException e) {
                        SwingUtilities.invokeLater(() -> showInfo("Không đọc được lịch sử đã lưu. Tin nhắn mới chỉ được giữ trong phiên này."));
                    }
                }
                username = message.string("username");
                authenticated = true;
                coins = message.integer("coins");
                currentRating = message.integer("rating");
                currentWins = message.integer("wins");
                equippedTheme = message.string("equippedTheme");
                equippedTitle = message.string("equippedTitle");
                applyEquippedTheme();
                reconnecting = false;
                reconnectAttempts = 0;
                if (reconnectTimer != null) reconnectTimer.stop();
                connectionBadge.setText("● Đã kết nối");
                connectionBadge.setForeground(AppTheme.PRIMARY);
                updateWalletLabels();
                updateLobbyIdentity();
                loginButton.setEnabled(true);
                pages.show(root, "lobby");
                AUDIO.startLobbyMusic();
            }
            case PROFILE_UPDATE -> {
                if (!profileRequested && (profileDialog == null || !profileDialog.isDisplayable())) break;
                profileRequested = false;
                if (profileDialog != null && profileDialog.isDisplayable()) { profilePanel.update(message); }
                else {
                    profileDialog = new JDialog(this, "Thông tin cá nhân", false);
                    profilePanel = new ProfilePanel(message,
                            avatar -> send(Message.of(Protocol.Type.AVATAR_UPDATE).with("avatar", avatar)),
                            (oldPassword, newPassword) -> {
                                pendingPassword = newPassword;
                                send(Message.of(Protocol.Type.PASSWORD_CHANGE).with("currentPassword", oldPassword).with("newPassword", newPassword));
                            }, () -> profileDialog.dispose());
                    profileDialog.setContentPane(profilePanel);
                    profileDialog.setSize(620, 540); profileDialog.setMinimumSize(new Dimension(620, 540));
                    profileDialog.setLocationRelativeTo(this); profileDialog.setVisible(true);
                }
            }
            case PROFILE_RESULT -> {
                if (message.string("action").equals("password")) {
                    if (message.bool("success") && pendingPassword != null) savedPassword = pendingPassword;
                    pendingPassword = null;
                }
                if (profilePanel != null) profilePanel.result(message);
            }
            case REGISTER_OK -> {
                registerButton.setEnabled(true);
                loginButton.setEnabled(true);
                client.close();
                usernameField.setText(message.string("username"));
                passwordField.setText(savedPassword);
                hostField.setText(savedHost);
                portField.setText(String.valueOf(savedPort));
                loginStatus.setForeground(AppTheme.PRIMARY);
                loginStatus.setText("Đăng ký thành công. Hãy đăng nhập tài khoản mới.");
                pages.show(root, "login");
            }
            case ERROR -> {
                loginButton.setEnabled(true);
                registerButton.setEnabled(true);
                if (!authenticated) {
                    JLabel target = pendingAuthType == Protocol.Type.REGISTER ? registerStatus : loginStatus;
                    target.setText(message.string("message"));
                    target.setForeground(AppTheme.DANGER);
                } else {
                    refreshLobbyActions();
                    refreshRoomActions();
                    showInfo(message.string("message"));
                }
            }
            case LOBBY_UPDATE -> updateLobby(message);
            case ROOM_UPDATE -> updateRoom(message);
            case INVITE_RECEIVED -> receiveInvite(message);
            case FRIEND_RECEIVED -> {
                int choice = showActionDialog("Lời mời kết bạn", message.string("from") + " muốn kết bạn với bạn.",
                        null, new String[]{"Chấp nhận", "Từ chối"}, new Color[]{ACCENT, MUTED}, 460, 230);
                send(Message.of(Protocol.Type.FRIEND_REPLY).with("requestId", message.string("requestId")).with("accepted", choice == 0));
            }
            case FRIEND_UPDATE -> {
                if (message.get("friends") != null) { friends.clear(); friends.addAll(message.<List<String>>value("friends")); roomInvites.updateFriends(friends); lobbyList.repaint(); }
                if (!message.string("notice").isBlank()) setBanner(lobbyNotice, message.string("notice"), INFO_BG, ACCENT);
            }
            case WHISPER_MESSAGE -> {
                String peer = message.string("from").equalsIgnoreCase(username) ? message.string("to") : message.string("from");
                memorygame.common.Protocol.ChatEntry entry = message.value("entry");
                if (entry != null) {
                    try { whisperHistory.append(peer, entry); }
                    catch (java.io.IOException e) { setBanner(lobbyNotice, "Không thể lưu lịch sử tin nhắn trên máy này.", WARNING_BG, MUTED); }
                    String key = peer.toLowerCase(Locale.ROOT);
                    ChatPanel panel = whispers.get(key);
                    if (panel != null) panel.setMessages(whisperHistory.messages(peer));
                    JDialog window = whisperWindows.get(key);
                    unreadMessages.receive(peer, !message.string("from").equalsIgnoreCase(username),
                            window != null && window.isVisible() && window.isFocused());
                    refreshConversations();
                }
            }
            case GAME_START -> startGame(message);
            case CHAT_MESSAGE -> {
                if (roomCode.equals(message.string("code"))) chatPanel.append(message.value("entry"));
            }
            case CARD_REVEALED -> revealCard(message.integer("index"), message.integer("value"));
            case CARD_HIDDEN -> hideCards(message.value("indexes"));
            case MATCH_RESULT -> matchResult(message);
            case TURN_UPDATE -> updateTurn(message);
            case GAME_END -> gameEnd(message);
            case RANKING_UPDATE -> showRanking(message);
            case HISTORY_UPDATE -> showHistory(message);
            case WALLET_UPDATE -> updateWallet(message);
            case SHOP_UPDATE -> updateShop(message);
            default -> { }
        }
    }

    private void updateLobby(Message message) {
        String notice = message.string("notice");
        if (!notice.isBlank()) lobbyNotice.setText(notice);
        List<Map<String, Object>> players = message.value("players");
        if (players == null) return;
        PlayerItem selectedPlayer = lobbyList.getSelectedValue();
        String selectedName = selectedPlayer == null ? "" : selectedPlayer.username;
        lobbyModel.clear();
        for (Map<String, Object> row : players) {
            String playerName = String.valueOf(row.get("username"));
            lobbyAvatars.put(playerName, ProfilePanel.icon(Objects.toString(row.get("avatar"), ""), 50));
            String status = String.valueOf(row.get("status"));
            lobbyModel.addElement(new PlayerItem(playerName, status,
                    ((Number) row.get("rating")).intValue(), ((Number) row.get("wins")).intValue(),
                    Objects.toString(row.get("title"), "")));
            if (playerName.equalsIgnoreCase(username)) {
                selfStatus = status;
                currentRating = ((Number) row.get("rating")).intValue();
                currentWins = ((Number) row.get("wins")).intValue();
                updateLobbyIdentity();
            }
        }
        for (int i = 0; i < lobbyModel.size(); i++) {
            if (lobbyModel.get(i).username.equals(selectedName)) lobbyList.setSelectedIndex(i);
        }
        whispers.values().forEach(ChatPanel::refreshBubbles);
        if (chatPanel != null) chatPanel.refreshTranscriptAvatars();
        onlineCountLabel.setText(players.size() + " người online");
        roomInvites.updatePlayers(players, username);
        refreshLobbyActions();
    }

    private void updateLobbyIdentity() {
        lobbyTitle.setText("Chào " + username + "!");
        lobbyStats.setText("Điểm xếp hạng: " + currentRating);
        lobbyWins.setText("Thắng: " + currentWins
                + (equippedTitle.isBlank() ? "" : "     •     " + titleName(equippedTitle)));
        lobbyWins.setToolTipText(lobbyWins.getText());
        lobbyStats.setToolTipText(lobbyStats.getText());
    }

    private static JPanel lobbyPanel(LayoutManager layout) {
        JPanel result = new JPanel(layout);
        result.setOpaque(false);
        return result;
    }

    private static JButton illustratedLobbyButton(String title, boolean primary) {
        JButton button = LobbyStyle.button(title, primary);
        button.addActionListener(e -> AUDIO.playClick());
        return button;
    }

    private static JButton lobbyButton(String title, boolean primary) {
        JButton result = new LoginPanel.NeonButton(title, primary);
        result.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        result.setPreferredSize(new Dimension(100, 42));
        result.addActionListener(e -> AUDIO.playClick());
        return result;
    }

    private void updateWallet(Message message) {
        coins = message.integer("coins");
        updateWalletLabels();
    }

    private void updateWalletLabels() {
        String text = "◆ " + coins + " xu";
        if (walletLabel != null) walletLabel.setText(text);
        if (shopBalance != null) shopBalance.setText(text);
        if (roomWallet != null) roomWallet.setText(text);
    }

    private void updateShop(Message message) {
        ShopItem previous = shopList.getSelectedValue();
        String previousId = previous == null ? "" : previous.id;
        coins = message.integer("coins");
        equippedTheme = message.string("equippedTheme");
        equippedTitle = message.string("equippedTitle");
        updateWalletLabels();
        applyEquippedTheme();
        updateLobbyIdentity();
        List<Map<String, Object>> products = message.value("products");
        shopModel.clear();
        if (products != null) {
            for (Map<String, Object> row : products) {
                shopModel.addElement(new ShopItem(
                        Objects.toString(row.get("id"), ""),
                        Objects.toString(row.get("name"), ""),
                        Objects.toString(row.get("description"), ""),
                        ((Number) row.getOrDefault("price", 0)).intValue(),
                        Objects.toString(row.get("type"), ""),
                        Objects.toString(row.get("color"), AppTheme.hex(AppTheme.PRIMARY)),
                        Boolean.TRUE.equals(row.get("owned")),
                        Boolean.TRUE.equals(row.get("equipped"))));
            }
        }
        int selectedIndex = 0;
        for (int i = 0; i < shopModel.size(); i++) {
            if (shopModel.get(i).id.equals(previousId)) {
                selectedIndex = i;
                break;
            }
        }
        if (!shopModel.isEmpty()) shopList.setSelectedIndex(selectedIndex);
        refreshShopSelection();
        pages.show(root, "shop");
    }

    private void refreshShopSelection() {
        if (shopPrimaryButton == null) return;
        ShopItem selected = shopList.getSelectedValue();
        if (selected == null) {
            shopSelectionTitle.setText("Chọn một vật phẩm");
            shopSelectionDescription.setText("Xem mô tả, giá và trạng thái sở hữu tại đây.");
            shopPrimaryButton.setText("Chọn vật phẩm");
            shopPrimaryButton.setEnabled(false);
            shopPreviewTheme = "theme_classic";
            return;
        }
        shopSelectionTitle.setText(selected.name);
        shopSelectionDescription.setText("<html><div style='width:290px'>" + selected.description
                + "<br><br><b>Loại:</b> " + (selected.type.equals("THEME") ? "Mặt sau thẻ" : "Danh hiệu")
                + "<br><b>Giá:</b> " + (selected.price == 0 ? "Miễn phí" : selected.price + " xu")
                + "</div></html>");
        if (selected.type.equals("THEME")) {
            shopPreviewTheme = selected.id;
            try {
                shopCardPreview.setBackground(Color.decode(selected.color));
            } catch (NumberFormatException ignored) {
                shopCardPreview.setBackground(AppTheme.PRIMARY);
            }
            shopCardPreview.setText("?");
            shopCardPreview.setForeground(Color.WHITE);
            shopCardPreview.setThemeEffect(shopPreviewTheme, visualEffectPhase);
        } else {
            shopPreviewTheme = selected.id;
            boolean champion = "title_champion".equals(selected.id);
            shopCardPreview.setBackground(champion ? new Color(92, 55, 8) : new Color(12, 62, 82));
            shopCardPreview.setForeground(champion ? new Color(254, 240, 138) : new Color(165, 243, 252));
            shopCardPreview.setText(champion ? "★" : "◆");
            shopCardPreview.setThemeEffect(shopPreviewTheme, visualEffectPhase);
        }
        if (selected.equipped) {
            shopPrimaryButton.setText("Đang trang bị");
            shopPrimaryButton.setEnabled(false);
        } else if (selected.owned) {
            shopPrimaryButton.setText("Trang bị");
            shopPrimaryButton.setEnabled(true);
        } else {
            shopPrimaryButton.setText("Mua • " + selected.price + " xu");
            shopPrimaryButton.setEnabled(coins >= selected.price);
        }
    }

    private void applyEquippedTheme() {
        cardBackColor = switch (equippedTheme) {
            case "theme_ocean" -> new Color(14, 116, 144);
            case "theme_violet" -> new Color(109, 40, 217);
            case "theme_gold" -> new Color(161, 98, 7);
            default -> AppTheme.PRIMARY;
        };
        for (int i = 0; i < cards.length; i++) {
            if (cards[i] instanceof CardButton card) {
                card.setThemeEffect(equippedTheme, visualEffectPhase);
            }
            if (cardStates[i] == 0) cards[i].setBackground(cardBackColor);
        }
        if (boardPanel != null) boardPanel.repaint();
    }

    private void updateCardThemeEffects() {
        boolean animateBoard = !"theme_classic".equals(equippedTheme) && cards.length > 0;
        boolean animatePreview = shopCardPreview != null;
        if (!animateBoard && !animatePreview) return;
        visualEffectPhase = (visualEffectPhase + 0.025) % 1.0;
        if (animateBoard) {
            for (JButton button : cards) {
                if (button instanceof CardButton card) card.setThemeEffect(equippedTheme, visualEffectPhase);
            }
        }
        if (animatePreview) shopCardPreview.setThemeEffect(shopPreviewTheme, visualEffectPhase);
        if (shopList != null) shopList.repaint();
    }

    private void refreshAudioButtons() {
        if (musicToggleButton != null) {
            musicToggleButton.setText("Nhạc: " + (AUDIO.isMusicEnabled() ? "Bật" : "Tắt"));
        }
        if (effectsToggleButton != null) {
            effectsToggleButton.setText("SFX: " + (AUDIO.isEffectsEnabled() ? "Bật" : "Tắt"));
        }
    }

    private void logout() {
        if (profileDialog != null) profileDialog.dispose();
        profilePanel = null; pendingPassword = null;
        if (!"IDLE".equals(selfStatus)) {
            showInfo("Bạn chỉ có thể đăng xuất khi đang rảnh tại Lobby.");
            return;
        }
        if (reconnectTimer != null) reconnectTimer.stop();
        reconnecting = false;
        authenticated = false;
        AUDIO.stopLobbyMusic();
        client.close();
        resetWhispers(); friends.clear(); roomInvites.updateFriends(friends);
        username = "";
        roomCode = "";
        selfStatus = "IDLE";
        coins = 0;
        updateWalletLabels();
        savedPassword = "";
        passwordField.setText("");
        loginStatus.setForeground(MUTED);
        loginStatus.setText("Bạn đã đăng xuất an toàn.");
        pages.show(root, "login");
    }

    private static String titleName(String id) {
        return switch (id) {
            case "title_memory" -> "Bậc thầy trí nhớ";
            case "title_champion" -> "Nhà vô địch";
            default -> "";
        };
    }

    private void refreshLobbyActions() {
        if (findMatchButton == null) return;
        boolean idle = "IDLE".equals(selfStatus);
        boolean queued = "QUEUE".equals(selfStatus);

        playerCountCombo.setEnabled(idle);
        boardSizeCombo.setEnabled(idle);
        lobbyThemeCombo.setEnabled(idle);
        findMatchButton.setVisible(!queued);
        findMatchButton.setEnabled(idle);
        cancelQueueButton.setVisible(queued);
        cancelQueueButton.setEnabled(queued);
        createRoomButton.setEnabled(idle);
        joinRoomButton.setEnabled(idle);
        lobbyList.repaint();
        shopButton.setEnabled(idle);
        logoutButton.setEnabled(idle);

        if (queued) {
            setBanner(lobbyNotice, "Đang tìm trận phù hợp — bạn có thể hủy để đổi chế độ.",
                    WARNING_BG, AppTheme.GOLD_TEXT);
        } else if (idle) {
            setBanner(lobbyNotice, "Bạn đang rảnh — chọn một hành động để bắt đầu.",
                    INFO_BG, AppTheme.PRIMARY);
        } else {
            setBanner(lobbyNotice, "Bạn đang ở trạng thái " + selfStatus
                            + ". Các hành động Lobby đã được khóa.",
                    AppTheme.INSET, MUTED);
        }
        lobbyNotice.setOpaque(false);
        lobbyNotice.setForeground(Color.WHITE);
        lobbyNotice.setBorder(new EmptyBorder(12, 16, 14, 16));
        lobbyNotice.getParent().revalidate();
        lobbyNotice.getParent().repaint();
    }

    private void updateRoom(Message message) {
        List<Map<String, Object>> players = message.value("players");
        if (players == null) return;
        boolean belongs = players.stream().anyMatch(row -> username.equalsIgnoreCase(String.valueOf(row.get("username"))));
        if (!belongs) {
            roomCode = "";
            chatPanel.setSendingEnabled(false);
            chatPanel.setMessages(List.of());
            matchmakingRoom = false;
            roomStartCountdownDeadline = 0;
            pages.show(root, "lobby");
            AUDIO.startLobbyMusic();
            return;
        }
        roomCode = message.string("code");
        roomCapacity = message.integer("capacity") >= 2 ? message.integer("capacity") : 4;
        applyRoomTheme(message);
        roomHostName = message.string("host");
        matchmakingRoom = message.bool("matchmaking");
        matchmakingTargetPlayers = message.integer("targetPlayers");
        roomStartCountdownDeadline = message.longValue("countdownDeadline");
        postGame = message.bool("postGame");
        replayDeadline = message.longValue("replayDeadline");
        List<String> receivedVotes = message.value("replayVotes");
        List<String> replayVotes = receivedVotes == null ? List.of() : receivedVotes;
        int roomBoardSize = message.integer("boardSize");
        roomTitle.setText((matchmakingRoom ? "Ghép ngẫu nhiên" : message.string("roomName").isBlank() ? "Phòng " + roomCode : message.string("roomName"))
                + "   •   Bàn " + roomBoardSize + "×" + roomBoardSize
                + (matchmakingRoom ? "   •   " + matchmakingTargetPlayers + " người" : "   •   " + players.size() + "/" + roomCapacity + " người"));
        roomHost.setText(matchmakingRoom
                ? "Phòng do Server điều phối — không có chủ phòng người chơi"
                : "Mã phòng: " + roomCode + "  •  Chủ phòng: " + roomHostName
                + (username.equalsIgnoreCase(roomHostName) ? " — Bạn quản lý phòng này" : ""));
        RoomPlayerItem selectedMember = roomList.getSelectedValue();
        String selectedMemberName = selectedMember == null ? "" : selectedMember.username;
        roomModel.clear();
        boolean allReady = players.size() >= 2;
        boolean allOnline = true;
        selfReady = false;
        selfReplay = replayVotes.stream().anyMatch(name -> name.equalsIgnoreCase(username));
        for (Map<String, Object> row : players) {
            String name = String.valueOf(row.get("username"));
            boolean ready = Boolean.TRUE.equals(row.get("ready"));
            boolean online = Boolean.TRUE.equals(row.get("online"));
            boolean replay = replayVotes.stream().anyMatch(value -> value.equalsIgnoreCase(name));
            roomModel.addElement(new RoomPlayerItem(name, ready, online, name.equals(roomHostName), replay,
                    postGame, Objects.toString(row.get("title"), "")));
            if (name.equalsIgnoreCase(username)) selfReady = ready;
            allReady &= ready;
            allOnline &= online;
        }
        roomAllReady = allReady;
        roomAllOnline = allOnline;
        roomPlayerCount = players.size();
        for (int i = 0; i < roomModel.size(); i++) {
            if (roomModel.get(i).username.equals(selectedMemberName)) roomList.setSelectedIndex(i);
        }
        rebuildRoomMembers();
        refreshRoomActions();
        if (!message.bool("playing")) {
            AUDIO.stopLobbyMusic();
            pages.show(root, "room");
        }
    }

    private void refreshRoomActions() {
        if (readyButton == null) return;
        for (int i = 0; i < roomMemberGrid.getComponentCount(); i++) {
            ((JButton) roomMemberGrid.getComponent(i)).setSelected(i == roomList.getSelectedIndex() && i < roomModel.size());
        }
        boolean isHost = username.equalsIgnoreCase(roomHostName);
        boolean canInvite = !roomCode.isBlank() && !matchmakingRoom && !postGame
                && roomPlayerCount < roomCapacity && roomStartCountdownDeadline <= System.currentTimeMillis();
        String inviteReason = matchmakingRoom ? "Phòng ghép nhanh không nhận lời mời."
                : postGame ? "Chờ kết thúc lượt chơi lại."
                : roomPlayerCount >= roomCapacity ? "Phòng đã đủ người."
                : roomStartCountdownDeadline > System.currentTimeMillis() ? "Phòng đang chuẩn bị bắt đầu."
                : "Chưa ở phòng chờ.";
        Set<String> members = new HashSet<>();
        for (int i = 0; i < roomModel.size(); i++) members.add(roomModel.get(i).username);
        roomInvites.setRoomContext(canInvite, inviteReason, members);
        RoomPlayerItem selected = roomList.getSelectedValue();
        boolean canKick = isHost && !postGame && selected != null
                && !selected.username.equalsIgnoreCase(username);

        startButton.setVisible(!matchmakingRoom);
        kickButton.setVisible(!matchmakingRoom);
        copyRoomCodeButton.setVisible(!matchmakingRoom);

        readyButton.setText(postGame ? (selfReplay ? "Đã chọn chơi lại" : "Chơi lại")
                : selfReady ? "Hủy sẵn sàng" : "Sẵn sàng");
        readyButton.setEnabled(!postGame || !selfReplay);
        startButton.setEnabled(!postGame && isHost && roomPlayerCount >= 2
                && roomAllReady && roomAllOnline && roomPlayerCount <= roomCapacity
                && roomStartCountdownDeadline <= System.currentTimeMillis());
        kickButton.setEnabled(canKick && roomStartCountdownDeadline <= System.currentTimeMillis());

        if (postGame) {
            setBanner(roomStatusLabel, "Trận đã kết thúc — chọn Chơi lại trong thời gian còn lại.",
                    AppTheme.SELECTED, AppTheme.SECONDARY);
        } else if (roomStartCountdownDeadline > System.currentTimeMillis()) {
            long remaining = roomStartCountdownDeadline - System.currentTimeMillis();
            setBanner(roomStatusLabel, (matchmakingRoom
                    ? "Tất cả đã sẵn sàng — Server bắt đầu sau "
                    : "Chủ phòng đã bắt đầu — vào trận sau ")
                    + Math.max(1, (remaining + 999) / 1000) + " giây",
                    SUCCESS_BG, AppTheme.PRIMARY);
        } else if (matchmakingRoom && roomPlayerCount < matchmakingTargetPlayers) {
            setBanner(roomStatusLabel, "Server đang chờ đủ " + matchmakingTargetPlayers + " người chơi.",
                    WARNING_BG, AppTheme.GOLD_TEXT);
        } else if (roomPlayerCount < 2) {
            setBanner(roomStatusLabel, "Cần thêm ít nhất 1 người chơi để bắt đầu.",
                    WARNING_BG, AppTheme.GOLD_TEXT);
        } else if (!roomAllOnline) {
            setBanner(roomStatusLabel, "Đang chờ người chơi mất kết nối quay lại.",
                    DANGER_BG, AppTheme.DANGER);
        } else if (!roomAllReady) {
            setBanner(roomStatusLabel, "Đang chờ tất cả thành viên xác nhận Sẵn sàng.",
                    INFO_BG, AppTheme.PRIMARY);
        } else if (matchmakingRoom) {
            setBanner(roomStatusLabel, "Tất cả đã sẵn sàng — Server đang chuẩn bị bắt đầu.",
                    SUCCESS_BG, AppTheme.PRIMARY);
        } else if (isHost) {
            setBanner(roomStatusLabel, "Tất cả đã sẵn sàng — bạn có thể bắt đầu trận.",
                    SUCCESS_BG, AppTheme.PRIMARY);
        } else {
            setBanner(roomStatusLabel, "Tất cả đã sẵn sàng — đang chờ chủ phòng bắt đầu.",
                    SUCCESS_BG, AppTheme.PRIMARY);
        }
    }

    private boolean canInvitePlayer(PlayerItem player) {
        return player != null && !player.username.equalsIgnoreCase(username)
                && "IDLE".equals(selfStatus) && "IDLE".equals(player.status);
    }

    private void invitePlayer(PlayerItem player) {
        if (canInvitePlayer(player)) showCreateRoomDialog(player.username);
    }

    private void showPlayerMenu(PlayerItem player, int x, int y) {
        if (player.username.equalsIgnoreCase(username)) return;
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AppTheme.BORDER), new EmptyBorder(5, 5, 5, 5)));
        menu.setBackground(AppTheme.SURFACE);
        JMenuItem friend = new JMenuItem(friends.contains(player.username) ? "Đã là bạn bè" : "Kết bạn");
        friend.setEnabled(!friends.contains(player.username));
        friend.addActionListener(e -> send(Message.of(Protocol.Type.FRIEND_SEND).with("username", player.username)));
        JMenuItem whisper = new JMenuItem("Thì thầm");
        whisper.addActionListener(e -> openWhisper(player.username));
        for (JMenuItem item : new JMenuItem[]{friend, whisper}) {
            item.setFont(new Font("Segoe UI", Font.BOLD, 15)); item.setForeground(ACCENT);
            item.setBackground(AppTheme.SURFACE); item.setBorder(new EmptyBorder(10, 16, 10, 36)); menu.add(item);
        }
        menu.show(lobbyList, x, y);
    }

    private void resetWhispers() {
        whisperWindows.values().forEach(Window::dispose); whisperWindows.clear(); whispers.clear();
        if (conversationsWindow != null) conversationsWindow.dispose();
        conversationsWindow = null; conversationsPanel = null;
        whisperHistory = new WhisperHistory(); whisperScope = "";
        lobbyAvatars.clear();
        unreadMessages.clear(); refreshConversations();
    }

    private void refreshConversations() {
        if (conversationsButton != null) conversationsButton.setUnread(unreadMessages.total());
        if (roomInvites != null) roomInvites.setUnreadMessages(unreadMessages.total());
        if (conversationsPanel != null) conversationsPanel.update(whisperHistory.conversations());
    }

    private void showConversations() {
        if (conversationsWindow == null) {
            conversationsPanel = new ConversationsPanel(peer -> { openWhisper(peer); conversationsWindow.setVisible(false); }, unreadMessages::count);
            conversationsWindow = new JDialog(this, "Lịch sử trò chuyện", false);
            conversationsWindow.setContentPane(conversationsPanel);
            conversationsWindow.setSize(500, 440); conversationsWindow.setMinimumSize(new Dimension(380, 280));
            conversationsWindow.setLocationRelativeTo(this);
            conversationsWindow.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        }
        refreshConversations(); conversationsWindow.setVisible(true); conversationsWindow.toFront();
    }

    private Icon chatAvatar(String name, int size) {
        Icon avatar = lobbyAvatars.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).findFirst().orElseGet(() -> ProfilePanel.icon("", 50));
        return new Icon() {
            public int getIconWidth() { return size; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D scaled = (Graphics2D) g.create();
                scaled.translate(x, y);
                scaled.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                scaled.scale((double) size / avatar.getIconWidth(), (double) size / avatar.getIconHeight());
                avatar.paintIcon(c, scaled, 0, 0);
                scaled.dispose();
            }
        };
    }

    private ChatPanel openWhisper(String peer) {
        String key = peer.toLowerCase(Locale.ROOT);
        ChatPanel panel = whispers.get(key);
        if (panel == null) {
            panel = new ChatPanel(text -> send(Message.of(Protocol.Type.WHISPER_SEND).with("username", peer).with("text", text)),
                    "Thì thầm với " + peer);
            panel.useMessageBubbles(username, name -> chatAvatar(name, 36));
            panel.setMessages(whisperHistory.messages(peer));
            panel.setBorder(new EmptyBorder(14, 14, 14, 14));
            JDialog dialog = new JDialog(this, "Thì thầm · " + peer, false);
            dialog.setContentPane(panel); dialog.setSize(460, 360); dialog.setMinimumSize(new Dimension(340, 250));
            dialog.setLocationRelativeTo(this); dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            dialog.addWindowFocusListener(new WindowAdapter() {
                @Override public void windowGainedFocus(WindowEvent e) { unreadMessages.read(peer); refreshConversations(); }
            });
            whispers.put(key, panel); whisperWindows.put(key, dialog);
        }
        JDialog dialog = whisperWindows.get(key);
        if (!dialog.isVisible()) dialog.setVisible(true);
        dialog.toFront();
        unreadMessages.read(peer); refreshConversations();
        return panel;
    }

    private void receiveInvite(Message message) {
        JDialog dialog = new JDialog(this, "Lời mời vào phòng", false);
        InvitationPanel invitation = new InvitationPanel(message, accepted -> {
            if (client.isConnected()) send(Message.of(Protocol.Type.INVITE_REPLY)
                    .with("inviteId", message.string("inviteId")).with("accepted", accepted));
        }, dialog::dispose);
        dialog.setContentPane(invitation);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { invitation.respond(false); }
        });
        dialog.getRootPane().registerKeyboardAction(e -> invitation.respond(false),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setVisible(true);
    }

    private void setupBoard(int size) {
        boardSize = size;
        totalPairs = size * size / 2;
        cards = new JButton[size * size];
        cardStates = new int[size * size];
        cardValues = new int[size * size];
        cardOwners = new String[size * size];
        boardPanel.removeAll();
        int gap = size == 4 ? 18 : size == 6 ? 14 : 10;
        boardPanel.setLayout(new GridLayout(size, size, gap, gap));
        int fontSize = size == 4 ? 30 : size == 6 ? 25 : 20;
        for (int i = 0; i < cards.length; i++) {
            final int index = i;
            JButton card = new CardButton("?");
            card.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
            card.setToolTipText("Hàng " + (index / size + 1) + ", cột " + (index % size + 1));
            card.addActionListener(e -> {
                if (cardStates[index] == 0 && username.equals(currentPlayer)) {
                    AUDIO.playFlip();
                    card.setEnabled(false);
                    send(Message.of(Protocol.Type.CARD_PICK).with("index", index));
                }
            });
            cards[i] = card;
            boardPanel.add(card);
        }
        boardPanel.revalidate();
        boardPanel.repaint();
    }

    private void startGame(Message message) {
        applyRoomTheme(message);
        roomCode = message.string("code");
        chatPanel.setMessages(message.value("chat"));
        chatPanel.setSendingEnabled(true);
        AUDIO.stopLobbyMusic();
        postGame = false;
        replayDeadline = 0;
        roomStartCountdownDeadline = 0;
        int requestedSize = message.integer("boardSize");
        if (requestedSize != boardSize || cards.length != requestedSize * requestedSize) setupBoard(requestedSize);
        totalPairs = message.integer("totalPairs");
        List<String> gamePlayers = message.value("players");
        assignPlayerColors(gamePlayers == null ? List.of() : gamePlayers);
        Arrays.fill(cardStates, 0);
        Arrays.fill(cardValues, 0);
        Arrays.fill(cardOwners, null);
        int[] states = message.value("states");
        int[] values = message.value("values");
        String[] owners = message.value("owners");
        if (states != null) System.arraycopy(states, 0, cardStates, 0, Math.min(cards.length, states.length));
        if (owners != null) System.arraycopy(owners, 0, cardOwners, 0, Math.min(cards.length, owners.length));
        for (int i = 0; i < cards.length; i++) {
            JButton card = cards[i];
            if (values != null && i < values.length && cardStates[i] != 0) cardValues[i] = values[i];
            if (cardStates[i] == 2) {
                card.setText(values != null && values[i] >= 0 ? cardTheme.face(values[i]) : "✓");
                applyMatchedOwnerColor(i, cardOwners[i]);
            } else if (cardStates[i] == 1 && values != null && values[i] >= 0) {
                card.setText(cardTheme.face(values[i]));
                Color faceColor = colorForValue(values[i]);
                card.setBackground(faceColor);
                card.setForeground(textColorFor(faceColor));
                setCardRing(i, null);
            } else {
                card.setText("?");
                card.setBackground(cardBackColor);
                card.setForeground(Color.WHITE);
                setCardRing(i, null);
            }
        }
        currentPlayer = message.string("current");
        gamePairs.setText("Đã ghép: " + message.integer("pairs") + "/" + totalPairs);
        updateScores(message.value("scores"));
        updateCardEnabled();
        pages.show(root, "game");
    }

    private void revealCard(int index, int value) {
        if (index < 0 || index >= cards.length) return;
        cardStates[index] = 1;
        cardValues[index] = value;
        Color faceColor = colorForValue(value);
        cards[index].setText(cardTheme.face(value));
        cards[index].setBackground(faceColor);
        cards[index].setForeground(textColorFor(faceColor));
        cards[index].setEnabled(false);
    }

    private void hideCards(int[] indexes) {
        if (indexes == null) return;
        for (int index : indexes) {
            if (index < 0 || index >= cards.length) continue;
            cardStates[index] = 0;
            cardValues[index] = 0;
            cardOwners[index] = null;
            cards[index].setText("?");
            cards[index].setBackground(cardBackColor);
            cards[index].setForeground(Color.WHITE);
            setCardRing(index, null);
        }
        updateCardEnabled();
    }

    private void matchResult(Message message) {
        int first = message.integer("first");
        int second = message.integer("second");
        if (message.bool("match")) {
            AUDIO.playCorrect();
            String owner = message.string("player");
            cardStates[first] = cardStates[second] = 2;
            cardOwners[first] = cardOwners[second] = owner;
            applyMatchedOwnerColor(first, owner);
            applyMatchedOwnerColor(second, owner);
        } else {
            AUDIO.playWrong();
            animateMismatch(first, second);
        }
        gamePairs.setText("Đã ghép: " + message.integer("pairs") + "/" + totalPairs);
        updateScores(message.value("scores"));
        updateCardEnabled();
    }

    private void animateMismatch(int first, int second) {
        int[] indexes = {first, second};
        long startedAt = System.nanoTime();
        boolean[] faceChanged = {false};
        javax.swing.Timer animation = new javax.swing.Timer(32, null);
        animation.addActionListener(e -> {
            long elapsed = (System.nanoTime() - startedAt) / 1_000_000L;
            double lift;
            double flip;
            if (elapsed < 350) {
                // Nâng chậm để người chơi vẫn kịp quan sát hai mặt thẻ sai.
                lift = elapsed / 350.0;
                flip = 1.0;
            } else if (elapsed < 700) {
                // Giữ thẻ ở vị trí cao thêm một nhịp trước khi quay úp.
                lift = 1.0;
                flip = 1.0;
            } else if (elapsed < 880) {
                lift = 1.0;
                flip = 1.0 - (elapsed - 700) / 180.0;
            } else if (elapsed < 1060) {
                if (!faceChanged[0]) {
                    faceChanged[0] = true;
                    for (int index : indexes) {
                        if (index >= 0 && index < cards.length) {
                            cards[index].setText("?");
                            cards[index].setBackground(cardBackColor);
                            cards[index].setForeground(Color.WHITE);
                        }
                    }
                }
                lift = 1.0;
                flip = (elapsed - 880) / 180.0;
            } else {
                lift = Math.max(0.0, 1.0 - (elapsed - 1060) / 200.0);
                flip = 1.0;
            }

            for (int index : indexes) {
                if (index >= 0 && index < cards.length && cards[index] instanceof CardButton card) {
                    card.setMotion(lift, Math.max(0.04, flip));
                }
            }
            if (elapsed >= 1260) {
                animation.stop();
                for (int index : indexes) {
                    if (index >= 0 && index < cards.length && cards[index] instanceof CardButton card) {
                        card.setMotion(0, 1);
                    }
                }
            }
        });
        animation.setInitialDelay(0);
        animation.start();
    }

    private void updateTurn(Message message) {
        currentPlayer = message.string("current");
        deadline = message.longValue("deadline");
        gameTurn.setText(username.equals(currentPlayer) ? "Lượt của bạn" : "Lượt: " + currentPlayer);
        gameTurn.setForeground(playerColors.getOrDefault(currentPlayer, TEXT));
        updateCardEnabled();
    }

    private void updateCountdown() {
        if (postGame && replayDeadline > 0) {
            long replayRemaining = Math.max(0, replayDeadline - System.currentTimeMillis());
            roomStatusLabel.setText("Chọn Chơi lại hoặc Rời phòng — còn "
                    + Math.max(0, (replayRemaining + 999) / 1000) + " giây");
        } else if (roomStartCountdownDeadline > 0) {
            long remaining = Math.max(0, roomStartCountdownDeadline - System.currentTimeMillis());
            if (remaining > 0) {
                roomStatusLabel.setText((matchmakingRoom
                        ? "Tất cả đã sẵn sàng — Server bắt đầu sau "
                        : "Chủ phòng đã bắt đầu — vào trận sau ")
                        + Math.max(1, (remaining + 999) / 1000) + " giây");
            }
        }
        if (deadline <= 0) return;
        long remaining = Math.max(0, deadline - System.currentTimeMillis());
        gameTimer.setText(String.format(Locale.ROOT, "Còn %.1f giây", remaining / 1000.0));
        gameTimer.setForeground(remaining <= 5000 ? AppTheme.DANGER : CORAL_LIGHT);
        turnProgress.setValue((int) Math.min(150, remaining / 100));
        turnProgress.setForeground(remaining <= 5000 ? AppTheme.DANGER : VIOLET);
    }

    private void updateCardEnabled() {
        boolean myTurn = username.equals(currentPlayer);
        for (int i = 0; i < cards.length; i++) cards[i].setEnabled(myTurn && cardStates[i] == 0);
    }

    private void updateScores(Map<String, Integer> scores) {
        if (scores == null) return;
        StringBuilder html = new StringBuilder("<html><div style='line-height:2.0; font-size:16px'>");
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            Color color = playerColors.getOrDefault(entry.getKey(), TEXT);
            html.append("<font color='").append(toHex(color)).append("' size='+2'>●</font>&nbsp;&nbsp;")
                    .append("<b>P").append(new ArrayList<>(playerColors.keySet()).indexOf(entry.getKey()) + 1)
                    .append(" · ").append(entry.getKey()).append("</b>:&nbsp;&nbsp;<b>")
                    .append(entry.getValue()).append("</b><br>");
        }
        gameScores.setText(html.append("</div></html>").toString());
    }

    private void assignPlayerColors(List<String> players) {
        playerColors.clear();
        playerColors.putAll(GameColors.forPlayers(players));
    }

    private void setCardRing(int index, Color color) {
        if (index >= 0 && index < cards.length && cards[index] instanceof CardButton card) {
            card.setRingColor(color);
            if (color == null) {
                card.ownerLabel = null;
                card.setToolTipText("Hàng " + (index / boardSize + 1) + ", cột " + (index % boardSize + 1));
            }
        }
    }

    private void applyMatchedOwnerColor(int index, String owner) {
        if (index < 0 || index >= cards.length) return;
        Color ownerColor = playerColors.getOrDefault(owner, GameColors.UNASSIGNED);
        cards[index].setBackground(ownerColor);
        cards[index].setForeground(textColorFor(ownerColor));
        setCardRing(index, ownerColor);
        if (cards[index] instanceof CardButton card) {
            int playerNumber = new ArrayList<>(playerColors.keySet()).indexOf(owner) + 1;
            card.ownerLabel = playerNumber > 0 ? "P" + playerNumber + " · " + owner : "✓";
            card.setToolTipText("Hàng " + (index / boardSize + 1) + ", cột " + (index % boardSize + 1)
                    + " · Đã ghép bởi: " + (owner == null ? "Không xác định" : owner));
            card.repaint();
        }
    }

    private static Color textColorFor(Color background) {
        return GameColors.textOn(background);
    }

    private static String toHex(Color color) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private void gameEnd(Message message) {
        chatPanel.setSendingEnabled(false);
        deadline = 0;
        turnProgress.setValue(0);
        replayDeadline = message.longValue("replayDeadline");
        List<Map<String, Object>> results = message.value("results");
        List<Map<String, Object>> orderedResults = orderGameResults(results);
        showScoringProgress(orderedResults.size());
        JComponent resultPanel = buildGameResultPanel(message.string("reason"), orderedResults);
        int choice = showActionDialog("Kết quả trận đấu", "", resultPanel,
                new String[]{"Chơi lại", "Về Lobby"},
                new Color[]{SUCCESS, AppTheme.MUTED}, 720, 520);
        if (choice == 0) send(Message.of(Protocol.Type.PLAY_AGAIN));
        else send(Message.of(Protocol.Type.BACK_LOBBY));
    }

    private static List<Map<String, Object>> orderGameResults(List<Map<String, Object>> results) {
        List<Map<String, Object>> ordered = new ArrayList<>();
        if (results == null) return ordered;
        for (Map<String, Object> row : results) ordered.add(new LinkedHashMap<>(row));
        ordered.sort(Comparator
                .comparingInt((Map<String, Object> row) -> ((Number) row.getOrDefault("score", 0)).intValue())
                .reversed()
                .thenComparing(row -> Objects.toString(row.get("username"), ""),
                        String.CASE_INSENSITIVE_ORDER));

        int placement = 0;
        int previousScore = Integer.MIN_VALUE;
        for (Map<String, Object> row : ordered) {
            int score = ((Number) row.getOrDefault("score", 0)).intValue();
            if (score != previousScore) {
                placement++;
                previousScore = score;
            }
            row.put("placement", placement);
        }
        return ordered;
    }

    private void showScoringProgress(int playerCount) {
        JDialog dialog = new JDialog(this, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setResizable(false);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel shell = panel(new BorderLayout(0, 18));
        shell.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.PRIMARY),
                new EmptyBorder(26, 30, 26, 30)));

        JPanel heading = panel(new GridLayout(0, 1, 0, 6));
        JLabel eyebrow = label("KẾT THÚC TRẬN ĐẤU", 12, Font.BOLD);
        eyebrow.setForeground(AppTheme.PRIMARY);
        JLabel title = label("Đang tính điểm các cặp đấu...", 22, Font.BOLD);
        JLabel detail = label("Đang xếp hạng " + playerCount
                + " người chơi và cập nhật phần thưởng.", 14, Font.PLAIN);
        detail.setForeground(MUTED);
        heading.add(eyebrow);
        heading.add(title);
        heading.add(detail);

        JProgressBar progress = new JProgressBar(0, 100);
        progress.setValue(0);
        progress.setStringPainted(true);
        progress.setString("Tổng hợp kết quả");
        progress.setForeground(VIOLET);
        progress.setBackground(FIELD);
        progress.setBorder(BorderFactory.createLineBorder(BORDER));
        progress.setPreferredSize(new Dimension(460, 30));

        shell.add(heading, BorderLayout.CENTER);
        shell.add(progress, BorderLayout.SOUTH);
        dialog.setContentPane(shell);
        dialog.setSize(540, 230);
        dialog.setLocationRelativeTo(this);

        int[] value = {0};
        javax.swing.Timer calculation = new javax.swing.Timer(100, null);
        calculation.addActionListener(e -> {
            value[0] = Math.min(100, value[0] + 6);
            progress.setValue(value[0]);
            if (value[0] >= 72) progress.setString("Cập nhật xếp hạng và xu");
            else if (value[0] >= 36) progress.setString("Xác định thứ hạng");
            if (value[0] >= 100) {
                calculation.stop();
                dialog.dispose();
            }
        });
        calculation.start();
        dialog.setVisible(true);
    }

    private JComponent buildGameResultPanel(String reason, List<Map<String, Object>> results) {
        JPanel content = panel(new BorderLayout(0, 10));

        JPanel heading = panel(new BorderLayout(0, 10));
        JLabel summary = label(reason, 14, Font.BOLD);
        setBanner(summary, reason, INFO_BG, AppTheme.PRIMARY);
        heading.add(summary, BorderLayout.NORTH);

        JPanel columns = panel(new BorderLayout(12, 0));
        columns.setBorder(new EmptyBorder(0, 12, 0, 14));
        JLabel rankColumn = label("THỨ HẠNG", 11, Font.BOLD);
        rankColumn.setForeground(MUTED);
        rankColumn.setPreferredSize(new Dimension(82, 18));
        JLabel playerColumn = label("NGƯỜI CHƠI", 11, Font.BOLD);
        playerColumn.setForeground(MUTED);
        JLabel rewardColumn = label("THÀNH TÍCH", 11, Font.BOLD);
        rewardColumn.setForeground(MUTED);
        rewardColumn.setHorizontalAlignment(SwingConstants.RIGHT);
        rewardColumn.setPreferredSize(new Dimension(142, 18));
        columns.add(rankColumn, BorderLayout.WEST);
        columns.add(playerColumn, BorderLayout.CENTER);
        columns.add(rewardColumn, BorderLayout.EAST);
        heading.add(columns, BorderLayout.SOUTH);
        content.add(heading, BorderLayout.NORTH);

        JPanel rows = panel(new BorderLayout());
        JPanel resultList = panel(null);
        resultList.setLayout(new BoxLayout(resultList, BoxLayout.Y_AXIS));
        resultList.setBorder(new EmptyBorder(0, 0, 4, 0));
        for (Map<String, Object> row : results) {
            int placement = ((Number) row.getOrDefault("placement", 1)).intValue();
            int score = ((Number) row.getOrDefault("score", 0)).intValue();
            int delta = ((Number) row.getOrDefault("ratingDelta", 0)).intValue();
            int reward = ((Number) row.getOrDefault("coinReward", 0)).intValue();
            boolean winner = Boolean.TRUE.equals(row.get("winner"));
            boolean forfeited = Boolean.TRUE.equals(row.get("forfeited"));

            Color placementColor = switch (placement) {
                case 1 -> AppTheme.GOLD_TEXT;
                case 2 -> AppTheme.PRIMARY;
                case 3 -> CORAL_LIGHT;
                default -> MUTED;
            };
            Color rankBackground = switch (placement) {
                case 1 -> AppTheme.WARNING_BG;
                case 2 -> AppTheme.SELECTED;
                case 3 -> AppTheme.DANGER_BG;
                default -> AppTheme.SURFACE;
            };
            JPanel resultRow = new JPanel(new BorderLayout(14, 0));
            resultRow.setBackground(FIELD);
            resultRow.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    new EmptyBorder(10, 12, 10, 14)));
            resultRow.setPreferredSize(new Dimension(620, 78));
            resultRow.setMinimumSize(new Dimension(300, 78));
            resultRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
            resultRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel rankBadge = new JPanel(new GridBagLayout());
            rankBadge.setBackground(rankBackground);
            rankBadge.setBorder(BorderFactory.createLineBorder(placementColor));
            rankBadge.setPreferredSize(new Dimension(70, 32));
            rankBadge.setMinimumSize(new Dimension(70, 32));
            rankBadge.setMaximumSize(new Dimension(70, 32));
            JLabel rank = label("HẠNG " + placement, 11, Font.BOLD);
            rank.setForeground(placementColor);
            rankBadge.add(rank);
            JPanel rankHolder = new JPanel(new GridBagLayout());
            rankHolder.setOpaque(false);
            rankHolder.setPreferredSize(new Dimension(82, 54));
            rankHolder.add(rankBadge);

            JPanel player = new JPanel();
            player.setOpaque(false);
            player.setLayout(new BoxLayout(player, BoxLayout.Y_AXIS));
            JLabel playerName = label(Objects.toString(row.get("username"), ""), 16, Font.BOLD);
            String status = forfeited ? "Bỏ cuộc" : winner ? "Chiến thắng" : "Hoàn thành";
            JLabel scoreLine = label(score + " cặp  •  " + status, 13, Font.PLAIN);
            scoreLine.setForeground(MUTED);
            player.add(playerName);
            player.add(Box.createVerticalStrut(3));
            player.add(scoreLine);

            JPanel rewards = new JPanel();
            rewards.setOpaque(false);
            rewards.setLayout(new BoxLayout(rewards, BoxLayout.Y_AXIS));
            JLabel rating = label("Xếp hạng " + (delta >= 0 ? "+" : "") + delta, 13, Font.BOLD);
            rating.setForeground(delta >= 0 ? AppTheme.PRIMARY : AppTheme.DANGER);
            rating.setAlignmentX(Component.RIGHT_ALIGNMENT);
            JLabel coinsEarned = label("+" + reward + " xu", 14, Font.BOLD);
            coinsEarned.setForeground(AppTheme.GOLD_TEXT);
            coinsEarned.setAlignmentX(Component.RIGHT_ALIGNMENT);
            rewards.add(rating);
            rewards.add(Box.createVerticalStrut(3));
            rewards.add(coinsEarned);
            rewards.setPreferredSize(new Dimension(142, 48));

            resultRow.add(rankHolder, BorderLayout.WEST);
            resultRow.add(player, BorderLayout.CENTER);
            resultRow.add(rewards, BorderLayout.EAST);
            resultList.add(resultRow);
            resultList.add(Box.createVerticalStrut(8));
        }
        rows.add(resultList, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(rows);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        content.add(scroll, BorderLayout.CENTER);
        return content;
    }

    private void showRanking(Message message) {
        List<Map<String, Object>> rows = message.value("rows");
        JDialog dialog = new JDialog(this, "Bảng xếp hạng", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(new RankingPanel(username, rows == null ? List.of() : rows, dialog::dispose));
        dialog.setSize(1000, 720);
        dialog.setMinimumSize(new Dimension(850, 610));
        dialog.setLocationRelativeTo(this);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setVisible(true);
    }

    private void showHistory(Message message) {
        List<String> history = message.value("history");
        JDialog dialog = new JDialog(this, "Lịch sử trận đấu", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(new HistoryPanel(username, coins, history == null ? List.of() : history, dialog::dispose));
        dialog.setSize(1000, 690);
        dialog.setMinimumSize(new Dimension(860, 590));
        dialog.setLocationRelativeTo(this);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setVisible(true);
    }

    private void showTextDialog(String title, String text) {
        JTextArea area = dialogTextArea(text, false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(AppTheme.MUTED));
        scroll.getViewport().setBackground(PANEL);
        showActionDialog(title, "", scroll, new String[]{"Đóng"},
                new Color[]{AppTheme.PRIMARY}, 820, 560);
    }

    private void disconnected(String reason) {
        if (!isDisplayable() || closing) return;
        if (profileDialog != null) profileDialog.dispose();
        profilePanel = null; profileRequested = false;
        AUDIO.stopLobbyMusic();
        if (connectionBadge != null) {
            connectionBadge.setText("● Mất kết nối");
            connectionBadge.setForeground(AppTheme.DANGER);
        }
        pages.show(root, "login");
        if (!authenticated || savedUsername.isBlank() || savedPassword.isBlank()) {
            loginButton.setEnabled(true);
            registerButton.setEnabled(true);
            loginStatus.setText(reason);
            return;
        }
        loginButton.setEnabled(false);
        loginStatus.setText(reason + " Đang tự động kết nối lại...");
        if (reconnecting) return;
        reconnecting = true;
        reconnectAttempts = 0;
        reconnectTimer = new javax.swing.Timer(3000, e -> attemptReconnect());
        reconnectTimer.setInitialDelay(0);
        reconnectTimer.start();
    }

    private void attemptReconnect() {
        if (closing || !reconnecting) return;
        if (client.isConnected() || reconnectAttemptRunning) return;
        if (reconnectAttempts >= 10) {
            reconnectTimer.stop();
            reconnecting = false;
            loginButton.setEnabled(true);
            loginStatus.setText("Không thể kết nối lại. Hãy kiểm tra server rồi bấm Đăng nhập.");
            return;
        }
        reconnectAttempts++;
        loginStatus.setText("Đang kết nối lại lần " + reconnectAttempts + "/10...");
        openConnection(true);
    }

    private JComboBox<String> themeCombo() {
        JComboBox<String> combo = styledCombo(new String[0]);
        combo.setModel(cardThemeModel);
        combo.setToolTipText("Chọn bộ hình thẻ hiển thị trên máy của bạn");
        combo.getAccessibleContext().setAccessibleName("Chủ đề thẻ");
        combo.addActionListener(e -> {
            int index = combo.getSelectedIndex();
            if (index < 0) return;
            CardTheme selected = CardTheme.values()[index];
            if (cardTheme == selected) return;
            cardTheme = selected;
            for (int i = 0; i < cards.length; i++) {
                if (cardStates[i] != 0 && cardValues[i] > 0 && !"?".equals(cards[i].getText())) {
                    cards[i].setText(cardTheme.face(cardValues[i]));
                }
            }
            if (boardPanel != null) boardPanel.repaint();
        });
        return combo;
    }

    private int selectedBoardSize() {
        return switch (boardSizeCombo.getSelectedIndex()) {
            case 0 -> 4;
            case 1 -> 6;
            default -> 8;
        };
    }

    private void joinQueue(int players) {
        int size = selectedBoardSize();
        findMatchButton.setEnabled(false);
        send(Message.of(Protocol.Type.QUEUE_JOIN).with("size", players).with("boardSize", size)
                .with("cardTheme", cardTheme.name()));
        lobbyNotice.setText("Đang tìm " + players + " người cùng chọn bàn " + size + "×" + size + " · " + cardTheme + "...");
    }

    private void send(Message message) {
        if (client.isConnected()) client.send(message);
        else showInfo("Chưa kết nối tới server.");
    }

    private void applyRoomTheme(Message message) {
        String theme = message.string("cardTheme");
        if (!theme.isEmpty()) {
            try { cardThemeModel.setSelectedItem(CardTheme.valueOf(theme).toString()); }
            catch (IllegalArgumentException ignored) { }
        }
        gameThemeCombo.setEnabled(theme.isEmpty());
        gameThemeCombo.setToolTipText(theme.isEmpty() ? "Chọn bộ hình thẻ trên máy của bạn" : "Chủ đề chung của trận đấu");
    }

    private void showCreateRoomDialog() { showCreateRoomDialog(""); }

    private void showCreateRoomDialog(String invitee) {
        JDialog dialog = new JDialog(this, invitee.isEmpty() ? "Tạo phòng" : "Thiết lập trận đấu", true);
        dialog.setUndecorated(true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(new CreateRoomPanel(username, cardTheme, selectedBoardSize(), invitee, dialog::dispose, request -> {
            send(request);
            dialog.dispose();
        }));
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setVisible(true);
    }

    private String showRoomCodeDialog() {
        DialogView view = createDialog("Tham gia phòng", 500, 325, true);
        String[] result = {null};

        JTextArea instruction = dialogTextArea(
                "Nhập mã phòng gồm 6 ký tự mà chủ phòng đã chia sẻ.", true);
        instruction.setOpaque(false);
        instruction.setForeground(LoginPanel.MUTED);
        view.body.add(instruction, BorderLayout.NORTH);

        JPanel inputArea = lobbyPanel(new GridBagLayout());
        inputArea.setBorder(new EmptyBorder(12, 0, 8, 0));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        JLabel inputLabel = label("MÃ PHÒNG", 12, Font.BOLD);
        inputLabel.setForeground(LoginPanel.CYAN);
        inputArea.add(inputLabel, c);
        JTextField codeField = field("");
        LoginPanel.styleRegistrationInput(codeField);
        inputLabel.setLabelFor(codeField);
        codeField.setFont(new Font("Segoe UI", Font.BOLD, 20));
        codeField.setHorizontalAlignment(SwingConstants.CENTER);
        codeField.setPreferredSize(new Dimension(10, 44));
        c.gridy++; c.insets = new Insets(7, 0, 7, 0);
        inputArea.add(codeField, c);
        JLabel validation = label("Ví dụ: A2B3C4", 12, Font.PLAIN);
        validation.setForeground(LoginPanel.MUTED);
        c.gridy++; c.insets = new Insets(0, 0, 0, 0);
        inputArea.add(validation, c);
        view.body.add(inputArea, BorderLayout.CENTER);

        JButton join = lobbyButton("Vào phòng", true);
        JButton cancel = lobbyButton("Hủy", false);
        join.setPreferredSize(new Dimension(130, 42));
        cancel.setPreferredSize(new Dimension(100, 42));
        Runnable submit = () -> {
            String code = codeField.getText().trim().toUpperCase(Locale.ROOT);
            if (!code.matches("[A-Z0-9]{6}")) {
                validation.setText("Mã phòng phải có đúng 6 ký tự chữ hoặc số.");
                validation.setForeground(AppTheme.DANGER);
                codeField.requestFocusInWindow();
                return;
            }
            result[0] = code;
            view.dialog.dispose();
        };
        join.addActionListener(e -> submit.run());
        cancel.addActionListener(e -> view.dialog.dispose());
        codeField.addActionListener(e -> submit.run());
        view.actions.add(join);
        view.actions.add(cancel);
        view.dialog.getRootPane().setDefaultButton(join);
        SwingUtilities.invokeLater(codeField::requestFocusInWindow);
        view.dialog.setVisible(true);
        return result[0];
    }

    private int showActionDialog(String title, String message, JComponent content,
                                 String[] actions, Color[] colors, int width, int height) {
        DialogView view = createDialog(title, width, height);
        int[] selected = {-1};
        if (message != null && !message.isBlank()) {
            view.body.add(dialogTextArea(message, true), content == null
                    ? BorderLayout.CENTER : BorderLayout.NORTH);
        }
        if (content != null) view.body.add(content, BorderLayout.CENTER);

        for (int i = 0; i < actions.length; i++) {
            int index = i;
            Color color = i < colors.length ? colors[i] : AppTheme.MUTED;
            JButton action = button(actions[i], color);
            action.setPreferredSize(new Dimension(Math.max(120, actions[i].length() * 9 + 34), 42));
            action.addActionListener(e -> {
                selected[0] = index;
                view.dialog.dispose();
            });
            view.actions.add(action);
            if (i == 0) view.dialog.getRootPane().setDefaultButton(action);
        }
        view.dialog.setVisible(true);
        return selected[0];
    }

    private DialogView createDialog(String title, int width, int height) {
        return createDialog(title, width, height, false);
    }

    private DialogView createDialog(String title, int width, int height, boolean neon) {
        JDialog dialog = new JDialog(this, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setResizable(false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel shell = neon ? LoginPanel.authSurface(new BorderLayout()) : panel(new BorderLayout());
        shell.setBorder(neon ? new EmptyBorder(4, 4, 4, 4) : BorderFactory.createLineBorder(BORDER, 1));

        JPanel titleBar = new JPanel(new BorderLayout(10, 0));
        titleBar.setBackground(PANEL);
        titleBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                new EmptyBorder(10, 16, 10, 10)));
        JLabel heading = label(title, 17, Font.BOLD);
        if (neon) {
            titleBar.setOpaque(false);
            titleBar.setBorder(new EmptyBorder(12, 16, 10, 10));
            heading.setForeground(LoginPanel.CYAN);
        }
        titleBar.add(heading, BorderLayout.WEST);
        JButton close = neon ? new LoginPanel.NeonButton("×", false) : new HeaderButton("×");
        if (neon) close.setBorder(new EmptyBorder(0, 0, 0, 0));
        close.setPreferredSize(new Dimension(34, 30));
        close.addActionListener(e -> dialog.dispose());
        titleBar.add(close, BorderLayout.EAST);
        Point[] dragOrigin = {null};
        titleBar.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragOrigin[0] = e.getPoint();
            }
        });
        titleBar.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (dragOrigin[0] == null) return;
                Point location = dialog.getLocation();
                dialog.setLocation(location.x + e.getX() - dragOrigin[0].x,
                        location.y + e.getY() - dragOrigin[0].y);
            }
        });

        JPanel body = panel(new BorderLayout(12, 12));
        body.setBorder(new EmptyBorder(18, 20, 10, 20));
        JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setBorder(new EmptyBorder(8, 20, 18, 20));

        shell.add(titleBar, BorderLayout.NORTH);
        shell.add(body, BorderLayout.CENTER);
        shell.add(actions, BorderLayout.SOUTH);
        if (neon) {
            body.setOpaque(false);
            actions.setOpaque(false);
            JPanel backdrop = new JPanel(new BorderLayout());
            backdrop.setBackground(AppTheme.BACKGROUND);
            backdrop.add(shell);
            dialog.setContentPane(backdrop);
        } else dialog.setContentPane(shell);
        dialog.setSize(width, height);
        dialog.setLocationRelativeTo(this);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        return new DialogView(dialog, body, actions);
    }

    private static JTextArea dialogTextArea(String text, boolean wrap) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(wrap);
        area.setWrapStyleWord(wrap);
        area.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        area.setForeground(AppTheme.TEXT);
        area.setBackground(BACKGROUND);
        area.setOpaque(false);
        area.setFocusable(false);
        area.setHighlighter(null);
        area.setBorder(new EmptyBorder(2, 0, 6, 0));
        return area;
    }

    private void showInfo(String text) {
        boolean declined = text.endsWith("đã từ chối lời mời.");
        DialogView view = createDialog(declined ? "Lời mời bị từ chối" : "Thông báo", 490, 290, true);
        JPanel messageCard = LoginPanel.authSurface(new BorderLayout(16, 0));
        messageCard.setBorder(new EmptyBorder(18, 18, 18, 18));
        JLabel icon = new JLabel(declined ? "×" : "i", SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(declined ? AppTheme.WARNING_BG : AppTheme.SELECTED);
                int size = Math.min(getWidth(), getHeight());
                g.fillOval((getWidth() - size) / 2, (getHeight() - size) / 2, size, size);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        icon.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        icon.setForeground(declined ? AppTheme.GOLD_TEXT : AppTheme.PRIMARY);
        icon.setPreferredSize(new Dimension(52, 52));
        JPanel iconArea = lobbyPanel(new BorderLayout());
        iconArea.add(icon, BorderLayout.NORTH);
        messageCard.add(iconArea, BorderLayout.WEST);
        JTextArea messageText = dialogTextArea(text, true);
        messageText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        JScrollPane messageScroll = new JScrollPane(messageText);
        messageScroll.setBorder(BorderFactory.createEmptyBorder());
        messageScroll.setOpaque(false); messageScroll.getViewport().setOpaque(false);
        messageCard.add(messageScroll);
        view.body.add(messageCard);
        if (declined) {
            JLabel hint = label("Bạn có thể chọn người chơi khác để gửi lời mời.", 12, Font.PLAIN);
            hint.setForeground(AppTheme.MUTED);
            view.body.add(hint, BorderLayout.SOUTH);
        }
        JButton done = lobbyButton("Đã hiểu", true);
        done.setPreferredSize(new Dimension(145, 42));
        done.addActionListener(e -> view.dialog.dispose());
        view.actions.add(done);
        view.dialog.getRootPane().setDefaultButton(done);
        view.dialog.setVisible(true);
    }

    private static void setBanner(JLabel target, String text, Color background, Color accent) {
        target.setText(text);
        target.setOpaque(true);
        target.setBackground(background);
        target.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                new EmptyBorder(9, 12, 9, 12)));
    }

    private static Color colorForValue(int value) {
        return GameColors.forValue(value);
    }

    private static JPanel panel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(BACKGROUND);
        return panel;
    }

    private static JLabel label(String text, int size, int style) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(new Font("Segoe UI", style, size));
        return label;
    }

    private static JLabel stepLabel(String number, String text) {
        JLabel label = new JLabel(stepHtml(number, text));
        label.setForeground(AppTheme.TEXT);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        label.setBorder(new EmptyBorder(5, 0, 5, 0));
        return label;
    }

    private static JLabel gameRule(String number, String text) {
        JLabel label = new JLabel("<html><div style='width:210px'><font color='" + AppTheme.hex(AppTheme.PRIMARY) + "'><b>"
                + number + ".</b></font>&nbsp;&nbsp;" + text + "</div></html>");
        label.setForeground(AppTheme.TEXT);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setBorder(new EmptyBorder(7, 0, 7, 0));
        return label;
    }

    private static String stepHtml(String number, String text) {
        String marker = "•".equals(number) ? number : number + ".";
        return "<html><font color='" + AppTheme.hex(AppTheme.PRIMARY) + "'><b>" + marker
                + "</b></font>&nbsp;&nbsp;" + text + "</html>";
    }

    private static JTextField field(String value) {
        JTextField field = new JTextField(value);
        styleField(field);
        return field;
    }

    private static JComboBox<String> styledCombo(String[] values) {
        JComboBox<String> combo = new JComboBox<>(values);
        combo.setUI(new BasicComboBoxUI() {
            @Override public void paintCurrentValueBackground(Graphics graphics, Rectangle bounds,
                                                                boolean hasFocus) {
                graphics.setColor(FIELD);
                graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }

            @Override public void paintCurrentValue(Graphics graphics, Rectangle bounds, boolean hasFocus) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(FIELD);
                g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                g2.setFont(comboBox.getFont());
                g2.setColor(TEXT);
                String text = Objects.toString(comboBox.getSelectedItem(), "");
                FontMetrics metrics = g2.getFontMetrics();
                int baseline = bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent();
                g2.drawString(text, bounds.x + 12, baseline);
                g2.dispose();
            }

            @Override protected JButton createArrowButton() {
                JButton arrow = new JButton() {
                    @Override protected void paintComponent(Graphics graphics) {
                        Graphics2D g2 = (Graphics2D) graphics.create();
                        g2.setColor(ACCENT);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        int centerX = getWidth() / 2;
                        int centerY = getHeight() / 2 + 1;
                        g2.setColor(Color.WHITE);
                        g2.fillPolygon(
                                new int[]{centerX - 5, centerX + 5, centerX},
                                new int[]{centerY - 3, centerY - 3, centerY + 3}, 3);
                        g2.dispose();
                    }
                };
                arrow.setOpaque(false);
                arrow.setContentAreaFilled(false);
                arrow.setFocusPainted(false);
                arrow.setBorderPainted(false);
                arrow.setBorder(new EmptyBorder(0, 10, 0, 10));
                return arrow;
            }
        });
        // Cài UI trước để màu nền/chữ nhất quán giữa các hệ điều hành.
        combo.setFont(new Font("Segoe UI", Font.BOLD, 14));
        combo.setBackground(FIELD);
        combo.setForeground(TEXT);
        combo.setOpaque(true);
        combo.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        combo.setPreferredSize(new Dimension(185, 38));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                     boolean selected, boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                label.setOpaque(true);
                label.setEnabled(true);
                label.setBorder(new EmptyBorder(7, 10, 7, 10));
                label.setBackground(selected ? ACCENT : FIELD);
                label.setForeground(selected ? AppTheme.ON_ACCENT : TEXT);
                return label;
            }
        });
        return combo;
    }

    private static void styleField(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        field.setBackground(FIELD);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setSelectionColor(ACCENT);
        field.setSelectedTextColor(AppTheme.ON_ACCENT);
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(9, 11, 9, 11)));
    }

    private static JButton button(String text, Color color) {
        JButton button = new ModernButton(text, color);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(10, 15, 10, 15));
        button.setPreferredSize(new Dimension(10, 42));
        button.addActionListener(e -> AUDIO.playClick());
        return button;
    }

    private static void addFormRow(JPanel panel, GridBagConstraints c, int row, String title, JComponent field) {
        c.gridwidth = 1; c.gridy = row; c.insets = new Insets(7, 8, 7, 8);
        c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        panel.add(label(title, 15, Font.BOLD), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, c);
    }

    private record DialogView(JDialog dialog, JPanel body, JPanel actions) { }

    private static final class HeaderButton extends JButton {
        HeaderButton(String text) {
            super(text);
            setFont(new Font("Segoe UI", Font.BOLD, 18));
            setForeground(MUTED);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(BorderFactory.createLineBorder(BORDER));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            if (getModel().isRollover()) {
                g.setColor(AppTheme.INSET);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class ModernButton extends JButton {
        private final Color base;

        ModernButton(String text, Color base) {
            super(text);
            this.base = base;
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Color color = !isEnabled() ? AppTheme.MUTED
                    : getModel().isPressed() ? base.darker()
                    : getModel().isRollover() ? mixColor(base, AppTheme.ON_ACCENT, 0.08) : base;
            g.setColor(color);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(255, 255, 255, isEnabled() ? 32 : 16));
            g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class GradientPanel extends JPanel {
        GradientPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setPaint(new GradientPaint(0, 0, AppTheme.BACKGROUND,
                    getWidth(), getHeight(), AppTheme.BACKGROUND));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class RoundedPanel extends JPanel {
        RoundedPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(PANEL);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(BORDER);
            g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class ArenaBoardPanel extends JPanel {
        ArenaBoardPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(0xEDE3FF),
                    getWidth(), getHeight(), new Color(0xCDBAF3)));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(255, 255, 255, 100));
            for (int i = 0; i < 24; i++) {
                int x = (i * 137 + 23) % Math.max(1, getWidth());
                int y = (i * 97 + 17) % Math.max(1, getHeight());
                g.drawLine(x - 3, y, x + 3, y);
                g.drawLine(x, y - 3, x, y + 3);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class CardButton extends JButton {
        private Color ringColor;
        private String ownerLabel;
        private double liftProgress;
        private double flipScale = 1.0;
        private String themeEffect = "theme_classic";
        private double themePhase;

        CardButton(String text) {
            super(text);
            setBackground(AppTheme.PRIMARY);
            setForeground(Color.WHITE);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(new EmptyBorder(4, 4, 4, 4));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void setRingColor(Color ringColor) {
            this.ringColor = ringColor;
            repaint();
        }

        void setMotion(double liftProgress, double flipScale) {
            this.liftProgress = Math.max(0, Math.min(1, liftProgress));
            this.flipScale = Math.max(0.04, Math.min(1, flipScale));
            repaint();
        }

        void setThemeEffect(String themeEffect, double themePhase) {
            this.themeEffect = themeEffect == null ? "theme_classic" : themeEffect;
            this.themePhase = themePhase;
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color = getBackground();
            if (isEnabled() && getModel().isRollover()) color = mixColor(color, AppTheme.ON_ACCENT, 0.06);
            int normalY = 8;
            int hoverLift = isEnabled() && getModel().isRollover() ? 2 : 0;
            int y = normalY - (int) Math.round(7 * liftProgress) - hoverLift;
            int height = Math.max(2, getHeight() - 16);
            int fullWidth = Math.max(2, getWidth() - 12);
            double pressScale = getModel().isPressed() ? 0.96 : 1.0;
            int width = Math.max(2, (int) Math.round(fullWidth * flipScale * pressScale));
            int x = (getWidth() - width) / 2;
            int shadowAlpha = 58 + (int) Math.round(60 * liftProgress) + hoverLift * 10;
            g.setColor(new Color(0, 0, 0, Math.min(150, shadowAlpha)));
            g.fillRoundRect(x + 2, y + 4, width, height, 16, 16);
            g.setPaint(new GradientPaint(x, y, mixColor(color, AppTheme.ON_ACCENT, 0.08), x, y + height, color.darker()));
            g.fillRoundRect(x, y, width, height, 16, 16);
            Shape cardClip = g.getClip();
            g.clip(new java.awt.geom.RoundRectangle2D.Double(x, y, width, height, 16, 16));
            if (width > 8) {
                if ("?".equals(getText())) {
                    switch (themeEffect) {
                        case "theme_ocean" -> paintOceanEffect(g, x, y, width, height);
                        case "theme_violet" -> paintVioletEffect(g, x, y, width, height);
                        case "theme_gold" -> paintGoldEffect(g, x, y, width, height);
                        default -> paintClassicEffect(g, x, y, width, height);
                    }
                } else if ("title_memory".equals(themeEffect)) {
                    paintMemoryTitleEffect(g, x, y, width, height);
                } else if ("title_champion".equals(themeEffect)) {
                    paintChampionTitleEffect(g, x, y, width, height);
                }
            }
            g.setClip(cardClip);
            for (int glow = 3; glow >= 1; glow--) {
                g.setStroke(new BasicStroke(glow * 2f));
                g.setColor(new Color(241, 180, 255, 35));
                g.drawRoundRect(x, y, width, height, 16, 16);
            }
            g.setStroke(new BasicStroke(1.5f));
            g.setColor(new Color(255, 231, 175));
            g.drawRoundRect(x + 1, y + 1, Math.max(1, width - 2), Math.max(1, height - 2), 14, 14);
            g.setColor(new Color(248, 215, 255, 180));
            g.drawRoundRect(x + 4, y + 4, Math.max(1, width - 8), Math.max(1, height - 8), 11, 11);
            int badgeHeight = ownerLabel == null ? 0 : Math.min(22, Math.max(14, height / 4));
            if (flipScale > 0.22 && getText() != null) {
                g.setColor(getForeground());
                CardTheme.paintFace(g, getText(), "?".equals(getText())
                        ? getFont().deriveFont(Font.BOLD, Math.max(16f, height * 0.48f)) : getFont(), x + 9, y + 7,
                        Math.max(1, width - 18), Math.max(1, height - 14 - badgeHeight));
            }
            // Draw ownership after the illustration, in a reserved strip that artwork cannot cover.
            if (ringColor != null && flipScale > 0.25) {
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(7f));
                g.drawRoundRect(x + 4, y + 4, Math.max(1, width - 8), Math.max(1, height - 8), 12, 12);
                g.setColor(ringColor);
                g.setStroke(new BasicStroke(5f));
                g.drawRoundRect(x + 4, y + 4, Math.max(1, width - 8), Math.max(1, height - 8), 12, 12);
                if (ownerLabel != null && width > 20 && height > 20) {
                    int badgeY = y + height - badgeHeight - 3;
                    g.fillRoundRect(x + 3, badgeY, width - 6, badgeHeight, 8, 8);
                    g.setColor(GameColors.textOn(ringColor));
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, Math.min(12, badgeHeight - 3))));
                    FontMetrics metrics = g.getFontMetrics();
                    String label = ownerLabel;
                    if (metrics.stringWidth(label) > width - 16) label = ownerLabel.split(" · ", 2)[0];
                    g.drawString(label, x + (width - metrics.stringWidth(label)) / 2,
                            badgeY + (badgeHeight - metrics.getHeight()) / 2 + metrics.getAscent());
                }
            }
            g.dispose();
        }

        private void paintClassicEffect(Graphics2D g, int x, int y, int width, int height) {
            memorygame.common.CardArtwork.paintBack(g, x, y, width, height);
        }

        private void paintOceanEffect(Graphics2D g, int x, int y, int width, int height) {
            Shape oldClip = g.getClip();
            g.clipRect(x, y, width, height);

            int shimmerX = x - 30 + (int) Math.round((width + 60) * themePhase);
            g.setPaint(new GradientPaint(shimmerX, y, new Color(255, 255, 255, 0),
                    shimmerX + 28, y, new Color(186, 230, 253, 82), true));
            g.fillRect(shimmerX, y, 32, height);

            g.setStroke(new BasicStroke(1.8f));
            for (int wave = 0; wave < 2; wave++) {
                Path2D path = new Path2D.Double();
                double baseY = y + height * (0.46 + wave * 0.18);
                for (int px = 0; px <= width; px += 2) {
                    double angle = px * 0.12 + themePhase * Math.PI * 2 + wave * 1.4;
                    double py = baseY + Math.sin(angle) * 2.8;
                    if (px == 0) path.moveTo(x, py); else path.lineTo(x + px, py);
                }
                g.setColor(wave == 0 ? new Color(125, 211, 252, 150)
                        : new Color(103, 232, 249, 95));
                g.draw(path);
            }

            if (getModel().isRollover()) {
                double pulse = 0.5 + 0.5 * Math.sin(themePhase * Math.PI * 2);
                int radius = 8 + (int) Math.round(pulse * Math.min(width, height) * 0.32);
                g.setColor(new Color(165, 243, 252, 75));
                g.setStroke(new BasicStroke(2f));
                g.drawOval(x + width / 2 - radius, y + height / 2 - radius,
                        radius * 2, radius * 2);
            }
            g.setClip(oldClip);
        }

        private void paintVioletEffect(Graphics2D g, int x, int y, int width, int height) {
            Shape oldClip = g.getClip();
            g.clipRect(x, y, width, height);
            double pulse = 0.5 + 0.5 * Math.sin(themePhase * Math.PI * 2);
            g.setStroke(new BasicStroke(1.5f + (float) pulse));
            g.setColor(new Color(216, 180, 254, 80 + (int) (70 * pulse)));
            g.drawRect(x + 4, y + 4, Math.max(1, width - 9), Math.max(1, height - 9));
            for (int i = 0; i < 6; i++) {
                double phase = (themePhase + i * 0.173) % 1.0;
                int px = x + 6 + (int) ((width - 12) * ((phase * 1.7 + i * .21) % 1.0));
                int py = y + 6 + (int) ((height - 12) * ((phase + i * .29) % 1.0));
                int radius = 1 + (i % 2);
                g.setColor(new Color(233, 213, 255, 85 + i * 18));
                g.fillOval(px - radius, py - radius, radius * 2 + 1, radius * 2 + 1);
            }
            int glowX = x + width / 2;
            int glowY = y + height / 2;
            int glowRadius = Math.max(8, (int) (Math.min(width, height) * (0.18 + pulse * 0.12)));
            g.setColor(new Color(196, 181, 253, 20 + (int) (28 * pulse)));
            g.fillOval(glowX - glowRadius, glowY - glowRadius, glowRadius * 2, glowRadius * 2);
            g.setClip(oldClip);
        }

        private void paintGoldEffect(Graphics2D g, int x, int y, int width, int height) {
            Shape oldClip = g.getClip();
            g.clipRect(x, y, width, height);
            g.setColor(new Color(255, 237, 170, 28));
            for (int line = -height; line < width; line += 12) {
                g.drawLine(x + line, y + height, x + line + height, y);
            }
            int shineX = x - height + (int) Math.round((width + height * 2) * themePhase);
            Polygon shine = new Polygon(
                    new int[]{shineX, shineX + Math.max(10, width / 3), shineX + height + Math.max(10, width / 3), shineX + height},
                    new int[]{y + height, y + height, y, y}, 4);
            g.setColor(new Color(255, 248, 205, 72));
            g.fillPolygon(shine);
            g.setColor(new Color(255, 244, 180, 95));
            g.drawRect(x + 4, y + 4, Math.max(1, width - 9), Math.max(1, height - 9));
            for (int i = 0; i < 3; i++) {
                double sparklePhase = (themePhase + i * 0.31) % 1.0;
                int px = x + 9 + (int) ((width - 18) * ((i * 0.37 + 0.18) % 1.0));
                int py = y + 8 + (int) ((height - 16) * sparklePhase);
                int alpha = 55 + (int) (120 * Math.sin(sparklePhase * Math.PI));
                g.setColor(new Color(255, 250, 205, Math.max(0, alpha)));
                g.drawLine(px - 3, py, px + 3, py);
                g.drawLine(px, py - 3, px, py + 3);
            }
            g.setClip(oldClip);
        }

        private void paintMemoryTitleEffect(Graphics2D g, int x, int y, int width, int height) {
            Shape oldClip = g.getClip();
            g.clipRect(x, y, width, height);
            int cx = x + width / 2;
            int cy = y + height / 2;
            double pulse = 0.5 + 0.5 * Math.sin(themePhase * Math.PI * 2);

            for (int ring = 0; ring < 3; ring++) {
                double phase = (themePhase + ring / 3.0) % 1.0;
                int radius = 10 + (int) (phase * Math.min(width, height) * 0.46);
                int alpha = Math.max(0, (int) (105 * (1.0 - phase)));
                g.setColor(new Color(103, 232, 249, alpha));
                g.setStroke(new BasicStroke(1.4f));
                g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
            }

            int orbitRadius = Math.max(14, Math.min(width, height) / 3);
            for (int dot = 0; dot < 4; dot++) {
                double angle = themePhase * Math.PI * 2 + dot * Math.PI / 2;
                int px = cx + (int) Math.round(Math.cos(angle) * orbitRadius);
                int py = cy + (int) Math.round(Math.sin(angle) * orbitRadius);
                int radius = dot % 2 == 0 ? 3 : 2;
                g.setColor(new Color(165, 243, 252, 130 + dot * 20));
                g.fillOval(px - radius, py - radius, radius * 2 + 1, radius * 2 + 1);
            }

            int scanY = y - 12 + (int) Math.round((height + 24) * themePhase);
            g.setPaint(new GradientPaint(x, scanY - 8, new Color(103, 232, 249, 0),
                    x, scanY + 8, new Color(165, 243, 252, 65), true));
            g.fillRect(x, scanY - 8, width, 16);
            g.setColor(new Color(165, 243, 252, 45 + (int) (45 * pulse)));
            g.drawRect(x + 5, y + 5, Math.max(1, width - 11), Math.max(1, height - 11));
            g.setClip(oldClip);
        }

        private void paintChampionTitleEffect(Graphics2D g, int x, int y, int width, int height) {
            Shape oldClip = g.getClip();
            g.clipRect(x, y, width, height);
            int cx = x + width / 2;
            int cy = y + height / 2;
            double pulse = 0.5 + 0.5 * Math.sin(themePhase * Math.PI * 2);

            g.setStroke(new BasicStroke(1.2f));
            for (int ray = 0; ray < 12; ray++) {
                double angle = ray * Math.PI / 6 + themePhase * 0.35;
                int inner = Math.max(13, Math.min(width, height) / 5);
                int outer = inner + 9 + (int) (pulse * 7);
                int x1 = cx + (int) Math.round(Math.cos(angle) * inner);
                int y1 = cy + (int) Math.round(Math.sin(angle) * inner);
                int x2 = cx + (int) Math.round(Math.cos(angle) * outer);
                int y2 = cy + (int) Math.round(Math.sin(angle) * outer);
                g.setColor(new Color(254, 240, 138, 45 + (ray % 3) * 22));
                g.drawLine(x1, y1, x2, y2);
            }

            int shineX = x - height + (int) Math.round((width + height * 2) * themePhase);
            Polygon shine = new Polygon(
                    new int[]{shineX, shineX + 18, shineX + height + 18, shineX + height},
                    new int[]{y + height, y + height, y, y}, 4);
            g.setColor(new Color(255, 250, 205, 68));
            g.fillPolygon(shine);

            for (int i = 0; i < 7; i++) {
                double phase = (themePhase * 1.4 + i * 0.137) % 1.0;
                int px = x + 8 + (int) ((width - 16) * ((i * 0.23 + phase * 0.22) % 1.0));
                int py = y + height - 8 - (int) ((height - 16) * phase);
                int alpha = Math.max(0, (int) (170 * Math.sin(phase * Math.PI)));
                g.setColor(new Color(255, 244, 180, alpha));
                g.fillOval(px - 1, py - 1, 3, 3);
            }
            g.setColor(new Color(253, 224, 71, 80 + (int) (65 * pulse)));
            g.setStroke(new BasicStroke(2f));
            g.drawRect(x + 5, y + 5, Math.max(1, width - 11), Math.max(1, height - 11));
            g.setClip(oldClip);
        }
    }

    private final class PlayerRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                 boolean selected, boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
            label.setBorder(new EmptyBorder(7, 12, 7, 12));
            label.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            if (value instanceof PlayerItem player) {
                label.setBackground(selected ? AppTheme.SELECTED
                        : index % 2 == 0 ? AppTheme.INSET : AppTheme.SURFACE);
                label.setForeground(LoginPanel.INK);
                String state = switch (player.status) {
                    case "IDLE" -> "Rảnh";
                    case "QUEUE" -> "Đang tìm trận";
                    case "ROOM" -> "Trong phòng";
                    case "PLAYING" -> "Đang chơi";
                    default -> player.status;
                };
                String self = player.username.equalsIgnoreCase(username) ? "  [Bạn]" : friends.contains(player.username) ? "  [Bạn bè]" : "";
                label.setText("<html><b>" + player.username + self + "</b> &nbsp; <font color='"
                        + (player.status.equals("IDLE") ? AppTheme.hex(AppTheme.SECONDARY) : AppTheme.hex(AppTheme.MUTED)) + "'>● " + state
                        + "</font><br><font color='" + AppTheme.hex(AppTheme.MUTED) + "'>Điểm: " + player.rating + " &nbsp; • &nbsp; Thắng: "
                        + player.wins + (player.title.isBlank() ? "" : " &nbsp; • &nbsp; " + titleName(player.title)) + "</font></html>");
                label.setToolTipText(player.toString());
            }
            JPanel row = LobbyStyle.surface(new BorderLayout(8, 0), 4);
            row.setBorder(new EmptyBorder(5, 8, 7, 8));
            label.setOpaque(false);
            JLabel avatar = new JLabel(value instanceof PlayerItem item
                    ? lobbyAvatars.getOrDefault(item.username, LobbyStyle.cat(50)) : LobbyStyle.cat(50));
            row.add(avatar, BorderLayout.WEST);
            if (selected || focused) row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AppTheme.PRIMARY, 2, true), new EmptyBorder(3, 6, 5, 6)));
            row.add(label);
            if (value instanceof PlayerItem player && !player.username.equalsIgnoreCase(username)) {
                JLabel plus = new JLabel("+", SwingConstants.CENTER);
                plus.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
                plus.setForeground(canInvitePlayer(player) ? ACCENT : AppTheme.MUTED);
                plus.setPreferredSize(new Dimension(56, 40));
                plus.setToolTipText(canInvitePlayer(player) ? "Mời " + player.username + " vào phòng" : "Chỉ mời khi cả hai đang rảnh");
                plus.getAccessibleContext().setAccessibleName("Mời " + player.username + " vào phòng");
                row.add(plus, BorderLayout.EAST);
            }
            row.setSize(Math.max(1, list.getWidth()), list.getFixedCellHeight());
            row.doLayout();
            return row;
        }
    }

    private static final class RoomPlayerRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                 boolean selected, boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
            label.setBorder(new EmptyBorder(8, 12, 8, 12));
            label.setFont(new Font("Segoe UI", Font.BOLD, 16));
            if (!selected && value instanceof RoomPlayerItem player) {
                label.setBackground(index % 2 == 0 ? PANEL : AppTheme.INSET);
                label.setForeground(!player.online ? AppTheme.DANGER
                        : player.ready || player.replay ? AppTheme.PRIMARY : TEXT);
            }
            return label;
        }
    }

    private final class ShopRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                 boolean selected, boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
            label.setBorder(new EmptyBorder(10, 14, 10, 14));
            label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            if (!selected) label.setBackground(index % 2 == 0 ? PANEL : AppTheme.INSET);
            if (value instanceof ShopItem item) {
                Color swatch = decodeColor(item.color);
                double pulse = 0.5 + 0.5 * Math.sin((visualEffectPhase + index * 0.11) * Math.PI * 2);
                Color animatedSwatch = mixColor(swatch, Color.WHITE, 0.08 + pulse * 0.25);
                String marker = item.type.equals("TITLE") ? "★" : "◆";
                label.setText("<html><font color='" + toHex(animatedSwatch) + "'>" + marker
                        + "</font>&nbsp;&nbsp;<b>"
                        + item.name + "</b><br>&nbsp;&nbsp;&nbsp;&nbsp;"
                        + (item.equipped ? "Đang trang bị" : item.owned ? "Đã sở hữu"
                        : item.price + " xu") + "</html>");
                label.setForeground(item.equipped ? AppTheme.PRIMARY : TEXT);
                if (item.equipped) {
                    label.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 3, 0, 0, animatedSwatch),
                            new EmptyBorder(10, 11, 10, 14)));
                }
            }
            return label;
        }
    }

    private static Color mixColor(Color first, Color second, double amount) {
        double safe = Math.max(0, Math.min(1, amount));
        int red = (int) Math.round(first.getRed() * (1 - safe) + second.getRed() * safe);
        int green = (int) Math.round(first.getGreen() * (1 - safe) + second.getGreen() * safe);
        int blue = (int) Math.round(first.getBlue() * (1 - safe) + second.getBlue() * safe);
        return new Color(red, green, blue);
    }

    private static Color decodeColor(String color) {
        try { return Color.decode(color); }
        catch (NumberFormatException ignored) { return ACCENT; }
    }

    private static final class ShopItem {
        final String id;
        final String name;
        final String description;
        final int price;
        final String type;
        final String color;
        final boolean owned;
        final boolean equipped;

        ShopItem(String id, String name, String description, int price, String type,
                 String color, boolean owned, boolean equipped) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.price = price;
            this.type = type;
            this.color = color;
            this.owned = owned;
            this.equipped = equipped;
        }

        @Override public String toString() { return name; }
    }

    private static final class PlayerItem {
        final String username;
        final String status;
        final int rating;
        final int wins;
        final String title;

        PlayerItem(String username, String status, int rating, int wins, String title) {
            this.username = username;
            this.status = status;
            this.rating = rating;
            this.wins = wins;
            this.title = title;
        }

        @Override public String toString() {
            String statusText = switch (status) {
                case "IDLE" -> "Rảnh";
                case "QUEUE" -> "Đang ghép trận";
                case "ROOM" -> "Trong phòng";
                case "PLAYING" -> "Đang chơi";
                default -> status;
            };
            String displayName = title.isBlank() ? username : username + " [" + title + "]";
            return String.format("%-28s  %-16s  Xếp hạng: %d  •  Thắng: %d", displayName, statusText, rating, wins);
        }
    }

    private static final class RoomPlayerItem {
        final String username;
        final boolean ready;
        final boolean online;
        final boolean host;
        final boolean replay;
        final boolean postGame;
        final String title;

        RoomPlayerItem(String username, boolean ready, boolean online, boolean host,
                       boolean replay, boolean postGame, String title) {
            this.username = username;
            this.ready = ready;
            this.online = online;
            this.host = host;
            this.replay = replay;
            this.postGame = postGame;
            this.title = title;
        }

        @Override public String toString() {
            return (host ? "★ " : "   ") + username + (title.isBlank() ? "" : " [" + title + "]") + " — "
                    + (!online ? "Mất kết nối" : postGame ? replay ? "Muốn chơi lại" : "Chưa quyết định"
                    : ready ? "Sẵn sàng" : "Chưa sẵn sàng");
        }
    }
}
