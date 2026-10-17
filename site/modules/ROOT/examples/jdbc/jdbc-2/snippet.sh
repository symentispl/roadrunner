roadrunner run -c 20 -n 1000 jdbc \
  --url <jdbc_url> \
  --username <username> \
  --password <password> \
  --driver <driver> \
  'query("SELECT id, name FROM users WHERE id = 1")'
