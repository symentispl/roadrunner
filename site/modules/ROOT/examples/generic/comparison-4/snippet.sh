roadrunner run -c 5 -n 200 -s runs/baseline vm --sleep-time 10
roadrunner run -c 5 -n 200 -s runs/current vm --sleep-time 10

roadrunner diff runs/baseline runs/current -r console:nocolors=true,ascii=true
