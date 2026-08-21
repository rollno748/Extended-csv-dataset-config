# Extended CSV Data Set Config

A JMeter plugin that gives you more control over how CSV test data is fed
into your samplers. It behaves like JMeter's built in **CSV Data Set Config**
but adds three things you usually have to work around in real load tests:

1. Row selection modes: pick the next row **Sequentially**, at **Random**, or
   as part of a **Unique** block assigned to each thread.
2. Update strategies: refresh the CSV variables on **Every Iteration** or
   **Once** per thread.
3. End of data handling: **Continue Cyclic** (wrap to the top),
   **Continue with Last Value** (keep reusing the last row), or
   **Abort Thread** (stop the thread cleanly).

If you have used LoadRunner parameters, this is the closest match inside
JMeter.

## Preview

![Extended CSV Dataset Config](/images/ExtendedCsvDataSetConfig.png)

## Requirements

* JMeter 5.2.1 or above (the plugin is compiled against 5.1.1 for maximum
  binary compatibility, and runs fine on 5.6.x).
* Java 8 or above.

The plugin has no third party runtime dependencies beyond what JMeter itself
ships with.

## Install

1. Download the source from GitHub.
2. Run `mvn clean install` (Maven 3.x).
3. Copy `target/di-extended-csv-<version>.jar` into
   `<JMETER_HOME>/lib/ext/`.
4. Restart JMeter. You should see **Extended CSV Data Set Config** under
   `Add > Config Element`.

## Quick start

1. Add an **Extended CSV Data Set Config** element to your test plan (usually
   inside a Thread Group).
2. Point it at a CSV file. Use a relative path if you want the file to travel
   with the JMX (it will be resolved next to the JMX file).
3. Either leave **Variable Names** empty (the first line of the file is used
   as headers) or type comma separated variable names.
4. Pick a **Select Row**, **Update Values**, **When out of Values** and
   **Sharing Mode** for your scenario. See the option details below.
5. Reference the variables inside samplers with `${varName}` as usual.

## Options

### Select Row

How the plugin picks which row to hand out on each iteration.

* **Sequential**: read the file top to bottom. Combined with `All threads`
  sharing this gives you a classic collective partition (each thread grabs
  the next line, no duplicates).
* **Random**: pick a random row every iteration. Backed by an in memory byte
  offset index, so the pick is O(1) even on large files.
* **Unique**: divide the file into non overlapping blocks, one per thread,
  so each thread reads its own slice without duplicates across threads. See
  *Block allocation* below.

### Update Values

* **Each Iteration**: refresh the JMeter variables every iteration.
* **Once**: set the variables the first time and keep them for the rest of
  the thread's life.

### When out of Values

Applies when the plugin runs out of rows to serve (end of file for
Sequential, or end of block for Unique).

* **Continue Cyclic**: wrap around to the beginning. For Sequential this
  means the file recycles; for Unique the thread wraps to the start of its
  own block.
* **Continue with Last Value**: keep reusing the last row that was read.
  Downstream samplers keep seeing the same values for the rest of the run.
* **Abort Thread**: stop this thread cleanly with a clear "end of file"
  message. Other threads carry on.

Random mode does not use this setting (it never runs out of rows unless the
file is empty).

### Sharing Mode

Controls which threads share the same read cursor.

* **All threads**: every thread that references this CSV shares a single
  cursor. With Sequential row selection this is the classic partition
  pattern: each row goes to exactly one thread.
* **Current thread group**: threads inside the same Thread Group share a
  cursor. Different Thread Groups get independent cursors.
* **Current thread**: each thread has its own private cursor and starts
  from the top of the file.

### Block allocation (Unique mode only)

* **Automatically allocate block for threads**: the plugin divides the file
  into equal blocks based on the Thread Group's configured Number of
  Threads. Any leftover rows are distributed one at a time to the first few
  threads, so no rows are dropped.
* **Allocate N values for each thread**: fixed block size. Thread 0 reads
  rows `[0, N-1]`, thread 1 reads rows `[N, 2N-1]`, and so on.

## Combination table

Not every combination makes sense. Here's what the GUI supports:

| Select Row | Update Value   | Out of Values            | Block Allocation |
|------------|----------------|--------------------------|------------------|
| Sequential | Each Iteration | Continue Cyclic          | n/a              |
| Sequential | Each Iteration | Abort Thread             | n/a              |
| Sequential | Each Iteration | Continue with Last Value | n/a              |
| Sequential | Once           | n/a                      | n/a              |
| Random     | Each Iteration | n/a                      | n/a              |
| Random     | Once           | n/a                      | n/a              |
| Unique     | Each Iteration | Continue with Last Value | Enabled          |
| Unique     | Each Iteration | Continue Cyclic          | Enabled          |
| Unique     | Each Iteration | Abort Thread             | Enabled          |
| Unique     | Once           | n/a                      | n/a              |

## How it works under the hood

A few implementation details worth knowing:

* **Per file locking**. The plugin's file server keeps one entry per unique
  alias (based on filename plus Sharing Mode). Each entry has its own lock,
  so activity on one CSV file never blocks activity on another.
* **Indexed reads**. On the first iteration the plugin scans the file once
  to build a byte offset index of every line. Random and Unique reads seek
  directly to the requested row, so the cost of a read stays flat regardless
  of file size.
* **Per thread cleanup**. When a thread's Sharing Mode is *Current thread*,
  the plugin releases that thread's file handle as soon as the thread
  finishes rather than at test end. This matters when you have many short
  lived threads.
* **Independent CSV files stay independent**. Row counts and block positions
  are tracked per alias, so having two Extended CSV Data Set Config elements
  in the same test plan (with different files) will not clobber each other.

## Building from source

```sh
mvn clean verify
```

`verify` also runs the JUnit test suite (see `src/test/java`).
The final artefact lands in `target/di-extended-csv-<version>.jar`.

## Continuous integration

Every push and pull request runs `mvn verify` on JDK 8, 11, and 17 via
GitHub Actions (`.github/workflows/build.yml`).

## Known limitations

* **Unique + All threads across multiple Thread Groups**: block boundaries
  are computed per Thread Group, so a single file shared across two or more
  Thread Groups in Unique mode will produce overlapping blocks. Use one
  Extended CSV Data Set Config per Thread Group, or partition the input
  file up front.
* **Custom Thread Groups** (Ultimate Thread Group, Concurrency Thread Group,
  Stepping Thread Group, etc.): auto allocate Unique mode relies on the
  Thread Group's configured Number of Threads. Some custom groups do not
  expose that in the same way, in which case use manual block allocation.
* **CSV lines with embedded newlines inside quoted fields**: the parser
  assumes one logical row per physical line, matching JMeter's built in
  CSV Data Set Config. Preprocess the file if that is a problem.

## Roadmap

Ideas we would like to work on next. Contributions are welcome.

* Preview and edit the CSV inline inside the JMeter GUI.
* Simulate parameter window that lets you dry run the row picking logic
  without executing samplers.
* Per row filters (skip rows matching a predicate).
* Support for very large CSV files via streaming with a bounded in memory
  index.
* First class support for Ultimate / Concurrency Thread Groups in auto
  allocate Unique mode.

## References

* JMeter [CSV Data Set Config](https://jmeter.apache.org/usermanual/component_reference.html#CSV_Data_Set_Config)
* Original discussion and design notes: see the closed pull requests in this
  repository.

## Support

If this project saves you time and you want to say thanks:

[![paypal](https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif)](https://ko-fi.com/rollno748)

* Star the repo if you find it useful.
* Open an issue if something looks broken. Attaching a example mock JMX script would help heavily to pinpoint the issue faster.
