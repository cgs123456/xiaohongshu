#!/usr/bin/env bash
# Ablation probes: prove each core mechanism's behavior by removing/disabling it.
#  1) bloom filter      : DEL seen-key -> feed full page again (filter is the page-shrink cause)
#  2) feed inbox        : DEL inbox -> attention feed still served (DB pull fallback)
#  3) note detail cache : DEL cache -> getNote rebuilds (200)
#  4) checkout idem     : two rapid checkouts -> 2nd rejected (double-click guard)
#  5) optimistic state  : pay a CANCELLED order -> rejected
#  6) timeout scan task : backdate a DUE order 16min -> scanned+closed+restocked within ~70s
set -u
cd "$(dirname "$0")/.."
source .env
GW=http://localhost:10010
PHONE="${E2E_PHONE:?set E2E_PHONE in .env}"

rc() { docker exec redbook-redis sh -c "redis-cli -a '$REDIS_PASSWORD' --no-auth-warning $*"; }
mq() { docker exec redbook-mysql sh -c "mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" rb_order -N -e \"$1\"" 2>/dev/null; }

get_code() { rc GET "redbook:user:phone_code:$PHONE" | tr -d '\r\n'; }
login_token() {
  local code tok
  code=$(get_code)
  if [ -z "$code" ]; then
    curl -s -m 8 -X POST $GW/user/send -H "Content-Type: application/json" -d "{\"phone\":\"$PHONE\"}" >/dev/null
    sleep 3
    code=$(get_code)
  fi
  tok=$(curl -s -m 10 -X POST $GW/user/verify -H "Content-Type: application/json" -d "{\"phone\":\"$PHONE\",\"code\":\"$code\"}" | python -c "import json,sys; t=json.load(sys.stdin)['data']; print(t if t else '')" 2>/dev/null)
  echo "$tok"
}
TOKEN=$(login_token)
[ -z "$TOKEN" ] || [ "$TOKEN" = "None" ] && { echo "LOGIN FAILED"; exit 1; }
UID3=$(curl -s -m 8 "$GW/user/getInfo" -H "token: $TOKEN" | python -c "import json,sys; print(json.load(sys.stdin)['data']['user']['id'])")
echo "login ok uid=$UID3"

feedcount() { curl -s -m 8 "$GW/note/getNoteList?page=1&pageSize=20" -H "token: $TOKEN" | python -c "import json,sys; print(len(json.load(sys.stdin)['data'] or []))"; }

echo "== [1] bloom filter ablation =="
rc DEL "redbook:note:user_bloom_filter:$UID3" >/dev/null
B1=$(feedcount); echo "after DEL bloom key, feed page1 count = $B1 (expect 20)"
NOTE_ID=$(curl -s -m 8 "$GW/note/getNoteList?page=1&pageSize=1" -H "token: $TOKEN" | python -c "import json,sys; print(json.load(sys.stdin)['data'][0]['note']['id'])")
curl -s -m 8 "$GW/note/getNote/$NOTE_ID" -H "token: $TOKEN" >/dev/null   # marks it seen
B2=$(feedcount); echo "after viewing one note, feed page1 count = $B2 (expect $((B1-1)) -> filter shrinks page)"

echo "== [2] feed inbox ablation =="
rc DEL "redbook:note:feed_inbox:$UID3" >/dev/null
A1=$(curl -s -m 8 "$GW/note/getNoteListByAttention?page=1&pageSize=20" -H "token: $TOKEN" | python -c "import json,sys; print(len(json.load(sys.stdin)['data'] or []))")
echo "after DEL inbox, attention feed count = $A1 (>=0 and HTTP 200 -> DB pull fallback works)"

echo "== [3] note detail cache ablation =="
rc DEL "redbook:note:note_detail_cache:$NOTE_ID" >/dev/null
C1=$(curl -s -m 8 -o /dev/null -w '%{http_code}' "$GW/note/getNote/$NOTE_ID" -H "token: $TOKEN")
TTL=$(rc TTL "redbook:note:note_detail_cache:$NOTE_ID" | tr -d '\r\n')
echo "after DEL cache, getNote http=$C1, rebuilt cache TTL=$TTL (>0 -> mutex rebuild works)"

echo "== [4] checkout idempotency =="
CART_N=$(curl -s -m 8 "$GW/product/getCartList" -H "token: $TOKEN" | python -c "import json,sys; d=json.load(sys.stdin)['data']; print(len(d) if d else 0)")
if [ "$CART_N" = "0" ]; then
  curl -s -m 10 -X POST "$GW/product/addToCart" -H "token: $TOKEN" -H "Content-Type: application/json" -d '{"productId":1,"quantity":1}' >/dev/null
  echo "cart was empty, added productId=1"
fi
DUE_ORDER=$(mq "SELECT id FROM rb_order WHERE status=0 AND user_id=$UID3 ORDER BY id DESC LIMIT 1")
if [ -z "$DUE_ORDER" ]; then
  curl -s -m 20 -X POST $GW/product/checkout -H "token: $TOKEN" -H "Content-Type: application/json" \
    -d "{\"receiverName\":\"Ablation\",\"receiverPhone\":\"$PHONE\",\"address\":\"probe\"}" >/dev/null
  DUE_ORDER=$(mq "SELECT id FROM rb_order WHERE status=0 AND user_id=$UID3 ORDER BY id DESC LIMIT 1")
fi
echo "due order: $DUE_ORDER"
R1=$(curl -s -m 20 -X POST $GW/product/checkout -H "token: $TOKEN" -H "Content-Type: application/json" -d "{\"receiverName\":\"A\",\"receiverPhone\":\"$PHONE\",\"address\":\"p\"}" | python -c "import json,sys; d=json.load(sys.stdin); print(d.get('message') or d)")
echo "rapid second checkout -> $R1 (reject = guard works)"

echo "== [5] optimistic state machine =="
mq "UPDATE rb_order SET status=4 WHERE id=$DUE_ORDER" >/dev/null
P1=$(curl -s -m 10 -X POST "$GW/order/pay" -H "token: $TOKEN" -H "Content-Type: application/json" -d "{\"orderId\":\"$DUE_ORDER\"}" | python -c "import json,sys; d=json.load(sys.stdin); print(d.get('message') or d)")
echo "pay cancelled order -> $P1 (reject = state guard works)"

echo "== [6] timeout scan fallback (background, ~70s) =="
mq "UPDATE rb_order SET status=0, create_time=NOW()-INTERVAL 16 MINUTE WHERE id=$DUE_ORDER" >/dev/null
SKU_BEFORE=$(mq "SELECT i.sku_id FROM rb_order_item i WHERE i.order_id=$DUE_ORDER LIMIT 1")
echo "order $DUE_ORDER backdated, item skuId=$SKU_BEFORE; waiting for scan task..."
