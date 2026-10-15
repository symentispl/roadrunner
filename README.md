[![codecov](https://codecov.io/gh/symentispl/roadrunner/graph/badge.svg?token=37S96CL3YR)](https://codecov.io/gh/symentispl/roadrunner)

# Roadrunner

Roadrunner is a command-line load generator for exercising services and measuring their performance. It uses Java virtual threads (JDK 25) to model concurrent users, and includes HTTP, JDBC, Neo4j, and VM samplers, with an extension API for additional samplers.

## Why Roadrunner?

Roadrunner is a good fit when you want a lightweight CLI, virtual-thread concurrency, and a Java API for extending the supported protocols. Compared with established tools such as JMeter, Gatling, Locust, and k6, Roadrunner focuses on straightforward load generation and extensibility rather than a broad ecosystem of scenario-authoring tools. If you depend on a particular tool's scenario language, integrations, or existing workflows, that tool may be a better fit.

## Install

### Run with JBang

Install [JBang](https://www.jbang.dev/) and run Roadrunner directly:

```bash
jbang roadrunner@symentispl/roadrunner -- run vm -n 100 -c 10 --sleep-time 10
```

### Download a release archive

Choose an archive from [GitHub Releases](https://github.com/symentispl/roadrunner/releases). The `roadrunner` jlink archive bundles its Java runtime; the `roadrunner-jars` archive requires JDK 25.

Extract the archive, change into the extracted directory, and run:

```bash
./bin/roadrunner run vm -n 100 -c 10 --sleep-time 10
```

### Build from source

Install [JDK 25](https://adoptium.net/temurin/releases/?version=25), then build with the included Maven Wrapper:

```bash
./mvnw verify
```

The release archives are assembled under `roadrunner-app/target/jreleaser/assemble/`.

## First run

The `vm` sampler is a simple way to try Roadrunner without a target service. This command runs 100 requests at a concurrency of 10, sleeping 10 milliseconds per request:

```bash
jbang roadrunner@symentispl/roadrunner -- run vm -n 100 -c 10 --sleep-time 10
```

Roadrunner commands use `run` followed by a sampler name and its options. See the [documentation site](https://symentispl.github.io/roadrunner) for HTTP examples, configuration, sampler references, and reporting.

## Platform support

Roadrunner targets JDK 25. JBang, the `roadrunner-jars` archive, and source builds use JDK 25; the jlink archive bundles its runtime. Linux is tested; other platforms have not been verified.

## Roadmap

See the [Roadrunner project roadmap](https://github.com/orgs/symentispl/projects/1) for planned work and progress.
