# Incident Investigation
Capture Jenkins build/branch/SHA, console output, docker ps -a, candidate logs, Config.Env, port mappings, network inspect, host health, in-container connectivity and actual listener port before fixing.
Worked investigation 1: wrong candidate port -> docker port shows mismatch -> health fails -> remove candidate only -> current remains.
Worked investigation 2: wrong DB hostname -> /db-check fails -> inspect datasource URL/network DNS -> remove candidate only -> current remains.
Recovery rule: current -> candidate -> validation -> PASS switch/remove old; FAIL remove candidate/keep current.
