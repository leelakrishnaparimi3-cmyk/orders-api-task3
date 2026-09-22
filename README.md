# orders-api Task 3
BLUE orders-blue:8081; GREEN orders-green:8082; production traffic via nginx orders-proxy:8080; DB orders-db on orders-network; volume orders-db-data.
Create Jenkins secret credential ID `orders-db-password`. Pipeline stages cover checkout, validation, tests, Docker build/image validation, candidate start, container/health/DB validation, traffic switch, cleanup, verification and rollback.
