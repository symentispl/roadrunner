roadrunner run -c 5 -n 200 -s runs/baseline vm --sleep-time 10

# the second run prints a comparison against the first instead of its own report
roadrunner run -c 5 -n 200 -s runs/current --baseline runs/baseline vm --sleep-time 10
