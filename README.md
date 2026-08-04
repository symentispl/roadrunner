[![codecov](https://codecov.io/gh/symentispl/roadrunner/graph/badge.svg?token=37S96CL3YR)](https://codecov.io/gh/symentispl/roadrunner)

# Roadrunner

**Roadrunner** is a high-performance Java load generator built on **Java Virtual Threads** (JDK 25). It provides fine-grained, low-overhead performance measurements and extensible protocol samplers (HTTP, JDBC, Neo4j, VM baseline, and custom protocols).

---

## 🚀 Why Roadrunner? (vs. JMeter, Gatling, Locust, k6)

* **Protocol Agnostic:** Most load testing tools are designed exclusively around HTTP semantics. Roadrunner provides a lightweight SPI framework so adding custom protocol samplers (HTTP, SQL/JDBC, Graph/Neo4j, gRPC, custom TCP) is simple and fast.
* **Low Overhead via Virtual Threads:** Uses Java 25 virtual threads to scale concurrency effortlessly with minimal CPU/memory footprint—eliminating OS thread pool bottlenecks.
* **No Scenario Bloat:** Designed for rapid, straightforward load generation when you don't need complex script scenarios or heavy GUI bloat.
* **Accurate Latency & Reporting:** Focuses on continuous performance management, open-world load models, and coordinated-omission-corrected latency reporting.

---

## 💻 Installation

### 1. JBang Launcher (Fastest)

Run directly using [JBang](https://www.jbang.dev/) without manual installation:

```bash
jbang roadrunner@symentispl -- run vm -n 500 -c 50 --sleep-time 10
```

### 2. Pre-built Release Archive

Download pre-packaged releases from [GitHub Releases](https://github.com/symentispl/roadrunner/releases):

1. Extract the downloaded release archive (`.zip`).
2. Run the executable from `bin/roadrunner`:
   ```bash
   ./bin/roadrunner run vm -n 500 -c 50 --sleep-time 10
   ```

### 3. Build from Source

**Prerequisites:** [JDK 25](https://adoptium.net/temurin/releases/?version=25) and Maven Wrapper (included).

```bash
./mvnw verify
```

The runnable application is generated under:
`roadrunner-app/target/jreleaser/assemble/roadrunner/jlink/roadrunner-app-<version>/bin/roadrunner`

---

## 🧪 Working First Run

Roadrunner commands use the `run` subcommand followed by the sampler name and parameters:

### Baseline Test (`vm` Sampler)
Test internal overhead using a dummy sampler that sleeps fixed milliseconds per request:

```bash
roadrunner run vm -n 500 -c 50 --sleep-time 10
```
*(Executes 500 total requests across 50 concurrent virtual threads, sleeping 10ms per request).*

### HTTP Test (`ab` Sampler)
Run a high-throughput HTTP benchmark against a target endpoint:

```bash
roadrunner run ab -n 500 -c 50 http://localhost:8080/
```
*(The `ab` sampler offers ApacheBench-compatible command-line semantics for benchmarking HTTP endpoints).*

---

## 📚 Documentation

For detailed configuration guides, parameter sources, sampler references, and reporting options, visit our official documentation site:

👉 **[https://symentispl.github.io/roadrunner](https://symentispl.github.io/roadrunner)**

---

## ⚠️ Platform Support & Status

* **JDK Requirement:** Requires **JDK 25** (utilizing Java Virtual Threads and modern JVM features).
* **Platform Testing:** Roadrunner is primarily developed and tested on **Linux**.
* **API Stability:** APIs are evolving as we move toward full release stability.

---

## 🗺️ Roadmap & Contributing

Looking to contribute or track progress? View our release milestones and upcoming tasks on the [Roadrunner GitHub Project Roadmap](https://github.com/orgs/symentispl/projects/1).