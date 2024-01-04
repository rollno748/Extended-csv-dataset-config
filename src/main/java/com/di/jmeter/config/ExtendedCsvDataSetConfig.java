package com.di.jmeter.config;

import com.di.jmeter.utils.FileServerExtended;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.JMeter;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.engine.event.LoopIterationListener;
import org.apache.jmeter.engine.util.NoConfigMerge;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.save.CSVSaveService;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jorphan.util.JMeterStopThreadException;
import org.apache.jorphan.util.JOrphanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ExtendedCsvDataSetConfig extends ConfigTestElement implements LoopIterationListener, TestStateListener, NoConfigMerge {
    private static final long serialVersionUID = 767792680142202807L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedCsvDataSetConfig.class);
    public static final String FILENAME = "filename";
    public static final String FILE_ENCODING = "fileEncoding";
    public static final String VARIABLE_NAMES = "variableNames";
    public static final String DELIMITER = "delimiter";
    public static final String IGNORE_FIRST_LINE = "ignoreFirstLine";
    public static final String QUOTED_DATA = "quotedData";
    public static final String SELECT_ROW = "selectRow";
    public static final String UPDATE_VALUE = "updateValue";
    public static final String OO_VALUE = "ooValue";
    public static final String SHARE_MODE = "shareMode";
    public static final String AUTO_ALLOCATE = "autoAllocate";
    public static final String ALLOCATE = "allocate";
    public static final String BLOCK_SIZE = "blockSize";
    private String[] variables;
    private String alias;
    private boolean recycleFile;
    private boolean ignoreFirstLine;
    private boolean updateOnceFlag = true;

    @Override
    public void iterationStart(LoopIterationEvent iterationEvent) {
        final JMeterContext context = getThreadContext();
        final String delimiter = this.getDelimiter();
        FileServerExtended fileServer = FileServerExtended.getFileServer();
        String[] lineValues = {};
        if (variables == null) {
            FileServerExtended.setReadPos(0);
            initVars(fileServer, context, delimiter);
        }
        JMeterVariables jMeterVariables = context.getVariables();
        // Select Row -> Sequential, Random, Unique
        switch(getSelectRow().toLowerCase()){
            case "sequential":
                try{
                    String line = fileServer.readLine(alias, recycleFile, ignoreFirstLine);
                    LOGGER.debug("Sequential line fetched : {}", line);
                    if(isQuotedData()){
                        lineValues = fileServer.csvReadLine(line, delimiter.charAt(0));
                    }else{
                        lineValues = JOrphanUtils.split(line, delimiter, false);
                    }
                }catch(IOException e){
                    LOGGER.error(e.toString());
                }
                break;
            case "random":
                try{
                    String line = fileServer.readRandom(alias, ignoreFirstLine);
                    LOGGER.debug("Random line fetched : {}", line);
                    if(isQuotedData()){
                        lineValues = fileServer.csvReadLine(line, delimiter.charAt(0));
                    }else{
                        lineValues = JOrphanUtils.split(line, delimiter, false);
                    }
                }catch(IOException e){
                    LOGGER.error(e.toString());
                }
                break;
            case "unique":
                try{
                    String line = fileServer.readUnique(alias, ignoreFirstLine, getOoValue(), FileServerExtended.getReadPos(), FileServerExtended.getStartPos(), FileServerExtended.getEndPos());
                    LOGGER.debug("Unique line fetched : {}", line);
                    if(isQuotedData()){
                        lineValues = fileServer.csvReadLine(line, delimiter.charAt(0));
                    }else{
                        lineValues = JOrphanUtils.split(line, delimiter, false);
                    }
                }catch(IOException e){
                    LOGGER.error(e.toString());
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + getSelectRow().toLowerCase());
        }

        //        Update Value --> Each Iteration, Once
        switch (getPropertyAsString(UPDATE_VALUE).toLowerCase()) {
            case "each iteration":
                if(lineValues.length > 0){
                    for (int a = 0; a < variables.length && a < lineValues.length; a++) {
                        jMeterVariables.put(variables[a], lineValues[a]);
                    }
                }else {
                    for (String variable : variables) {
                        jMeterVariables.put(variable, null);
                    }
                }
                break;
            case "once":
                if(updateOnceFlag){
                    for (int a = 0; a < variables.length && a < lineValues.length; a++) {
                        jMeterVariables.put(variables[a], lineValues[a]);
                    }
                    this.updateOnceFlag = false;
                }
                break;
            default:
                LOGGER.error("Invalid selection on Update Value");
                throw new JMeterStopThreadException("Invalid selection :" + getFilename() + " detected for Extended CSV DataSet:"
                        + getName() + " configured to Select Row Parameter :" + getUpdateValue());
        }
    }

    private void initVars(FileServerExtended fileServer, JMeterContext context, String delimiter) {
        String fileName = getFilename().trim();
        final String varNames = getVariableNames();
        setAlias(context, fileName);
        this.ignoreFirstLine = this.isIgnoreFirstLine();

        if(getOoValue() != null && getOoValue().equalsIgnoreCase("Continue Cyclic")){
            this.recycleFile = true;
        }
        if (StringUtils.isEmpty(varNames)) {
            String header = fileServer.reserveFile(fileName, getFileEncoding(), alias, true);
            try {
                variables = CSVSaveService.csvSplitString(header, delimiter.charAt(0));
                ignoreFirstLine = true;
                trimVarNames(variables);
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not split CSV header line from file:" + fileName, e);
            }
        }else{
            fileServer.reserveFile(fileName, getFileEncoding(), alias, isIgnoreFirstLine());
            variables = JOrphanUtils.split(varNames, ",");
        }

        if(!getSelectRow().equalsIgnoreCase("Sequential")){
            fileServer.calculateRowCount(alias, getVariableNames().isEmpty() && isIgnoreFirstLine());
        }
        if(getSelectRow().equalsIgnoreCase("Unique")){
            this.initBlockFeatures(context);
        }
        trimVarNames(variables);
    }

    private void initBlockFeatures(JMeterContext context) {
        String threadName = context.getThread().getThreadName();
        int blockSize;

        if(isAutoAllocate()){
            blockSize = FileServerExtended.getRowCount() / JMeterContextService.getTotalThreads();
        }else{
            blockSize = Integer.parseInt(getBlockSize());
            if(blockSize < 1){
                throw new JMeterStopThreadException("Block Size Allocation Exception :" + getBlockSize() + " Please Ensure the block size is greater than 0"
                        + " Or select auto allocate feature, which is currently set to : " + isAutoAllocate());
            }
        }
        //Set Start and end position to block
        FileServerExtended.setReadPosition(threadName, blockSize, isIgnoreFirstLine());
        if(FileServerExtended.getReadPos() == 0){
            FileServerExtended.setReadPos(FileServerExtended.getStartPos());
        }
    }

    private void setAlias(final JMeterContext context, String alias) {
        switch (getShareMode()) {
            case "All threads":
                this.alias = alias;
                break;
            case "Current thread group":
                this.alias = alias + "@" + System.identityHashCode(context.getThreadGroup());
                break;
            case "Current thread":
                this.alias = alias + "@" + System.identityHashCode(context.getThread());
                break;
            default:
                this.alias = alias + "@" + getShareMode();
                break;
        }
        if(getSelectRow().equalsIgnoreCase("Sequential")){
            this.alias = alias + "@" + System.identityHashCode(context.getThread());
        }
    }

    @Override
    public void testStarted() {
        FileServerExtended fileServer = FileServerExtended.getFileServer();
        if(JMeter.isNonGUI()){
            String baseDirectory = org.apache.jmeter.services.FileServer.getFileServer().getBaseDir();
            fileServer.setBasedir(baseDirectory);
        }else {
            String testPlanFile = GuiPackage.getInstance().getTestPlanFile();
            fileServer.setBasedir(testPlanFile);
        }
    }

    @Override
    public void testEnded() {
        FileServerExtended fileServerExtended = FileServerExtended.getFileServer();
        try{
            fileServerExtended.closeFiles();
        } catch (IOException e){
            LOGGER.error("Exception occurred while closing file(s) : {}", e.toString());
        }
    }

    @Override
    public void testStarted(String host) {
        testStarted();
    }

    @Override
    public void testEnded(String host) {
        testEnded();
    }

    /**
     * trim content of array varNames
     * @param varsNames - Variable names
     */
    private void trimVarNames(String[] varsNames) {
        for (int i = 0; i < varsNames.length; i++) {
            varsNames[i] = varsNames[i].trim();
        }
    }

    /**
     * Getters and setters for the Config variables
     */
    public String getFilename() {
        return getPropertyAsString(FILENAME);
    }
    public void setFilename(String filename) {
        setProperty(FILENAME, filename);
    }
    public String getFileEncoding() {
        return getPropertyAsString(FILE_ENCODING);
    }
    public void setFileEncoding(String fileEncoding) {
        setProperty(FILE_ENCODING, fileEncoding);
    }
    public String getVariableNames() {
        return getPropertyAsString(VARIABLE_NAMES);
    }
    public void setVariableNames(String variableNames) {
        setProperty(VARIABLE_NAMES, variableNames);
    }
    public boolean isIgnoreFirstLine() {
        return getPropertyAsBoolean(IGNORE_FIRST_LINE);
    }
    public void setIgnoreFirstLine(Boolean selectedItem) {
        setProperty(IGNORE_FIRST_LINE, selectedItem);
    }
    public String getDelimiter() {
        return getPropertyAsString(DELIMITER);
    }
    public void setDelimiter(String delimiter) {
        String delim;
        if ("\\t".equals(delimiter)) {
            delim = "\t";
        } else if (delimiter.isEmpty()){
            LOGGER.debug("Empty delimiter, ',' (Comma) will be used by default");
            delim = ",";
        }else{
            delim = delimiter;
        }
        setProperty(DELIMITER, delim);
    }
    public boolean isQuotedData() {
        return getPropertyAsBoolean(QUOTED_DATA);
    }
    public void setQuotedData(Boolean selectedItem) {
        setProperty(QUOTED_DATA, selectedItem);
    }
    public String getSelectRow() {
        return getPropertyAsString(SELECT_ROW);
    }
    public void setSelectRow(String selectRow) {
        setProperty(SELECT_ROW, selectRow);
    }
    public String getUpdateValue() {
        return getPropertyAsString(UPDATE_VALUE);
    }
    public void setUpdateValue(String updateValue) {
        setProperty(UPDATE_VALUE, updateValue);
    }
    public String getOoValue() {
        return getPropertyAsString(OO_VALUE);
    }
    public void setOoValue(String ooValue) {
        setProperty(OO_VALUE, ooValue);
    }
    public void setShareMode(String mode){
        setProperty(SHARE_MODE, mode);
    }
    public String getShareMode() {
        return getPropertyAsString(SHARE_MODE);
    }
    public boolean isAutoAllocate() {
        return getPropertyAsBoolean(AUTO_ALLOCATE);
    }
    public void setAutoAllocate(boolean autoAllocate) {
        setProperty(AUTO_ALLOCATE, autoAllocate);
    }
    public boolean isAllocate() {
        return getPropertyAsBoolean(ALLOCATE);
    }
    public void setAllocate(boolean allocate) {
        setProperty(ALLOCATE, allocate);
    }
    public String getBlockSize() {
        return getPropertyAsString(BLOCK_SIZE);
    }
    public void setBlockSize(String blockSize) {
        setProperty(BLOCK_SIZE, blockSize);
    }
    public String printAllProperties() {
        return String.format("Filename: %s\n,FileEncoding: %s\n VariableName: %s\n IgnoreFirstLine: %s\n Delimiter: %s\n IsQuotedData: %s\n SelectRow: %s\n UpdateValue: %s\n OOValue: %s\n AutoAllocate: %s\n Allocate: %s\n BlockSize: %s\n",getFilename(),getFileEncoding(),getVariableNames(),isIgnoreFirstLine(),getDelimiter(),isQuotedData(),getSelectRow(),getUpdateValue(),getOoValue(),isAllocate(),isAutoAllocate(),getBlockSize());
    }
}
