# Build project

Compile and run all tests with:

    mise run

Compile and run roadrunner with VM sampler:

    mise roadrunner -- run -n 1000 -c 10 vm --sleep-time 10

Compile and run roadrunner with HTTP sampler:

    mise roadrunner -- run -n 1000 -c 10 http 'GET("https://example.com/")'
