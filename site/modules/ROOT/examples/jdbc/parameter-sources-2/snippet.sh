roadrunner run -c 10 -n 100 --parameters-source csv:file=queries.csv jdbc \
  --url <jdbc_url> \
  --username <username> --password <password> \
  --driver <driver> \
  'query("SELECT * FROM users WHERE id = ?")'
