#!/usr/bin/env bash
# Attribute snapshot probe: checkout product 3 with selected attributes, verify rb_order_item.attributes
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

curl -s -m 8 -X POST "$GW/cart/addToCart?productId=3&quantity=1" -H "token: $TOKEN" >/dev/null
echo "== checkout with attributes =="
curl -s -m 15 -X POST $GW/cart/checkout -H "token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"receiverName":"AttrTester","receiverPhone":"12345678901","address":"Attr Test Addr","productIds":[3],"attributes":{"\u989c\u8272":"\u9ed1\u8272","\u5c3a\u7801":"XL"}}'
echo
sleep 3
echo "== rb_order_item snapshot =="
docker exec redbook-mysql sh -c "mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" rb_order -N -e 'SELECT order_id, product_id, attributes FROM rb_order_item WHERE product_id=3 ORDER BY id DESC LIMIT 1;'" 2>/dev/null
