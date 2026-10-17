roadrunner run -c 10 -n 100 --parameters-source csv:file=ids.csv \
  http 'GET("http://localhost:8080/api/orders/${id}")'
