package com.di.jmeter.config;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.engine.event.LoopIterationListener;
import org.apache.jmeter.engine.util.NoConfigMerge;
import org.apache.jmeter.save.CSVSaveService;
import org.apache.jmeter.services.FileServer;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.util.JMeterStopThreadException;
import org.apache.jorphan.util.JOrphanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedCsvDataSet extends ConfigTestElement implements TestBean, LoopIterationListener, NoConfigMerge {

	private static final long serialVersionUID = 767792680142202807L;
	private static Logger LOGGER = LoggerFactory.getLogger(ExtendedCsvDataSet.class);

	private static final String EOFVALUE = JMeterUtils.getPropDefault("csvdataset.eofstring", "<EOF>");

	private transient String filename;
	private transient String fileEncoding;
	private transient String variableNames;
	private transient boolean ignoreFirstLine;
	private transient String delimiter;
	private transient boolean quotedData;
	private transient String selectRow; // Sequential | random | unique
	private transient String updateValue; // Each iteration | Each occurrence | Once
	private transient String recycle; // abort Vuser | Continue cyclic manner | continue with lastvalue
	private transient String shareMode;
	private transient boolean autoAllocate;
	private transient String allocateSize;

	private transient String[] variables;
	private transient String alias;
	private boolean firstLineIsNames = false;
	private boolean recycleBool;
	private String[] lineValues = {};

	@Override
	public void iterationStart(LoopIterationEvent iterEvent) {

		FileServer fServer = FileServer.getFileServer();
		final JMeterContext context = getThreadContext();
		String delimiter = getDelimiter(); // If delimiter value not present -> ',' (comma) will be considered as
											// default
		String fileName = getFilename().trim();

		String selectRow = getSelectRow();
		String updateVal = getUpdateValue();
		String shareMode = getShareMode();

		int selectRowInt = ExtendedCsvDataSetBeanInfo.getSelectRowAsInt(selectRow);
		int updateValInt = ExtendedCsvDataSetBeanInfo.getUpdateValueAsInt(updateVal);
		int shareModeInt = ExtendedCsvDataSetBeanInfo.getShareModeAsInt(shareMode);

		// Update Value --> Each Iteration, Each occurence, Once

		switch (updateValInt) {

		case ExtendedCsvDataSetBeanInfo.EACH_ITERATION:

			break;
		case ExtendedCsvDataSetBeanInfo.EACH_OCCURRENCE:

			break;
		case ExtendedCsvDataSetBeanInfo.ONCE:

			break;

		default:
			// EACH ITERATION
			break;
		}

		if (variables == null) {

			// Sharing Mode --> All threads, Current ThreadGroup, Current Thread

			switch (shareModeInt) {

			case ExtendedCsvDataSetBeanInfo.SHARE_ALL:
				alias = fileName;
				break;
			case ExtendedCsvDataSetBeanInfo.SHARE_GROUP:
				alias = fileName + "@" + System.identityHashCode(context.getThreadGroup());
				break;
			case ExtendedCsvDataSetBeanInfo.SHARE_THREAD:
				alias = fileName + "@" + System.identityHashCode(context.getThread());
				break;
			default:
				alias = fileName + "@" + shareMode; // user-specified key
				break;
			}

			final String names = getVariableNames();

			if (StringUtils.isEmpty(names)) {
				String header = fServer.reserveFile(fileName, getFileEncoding(), alias, true);
				try {
					variables = CSVSaveService.csvSplitString(header, delimiter.charAt(0));
					firstLineIsNames = true;
				} catch (IOException e) {
					throw new IllegalArgumentException("Could not split CSV header line from file:" + fileName, e);
				}
			} else {
				fServer.reserveFile(fileName, getFileEncoding(), alias, ignoreFirstLine);
				variables = JOrphanUtils.split(names, ",");
			}
			trimVarNames(variables);
		}

		JMeterVariables threadVariables = context.getVariables();

		// Select Row -> Sequential, random, unique
		switch (selectRowInt) {

		case ExtendedCsvDataSetBeanInfo.SEQUENTIAL:
			readSequential(fServer, threadVariables);
			break;

		case ExtendedCsvDataSetBeanInfo.RANDOM:
			try {
				readRandom(fileName, fServer, threadVariables);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;

		case ExtendedCsvDataSetBeanInfo.UNIQUE:
			break;

		default:
			readSequential(fServer, threadVariables);

			break;

		}

	}

	private void readRandom(String fileName, FileServer fServer, JMeterVariables threadVariables) throws IOException {

		InputStream is = null;
		BufferedReader reader = null;
		try {
			is = new FileInputStream(fileName);

			reader = new BufferedReader(new InputStreamReader(is));
			LOGGER.info("READING file");
			List<String> list = new ArrayList<String>();
			String line = reader.readLine();
			while (line != null) {
				list.add(line);
				line = reader.readLine();
			}
			
			if (isIgnoreFirstLine()) {
				list.remove(0);
			}

			LOGGER.info("Generating random...");
			SecureRandom random = new SecureRandom();
			int row = random.nextInt(list.size());

			LOGGER.info("Random selection is:\n");
			LOGGER.info(list.get(row));
			
			lineValues = JOrphanUtils.split(list.get(row), delimiter, false);

			for (int a = 0; a < variables.length && a < lineValues.length; a++) {
				threadVariables.put(variables[a], lineValues[a]);
			}

		} finally {
			if (is != null) {
				is.close();
			}
			if (reader != null) {
				reader.close();
			}
		}

	}

	private void readSequential(FileServer fServer, JMeterVariables threadVariables) {

		
		String ooVal = getRecycle();
		int ooValInt = ExtendedCsvDataSetBeanInfo.getRecycleAsInt(ooVal);

		try {

			String ooValues = getRecycle();

			if (isQuotedData()) {
				if (ooValues.equalsIgnoreCase("recycle.countinueCyclic")) {
					this.recycleBool = true;
				}

				lineValues = fServer.getParsedLine(alias, recycleBool, firstLineIsNames || ignoreFirstLine,
						delimiter.charAt(0));

			} else {
				String line = fServer.readLine(alias, recycleBool, firstLineIsNames || ignoreFirstLine);
				lineValues = JOrphanUtils.split(line, delimiter, false);
			}

			for (int a = 0; a < variables.length && a < lineValues.length; a++) {
				threadVariables.put(variables[a], lineValues[a]);
			}

		} catch (IOException e) {
			LOGGER.error(e.toString());
		}

		if (lineValues.length == 0) {

			// When Out of Values --> Continue cyclic, Abort thread, Continue with last
			// value
			switch (ooValInt) {

			case ExtendedCsvDataSetBeanInfo.CONTINUE_CYCLIC:

				try {
					String line = fServer.readLine(alias, Boolean.valueOf(recycle),
							firstLineIsNames || ignoreFirstLine);
					lineValues = JOrphanUtils.split(line, delimiter, false);

					for (int a = 0; a < variables.length && a < lineValues.length; a++) {
						threadVariables.put(variables[a], lineValues[a]);
					}

				} catch (IOException e) {
					LOGGER.error(e.toString());
				}
				break;

			case ExtendedCsvDataSetBeanInfo.ABORT_THREAD:

				if (lineValues.length == 0) {
					throw new JMeterStopThreadException("End of file:" + getFilename() + " detected for CSV DataSet:"
							+ getName() + " configured with stopThread:" + getRecycle());
				}

				break;

			case ExtendedCsvDataSetBeanInfo.CONTINUE_WITH_LAST_VALUE:
				// When EOF is a empty line - showing some abnormality
				break;

			default:
				try {
					String line = fServer.readLine(alias, Boolean.valueOf(recycle),
							firstLineIsNames || ignoreFirstLine);
					lineValues = JOrphanUtils.split(line, delimiter, false);

					for (int a = 0; a < variables.length && a < lineValues.length; a++) {
						threadVariables.put(variables[a], lineValues[a]);
					}

				} catch (IOException e) {
					LOGGER.error(e.toString());
				}
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

	public String getRecycle() {
		return recycle;
	}

	public void setRecycle(String recycle) {
		this.recycle = recycle;
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

	public String getAllocateSize() {
		return allocateSize;
	}

	public void setAllocateSize(String allocateSize) {
		this.allocateSize = allocateSize;
	}

}
