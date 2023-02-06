package com.di.jmeter.config.gui;

import com.di.jmeter.config.ExtendedCsvDataSetConfig;
import com.di.jmeter.utils.BrowseAction;
import com.di.jmeter.utils.GuiBuilderHelper;
import org.apache.jmeter.config.gui.AbstractConfigGui;
import org.apache.jmeter.testelement.TestElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ExtendedCsvDataSetGui extends AbstractConfigGui implements ActionListener {
    private static final long serialVersionUID = 240L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedCsvDataSetGui.class);

    private static final String DISPLAY_NAME="Extended CSV Data Set Config";

//    private transient String selectRow; // Sequential | random | unique
//    private transient String updateValue; // Each iteration | Once
//    private transient String ooValue; // Abort Thread | Continue cyclic manner | Continue with lastValue
//    private transient String sharingMode;
//    private transient boolean autoAllocate;
//    private transient boolean allocate;
//    private transient String blockSize;
    private JTextField filename;
    private JTextField fileEncoding;
    private JTextField variableNames;
    private JComboBox ignoreFirstLine;
    private JTextField delimiter;
    private JComboBox quotedData;
    private JRadioButton autoAllocate;
    private JRadioButton allocate;
    private JComboBox selectRow; // Sequential | random | unique
    private JComboBox updateValue; // Each iteration | Once
    private JComboBox ooValue; // Abort Thread | Continue cyclic manner | Continue with lastValue

    
    public ExtendedCsvDataSetGui(){
        initialiseGui();
        initialiseGuiValues();
    }


    private void initialiseGui() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());
        Container topPanel = makeTitlePanel();
        add(topPanel, BorderLayout.NORTH);

        JPanel container = new JPanel(new BorderLayout());
        container.add(getDataSourceConfigBox(), BorderLayout.NORTH);
        container.add(getAllocateConfigBox(), BorderLayout.CENTER);
        add(container,BorderLayout.CENTER);
    }

    private Box getDataSourceConfigBox() {
        Box csvDataSourceConfigBox = Box.createVerticalBox();
        csvDataSourceConfigBox.add(getCsvDataSourceConfigPanel());
        return csvDataSourceConfigBox;
    }

    private Box getAllocateConfigBox(){
        Box allocateBlockConfigBox = Box.createVerticalBox();
        allocateBlockConfigBox.add(getAllocateBlockConfigBox());
        return allocateBlockConfigBox;
    }

    private Component getAllocateBlockConfigBox() {
        JPanel container = new JPanel(new BorderLayout());
        container.add(getAllocateBlockPanel(), BorderLayout.NORTH);
        add(container, BorderLayout.CENTER);
        return container;
    }

    private JPanel getCsvDataSourceConfigPanel() {
        JPanel container = new JPanel(new BorderLayout());
        container.add(getMainPanel(), BorderLayout.NORTH);
        add(container, BorderLayout.CENTER);
        return container;
    }

    private JPanel getAllocateBlockPanel() {
        JPanel allocateConfigPanel = new JPanel(new GridBagLayout());
        allocateConfigPanel.setBorder(BorderFactory.createTitledBorder("Allocate values to thread"));
        allocateConfigPanel.setLayout(new BoxLayout(allocateConfigPanel, BoxLayout.Y_AXIS));

        JRadioButton autoAllocate = new JRadioButton();
        JLabel autoAllocateLabel = new JLabel("Automatically allocate block for threads");

        JPanel radioButtonPanel1 = new JPanel();
        radioButtonPanel1.setLayout(new FlowLayout(FlowLayout.LEFT));
        radioButtonPanel1.add(autoAllocate);
        radioButtonPanel1.add(autoAllocateLabel);
        allocateConfigPanel.add(radioButtonPanel1);

        JRadioButton allocate = new JRadioButton();
        JLabel allocateLabel1 = new JLabel("Allocate");
        JTextField blockSize = new JTextField(3);
        blockSize.setEnabled(false);
        JLabel allocateLabel2 = new JLabel(" values for each thread");

        JPanel radioButtonPanel2 = new JPanel();
        radioButtonPanel2.setLayout(new FlowLayout(FlowLayout.LEFT));
        radioButtonPanel2.add(allocate);
        radioButtonPanel2.add(allocateLabel1);
        radioButtonPanel2.add(blockSize);
        radioButtonPanel2.add(allocateLabel2);
        allocateConfigPanel.add(radioButtonPanel2);

        ButtonGroup allocationGroup = new ButtonGroup();
        allocationGroup.add(autoAllocate);
        allocationGroup.add(allocate);

        allocate.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                blockSize.setEnabled(allocate.isSelected());
            }
        });

        return allocateConfigPanel;
    }

    private JPanel getMainPanel(){
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createTitledBorder("Configure the CSV Data Source")); //$NON-NLS-1$

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.anchor = GridBagConstraints.FIRST_LINE_END;

        GridBagConstraints editConstraints = new GridBagConstraints();
        editConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
        editConstraints.weightx = 1.0;
        editConstraints.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addToPanel(mainPanel, labelConstraints, 0, row, new JLabel("Filename: ", JLabel.RIGHT));
        addToPanel(mainPanel, editConstraints, 1, row, filename = new JTextField(20));
        JButton browseButton;
        addToPanel(mainPanel, labelConstraints, 2, row, browseButton = new JButton("Browse..."));
        row++;

        GuiBuilderHelper.strechItemToComponent(filename, browseButton);
        editConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        labelConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        browseButton.addActionListener(new BrowseAction(filename, false));

        addToPanel(mainPanel, labelConstraints, 0, row, new JLabel("File encoding: ", JLabel.CENTER));
        addToPanel(mainPanel, editConstraints, 1, row, fileEncoding = new JTextField(20));
        row++;

        addToPanel(mainPanel, labelConstraints, 0, row, new JLabel("Variable Name(s): ", JLabel.CENTER));
        addToPanel(mainPanel, editConstraints, 1, row, variableNames = new JTextField(20));
        row++;

        addToPanel(mainPanel, labelConstraints, 0, row, new JLabel("Consider first line as Variable Name: ", JLabel.CENTER));
        addToPanel(mainPanel, editConstraints, 1, row, ignoreFirstLine = new JComboBox());
        row++;

        addToPanel(mainPanel, labelConstraints, 0, row, new JLabel("Delimiter: ", JLabel.CENTER));
        addToPanel(mainPanel, editConstraints, 1, row, delimiter = new JTextField(20));
        row++;

        addToPanel(mainPanel, labelConstraints, 0, row, new JLabel("Allow Quoted Data: ", JLabel.CENTER));
        addToPanel(mainPanel, editConstraints, 1, row, quotedData = new JComboBox());
        row++;

        addToPanel(mainPanel, labelConstraints, 0, row, new JLabel("Select Row: ", JLabel.CENTER));
        addToPanel(mainPanel, editConstraints, 1, row, selectRow = new JComboBox());
        row++;

        addToPanel(mainPanel, labelConstraints, 0, row, new JLabel("Update Values: ", JLabel.CENTER));
        addToPanel(mainPanel, editConstraints, 1, row, updateValue = new JComboBox());
        row++;

        addToPanel(mainPanel, labelConstraints, 0, row, new JLabel("When out of Values: ", JLabel.CENTER));
        addToPanel(mainPanel, editConstraints, 1, row, ooValue = new JComboBox());
        row++;

        return mainPanel;
    }

    private void initialiseGuiValues() {
        filename.setText("");
        fileEncoding.setText("");
        variableNames.setText("");
//        ignoreFirstLine.setSelectedIndex(0);
        delimiter.setText("");
//        quotedData.setSelectedIndex(0);
    }

    @Override
    public String getLabelResource() {
        return DISPLAY_NAME;
    }

    @Override
    public String getStaticLabel() {
        return DISPLAY_NAME;
    }

    @Override
    public TestElement createTestElement() {
        ExtendedCsvDataSetConfig extendedCsvDataSetConfig = new ExtendedCsvDataSetConfig();
        modifyTestElement(extendedCsvDataSetConfig);
        return extendedCsvDataSetConfig;
    }

    @Override
    public void modifyTestElement(TestElement testElement) {
        configureTestElement(testElement);
        if (testElement instanceof ExtendedCsvDataSetConfig) {
            ExtendedCsvDataSetConfig extendedCsvDataSetConfig = (ExtendedCsvDataSetConfig) testElement;
            extendedCsvDataSetConfig.setFilename(this.filename.getText());
            extendedCsvDataSetConfig.setFilename(this.fileEncoding.getText());
            extendedCsvDataSetConfig.setVariableNames(this.variableNames.getText());
//            extendedCsvDataSetConfig.setIgnoreFirstLine(this.ignoreFirstLine.getSelectedIndex());
            extendedCsvDataSetConfig.setDelimiter(this.delimiter.getSelectedText());
//            extendedCsvDataSetConfig.setQuotedData(this.quotedData.getSelectedIndex());
        }

    }
    public JComboBox getBooleanComboBox(){
        String[] boolValues = { "true", "false" };
        JComboBox comboBox = new JComboBox(boolValues);
        return comboBox;
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        if (element instanceof ExtendedCsvDataSetConfig) {
            ExtendedCsvDataSetConfig extendedCsvDataSetConfig = (ExtendedCsvDataSetConfig) element;
            filename.setText(extendedCsvDataSetConfig.getFilename());
            fileEncoding.setText(extendedCsvDataSetConfig.getFileEncoding());
            variableNames.setText(extendedCsvDataSetConfig.getVariableNames());
//            ignoreFirstLine.setSelectedIndex(extendedCsvDataSetConfig.getIgnoreFirstLine());
            delimiter.setText(extendedCsvDataSetConfig.getDelimiter());
//            quotedData.setSelectedIndex(extendedCsvDataSetConfig.getQuotedData());
        }
    }

    private void addToPanel(JPanel panel, GridBagConstraints constraints, int col, int row, JComponent component) {
        constraints.gridx = col;
        constraints.gridy = row;
        panel.add(component, constraints);
    }

    @Override
    public void clearGui() {
        super.clearGui();
        initialiseGuiValues();
    }

    @Override
    public void actionPerformed(ActionEvent e) {

    }
}
