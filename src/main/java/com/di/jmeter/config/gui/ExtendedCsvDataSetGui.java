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
import java.awt.event.ActionListener;

public class ExtendedCsvDataSetGui extends AbstractConfigGui {
    private static final long serialVersionUID = 240L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedCsvDataSetGui.class);

    private static final String DISPLAY_NAME="Extended CSV Data Set Config";
//    private transient String filename;
//    private transient String fileEncoding;
//    private transient String variableNames;
//    private transient String ignoreFirstLine;
//    private transient String delimiter;
//    private transient boolean quotedData;
//    private transient String selectRow; // Sequential | random | unique
//    private transient String updateValue; // Each iteration | Once
//    private transient String ooValue; // Abort Thread | Continue cyclic manner | Continue with lastValue
//    private transient String sharingMode;
//    private transient boolean autoAllocate;
//    private transient boolean allocate;
//    private transient String blockSize;
    private JTextField filename;
    private JTextField fileEncoding;
//    private JTextField variableNames;
//    private JTextField ignoreFirstLine;
//    private JTextField delimiter;
//    private JTextField quotedData;
//    private JTextField selectRow; // Sequential | random | unique
//    private JTextField updateValue; // Each iteration | Once
//    private JTextField ooValue; // Abort Thread | Continue cyclic manner | Continue with lastValue
    
    public ExtendedCsvDataSetGui(){
        initialiseGui();
        initialiseGuiValues();
    }

    private void initialiseGui() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());
        Container topPanel = makeTitlePanel();
        add(topPanel, BorderLayout.NORTH);

        JPanel mainPanel = new JPanel(new GridBagLayout());
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

        addToPanel(mainPanel, labelConstraints, 0, row, new JLabel("File encoding: ", JLabel.RIGHT));
        addToPanel(mainPanel, editConstraints, 1, row, fileEncoding = new JTextField(20));
        row++;

        JPanel container = new JPanel(new BorderLayout());
        container.add(mainPanel, BorderLayout.NORTH);
        add(container, BorderLayout.CENTER);
        //demo
    }

    private void initialiseGuiValues() {
        filename.setText("");
        fileEncoding.setText("");
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

        }

    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);

        if (element instanceof ExtendedCsvDataSetConfig) {
            ExtendedCsvDataSetConfig extendedCsvDataSetConfig = (ExtendedCsvDataSetConfig) element;

            filename.setText(extendedCsvDataSetConfig.getFilename());
            fileEncoding.setText(extendedCsvDataSetConfig.getFileEncoding());

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
}
