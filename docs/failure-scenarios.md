# Failure scenarios
Candidate exit; health failure; wrong port; DB unreachable; wrong DB hostname; missing env var; wrong network; port conflict; bad Git credentials; wrong branch; missing image tag; HTTP 500. Evidence commands: docker ps -a, docker logs, docker port, docker inspect, docker network inspect, git branch --show-current, git rev-parse HEAD, docker images orders-api.
