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
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jorphan.util.JMeterStopThreadException;
import org.apache.jorphan.util.JOrphanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class ExtendedCsvDataSet extends ConfigTestElement implements TestBean, LoopIterationListener, NoConfigMerge {

	private static final long serialVersionUID = 767792680142202807L;
	private static Logger LOGGER = LoggerFactory.getLogger(ExtendedCsvDataSet.class);

	private transient String filename;
	private transient String fileEncoding;
	private transient String variableNames;
	private transient boolean ignoreFirstLine;
	private transient String delimiter;
	private transient boolean quotedData;
	private transient String selectRow; // Sequential | random | unique
	private transient String updateValue; // Each iteration | Once
	private transient String ooValue; // Abort Vuser | Continue cyclic manner | Continue with lastvalue
	private transient String shareMode;
	private transient boolean autoAllocate;
	private transient String blockSize;

	private boolean recycle = false;
	private transient String[] variables;
	private transient String alias;
	private boolean firstLineIsNames = false;
	private boolean ooFlag = true;
	private boolean updateOnceFlag = true;

	@Override
	public void iterationStart(LoopIterationEvent iterEvent) {

//		final int selectRowInt = ExtendedCsvDataSetBeanInfo.getSelectRowAsInt(getSelectRow());
//		final int updateValInt = ExtendedCsvDataSetBeanInfo.getUpdateValueAsInt(getUpdateValue());
//		final int ooValuesInt = ExtendedCsvDataSetBeanInfo.getRecycleAsInt(getOoValue());
//		final int shareModeInt = ExtendedCsvDataSetBeanInfo.getShareModeAsInt(getShareMode());

		ExtFileServer fServer = ExtFileServer.getFileServer();
		final JMeterContext context = getThreadContext();
		String delimiter = getDelimiter(); // delimiter -> ',' (comma) will be default
		String[] lineValues = {};
		if (variables == null) {
			ExtFileServer.setReadPos(0);
			initVars(fServer, context, delimiter);
		}

		JMeterVariables jMeterVariables = context.getVariables();

		// Select Row -> Sequential, Random, Unique
		switch (ExtendedCsvDataSetBeanInfo.getSelectRowAsInt(getSelectRow())) {
			case ExtendedCsvDataSetBeanInfo.SEQUENTIAL:
				try{
					if(isQuotedData()){
						lineValues = fServer.getParsedLine(alias, recycle, firstLineIsNames || ignoreFirstLine, delimiter.charAt(0));
					}else {
						String line = fServer.readLine(alias, recycle, firstLineIsNames || ignoreFirstLine);
						lineValues = JOrphanUtils.split(line, delimiter, false);
					}
				}catch (IOException e){
					LOGGER.error(e.toString());
				}
				LOGGER.info("Sequential");
				break;

			case ExtendedCsvDataSetBeanInfo.UNIQUE:
				try{
					if(isQuotedData()){
						lineValues = fServer.getParsedLine(alias, recycle, firstLineIsNames || ignoreFirstLine, delimiter.charAt(0));
					}else{
						String line = fServer.getUniqueLine(alias, firstLineIsNames || ignoreFirstLine, ExtFileServer.getReadPos(), ExtFileServer.getStartPos(), ExtFileServer.getEndPos());
						lineValues = JOrphanUtils.split(line, delimiter, false);
					}
				}catch (IOException e){
					LOGGER.error(e.toString());
				}
				LOGGER.info("Unique");
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
				LOGGER.info("Random");
				break;
			default:
				LOGGER.info("Default");
				throw new JMeterStopThreadException("Invalid selection :" + getFilename() + " detected for Extended CSV DataSet:"
						+ getName() + " configured to Select Row Parameter :" + getSelectRow());
		}

		// Update Value --> Each Iteration, Once
		switch (ExtendedCsvDataSetBeanInfo.getUpdateValueAsInt(getUpdateValue())) {
			case ExtendedCsvDataSetBeanInfo.EACH_ITERATION:
				LOGGER.info("Each Iteration");
				if(ooFlag){
					for (int a = 0; a < variables.length && a < lineValues.length; a++) {
						jMeterVariables.put(variables[a], lineValues[a]);
					}
				}
				break;
			case ExtendedCsvDataSetBeanInfo.ONCE:
				LOGGER.info("Once");
				if(updateOnceFlag){
					for (int a = 0; a < variables.length && a < lineValues.length; a++) {
						jMeterVariables.put(variables[a], lineValues[a]);
					}
					this.updateOnceFlag = false;
				}
				break;
			default:
				LOGGER.info("Default");
				throw new JMeterStopThreadException("Invalid selection :" + getFilename() + " detected for Extended CSV DataSet:"
						+ getName() + " configured to Select Row Parameter :" + getUpdateValue());
		}

		// When Out of Values --> Continue Cyclic, Abort Thread, Continue with the last value
		switch (ExtendedCsvDataSetBeanInfo.getRecycleAsInt(getOoValue())){
			case ExtendedCsvDataSetBeanInfo.CONTINUE_CYCLIC://0
				if(getSelectRow().equalsIgnoreCase("selectRow.unique")){
					if((ExtFileServer.getReadPos() >= ExtFileServer.getEndPos())){
						ExtFileServer.setReadPos(ExtFileServer.getStartPos());
					}else {
						ExtFileServer.setReadPos(ExtFileServer.getReadPos() + 1);
					}
				}
				break;
			case ExtendedCsvDataSetBeanInfo.ABORT_THREAD://1
				if (lineValues.length == 0 || (ExtFileServer.getReadPos() > ExtFileServer.getEndPos())){
					throw new JMeterStopThreadException("End of file :" + getFilename() + " detected for Extended CSV DataSet:"
							+ getName() + " configured with stopThread: " + getOoValue());
				}else if(getSelectRow().equalsIgnoreCase("selectRow.unique")){
						ExtFileServer.setReadPos(ExtFileServer.getReadPos() + 1);
				}
				break;
			case ExtendedCsvDataSetBeanInfo.CONTINUE_WITH_LAST_VALUE://2
				if (!jMeterVariables.get(variables[0]).isEmpty()){
					this.ooFlag = false;
				}else{
					this.ooFlag = true;
				}
				if(getSelectRow().equalsIgnoreCase("selectRow.unique")){
					if((ExtFileServer.getReadPos() >= ExtFileServer.getEndPos())){
						ExtFileServer.setReadPos(ExtFileServer.getReadPos());
					}else {
						ExtFileServer.setReadPos(ExtFileServer.getReadPos() + 1);
						this.ooFlag = true;
					}
				}
				break;
			default:
				LOGGER.info("Default");
				throw new JMeterStopThreadException("Invalid selection :" + getFilename() + " detected for Extended CSV DataSet:"
						+ getName() + " Configured to When out of values parameter :" + getOoValue());
		}


	}

	private void initBlockFeatures(String filename, JMeterContext context, ExtFileServer fServer, boolean autoAllocate, String blockSize) throws IOException{
		int blockSizeInt;
		String threadName = context.getThread().getThreadName();
		//Integer.parseInt(context.getThread().getThreadName().substring(context.getThread().getThreadName().lastIndexOf('-') + 1)))
		if(ExtFileServer.getListSize() < 1){
			fServer.reserveFile(filename, getFileEncoding(), alias, ignoreFirstLine);
			fServer.loadCsv(filename, isIgnoreFirstLine());
		}
		if(isAutoAllocate()){
			blockSizeInt = ExtFileServer.getListSize() / context.getThreadGroup().getNumberOfThreads();
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

	private void initVars(ExtFileServer server, final JMeterContext context, String delim) {
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
				firstLineIsNames = true;
			} catch (IOException e) {
				throw new IllegalArgumentException("Could not split CSV header line from file:" + fileName, e);
			}
		} else {
			server.reserveFile(fileName, getFileEncoding(), alias, ignoreFirstLine);
			variables = JOrphanUtils.split(names, ",");
		}
		trimVarNames(variables);

		if(getSelectRow().equalsIgnoreCase("selectRow.unique")){
			try {
				initBlockFeatures(alias, context, server, isAutoAllocate(), getBlockSize());
			} catch (IOException e) {
				e.printStackTrace();
			}
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
		this.variableNames = variableNames;
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
