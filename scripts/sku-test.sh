#!/usr/bin/env bash
# SKU probe: add product 3 (variant 尺码:M) to cart, checkout, verify rb_order_item sku/attributes + sku stock split
set -u
cd "$(dirname "$0")/.."
source .env
GW=http://localhost:10010
PHONE="${E2E_PHONE:?set E2E_PHONE in .env}"

get_code() { docker exec redbook-redis sh -c "redis-cli -a '$REDIS_PASSWORD' --no-auth-warning GET redbook:user:phone_code:$PHONE"; }
CODE=$(get_code)
TOKEN=$(curl -s -m 10 -X POST $GW/user/verify -H "Content-Type: application/json" -d "{\"phone\":\"$PHONE\",\"code\":\"$CODE\"}" | python -c "import json,sys; print(json.load(sys.stdin)['data'])" 2>/dev/null)
if [ -z "$TOKEN" ] || [ "$TOKEN" = "None" ]; then
  curl -s -m 8 -X POST $GW/user/send -H "Content-Type: application/json" -d "{\"phone\":\"$PHONE\"}" >/dev/null
  sleep 3
  CODE=$(get_code)
  TOKEN=$(curl -s -m 10 -X POST $GW/user/verify -H "Content-Type: application/json" -d "{\"phone\":\"$PHONE\",\"code\":\"$CODE\"}" | python -c "import json,sys; print(json.load(sys.stdin)['data'])" 2>/dev/null)
fi
if [ -z "$TOKEN" ] || [ "$TOKEN" = "None" ]; then echo "LOGIN FAILED"; exit 1; fi
echo "login ok"

echo "== addToCart product3 {\\u5c3a\\u7801:M} =="
ADD=$(curl -s -m 8 -X POST $GW/cart/addToCart -H "token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"productId":3,"quantity":1,"attributes":{"\u5c3a\u7801":"M"}}')
echo "$ADD"
SKU_ID=$(echo "$ADD" | python -c "import json,sys; d=json.load(sys.stdin); print(d['data'] or '')" 2>/dev/null)
if [ -z "$SKU_ID" ]; then echo "ADD FAILED (no skuId)"; exit 1; fi

echo "== checkout skuIds=[$SKU_ID] =="
curl -s -m 15 -X POST $GW/cart/checkout -H "token: $TOKEN" -H "Content-Type: application/json" \
  -d "{\"receiverName\":\"SkuTester\",\"receiverPhone\":\"12345678901\",\"address\":\"Sku Test Addr\",\"skuIds\":[$SKU_ID]}"
echo
sleep 3
echo "== rb_order_item snapshot =="
docker exec redbook-mysql sh -c "mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" rb_order -N -e 'SELECT order_id, product_id, sku_id, HEX(attributes) FROM rb_order_item WHERE sku_id=$SKU_ID ORDER BY id DESC LIMIT 1;'" 2>/dev/null
echo "== rb_sku stock after (M was 60) =="
docker exec redbook-mysql sh -c "mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" rb_product -N -e 'SELECT id, attrs_key, stock FROM rb_sku WHERE product_id=3 ORDER BY id;'" 2>/dev/null
