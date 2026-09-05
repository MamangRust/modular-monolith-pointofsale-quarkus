#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Start all Java services LOCALLY (java -jar) against the infra containers
# (postgres, pgbouncer, kafka, redis cluster, observability) which must be
# running via:
#   docker compose -f deployments/local/docker-compose.yml up -d \
#     postgres pgbouncer kafka redis-node-1 ... redis-node-6 \
#     jaeger otel-collector prometheus grafana loki alertmanager ...
#
# Requires: mvn clean package -DskipTests (run once, or use --package).
#
# Usage:
#   ./e2e/start-local.sh            # start all services (background, logs in e2e/logs/)
#   ./e2e/start-local.sh --package  # run mvn package first
#   ./e2e/start-local.sh stop       # stop all local JVMs
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
LOGDIR="$ROOT/e2e/logs"
mkdir -p "$LOGDIR"

# ── infra endpoints ─────────────────────────────────────────────────────────
export DB_HOST="${DB_HOST:-localhost}"
export DB_PORT="${DB_PORT:-5432}"
export DB_USERNAME="${DB_USERNAME:-DRAGON}"
export DB_PASSWORD="${DB_PASSWORD:-DRAGON}"
export DB_USER="${DB_USER:-DRAGON}"
export DB_PASS="${DB_PASS:-DRAGON}"
export DB_NAME="${DB_NAME:-POINT_OF_SALE}"
export REDIS_HOSTS="${REDIS_HOSTS:-redis://:dragon_knight@localhost:6379}"
export QUARKUS_REDIS_HOSTS="$REDIS_HOSTS"
export QUARKUS_REDIS_CLIENT_TYPE="cluster"
export KAFKA_BROKERS="${KAFKA_BROKERS:-localhost:9092}"
export OTEL_ENDPOINT="${OTEL_ENDPOINT:-localhost:4317}"
export APP_ENV="local"

# gRPC client host/port mapping (same as docker.env) — services talk to each
# other on localhost since all run on this host.
export AUTH_HOST=localhost AUTH_GRPC_PORT=9012
export USER_HOST=localhost USER_GRPC_PORT=9011
export USER_SERVICE_HOST=localhost USER_SERVICE_GRPC_PORT=9011
export ROLE_HOST=localhost ROLE_GRPC_PORT=9006
export MERCHANT_HOST=localhost MERCHANT_GRPC_PORT=9005
export TRANSACTION_HOST=localhost TRANSACTION_GRPC_PORT=9009
export CASHIER_HOST=localhost CASHIER_GRPC_PORT=9014
export CATEGORY_HOST=localhost CATEGORY_GRPC_PORT=9015
export PRODUCT_HOST=localhost PRODUCT_GRPC_PORT=9003
export ORDER_HOST=localhost ORDER_GRPC_PORT=9001
export ORDER_ITEM_HOST=localhost ORDER_ITEM_GRPC_PORT=9016

# ── commands ────────────────────────────────────────────────────────────────
stop() {
    echo "==> Stopping local JVMs..."
    pkill -f 'quarkus-run.jar' 2>/dev/null || true
    pkill -f 'target/quarkus-app' 2>/dev/null || true
    echo "==> Stopped."
    exit 0
}
[ "${1:-}" = "stop" ] && stop
[ "${1:-}" = "--package" ] && { echo "==> Packaging..."; mvn clean package -DskipTests -q; }

# ── JVM helper ──────────────────────────────────────────────────────────────
# start <name> <http-port> <java-args...>
start() {
    local name="$1"; shift
    local http_port="$1"; shift
    local jar="$ROOT/$name/target/quarkus-app/quarkus-run.jar"
    if [ ! -f "$jar" ]; then
        echo "!! $name: jar not found, run: mvn clean package -DskipTests"; return
    fi
    echo "==> Starting $name (http:$http_port)"
    setsid nohup java -Xmx512m \
        -Dquarkus.http.port="$http_port" \
        "$@" \
        -jar "$jar" \
        > "$LOGDIR/$name.log" 2>&1 < /dev/null &
    disown 2>/dev/null || true
    echo "$!" >> "$LOGDIR/pids"
}

rm -f "$LOGDIR/pids"

# ── start services (order matters: leaves first, gateway last) ─────────────
# HTTP ports: auth/user/role/merchant/email keep their configured ones, the
# rest get unique ports to avoid collisions (they're gRPC-first services).
start user      8091
start role      8086
start merchant  8085
start category  8087
start product   8088
start cashier   8089
start order_item 8093
start order     8094
start transaction 8095
start auth      8092
start email-service 8098
start gateway   5000

echo ""
echo "==> All services launched. Logs: $LOGDIR/"
echo "==> Gateway: http://localhost:5000  (wait ~60-90s for full startup)"
echo "==> Watch:   tail -f $LOGDIR/gateway.log"
echo "==> Stop:    $0 stop"
