Roadrunner introduction
=======================

Roadrunner is (or rather at the moment, will be) yet another Java performance testing framework/library/tool, this time using internal DSLs for building scenarios, with low-overhead, fine grained reporting 
and another lovely things that might come in the future (like distributed load testing, trend analisys, web app for tracing execution and so on).

Basically the idea is to build something fast to learn and work with but packed with "power features" and easy to extend. 

At the moment I am struggling with building internal API and support basic cases like HTTP GET, HTML assertions. I am more focused on building 
basic framework than on providing support for more sophisticated cases.

NOTE:
I am strongly inspired by funkload, locust.io and mechanize from Python universe. Still none of them provide all the futures I need when I am testing performance of
applications. I am writing my thoughts and ideas for Roadrunner at [this page](task/wiki/Ideas)

How to get started?
-------------------
Since this is still "ideas under construction" phase, there is no stable release. There are two ways to give it a try, you can either use snapshot version or build project from scratch.
Snapshot versions tend to be more stable, and better tested then latest code. I don't have any schedule to deploy snapshots, I do it whenever I feel they provide value.

You can find latest version at [this link](https://oss.sonatype.org/content/repositories/snapshots/pl/symentis/task/task-tools/0.0.1-SNAPSHOT/task-tools-0.0.1-20140125.202418-8-bin.zip).


If you choose to build if from source,you need to clone this repo, get Maven 3.x and JDK 7. Once you will have it on your hard drive, do simple mvn install, and you are "ready to rock".

NOTE:
API can change with each commit, until it reaches state of beauty I am looking for. I am giving you this framework so early to see if it catches your attention and of course
to get your feedback as soon as possible


How to write performance test scenario?
---------------------------------------

Create new project and import just single dependency, dependency to task-framework artifact (if you don't want to build it from source, don't forget to include SNAPSHOT repository):

	:::xml
	<dependencies>
		<dependency>
			<groupId>pl.symentis.task</groupId>
			<artifactId>task-framework</artifactId>
			<version>0.0.1-SNAPSHOT</version>
		</dependency>
	</dependencies>
	
	<repositories>
		<repository>
			<id>oss-sonatype-snapshots</id>
			<url>https://oss.sonatype.org/content/repositories/snapshots/</url>
			<snapshots>
				<enabled>true</enabled>
			</snapshots>
		</repository>
	</repositories>
	


Once you have task on your classpath, you can try to create your first scenario:

	:::java
	import static pl.symentis.task.http.api.HTTPEntities.form;
	import static pl.symentis.task.http.api.HTTPEntities.text;
	import static pl.symentis.task.http.api.HTTPEntities.parameter;
	import pl.symentis.task.BaseScenario;
	
	public class ExampleScenario extends BaseScenario {
	
		public ExampleScenario() {
			super("example");
		}
	
		@Override
		public void run() {
	
			http.path("/").get();
	
			http.path("/services/rest/").header("username", "someusername").get();
	
			http.path("/services/rest/").post(form(parameter("somefield", "somevalue")));
	
			http.path("/services/rest/").put(text("some text"));
			
		}	
	}
	
As you can see API is rather limited, but I am justing "planting seeds" :). I hope it is self explanatory :). If not it means I have failed.

How to run performance test scenario?
-------------------------------------

Once you have your scenario you can run it and check results.

Basically all is hidden in task/task-tools/target/appassembler/bin if you build from sources or in bin directory of tools zip which you downloaded from snapshot repository. 
Once you have Roadrunner, in this directory you will find two scripts, one named rr-run-test and second named rr-run-bench.

**rr-run-test**

You can use this script to test your scenario, it doesn't run performance tests, it is rather single pass of your scenario to check if it works.

	:::bash
	rr-run-test -cp [classpath with your test scenario] -l debug:summary -b http://google.com/ PerfTest

What are these mysterious switches?

* -cp, is a classpath where task will be looking for your performance test class
* -b, sets base URL you are going to use during test
* -l debug:summary, means that you want two listeners for this test, one which will output execution of the test to console (debug), and second which will print short summary after test, with min, max and average execution time
* PerfTest, is a name of you performance test class, you don't have to put here fully qualified name with package, task will scan your classpath to find class which matches name you passed as argument

Hey, why don't you give it a try?

**rr-run-bench**

This is where you can specify number of concurrent users and for how long you want to run your test.

	:::bash
	rr-run-bench -cp [classpath with your test scenario] -l xml(output.xml):summary -b http://google.com/ -C 20 -D 60 PerfTest
	
And we have here new options available:

* -C, sets concurrency level, in other words number of concurrent users
* -D, sets duriation of test case run
* -l xml(output.xml):summary, sets two listeners, one which writes execution of the scenario to output.xml file, and second which prints short summary on console after test execution

Remember to run both commands without options, to see usage details.

task bench http https://onet.pl

