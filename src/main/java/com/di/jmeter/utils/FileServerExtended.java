/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.di.jmeter.utils;

import org.apache.commons.io.input.BOMInputStream;
import org.apache.jmeter.gui.JMeterFileFilter;
import org.apache.jmeter.save.CSVSaveService;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.util.JMeterStopThreadException;
import org.apache.jorphan.util.JOrphanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class provides thread-safe access to files, and to
 * provide some simplifying assumptions about where to find files and how to
 * name them. For instance, putting supporting files in the same directory as
 * the saved test plan file allows users to refer to the file with just it's
 * name - this FileServer class will find the file without a problem.
 * Eventually, I want all in-test file access to be done through here, with the
 * goal of packaging up entire test plans as a directory structure that can be
 * sent via rmi to remote servers (currently, one must make sure the remote
 * server has all support files in a relative-same location) and to package up
 * test plans to execute on unknown boxes that only have Java installed.
 */
public class FileServerExtended {

    private static final Logger log = LoggerFactory.getLogger(FileServerExtended.class);

    /**
     * The default base used for resolving relative files, i.e.<br/>
     * {@code System.getProperty("user.dir")}
     */
    private static final String DEFAULT_BASE = System.getProperty("user.dir");// $NON-NLS-1$

    /** Default base prefix: {@value} */
    private static final String BASE_PREFIX_DEFAULT = "~/"; // $NON-NLS-1$

    private static final String BASE_PREFIX =
            JMeterUtils.getPropDefault("jmeter.save.saveservice.base_prefix", // $NON-NLS-1$
                    BASE_PREFIX_DEFAULT);

    // volatile: previously only ever read/written while holding the (now removed) whole-instance
    // lock on every file operation; per-file locking no longer provides that incidental protection.
    private volatile File base;
    // Positions are tracked per alias (not just per thread) so that a single thread using
    // more than one CSV Data Set Config (each with its own alias) does not have one dataset's
    // block/read position overwrite another's.
    private static final ThreadLocal<Map<String, Positions>> positionsByAlias = ThreadLocal.withInitial(HashMap::new);
    // ConcurrentHashMap so that reading/reserving one alias never blocks on activity for another,
    // and reservation of a brand-new alias (computeIfAbsent) is atomic without a whole-map lock.
    private final Map<String, FileEntry> files = new ConcurrentHashMap<>();
    private static final FileServerExtended server = new FileServerExtended();

    // volatile needed to ensure safe publication
    private volatile String scriptName;

    // Cannot be instantiated
    private FileServerExtended() {
        base = new File(DEFAULT_BASE);
        log.info("Default base='{}'", DEFAULT_BASE);
    }

    /**
     * @return the singleton instance of the server.
     */
    public static FileServerExtended getFileServer() {
        return server;
    }

    /**
     * Resets the current base to DEFAULT_BASE.
     */
    public synchronized void resetBase() {
        checkForOpenFiles();
        base = new File(DEFAULT_BASE);
        log.info("Reset base to '{}'", base);
    }

    /**
     * Sets the current base directory for relative file names from the provided path.
     * If the path does not refer to an existing directory, then its parent is used.
     * Normally the provided path is a file, so using the parent directory is appropriate.
     *
     * @param basedir the path to set, or {@code null} if the GUI is being cleared
     * @throws IllegalStateException if files are still open
     */
    public synchronized void setBasedir(String basedir) {
        checkForOpenFiles(); // TODO should this be called if basedir == null?
        if (basedir != null) {
            File newBase = new File(basedir);
            if (!newBase.isDirectory()) {
                newBase = newBase.getParentFile();
            }
            base = newBase;
            log.info("Set new base='{}'", base);
        }
    }

    /**
     * Sets the current base directory for relative file names from the provided script file.
     * The parameter is assumed to be the path to a JMX file, so the base directory is derived
     * from its parent.
     *
     * @param scriptPath the path of the script file; must be not be {@code null}
     * @throws IllegalStateException if files are still open
     * @throws IllegalArgumentException if scriptPath parameter is null
     */
    public synchronized void setBaseForScript(File scriptPath) {
        if (scriptPath == null){
            throw new IllegalArgumentException("scriptPath must not be null");
        }
        setScriptName(scriptPath.getName());
        // getParentFile() may not work on relative paths
        setBase(scriptPath.getAbsoluteFile().getParentFile());
    }

    /**
     * Sets the current base directory for relative file names.
     *
     * @param jmxBase the path of the script file base directory, cannot be null
     * @throws IllegalStateException if files are still open
     * @throws IllegalArgumentException if {@code basepath} is null
     */
    public synchronized void setBase(File jmxBase) {
        if (jmxBase == null) {
            throw new IllegalArgumentException("jmxBase must not be null");
        }
        checkForOpenFiles();
        base = jmxBase;
        log.info("Set new base='{}'", base);
    }

    /**
     * Check if there are entries in use.
     * <p>
     * Caller must ensure that access to the files map is single-threaded as
     * there is a window between checking the files Map and clearing it.
     *
     * @throws IllegalStateException if there are any entries still in use
     */
    private void checkForOpenFiles() throws IllegalStateException {
        if (filesOpen()) { // checks for entries in use
            throw new IllegalStateException("Files are still open, cannot change base directory");
        }
        files.clear(); // tidy up any unused entries
    }

    public synchronized String getBaseDir() {
        return base.getAbsolutePath();
    }

    public static String getDefaultBase(){
        return DEFAULT_BASE;
    }

    /**
     * Calculates the relative path from DEFAULT_BASE to the current base,
     * which must be the same as or a child of the default.
     *
     * @return the relative path, or {@code "."} if the path cannot be determined
     */
    public synchronized File getBaseDirRelative() {
        // Must first convert to absolute path names to ensure parents are available
        File parent = new File(DEFAULT_BASE).getAbsoluteFile();
        File f = base.getAbsoluteFile();
        ArrayDeque<String> l = new ArrayDeque<>();
        while (f != null) {
            if (f.equals(parent)){
                if (l.isEmpty()){
                    break;
                }
                File rel = new File(l.pop());
                while(!l.isEmpty()) {
                    rel = new File(rel, l.pop());
                }
                return rel;
            }
            l.push(f.getName());
            f = f.getParentFile();
        }
        return new File(".");
    }

    /**
     * Creates an association between a filename and a File inputOutputObject,
     * and stores it for later use - unless it is already stored.
     *
     * @param filename - relative (to base) or absolute file name (must not be null)
     */
    public void reserveFile(String filename) {
        reserveFile(filename,null);
    }

    /**
     * Creates an association between a filename and a File inputOutputObject,
     * and stores it for later use - unless it is already stored.
     *
     * @param filename - relative (to base) or absolute file name (must not be null)
     * @param charsetName - the character set encoding to use for the file (perhaps null)
     */
    public void reserveFile(String filename, String charsetName) {
        reserveFile(filename, charsetName, filename, false);
    }

    /**
     * Creates an association between a filename and a File inputOutputObject,
     * and stores it for later use - unless it is already stored.
     *
     * @param filename - relative (to base) or absolute file name (must not be null)
     * @param charsetName - the character set encoding to use for the file (perhaps null)
     * @param alias - the name to be used to access the object (must not be null)
     */
    public void reserveFile(String filename, String charsetName, String alias) {
        reserveFile(filename, charsetName, alias, false);
    }

    /**
     * Creates an association between a filename and a File inputOutputObject,
     * and stores it for later use - unless it is already stored.
     *
     * @param filename - relative (to base) or absolute file name (must not be null or empty)
     * @param charsetName - the character set encoding to use for the file (perhaps null)
     * @param alias - the name to be used to access the object (must not be null)
     * @param hasHeader true if the file has a header line describing the contents
     * @return the header line; may be null
     * @throws IllegalArgumentException if header could not be read or filename is null or empty
     */
    public String reserveFile(String filename, String charsetName, String alias, boolean hasHeader) {
        if (filename == null || filename.isEmpty()){
            throw new IllegalArgumentException("Filename must not be null or empty");
        }
        if (alias == null){
            throw new IllegalArgumentException("Alias must not be null");
        }
        // computeIfAbsent makes create-if-missing atomic per alias: concurrent threads reserving
        // the SAME new alias cannot both create/insert a FileEntry, and threads reserving
        // DIFFERENT aliases are not blocked on each other (no whole-map lock is held).
        FileEntry fileEntry = files.computeIfAbsent(alias, key -> {
            FileEntry newEntry = new FileEntry(resolveFileFromPath(filename), null, charsetName);
            if (filename.equals(key)){
                log.info("Stored: {}", filename);
            } else {
                log.info("Stored: {} Alias: {}", filename, key);
            }
            if (hasHeader) {
                try {
                    newEntry.headerLine = readLineFromEntry(newEntry, false, false);
                    if (newEntry.headerLine == null) {
                        newEntry.exception = new EOFException("File is empty: " + newEntry.file);
                    }
                } catch (IOException | IllegalArgumentException e) {
                    newEntry.exception = e;
                }
            }
            return newEntry;
        });
        if (hasHeader && fileEntry.headerLine == null) {
            throw new IllegalArgumentException("Could not read file header line for file " + filename,
                    fileEntry.exception);
        }
        return fileEntry.headerLine;
    }

    /**
     * Resolves file name into {@link File} instance.
     * When filename is not absolute and not found from current working dir,
     * it tries to find it under current base directory
     * @param filename original file name
     * @return {@link File} instance
     */
    private File resolveFileFromPath(String filename) {
        File f = new File(filename);
        if (f.isAbsolute() || f.exists()) {
            return f;
        } else {
            return new File(base, filename);
        }
    }

    /**
     * Get the next line of the named file, recycle by default.
     *
     * @param filename the filename or alias that was used to reserve the file
     * @return String containing the next line in the file
     * @throws IOException when reading of the file fails, or the file was not reserved properly
     */
    public String readLine(String filename) throws IOException {
        return readLine(filename, true);
    }

    /**
     * Get the next line of the named file, first line is name to false
     *
     * @param filename the filename or alias that was used to reserve the file
     * @param recycle - should file be restarted at EOF?
     * @return String containing the next line in the file (null if EOF reached and not recycle)
     * @throws IOException when reading of the file fails, or the file was not reserved properly
     */
    public String readLine(String filename, boolean recycle) throws IOException {
        return readLine(filename, recycle, false);
    }

    /**
     * Get the next line of the named file
     *
     * @param filename the filename or alias that was used to reserve the file
     * @param recycle - should file be restarted at EOF?
     * @param ignoreFirstLine - Ignore first line
     * @return String containing the next line in the file (null if EOF reached and not recycle)
     * @throws IOException when reading of the file fails, or the file was not reserved properly
     */
    public String readLine(String filename, boolean recycle,
                                        boolean ignoreFirstLine) throws IOException {
        FileEntry fileEntry = files.get(filename);
        if (fileEntry == null) {
            throw new IOException("File never reserved: "+filename);
        }
        return readLineFromEntry(fileEntry, recycle, ignoreFirstLine);
    }

    /**
     * Reads the next line directly from a {@link FileEntry}, locking only that entry so that
     * concurrent reads of unrelated files/aliases are not blocked.
     */
    private String readLineFromEntry(FileEntry fileEntry, boolean recycle, boolean ignoreFirstLine) throws IOException {
        fileEntry.lock.lock();
        try {
            if (fileEntry.inputOutputObject == null) {
                fileEntry.inputOutputObject = createBufferedReader(fileEntry);
            } else if (!(fileEntry.inputOutputObject instanceof Reader)) {
                throw new IOException("File " + fileEntry.file.getName() + " already in use");
            }
            BufferedReader reader = (BufferedReader) fileEntry.inputOutputObject;
            String line = reader.readLine();
            if (line == null && recycle) {
                reader.close();
                reader = createBufferedReader(fileEntry);
                fileEntry.inputOutputObject = reader;
                if (ignoreFirstLine) {
                    // read first line and forget
                    reader.readLine();//NOSONAR
                }
                line = reader.readLine();
            }
            return line;
        } finally {
            fileEntry.lock.unlock();
        }
    }

    /**
     *
     * @param alias the file name or alias
     * @param ignoreFirstLine whether the file contains a file header which will be ignored
     * @param delim the delimiter to use for parsing
     * @return the parsed line, will be empty if the file is at EOF
     * @throws IOException when reading of the aliased file fails, or the file was not reserved properly
     */
    public String[] getParsedLine(String alias, boolean recycle, boolean ignoreFirstLine, char delim) throws IOException {
        FileEntry fileEntry = files.get(alias);
        if (fileEntry == null) {
            throw new IOException("File never reserved: "+alias);
        }
        fileEntry.lock.lock();
        try {
            BufferedReader reader = getReader(fileEntry, alias, recycle, ignoreFirstLine);
            return CSVSaveService.csvReadFile(reader, delim);
        } finally {
            fileEntry.lock.unlock();
        }
    }

    /**
     * Return BufferedReader handling close if EOF reached and recycle is true
     * and ignoring first line if ignoreFirstLine is true.
     * Caller must hold {@code fileEntry.lock}.
     *
     * @param fileEntry       the reserved file entry
     * @param alias           String alias (used only for error messages)
     * @param recycle         Recycle at eof
     * @param ignoreFirstLine Ignore first line
     * @return {@link BufferedReader}
     */
    private BufferedReader getReader(FileEntry fileEntry, String alias, boolean recycle, boolean ignoreFirstLine) throws IOException {
        BufferedReader reader;
        if (fileEntry.inputOutputObject == null) {
            reader = createBufferedReader(fileEntry);
            fileEntry.inputOutputObject = reader;
            if (ignoreFirstLine) {
                // read first line and forget
                reader.readLine(); //NOSONAR
            }
        } else if (!(fileEntry.inputOutputObject instanceof Reader)) {
            throw new IOException("File " + alias + " already in use");
        } else {
            reader = (BufferedReader) fileEntry.inputOutputObject;
            if (recycle) { // need to check if we are at EOF already
                reader.mark(1);
                int peek = reader.read();
                if (peek == -1) { // already at EOF
                    reader.close();
                    reader = createBufferedReader(fileEntry);
                    fileEntry.inputOutputObject = reader;
                    if (ignoreFirstLine) {
                        // read first line and forget
                        reader.readLine(); //NOSONAR
                    }
                } else { // OK, we still have some data, restore it
                    reader.reset();
                }
            }
        }
        return reader;
    }

    private BufferedReader createBufferedReader(FileEntry fileEntry) throws IOException {
        if (!fileEntry.file.canRead() || !fileEntry.file.isFile()) {
            throw new IllegalArgumentException("File "+ fileEntry.file.getName()+ " must exist and be readable");
        }
        BOMInputStream fis = new BOMInputStream(Files.newInputStream(fileEntry.file.toPath())); //NOSONAR
        InputStreamReader isr = null;
        // If file encoding is specified, read using that encoding, otherwise use default platform encoding
        String charsetName = fileEntry.charSetEncoding;
        if(!JOrphanUtils.isBlank(charsetName)) {
            isr = new InputStreamReader(fis, charsetName);
        } else if (fis.hasBOM()) {
            isr = new InputStreamReader(fis, fis.getBOM().getCharsetName());
        } else {
            @SuppressWarnings("DefaultCharset")
            final InputStreamReader withPlatformEncoding = new InputStreamReader(fis);
            isr = withPlatformEncoding;
        }
        return new BufferedReader(isr);
    }

    public void write(String filename, String value) throws IOException {
        FileEntry fileEntry = files.get(filename);
        if (fileEntry == null) {
            throw new IOException("File never reserved: "+filename);
        }
        fileEntry.lock.lock();
        try {
            if (fileEntry.inputOutputObject == null) {
                fileEntry.inputOutputObject = createBufferedWriter(fileEntry);
            } else if (!(fileEntry.inputOutputObject instanceof Writer)) {
                throw new IOException("File " + filename + " already in use");
            }
            BufferedWriter writer = (BufferedWriter) fileEntry.inputOutputObject;
            log.debug("Write:{}", value);
            writer.write(value);
        } finally {
            fileEntry.lock.unlock();
        }
    }

    private BufferedWriter createBufferedWriter(FileEntry fileEntry) throws IOException {
        OutputStream fos = Files.newOutputStream(fileEntry.file.toPath());
        OutputStreamWriter osw;
        // If file encoding is specified, write using that encoding, otherwise use default platform encoding
        String charsetName = fileEntry.charSetEncoding;
        if(!JOrphanUtils.isBlank(charsetName)) {
            osw = new OutputStreamWriter(fos, charsetName);
        } else {
            @SuppressWarnings("DefaultCharset")
            final OutputStreamWriter withPlatformEncoding = new OutputStreamWriter(fos);
            osw = withPlatformEncoding;
        }
        return new BufferedWriter(osw);
    }

    public synchronized void closeFiles() throws IOException {
        for (Map.Entry<String, FileEntry> me : files.entrySet()) {
            closeFile(me.getKey(),me.getValue() );
        }
        files.clear();
    }

    /**
     * @param name the name or alias of the file to be closed
     * @throws IOException when closing of the aliased file fails
     */
    public synchronized void closeFile(String name) throws IOException {
        FileEntry fileEntry = files.get(name);
        closeFile(name, fileEntry);
    }

    private void closeFile(String name, FileEntry fileEntry) throws IOException {
        if (fileEntry == null) {
            return;
        }
        fileEntry.lock.lock();
        try {
            if (fileEntry.inputOutputObject != null) {
                log.info("Close: {}", name);
                fileEntry.inputOutputObject.close();
                fileEntry.inputOutputObject = null;
            }
            if (fileEntry.randomAccessFile != null) {
                fileEntry.randomAccessFile.close();
                fileEntry.randomAccessFile = null;
            }
        } finally {
            fileEntry.lock.unlock();
        }
    }

    /**
     * Closes and removes the file entry for an alias, reclaiming it immediately rather than
     * waiting for {@link #closeFiles()} at test end. Intended for aliases that are exclusively
     * owned by a single thread (e.g. "Current thread" sharing mode, or Sequential row selection
     * which is always thread-exclusive) so callers must be sure no other thread can still be
     * using the alias before calling this.
     *
     * @param alias the alias to release
     * @throws IOException when closing of the aliased file fails
     */
    public void releaseFile(String alias) throws IOException {
        FileEntry fileEntry = files.remove(alias);
        if (fileEntry == null) {
            return;
        }
        fileEntry.lock.lock();
        try {
            if (fileEntry.inputOutputObject != null) {
                log.info("Close: {}", alias);
                fileEntry.inputOutputObject.close();
                fileEntry.inputOutputObject = null;
            }
            if (fileEntry.randomAccessFile != null) {
                fileEntry.randomAccessFile.close();
                fileEntry.randomAccessFile = null;
            }
        } finally {
            fileEntry.lock.unlock();
        }
    }

    boolean filesOpen() { // package access for test code only
        return files.values().stream()
                .anyMatch(fileEntry -> fileEntry.inputOutputObject != null);
    }

    /**
     * Method will get a random file in a base directory
     * <p>
     * TODO hey, not sure this method belongs here.
     * FileServer is for thread safe File access relative to current test's base directory.
     *
     * @param basedir    name of the directory in which the files can be found
     * @param extensions array of allowed extensions, if <code>null</code> is given,
     *                   any file be allowed
     * @return a random File from the <code>basedir</code> that matches one of
     * the extensions
     */
    public File getRandomFile(String basedir, String[] extensions) {
        File input = null;
        if (basedir != null) {
            File src = new File(basedir);
            File[] lfiles = src.listFiles(new JMeterFileFilter(extensions));
            if (lfiles != null) {
                // lfiles cannot be null as it has been checked before
                int count = lfiles.length;
                input = lfiles[ThreadLocalRandom.current().nextInt(count)];
            }
        }
        return input;
    }

    /**
     * Get {@link File} instance for provided file path,
     * resolve file location relative to base dir or script dir when needed
     *
     * @param path original path to file, maybe relative
     * @return {@link File} instance
     */
    public File getResolvedFile(String path) {
        reserveFile(path);
        return files.get(path).file;
    }

    private static class FileEntry{
        private String headerLine;
        private Throwable exception;
        private final File file;
        private Closeable inputOutputObject;
        private final String charSetEncoding;
        // Row count is specific to this file/alias, so it must not be shared with other files.
        // volatile: today every access goes through `lock`, but marking these volatile means a
        // future refactor that reads them lock-free (e.g. hoisting the row-count check out of
        // the critical section for a further contention win) will still see a properly
        // published, coherent value.
        private volatile int rowCount;
        // Byte offset of the start of every line in the file, built once by calculateRowCount().
        // Lets random/unique reads seek directly to a line instead of rescanning from the start.
        private volatile long[] lineOffsets;
        // Cached RandomAccessFile used by readIndexed() so we don't open+close a file handle
        // on every random/unique read - opened lazily on the first indexed read, closed by
        // closeFile()/releaseFile() alongside inputOutputObject. Guarded by `lock`.
        private RandomAccessFile randomAccessFile;
        // Guards this entry's mutable state (inputOutputObject/rowCount/lineOffsets/headerLine).
        // Per-entry (rather than a single server-wide lock) so activity on one file/alias never
        // blocks activity on another.
        private final ReentrantLock lock = new ReentrantLock();

        FileEntry(File f, Closeable o, String e) {
            file = f;
            inputOutputObject = o;
            charSetEncoding = e;
        }
    }

    /**
     * Holds the "Unique" row-selection block position (start/end/current) for a single
     * alias, on a single thread. Kept out of {@link FileEntry} because these positions are
     * per-thread, whereas a {@link FileEntry} can be shared by many threads.
     */
    private static class Positions {
        private int readPos;
        private int startPos;
        private int endPos;
    }

    /**
     * Resolve a file name that may be relative to the base directory. If the
     * name begins with the value of the JMeter property
     * "jmeter.save.saveservice.base_prefix" - default "~/" - then the name is
     * assumed to be relative to the basename.
     *
     * @param relativeName
     *            filename that should be checked for
     *            <code>jmeter.save.saveservice.base_prefix</code>
     * @return the updated filename
     */
    public static String resolveBaseRelativeName(String relativeName) {
        if (relativeName.startsWith(BASE_PREFIX)){
            String newName = relativeName.substring(BASE_PREFIX.length());
            return new File(getFileServer().getBaseDir(),newName).getAbsolutePath();
        }
        return relativeName;
    }

    /**
     * @return JMX Script name
     * @since 2.6
     */
    public String getScriptName() {
        return scriptName;
    }

    /**
     * @param scriptName Script name
     * @since 2.6
     */
    public void setScriptName(String scriptName) {
        this.scriptName = scriptName;
    }

    /**
     * Get the number of rows count for the named file
     *
     * @param filename the filename or alias that was used to reserve the file
     * @param ignoreFirstLine Consider first line as variable name ?
     * @return String containing the next line in the file
     * @throws IOException when reading of the file fails, or the file was not reserved properly
     */
    public void calculateRowCount(String filename, boolean ignoreFirstLine) throws IOException {
        FileEntry fileEntry = files.get(filename);
        if (fileEntry == null) {
            throw new IllegalArgumentException("File never reserved: " + filename);
        }
        fileEntry.lock.lock();
        try {
            // Single pass over the file, recording the byte offset of every line so that
            // readIndexed() can later seek straight to any line instead of rescanning from
            // the start of the file on every random/unique read.
            // Any I/O failure here (bad permissions, disk error, missing file) is propagated
            // instead of swallowed - previously it silently left the index partially built,
            // producing later "End of file" errors on random reads with no diagnosable cause.
            List<Long> offsets = new ArrayList<>();
            try (RandomAccessFile raf = new RandomAccessFile(fileEntry.file, "r")) {
                long pos = raf.getFilePointer();
                while (raf.readLine() != null) {
                    offsets.add(pos);
                    pos = raf.getFilePointer();
                }
            }
            long[] lineOffsets = new long[offsets.size()];
            for (int i = 0; i < offsets.size(); i++) {
                lineOffsets[i] = offsets.get(i);
            }
            fileEntry.lineOffsets = lineOffsets;
            // Math.max guards against an empty file with ignoreFirstLine=true producing -1.
            fileEntry.rowCount = Math.max(0, ignoreFirstLine ? lineOffsets.length - 1 : lineOffsets.length);
        } finally {
            fileEntry.lock.unlock();
        }
    }

    /**
     * Get the random line using index of the named file
     *
     * @param filename the filename or alias that was used to reserve the file
     * @param ignoreFirstLine Consider first line as variable name ?
     * @return String containing the next line in the file
     * @throws IOException when reading of the file fails, or the file was not reserved properly
     */
    public String readRandom(String filename, boolean ignoreFirstLine) throws IOException {
        FileEntry fileEntry = files.get(filename);
        if (fileEntry == null) {
            throw new IOException("File never reserved: "+filename);
        }
        fileEntry.lock.lock();
        try {
            // rowCount is already the number of DATA rows (header excluded when
            // ignoreFirstLine=true). Valid absolute line indices for data are
            // [startOffset, startOffset + rowCount - 1], so nextInt(rowCount) + startOffset.
            // Guard against empty / header-only files (rowCount == 0) which would otherwise
            // crash ThreadLocalRandom.nextInt with IllegalArgumentException, and also fix a
            // latent off-by-one in the original formula that never picked the last row when
            // ignoreFirstLine was true.
            if (fileEntry.rowCount <= 0) {
                return null;
            }
            int startOffset = ignoreFirstLine ? 1 : 0;
            int randPos = ThreadLocalRandom.current().nextInt(fileEntry.rowCount) + startOffset;
            return readIndexed(fileEntry, randPos);
        } finally {
            fileEntry.lock.unlock();
        }
    }

    /**
     * Get the indexed line of the named file according to thread specific (Thread local)
     *
     * @param filename the filename or alias that was used to reserve the file
     * @param ignoreFirstLine Consider first line as variable name?
     * @param ooValue Out of value handler (recycle/abort thread/ Continue with last used value)
     * @param currPos - position in the allocated block (Read position - specific to thread local)
     * @param startPos - Starting position in the allocated block (start position - specific to thread local)
     * @param endPos - Ending position in the allocated block (End position - specific to thread local)
     * @return String containing the next line in the file
     * @throws IOException when reading of the file fails, or the file was not reserved properly
     */
    public String readUnique(String filename, boolean ignoreFirstLine, String ooValue, int currPos, int startPos, int endPos) throws IOException {
        FileEntry fileEntry = files.get(filename);
        if (fileEntry == null) {
            throw new IOException("File never reserved: "+filename);
        }
        // For "Abort Thread", check BEFORE reading. The previous logic read at currPos first
        // and only then checked whether we were already past endPos, which meant every thread
        // read one row belonging to the next thread's block before aborting — producing
        // duplicated rows across threads in multi-thread Unique mode.
        if (ooValue.equalsIgnoreCase("Abort Thread") && currPos > endPos) {
            throw new JMeterStopThreadException("End of Block :" + filename + " detected for Extended CSV DataSet:"
                    + filename + " configured with stopThread: " + ooValue);
        }
        String line = null;
        fileEntry.lock.lock();
        try {
            if (currPos < fileEntry.rowCount && currPos <= endPos) {
                line = readIndexed(fileEntry, currPos);
            }
        } finally {
            fileEntry.lock.unlock();
        }
        if(ooValue.equalsIgnoreCase("Continue Cyclic")){
            if(currPos >= endPos){
                setReadPos(filename, startPos);
            }else {
                setReadPos(filename, currPos + 1);
            }
        }else if(ooValue.equalsIgnoreCase("Abort Thread")){
            setReadPos(filename, currPos + 1);
        }else{
            // "Continue with Last Value": stay on endPos once the block is exhausted.
            if(currPos >= endPos){
                setReadPos(filename, currPos);
            }else{
                setReadPos(filename, currPos + 1);
            }
        }
        return line;
    }

    /**
     * Get the indexed line of a file, using the byte-offset index built by
     * {@link #calculateRowCount(String, boolean)} to seek directly to the line instead of
     * scanning the file from the start. Caller must hold {@code fileEntry.lock} and must have
     * already called {@code calculateRowCount} for this entry.
     *
     * @param fileEntry the reserved file entry
     * @param pos - line number to fetch from the file (starts from 0)
     * @return String containing the requested line, or {@code null} if pos is out of range
     * @throws IOException when reading of the file fails
     */
    private String readIndexed(FileEntry fileEntry, int pos) throws IOException {
        long[] offsets = fileEntry.lineOffsets;
        if (offsets == null || pos < 0 || pos >= offsets.length) {
            return null;
        }
        // Lazily open a cached RandomAccessFile once per entry (safe because the caller holds
        // fileEntry.lock, which serialises all indexed reads on this entry). Closed by
        // closeFile()/releaseFile().
        if (fileEntry.randomAccessFile == null) {
            fileEntry.randomAccessFile = new RandomAccessFile(fileEntry.file, "r");
        }
        RandomAccessFile raf = fileEntry.randomAccessFile;
        raf.seek(offsets[pos]);
        String rawLine = raf.readLine();
        if (rawLine == null) {
            return null;
        }
        // RandomAccessFile.readLine() decodes bytes as ISO-8859-1 (no charset support), so
        // recover the original bytes and decode them with the configured charset, matching
        // the encoding-handling used for sequential reads in createBufferedReader().
        byte[] rawBytes = rawLine.getBytes(StandardCharsets.ISO_8859_1);
        String charsetName = fileEntry.charSetEncoding;
        if (!JOrphanUtils.isBlank(charsetName)) {
            return new String(rawBytes, charsetName);
        }
        @SuppressWarnings("DefaultCharset")
        final String withPlatformEncoding = new String(rawBytes);
        return withPlatformEncoding;
    }

    private enum ParserState {
        INITIAL,
        PLAIN,
        QUOTED,
        EMBEDDEDQUOTE
    }
    private static final char QUOTING_CHAR = '"';

    private static boolean isDelimOrEOL(char delim, int ch) {
        return ch == delim || ch == '\n' || ch == '\r';
    }

    /**
     * Reads from file and splits input into strings according to the delimiter,
     * taking note of quoted strings.
     * <p>
     * Handles DOS (CRLF), Unix (LF), and Mac (CR) line-endings equally.
     * <p>
     * A blank line - or a quoted blank line - both return an array containing
     * a single empty String.
     * @param line String
     * @param delimiter delimiter (e.g. comma)
     * @return array of strings, will be empty if there is no data, i.e. if the input is at EOF.
     * @throws IOException
     *             also for unexpected quote characters
     */
    public String[] csvReadLine(String line, char delimiter) throws IOException {
        int index = 0;
        int length = line.length();
        ParserState state = ParserState.INITIAL;
        List<String> list = new ArrayList<>();
        CharArrayWriter baos = new CharArrayWriter(200);
        boolean push = false;
        while (index < length) {
            push = false;
            int ch = line.charAt(index++);
            switch (state) {
                case INITIAL:
                    if (ch == QUOTING_CHAR) {
                        state = ParserState.QUOTED;
                    } else if (isDelimOrEOL(delimiter, ch)) {
                        push = true;
                    } else {
                        baos.write(ch);
                        state = ParserState.PLAIN;
                    }
                    break;
                case PLAIN:
                    if (ch == QUOTING_CHAR) {
                        baos.write(ch);
                        throw new IOException("Cannot have quote-char in plain field:[" + baos.toString() + "]");
                    } else if (isDelimOrEOL(delimiter, ch)) {
                        push = true;
                        state = ParserState.INITIAL;
                    } else {
                        baos.write(ch);
                    }
                    break;
                case QUOTED:
                    if (ch == QUOTING_CHAR) {
                        state = ParserState.EMBEDDEDQUOTE;
                    } else {
                        baos.write(ch);
                    }
                    break;
                case EMBEDDEDQUOTE:
                    if (ch == QUOTING_CHAR) {
                        baos.write(QUOTING_CHAR); // doubled quote => quote
                        state = ParserState.QUOTED;
                    } else if (isDelimOrEOL(delimiter, ch)) {
                        push = true;
                        state = ParserState.INITIAL;
                    } else {
                        baos.write(QUOTING_CHAR);
                        throw new IOException("Cannot have single quote-char in quoted field:[" + baos.toString() + "]");
                    }
                    break;
                default:
                    throw new IllegalStateException("Unexpected state " + state);
            }
            if (push) {
                if (ch == '\r' && index < length && line.charAt(index) == '\n') {
                    index++; // skip the '\n' character after '\r'
                }
                String s = baos.toString();
                list.add(s);
                baos.reset();
            }
        }
        if (state == ParserState.QUOTED) {
            throw new IOException("Missing trailing quote-char in quoted field:[\"" + baos.toString() + "\"]");
        }
        // Do we have some data, or a trailing empty field?
        if (baos.size() > 0 // we have some data
                || push // we've started a field
                || state == ParserState.EMBEDDEDQUOTE // Just seen ""
        ) {
            list.add(baos.toString());
        }
        return list.toArray(new String[list.size()]);
    }

    /**
     * @param alias the alias that was used to reserve the file
     * @return the row count previously computed for this alias's file, or 0 if not yet calculated
     */
    public int getRowCount(String alias) {
        FileEntry fileEntry = files.get(alias);
        if (fileEntry == null) {
            return 0;
        }
        fileEntry.lock.lock();
        try {
            return fileEntry.rowCount;
        } finally {
            fileEntry.lock.unlock();
        }
    }

    private static Positions getPositions(String alias) {
        return positionsByAlias.get().computeIfAbsent(alias, k -> new Positions());
    }

    public static int getEndPos(String alias) {
        return getPositions(alias).endPos;
    }
    public static void setEndPos(String alias, int ep) {
        getPositions(alias).endPos = ep;
    }
    public static int getStartPos(String alias) {
        return getPositions(alias).startPos;
    }
    public static void setStartPos(String alias, int sp) {
        getPositions(alias).startPos = sp;
    }
    public static int getReadPos(String alias) {
        return getPositions(alias).readPos;
    }
    public static void setReadPos(String alias, int rp) {
        getPositions(alias).readPos = rp;
    }
}
