# Parallel File Downloader

A standalone Java tool that downloads a file over HTTP using **parallel range requests** and assembles it into a single
output file.

## Features

* Parallel chunk downloading via HTTP `Range` requests
* Retry mechanism with backoff for transient I/O failures
* Input validation/ unit tested

---

## Requirements

* Java 17+

---

## Build

```bash
./gradlew build
```

---

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

<details>
<summary>How it works?</summary>
A `HEAD` request is first sent to the server to retrieve metadata such as 
`Content-Length` and `Accept-Ranges`, and if range requests are not supported,
the downloader fails immediately.

The file is then divided into fixed-size chunks, where each chunk is defined
by a start and end byte range and is processed independently.

Multiple threads download these chunks in parallel by requesting specific byte
ranges using the `Range: bytes=start-end` header.

Each downloaded chunk is written directly to its correct position in the output
file using `FileChannel`, ensuring proper placement without sequential writing.

Writes are performed safely using positional writes, and partial writes are
handled by repeatedly writing until the entire buffer is fully persisted;
chunk boundaries and data length consistency are also validated.

If an `IOException` occurs, the system retries the operation using an
incremental backoff strategy implemented with `Thread.sleep(100L * attempts)`,
and it ultimately fails once a configurable maximum number of attempts is reached.

</details>

## Design Decisions

### Why `FileChannel`?

* Supports **random-access writes**
* Enables safe concurrent writes without explicit locking (non-overlapping ranges)

---

### Why Parallelism?

* Improves throughput for:

    * high-latency networks
    * large files
* Controlled via configurable thread pool

---

### Why Manual Chunking?

* Avoids relying on external libraries
* Gives deterministic control over:

    * memory usage
    * concurrency level

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

## Limitations / Possible Improvements

* No checksum validation (e.g., SHA-256)
* Fixed chunk size (could be adaptive)
* Blocking retry model (could be async/non-blocking)
* No download resume support
* No rate limiting or bandwidth control
