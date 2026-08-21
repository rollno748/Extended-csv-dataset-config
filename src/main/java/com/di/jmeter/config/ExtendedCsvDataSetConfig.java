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
import org.apache.jmeter.testelement.ThreadListener;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.util.JMeterStopThreadException;
import org.apache.jorphan.util.JOrphanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ExtendedCsvDataSetConfig extends ConfigTestElement implements LoopIterationListener, TestStateListener, ThreadListener, NoConfigMerge {
    private static final long serialVersionUID = 767792680142202807L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedCsvDataSetConfig.class);

    private static final String EOFVALUE = JMeterUtils.getPropDefault("csvdataset.eofstring", "<EOF>");
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
    // true when this thread is the sole owner of `alias` (i.e. no other thread can be using it),
    // so its file entry can be released as soon as this thread finishes. Set by setAlias().
    private boolean perThreadAlias;
    // Property values cached by initVars() to avoid property-map lookups on every iteration.
    // Properties are effectively immutable after test start, so this cache stays valid.
    private String selectRow;
    private String updateValue;
    private String ooValue;
    private String delimiter;
    private boolean quotedData;

    @Override
    public void iterationStart(LoopIterationEvent iterationEvent) {
        final JMeterContext context = getThreadContext();
        FileServerExtended fileServer = FileServerExtended.getFileServer();
        String[] lineValues = {};
        if (variables == null) {
            initVars(fileServer, context);
        }
        JMeterVariables jMeterVariables = context.getVariables();
        // Select Row -> Sequential, Random, Unique
        // A null line is a legitimate "no data" signal (e.g. Sequential at EOF without recycle,
        // or Unique past the end of its allocated block); handled below via the ooValue branch.
        // Any IOException here is a genuine I/O failure and must not be treated the same way -
        // it is reported and stops the thread instead of being silently ignored.
        String line;
        switch (selectRow) {
            case "sequential":
                try {
                    line = fileServer.readLine(alias, recycleFile, ignoreFirstLine);
                } catch (IOException e) {
                    throw new JMeterStopThreadException("I/O error reading file:" + getFilename()
                            + " for Extended CSV DataSet:" + getName(), e);
                }
                break;
            case "random":
                try {
                    line = fileServer.readRandom(alias, ignoreFirstLine);
                } catch (IOException e) {
                    throw new JMeterStopThreadException("I/O error reading file:" + getFilename()
                            + " for Extended CSV DataSet:" + getName(), e);
                }
                break;
            case "unique":
                try {
                    line = fileServer.readUnique(alias, ignoreFirstLine, ooValue,
                            FileServerExtended.getReadPos(alias), FileServerExtended.getStartPos(alias), FileServerExtended.getEndPos(alias));
                } catch (IOException e) {
                    throw new JMeterStopThreadException("I/O error reading file:" + getFilename()
                            + " for Extended CSV DataSet:" + getName(), e);
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + selectRow);
        }

        if (line == null) {
            // Legitimate "out of values" signal. Honor the configured ooValue rather than
            // falling through and letting the update-value switch draw a misleading "End of
            // file" conclusion for every case:
            //   - Continue with Last Value: skip variable assignment so previously-set values
            //     remain visible to downstream samplers. (Sequential mode had no support for
            //     this at all before - it silently behaved like Abort Thread.)
            //   - Abort Thread: stop this thread cleanly with a clear message, uniformly for
            //     both "Each Iteration" and "Once" update modes. (Sequential + Once + Abort
            //     Thread previously silently no-oped instead of aborting.)
            //   - Continue Cyclic: never reaches here (Sequential's readLine recycles, and
            //     Unique's readUnique wraps position internally).
            if (ooValue != null && ooValue.equalsIgnoreCase("Continue with Last Value")) {
                return;
            }
            if (ooValue != null && ooValue.equalsIgnoreCase("Abort Thread")) {
                throw new JMeterStopThreadException("End of file:" + getFilename() + " for CSV DataSet:"
                        + getName() + " configured with 'Abort Thread' on out-of-values");
            }
            // Undefined ooValue - fall through to legacy update-value handling below.
        } else {
            LOGGER.debug("{} line fetched : {}", selectRow, line);
            try {
                lineValues = quotedData ? fileServer.csvReadLine(line, delimiter.charAt(0))
                        : JOrphanUtils.split(line, delimiter, false);
            } catch (IOException e) {
                throw new JMeterStopThreadException("Could not parse CSV line from file:" + getFilename()
                        + " for Extended CSV DataSet:" + getName(), e);
            }
        }

        //        Update Value --> Each Iteration, Once
        switch (updateValue) {
            case "each iteration":
                if (lineValues.length == 0) {
                    throw new JMeterStopThreadException("End of file:" + getFilename() + " detected for CSV DataSet:"
                            + getName() + " configured to Select Row Parameter :" + updateValue);
                }
                for (int a = 0; a < variables.length && a < lineValues.length; a++) {
                    jMeterVariables.put(variables[a], lineValues[a]);
                }
                break;
            case "once":
                if (updateOnceFlag) {
                    for (int a = 0; a < variables.length && a < lineValues.length; a++) {
                        jMeterVariables.put(variables[a], lineValues[a]);
                    }
                    this.updateOnceFlag = false;
                }
                break;
            default:
                LOGGER.error("Invalid selection on Update Value");
                throw new JMeterStopThreadException("Invalid selection :" + getFilename() + " detected for Extended CSV DataSet:"
                        + getName() + " configured to Select Row Parameter :" + updateValue);
        }
    }

    private void initVars(FileServerExtended fileServer, JMeterContext context) {
        String fileName = getFilename().trim();
        final String varNames = getVariableNames();
        // Cache property values once per thread (per-thread clone of this element) - they don't
        // change during a run, and repeatedly hitting the property map on every iteration adds
        // measurable overhead under high sampler throughput.
        this.selectRow = getSelectRow() == null ? "" : getSelectRow().toLowerCase();
        this.updateValue = getPropertyAsString(UPDATE_VALUE).toLowerCase();
        this.ooValue = getOoValue();
        this.delimiter = getDelimiter();
        this.quotedData = isQuotedData();
        setAlias(context, fileName);
        FileServerExtended.setReadPos(alias, 0);
        this.ignoreFirstLine = this.isIgnoreFirstLine();

        if(ooValue != null && ooValue.equalsIgnoreCase("Continue Cyclic")){
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

        if(!"sequential".equals(selectRow)){
            // Use the *effective* ignoreFirstLine (which may have been overridden to true above
            // when varNames was empty and the header was auto-detected), so the row count that
            // read-time bounds checks rely on matches the read-time startOffset (avoids random
            // positions past the end of the offset index for auto-detected-header files where
            // the user left "Consider first line as Variable Name" set to false).
            try {
                fileServer.calculateRowCount(alias, this.ignoreFirstLine);
            } catch (IOException e) {
                throw new JMeterStopThreadException("Could not build line index for file:" + fileName
                        + " in Extended CSV DataSet:" + getName(), e);
            }
        }
        if("unique".equals(selectRow)){
            this.initBlockFeatures(context);
        }
        trimVarNames(variables);
    }

    private void initBlockFeatures(JMeterContext context) {
        final int rowCount = FileServerExtended.getFileServer().getRowCount(alias);
        final int headOffset = this.ignoreFirstLine ? 1 : 0;
        // Stable, JMeter-assigned 0-based index within this thread group (no thread-name string
        // parsing — that was fragile against custom thread-group names, and NumberFormatException
        // would abort the test at start-up if the name didn't end in a number).
        final int threadNum = context.getThreadNum();

        int startPos;
        int endPos;

        if (isAutoAllocate()) {
            // Use the CONFIGURED Number of Threads on this thread group (static per JMX) instead
            // of JMeterContextService.getTotalThreads() (which reflects the live count during
            // ramp-up and would give different block boundaries to threads that initialise at
            // different points, producing overlapping or gapped blocks).
            int numThreadsInGroup = context.getThreadGroup().getNumThreads();
            if (numThreadsInGroup <= 0) {
                throw new JMeterStopThreadException("Auto-allocate Unique mode requires the thread group's configured Number of Threads to be > 0 (got "
                        + numThreadsInGroup + ") for file:" + getFilename());
            }
            if (rowCount <= 0) {
                throw new JMeterStopThreadException("Auto-allocate Unique mode requires a non-empty data file, but file:"
                        + getFilename() + " has no data rows.");
            }
            if (threadNum >= rowCount) {
                // More threads than data rows: this thread has no block. Fail fast with a clear
                // message instead of producing an inverted/empty block that would either abort
                // cryptically on the first iteration or loop reading nothing.
                throw new JMeterStopThreadException("Auto-allocate Unique mode: thread " + threadNum
                        + " has no rows allocated because the thread group's configured Number of Threads ("
                        + numThreadsInGroup + ") exceeds the number of data rows (" + rowCount
                        + ") in file:" + getFilename());
            }
            int baseBlockSize = rowCount / numThreadsInGroup;
            int extras = rowCount % numThreadsInGroup;
            // Give the first `extras` threads one extra row each so no rows are dropped by
            // integer-division truncation.
            startPos = threadNum * baseBlockSize + Math.min(threadNum, extras);
            endPos = startPos + baseBlockSize + (threadNum < extras ? 1 : 0) - 1;
        } else {
            int blockSize;
            try {
                blockSize = Integer.parseInt(getBlockSize());
            } catch (NumberFormatException e) {
                throw new JMeterStopThreadException("Block Size must be a positive integer, got '"
                        + getBlockSize() + "' for file:" + getFilename(), e);
            }
            if (blockSize < 1) {
                throw new JMeterStopThreadException("Block Size Allocation Exception :" + getBlockSize()
                        + " Please Ensure the block size is greater than 0"
                        + " Or select auto allocate feature, which is currently set to : " + isAutoAllocate());
            }
            startPos = threadNum * blockSize;
            if (startPos >= rowCount) {
                throw new JMeterStopThreadException("Manual-allocate Unique mode: thread " + threadNum
                        + " has no rows because block-size (" + blockSize
                        + ") multiplied by the thread index places the block past the end of file:"
                        + getFilename() + " (which has " + rowCount + " data rows).");
            }
            endPos = startPos + blockSize - 1;
        }

        // Shift the whole block past the header row when we're skipping it so the positions
        // match the absolute line indices used by readIndexed().
        startPos += headOffset;
        endPos += headOffset;

        FileServerExtended.setStartPos(alias, startPos);
        FileServerExtended.setEndPos(alias, endPos);
        if (FileServerExtended.getReadPos(alias) == 0) {
            FileServerExtended.setReadPos(alias, startPos);
        }
    }

    private void setAlias(final JMeterContext context, String alias) {
        // Case-insensitive so hand-edited JMX with different casing still dispatches correctly
        // (Java switch-on-String is case-sensitive, so we use equalsIgnoreCase instead).
        String mode = getShareMode() != null ? getShareMode().trim() : "";
        if ("All threads".equalsIgnoreCase(mode)) {
            // All threads share the same file entry, taking turns via the entry's per-file lock.
            // This is the classic "partition a file across concurrent threads" pattern - each
            // thread's readLine returns the next line collectively.
            this.alias = alias;
            this.perThreadAlias = false;
        } else if ("Current thread group".equalsIgnoreCase(mode)) {
            this.alias = alias + "@" + System.identityHashCode(context.getThreadGroup());
            this.perThreadAlias = false;
        } else if ("Current thread".equalsIgnoreCase(mode)) {
            this.alias = alias + "@" + System.identityHashCode(context.getThread());
            this.perThreadAlias = true;
        } else {
            // Unknown mode (older JMX, hand-edited value) - preserve the historical fallback
            // of embedding the raw mode string in the alias so different unknown-mode values
            // still don't collide with each other.
            this.alias = alias + "@" + mode;
            this.perThreadAlias = false;
        }
        // The Sequential row-selection mode used to force per-thread aliasing here regardless
        // of the user's Sharing Mode choice, silently overriding "All threads"/"Current thread
        // group" and breaking the classic partition-across-threads pattern. It's now honored.
    }

    @Override
    public void threadStarted() {
        // Initialization happens lazily on the first iteration via initVars().
    }

    @Override
    public void threadFinished() {
        // Only release the file entry when this thread is its sole owner - for shared aliases
        // ("All threads"/"Current thread group") other threads may still be reading it.
        if (perThreadAlias && alias != null) {
            try {
                FileServerExtended.getFileServer().releaseFile(alias);
            } catch (IOException e) {
                LOGGER.error("Exception occurred while releasing file for alias {} : {}", alias, e.toString());
            }
        }
    }

    private boolean isServerMode() {
        return System.getProperty("server_port") != null;
    }

    @Override
    public void testStarted() {
        FileServerExtended fileServer = FileServerExtended.getFileServer();
        String baseDirectory = org.apache.jmeter.services.FileServer.getFileServer().getBaseDir();

        if(JMeter.isNonGUI() || isServerMode() || GuiPackage.getInstance() == null){
            fileServer.setBasedir(baseDirectory);
        } else {
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
        return String.format("Filename: %s\n FileEncoding: %s\n VariableName: %s\n IgnoreFirstLine: %s\n Delimiter: %s\n IsQuotedData: %s\n SelectRow: %s\n UpdateValue: %s\n OOValue: %s\n AutoAllocate: %s\n Allocate: %s\n BlockSize: %s\n",getFilename(),getFileEncoding(),getVariableNames(),isIgnoreFirstLine(),getDelimiter(),isQuotedData(),getSelectRow(),getUpdateValue(),getOoValue(),isAutoAllocate(),isAllocate(),getBlockSize());
    }
}
