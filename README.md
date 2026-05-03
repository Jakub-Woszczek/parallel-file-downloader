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

Run docker server (from which you download file):

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

- The next major improvement would be implementing **wrapper around retry logic** that comes up couple times in I/O
  actions. Due to short deadline I decided to ship reliable code but I deem it 'to improve'.
- Another improvement I considered would be to implement threads as `executorService` for reliable scaling.
- For even better and more reliable testing some functional tests (apart from unit and behavioral) would make software
  better, eg using `WireMock` or `MockServer`.

*Sophisticated:*

- Dynamic tuning `chunkSize`/`threadsPerCore` based on heap usage/network bandwidth saturation/file size 