roadrunner run -c 20 -n 1000 neo4j \
  --uri <uri> \
  --username <username> \
  --password <password> \
  'query("MATCH (n:Person) RETURN n.name LIMIT 1")'
