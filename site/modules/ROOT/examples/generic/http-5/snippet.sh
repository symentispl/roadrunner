roadrunner run -c 10 -n 100 --parameters-source csv:file=payloads.csv \
  http 'POST("http://localhost:8080/api/orders", "@payload")'
