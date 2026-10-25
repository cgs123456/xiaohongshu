#!/usr/bin/env bash
# 全栈启动脚本：凭据统一来源项目根目录 .env（与 docker compose --env-file 共用）
# 用法: bash scripts/start-all.sh
set -u
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "缺少 .env（参考 .env.example）"; exit 1
fi
set -a; source .env; set +a
export DB_USERNAME="${DB_USERNAME:-root}"
export DB_PASSWORD="${DB_PASSWORD:-$MYSQL_ROOT_PASSWORD}"
export REDIS_PASSWORD
export RABBITMQ_USERNAME="${RABBITMQ_USER:-admin}"
export RABBITMQ_PASSWORD
export MONGO_URI
export ES_PASSWORD
export INTERNAL_TOKEN
export JWT_SECRET_KEY
export EMBEDDING_ENABLED="${EMBEDDING_ENABLED:-false}"

mkdir -p .run/logs

start() {
  local name=$1 jar=$2 port=$3
  if netstat -an | grep -q ":$port .*LISTEN"; then
    echo "$name already up (port $port)"; return
  fi
  echo "starting $name ..."
  java -Xms128m -Xmx320m -jar "$jar" > ".run/logs/$name.log" 2>&1 &
  echo $! > ".run/$name.pid"
}

start user    redbook-service/redbook-service-user/target/redbook-service-user-1.0-SNAPSHOT.jar 8080
start note    redbook-service/redbook-service-note/target/redbook-service-note-1.0-SNAPSHOT.jar 8085
start product redbook-service/redbook-service-product/target/redbook-service-product-1.0-SNAPSHOT.jar 8095
start order   redbook-service/redbook-service-order/target/redbook-service-order-1.0-SNAPSHOT.jar 8105
start comment redbook-service/redbook-service-comment/target/redbook-service-comment-1.0-SNAPSHOT.jar 8100
start search  redbook-service/redbook-service-search/target/redbook-service-search-1.0-SNAPSHOT.jar 8090
start message redbook-service/redbook-service-message/target/redbook-service-message-1.0-SNAPSHOT.jar 8889
start ai      redbook-service/redbook-service-ai/target/redbook-service-ai-1.0-SNAPSHOT.jar 8091
sleep 20
start gateway redbook-gateway/target/redbook-gateway-1.0-SNAPSHOT.jar 10010

echo "all start commands issued; logs in .run/logs/"
