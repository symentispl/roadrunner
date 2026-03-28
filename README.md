[![codecov](https://codecov.io/gh/symentispl/roadrunner/graph/badge.svg?token=37S96CL3YR)](https://codecov.io/gh/symentispl/roadrunner)

# Roadrunner: introduction

Roadrunner is (or rather at the moment, will be) yet another load generator tool, this time using Java with
low-overhead, fine-grained reporting and another lovely thing that might come in the future
(like distributed load testing, trend analysis, web app for tracing execution and so on).

Basically, the idea is to build something that is fast to learn and work with but packed with "power features"
and easy to extend.

# Motivation

Why not [JMeter](https://jmeter.apache.org/), [Gatling](https://gatling.io/),[locust.io](https://locust.io/) or [k6s](https://k6.io/)?

* because sometimes you need something that is really simple, that can generate load, and you don't need fancy scenarios and write code to generate load
* because most load generation tools target HTTP protocol and are built around HTTP protocol semantics, I wanted (and need something) that can test any protocol, so adding protocol implementations should be trivial
* in a continuous performance management it is crucial to make it easy to work with performance tests results, so reporting and processing of load generator results is key
* low-overhead, which doesn't need explanation
* as always, it is an opportunity to learn; this implementation is heavily using Java virtual threads, so expect some bumps down the road

# How to get started?

Build from source (you will need [JDK 25](https://adoptium.net/temurin/releases/?version=25)).
Build a project with:

    ./mvnw verify

NOTICE: I am at the moment only running and testing on Linux.

NOTICE: Binary builds will come, someday.

# How to run it

First let's use a simple sampler called `vm`. This is a testing/baseline protocol, which sleeps X ms per each request. I use it mostly for testing overhead of Roadrunner.

    ./roadrunner-preferences/target/maven-jlink/default/bin/roadrunner -c 50 -n 500 vm -sleep-time 10

This will execute 500 requests using 50 concurrent users, and each user will sleep for 10 ms per each request.

Now something more handy:

    ./roadrunner-preferences/target/maven-jlink/default/bin/roadrunner -c 50 -n 500 ab http://google.com/

This will execute 500 requests using 50 concurrent users, using HTTP protocol against the provided URL.

Why it is called `ab`? Because in the first iteration I want to provide the same command line syntax and behaviour as [Apache HTTP server benchmarking tool](https://httpd.apache.org/docs/2.4/programs/ab.html)
So this isn't going to be a full-blown HTTP load test. Rather drop in replacement for `ab` command (and I will use `ab` as baseline)

# Protocols

By now, you should grab the idea. Roadrunner is a framework for executing requests and simulating user distribution. It will not (at the moment, and probably never will) allow you to run complex load generation scenarios.
Just grab URL, query, request and allow Roadrunner to flood your system under test with requests.

NOTICE: At some point in the future, you will be able to provide a list of urls, queries and sets of parameters.

# Reporting

At the moment Roadrunner prints out a summary to console and generates a basic HTML report (there is still work to be done).

# Interesting reads

*  close-world model vs. open world models [Open Versus Closed: A Cautionary Tale](https://www.usenix.org/legacy/event/nsdi06/tech/full_papers/schroeder/schroeder.pdf)
* coordinated omission, if you are not familiar, this is the nice introduction, [On Coordinated Omission](https://www.scylladb.com/2021/04/22/on-coordinated-omission/)

# WARNING

all of the APIs are experimental, anything can change

# Roadmap

I am at the moment building a roadmap for [first release](https://github.com/orgs/symentispl/projects/1). So if you are looking to contribute, feel free to take a look at a list of tasks.