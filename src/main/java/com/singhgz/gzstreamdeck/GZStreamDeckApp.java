package com.singhgz.gzstreamdeck;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Shell32;
import org.hid4java.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.List;

public class GZStreamDeckApp extends JFrame {

    private Robot systemRobot;
    private final Map<Integer, List<Integer>> keyBindings = new HashMap<>();
    
    // State Tracking for CPU Optimization
    private int lastButtonState = 0;
    private final int[] lastSliderValues = new int[]{-1, -1, -1, -1, -1};

    private final JComboBox<String>[] volumeSelectors = new JComboBox[5];
    private final JProgressBar[] volumeIndicators = new JProgressBar[5];
    private final JTextField[] keyTextFields = new JTextField[5];

    // Persistence Settings
    private final Properties config = new Properties();
    private final File configFile = new File("gzstreamdeck_config.properties");
    private final String[] savedAudioApps = new String[]{"Master Volume", "Master Volume", "Master Volume", "Master Volume", "Master Volume"};
    
    // UI State
    private Point initialClick;
    private boolean isUpdatingUI = false;
    private boolean isFirstPoll = true;

    public GZStreamDeckApp() {
        loadConfig();
        initTheme();
        initRobot();
        initUI();
        initSystemTray(); 
        initHID();
        startAudioProcessPoller();
    }

    // --- PERSISTENCE LOGIC ---
    private void loadConfig() {
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                config.load(reader);
                for (int i = 0; i < 5; i++) {
                    savedAudioApps[i] = config.getProperty("volApp_" + i, "Master Volume");
                    String keysStr = config.getProperty("keys_" + i, "");
                    if (!keysStr.isEmpty()) {
                        List<Integer> keys = new ArrayList<>();
                        for (String k : keysStr.split(",")) keys.add(Integer.parseInt(k));
                        keyBindings.put(i, keys);
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void saveConfig() {
        try (FileWriter writer = new FileWriter(configFile)) {
            for (int i = 0; i < 5; i++) {
                config.setProperty("volApp_" + i, savedAudioApps[i]);
                if (keyBindings.containsKey(i)) {
                    List<String> keyStrs = new ArrayList<>();
                    for (Integer k : keyBindings.get(i)) keyStrs.add(String.valueOf(k));
                    config.setProperty("keys_" + i, String.join(",", keyStrs));
                } else {
                    config.remove("keys_" + i);
                }
            }
            config.store(writer, "GZ Stream Deck Configuration");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void resetSettings() {
        int result = JOptionPane.showConfirmDialog(this, 
            "Are you sure you want to reset all bindings and audio settings?", 
            "Reset Settings", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            
        if (result == JOptionPane.YES_OPTION) {
            keyBindings.clear();
            for (int i = 0; i < 5; i++) {
                savedAudioApps[i] = "Master Volume";
                if (volumeSelectors[i] != null) volumeSelectors[i].setSelectedItem("Master Volume");
                if (keyTextFields[i] != null) keyTextFields[i].setText("Click here and press key(s)...");
            }
            if (configFile.exists()) configFile.delete();
            config.clear();
            saveConfig();
        }
    }

    // --- UI SETUP ---
    private void initTheme() {
        try {
            Map<String, String> theme = new HashMap<>();
            theme.put("@background", "#111114");
            theme.put("@foreground", "#E5E5E5");
            theme.put("@accentColor", "#D62828");
            theme.put("@buttonBackground", "#1C1C1C");
            theme.put("@componentBackground", "#1A1A1F");
            theme.put("Component.focusWidth", "1");
            theme.put("Component.arc", "5");
            theme.put("Button.arc", "5");
            theme.put("ProgressBar.arc", "5");
            theme.put("@selectionBackground", "#A4161A");
            theme.put("TabbedPane.selectedBackground", "#8F1D2C");
            theme.put("Slider.thumbColor", "#FF0054");
            theme.put("Slider.trackColor", "#D62828");
            FlatLaf.setGlobalExtraDefaults(theme);
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {
            System.err.println("Fatal Rendering Error");
        }
    }

    private void initRobot() {
        try {
            systemRobot = new Robot();
            systemRobot.setAutoDelay(0);
        } catch (AWTException e) {
            JOptionPane.showMessageDialog(this, "OS denied Robot access.");
        }
    }

    private Image createAppIcon() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.decode("#111114"));
        g2d.fillRoundRect(0, 0, 64, 64, 16, 16);
        g2d.setColor(Color.decode("#D62828"));
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 36));
        g2d.drawString("GZ", 8, 46);
        g2d.dispose();
        return img;
    }

    private void initSystemTray() {
        if (!SystemTray.isSupported()) return;

        SystemTray tray = SystemTray.getSystemTray();
        Image trayImage = createAppIcon();

        PopupMenu popup = new PopupMenu();
        MenuItem restoreItem = new MenuItem("Restore");
        MenuItem exitItem = new MenuItem("Exit");

        restoreItem.addActionListener(e -> {
            setVisible(true);
            setExtendedState(JFrame.NORMAL);
            toFront();
        });

        exitItem.addActionListener(e -> System.exit(0));

        popup.add(restoreItem);
        popup.addSeparator();
        popup.add(exitItem);

        TrayIcon trayIcon = new TrayIcon(trayImage, "GZ Stream Deck", popup);
        trayIcon.setImageAutoSize(true);
        
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                    setVisible(true);
                    setExtendedState(JFrame.NORMAL);
                    toFront();
                }
            }
        });

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("TrayIcon could not be added.");
        }
    }

    private JButton createHeaderButton(String text, boolean isCloseBtn) {
        JButton btn = new JButton(text);
        btn.setPreferredSize(new Dimension( 45, 35));
        btn.setMargin(new Insets(0, 0, 0, 0)); 
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setBackground(Color.decode("#111114"));
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 16)); 
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent evt) { 
                btn.setBackground(isCloseBtn ? Color.decode("#D62828") : Color.decode("#2A2A2F")); 
            }
            public void mouseExited(MouseEvent evt) { 
                btn.setBackground(Color.decode("#111114")); 
            }
        });
        return btn;
    }

    private void initUI() {
        setUndecorated(true);
        setSize(1000, 520);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        
        setIconImage(createAppIcon());

        // --- Custom Header ---
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.decode("#111114"));
        header.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.decode("#2A2A2F")));
        header.setPreferredSize(new Dimension(900 , 35));

        JLabel title = new JLabel("   GZ Stream Deck", SwingConstants.LEFT);
        title.setForeground(Color.decode("#D62828"));
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.add(title, BorderLayout.WEST);

        // Window Controls (Minimize & Close)
        JPanel controlBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controlBox.setOpaque(false);

        JButton minBtn = createHeaderButton("-", false);
        minBtn.addActionListener(e -> {
            if (SystemTray.isSupported()) {
                setVisible(false); // Hides to System Tray
            } else {
                setState(JFrame.ICONIFIED); 
            }
        });
        
        JButton closeBtn = createHeaderButton("X", true);
        closeBtn.addActionListener(e -> System.exit(0));

        controlBox.add(minBtn);
        controlBox.add(closeBtn);
        header.add(controlBox, BorderLayout.EAST);

        // Dragging Logic for Custom Header
        header.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { initialClick = e.getPoint(); }
        });
        header.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                int thisX = getLocation().x;
                int thisY = getLocation().y;
                int xMoved = e.getX() - initialClick.x;
                int yMoved = e.getY() - initialClick.y;
                setLocation(thisX + xMoved, thisY + yMoved);
            }
        });
        add(header, BorderLayout.NORTH);

        // --- Main Content Body ---
        JPanel mainBody = new JPanel(new BorderLayout());
        mainBody.setBorder(BorderFactory.createMatteBorder(0, 1, 1, 1, Color.decode("#2A2A2F")));

        // Global Instructions Sidebar
        JPanel instructionsPanel = new JPanel();
        instructionsPanel.setLayout(new BoxLayout(instructionsPanel, BoxLayout.Y_AXIS));
        instructionsPanel.setPreferredSize(new Dimension(270, 0));
        instructionsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Color.decode("#2A2A2F")),
                new EmptyBorder(20, 15, 20, 15)
        ));

        JLabel lblTitle = new JLabel("<html><b>How to use:</b></html>");
        lblTitle.setForeground(Color.decode("#D62828"));
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setBorder(new EmptyBorder(0, 0, 15, 0));

        JLabel lblText = new JLabel("<html><div style='width: 250px; margin-top: 5px;'>"
                + "<b style='color:#FF0054;'>Audio Mixer:</b><br>"
                + "1. Select an app for each channel.<br>"
                + "2. Slide the physical hardware slider<br>     to control its volume.<br><br><br>"
                + "<b style='color:#FF0054;'>Key Bindings:</b><br>"
                + "1. Switch to the Key Bindings tab.<br>"
                + "2. Click a text box and press any key <br>    combo (e.g. Ctrl+Shift+M).<br>"
                + "3. Press the physical button to trigger    <br> the shortcut."
                + "</div></html>");
        lblText.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblText.setForeground(Color.decode("#E5E5E5"));

        instructionsPanel.add(lblTitle);
        instructionsPanel.add(lblText);
        mainBody.add(instructionsPanel, BorderLayout.WEST);

        // Tabs Section
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Audio Mixer", createMixerPanel());
        tabbedPane.addTab("Key Bindings", createKeyBindPanel());
        mainBody.add(tabbedPane, BorderLayout.CENTER);

        // --- Footer Panel with Reset Button ---
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setBackground(Color.decode("#111114"));
        footer.setBorder(new EmptyBorder(5, 10, 5, 10));

        JButton resetBtn = new JButton("Reset Settings");
        resetBtn.setBackground(Color.decode("#A4161A"));
        resetBtn.setForeground(Color.WHITE);
        resetBtn.setFocusPainted(false);
        resetBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        resetBtn.addActionListener(e -> resetSettings());
        
        footer.add(resetBtn);
        mainBody.add(footer, BorderLayout.SOUTH);

        add(mainBody, BorderLayout.CENTER);
    }

    private JPanel createMixerPanel() {
        JPanel channelsPanel = new JPanel(new GridLayout(1, 5, 15, 15));
        channelsPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        for (int i = 0; i < 5; i++) {
            JPanel channelPanel = new JPanel(new BorderLayout(0, 10));
            
            JLabel lblChannel = new JLabel("CH " + (i + 1), SwingConstants.CENTER);
            lblChannel.setFont(new Font("Segoe UI", Font.BOLD, 16));
            
            volumeSelectors[i] = new JComboBox<>();
            volumeSelectors[i].addItem("Master Volume");
            
            final int index = i;
            volumeSelectors[i].addActionListener(e -> {
                if (!isUpdatingUI && volumeSelectors[index].getSelectedItem() != null) {
                    savedAudioApps[index] = (String) volumeSelectors[index].getSelectedItem();
                    saveConfig();
                }
            });

            volumeIndicators[i] = new JProgressBar(JProgressBar.VERTICAL, 0, 1023);
            volumeIndicators[i].setValue(512);

            JPanel barWrapper = new JPanel(new BorderLayout());
            barWrapper.setBorder(new EmptyBorder(0, 30, 0, 30)); 
            barWrapper.add(volumeIndicators[i], BorderLayout.CENTER);

            channelPanel.add(lblChannel, BorderLayout.NORTH);
            channelPanel.add(volumeSelectors[i], BorderLayout.SOUTH);
            channelPanel.add(barWrapper, BorderLayout.CENTER); 

            channelsPanel.add(channelPanel);
        }
        
        return channelsPanel;
    }

    private JPanel createKeyBindPanel() {
        JPanel panel = new JPanel(new GridLayout(5, 1, 10, 10));
        panel.setBorder(new EmptyBorder(20, 40, 20, 40));

        for (int i = 0; i < 5; i++) {
            JPanel row = new JPanel(new BorderLayout(15, 0));
            JLabel label = new JLabel("Hardware Button " + (i + 1) + ":");
            label.setPreferredSize(new Dimension(150, 30));
            
            keyTextFields[i] = new JTextField("Click here and press key(s)...");
            keyTextFields[i].setHorizontalAlignment(JTextField.CENTER);
            keyTextFields[i].setEditable(false);
            keyTextFields[i].setFocusTraversalKeysEnabled(false);
            
            if (keyBindings.containsKey(i)) {
                List<String> keyNames = new ArrayList<>();
                for (int code : keyBindings.get(i)) keyNames.add(KeyEvent.getKeyText(code));
                keyTextFields[i].setText(String.join(" + ", keyNames));
            }
            
            final int buttonIndex = i;
            keyTextFields[i].addKeyListener(new KeyAdapter() {
                List<Integer> currentKeys = new ArrayList<>();
                List<String> keyNames = new ArrayList<>();

                @Override
                public void keyPressed(KeyEvent e) {
                    int code = e.getKeyCode();
                    if (!currentKeys.contains(code)) {
                        currentKeys.add(code);
                        keyNames.add(KeyEvent.getKeyText(code));
                    }
                    keyTextFields[buttonIndex].setText(String.join(" + ", keyNames));
                    keyBindings.put(buttonIndex, new ArrayList<>(currentKeys));
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    currentKeys.clear();
                    keyNames.clear();
                    saveConfig(); 
                }
            });

            JButton btnClear = new JButton("Clear");
            btnClear.addActionListener(e -> {
                keyBindings.remove(buttonIndex);
                keyTextFields[buttonIndex].setText("Click here and press key(s)...");
                saveConfig(); 
            });

            row.add(label, BorderLayout.WEST);
            row.add(keyTextFields[i], BorderLayout.CENTER);
            row.add(btnClear, BorderLayout.EAST);
            panel.add(row);
        }
        return panel;
    }

    // --- LOGIC & HID COMMUNICATION ---
    private void initHID() {
        HidServicesSpecification hidSpec = new HidServicesSpecification();
        hidSpec.setAutoStart(false);
        
        HidServices hidServices = HidManager.getHidServices(hidSpec);
        hidServices.start();
        System.out.println("HID Scanner Started. Waiting for GZ Stream Deck...");

        // Active Polling Thread
        new Thread(() -> {
            HidDevice streamDeck = null;
            byte[] buffer = new byte[32];

            while (true) {
                try {
                    if (streamDeck == null || !streamDeck.isOpen()) {
                        for (HidDevice dev : hidServices.getAttachedHidDevices()) {
                            String prod = dev.getProduct();
                            boolean isArduino = prod != null && (prod.contains("Arduino") || prod.contains("Leonardo") || prod.contains("Micro"));
                            boolean isGamepad = (dev.getUsagePage() == 1 && (dev.getUsage() == 4 || dev.getUsage() == 5));
                            
                            if (isArduino || isGamepad) {
                                if (dev.open()) {
                                    streamDeck = dev;
                                    System.out.println("Hardware Connected: " + dev.getProduct());
                                    break;
                                }
                            }
                        }
                    }

                    if (streamDeck != null && streamDeck.isOpen()) {
                        int bytesRead = streamDeck.read(buffer, 50); // 50ms read block
                        if (bytesRead >= 11) {
                            processHardwareData(buffer);
                        } else {
                            // CPU OPTIMIZATION: Prevent Thread Spinnng if buffer is empty
                            Thread.sleep(5); 
                        }
                    } else {
                        Thread.sleep(1000); 
                    }
                } catch (Exception e) {
                    streamDeck = null;
                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private void processHardwareData(byte[] data) {
        int offset = (data[0] == 0x04) ? 0 : (data[1] == 0x04 ? 1 : -1);
        if (offset == -1) return; 

        // 1. Process Macro Buttons
        int currentButtonState = data[offset + 1] & 0xFF;
        for (int i = 0; i < 5; i++) {
            boolean isPressed = (currentButtonState & (1 << i)) != 0;
            boolean wasPressed = (lastButtonState & (1 << i)) != 0;

            if (isPressed && !wasPressed) triggerKeyPress(i);
            else if (!isPressed && wasPressed) triggerKeyRelease(i);
        }
        lastButtonState = currentButtonState;

        // 2. Process Analog Sliders 
        int[] currentSliderValues = new int[5];
        currentSliderValues[0] = (data[offset + 2] & 0xFF) | ((data[offset + 3] & 0xFF) << 8); 
        currentSliderValues[1] = (data[offset + 4] & 0xFF) | ((data[offset + 5] & 0xFF) << 8); 
        currentSliderValues[2] = (data[offset + 6] & 0xFF) | ((data[offset + 7] & 0xFF) << 8); 
        currentSliderValues[3] = (data[offset + 8] & 0xFF) | ((data[offset + 9] & 0xFF) << 8); 
        currentSliderValues[4] = (data[offset + 10] & 0xFF) | ((data[offset + 11] & 0xFF) << 8); 

        // CPU OPTIMIZATION: State caching. Only recalculate and update if a slider actually moved
        boolean uiNeedsUpdate = false;
        for (int i = 0; i < 5; i++) {
            if (currentSliderValues[i] != lastSliderValues[i]) {
                lastSliderValues[i] = currentSliderValues[i];
                uiNeedsUpdate = true;
                
                // Process WASAPI update in this background thread
                applyWasapiVolume(i, currentSliderValues[i]);
            }
        }

        // CPU OPTIMIZATION: Only push to the graphical EDT if an update occurred
        if (uiNeedsUpdate) {
            SwingUtilities.invokeLater(() -> {
                for (int i = 0; i < 5; i++) {
                    volumeIndicators[i].setValue(lastSliderValues[i]);
                }
            });
        }
    }

    private void triggerKeyPress(int buttonIndex) {
        List<Integer> keys = keyBindings.get(buttonIndex);
        if (keys == null || keys.isEmpty()) return;
        for (int vKey : keys) {
            try { systemRobot.keyPress(vKey); } catch (Exception ignored) {}
        }
    }

    private void triggerKeyRelease(int buttonIndex) {
        List<Integer> keys = keyBindings.get(buttonIndex);
        if (keys == null || keys.isEmpty()) return;
        for (int i = keys.size() - 1; i >= 0; i--) {
            try { systemRobot.keyRelease(keys.get(i)); } catch (Exception ignored) {}
        }
    }

    private void startAudioProcessPoller() {
        new Thread(() -> {
            List<String> lastApps = new ArrayList<>();
            while (true) {
                try {
                    // JNA WASAPI Hook gets active apps here
                    List<String> currentApps = WASAPIUtils.getActiveAudioApps(); 
                    
                    if (!currentApps.equals(lastApps) || isFirstPoll) {
                        lastApps = new ArrayList<>(currentApps);
                        SwingUtilities.invokeLater(() -> updateMixerUI(currentApps));
                    }
                    Thread.sleep(2000);
                } catch (Exception e) {
                    try { Thread.sleep(5000); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private void updateMixerUI(List<String> currentApps) {
        isUpdatingUI = true; 
        boolean configChanged = false;

        for (int i = 0; i < 5; i++) {
            JComboBox<String> selector = volumeSelectors[i];
            selector.removeAllItems();
            selector.addItem("Master Volume");
            
            for (String app : currentApps) {
                if (!app.equals("Master Volume")) selector.addItem(app);
            }

            // Validation: If a saved app is no longer running on startup, reset it.
            if (!savedAudioApps[i].equals("Master Volume") && !currentApps.contains(savedAudioApps[i])) {
                if (isFirstPoll) {
                    savedAudioApps[i] = "Master Volume";
                    configChanged = true;
                } else {
                    selector.addItem(savedAudioApps[i]);
                }
            }

            selector.setSelectedItem(savedAudioApps[i]);
        }
        
        if (configChanged) saveConfig();
        isFirstPoll = false;
        isUpdatingUI = false;
    }

    private void applyWasapiVolume(int channelIndex, int analogValue) {
        float volume = analogValue / 1023.0f; 
        String targetApp = (String) volumeSelectors[channelIndex].getSelectedItem();
        if (targetApp != null) {
            WASAPIUtils.setAppVolume(targetApp, volume);
        }
    }

    // --- APPLICATION STARTUP ---
    public static void main(String[] args) {
        
        // Task Manager Custom Identity 
        try {
            Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(new WString("com.singhgz.gzstreamdeck"));
        } catch (Throwable t) {}

        // Single Instance Enforcer
        final int UNIQUE_PORT = 45678; 
        try {
            ServerSocket serverSocket = new ServerSocket(UNIQUE_PORT, 10, InetAddress.getByName("127.0.0.1"));
            
            SwingUtilities.invokeLater(() -> {
                GZStreamDeckApp app = new GZStreamDeckApp();
                app.setVisible(true);
                
                new Thread(() -> {
                    while (true) {
                        try (Socket client = serverSocket.accept()) {
                            SwingUtilities.invokeLater(() -> {
                                app.setVisible(true);
                                app.setExtendedState(JFrame.NORMAL);
                                app.toFront();
                            });
                        } catch (Exception e) {}
                    }
                }).start();
            });
            
        } catch (Exception e) {
            System.out.println("GZ Stream Deck is already running! Waking up original session...");
            try (Socket client = new Socket("127.0.0.1", UNIQUE_PORT)) {
                client.getOutputStream().write(1);
            } catch (Exception ex) {}
            
            System.exit(0);
        }
    }
}
