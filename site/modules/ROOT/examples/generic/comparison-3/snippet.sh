roadrunner run -c 5 -n 200 -s runs/baseline vm --sleep-time 10
# the current run is five times slower
roadrunner run -c 5 -n 200 -s runs/current vm --sleep-time 50

# a breached tolerance makes roadrunner exit with code 3: in CI just run the diff,
# here the exit code is captured so the example can check it
status=0
roadrunner diff runs/baseline runs/current --tolerance 'p99:10%,throughput:5%,errors:0.5%' || status=$?
test "$status" -eq 3
