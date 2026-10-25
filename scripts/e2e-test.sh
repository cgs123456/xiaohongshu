#!/usr/bin/env bash
# E2E 关键链路验证：登录态下的 交易闭环 + 笔记发布 + 搜索
set -u
cd "$(dirname "$0")/.."
source .env

GW=http://localhost:10010
OUT=.run/e2e-result.txt
: > "$OUT"

step() { echo "===== $1 =====" >> "$OUT"; }

# 1. 登录拿 token（验证码有 60s 发送间隔，取不到码时等待后重发）
get_code() {
  docker exec redbook-redis sh -c "redis-cli -a '$REDIS_PASSWORD' --no-auth-warning GET redbook:user:phone_code:$E2E_PHONE"
}
CODE=$(get_code)
if [ -z "$CODE" ]; then
  curl -s -m 8 -X POST $GW/user/send -H "Content-Type: application/json" -d "{\"phone\":\"$E2E_PHONE\"}" > /dev/null
  sleep 2
  CODE=$(get_code)
fi
if [ -z "$CODE" ]; then
  echo "waiting 60s for sms interval ..." >&2
  sleep 62
  curl -s -m 8 -X POST $GW/user/send -H "Content-Type: application/json" -d "{\"phone\":\"$E2E_PHONE\"}" > /dev/null
  sleep 2
  CODE=$(get_code)
fi
TOKEN=$(curl -s -m 10 -X POST $GW/user/verify -H "Content-Type: application/json" -d "{\"phone\":\"$E2E_PHONE\",\"code\":\"$CODE\"}" | python -c "import json,sys; print(json.load(sys.stdin)['data'])" 2>/dev/null)
if [ -z "$TOKEN" ] || [ "$TOKEN" = "None" ]; then echo "LOGIN FAILED" >> "$OUT"; cat "$OUT"; exit 1; fi
step "login token (first 12 chars): ${TOKEN:0:12}"

# 2. 用户信息
step "getInfo"
curl -s -m 8 $GW/user/getInfo -H "token: $TOKEN" | head -c 300 >> "$OUT"; echo >> "$OUT"

# 3. 商品列表（取第一个商品 id）
step "productList"
curl -s -m 8 "$GW/product/getProductList" -H "token: $TOKEN" | python -c "import json,sys; d=json.load(sys.stdin)['data'][:3]; print(len(json.load(open('/dev/null')) ) if False else [(p['id'],p['name'],p['price'],p['stock']) for p in d])" >> "$OUT" 2>&1 || echo "parse fail" >> "$OUT"

# 4. 购物车
step "cartList"
curl -s -m 8 "$GW/cart/getCartList" -H "token: $TOKEN" | python -c "import json,sys; d=json.load(sys.stdin)['data'] or []; print([(c['id'],c['productId'],c['productName'],c['quantity']) for c in d])" >> "$OUT" 2>&1

# 5. 加购后结算（新注册用户无持券，不用券）
step "addToCart"
curl -s -m 8 -X POST "$GW/cart/addToCart?productId=1&quantity=2" -H "token: $TOKEN" >> "$OUT"; echo >> "$OUT"

step "checkout"
curl -s -m 15 -X POST "$GW/cart/checkout" -H "token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"receiverName":"Tester","receiverPhone":"12345678901","address":"Test Address No.1"}' >> "$OUT"; echo >> "$OUT"

# 6. 订单列表
step "orderList"
curl -s -m 8 "$GW/order/getOrderList?page=1&pageSize=5" -H "token: $TOKEN" | python -c "
import json,sys
d=json.load(sys.stdin)['data'] or []
for vo in d:
    o=vo['order']; items=vo.get('items') or []
    print(o['id'], 'status=',o['status'], 'total=',o.get('totalPrice'), 'discount=',o.get('discountPrice'), 'final=',o.get('finalPrice'), 'coupon=',o.get('couponId'), 'items=',[(i.get('productName'),i.get('quantity'),i.get('unitPrice')) for i in items])
" >> "$OUT" 2>&1

# 7. 支付第一笔待支付订单
step "pay"
PAY_ID=$(curl -s -m 8 "$GW/order/getOrderList?page=1&pageSize=5" -H "token: $TOKEN" | python -c "
import json,sys
d=json.load(sys.stdin)['data'] or []
print(next((vo['order']['id'] for vo in d if vo['order']['status']==0), ''))
")
if [ -n "$PAY_ID" ]; then
  curl -s -m 8 -X POST "$GW/order/pay/$PAY_ID" -H "token: $TOKEN" >> "$OUT"; echo >> "$OUT"
  sleep 1
  curl -s -m 8 "$GW/order/getOrderList?page=1&pageSize=5" -H "token: $TOKEN" | python -c "
import json,sys
d=json.load(sys.stdin)['data'] or []
print('after pay:', [(vo['order']['id'][-4:], vo['order']['status'], vo['order'].get('payTime')) for vo in d])
" >> "$OUT" 2>&1
else
  echo "no DUE order to pay" >> "$OUT"
fi

# 8. 发布笔记（multipart：接口要求 image 文件必传）
step "postNote"
printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\x0aIDATx\x9cc\x00\x01\x00\x00\x05\x00\x01\r\n-\xb4\x00\x00\x00\x00IEND\xaeB\x60\x82' > .run/test.png
curl -s -m 40 -X POST "$GW/note/postNote" -H "token: $TOKEN" \
  -F "image=@.run/test.png;type=image/png" -F "title=E2E 测试笔记" -F "content=端到端验证发布链路，包含话题 周末去哪儿" -F "longitude=121.5221" -F "latitude=31.2645" >> "$OUT"; echo >> "$OUT"

# 9. 首页列表应包含新笔记
step "noteList contains new note"
curl -s -m 8 "$GW/note/getNoteList?page=1&pageSize=20" | python -c "
import json,sys
d=json.load(sys.stdin)['data'] or []
hit=[n for n in d if 'E2E' in (n['note'].get('title') or '')]
print('found new note:', bool(hit), hit[0]['note']['id'] if hit else '')
" >> "$OUT" 2>&1

# 10. 搜索
step "search"
curl -s -m 8 "$GW/search/search/E2E" -H "token: $TOKEN" | python -c "
import json,sys
d=json.load(sys.stdin)['data'] or []
print('search E2E results:', len(d))
" >> "$OUT" 2>&1

# 11. 内部接口封堵验证（外部访问应 404）
step "gateway block internal endpoints"
B1=$(curl -s -o /dev/null -w '%{http_code}' -m 8 -X POST "$GW/product/helper/restoreStock?productId=1&quantity=99" -H "token: $TOKEN" -H "X-Internal-Token: anything")
B2=$(curl -s -o /dev/null -w '%{http_code}' -m 8 "$GW/note/admin/getPendingNotes" -H "token: $TOKEN")
echo "restoreStock via gateway: $B1 (expect 404), note admin: $B2 (expect 404)" >> "$OUT"

# 12. 未登录访问受保护接口（应 401）
step "unauthorized check"
U=$(curl -s -o /dev/null -w '%{http_code}' -m 8 "$GW/cart/getCartList")
echo "cart without token: $U (expect 401)" >> "$OUT"

echo "E2E DONE"
cat "$OUT"
