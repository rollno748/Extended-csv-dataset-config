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

import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.engine.event.LoopIterationListener;
import org.apache.jmeter.engine.util.NoThreadClone;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.testelement.ThreadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ExtendedCsvDataSetConfig extends ConfigTestElement implements NoThreadClone, LoopIterationListener, TestStateListener, ThreadListener {

    private static final long serialVersionUID = 767792680142202807L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedCsvDataSetConfig.class);

    private transient String filename;
    private transient String fileEncoding;
    private transient String variableNames;
    private transient int ignoreFirstLine;
    private transient String delimiter;
    private transient int quotedData;
//    private transient String selectRow; // Sequential | random | unique
//    private transient String updateValue; // Each iteration | Once
//    private transient String ooValue; // Abort Thread | Continue cyclic manner | Continue with lastValue
//    private transient String sharingMode;
//    private transient boolean autoAllocate;
//    private transient boolean allocate;
//    private transient String blockSize;

    @Override
    public void iterationStart(LoopIterationEvent loopIterationEvent) {
    }

    @Override
    public void testStarted() {

    }

    @Override
    public void testStarted(String s) {

    }

    @Override
    public void testEnded() {

    }

    @Override
    public void testEnded(String s) {

    }

    @Override
    public void threadStarted() {

    }

    @Override
    public void threadFinished() {

    }

    //Getters and Setters

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getFileEncoding() {
        return fileEncoding;
    }

    public void setFileEncoding(String fileEncoding) {
        this.fileEncoding = fileEncoding;
    }

    public String getVariableNames() {
        return variableNames;
    }

    public void setVariableNames(String variableNames) {
        this.variableNames = variableNames;
    }

    public int getIgnoreFirstLine() {
        return ignoreFirstLine;
    }

    public void setIgnoreFirstLine(int ignoreFirstLine) {
        this.ignoreFirstLine = ignoreFirstLine;
    }

    public String getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }

    public int getQuotedData() {
        return quotedData;
    }

    public void setQuotedData(int quotedData) {
        this.quotedData = quotedData;
    }
}
