# Architecture
Git/main -> Jenkins -> Maven -> immutable Docker image -> BLUE/GREEN candidate -> health + DB validation -> nginx traffic switch on :8080 -> old cleanup. Both app containers and DB use orders-network; DB uses orders-db-data.
