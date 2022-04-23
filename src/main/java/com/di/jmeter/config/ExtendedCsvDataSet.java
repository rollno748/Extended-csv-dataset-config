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
 *
 */

package com.di.jmeter.config;

import com.di.jmeter.utils.ExtFileServer;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.engine.event.LoopIterationListener;
import org.apache.jmeter.engine.util.NoConfigMerge;
import org.apache.jmeter.save.CSVSaveService;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jorphan.util.JMeterStopThreadException;
import org.apache.jorphan.util.JOrphanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class ExtendedCsvDataSet extends ConfigTestElement implements TestBean, LoopIterationListener, NoConfigMerge {

	private static final long serialVersionUID = 767792680142202807L;
	private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedCsvDataSet.class);

	private transient String filename;
	private transient String fileEncoding;
	private transient String variableNames;
	private transient boolean ignoreFirstLine;
	private transient String delimiter;
	private transient boolean quotedData;
	private transient String selectRow; // Sequential | random | unique
	private transient String updateValue; // Each iteration | Once
	private transient String ooValue; // Abort Thread | Continue cyclic manner | Continue with lastValue
	private transient String shareMode;
	private transient boolean autoAllocate;
	private transient String blockSize;

	private boolean recycle = false;
	private transient String[] variables;
	private transient String alias;
	private boolean firstLineIsNames = false;
	private boolean updateOnceFlag = true;

	@Override
	public void iterationStart(LoopIterationEvent iterEvent) {

		ExtFileServer fServer = ExtFileServer.getFileServer();
		final JMeterContext context = getThreadContext();
		String delimiter = getDelimiter(); // delimiter -> ',' (comma) will be default
		String[] lineValues = {};
		if (variables == null) {
			ExtFileServer.setReadPos(0);
			try {
				initVars(fServer, context, delimiter);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		JMeterVariables jMeterVariables = context.getVariables();

		// Select Row -> Sequential, Random, Unique
		switch (ExtendedCsvDataSetBeanInfo.getSelectRowAsInt(getSelectRow())) {
			case ExtendedCsvDataSetBeanInfo.SEQUENTIAL:
				try{
					if(isQuotedData()){
						lineValues = fServer.getParsedLine(alias, recycle, firstLineIsNames || ignoreFirstLine, delimiter.charAt(0));
					}else {
						String line = fServer.readSequential(alias, firstLineIsNames || ignoreFirstLine, getUpdateValue(), getOoValue());
						lineValues = JOrphanUtils.split(line, delimiter, false);
					}
				}catch (IOException e){
					LOGGER.error(e.toString());
				}
				LOGGER.debug("Sequential :: " + lineValues);
				break;
			case ExtendedCsvDataSetBeanInfo.UNIQUE:
				try{
					if(isQuotedData()){
						lineValues = fServer.getParsedLine(alias, recycle, firstLineIsNames || ignoreFirstLine, delimiter.charAt(0));
					}else{
						String line = fServer.getUniqueLine(alias, firstLineIsNames || ignoreFirstLine, getOoValue(), ExtFileServer.getReadPos(), ExtFileServer.getStartPos(), ExtFileServer.getEndPos());
						lineValues = JOrphanUtils.split(line, delimiter, false);
					}
				}catch (IOException e){
					LOGGER.error(e.toString());
				}
				LOGGER.debug("Unique :: " + lineValues);
				break;
			case ExtendedCsvDataSetBeanInfo.RANDOM:
				try{
					if(isQuotedData()){
						lineValues = fServer.getParsedLine(alias, recycle, firstLineIsNames || ignoreFirstLine, delimiter.charAt(0));
					}else{
						String line = fServer.getRandomLine(alias, recycle, firstLineIsNames || ignoreFirstLine);
						lineValues = JOrphanUtils.split(line, delimiter, false);
					}
				}catch(IOException e){
					LOGGER.error(e.toString());
				}
				LOGGER.debug("Random :: " + lineValues);
				break;
			default:
				LOGGER.debug("Invalid selection on Select row");
				throw new JMeterStopThreadException("Invalid selection :" + getFilename() + " detected for Extended CSV DataSet:"
						+ getName() + " configured to Select Row Parameter :" + getSelectRow());
		}

		// Update Value --> Each Iteration, Once
		switch (ExtendedCsvDataSetBeanInfo.getUpdateValueAsInt(getUpdateValue())) {
			case ExtendedCsvDataSetBeanInfo.EACH_ITERATION:
				LOGGER.debug("Each Iteration");
				if(lineValues.length > 0){
					for (int a = 0; a < variables.length && a < lineValues.length; a++) {
						jMeterVariables.put(variables[a], lineValues[a]);
					}
				}
				break;
			case ExtendedCsvDataSetBeanInfo.ONCE:
				LOGGER.debug("Once");
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

	private void initBlockFeatures(String filename, JMeterContext context, ExtFileServer fServer, boolean autoAllocate, String blockSize) {
		int blockSizeInt;
		String threadName = context.getThread().getThreadName();

		if(autoAllocate){
			blockSizeInt = ExtFileServer.getListSize() / JMeterContextService.getTotalThreads();
		}else{
			blockSizeInt = Integer.parseInt(blockSize);
		}
		//Set Start and end position to block
		if(blockSizeInt < 1){
			throw new JMeterStopThreadException("Allocate Block Size Exception :" + getBlockSize() + " Please Ensure the block size is greater than 0"
					+ " Or configure the auto allocate feature, which is currently set to : " + isAutoAllocate());
		}
		fServer.setEndPos((Integer.parseInt(threadName.substring(threadName.lastIndexOf('-') + 1)) * blockSizeInt) - 1);
		fServer.setStartPos((fServer.getEndPos() - blockSizeInt) + 1);

		//Set read position to block
		if(fServer.getReadPos() == 0){
			fServer.setReadPos(fServer.getStartPos());
		}
	}

	private void initVars(ExtFileServer server, final JMeterContext context, String delim) throws IOException {
		String fileName = getFilename().trim();
		final String names = getVariableNames();
		setAlias(context, fileName);

		if(ExtendedCsvDataSetBeanInfo.getRecycleAsInt(getOoValue()) == 0){
			this.recycle = true;
		}
		if (StringUtils.isEmpty(names)) {
			String header = server.reserveFile(fileName, getFileEncoding(), alias, true);
			try {
				variables = CSVSaveService.csvSplitString(header, delim.charAt(0));
				firstLineIsNames = true;ignoreFirstLine = true;
				trimVarNames(variables);
			} catch (IOException e) {
				throw new IllegalArgumentException("Could not split CSV header line from file:" + fileName, e);
			}
		}

		if(getSelectRow().equalsIgnoreCase("selectRow.sequential")){
			server.reserveFile(fileName, getFileEncoding(), alias, ignoreFirstLine);
			variables = JOrphanUtils.split(names, ",");
			trimVarNames(variables);
		}else if(getSelectRow().equalsIgnoreCase("selectRow.unique") || getSelectRow().equalsIgnoreCase("selectRow.random")){
			server.reserveFile(filename, getFileEncoding(), alias, ignoreFirstLine);
			server.loadCsv(filename, ignoreFirstLine);
			initBlockFeatures(alias, context, server, isAutoAllocate(), getBlockSize());
			variables = JOrphanUtils.split(names, ",");
			trimVarNames(variables);
		}
	}

	private void setAlias(final JMeterContext context, String alias) {
		if(getSelectRow().equalsIgnoreCase("selectRow.Sequential")){
			this.alias = alias + "@" + System.identityHashCode(context.getThread());
		}else{
			switch (ExtendedCsvDataSetBeanInfo.getShareModeAsInt(getShareMode())) {
				case ExtendedCsvDataSetBeanInfo.SHARE_ALL:
					this.alias = alias;
					break;
				case ExtendedCsvDataSetBeanInfo.SHARE_GROUP:
					this.alias = alias + "@" + System.identityHashCode(context.getThreadGroup());
					break;
				case ExtendedCsvDataSetBeanInfo.SHARE_THREAD:
					this.alias = alias + "@" + System.identityHashCode(context.getThread());
					break;
				default:
					this.alias = alias + "@" + getShareMode();
					break;
			}
		}
	}

	private void trimVarNames(String[] varsNames) {
		for (int i = 0; i < varsNames.length; i++) {
			varsNames[i] = varsNames[i].trim();
		}
	}

	// Getters and setters
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
		if(!ignoreFirstLine){
			this.variableNames = variableNames;
		}
	}

	public String getDelimiter() {
		if ("\\t".equals(delimiter)) {
			delimiter = "\t";
		} else if (delimiter.isEmpty()) {
			delimiter = ",";
		}
		return delimiter;
	}

	public void setDelimiter(String delimiter) {
		this.delimiter = delimiter;
	}

	public boolean isIgnoreFirstLine() {
		return ignoreFirstLine;
	}

	public void setIgnoreFirstLine(boolean ignoreFirstLine) {
		this.ignoreFirstLine = ignoreFirstLine;
	}

	public boolean isQuotedData() {
		return quotedData;
	}

	public void setQuotedData(boolean quotedData) {
		this.quotedData = quotedData;
	}

	public String getSelectRow() {
		return selectRow;
	}

	public void setSelectRow(String selectRow) {
		this.selectRow = selectRow;
	}

	public String getUpdateValue() {
		return updateValue;
	}

	public void setUpdateValue(String updateValue) {
		this.updateValue = updateValue;
	}

	public String getOoValue() {
		return ooValue;
	}

	public void setOoValue(String ooValue) {
		this.ooValue = ooValue;
	}

	public String getShareMode() {
		return shareMode;
	}

	public void setShareMode(String shareMode) {
		this.shareMode = shareMode;
	}

	public boolean isAutoAllocate() {
		return autoAllocate;
	}

	public void setAutoAllocate(boolean autoAllocate) {
		this.autoAllocate = autoAllocate;
	}

	public String getBlockSize() {
		return blockSize;
	}

	public void setBlockSize(String blockSize) {
		this.blockSize = blockSize;
	}
}
