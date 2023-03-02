/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.di.jmeter.config;

import com.di.jmeter.utils.FileServerExtended;
import org.apache.commons.lang.StringUtils;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.engine.event.LoopIterationListener;
import org.apache.jmeter.engine.util.NoThreadClone;
import org.apache.jmeter.save.CSVSaveService;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.testelement.ThreadListener;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jorphan.util.JMeterStopThreadException;
import org.apache.jorphan.util.JOrphanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ExtendedCsvDataSetConfig extends ConfigTestElement implements NoThreadClone, LoopIterationListener, TestStateListener, ThreadListener {
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
    private transient String[] variables;
    private boolean recycleFile = false;
    private transient String alias;
    private boolean ignoreFirstLine = false;
    private boolean firstLineIsNames = false;
    private boolean updateOnceFlag = true;


    @Override
    public void iterationStart(LoopIterationEvent loopIterationEvent) {
        FileServerExtended fileServer = FileServerExtended.getFileServer();
        final JMeterContext context = getThreadContext();
        final String delimiter = getDelimiter();
        JMeterVariables jMeterVariables = context.getVariables();
        boolean ignoreFirstLine = getPropertyAsBoolean(IGNORE_FIRST_LINE);
        boolean recycle = getOoValue().equalsIgnoreCase("Continue Cyclic") ? true : false;
        String[] lineValues = {};
        if (variables == null) {
            FileServerExtended.setReadPos(0);
            initVars(fileServer, context, delimiter);
        }

        switch(getSelectRow().toLowerCase()){
            case "sequential":
                try{
                    if(isQuotedData()){
                        lineValues = fileServer.getParsedLine(alias, recycle, firstLineIsNames || ignoreFirstLine, delimiter.charAt(0));
                    }else{
                        String line = fileServer.readSequential(alias, ignoreFirstLine, getOoValue());
                        lineValues = JOrphanUtils.split(line, delimiter, false);
                    }
                }catch(IOException e){
                    LOGGER.error(e.toString());
                }
                LOGGER.debug("Sequential line fetched : {}", lineValues);
                break;
            case "random":
                try{
                    if(isQuotedData()){
                        lineValues = fileServer.getParsedLine(alias, recycle, firstLineIsNames || ignoreFirstLine, delimiter.charAt(0));
                    }else{
                        String line = fileServer.readRandom(alias, ignoreFirstLine);
                        lineValues = JOrphanUtils.split(line, delimiter, false);
                    }
                }catch(IOException e){
                    LOGGER.error(e.toString());
                }
                LOGGER.debug("Random line fetched : {}", lineValues);
                break;
            case "unique":
                try{
                    if(isQuotedData()){
                        lineValues = fileServer.getParsedLine(alias, recycle, firstLineIsNames || ignoreFirstLine, delimiter.charAt(0));
                    }else{
                        String line = fileServer.readUnique(alias, ignoreFirstLine, getOoValue(), FileServerExtended.getReadPos(), FileServerExtended.getStartPos(), FileServerExtended.getEndPos());
                        lineValues = JOrphanUtils.split(line, delimiter, false);
                    }
                }catch(IOException e){
                    LOGGER.error(e.toString());
                }
                LOGGER.debug("Unique line fetched : {}", lineValues);
                break;
        }

//        Update Value --> Each Iteration, Once
        switch (getPropertyAsString(UPDATE_VALUE).toLowerCase()) {
            case "each iteration":
                LOGGER.info("Each Iteration");
                if(lineValues.length > 0){
                    for (int a = 0; a < variables.length && a < lineValues.length; a++) {
                        jMeterVariables.put(variables[a], lineValues[a]);
                    }
                }
                break;

            case "once":
                LOGGER.info("Once");
                if(updateOnceFlag){
                    for (int a = 0; a < variables.length && a < lineValues.length; a++) {
                        jMeterVariables.put(variables[a], lineValues[a]);
                    }
                    this.updateOnceFlag = false;
                }
                break;

            default:
                LOGGER.debug("Invalid selection on Update Value");
                throw new JMeterStopThreadException("Invalid selection :" + getFilename() + " detected for Extended CSV DataSet:"
                        + getName() + " configured to Select Row Parameter :" + getUpdateValue());
        }
    }

    private void initVars(FileServerExtended fileServer, JMeterContext context, String delimiter) {
        String fileName = getFilename().trim();
        setAlias(context, fileName);
        final String varNames = getVariableNames();
        if(getOoValue() != null && getOoValue().equalsIgnoreCase("Continue Cyclic")){
            this.setRecycleFile(true);
        }
        if (StringUtils.isEmpty(varNames)) {
            String header = fileServer.reserveFile(fileName, getFileEncoding(), alias, true);
            try {
                variables = CSVSaveService.csvSplitString(header, delimiter.charAt(0));
                firstLineIsNames = true;
                ignoreFirstLine = true;
                trimVarNames(variables);
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not split CSV header line from file:" + fileName, e);
            }
        }else{
            fileServer.reserveFile(fileName, getFileEncoding(), alias, ignoreFirstLine);
            variables = JOrphanUtils.split(varNames, ",");
        }
        fileServer.calculateRowCount(getFilename(), getVariableNames().isEmpty() && isIgnoreFirstLine());
        if(getSelectRow().equalsIgnoreCase("Unique")){
            this.initBlockFeatures(fileServer, alias, context);
        }
        trimVarNames(JOrphanUtils.split(varNames, ","));
    }

    private void initBlockFeatures(FileServerExtended fileServer, String alias, JMeterContext context) {
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
        FileServerExtended.setReadPosition(threadName, blockSize);
        if(FileServerExtended.getReadPos() == 0){
            FileServerExtended.setReadPos(FileServerExtended.getStartPos());
        }
    }

    private void setAlias(final JMeterContext context, String alias) {
        if(getSelectRow().equalsIgnoreCase("Sequential")){
            this.alias = alias + "@" + System.identityHashCode(context.getThread());
        }else{
            switch (getShareMode()) {
                case "share all":
                    this.alias = alias;
                    break;
                case "share group":
                    this.alias = alias + "@" + System.identityHashCode(context.getThreadGroup());
                    break;
                case "share thread":
                    this.alias = alias + "@" + System.identityHashCode(context.getThread());
                    break;
                default:
                    this.alias = alias + "@" + getShareMode();
                    break;
            }
        }
    }

    @Override
    public void testStarted() {
    }
    @Override
    public void threadStarted() {
    }

    @Override
    public void threadFinished() {
    }

    @Override
    public void testEnded() {
    }

    @Override
    public void testStarted(String s) {
        testStarted();
    }

    @Override
    public void testEnded(String s) {
        testEnded();
    }

    /**
     * trim content of array varNames
     * @param varsNames
     */
    private void trimVarNames(String[] varsNames) {
        for (int i = 0; i < varsNames.length; i++) {
            varsNames[i] = varsNames[i].trim();
        }
    }

    //Getters and Setters
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
    public void setIgnoreFirstLine(String ignoreFirstLine) {
        setProperty(IGNORE_FIRST_LINE, ignoreFirstLine);
    }
    public String getDelimiter() {
        String delim = null;
        if ("\\t".equals(getPropertyAsString(DELIMITER))) {
            delim = "\t";
        } else if (getPropertyAsString(DELIMITER).isEmpty()){
            LOGGER.debug("Empty delimiter, ',' (Comma) will be used by default");
            delim = ",";
        }
        return delim;
    }
    public void setDelimiter(String delimiter) {
        setProperty(DELIMITER, delimiter);
    }
    public boolean isQuotedData() {
        return getPropertyAsBoolean(QUOTED_DATA);
    }
    public void setQuotedData(String quotedData, Boolean selectedItem) {
        setProperty(QUOTED_DATA, quotedData);
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
    private void setShareMode(String mode){
        setProperty(SHARE_MODE, mode);
    }
    private String getShareMode() {
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
    public boolean isRecycleFile() {
        return recycleFile;
    }
    public void setRecycleFile(boolean recycleFile) {
        this.recycleFile = recycleFile;
    }
    public String printAllProperties() {
        return String.format("Filename: %s\n,FileEncoding: %s\n, VariableName: %s\n, IgnoreFirstLine: %s\n, Delimiter: %s\n, IsQuotedData: %s\n, SelectRow: %s\n, UpdateValue: %s\n, OOValue: %s\n, AutoAllocate: %s\n, Allocate: %s\n, BlockSize: %s\n",getFilename(),getFileEncoding(),getVariableNames(),isIgnoreFirstLine(), getDelimiter(),isQuotedData(),getSelectRow(),getUpdateValue(),getOoValue(),isAllocate(),isAutoAllocate(),getBlockSize());
    }
}