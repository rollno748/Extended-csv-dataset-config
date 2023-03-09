package com.di.jmeter.config.gui;

import com.di.jmeter.config.ExtendedCsvDataSetConfig;
import com.di.jmeter.utils.BrowseAction;
import org.apache.jmeter.config.gui.AbstractConfigGui;
import org.apache.jmeter.testelement.TestElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

import static com.di.jmeter.config.ExtendedCsvDataSetConfig.IGNORE_FIRST_LINE;
import static com.di.jmeter.config.ExtendedCsvDataSetConfig.QUOTED_DATA;

public class ExtendedCsvDataSetConfigGui extends AbstractConfigGui {
    private static final long serialVersionUID = 240L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedCsvDataSetConfigGui.class);
    private static final String DISPLAY_NAME="Extended CSV Data Set Config";
    private JTextField filenameField;
    private JComboBox<String> fileEncodingCBox;
    private JTextField variableNamesField;
    private JComboBox<Boolean> ignoreFirstLineCBox;
    private JTextField delimiterField;
    private JComboBox<Boolean> quotedDataCBox;
    private JComboBox<String> sharingModeCBox;
    private JComboBox<String> selectRowCBox;
    private JComboBox<String> updateValueCBox;
    private JComboBox<String> ooValueCBox;
    private JRadioButton autoAllocateRButton;
    private JRadioButton allocateRButton;
    private JTextField blockSizeField;
    private final String[] fileEncodingValues = {"UTF-8", "UTF-16", "ISO-8859-15", "US-ASCII"};
    private final String[] sharingModeValues = {"All threads", "Current thread group", "Current thread"};
    private final String[] selectRowValues = {"Sequential", "Random", "Unique"};
    private final String[] updateValues = {"Each Iteration", "Once"};
    private final String[] ooValues = {"Continue Cyclic", "Continue with Last Value", "Abort Thread"};

    public ExtendedCsvDataSetConfigGui(){
        init();
        initGuiValues();
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
        ExtendedCsvDataSetConfig element = new ExtendedCsvDataSetConfig();
        modifyTestElement(element);
        return element;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        configureTestElement(element);
        if(element instanceof ExtendedCsvDataSetConfig){
            ExtendedCsvDataSetConfig eCsvDataSetConfig = (ExtendedCsvDataSetConfig) element;
            eCsvDataSetConfig.setFilename(this.filenameField.getText());
            eCsvDataSetConfig.setFileEncoding(this.fileEncodingCBox.getItemAt(fileEncodingCBox.getSelectedIndex()));
            eCsvDataSetConfig.setVariableNames(this.variableNamesField.getText());
            eCsvDataSetConfig.setIgnoreFirstLine(IGNORE_FIRST_LINE, (Boolean) ignoreFirstLineCBox.getSelectedItem());
            eCsvDataSetConfig.setQuotedData(QUOTED_DATA, (Boolean) quotedDataCBox.getSelectedItem());
            eCsvDataSetConfig.setSelectRow(this.selectRowCBox.getItemAt(selectRowCBox.getSelectedIndex()));
            eCsvDataSetConfig.setUpdateValue(this.updateValueCBox.getItemAt(updateValueCBox.getSelectedIndex()));
            eCsvDataSetConfig.setOoValue(this.ooValueCBox.getItemAt(ooValueCBox.getSelectedIndex()));
            eCsvDataSetConfig.setShareMode(this.sharingModeCBox.getItemAt(sharingModeCBox.getSelectedIndex()));
            eCsvDataSetConfig.setAutoAllocate(this.autoAllocateRButton.isSelected());
            eCsvDataSetConfig.setAllocate(this.allocateRButton.isSelected());
            eCsvDataSetConfig.setBlockSize(this.blockSizeField.getText());
            LOGGER.debug("{}", eCsvDataSetConfig.printAllProperties());
        }
    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());
        Container topPanel = makeTitlePanel();
        add(topPanel, BorderLayout.NORTH);

        JPanel rootPanel = new JPanel(new BorderLayout());
        Box csvDataSourceConfigBox = Box.createVerticalBox();
        JPanel csvDatasourceConfigPanel = new JPanel(new BorderLayout());
        JPanel csvDataSourcePanel = new JPanel(new GridBagLayout());
        csvDataSourcePanel.setBorder(BorderFactory.createTitledBorder("Configure the CSV Data Source")); //$NON-NLS-1$

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.anchor = GridBagConstraints.FIRST_LINE_END;

        GridBagConstraints editConstraints = new GridBagConstraints();
        editConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
        editConstraints.weightx = 1.0;
        editConstraints.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addToPanel(csvDataSourcePanel, labelConstraints, 0, row, new JLabel("Filename: ", JLabel.RIGHT));
        addToPanel(csvDataSourcePanel, editConstraints, 1, row, filenameField = new JTextField(20));
        JButton browseButton;
        JButton viewFileButton;
        addToPanel(csvDataSourcePanel, labelConstraints, 2, row, browseButton = new JButton("..."));
        addToPanel(csvDataSourcePanel, labelConstraints, 3, row, viewFileButton = new JButton("Edit"));
        row++;
        stretchItemToComponent(filenameField, browseButton);
        labelConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        editConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        browseButton.addActionListener(new BrowseAction(filenameField, false));

        editConstraints.insets = new Insets(0, 5, 0, 0);
        browseButton.setPreferredSize(new Dimension(30, filenameField.getPreferredSize().height));
        editConstraints.anchor = GridBagConstraints.BASELINE;

        addToPanel(csvDataSourcePanel, labelConstraints, 0, row, new JLabel("File encoding: ", JLabel.CENTER));
        addToPanel(csvDataSourcePanel, editConstraints, 1, row, fileEncodingCBox = new JComboBox<>(fileEncodingValues));
        row++;

        addToPanel(csvDataSourcePanel, labelConstraints, 0, row, new JLabel("Variable Name(s): ", JLabel.CENTER));
        addToPanel(csvDataSourcePanel, editConstraints, 1, row, variableNamesField = new JTextField(30));
        row++;

        addToPanel(csvDataSourcePanel, labelConstraints, 0, row, new JLabel("Consider first line as Variable Name: ", JLabel.CENTER));
        addToPanel(csvDataSourcePanel, editConstraints, 1, row, ignoreFirstLineCBox = new JComboBox<>(new Boolean[] { true, false }));
        row++;

        addToPanel(csvDataSourcePanel, labelConstraints, 0, row, new JLabel("Delimiter: ", JLabel.CENTER));
        addToPanel(csvDataSourcePanel, editConstraints, 1, row, delimiterField = new JTextField(20));
        row++;

        addToPanel(csvDataSourcePanel, labelConstraints, 0, row, new JLabel("Allow Quoted Data: ", JLabel.CENTER));
        addToPanel(csvDataSourcePanel, editConstraints, 1, row, quotedDataCBox = new JComboBox<>(new Boolean[] { true, false }));
        row++;

        addToPanel(csvDataSourcePanel, labelConstraints, 0, row, new JLabel("Select Row: ", JLabel.CENTER));
        addToPanel(csvDataSourcePanel, editConstraints, 1, row, selectRowCBox = new JComboBox<>(selectRowValues));
        row++;

        addToPanel(csvDataSourcePanel, labelConstraints, 0, row, new JLabel("Update Values: ", JLabel.CENTER));
        addToPanel(csvDataSourcePanel, editConstraints, 1, row, updateValueCBox = new JComboBox<>(updateValues));
        row++;

        addToPanel(csvDataSourcePanel, labelConstraints, 0, row, new JLabel("When out of Values: ", JLabel.CENTER));
        addToPanel(csvDataSourcePanel, editConstraints, 1, row, ooValueCBox = new JComboBox<>(ooValues));
        row++;

        addToPanel(csvDataSourcePanel, labelConstraints, 0, row, new JLabel("Sharing Mode: ", JLabel.CENTER));
        addToPanel(csvDataSourcePanel, editConstraints, 1, row, sharingModeCBox = new JComboBox<>(sharingModeValues));
//        row++;

        fileEncodingCBox.setEditable(true);
        csvDatasourceConfigPanel.add(csvDataSourcePanel, BorderLayout.NORTH);
        add(csvDatasourceConfigPanel, BorderLayout.CENTER);
        csvDataSourceConfigBox.add(csvDatasourceConfigPanel);

        Box allocateBlockConfigBox = Box.createVerticalBox();
        JPanel allocateBlockConfigBoxPanel = new JPanel(new BorderLayout());
        JPanel allocateConfigPanel = new JPanel(new GridBagLayout());
        allocateConfigPanel.setBorder(BorderFactory.createTitledBorder("Allocate values to thread"));
        allocateConfigPanel.setLayout(new BoxLayout(allocateConfigPanel, BoxLayout.Y_AXIS));

        autoAllocateRButton = new JRadioButton();
        JLabel autoAllocateLabel = new JLabel("Automatically allocate block for threads");
        JPanel radioButtonPanel1 = new JPanel();
        radioButtonPanel1.setLayout(new FlowLayout(FlowLayout.LEFT));
        radioButtonPanel1.add(autoAllocateRButton);
        radioButtonPanel1.add(autoAllocateLabel);
        radioButtonPanel1.setEnabled(true);
        allocateConfigPanel.add(radioButtonPanel1);

        allocateRButton = new JRadioButton();
        JLabel allocateLabel1 = new JLabel("Allocate");
        blockSizeField = new JTextField(3);
        JLabel allocateLabel2 = new JLabel(" values for each thread");
        JPanel radioButtonPanel2 = new JPanel();
        radioButtonPanel2.setLayout(new FlowLayout(FlowLayout.LEFT));
        radioButtonPanel2.add(allocateRButton);
        radioButtonPanel2.add(allocateLabel1);
        radioButtonPanel2.add(blockSizeField);
        radioButtonPanel2.add(allocateLabel2);
        allocateConfigPanel.add(radioButtonPanel2);

        ButtonGroup allocationGroup = new ButtonGroup();
        allocationGroup.add(autoAllocateRButton);
        allocationGroup.add(allocateRButton);

        allocateBlockConfigBoxPanel.add(allocateConfigPanel, BorderLayout.NORTH);
        add(allocateBlockConfigBoxPanel, BorderLayout.CENTER);
        allocateBlockConfigBox.add(allocateBlockConfigBoxPanel);

        ooValueCBox.setEnabled(false);
        allocateConfigPanel.setEnabled(false);
        allocateBlockConfigBoxPanel.setEnabled(false);
        autoAllocateRButton.setEnabled(false);
        autoAllocateLabel.setEnabled(false);
        autoAllocateRButton.setSelected(false);
        allocateRButton.setEnabled(false);
        allocateLabel1.setEnabled(false);
        allocateLabel2.setEnabled(false);
        allocateRButton.setSelected(false);

        rootPanel.add(csvDataSourceConfigBox, BorderLayout.NORTH);
        rootPanel.add(allocateBlockConfigBox, BorderLayout.CENTER);
//        rootPanel.add(fileManipulatorConfigBox, BorderLayout.CENTER);
        add(rootPanel,BorderLayout.CENTER);

        ignoreFirstLineCBox.addActionListener(e-> LOGGER.debug("Ignore First line in csv is set as : {}", ignoreFirstLineCBox.getSelectedItem()));
        quotedDataCBox.addActionListener(e-> LOGGER.debug("Quoted data in csv is set as : {}", quotedDataCBox.getSelectedItem()));
        sharingModeCBox.addActionListener(e-> LOGGER.debug("Sharing mode is set as : {}", sharingModeCBox.getSelectedItem()));

        selectRowCBox.addActionListener(e -> {
            LOGGER.debug("Selection is : {}", selectRowCBox.getSelectedItem());
            if(Objects.equals(selectRowCBox.getSelectedItem(), "Unique")){
                ooValueCBox.setEnabled(true);
                allocateConfigPanel.setEnabled(true);
                autoAllocateLabel.setEnabled(true);
                allocateLabel1.setEnabled(true);
                allocateLabel2.setEnabled(true);
                autoAllocateRButton.setEnabled(true);
                allocateRButton.setEnabled(true);
                autoAllocateRButton.setSelected(autoAllocateRButton.isSelected());
                allocateRButton.setSelected(allocateRButton.isSelected());
                blockSizeField.setEnabled(allocateRButton.isSelected() && allocateRButton.isEnabled());
            }else{
                ooValueCBox.setEnabled(false);
                allocateConfigPanel.setEnabled(false);
                autoAllocateLabel.setEnabled(false);
                allocateLabel1.setEnabled(false);
                allocateLabel2.setEnabled(false);
                autoAllocateRButton.setEnabled(false);
                allocateRButton.setEnabled(false);
                blockSizeField.setEnabled(false);
            }
        });

        autoAllocateRButton.addActionListener(e -> {
            autoAllocateRButton.setEnabled(autoAllocateRButton.isSelected());
            allocateRButton.setEnabled(true);
            blockSizeField.setEnabled(false);
        });

        allocateRButton.addActionListener(e -> {
            allocateLabel2.setEnabled(true);
            blockSizeField.setEnabled(allocateRButton.isSelected());
        });

        viewFileButton.addActionListener(e -> {
            try {
                Desktop desktop = Desktop.getDesktop();
                if(filenameField.getText().equals("") || filenameField.getText().isEmpty()){
                    throw new FileNotFoundException();
                }

                if(desktop.isSupported(Desktop.Action.EDIT)){
                    desktop.edit(new File(filenameField.getText()));
                }else if(desktop.isSupported(Desktop.Action.OPEN)){
                    desktop.open(new File(filenameField.getText()));
                }else{
                    JOptionPane.showMessageDialog(new ExtendedCsvDataSetConfigGui(), "Unable to get the default editor");
                }
            } catch (FileNotFoundException fne){
                JOptionPane.showMessageDialog(new ExtendedCsvDataSetConfigGui(),"Invalid File path.");
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private void initGuiValues() {
        filenameField.setText("");
        fileEncodingCBox.setSelectedIndex(0);
        variableNamesField.setText("");
        ignoreFirstLineCBox.setSelectedIndex(0);
        delimiterField.setText(",");
        quotedDataCBox.setSelectedIndex(1);
        selectRowCBox.setSelectedIndex(0);
        updateValueCBox.setSelectedIndex(0);
        ooValueCBox.setSelectedIndex(0);
        sharingModeCBox.setSelectedIndex(0);
        blockSizeField.setText("");
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        if(element instanceof ExtendedCsvDataSetConfig){
            ExtendedCsvDataSetConfig config = (ExtendedCsvDataSetConfig) element;
            filenameField.setText(config.getFilename());
            fileEncodingCBox.setSelectedItem(config.getFileEncoding());
            variableNamesField.setText(config.getVariableNames());
            ignoreFirstLineCBox.setSelectedItem(config.isIgnoreFirstLine());
            delimiterField.setText(config.getDelimiter());
            quotedDataCBox.setSelectedItem(config.isQuotedData());
            selectRowCBox.setSelectedItem(config.getSelectRow());
            updateValueCBox.setSelectedItem(config.getUpdateValue());
            ooValueCBox.setSelectedItem(config.getOoValue());
            sharingModeCBox.setSelectedItem(config.getShareMode());
            autoAllocateRButton.setSelected(config.isAutoAllocate());
            allocateRButton.setSelected(config.isAllocate());
            blockSizeField.setText(config.getBlockSize());
        }
    }

    @Override
    public void clearGui() {
        super.clearGui();
        initGuiValues();
    }

    private void addToPanel(JPanel panel, GridBagConstraints constraints, int col, int row, JComponent component) {
        constraints.gridx = col;
        constraints.gridy = row;
        panel.add(component, constraints);
    }
    public static void stretchItemToComponent(JComponent component, JComponent item) {
        int iWidth = (int) item.getPreferredSize().getWidth();
        int iHeight = (int) component.getPreferredSize().getHeight();
        item.setPreferredSize(new Dimension(iWidth, iHeight));
    }

}
