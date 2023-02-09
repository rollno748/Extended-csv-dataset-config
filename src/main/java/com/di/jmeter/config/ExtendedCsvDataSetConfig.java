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

import com.di.jmeter.utils.ExtFileServer;
import jodd.csselly.selector.PseudoClass;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.engine.event.LoopIterationListener;
import org.apache.jmeter.engine.util.NoThreadClone;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.testelement.ThreadListener;
import org.apache.jmeter.threads.JMeterContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public static final String BLOCK_SIZE = "blockSize";
    public static final String ALLOCATE = "allocate";
    public static final String AUTO_ALLOCATE = "autoAllocate";

    //    private boolean recycle = false;
//    private transient String[] variables;
//    private transient String alias;
//    private boolean firstLineIsNames = false;
//    private boolean updateOnceFlag = true;


//    private transient String filename;
//    private transient String fileEncoding;
//    private transient String variableNames;
//    private transient boolean ignoreFirstLine;
//    private transient String delimiter;
//    private transient boolean quotedData;
//    private transient String selectRow; // Sequential | random | unique
//    private transient String updateValue; // Each iteration | Once
//    private transient String ooValue; // Abort Thread | Continue cyclic manner | Continue with lastValue
//    private transient boolean autoAllocate;
//    private transient boolean allocate;
//    private transient String blockSize;

    @Override
    public void iterationStart(LoopIterationEvent loopIterationEvent) {
        ExtFileServer fServer = ExtFileServer.getFileServer();
        final JMeterContext context = getThreadContext();
        String delimiter = getDelimiter();
        String[] lineValues = {};


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
        return getPropertyAsString(DELIMITER);
    }

    public void setDelimiter(String delimiter) {
        setProperty(DELIMITER, delimiter);
    }

    public boolean isQuotedData() {
        return getPropertyAsBoolean(QUOTED_DATA);
    }

    public void setQuotedData(String quotedData) {
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
        return String.format("Filename: {}\n,FileEncoding: {}\n, VariableName: {}\n," +
                        "IgnoreFirstLine: {}\n, Delimiter: {}\n, IsQuotedData: {}\n, SelectRow: {}\n, UpdateValue: {}\n, OOValue: {}\n," +
                        "AutoAllocate: {}\n, Allocate: {}\n, BlockSize: {}\n",getFilename(),getFileEncoding(),getVariableNames(),isIgnoreFirstLine(),
                getDelimiter(),isQuotedData(),getSelectRow(),getUpdateValue(),getOoValue(),isAllocate(),isAutoAllocate(),getBlockSize());
    }
}

/*
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

    public String getDelimiter() {
        return getPropertyAsString(DELIMITER);
    }

    public void setDelimiter(String delimiter) {
        setProperty(DELIMITER, delimiter);
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


    public void setAllocate(boolean allocate) {
        setProperty(ALLOCATE, allocate);
    }

    public String getBlockSize() {
        return getPropertyAsString(BLOCK_SIZE);
    }

    public void setBlockSize(String blockSize) {
        setProperty(BLOCK_SIZE, blockSize);
    }

    public boolean isIgnoreFirstLine() {
        return getPropertyAsBoolean(IGNORE_FIRST_LINE);
    }
    public void setIgnoreFirstLine(int selectedIndex) {
        setProperty(IGNORE_FIRST_LINE, selectedIndex);
    }
    public boolean isQuotedData() {
        return getPropertyAsBoolean(QUOTED_DATA);
    }
    public void setQuotedData(int selectedIndex) {
        setProperty(IGNORE_FIRST_LINE, selectedIndex);
    }
 */

