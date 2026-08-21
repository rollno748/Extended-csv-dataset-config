package com.di.jmeter.utils;

import org.apache.jorphan.util.JMeterStopThreadException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Regression tests for {@link FileServerExtended}. Each test uses a unique alias (UUID-based)
 * against the singleton file server, so tests don't interfere even though the singleton's
 * state persists across the JUnit run.
 */
public class FileServerExtendedTest {

    private FileServerExtended server;
    private File tempFile;
    private String alias;

    @Before
    public void setUp() throws IOException {
        server = FileServerExtended.getFileServer();
        tempFile = File.createTempFile("csv-fst-", ".csv");
        alias = "test-" + UUID.randomUUID();
    }

    @After
    public void tearDown() {
        try {
            server.releaseFile(alias);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
        if (tempFile != null && tempFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
        }
    }

    private void writeFile(String content) throws IOException {
        Files.write(tempFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------
    // csvReadLine (CSV parser)
    // ------------------------------------------------------------------

    @Test
    public void csvReadLine_simpleFields() throws IOException {
        assertArrayEquals(new String[]{"a", "b", "c"},
                server.csvReadLine("a,b,c", ','));
    }

    @Test
    public void csvReadLine_quotedFields() throws IOException {
        assertArrayEquals(new String[]{"hello", "world"},
                server.csvReadLine("\"hello\",\"world\"", ','));
    }

    @Test
    public void csvReadLine_quotedFieldWithDelimiterInside() throws IOException {
        assertArrayEquals(new String[]{"a,b", "c"},
                server.csvReadLine("\"a,b\",c", ','));
    }

    @Test
    public void csvReadLine_doubledQuoteEscape() throws IOException {
        assertArrayEquals(new String[]{"say \"hi\"", "bye"},
                server.csvReadLine("\"say \"\"hi\"\"\",bye", ','));
    }

    @Test
    public void csvReadLine_emptyMiddleField() throws IOException {
        assertArrayEquals(new String[]{"a", "", "c"},
                server.csvReadLine("a,,c", ','));
    }

    @Test
    public void csvReadLine_trailingEmptyField() throws IOException {
        assertArrayEquals(new String[]{"a", "b", ""},
                server.csvReadLine("a,b,", ','));
    }

    @Test(expected = IOException.class)
    public void csvReadLine_missingTrailingQuoteThrows() throws IOException {
        server.csvReadLine("\"unterminated,field", ',');
    }

    // ------------------------------------------------------------------
    // calculateRowCount + readIndexed (fixes G, I, empty-file clamp)
    // ------------------------------------------------------------------

    @Test
    public void calculateRowCount_ignoreHeader() throws IOException {
        writeFile("header\nrow1\nrow2\nrow3\n");
        server.reserveFile(tempFile.getAbsolutePath(), null, alias, false);
        server.calculateRowCount(alias, true);
        assertEquals(3, server.getRowCount(alias));
    }

    @Test
    public void calculateRowCount_noHeader() throws IOException {
        writeFile("row1\nrow2\nrow3\n");
        server.reserveFile(tempFile.getAbsolutePath(), null, alias, false);
        server.calculateRowCount(alias, false);
        assertEquals(3, server.getRowCount(alias));
    }

    @Test
    public void calculateRowCount_emptyFileClampsToZero() throws IOException {
        writeFile("");
        server.reserveFile(tempFile.getAbsolutePath(), null, alias, false);
        server.calculateRowCount(alias, true);
        // Empty file with ignoreFirstLine=true used to produce rowCount=-1; must clamp to 0.
        assertEquals(0, server.getRowCount(alias));
    }

    @Test(expected = IOException.class)
    public void calculateRowCount_ioErrorPropagates() throws IOException {
        // Fix G: I/O failure must no longer be silently swallowed. Reserving happens first,
        // then we delete the underlying file so the row-count scan hits FileNotFoundException.
        writeFile("row1\n");
        server.reserveFile(tempFile.getAbsolutePath(), null, alias, false);
        assertTrue("test setup: could not delete the file", tempFile.delete());
        server.calculateRowCount(alias, false);
    }

    // ------------------------------------------------------------------
    // readRandom (fix B: empty-file guard, plus off-by-one on last row)
    // ------------------------------------------------------------------

    @Test
    public void readRandom_emptyFile_returnsNullNotCrash() throws IOException {
        writeFile("");
        server.reserveFile(tempFile.getAbsolutePath(), null, alias, false);
        server.calculateRowCount(alias, true);
        // Previously threw IllegalArgumentException from ThreadLocalRandom.nextInt.
        assertNull(server.readRandom(alias, true));
    }

    @Test
    public void readRandom_headerOnly_returnsNull() throws IOException {
        writeFile("only-header\n");
        server.reserveFile(tempFile.getAbsolutePath(), null, alias, false);
        server.calculateRowCount(alias, true);
        assertNull(server.readRandom(alias, true));
    }

    @Test
    public void readRandom_samplesAllRowsIncludingTheLast() throws IOException {
        // Guards against the latent off-by-one where the last data row was never sampled
        // when ignoreFirstLine=true. With 3 data rows and 200 uniform draws, P(missing any
        // one row) ≈ (2/3)^200 ≈ 10^-35 - safe.
        writeFile("header\nrow1\nrow2\nrow3\n");
        server.reserveFile(tempFile.getAbsolutePath(), null, alias, false);
        server.calculateRowCount(alias, true);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String row = server.readRandom(alias, true);
            assertNotNull(row);
            seen.add(row);
        }
        assertTrue("row1 never picked", seen.contains("row1"));
        assertTrue("row2 never picked", seen.contains("row2"));
        assertTrue("row3 never picked (was off-by-one before fix B)", seen.contains("row3"));
        assertFalse("header should never be sampled", seen.contains("header"));
    }

    // ------------------------------------------------------------------
    // readUnique boundaries (fix A: no over-read on Abort Thread)
    // ------------------------------------------------------------------

    @Test(expected = JMeterStopThreadException.class)
    public void readUnique_abortThread_throwsBeforeReadingPastEndPos() throws IOException {
        writeFile("row0\nrow1\nrow2\nrow3\nrow4\n");
        server.reserveFile(tempFile.getAbsolutePath(), null, alias, false);
        server.calculateRowCount(alias, false);
        // Block = [1,2]. currPos = 3 is already past the block, so Abort Thread must throw
        // immediately without reading row 3 (which belongs to the next thread's block).
        server.readUnique(alias, false, "Abort Thread", 3, 1, 2);
    }

    @Test
    public void readUnique_abortThread_readsExactlyItsBlock() throws IOException {
        writeFile("row0\nrow1\nrow2\nrow3\nrow4\n");
        server.reserveFile(tempFile.getAbsolutePath(), null, alias, false);
        server.calculateRowCount(alias, false);
        FileServerExtended.setReadPos(alias, 1);

        String r1 = server.readUnique(alias, false, "Abort Thread", 1, 1, 2);
        int after1 = FileServerExtended.getReadPos(alias);
        String r2 = server.readUnique(alias, false, "Abort Thread", after1, 1, 2);
        int after2 = FileServerExtended.getReadPos(alias);

        assertEquals("row1", r1);
        assertEquals("row2", r2);
        assertEquals(2, after1);
        assertEquals(3, after2);
        try {
            server.readUnique(alias, false, "Abort Thread", after2, 1, 2);
            fail("Expected JMeterStopThreadException");
        } catch (JMeterStopThreadException expected) {
            // pass - abort fires before touching row 3
        }
    }

    @Test
    public void readUnique_continueWithLastValue_staysOnEndPos() throws IOException {
        writeFile("row0\nrow1\nrow2\nrow3\nrow4\n");
        server.reserveFile(tempFile.getAbsolutePath(), null, alias, false);
        server.calculateRowCount(alias, false);
        FileServerExtended.setReadPos(alias, 2);

        String r = server.readUnique(alias, false, "Continue with Last Value", 2, 1, 2);
        assertEquals("row2", r);
        assertEquals("readPos must stay at endPos to keep repeating it",
                2, FileServerExtended.getReadPos(alias));

        // Repeated calls should keep returning the same last row.
        String r2 = server.readUnique(alias, false, "Continue with Last Value",
                FileServerExtended.getReadPos(alias), 1, 2);
        assertEquals("row2", r2);
    }

    @Test
    public void readUnique_continueCyclic_wrapsAtEndPos() throws IOException {
        writeFile("row0\nrow1\nrow2\nrow3\nrow4\n");
        server.reserveFile(tempFile.getAbsolutePath(), null, alias, false);
        server.calculateRowCount(alias, false);
        FileServerExtended.setReadPos(alias, 2);

        String r = server.readUnique(alias, false, "Continue Cyclic", 2, 1, 2);
        assertEquals("row2", r);
        assertEquals("Cyclic must wrap back to startPos", 1, FileServerExtended.getReadPos(alias));
    }

    // ------------------------------------------------------------------
    // readLine (Sequential mode baseline + EOF semantics)
    // ------------------------------------------------------------------

    @Test
    public void readLine_returnsNullAtEofWithoutRecycle() throws IOException {
        writeFile("a\nb\n");
        server.reserveFile(tempFile.getAbsolutePath(), null, alias, false);
        assertEquals("a", server.readLine(alias, false, false));
        assertEquals("b", server.readLine(alias, false, false));
        assertNull("Third call must return null (EOF), not throw", server.readLine(alias, false, false));
    }

    @Test
    public void readLine_recyclesAtEof() throws IOException {
        writeFile("a\nb\n");
        server.reserveFile(tempFile.getAbsolutePath(), null, alias, false);
        assertEquals("a", server.readLine(alias, true, false));
        assertEquals("b", server.readLine(alias, true, false));
        assertEquals("Recycle must restart from first line",
                "a", server.readLine(alias, true, false));
    }

    // ------------------------------------------------------------------
    // releaseFile lifecycle (fix 7 – per-thread cleanup)
    // ------------------------------------------------------------------

    @Test
    public void releaseFile_closesEntryAndRemovesFromMap() throws IOException {
        writeFile("row1\nrow2\n");
        server.reserveFile(tempFile.getAbsolutePath(), null, alias, false);
        assertEquals("row1", server.readLine(alias, false, false));
        server.releaseFile(alias);
        try {
            server.readLine(alias, false, false);
            fail("Expected IOException after releaseFile");
        } catch (IOException expected) {
            assertTrue("Error should mention the missing reservation",
                    expected.getMessage() != null && expected.getMessage().contains("never reserved"));
        }
    }

    // ------------------------------------------------------------------
    // Per-alias state (fix 1 – rowCount + positions no longer global)
    // ------------------------------------------------------------------

    @Test
    public void perAlias_rowCountsDoNotClobberEachOther() throws IOException {
        File smallFile = File.createTempFile("csv-small-", ".csv");
        File largeFile = File.createTempFile("csv-large-", ".csv");
        String aliasA = "a-" + UUID.randomUUID();
        String aliasB = "b-" + UUID.randomUUID();
        try {
            Files.write(smallFile.toPath(), "x\ny\n".getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                sb.append("row").append(i).append('\n');
            }
            Files.write(largeFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));

            server.reserveFile(smallFile.getAbsolutePath(), null, aliasA, false);
            server.reserveFile(largeFile.getAbsolutePath(), null, aliasB, false);
            server.calculateRowCount(aliasA, false);
            server.calculateRowCount(aliasB, false);

            assertEquals("aliasA row count should be per-file", 2, server.getRowCount(aliasA));
            assertEquals("aliasB row count should be per-file", 50, server.getRowCount(aliasB));

            // Interleave the order; if rowCount was global it would flip depending on
            // whichever calculateRowCount ran last.
            server.calculateRowCount(aliasA, false);
            assertEquals("aliasB row count must be untouched by recomputing aliasA",
                    50, server.getRowCount(aliasB));
        } finally {
            try { server.releaseFile(aliasA); } catch (IOException ignored) {}
            try { server.releaseFile(aliasB); } catch (IOException ignored) {}
            //noinspection ResultOfMethodCallIgnored
            smallFile.delete();
            //noinspection ResultOfMethodCallIgnored
            largeFile.delete();
        }
    }

    @Test
    public void perAlias_uniquePositionsAreIndependent() {
        String aliasA = "posA-" + UUID.randomUUID();
        String aliasB = "posB-" + UUID.randomUUID();
        FileServerExtended.setStartPos(aliasA, 10);
        FileServerExtended.setEndPos(aliasA, 20);
        FileServerExtended.setReadPos(aliasA, 15);

        FileServerExtended.setStartPos(aliasB, 100);
        FileServerExtended.setEndPos(aliasB, 200);
        FileServerExtended.setReadPos(aliasB, 150);

        assertEquals(10, FileServerExtended.getStartPos(aliasA));
        assertEquals(20, FileServerExtended.getEndPos(aliasA));
        assertEquals(15, FileServerExtended.getReadPos(aliasA));
        assertEquals(100, FileServerExtended.getStartPos(aliasB));
        assertEquals(200, FileServerExtended.getEndPos(aliasB));
        assertEquals(150, FileServerExtended.getReadPos(aliasB));
    }
}
