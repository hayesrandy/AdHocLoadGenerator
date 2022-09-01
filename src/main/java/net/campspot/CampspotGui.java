package net.campspot;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.opencsv.CSVWriter;
import net.campspot.components.DateLabelFormatter;
import net.campspot.components.ShowErrorCallback;
import net.campspot.models.Campsite;
import net.campspot.models.Park;
import okhttp3.*;
import org.jdatepicker.impl.JDatePanelImpl;
import org.jdatepicker.impl.JDatePickerImpl;
import org.jdatepicker.impl.UtilDateModel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CampspotGui extends JFrame {
  private final OkHttpClient client = new OkHttpClient.Builder()
      .connectTimeout(60, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .build();
  private final List<Park> parks; // list of all parks
  private final Map<String, Park> parksMap; // map of all parks, key is park Id, value is park
  private final Map<String, List<Campsite>> campsites; // list of all campsites
  // in case we need to convert from campsite type number to text
  // it is not used anywhere now because in the generated CSV file, we are using campsite type number
  private final Map<String, String> campsiteTypes = new HashMap<>();
  private JPanel mainPanel;
  private JButton runBtn;
  private JButton exitBtn;
  private JTable testsTable;
  private JTable dataTable;
  private JTable parkTable;
  private JRadioButton rvRadioButton;
  private JRadioButton lodgingRadioButton;
  private JButton generateButton;
  private JRadioButton tentRadioButton;
  private JRadioButton storageRadioButton;
  private JLabel iconLabel;
  private JButton viewDetailBtn;
  private JDatePickerImpl checkoutDatePicker;
  private JDatePickerImpl checkinDatePicker;
  private JLabel totalLabel;
  private JRadioButton allRadioButton;
  private JRadioButton localRadioButton;
  private JRadioButton remoteRadioButton;
  private Properties config;
  private List<Campsite> selectedCampsites; // list of selected campsites
  private ButtonGroup campsiteTypeButtonGroup;
  private ButtonGroup localRemoteButtonGroup;

  public CampspotGui(String title) {
    super(title);
    $$$setupUI$$$();

    initializeUi();
    loadConfiguration();

    // read parks from parks.csv file
    this.parks = Helper.readParks();
    parksMap = new HashMap<>();
    for (Park i : parks) {
      parksMap.put(i.id, i);
    }
    // prepare campsite data, so that we can quickly display them when a park and a campsite type is selected
    this.campsites = Helper.readCampsites(parksMap);

    // show parks from parks.csv file
    showParks();

    // show tests files from Tests folder
    showLocalTests();
    // show files from Data folder
    showData();
  }

  private void showLocalTests() {
    File testDir = new File("Tests");
    if (!testDir.exists()) testDir.mkdirs();
    File resultdir = new File("Results");
    if (!resultdir.exists()) resultdir.mkdirs();
    File dataDir = new File("Data");
    if (!dataDir.exists()) dataDir.mkdirs();
    //List of all jmx files
    String[] contents = testDir.list();
    testsTable.getTableHeader().setUI((null));
    DefaultTableModel model = (DefaultTableModel) testsTable.getModel();
    model.setRowCount(0);
    if (model.getColumnCount() == 0) {
      model.addColumn("Name");
    }
    for (String content : contents) {
      if (content.toLowerCase().endsWith(".jmx")) {
        model.addRow(new Object[]{content});
      }
    }
  }

  private void showData() {
    DefaultTableModel model = (DefaultTableModel) dataTable.getModel();
    File dataDir = new File("Data");
    String[] contents = dataDir.list();
    dataTable.getTableHeader().setUI((null));
    model.addColumn("Name");
    for (String content : contents) {
      model.addRow(new Object[]{content});
    }
  }

  public static void main(String[] args) {
    JFrame frame = new CampspotGui("Campspot Ad Hoc Load Generator");
    frame.setVisible(true);
  }

  private void showParks() {
    parkTable.getTableHeader().setUI((null));
    DefaultTableModel model = (DefaultTableModel) parkTable.getModel();
    model.addColumn("Id");
    model.addColumn("Name");
    if (this.parks != null) {
      for (Park val : parks)
        model.addRow(new Object[]{val.id, val.name});
    }
    TableColumnModel columnModel = parkTable.getColumnModel();
    columnModel.getColumn(0).setPreferredWidth(60);
    columnModel.getColumn(0).setMaxWidth(60);

    parkTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    ListSelectionModel selectionModel = parkTable.getSelectionModel();
    selectionModel.addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) { // if user is still manipulating the selection, do nothing
        return;
      }
      updateTotalLabel();
    });
  }

  private String getSelectedParkId() {
    return parkTable.getSelectedRow() > -1 ?
        parkTable.getValueAt(parkTable.getSelectedRow(), 0).toString() : null;
  }

  private long getDays() {
    Date outDate = (Date) checkoutDatePicker.getJDateInstantPanel().getModel().getValue();
    Date inDate = (Date) checkinDatePicker.getJDateInstantPanel().getModel().getValue();
    long day = 0;
    if (outDate != null && inDate != null && outDate.getTime() >= inDate.getTime()) {
      day = (outDate.getTime() - inDate.getTime()) / 86400000 + 1;
    }
    return day;
  }

  private void updateTotalLabel() {
    String parkId = getSelectedParkId();
    selectedCampsites = new ArrayList<>();
    long day = getDays();
    if (parkId != null && day > 0) {
      if (allRadioButton.isSelected()) {
        selectedCampsites = campsites.get(parkId);
      } else {
        String typeId = campsiteTypeButtonGroup.getSelection().getActionCommand();
        selectedCampsites = campsites.get(parkId).stream()
            .filter(p -> typeId.equals(p.type)).collect(Collectors.toList());
      }
    }
    totalLabel.setText("Total: " + day * selectedCampsites.size());
    generateButton.setEnabled(day * selectedCampsites.size() > 0);
    repaint();
  }

  /**
   * Load configuration file to config object.
   */
  private void loadConfiguration() {
    FileReader inputStream;
    try {
      inputStream = new FileReader("config.properties");
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
    this.config = new Properties();
    try {
      this.config.load(inputStream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void generateData() {
    String filename;
    if (testsTable.getSelectedRow() != -1) { // if user selected a jmx file, then use the filename with CSV extension
      String jmxFile = testsTable.getValueAt(testsTable.getSelectedRow(), 0).toString();
      if (jmxFile.contains(".jmx")) {
        filename = jmxFile.replaceAll(".jmx", ".csv");
      } else {
        filename = jmxFile + ".csv";
      }
    } else {
      filename = JOptionPane.showInputDialog("Please enter new filename or select a test:");
    }

    if (filename == null) return;
    long day = getDays();
    if (selectedCampsites.size() > 0 && day > 0) {
      // append csv extension if user has not provide the csv extension
      if (!filename.toLowerCase().endsWith(".csv")) filename += ".csv";
      try (
          Writer writer = Files.newBufferedWriter(Paths.get("Data", filename));
          CSVWriter csvWriter = new CSVWriter(writer,
              CSVWriter.DEFAULT_SEPARATOR,
              CSVWriter.NO_QUOTE_CHARACTER,
              CSVWriter.DEFAULT_ESCAPE_CHARACTER,
              CSVWriter.DEFAULT_LINE_END)
      ) {
        String[] headerRecord = {"email", "campsite id", "park id", "type", "check in day", "check in month", "checkin year",
            "checkout day", "checkout date", "checkout year"};
        csvWriter.writeNext(headerRecord);

        Date inDate = (Date) checkinDatePicker.getJDateInstantPanel().getModel().getValue();
        int emailIndex = 1; // start of index in email address
        int maxEmailIndex = 1000; // end of index in email address
        String emailTemplate = "LoadtestUser+%d@campspot.com";
        for (Campsite campsite : selectedCampsites) {
          for (int i = 0; i < day; i++) {
            Calendar inCalendar = Calendar.getInstance();
            inCalendar.setTime(inDate);
            inCalendar.add(Calendar.DATE, i);
            Calendar outCalendar = Calendar.getInstance();
            outCalendar.setTime(inDate);
            outCalendar.add(Calendar.DATE, i + 1);
            String[] row = new String[]{
                String.format(emailTemplate, ((emailIndex - 1) % maxEmailIndex) + 1), // email index is increased to 1000 and comeback to 1 again
                campsite.id, campsite.parkId, campsite.type,
                String.valueOf(inCalendar.get(Calendar.DAY_OF_MONTH)),
                String.valueOf(inCalendar.get(Calendar.MONTH) + 1),
                String.valueOf(inCalendar.get(Calendar.YEAR)),
                String.valueOf(outCalendar.get(Calendar.DAY_OF_MONTH)),
                String.valueOf(outCalendar.get(Calendar.MONTH) + 1),
                String.valueOf(outCalendar.get(Calendar.YEAR)),
            };
            csvWriter.writeNext(row);
            emailIndex++;
          }
        }
        System.out.println("Generated file: " + filename);

        refreshDataTable();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void initializeUi() {
    this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    this.setContentPane((mainPanel));

    testsTable.setDefaultEditor(Object.class, null);
    dataTable.setDefaultEditor(Object.class, null);
    exitBtn.addActionListener(e -> System.exit(0));
    exitBtn.setMnemonic(KeyEvent.VK_X);
    runBtn.addActionListener(e -> {
      if (testsTable.getSelectedRow() != -1) {
        if (localRadioButton.isSelected()) {
          runJmeterTest(testsTable.getValueAt(testsTable.getSelectedRow(), 0).toString());
        } else {
          runRemoteJmeterTest(testsTable.getValueAt(testsTable.getSelectedRow(), 0).toString());
        }
      }
    });
    generateButton.addActionListener(e -> generateData());
    generateButton.setEnabled(false);

    // when user selects item in workflow table, set name text field to selected value
    testsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    // set type ID, action listener for each radio button and add it to group
    ActionListener listener = e -> updateTotalLabel();
    campsiteTypeButtonGroup = new ButtonGroup();
    addRadioButtonToCampsiteGroup(campsiteTypeButtonGroup, allRadioButton, listener, "-1");
    addRadioButtonToCampsiteGroup(campsiteTypeButtonGroup, rvRadioButton, listener, "0");
    addRadioButtonToCampsiteGroup(campsiteTypeButtonGroup, lodgingRadioButton, listener, "1");
    addRadioButtonToCampsiteGroup(campsiteTypeButtonGroup, tentRadioButton, listener, "2");
    addRadioButtonToCampsiteGroup(campsiteTypeButtonGroup, storageRadioButton, listener, "3");

    ActionListener localRemoteListener = e -> updateTestsList();
    localRemoteButtonGroup = new ButtonGroup();
    addRadioButtonToLocalRemoteGroup(localRemoteButtonGroup, localRadioButton, localRemoteListener, "local");
    addRadioButtonToLocalRemoteGroup(localRemoteButtonGroup, remoteRadioButton, localRemoteListener, "remote");

    URL url = ClassLoader.getSystemResource("icon.png");
    Toolkit kit = Toolkit.getDefaultToolkit();
    Image icon = kit.createImage(url);
    this.setIconImage(icon);
    iconLabel.setIcon(new ImageIcon(Objects.requireNonNull(getClass().getClassLoader().getResource("icon.png"))));
    iconLabel.setText("");

    this.pack();
  }

  /**
   * Add radio button to Local Remote Group
   * @param group
   * @param radioButton
   * @param localRemoteListener
   * @param actionCommand
   */
  private void addRadioButtonToLocalRemoteGroup(ButtonGroup group, JRadioButton radioButton,
                                                ActionListener localRemoteListener, String actionCommand) {
    group.add(radioButton);
    radioButton.setActionCommand(actionCommand);
    radioButton.addActionListener(localRemoteListener);
  }

  private void updateTestsList() {
    if (localRadioButton.isSelected()) {
      showLocalTests();
    } else {
      requestData(testsTable, config.getProperty("campspotServer") + "/internal/ViewTestList.aspx?tool=JMeter&a=" +
          config.getProperty("accountId"));
    }
  }

  /**
   * Call the RunJMeterTest.aspx URL to run the tests, then open browser ViewTestStatus.aspx?cancel=no
   */
  private void runRemoteJmeterTest(String testName) {
    try {
      testName = URLEncoder.encode(testName, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException ex) {
      throw new RuntimeException(ex);
    }
    String url = config.get("campspotServer")
        + "/internal/RunJMeterTest.aspx?name=" + testName
        + "&accountID=" + config.getProperty("accountId");

    System.out.println("Requesting: " + url);
    Request request = new Request.Builder().url(url).build();
    Call call = client.newCall(request);
    call.enqueue(new ShowErrorCallback("Cannot run test") {
      public void onResponse(Call call, Response response)
          throws IOException {
        try (ResponseBody responseBody = response.body()) {
          if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
          // open browser for user to view status of the test
          String url = config.get("campspotServer")
              + "/internal/ViewTestStatus.aspx?cancel=no";
          Helper.openWebpage(new URL(url));
        } catch (MalformedURLException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  /**
   * Get data for the tests list.
   */
  private void requestData(JTable table, String url) {
    Request request = new Request.Builder().url(url).build();

    Call call = client.newCall(request);
    call.enqueue(new ShowErrorCallback("Cannot get data from " + url) {
      public void onResponse(Call call, Response response)
          throws IOException {
        try (ResponseBody responseBody = response.body()) {
          if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
          String text = responseBody.string();
          // only get the raw text part, ignore the HTML part
          int index = text.indexOf("<!DOCTYPE");
          if (index > -1) {
            text = text.substring(0, index - 1);
          }
          List<String> tokens = Arrays.asList(text.split(","));
          table.getTableHeader().setUI((null));
          DefaultTableModel model = (DefaultTableModel) table.getModel();
          model.setRowCount(0);
          if (model.getColumnCount() == 0) {
            model.addColumn("Name");
          }
          for (int i = 0; i < tokens.size() / 2; i++) {
            model.addRow(new Object[]{tokens.get(i * 2 + 1)});
          }
        }
      }
    });
  }

  /**
   * Run Jmeter test from local
   * @param jmxFile name of jmx file in Tests folder
   */
  private void runJmeterTest(String jmxFile) {
    // when somebody selects a test to run and data file to use, copy the datafile to "campsites.csv" before running the test
    if (dataTable.getSelectedRow() != -1 &&
        !dataTable.getValueAt(dataTable.getSelectedRow(), 0).toString().equals("campsites.csv")) {
      try {
        Files.copy(Paths.get("Data/" + dataTable.getValueAt(dataTable.getSelectedRow(), 0).toString()),
            Paths.get("Data/campsites.csv"), StandardCopyOption.REPLACE_EXISTING);
        refreshDataTable();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    String os = System.getProperty("os.name").toLowerCase();
    String command = config.getProperty("jmeter_home") + System.getProperty("file.separator") +
        "jmeter -n -t Tests" + System.getProperty("file.separator") + jmxFile +
        " -l Results" + System.getProperty("file.separator") + jmxFile.replaceAll(".jmx", ".jtl");

    // invoke the command by SwingUtilities so that the UI is not blocked when the new command runs
    SwingUtilities.invokeLater(() -> {
      try {
        Process process;
        System.out.println("Running command: " + command);
        ProcessBuilder processBuilder = new ProcessBuilder();
        if (os.contains("win")) {
          processBuilder.command("cmd.exe", "/c", command);
        } else {
          processBuilder.command("bash", "-c", command);
        }
        process = processBuilder.start();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
          System.out.println(line);
        }
        int exitVal = process.waitFor();
        if (exitVal == 0) {
          System.out.println("Success!");
        } else {
          System.out.println("Abnormal!");
        }
      } catch (IOException | InterruptedException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    });
  }

  private void refreshDataTable() {
    // refresh list of file in Data folder
    DefaultTableModel model = (DefaultTableModel) dataTable.getModel();
    model.setRowCount(0);
    File dataDir = new File("Data");
    String[] contents = dataDir.list();
    for (String content : contents) {
      System.out.println(content);
      model.addRow(new Object[]{content});
    }
    model.fireTableDataChanged();
    dataTable.repaint();
  }

  private void addRadioButtonToCampsiteGroup(ButtonGroup group, JRadioButton button, ActionListener listener, String actionCommand) {
    group.add(button);
    button.setActionCommand(actionCommand);
    button.addActionListener(listener);
    campsiteTypes.put(actionCommand, button.getText());
  }

  /**
   * Method generated by IntelliJ IDEA GUI Designer
   * >>> IMPORTANT!! <<<
   * DO NOT edit this method OR call it in your code!
   *
   * @noinspection ALL
   */
  private void $$$setupUI$$$() {
    createUIComponents();
    mainPanel = new JPanel();
    mainPanel.setLayout(new BorderLayout(0, 10));
    mainPanel.setMinimumSize(new Dimension(1000, 740));
    mainPanel.setPreferredSize(new Dimension(1000, 740));
    mainPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new BorderLayout(0, 0));
    mainPanel.add(panel1, BorderLayout.NORTH);
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridBagLayout());
    panel2.setPreferredSize(new Dimension(1398, 600));
    panel1.add(panel2, BorderLayout.CENTER);
    final JLabel label1 = new JLabel();
    label1.setIconTextGap(4);
    label1.setText("Tests:");
    GridBagConstraints gbc;
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.insets = new Insets(0, 0, 5, 0);
    panel2.add(label1, gbc);
    final JScrollPane scrollPane1 = new JScrollPane();
    scrollPane1.setVerticalScrollBarPolicy(22);
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new Insets(0, 2, 0, 2);
    panel2.add(scrollPane1, gbc);
    testsTable = new JTable();
    testsTable.setAutoscrolls(false);
    testsTable.setEditingColumn(-1);
    testsTable.setEditingRow(-1);
    testsTable.setEnabled(true);
    testsTable.setFocusable(false);
    scrollPane1.setViewportView(testsTable);
    final JLabel label2 = new JLabel();
    label2.setText("Data:");
    gbc = new GridBagConstraints();
    gbc.gridx = 2;
    gbc.gridy = 0;
    gbc.weightx = 0.25;
    gbc.anchor = GridBagConstraints.WEST;
    panel2.add(label2, gbc);
    final JScrollPane scrollPane2 = new JScrollPane();
    scrollPane2.setHorizontalScrollBarPolicy(32);
    gbc = new GridBagConstraints();
    gbc.gridx = 2;
    gbc.gridy = 1;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new Insets(0, 2, 0, 2);
    panel2.add(scrollPane2, gbc);
    dataTable = new JTable();
    dataTable.setAutoResizeMode(4);
    scrollPane2.setViewportView(dataTable);
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridLayoutManager(9, 1, new Insets(0, 0, 0, 0), -1, -1));
    gbc = new GridBagConstraints();
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.gridheight = 2;
    gbc.fill = GridBagConstraints.BOTH;
    panel2.add(panel3, gbc);
    panel3.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
    final JLabel label3 = new JLabel();
    label3.setText("Park:");
    panel3.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JScrollPane scrollPane3 = new JScrollPane();
    panel3.add(scrollPane3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    parkTable = new JTable();
    scrollPane3.setViewportView(parkTable);
    final JLabel label4 = new JLabel();
    label4.setText("Type:");
    panel3.add(label4, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel4 = new JPanel();
    panel4.setLayout(new BorderLayout(0, 0));
    panel3.add(panel4, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final JPanel panel5 = new JPanel();
    panel5.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
    panel4.add(panel5, BorderLayout.WEST);
    allRadioButton = new JRadioButton();
    allRadioButton.setSelected(true);
    allRadioButton.setText("All");
    panel5.add(allRadioButton);
    rvRadioButton = new JRadioButton();
    rvRadioButton.setText("RV");
    panel5.add(rvRadioButton);
    lodgingRadioButton = new JRadioButton();
    lodgingRadioButton.setText("Lodging");
    panel5.add(lodgingRadioButton);
    tentRadioButton = new JRadioButton();
    tentRadioButton.setText("Tent");
    panel5.add(tentRadioButton);
    storageRadioButton = new JRadioButton();
    storageRadioButton.setText("Storage");
    panel5.add(storageRadioButton);
    final JPanel panel6 = new JPanel();
    panel6.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
    panel4.add(panel6, BorderLayout.CENTER);
    totalLabel = new JLabel();
    totalLabel.setMinimumSize(new Dimension(80, 16));
    totalLabel.setText("Total: 0");
    totalLabel.putClientProperty("html.disable", Boolean.FALSE);
    panel6.add(totalLabel);
    generateButton = new JButton();
    generateButton.setText(">>");
    panel3.add(generateButton, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    final JLabel label5 = new JLabel();
    label5.setText("Checkin date (mm/dd/yyyy)");
    panel3.add(label5, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label6 = new JLabel();
    label6.setText("Checkout date (mm/dd/yyyy)");
    panel3.add(label6, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    panel3.add(checkoutDatePicker, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    panel3.add(checkinDatePicker, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final JPanel panel7 = new JPanel();
    panel7.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
    panel1.add(panel7, BorderLayout.SOUTH);
    localRadioButton = new JRadioButton();
    localRadioButton.setSelected(true);
    localRadioButton.setText("Local");
    panel7.add(localRadioButton);
    remoteRadioButton = new JRadioButton();
    remoteRadioButton.setText("Remote");
    panel7.add(remoteRadioButton);
    final JPanel panel8 = new JPanel();
    panel8.setLayout(new BorderLayout(0, 0));
    mainPanel.add(panel8, BorderLayout.CENTER);
    final JPanel panel9 = new JPanel();
    panel9.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    panel8.add(panel9, BorderLayout.WEST);
    iconLabel = new JLabel();
    iconLabel.setText("Label");
    panel9.add(iconLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel10 = new JPanel();
    panel10.setLayout(new BorderLayout(-1, -1));
    panel8.add(panel10, BorderLayout.EAST);
    runBtn = new JButton();
    runBtn.setText("Run");
    panel10.add(runBtn, BorderLayout.CENTER);
    exitBtn = new JButton();
    exitBtn.setText("Exit");
    panel10.add(exitBtn, BorderLayout.EAST);
    viewDetailBtn = new JButton();
    viewDetailBtn.setText("View Detailed Test Results");
    viewDetailBtn.setVisible(false);
    panel10.add(viewDetailBtn, BorderLayout.WEST);
  }

  /**
   * @noinspection ALL
   */
  public JComponent $$$getRootComponent$$$() {
    return mainPanel;
  }

  /**
   * Place custom component creation code here
   */
  private void createUIComponents() {
    // date picker
    Properties p = new Properties();
    p.put("text.today", "Today");
    p.put("text.month", "Month");
    p.put("text.year", "Year");

    checkinDatePicker = new JDatePickerImpl(new JDatePanelImpl(new UtilDateModel(new Date()), p), new DateLabelFormatter());
    checkinDatePicker.getJFormattedTextField().setBorder(BorderFactory.createTitledBorder(BorderFactory.createLoweredBevelBorder()));
    checkinDatePicker.getModel().addChangeListener(e -> updateTotalLabel());

    checkoutDatePicker = new JDatePickerImpl(new JDatePanelImpl(new UtilDateModel(new Date()), p), new DateLabelFormatter());
    checkoutDatePicker.getJFormattedTextField().setBorder(BorderFactory.createTitledBorder(BorderFactory.createLoweredBevelBorder()));
    checkoutDatePicker.getModel().addChangeListener(e -> updateTotalLabel());
  }
}
