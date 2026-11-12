# two runs of the vm sampler: 200 requests, 5 at a time, 10 ms each
roadrunner run -c 5 -n 200 -s runs/baseline vm --sleep-time 10
roadrunner run -c 5 -n 200 -s runs/current vm --sleep-time 10

roadrunner diff runs/baseline runs/current
