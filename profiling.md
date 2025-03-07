export JDK_JAVA_OPTIONS="-Xlog:gc*,metaspace*,safepoint:file=gc.log -Djdk.tracePinnedThreads=full -XX:+AlwaysPreTouch -XX:StartFlightRecording=disk=true,filename=memory-tracking.jfr,dumponexit=true,settings=profile"

export JDK_JAVA_OPTIONS="-agentpath:/home/jarek/tools/async-profiler-3.0-c1ed9b3-linux-x64/lib/libasyncProfiler.so=start,event=cpu,threads=true,file=profile.html"

