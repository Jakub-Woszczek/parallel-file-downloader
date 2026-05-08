# Parallel File Downloader

A standalone Java tool that downloads a file over HTTP using **parallel range requests** and assembles it into a single
output file.

<details>
<summary>How it works?</summary>

A `HEAD` request is first sent to the server to retrieve metadata such as
`Content-Length` and `Accept-Ranges`, and if range requests are not supported,
the downloader fails immediately.

Orchestrator precomputes chunk ranges and run workers who download specific
chunks in parallel by requesting byte
ranges using the `Range: bytes=start-end` header.

Each downloaded chunk is written directly to its correct position in the output
file using `FileChannel`, ensuring proper placement without sequential writing.

If an `IOException` occurs, the system retries the operation using an
incremental backoff strategy,
and it ultimately fails once a configurable maximum number of attempts is reached.

</details>

## Implementation Highlights

* **Fault Tolerance with Retries**
  Built-in retry mechanisms with exponential backoff and jitter are applied to:

    * HTTP requests
    * File writes,file truncation (preallocation for efficient random access)

* **Memory-Efficient File Writing**
  Uses `FileChannel` with positional writes, allowing concurrent writes directly to the correct file offsets without
  loading the entire file into memory.

* **Data Integrity Validation**
  Each downloaded chunk is validated against expected byte range and size

* number of worker threads is adjusted based on available CPU cores and the number of chunks, preventing
  unnecessary thread overhead.

* code supports HTTP Range requests and **validates server responses** (e.g., `Content-Range`,
  `Content-Length`), ensuring correctness and protocol compliance.

* lock-based mechanism guarantees that each chunk is assigned exactly once, avoiding race conditions and duplicate
  work.

## Requirements

* Java 17+

## Build

```bash
./gradlew build
```

## Run

Compile project

```bash
javac -d out $(find src/main/java -name "*.java")
```

Run docker server (from which you download file), you may need to have *docker desktop* turned on:

```bash
docker run --rm -p 8080:80 -v /path/to/your/local/directory:/usr/local/apache2/htdocs/ httpd:latest
```

Execute download:

```bash
java -cp out downloader.Main <url> <output-file>
# Example: 
# http://localhost:8080/t_1gb.dat src/test/resources/save/savefile.dat
```

## Testing

```bash
./gradlew test
```

## Assumptions

* Server supports:

    * `Accept-Ranges: bytes`
    * `Content-Length`
* File does not change during download

---

## Possible Improvements

* Introduce a reusable **retry abstraction** for I/O operations. The retry logic currently appears in multiple places,
  and extracting it into a dedicated wrapper would improve maintainability and reduce duplication. Due to time
  constraints, the focus was on delivering a reliable implementation, but this is a clear candidate for refactoring.

* Replace manual thread management with an `ExecutorService` to provide more robust and scalable thread handling.

* Expand test coverage with **functional/integration tests** (in addition to unit and behavioral tests), for example
  using tools like `WireMock` or `MockServer`, to better simulate real HTTP interactions.

*Sophisticated*

* Implement **dynamic tuning** of `chunkSize` and `threadsPerCore` based on runtime conditions such as heap usage,
  network bandwidth saturation, and file size.
