package featurecat.lizzie.gui;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.Leelaz;
import featurecat.lizzie.analysis.gtpconfig.GtpConfigurationProbe;
import featurecat.lizzie.analysis.gtpconfig.GtpConfigurationSchema;
import featurecat.lizzie.util.EngineThreadPolicy;
import featurecat.lizzie.util.KataGoRuntimeHelper;
import featurecat.lizzie.util.Utils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import org.json.JSONException;
import org.json.JSONObject;

public class MoreEngines extends JPanel {
  public static Config config;
  public TableModel dataModel;
  PanelWithToolTips tablepanel;
  PanelWithToolTips selectpanel;
  JScrollPane scrollpane;
  public static JTable table;
  Font headFont;
  Font winrateFont;
  static boolean needUpdateEngine = false;
  static JDialog engjf;
  // Timer timer;
  int sortnum = 3;
  public static int selectedorder = -1;
  boolean issorted = false;
  JTextArea command;
  JFontTextField txtName;
  JFontLabel engineName;
  JFontLabel remoteManagedThreads;
  JTextArea threadPolicyStatus;
  private JPanel threadPolicyPanel;
  private JPanel threadPolicyControls;
  private JPanel threadPolicyChoices;
  private JPanel engineActionsPanel;
  JFontRadioButton threadPolicyCfg;
  JFontRadioButton threadPolicyBenchmark;
  JFontButton benchmarkSelectedEngine;
  JFontTextField txtInitialCommand;
  JFontTextField txtWidth;
  JFontTextField txtHeight;
  JFontTextField txtKomi;
  JFontButton exit;
  JFontButton scan;
  JFontButton delete;
  JFontButton add;
  JFontButton save;
  JFontButton cancel;
  JFontButton moveUp;
  JFontButton moveDown;
  JFontButton moveUp5;
  JFontButton moveDown5;
  JFontButton moveFirst;
  JFontButton moveLast;
  JFontButton gtpConfig;
  JFontCheckBox preload;
  JFontCheckBox chkDefault;
  JFontRadioButton rdoDefault;
  JFontRadioButton rdoLast;
  JFontRadioButton rdoMannul;
  JFontRadioButton rdoNone;
  int curIndex = -1;
  String keyGenPath = "";
  JFontCheckBox chkRemoteEngine;
  JFontRadioButton rdoUsePassword;
  JFontRadioButton rdoKeyGen;
  JFontTextField txtIP;
  JFontTextField txtPort;
  JFontTextField txtUserName;
  JPasswordField txtPassword;
  JFontButton scanKeygen;
  String selectedEntryId = "";
  EngineThreadPolicy.Source selectedThreadSource = EngineThreadPolicy.Source.CFG;
  boolean threadPolicySourceChanged;
  boolean loadingThreadPolicy;
  Timer threadPolicyRefreshTimer;

  private final ResourceBundle resourceBundle = Lizzie.resourceBundle;

  public MoreEngines() {
    setLayout((LayoutManager) null);
    try {
      Utils.normalizeEngineSettings();
    } catch (java.io.UncheckedIOException failure) {
      showThreadPolicySaveFailure(failure.getCause());
      throw failure;
    }
    this.dataModel = getTableModel();
    table = new JTable(this.dataModel);
    this.winrateFont = new Font(Config.sysDefaultFontName, 0, Math.max(Config.frameFontSize, 14));
    this.headFont = new Font(Config.sysDefaultFontName, 0, Math.max(Config.frameFontSize, 13));
    table.getTableHeader().setFont(this.headFont);
    table.setFont(this.winrateFont);
    AccessibilitySupport.named(
        table,
        this.resourceBundle.getString("MoreEngines.title"),
        this.resourceBundle.getString("MoreEngines.engineName"));
    table.getTableHeader().setReorderingAllowed(false);
    table.getTableHeader().setResizingAllowed(false);
    TableCellRenderer tcr = new ColorTableCellRenderer();
    table.setDefaultRenderer(Object.class, tcr);
    tablepanel = new PanelWithToolTips();
    tablepanel.setLayout(new BorderLayout());
    tablepanel.setBounds(0, 385, 885, 380);
    selectpanel = new PanelWithToolTips();
    selectpanel.setLayout((LayoutManager) null);
    add(this.tablepanel, "South");
    this.selectpanel.setBounds(0, 0, 900, 385);
    add(this.selectpanel, "North");
    this.scrollpane = new JScrollPane(table);
    this.tablepanel.add(this.scrollpane);
    table.setSelectionMode(0);
    table.setFillsViewportHeight(true);
    table.getColumnModel().getColumn(0).setPreferredWidth(30);
    table.getColumnModel().getColumn(1).setPreferredWidth(235);
    table.getColumnModel().getColumn(2).setPreferredWidth(305);
    table.getColumnModel().getColumn(3).setPreferredWidth(40);
    table.getColumnModel().getColumn(4).setPreferredWidth(20);
    table.getColumnModel().getColumn(5).setPreferredWidth(20);
    table.getColumnModel().getColumn(6).setPreferredWidth(30);
    table.getColumnModel().getColumn(7).setPreferredWidth(30);
    table.getColumnModel().getColumn(8).setPreferredWidth(30);
    table.setRowHeight(Config.menuHeight);
    table.getTableHeader().setFont(this.headFont);
    table
        .getTableHeader()
        .setPreferredSize(
            new Dimension(table.getColumnModel().getTotalColumnWidth(), Config.menuHeight));
    table.setFont(this.winrateFont);
    this.engineName = new JFontLabel(this.resourceBundle.getString("MoreEngines.engineName"));
    this.engineName.setForeground(Color.BLUE);
    this.engineName.setFont(new Font(Lizzie.config.uiFontName, 0, 14));
    JFontLabel lblName = new JFontLabel(this.resourceBundle.getString("MoreEngines.lblName"));
    this.txtName = new JFontTextField();
    this.txtName.setFont(new Font(Config.sysDefaultFontName, 0, Config.frameFontSize));
    this.txtKomi = new JFontTextField();
    JFontLabel lblInitialCommand =
        new JFontLabel(resourceBundle.getString("MoreEngines.lblInitialCommand"));
    txtInitialCommand = new JFontTextField();
    txtInitialCommand.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
    txtInitialCommand.setForeground(Color.GRAY);
    txtInitialCommand.setText(resourceBundle.getString("MoreEngines.initialCommandHint"));
    txtInitialCommand.addFocusListener(
        new FocusListener() {
          @Override
          public void focusGained(FocusEvent e) {
            if (resourceBundle
                .getString("MoreEngines.initialCommandHint")
                .equalsIgnoreCase(txtInitialCommand.getText())) {
              txtInitialCommand.setForeground(Color.BLACK);
              txtInitialCommand.setText("");
            }
          }

          @Override
          public void focusLost(FocusEvent e) {
            if ("".equals(txtInitialCommand.getText())) {
              txtInitialCommand.setForeground(Color.GRAY);
              txtInitialCommand.setText(resourceBundle.getString("MoreEngines.initialCommandHint"));
            }
          }
        });
    this.command = new JFontTextArea(5, 80);
    command.setBackground(this.getBackground());

    this.command.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
    this.txtName.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
    JFontLabel lblCommand = new JFontLabel(this.resourceBundle.getString("MoreEngines.lblCommand"));
    this.preload = new JFontCheckBox(this.resourceBundle.getString("MoreEngines.lblpreload"));
    JFontLabel lblWidth = new JFontLabel(this.resourceBundle.getString("MoreEngines.lblWidth"));
    JFontLabel lblHeight = new JFontLabel(this.resourceBundle.getString("MoreEngines.lblHeight"));
    JFontLabel lblKomi = new JFontLabel(this.resourceBundle.getString("MoreEngines.lblKomi"));
    this.remoteManagedThreads =
        new JFontLabel(this.resourceBundle.getString("EngineThreadPolicy.remoteManaged"));
    this.remoteManagedThreads.setVisible(false);
    this.threadPolicyCfg =
        new JFontRadioButton(this.resourceBundle.getString("EngineThreadPolicy.cfg"));
    this.threadPolicyBenchmark =
        new JFontRadioButton(this.resourceBundle.getString("EngineThreadPolicy.benchmark"));
    this.threadPolicyStatus = new JTextArea();
    this.threadPolicyStatus.setFont(threadPolicyCfg.getFont());
    this.threadPolicyStatus.setForeground(threadPolicyCfg.getForeground());
    this.threadPolicyStatus.setEditable(false);
    this.threadPolicyStatus.setFocusable(false);
    this.threadPolicyStatus.setOpaque(false);
    this.threadPolicyStatus.setBorder(null);
    this.threadPolicyStatus.setLineWrap(true);
    this.threadPolicyStatus.setWrapStyleWord(true);
    this.benchmarkSelectedEngine =
        new JFontButton(this.resourceBundle.getString("EngineThreadPolicy.startBenchmark"));
    ButtonGroup threadPolicyGroup = new ButtonGroup();
    threadPolicyGroup.add(threadPolicyCfg);
    threadPolicyGroup.add(threadPolicyBenchmark);
    threadPolicyCfg.addActionListener(event -> selectThreadPolicy(EngineThreadPolicy.Source.CFG));
    threadPolicyBenchmark.addActionListener(
        event -> selectThreadPolicy(EngineThreadPolicy.Source.BENCHMARK));
    this.txtWidth = new JFontTextField();
    this.txtHeight = new JFontTextField();
    this.add = new JFontButton(this.resourceBundle.getString("MoreEngines.add"));
    this.save = new JFontButton(this.resourceBundle.getString("MoreEngines.save"));
    this.cancel = new JFontButton(this.resourceBundle.getString("MoreEngines.cancel"));
    this.exit = new JFontButton(this.resourceBundle.getString("MoreEngines.exit"));
    this.delete = new JFontButton(this.resourceBundle.getString("MoreEngines.delete"));
    this.scan = new JFontButton(this.resourceBundle.getString("MoreEngines.scan"));
    this.moveUp = new JFontButton(this.resourceBundle.getString("MoreEngines.moveUp"));
    this.moveDown = new JFontButton(this.resourceBundle.getString("MoreEngines.moveDown"));
    this.moveUp5 = new JFontButton(this.resourceBundle.getString("MoreEngines.moveUp5"));
    this.moveDown5 = new JFontButton(this.resourceBundle.getString("MoreEngines.moveDown5"));
    this.moveFirst = new JFontButton(this.resourceBundle.getString("MoreEngines.moveFirst"));
    this.moveLast = new JFontButton(this.resourceBundle.getString("MoreEngines.moveLast"));
    this.gtpConfig = new JFontButton(this.resourceBundle.getString("MoreEngines.gtpConfig"));
    this.moveUp.setMargin(new Insets(0, 0, 0, 0));
    this.moveDown.setMargin(new Insets(0, 0, 0, 0));
    this.moveUp5.setMargin(new Insets(0, 0, 0, 0));
    this.moveDown5.setMargin(new Insets(0, 0, 0, 0));
    this.moveFirst.setMargin(new Insets(0, 0, 0, 0));
    this.moveLast.setMargin(new Insets(0, 0, 0, 0));
    this.gtpConfig.setMargin(new Insets(0, 4, 0, 4));
    this.scan.setMargin(new Insets(0, 0, 0, 0));
    this.add.setMargin(new Insets(0, 0, 0, 0));
    this.save.setMargin(new Insets(0, 0, 0, 0));
    this.cancel.setMargin(new Insets(0, 0, 0, 0));
    this.exit.setMargin(new Insets(0, 0, 0, 0));
    this.delete.setMargin(new Insets(0, 0, 0, 0));
    this.chkDefault = new JFontCheckBox(this.resourceBundle.getString("MoreEngines.lbldefault"));
    JFontLabel lblchooseStart =
        new JFontLabel(this.resourceBundle.getString("ChooseMoreEngine.lblchooseStart"));
    this.rdoDefault =
        new JFontRadioButton(this.resourceBundle.getString("MoreEngines.lblrdoDefault"));
    this.rdoLast =
        new JFontRadioButton(this.resourceBundle.getString("ChooseMoreEngine.lblrdoLast"));
    this.rdoMannul =
        new JFontRadioButton(this.resourceBundle.getString("ChooseMoreEngine.lblrdoMannul"));
    rdoNone = new JFontRadioButton(resourceBundle.getString("ChooseMoreEngine.lblrdoNone"));
    this.chkRemoteEngine =
        new JFontCheckBox(this.resourceBundle.getString("MoreEngines.chkRemoteEngine"));
    this.command
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent event) {
                updateThreadPolicyDetails();
              }

              @Override
              public void removeUpdate(DocumentEvent event) {
                updateThreadPolicyDetails();
              }

              @Override
              public void changedUpdate(DocumentEvent event) {
                updateThreadPolicyDetails();
              }
            });
    this.chkRemoteEngine.addItemListener(event -> updateThreadPolicyDetails());
    this.chkRemoteEngine.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (chkRemoteEngine.isSelected()) {
              txtIP.setEnabled(true);
              txtPort.setEnabled(true);
              rdoUsePassword.setEnabled(true);
              rdoKeyGen.setEnabled(true);
              txtUserName.setEnabled(true);
              if (rdoUsePassword.isSelected()) txtPassword.setEnabled(true);
              if (rdoKeyGen.isSelected()) scanKeygen.setEnabled(true);
            } else {
              txtIP.setEnabled(false);
              txtPort.setEnabled(false);
              rdoUsePassword.setEnabled(false);
              rdoKeyGen.setEnabled(false);
              txtUserName.setEnabled(false);
              txtPassword.setEnabled(false);
              scanKeygen.setEnabled(false);
            }
          }
        });
    ImageIcon btnRemoteEngineIcon = new ImageIcon();
    try {
      btnRemoteEngineIcon.setImage(
          ImageIO.read(getClass().getResourceAsStream("/assets/settings.png")));
    } catch (IOException e) {
      e.printStackTrace();
    }
    JFontButton btnRemoteEngine = new JFontButton(btnRemoteEngineIcon);
    AccessibilitySupport.button(
        btnRemoteEngine,
        resourceBundle.getString("MoreEngines.aboutRemoteEngineTitle"),
        resourceBundle.getString("MoreEngines.aboutRemoteEngine"));
    btnRemoteEngine.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            Discribe lizzieCacheDiscribe = new Discribe();
            lizzieCacheDiscribe.setInfo(
                resourceBundle.getString("MoreEngines.aboutRemoteEngine"),
                resourceBundle.getString("MoreEngines.aboutRemoteEngineTitle"),
                engjf);
          }
        });
    this.txtIP = new JFontTextField();
    this.txtPort = new JFontTextField();
    this.txtUserName = new JFontTextField();
    this.txtPassword = new JPasswordField();
    JFontLabel lblIp = new JFontLabel("IP");
    JFontLabel lblPort = new JFontLabel(this.resourceBundle.getString("MoreEngines.lblPort"));
    JFontLabel lblUserName =
        new JFontLabel(this.resourceBundle.getString("MoreEngines.rdoUserName"));
    this.rdoKeyGen = new JFontRadioButton(this.resourceBundle.getString("MoreEngines.rdoKeygen"));
    this.rdoUsePassword =
        new JFontRadioButton(this.resourceBundle.getString("MoreEngines.lblPassword"));
    this.scanKeygen = new JFontButton(this.resourceBundle.getString("MoreEngines.scanKeygen"));
    this.scanKeygen.setMargin(new Insets(0, 0, 0, 0));
    this.scanKeygen.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            MoreEngines.engjf.setAlwaysOnTop(false);
            FileDialog fileDialog =
                new FileDialog(
                    MoreEngines.engjf, resourceBundle.getString("MoreEngines.chooseKeygen"));
            fileDialog.setLocationRelativeTo(MoreEngines.engjf);
            fileDialog.setAlwaysOnTop(true);
            fileDialog.setModal(true);
            fileDialog.setMultipleMode(false);
            fileDialog.setMode(0);
            fileDialog.setVisible(true);
            File[] file = fileDialog.getFiles();
            if (file.length > 0) keyGenPath = file[0].getAbsolutePath();
            scanKeygen.setToolTipText(keyGenPath);
            rdoKeyGen.setToolTipText(keyGenPath);
            MoreEngines.engjf.setAlwaysOnTop(true);
          }
        });
    this.rdoUsePassword.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            scanKeygen.setEnabled(false);
            txtPassword.setEnabled(true);
          }
        });
    this.rdoKeyGen.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            scanKeygen.setEnabled(true);
            txtPassword.setEnabled(false);
          }
        });
    ButtonGroup gourpKeyPassword = new ButtonGroup();
    gourpKeyPassword.add(rdoUsePassword);
    gourpKeyPassword.add(rdoKeyGen);

    this.selectpanel.add(lblIp);
    this.selectpanel.add(lblPort);
    this.selectpanel.add(this.rdoUsePassword);
    this.selectpanel.add(lblUserName);
    this.selectpanel.add(this.chkRemoteEngine);
    this.selectpanel.add(btnRemoteEngine);
    this.selectpanel.add(this.txtIP);
    this.selectpanel.add(this.txtPort);
    this.selectpanel.add(this.txtUserName);
    this.selectpanel.add(this.txtPassword);
    this.selectpanel.add(this.rdoKeyGen);
    this.selectpanel.add(this.scanKeygen);
    AccessibilitySupport.labelFor(lblName, txtName, lblName.getText());
    AccessibilitySupport.labelFor(lblCommand, command, lblCommand.getText());
    AccessibilitySupport.labelFor(
        lblInitialCommand, txtInitialCommand, lblInitialCommand.getText());
    AccessibilitySupport.labelFor(lblWidth, txtWidth, lblWidth.getText());
    AccessibilitySupport.labelFor(lblHeight, txtHeight, lblHeight.getText());
    AccessibilitySupport.labelFor(lblKomi, txtKomi, lblKomi.getText());
    AccessibilitySupport.labelFor(lblIp, txtIP, lblIp.getText());
    AccessibilitySupport.labelFor(lblPort, txtPort, lblPort.getText());
    AccessibilitySupport.labelFor(lblUserName, txtUserName, lblUserName.getText());
    AccessibilitySupport.named(txtPassword, rdoUsePassword.getText(), rdoUsePassword.getText());
    this.engineName.setBounds(5, 3, 700, 24);
    int formControlX =
        Math.max(
            90,
            Math.max(
                    Math.max(lblName.getPreferredSize().width, lblCommand.getPreferredSize().width),
                    Math.max(
                        lblInitialCommand.getPreferredSize().width, scan.getPreferredSize().width))
                + 10);
    int formLabelWidth = formControlX - 10;
    int formControlWidth = 880 - formControlX;
    lblName.setBounds(5, 32, formLabelWidth, 24);
    lblCommand.setBounds(5, 60, formLabelWidth, 24);
    this.scan.setBounds(5, 83, formLabelWidth, 24);
    this.txtName.setBounds(formControlX, 35, formControlWidth, 24);
    this.command.setBounds(formControlX, 65, formControlWidth, 170);
    lblInitialCommand.setBounds(5, 240, formLabelWidth, 24);
    this.txtInitialCommand.setBounds(formControlX, 240, formControlWidth, 24);
    int boardSettingsX = 5;
    boardSettingsX = placeInRow(preload, boardSettingsX, 270, 4, 0);
    boardSettingsX = placeInRow(chkDefault, boardSettingsX, 270, 12, 0);
    boardSettingsX = placeInRow(lblWidth, boardSettingsX, 270, 4, 0);
    boardSettingsX = placeInRow(txtWidth, boardSettingsX, 270, 12, 40);
    boardSettingsX = placeInRow(lblHeight, boardSettingsX, 270, 4, 0);
    boardSettingsX = placeInRow(txtHeight, boardSettingsX, 270, 12, 40);
    boardSettingsX = placeInRow(lblKomi, boardSettingsX, 270, 4, 0);
    placeInRow(txtKomi, boardSettingsX, 270, 0, 48);
    JFontLabel threadSourceLabel =
        new JFontLabel(resourceBundle.getString("EngineThreadPolicy.sourceLabel"));
    threadSourceLabel.setFont(threadSourceLabel.getFont().deriveFont(Font.BOLD));
    AccessibilitySupport.labelFor(
        threadSourceLabel, threadPolicyCfg, threadSourceLabel.getText());
    threadPolicyChoices = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 0));
    threadPolicyChoices.setOpaque(false);
    threadPolicyChoices.add(threadPolicyCfg);
    threadPolicyChoices.add(threadPolicyBenchmark);
    threadPolicyControls = new JPanel(new BorderLayout(12, 0));
    threadPolicyControls.setOpaque(false);
    threadPolicyControls.add(threadSourceLabel, BorderLayout.WEST);
    threadPolicyControls.add(threadPolicyChoices, BorderLayout.CENTER);
    threadPolicyControls.add(benchmarkSelectedEngine, BorderLayout.EAST);
    threadPolicyControls.setPreferredSize(
        new Dimension(880, Math.max(28, threadPolicyControls.getPreferredSize().height)));
    threadPolicyPanel = new JPanel(new BorderLayout(0, 6));
    threadPolicyPanel.setOpaque(false);
    threadPolicyPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
    threadPolicyPanel.add(threadPolicyControls, BorderLayout.NORTH);
    threadPolicyPanel.add(remoteManagedThreads, BorderLayout.CENTER);
    threadPolicyPanel.add(threadPolicyStatus, BorderLayout.SOUTH);
    int remoteX = 5;
    remoteX = placeInRow(chkRemoteEngine, remoteX, 384, 6, 0);
    remoteX = placeInRow(btnRemoteEngine, remoteX, 384, 8, 24);
    remoteX = placeInRow(lblIp, remoteX, 384, 4, 0);
    remoteX = placeInRow(txtIP, remoteX, 384, 8, 150);
    remoteX = placeInRow(lblPort, remoteX, 384, 4, 0);
    remoteX = placeInRow(txtPort, remoteX, 384, 8, 58);
    remoteX = placeInRow(lblUserName, remoteX, 384, 4, 0);
    placeInRow(txtUserName, remoteX, 384, 0, 150);

    int authX = 5;
    authX = placeInRow(rdoUsePassword, authX, 412, 4, 0);
    authX = placeInRow(txtPassword, authX, 412, 14, 150);
    authX = placeInRow(rdoKeyGen, authX, 412, 4, 0);
    placeInRow(scanKeygen, authX, 412, 0, 100);

    int orderingX = 5;
    orderingX = placeInRow(moveUp, orderingX, 442, 2, 55);
    orderingX = placeInRow(moveDown, orderingX, 442, 2, 55);
    orderingX = placeInRow(moveUp5, orderingX, 442, 2, 55);
    orderingX = placeInRow(moveDown5, orderingX, 442, 2, 55);
    orderingX = placeInRow(moveFirst, orderingX, 442, 2, 55);
    placeInRow(moveLast, orderingX, 442, 0, 55);
    int gtpConfigWidth =
        Math.min(200, AccessibilitySupport.localizedControlWidth(this.gtpConfig, 150));
    this.gtpConfig.setBounds(360, 442, gtpConfigWidth, 24);

    int actionRight = 885;
    actionRight = placeFromRight(exit, actionRight, 442, 2, 60);
    actionRight = placeFromRight(save, actionRight, 442, 18, 60);
    actionRight = placeFromRight(cancel, actionRight, 442, 2, 54);
    actionRight = placeFromRight(delete, actionRight, 442, 2, 54);
    placeFromRight(add, actionRight, 442, 0, 54);

    int startupX = 5;
    startupX = placeInRow(lblchooseStart, startupX, 470, 6, 0);
    startupX = placeInRow(rdoDefault, startupX, 470, 4, 0);
    startupX = placeInRow(rdoLast, startupX, 470, 4, 0);
    startupX = placeInRow(rdoMannul, startupX, 470, 4, 0);
    placeInRow(rdoNone, startupX, 470, 0, 0);
    //    this.rdoDefault.setBounds(
    //        Lizzie.config.isFrameFontSmall() ? 70 : (Lizzie.config.isFrameFontMiddle() ? 90 :
    // 110),
    //        360,
    //        (Lizzie.config.isFrameFontSmall() ? 130 : (Lizzie.config.isFrameFontMiddle() ? 160 :
    // 190))
    //            + (Lizzie.config.isChinese ? 0 : 15),
    //        24);
    //    this.rdoLast.setBounds(
    //        (Lizzie.config.isFrameFontSmall() ? 210 : (Lizzie.config.isFrameFontMiddle() ? 250 :
    // 310))
    //            + (Lizzie.config.isChinese ? 0 : 15),
    //        360,
    //        Lizzie.config.isFrameFontSmall() ? 160 : (Lizzie.config.isFrameFontMiddle() ? 215 :
    // 250),
    //        24);
    //    this.rdoMannul.setBounds(
    //        (Lizzie.config.isFrameFontSmall() ? 368 : (Lizzie.config.isFrameFontMiddle() ? 465 :
    // 570))
    //            + (Lizzie.config.isChinese ? 0 : 15),
    //        360,
    //        145,
    //        24);
    JFontButton btnEncrypt =
        new JFontButton(this.resourceBundle.getString("MoreEngines.btnEncrypt"));
    btnEncrypt.setMargin(new Insets(0, 0, 0, 0));
    btnEncrypt.setBounds(765, 470, 120, 24);
    this.selectpanel.add(btnEncrypt);
    btnEncrypt.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (command.getText().startsWith("encryption||")) return;
            command.setText("encryption||" + Utils.doEncrypt2(command.getText().trim()));
          }
        });
    ButtonGroup startGroup = new ButtonGroup();
    startGroup.add(this.rdoDefault);
    startGroup.add(this.rdoLast);
    startGroup.add(this.rdoMannul);
    startGroup.add(this.rdoNone);
    if (Lizzie.config.uiConfig.optBoolean("autoload-default", false)) rdoDefault.setSelected(true);
    else if (Lizzie.config.uiConfig.optBoolean("autoload-last", false)) rdoLast.setSelected(true);
    else if (Lizzie.config.uiConfig.optBoolean("autoload-empty", false)) rdoNone.setSelected(true);
    else rdoMannul.setSelected(true);

    setEnable(false);
    this.selectpanel.setBounds(0, 0, 900, 500);
    this.tablepanel.setBounds(0, 500, 885, 285);
    this.selectpanel.add(this.engineName);
    this.selectpanel.add(lblName);
    this.selectpanel.add(this.txtName);
    this.selectpanel.add(this.command);
    this.selectpanel.add(lblCommand);
    this.selectpanel.add(lblInitialCommand);
    this.selectpanel.add(this.txtInitialCommand);
    this.selectpanel.add(this.preload);
    this.selectpanel.add(lblWidth);
    this.selectpanel.add(this.txtWidth);
    this.selectpanel.add(lblHeight);
    this.selectpanel.add(this.txtHeight);
    this.selectpanel.add(lblKomi);
    this.selectpanel.add(this.txtKomi);
    this.selectpanel.add(threadPolicyPanel);
    this.selectpanel.add(this.scan);
    this.selectpanel.add(this.add);
    this.selectpanel.add(this.save);
    this.selectpanel.add(this.cancel);
    this.selectpanel.add(this.exit);
    this.selectpanel.add(this.moveUp);
    this.selectpanel.add(this.moveUp5);
    this.selectpanel.add(this.moveFirst);
    this.selectpanel.add(this.moveLast);
    this.selectpanel.add(this.moveDown);
    this.selectpanel.add(this.moveDown5);
    this.selectpanel.add(this.gtpConfig);
    this.selectpanel.add(this.chkDefault);
    this.selectpanel.add(lblchooseStart);
    this.selectpanel.add(this.rdoDefault);
    this.selectpanel.add(this.rdoLast);
    this.selectpanel.add(this.rdoMannul);
    this.selectpanel.add(this.rdoNone);
    this.selectpanel.add(this.delete);
    AccessibilitySupport.button(
        this.gtpConfig,
        this.resourceBundle.getString("MoreEngines.gtpConfig"),
        this.resourceBundle.getString("MoreEngines.gtpConfigDescription"));
    AccessibilitySupport.applyToTree(this);
    AccessibilitySupport.button(
        this.benchmarkSelectedEngine,
        this.benchmarkSelectedEngine.getText(),
        this.benchmarkSelectedEngine.getText());
    this.scan.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            GetEngineLine getEngineLine = new GetEngineLine();
            String el = getEngineLine.getEngineLine(MoreEngines.engjf, false, false, false, false);
            if (!el.isEmpty()) command.setText(el);
            setVisible(true);
          }
        });
    this.gtpConfig.addActionListener(event -> configureSelectedEngine());
    this.benchmarkSelectedEngine.addActionListener(event -> benchmarkSelectedEngine());
    this.add.addActionListener(
        event -> {
          if (!checkSave()) return;
          ArrayList<EngineData> engData = Utils.getEngineData();
          EngineData newEng = new EngineData();
          newEng.commands = "";
          newEng.height = 19;
          newEng.index = 0;
          newEng.isDefault = false;
          newEng.komi = 7.5F;
          newEng.name = resourceBundle.getString("ChooseMoreEngine.newEngine");
          newEng.preload = false;
          newEng.width = 19;
          engData.add(0, newEng);
          if (!saveEngineCatalog(engData)) return;
          markEngineCatalogChangedAndRefresh();
          handleTableClick(0);
        });
    this.delete.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            SwingUtilities.invokeLater(
                new Runnable() {
                  public void run() {
                    Object[] options1 = {
                      resourceBundle.getString("MoreEngines.deleteHint"),
                      resourceBundle.getString("MoreEngines.deleteHint2")
                    };
                    int ret1 =
                        JOptionPane.showOptionDialog(
                            MoreEngines.engjf,
                            resourceBundle.getString("MoreEngines.deleteHint5"),
                            resourceBundle.getString("MoreEngines.deleteHint6"),
                            0,
                            3,
                            null,
                            options1,
                            options1[0]);
                    if (ret1 != 0) return;
                    ArrayList<EngineData> engineData = Utils.getEngineData();
                    int selectedIndex = findEntryIndex(engineData, selectedEntryId);
                    if (selectedIndex < 0) {
                      showThreadPolicySaveFailure(
                          new IOException(EngineThreadPolicy.message("targetDeleted")));
                      return;
                    }
                    engineData.remove(selectedIndex);
                    if (!saveEngineCatalog(engineData)) return;
                    table.validate();
                    table.updateUI();
                    table.getSelectionModel().clearSelection();
                    markEngineCatalogChangedAndRefresh();
                    selectedEntryId = "";
                    handleTableClick(Math.min(selectedIndex, engineData.size()));
                  }
                });
          }
        });
    this.exit.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            if (!checkSave()) return;
            MoreEngines.engjf.setVisible(false);
            if (needUpdateEngine)
              try {
                Lizzie.engineManager.updateEngines();
              } catch (JSONException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
              } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
              }
          }
        });
    this.cancel.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            command.setText("");
            engineName.setText(resourceBundle.getString("MoreEngines.engineName"));
            txtName.setText("");
            txtInitialCommand.setText("");
            txtInitialCommand.setForeground(Color.GRAY);
            txtInitialCommand.setText(resourceBundle.getString("MoreEngines.initialCommandHint"));
            preload.setSelected(false);
            txtWidth.setText("");
            txtHeight.setText("");
            chkDefault.setSelected(false);
            txtIP.setText("");
            txtPort.setText("");
            rdoUsePassword.setSelected(false);
            rdoKeyGen.setSelected(false);
            keyGenPath = "";
            txtUserName.setText("");
            txtPassword.setText("");
            scanKeygen.setToolTipText("");
            scanKeygen.setToolTipText("");
            curIndex = -1;
            selectedEntryId = "";
            threadPolicySourceChanged = false;
            setEnable(false);
            table.getSelectionModel().clearSelection();
            updateThreadPolicyDetails();
          }
        });
    this.save.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            boolean empty = command.getText().equals("");
            if (empty) command.setText(" ");
            boolean saved = curIndex < 0 || saveCurrentEngineConfig();
            if (empty) command.setText("");
            if (!saved || !saveDefaultEngine()) return;
            table.validate();
            table.updateUI();
          }
        });
    table.addMouseListener(
        new MouseAdapter() {
          public void mouseClicked(MouseEvent e) {
            int row = table.rowAtPoint(e.getPoint());
            if (!checkSave()) return;
            handleTableClick(row);
          }
        });
    this.moveFirst.addActionListener(event -> moveSelectedEngine(Integer.MIN_VALUE));
    this.moveLast.addActionListener(event -> moveSelectedEngine(Integer.MAX_VALUE));
    this.moveUp.addActionListener(event -> moveSelectedEngine(-1));
    this.moveUp5.addActionListener(event -> moveSelectedEngine(-5));
    this.moveDown.addActionListener(event -> moveSelectedEngine(1));
    this.moveDown5.addActionListener(event -> moveSelectedEngine(5));
    engineActionsPanel = new JPanel(null);
    engineActionsPanel.setOpaque(false);
    for (Component component : selectpanel.getComponents()) {
      if (component != threadPolicyPanel && component.getY() >= 384) {
        component.setLocation(component.getX(), component.getY() - 384);
        engineActionsPanel.add(component);
      }
    }
    selectpanel.add(engineActionsPanel);
    updateThreadPolicyDetails();
  }

  private static int placeInRow(
      Component component, int x, int y, int trailingGap, int minimumWidth) {
    int preferredWidth =
        component.getPreferredSize() == null ? 0 : component.getPreferredSize().width;
    int width = Math.max(minimumWidth, preferredWidth);
    if (component instanceof AbstractButton) {
      width += 8;
    } else if (component instanceof JLabel) {
      width += 2;
    }
    component.setBounds(x, y, width, 24);
    return x + width + trailingGap;
  }

  private static int placeFromRight(
      Component component, int right, int y, int leadingGap, int minimumWidth) {
    int preferredWidth =
        component.getPreferredSize() == null ? 0 : component.getPreferredSize().width;
    int width = Math.max(minimumWidth, preferredWidth);
    if (component instanceof AbstractButton) {
      width += 8;
    }
    int x = right - width;
    component.setBounds(x, y, width, 24);
    return x - leadingGap;
  }

  class ColorTableCellRenderer extends DefaultTableCellRenderer {
    DefaultTableCellRenderer renderer;

    ColorTableCellRenderer() {
      this.renderer = new DefaultTableCellRenderer();
    }

    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (column == 2) {
        JLabel label =
            (JLabel)
                super.getTableCellRendererComponent(
                    table, value, row == curIndex, hasFocus, row, column);
        label.setToolTipText(value.toString());
        return label;
      }
      return this.renderer.getTableCellRendererComponent(
          table, value, row == curIndex, false, row, column);
    }
  }

  public boolean saveDefaultEngine() {
    boolean oldDefault = Lizzie.config.uiConfig.optBoolean("autoload-default", false);
    boolean oldLast = Lizzie.config.uiConfig.optBoolean("autoload-last", false);
    boolean oldEmpty = Lizzie.config.uiConfig.optBoolean("autoload-empty", false);
    if (!updateStartupMode(
        Lizzie.config.uiConfig,
        rdoDefault.isSelected(),
        rdoLast.isSelected(),
        rdoNone.isSelected())) return true;
    try {
      Lizzie.config.save();
      return true;
    } catch (IOException failure) {
      Lizzie.config.uiConfig.put("autoload-default", oldDefault);
      Lizzie.config.uiConfig.put("autoload-last", oldLast);
      Lizzie.config.uiConfig.put("autoload-empty", oldEmpty);
      showThreadPolicySaveFailure(failure);
      return false;
    }
  }

  private boolean saveEngineCatalog(ArrayList<EngineData> entries) {
    try {
      Utils.saveEngineSettings(entries);
      return true;
    } catch (java.io.UncheckedIOException failure) {
      showThreadPolicySaveFailure(failure.getCause());
      return false;
    }
  }

  private void moveSelectedEngine(int direction) {
    ArrayList<EngineData> entries = Utils.getEngineData();
    int from = findEntryIndex(entries, selectedEntryId);
    if (from < 0 || entries.size() < 2) return;
    int requestedIndex =
        direction == Integer.MIN_VALUE
            ? 0
            : direction == Integer.MAX_VALUE ? entries.size() - 1 : from + direction;
    int to = Math.max(0, Math.min(requestedIndex, entries.size() - 1));
    if (from == to) return;
    EngineData selected = entries.remove(from);
    entries.add(to, selected);
    if (!saveEngineCatalog(entries)) return;
    curIndex = to;
    markEngineCatalogChangedAndRefresh();
    table.getSelectionModel().setSelectionInterval(to, to);
    table.validate();
    table.updateUI();
  }

  static boolean updateStartupMode(
      JSONObject uiConfig, boolean autoloadDefault, boolean autoloadLast, boolean autoloadEmpty) {
    boolean oldDefault = uiConfig.optBoolean("autoload-default", false);
    boolean oldLast = uiConfig.optBoolean("autoload-last", false);
    boolean oldEmpty = uiConfig.optBoolean("autoload-empty", false);
    boolean newDefault = autoloadDefault;
    boolean newLast = !newDefault && autoloadLast;
    boolean newEmpty = !newDefault && !newLast && autoloadEmpty;

    uiConfig.put("autoload-default", newDefault);
    uiConfig.put("autoload-last", newLast);
    uiConfig.put("autoload-empty", newEmpty);
    return oldDefault != newDefault || oldLast != newLast || oldEmpty != newEmpty;
  }

  private boolean checkSave() {
    if (this.curIndex < 0) return true;
    EngineData engDt = EngineThreadPolicy.findSavedEntry(selectedEntryId);
    if (engDt == null && !selectedEntryId.isBlank()) {
      showThreadPolicySaveFailure(new IOException(EngineThreadPolicy.message("targetDeleted")));
      return false;
    }
    if (!isCurrentEngineConfigDirty(engDt)) return true;
    Object[] options = {
      this.resourceBundle.getString("MoreEngines.saveHint"),
      this.resourceBundle.getString("MoreEngines.saveHint2")
    };
    int ret =
        JOptionPane.showOptionDialog(
            this,
            this.resourceBundle.getString("MoreEngines.saveHint3"),
            this.resourceBundle.getString("MoreEngines.saveHint4"),
            0,
            3,
            null,
            options,
            options[0]);
    return ret != 0 || saveCurrentEngineConfig();
  }

  private boolean isCurrentEngineConfigDirty(EngineData saved) {
    if (saved == null) {
      return !this.command.getText().trim().isEmpty()
          || !this.txtName.getText().isEmpty()
          || !currentInitialCommand().isEmpty()
          || this.preload.isSelected()
          || !this.txtWidth.getText().equals("19")
          || !this.txtHeight.getText().equals("19")
          || !this.txtKomi.getText().equals("7.5")
          || this.chkDefault.isSelected()
          || this.chkRemoteEngine.isSelected()
          || selectedThreadSource != EngineThreadPolicy.Source.CFG;
    }
    if (selectedThreadSource != EngineThreadPolicy.source(saved)) return true;
    if (!this.command.getText().trim().equals(saved.commands)) return true;
    if (!this.txtName.getText().equals(saved.name)) return true;
    if (!currentInitialCommand().equals(saved.initialCommand)) return true;
    if (this.preload.isSelected() != saved.preload) return true;
    if (!this.txtWidth.getText().equals(String.valueOf(saved.width))) return true;
    if (!this.txtHeight.getText().equals(String.valueOf(saved.height))) return true;
    if (!this.txtKomi.getText().equals(String.valueOf(saved.komi))) return true;
    if (this.chkDefault.isSelected() != saved.isDefault) return true;
    if (this.chkRemoteEngine.isSelected() != saved.useJavaSSH) return true;
    if (!this.chkRemoteEngine.isSelected()) return false;
    if (!this.txtIP.getText().equals(saved.ip)) return true;
    if (!this.txtPort.getText().equals(saved.port)) return true;
    if (!this.txtUserName.getText().equals(saved.userName)) return true;
    if (this.rdoKeyGen.isSelected() != saved.useKeyGen) return true;
    if (this.rdoKeyGen.isSelected()) return !this.keyGenPath.equals(saved.keyGenPath);
    return !Utils.doEncrypt(new String(this.txtPassword.getPassword())).equals(saved.password);
  }

  private String currentInitialCommand() {
    return this.txtInitialCommand
            .getText()
            .equals(resourceBundle.getString("MoreEngines.initialCommandHint"))
        ? ""
        : this.txtInitialCommand.getText();
  }

  private void benchmarkSelectedEngine() {
    EngineData saved = EngineThreadPolicy.findSavedEntry(selectedEntryId);
    if (saved == null) {
      showThreadPolicySaveFailure(new IOException(EngineThreadPolicy.message("targetDeleted")));
      return;
    }
    if (isCurrentEngineConfigDirty(saved)) {
      Object[] options = {
        resourceBundle.getString("EngineThreadPolicy.saveAndBenchmark"),
        resourceBundle.getString("MoreEngines.cancel")
      };
      int selection =
          JOptionPane.showOptionDialog(
              this,
              resourceBundle.getString("EngineThreadPolicy.benchmarkSavePrompt"),
              EngineThreadPolicy.message("settings"),
              JOptionPane.DEFAULT_OPTION,
              JOptionPane.QUESTION_MESSAGE,
              null,
              options,
              options[0]);
      if (selection != 0 || !saveCurrentEngineConfig()) return;
    }
    KataGoAutoSetupDialog.openForSavedEntry(engjf, selectedEntryId);
    updateThreadPolicyDetails();
  }

  private void selectThreadPolicy(EngineThreadPolicy.Source source) {
    if (loadingThreadPolicy) return;
    selectedThreadSource = source;
    EngineData saved = EngineThreadPolicy.findSavedEntry(selectedEntryId);
    threadPolicySourceChanged = saved == null || source != EngineThreadPolicy.source(saved);
    updateThreadPolicyDetails();
  }

  private void updateThreadPolicyDetails() {
    threadPolicyStatus.setText("");
    threadPolicyStatus.setToolTipText(null);
    threadPolicyBenchmark.setToolTipText(null);
    benchmarkSelectedEngine.setToolTipText(null);
    boolean remote =
        curIndex >= 0
            && EngineThreadPolicy.isRemoteManaged(command.getText(), chkRemoteEngine.isSelected());
    EngineData saved = EngineThreadPolicy.findSavedEntry(selectedEntryId);
    boolean local =
        curIndex >= 0
            && !remote
            && EngineThreadPolicy.isLocalKataGoCommand(
                command.getText(), chkRemoteEngine.isSelected());
    boolean unknown =
        curIndex >= 0 && isUnknownThreadTarget(command.getText(), remote, local);
    threadPolicyPanel.setVisible(remote || local || unknown);
    remoteManagedThreads.setVisible(remote);
    threadPolicyChoices.setVisible(local);
    threadPolicyCfg.setVisible(local);
    threadPolicyBenchmark.setVisible(local);
    benchmarkSelectedEngine.setVisible(local && saved != null);
    threadPolicyStatus.setVisible(local || unknown);
    if (!local) {
      if (unknown) {
        threadPolicyStatus.setText(EngineThreadPolicy.message("unknownBenchmarkTarget"));
      }
      layoutThreadPolicyDetails();
      return;
    }
    int recommendation = EngineThreadPolicy.recommendedThreads(saved);
    threadPolicyBenchmark.setText(
        recommendation > 0
            ? String.format(
                resourceBundle.getString("EngineThreadPolicy.benchmarkWithThreads"), recommendation)
            : resourceBundle.getString("EngineThreadPolicy.benchmarkNotDetected"));
    benchmarkSelectedEngine.setText(
        resourceBundle.getString(
            recommendation > 0
                ? "EngineThreadPolicy.rerunBenchmark"
                : "EngineThreadPolicy.startBenchmark"));
    AccessibilitySupport.button(
        benchmarkSelectedEngine,
        benchmarkSelectedEngine.getText(),
        benchmarkSelectedEngine.getText());
    benchmarkSelectedEngine.setEnabled(saved != null);
    loadingThreadPolicy = true;
    try {
      threadPolicyCfg.setSelected(selectedThreadSource == EngineThreadPolicy.Source.CFG);
      threadPolicyBenchmark.setSelected(
          selectedThreadSource == EngineThreadPolicy.Source.BENCHMARK);
      threadPolicyCfg.setEnabled(true);
      threadPolicyBenchmark.setEnabled(
          recommendation > 0 || selectedThreadSource == EngineThreadPolicy.Source.BENCHMARK);
    } finally {
      loadingThreadPolicy = false;
    }
    ArrayList<String> statuses = new ArrayList<>();
    if (selectedThreadSource == EngineThreadPolicy.Source.BENCHMARK && recommendation <= 0) {
      statuses.add(EngineThreadPolicy.message("invalidRecommendation"));
    } else {
      statuses.add(
          resourceBundle.getString(
              selectedThreadSource == EngineThreadPolicy.Source.CFG
                  ? "EngineThreadPolicy.cfgDescription"
                  : "EngineThreadPolicy.benchmarkDescription"));
    }
    if (saved != null) {
      String environment = EngineThreadPolicy.environmentStatus(saved);
      if (!environment.isBlank()) statuses.add(environment);
      if (saved.threadPolicy != null
          && saved.threadPolicy.optBoolean("legacyOverrideStopped", false)) {
        statuses.add(EngineThreadPolicy.message("legacyOverrideStopped"));
      }
      if (hasExplicitThreadOverride(command.getText())) {
        statuses.add(EngineThreadPolicy.message("explicitOverride"));
      }
      if (isThreadPolicyPending(saved)) {
        statuses.add(EngineThreadPolicy.message("pendingReload"));
      }
    }
    String statusText = String.join(" · ", statuses);
    threadPolicyStatus.setText(statusText);
    threadPolicyStatus.setToolTipText(statusText);
    layoutThreadPolicyDetails();
  }

  private void layoutThreadPolicyDetails() {
    if (engineActionsPanel == null) return;
    int width = 880;
    int sectionHeight = 0;
    if (threadPolicyPanel.isVisible()) {
      sectionHeight = Math.max(28, threadPolicyControls.getPreferredSize().height);
      if (remoteManagedThreads.isVisible()) {
        sectionHeight += 6 + remoteManagedThreads.getPreferredSize().height;
      }
      if (threadPolicyStatus.isVisible()) {
        threadPolicyStatus.setSize(width, Short.MAX_VALUE);
        sectionHeight += 6 + threadPolicyStatus.getPreferredSize().height;
      }
      sectionHeight += 12;
    }
    threadPolicyPanel.setBounds(5, 302, width, sectionHeight);
    engineActionsPanel.setBounds(0, 306 + sectionHeight, 900, 116);
    int tableTop = engineActionsPanel.getY() + engineActionsPanel.getHeight();
    selectpanel.setSize(900, tableTop);
    tablepanel.setBounds(0, tableTop, 885, 785 - tableTop);
    threadPolicyPanel.revalidate();
    selectpanel.revalidate();
    repaint();
  }

  private boolean isUnknownThreadTarget(String rawCommand, boolean remote, boolean local) {
    if (remote || local || rawCommand == null || rawCommand.isBlank()) return false;
    String decoded = rawCommand == null ? "" : rawCommand.trim();
    if (decoded.startsWith("encryption||")) decoded = Utils.doDecrypt2(decoded.substring(12));
    java.util.List<String> tokens = Utils.splitCommand(decoded);
    return decoded.isBlank()
        || tokens == null
        || tokens.isEmpty()
        || tokens.get(0).contains("://")
        || decoded.toLowerCase(java.util.Locale.ROOT).contains("katago");
  }

  private boolean hasExplicitThreadOverride(String rawCommand) {
    String decoded = rawCommand == null ? "" : rawCommand;
    if (decoded.startsWith("encryption||")) {
      decoded = Utils.doDecrypt2(decoded.substring(12));
    }
    return KataGoRuntimeHelper.hasEffectiveNumSearchThreadsOverride(Utils.splitCommand(decoded));
  }

  private boolean isThreadPolicyPending(EngineData entry) {
    if (Lizzie.engineManager == null || Lizzie.engineManager.engineList == null) return false;
    for (Leelaz engine : Lizzie.engineManager.engineList) {
      if (entry.id.equals(engine.savedEntryId) && engine.isThreadPolicyPending(entry)) return true;
    }
    return false;
  }

  private void setEnable(boolean isEnable) {
    if (isEnable) {
      this.txtName.setEnabled(true);
      txtInitialCommand.setEnabled(true);
      this.command.setEnabled(true);
      command.setBackground(AppleStyleSupport.validFieldBackground());
      this.preload.setEnabled(true);
      this.txtWidth.setEnabled(true);
      this.txtHeight.setEnabled(true);
      this.txtKomi.setEnabled(true);
      this.chkDefault.setEnabled(true);
      this.chkRemoteEngine.setEnabled(true);
      if (this.chkRemoteEngine.isSelected()) {
        this.txtIP.setEnabled(true);
        this.txtPort.setEnabled(true);
        this.rdoUsePassword.setEnabled(true);
        this.rdoKeyGen.setEnabled(true);
        if (this.rdoUsePassword.isSelected()) this.txtPassword.setEnabled(true);
        if (this.rdoKeyGen.isSelected()) this.scanKeygen.setEnabled(true);
      }
      this.delete.setEnabled(true);
      this.save.setEnabled(true);
      this.cancel.setEnabled(true);
      this.scan.setEnabled(true);
      this.moveUp.setEnabled(true);
      this.moveUp5.setEnabled(true);
      this.moveFirst.setEnabled(true);
      this.moveLast.setEnabled(true);
      this.moveDown.setEnabled(true);
      this.moveDown5.setEnabled(true);
      this.gtpConfig.setEnabled(true);
      updateThreadPolicyDetails();
    } else {
      this.txtName.setEnabled(false);
      txtInitialCommand.setEnabled(false);
      this.command.setEnabled(false);
      command.setBackground(getBackground());
      this.preload.setEnabled(false);
      this.txtWidth.setEnabled(false);
      this.txtHeight.setEnabled(false);
      this.txtKomi.setEnabled(false);
      this.chkRemoteEngine.setEnabled(false);
      this.txtIP.setEnabled(false);
      this.txtPort.setEnabled(false);
      this.txtUserName.setEnabled(false);
      this.txtPassword.setEnabled(false);
      this.moveUp.setEnabled(false);
      this.moveDown.setEnabled(false);
      this.moveUp5.setEnabled(false);
      this.moveDown5.setEnabled(false);
      this.chkDefault.setEnabled(false);
      this.delete.setEnabled(false);
      this.moveFirst.setEnabled(false);
      this.moveLast.setEnabled(false);
      this.scan.setEnabled(false);
      this.cancel.setEnabled(false);
      this.rdoUsePassword.setEnabled(false);
      this.rdoKeyGen.setEnabled(false);
      this.scanKeygen.setEnabled(false);
      this.gtpConfig.setEnabled(false);
      this.benchmarkSelectedEngine.setEnabled(false);
      threadPolicyCfg.setEnabled(false);
      threadPolicyBenchmark.setEnabled(false);
      threadPolicyCfg.setVisible(false);
      threadPolicyBenchmark.setVisible(false);
      this.benchmarkSelectedEngine.setVisible(false);
      threadPolicyStatus.setVisible(false);
      remoteManagedThreads.setVisible(false);
    }
  }

  private void handleTableClick(int row) {
    ArrayList<EngineData> engineDatas = Utils.getEngineData();
    if (row < 0) return;
    loadingThreadPolicy = true;
    if (row < engineDatas.size()) {
      EngineData engineData = engineDatas.get(row);
      selectedEntryId = engineData.id;
      selectedThreadSource = EngineThreadPolicy.source(engineData);
      this.command.setText(engineData.commands);
      this.engineName.setText(
          String.valueOf(this.resourceBundle.getString("MoreEngines.editEngine"))
              + (engineData.index + 1));
      this.txtName.setText(engineData.name);
      this.txtInitialCommand.setText(engineData.initialCommand);
      if (engineData.initialCommand.equals("")) {
        txtInitialCommand.setForeground(Color.GRAY);
        txtInitialCommand.setText(resourceBundle.getString("MoreEngines.initialCommandHint"));
      } else {
        txtInitialCommand.setForeground(Color.BLACK);
      }
      this.preload.setSelected(engineData.preload);
      this.txtWidth.setText((new StringBuilder(String.valueOf(engineData.width))).toString());
      this.txtHeight.setText((new StringBuilder(String.valueOf(engineData.height))).toString());
      this.chkDefault.setSelected(engineData.isDefault);
      this.txtKomi.setText((new StringBuilder(String.valueOf(engineData.komi))).toString());
      this.chkRemoteEngine.setSelected(engineData.useJavaSSH);
      if (engineData.useJavaSSH) {
        this.txtIP.setText(engineData.ip);
        this.txtPort.setText(engineData.port);
        this.rdoUsePassword.setSelected(!engineData.useKeyGen);
        this.rdoKeyGen.setSelected(engineData.useKeyGen);
        this.txtUserName.setText(engineData.userName);
        if (engineData.useKeyGen) {
          this.keyGenPath = engineData.keyGenPath;
          this.scanKeygen.setToolTipText(this.keyGenPath);
          this.rdoKeyGen.setToolTipText(this.keyGenPath);
        } else {
          this.txtPassword.setText(Utils.doDecrypt(engineData.password));
        }
      } else {
        this.txtIP.setText("");
        this.txtPort.setText("");
        this.rdoUsePassword.setSelected(false);
        this.rdoKeyGen.setSelected(false);
        this.keyGenPath = "";
        this.txtUserName.setText("");
        this.txtPassword.setText("");
        this.scanKeygen.setToolTipText("");
        this.scanKeygen.setToolTipText("");
      }
    } else {
      selectedEntryId = "";
      selectedThreadSource = EngineThreadPolicy.Source.CFG;
      this.command.setText("");
      this.txtName.setText("");
      this.txtInitialCommand.setText("");
      txtInitialCommand.setForeground(Color.GRAY);
      txtInitialCommand.setText(resourceBundle.getString("MoreEngines.initialCommandHint"));
      this.engineName.setText(
          String.valueOf(this.resourceBundle.getString("MoreEngines.editEngine")) + (row + 1));
      this.preload.setSelected(false);
      this.txtWidth.setText("19");
      this.txtHeight.setText("19");
      this.chkDefault.setSelected(false);
      this.txtKomi.setText("7.5");
      this.chkRemoteEngine.setSelected(false);
      this.txtIP.setText("");
      this.txtPort.setText("");
      this.rdoUsePassword.setSelected(false);
      this.rdoKeyGen.setSelected(false);
      this.keyGenPath = "";
      this.txtUserName.setText("");
      this.txtPassword.setText("");
      this.scanKeygen.setToolTipText("");
      this.scanKeygen.setToolTipText("");
    }
    if (this.chkRemoteEngine.isSelected()) {
      this.txtIP.setEnabled(true);
      this.txtPort.setEnabled(true);
      this.rdoUsePassword.setEnabled(true);
      this.rdoKeyGen.setEnabled(true);
      this.txtUserName.setEnabled(true);
      if (this.rdoUsePassword.isSelected()) this.txtPassword.setEnabled(true);
      if (this.rdoKeyGen.isSelected()) this.scanKeygen.setEnabled(true);
    } else {
      this.txtIP.setEnabled(false);
      this.txtPort.setEnabled(false);
      this.rdoUsePassword.setEnabled(false);
      this.rdoKeyGen.setEnabled(false);
      this.txtUserName.setEnabled(false);
      this.txtPassword.setEnabled(false);
      this.scanKeygen.setEnabled(false);
    }
    this.curIndex = row;
    threadPolicySourceChanged = false;
    loadingThreadPolicy = false;
    setEnable(true);
    updateThreadPolicyDetails();
    table.validate();
    table.updateUI();
  }

  private boolean saveCurrentEngineConfig() {
    ArrayList<EngineData> engineData = Utils.getEngineData();
    int targetIndex = findEntryIndex(engineData, selectedEntryId);
    if (!selectedEntryId.isBlank() && targetIndex < 0) {
      showThreadPolicySaveFailure(new IOException(EngineThreadPolicy.message("targetDeleted")));
      return false;
    }
    EngineData latest = targetIndex >= 0 ? engineData.get(targetIndex) : null;
    if (latest == null && !isCurrentEngineConfigDirty(null)) return true;
    EngineData engineDt = new EngineData();
    String editedCommand = this.command.getText().trim();
    if (latest != null) {
      copyGtpConfigurationForUnchangedCommand(engineDt, latest, editedCommand);
      engineDt.id = latest.id;
      engineDt.threadPolicy =
          latest.threadPolicy == null
              ? new JSONObject().put("source", "CFG").put("sourceRevision", 0L)
              : new JSONObject(latest.threadPolicy.toString());
    }
    if (engineDt.threadPolicy == null) {
      engineDt.threadPolicy = new JSONObject().put("source", "CFG").put("sourceRevision", 0L);
    }
    if (threadPolicySourceChanged) {
      if (selectedThreadSource == EngineThreadPolicy.Source.BENCHMARK
          && EngineThreadPolicy.recommendedThreads(latest) <= 0) {
        showThreadPolicySaveFailure(
            new IOException(EngineThreadPolicy.message("invalidRecommendation")));
        return false;
      }
      engineDt.threadPolicy.put("source", selectedThreadSource.name());
      engineDt.threadPolicy.put(
          "sourceRevision", engineDt.threadPolicy.optLong("sourceRevision", 0L) + 1L);
    }
    engineDt.index = targetIndex >= 0 ? targetIndex : engineData.size();
    engineDt.commands = editedCommand;
    engineDt.name = this.txtName.getText();
    engineDt.initialCommand = currentInitialCommand();
    engineDt.preload = this.preload.isSelected();
    engineDt.width = Utils.parseTextToInt(this.txtWidth, 19);
    engineDt.height = Utils.parseTextToInt(this.txtHeight, 19);
    engineDt.isDefault = this.chkDefault.isSelected();
    engineDt.komi = Utils.parseTextToFloat(this.txtKomi, Float.valueOf(7.5F)).floatValue();
    engineDt.useJavaSSH = this.chkRemoteEngine.isSelected();
    if (engineDt.useJavaSSH) {
      engineDt.ip = this.txtIP.getText();
      engineDt.port = this.txtPort.getText();
      engineDt.useKeyGen = this.rdoKeyGen.isSelected();
      engineDt.userName = this.txtUserName.getText();
      if (engineDt.useKeyGen) engineDt.keyGenPath = this.keyGenPath;
      else engineDt.password = Utils.doEncrypt(new String(this.txtPassword.getPassword()));
    }
    if (engineDt.isDefault) {
      for (EngineData engine : engineData) engine.isDefault = false;
    }
    if (targetIndex >= 0) engineData.set(targetIndex, engineDt);
    else engineData.add(engineDt);
    try {
      Utils.saveEngineSettings(engineData);
    } catch (java.io.UncheckedIOException failure) {
      showThreadPolicySaveFailure(failure.getCause());
      return false;
    }
    ArrayList<EngineData> savedEntries = Utils.getEngineData();
    if (engineDt.id.isBlank() && engineDt.index < savedEntries.size()) {
      engineDt = savedEntries.get(engineDt.index);
    }
    selectedEntryId = engineDt.id;
    curIndex = findEntryIndex(savedEntries, selectedEntryId);
    selectedThreadSource = EngineThreadPolicy.source(engineDt);
    threadPolicySourceChanged = false;
    needUpdateEngine = true;
    refreshEngineCatalogIfReady();
    applyThreadPolicyOffEdt(selectedEntryId);
    updateThreadPolicyDetails();
    return true;
  }

  private static int findEntryIndex(ArrayList<EngineData> entries, String id) {
    if (id == null || id.isBlank()) return -1;
    for (int i = 0; i < entries.size(); i++) {
      if (id.equals(entries.get(i).id)) return i;
    }
    return -1;
  }

  private void applyThreadPolicyOffEdt(String entryId) {
    if (entryId == null || entryId.isBlank()) return;
    new SwingWorker<Void, Void>() {
      @Override
      protected Void doInBackground() {
        EngineData saved = EngineThreadPolicy.findSavedEntry(entryId);
        if (saved == null
            || Lizzie.engineManager == null
            || Lizzie.engineManager.engineList == null) {
          return null;
        }
        for (Leelaz engine : Lizzie.engineManager.engineList) {
          if (entryId.equals(engine.savedEntryId)) engine.applySavedThreadPolicy(saved);
        }
        return null;
      }

      @Override
      protected void done() {
        updateThreadPolicyDetails();
        scheduleThreadPolicyStatusRefresh(entryId);
      }
    }.execute();
  }

  private void scheduleThreadPolicyStatusRefresh(String entryId) {
    if (threadPolicyRefreshTimer != null) threadPolicyRefreshTimer.stop();
    threadPolicyRefreshTimer =
        new Timer(
            500,
            event -> {
              EngineData saved = EngineThreadPolicy.findSavedEntry(entryId);
              updateThreadPolicyDetails();
              if (saved == null || !isThreadPolicyPending(saved) || !isShowing()) {
                ((Timer) event.getSource()).stop();
              }
            });
    threadPolicyRefreshTimer.start();
  }

  private void showThreadPolicySaveFailure(Throwable failure) {
    String detail = failure == null || failure.getMessage() == null ? "" : failure.getMessage();
    JFontTextArea message = new JFontTextArea(8, 60);
    message.setText(String.format(EngineThreadPolicy.message("saveFailed"), detail));
    message.setEditable(false);
    message.setLineWrap(true);
    message.setWrapStyleWord(true);
    message.setCaretPosition(0);
    JOptionPane.showMessageDialog(
        engjf,
        new JScrollPane(message),
        EngineThreadPolicy.message("settings"),
        JOptionPane.ERROR_MESSAGE);
  }

  static void copyGtpConfigurationForUnchangedCommand(
      EngineData target, EngineData previous, String editedCommand) {
    if (target != null
        && previous != null
        && editedCommand != null
        && editedCommand.equals(previous.commands)) {
      target.copyGtpConfigurationFrom(previous);
    }
  }

  private void configureSelectedEngine() {
    if (curIndex < 0) {
      return;
    }
    if (!saveCurrentEngineConfig()) return;
    ArrayList<EngineData> engines = Utils.getEngineData();
    if (curIndex >= engines.size()) {
      return;
    }
    EngineData selected = engines.get(curIndex);
    if (selected.useJavaSSH) {
      showGtpConfigurationMessage(
          resourceBundle.getString("GtpEngineConfig.remoteUnsupported"),
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    int selectedIndex = curIndex;
    String selectedCommand = selected.commands;
    JSONObject savedProfile =
        selected.gtpConfigurationProfile == null
            ? null
            : new JSONObject(selected.gtpConfigurationProfile.toString());
    setGtpConfigurationBusy(resourceBundle.getString("GtpEngineConfig.detecting"));
    new SwingWorker<GtpConfigurationProbe.Inspection, Void>() {
      @Override
      protected GtpConfigurationProbe.Inspection doInBackground() throws Exception {
        return new GtpConfigurationProbe().inspect(selectedCommand);
      }

      @Override
      protected void done() {
        restoreGtpConfigurationButton();
        try {
          GtpConfigurationProbe.Inspection inspection = get();
          if (!inspection.supported()) {
            showGtpConfigurationMessage(
                resourceBundle.getString("GtpEngineConfig.unsupported"),
                JOptionPane.INFORMATION_MESSAGE);
            return;
          }
          Optional<JSONObject> profile =
              GtpEngineConfigDialog.showDialog(
                  MoreEngines.this, inspection.schema(), savedProfile);
          profile.ifPresent(
              value ->
                  applyGtpConfiguration(
                      selectedIndex, selectedCommand, inspection.schema(), value));
        } catch (InterruptedException error) {
          Thread.currentThread().interrupt();
          showGtpConfigurationError(error);
        } catch (ExecutionException error) {
          showGtpConfigurationError(error.getCause());
        }
      }
    }.execute();
  }

  private void applyGtpConfiguration(
      int selectedIndex,
      String selectedCommand,
      GtpConfigurationSchema schema,
      JSONObject requestedProfile) {
    setGtpConfigurationBusy(resourceBundle.getString("GtpEngineConfig.applying"));
    new SwingWorker<GtpConfigurationProbe.ApplyResult, Void>() {
      @Override
      protected GtpConfigurationProbe.ApplyResult doInBackground() throws Exception {
        return new GtpConfigurationProbe().applyProfile(selectedCommand, requestedProfile);
      }

      @Override
      protected void done() {
        try {
          GtpConfigurationProbe.ApplyResult result = get();
          persistGtpConfiguration(
              selectedIndex, selectedCommand, schema.protocol(), result.profile());
          applyProfileToRunningEngine(selectedIndex, result.profile());
        } catch (InterruptedException error) {
          Thread.currentThread().interrupt();
          restoreGtpConfigurationButton();
          showGtpConfigurationError(error);
        } catch (ExecutionException | RuntimeException error) {
          restoreGtpConfigurationButton();
          Throwable cause = error instanceof ExecutionException ? error.getCause() : error;
          if (cause instanceof java.io.UncheckedIOException) {
            showThreadPolicySaveFailure(cause.getCause());
          } else {
            showGtpConfigurationError(cause);
          }
        }
      }
    }.execute();
  }

  private void persistGtpConfiguration(
      int selectedIndex, String expectedCommand, String protocol, JSONObject profile) {
    ArrayList<EngineData> engines = Utils.getEngineData();
    if (selectedIndex < 0 || selectedIndex >= engines.size()) {
      throw new IllegalStateException(resourceBundle.getString("GtpEngineConfig.engineChanged"));
    }
    EngineData engine = engines.get(selectedIndex);
    if (!expectedCommand.equals(engine.commands)) {
      throw new IllegalStateException(resourceBundle.getString("GtpEngineConfig.engineChanged"));
    }
    engine.gtpConfigurationProtocol = protocol;
    engine.gtpConfigurationProfile = new JSONObject(profile.toString());
    Utils.saveEngineSettings(engines);
    markEngineCatalogChangedAndRefresh();
  }

  private void applyProfileToRunningEngine(int selectedIndex, JSONObject profile) {
    if (Lizzie.engineManager == null
        || selectedIndex < 0
        || selectedIndex >= Lizzie.engineManager.engineList.size()) {
      showGtpConfigurationSavedStatus();
      return;
    }
    Leelaz engine = Lizzie.engineManager.engineList.get(selectedIndex);
    engine.gtpConfigurationProtocol = GtpConfigurationProbe.ZENGTP_PROTOCOL;
    engine.gtpConfigurationProfile = new JSONObject(profile.toString());
    if (!engine.started || !engine.supportsGtpConfiguration()) {
      showGtpConfigurationSavedStatus();
      return;
    }
    engine.applyGtpConfigurationProfile(
        profile,
        response -> SwingUtilities.invokeLater(this::showGtpConfigurationSavedStatus),
        error ->
            SwingUtilities.invokeLater(
                () -> {
                  restoreGtpConfigurationButton();
                  showGtpConfigurationMessage(
                      resourceBundle.getString("GtpEngineConfig.savedForNextStart"),
                      JOptionPane.WARNING_MESSAGE);
                }));
  }

  private void setGtpConfigurationBusy(String text) {
    gtpConfig.setText(text);
    gtpConfig.setEnabled(false);
    table.setEnabled(false);
  }

  private void showGtpConfigurationSavedStatus() {
    gtpConfig.setText(resourceBundle.getString("GtpEngineConfig.saved"));
    gtpConfig.setEnabled(false);
    table.setEnabled(true);
    Timer timer = new Timer(1600, event -> restoreGtpConfigurationButton());
    timer.setRepeats(false);
    timer.start();
  }

  private void restoreGtpConfigurationButton() {
    gtpConfig.setText(resourceBundle.getString("MoreEngines.gtpConfig"));
    gtpConfig.setEnabled(curIndex >= 0);
    table.setEnabled(true);
  }

  private void showGtpConfigurationError(Throwable error) {
    String detail = error == null || error.getMessage() == null ? "" : error.getMessage();
    showGtpConfigurationMessage(
        String.format(resourceBundle.getString("GtpEngineConfig.loadFailed"), detail),
        JOptionPane.ERROR_MESSAGE);
  }

  private void showGtpConfigurationMessage(String message, int messageType) {
    JOptionPane.showMessageDialog(
        engjf, message, resourceBundle.getString("GtpEngineConfig.title"), messageType);
  }

  private static void markEngineCatalogChangedAndRefresh() {
    needUpdateEngine = true;
    refreshEngineCatalogIfReady();
  }

  private static void refreshEngineCatalogIfReady() {
    if (!needUpdateEngine || Lizzie.engineManager == null) {
      return;
    }
    try {
      Lizzie.engineManager.refreshEngineCatalog();
      needUpdateEngine = false;
    } catch (JSONException | IOException e) {
      e.printStackTrace();
    }
  }

  public AbstractTableModel getTableModel() {
    return new AbstractTableModel() {
      public int getColumnCount() {
        return 9;
      }

      public int getRowCount() {
        return 500;
      }

      public String getColumnName(int column) {
        if (column == 0) return resourceBundle.getString("MoreEngines.column0");
        if (column == 1) return resourceBundle.getString("MoreEngines.column1");
        if (column == 2) return resourceBundle.getString("MoreEngines.column2");
        if (column == 3) return resourceBundle.getString("MoreEngines.column3");
        if (column == 4) return resourceBundle.getString("MoreEngines.column4");
        if (column == 5) return resourceBundle.getString("MoreEngines.column5");
        if (column == 6) return resourceBundle.getString("MoreEngines.column6");
        if (column == 7) return resourceBundle.getString("MoreEngines.column7");
        if (column == 8) return resourceBundle.getString("MoreEngines.column8");
        return "";
      }

      public Object getValueAt(int row, int col) {
        ArrayList<EngineData> EngineDatas = Utils.getEngineData();
        if (row > EngineDatas.size() - 1) {
          if (col == 0) return Integer.valueOf(row + 1);
          return "";
        }
        EngineData data = EngineDatas.get(row);
        switch (col) {
          case 0:
            return Integer.valueOf(row + 1);
          case 1:
            return data.name;
          case 2:
            return data.commands;
          case 3:
            if (data.preload) return resourceBundle.getString("MoreEngines.yes");
            return resourceBundle.getString("MoreEngines.no");
          case 4:
            return Integer.valueOf(data.width);
          case 5:
            return Integer.valueOf(data.height);
          case 6:
            return Float.valueOf(data.komi);
          case 7:
            if (data.isDefault) return resourceBundle.getString("MoreEngines.yes");
            return resourceBundle.getString("MoreEngines.no");
          case 8:
            if (data.useJavaSSH) return resourceBundle.getString("MoreEngines.yes");
            return resourceBundle.getString("MoreEngines.no");
        }
        return "";
      }
    };
  }

  public static JDialog createDialog() {
    return createDialog(null);
  }

  public static JDialog createDialog(String entryId) {
    engjf = new JDialog();
    needUpdateEngine = false;
    engjf.setTitle(Lizzie.resourceBundle.getString("MoreEngines.title"));
    engjf.setModal(true);
    engjf.addWindowListener(
        new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            MoreEngines.engjf.setVisible(false);
            if (needUpdateEngine)
              try {
                Lizzie.engineManager.updateEngines();
              } catch (JSONException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
              } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
              }
          }
        });
    MoreEngines newContentPane = new MoreEngines();
    newContentPane.setOpaque(true);
    engjf.setContentPane(newContentPane);
    if (entryId != null && !entryId.isBlank()) {
      int row = findEntryIndex(Utils.getEngineData(), entryId);
      if (row >= 0) {
        table.getSelectionModel().setSelectionInterval(row, row);
        newContentPane.handleTableClick(row);
      } else {
        JOptionPane.showMessageDialog(
            engjf,
            EngineThreadPolicy.message("targetDeleted"),
            EngineThreadPolicy.message("settings"),
            JOptionPane.WARNING_MESSAGE);
      }
    }
    Lizzie.setFrameSize(engjf, 891, 842);
    engjf.setResizable(false);
    try {
      engjf.setIconImage(ImageIO.read(MoreEngines.class.getResourceAsStream("/assets/logo.png")));
    } catch (IOException e) {
      e.printStackTrace();
    }
    engjf.setAlwaysOnTop(Lizzie.frame.isAlwaysOnTop());
    engjf.setLocationRelativeTo(engjf.getOwner());
    LizzieFrame.constrainWindowToAvailableWorkArea(engjf);
    AccessibilitySupport.installEscapeToClose(engjf.getRootPane(), engjf);
    return engjf;
  }
}
