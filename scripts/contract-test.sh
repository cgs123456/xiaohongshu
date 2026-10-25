#!/usr/bin/env bash
# Cross-service deserialization probe after Long->String serialization:
#  1) comment list user hydration (comment svc User copy <- user svc string Long)
#  2) note detail vo.user (note svc <- user svc)
set -u
cd "$(dirname "$0")/.."
source .env
GW=http://localhost:10010
PHONE="${E2E_PHONE:?set E2E_PHONE in .env}"

get_code() { docker exec redbook-redis sh -c "redis-cli -a '$REDIS_PASSWORD' --no-auth-warning GET redbook:user:phone_code:$PHONE"; }
CODE=$(get_code)
TOKEN=$(curl -s -m 10 -X POST $GW/user/verify -H "Content-Type: application/json" -d "{\"phone\":\"$PHONE\",\"code\":\"$CODE\"}" | python -c "import json,sys; t=json.load(sys.stdin)['data']; print(t if t else '')" 2>/dev/null)
if [ -z "$TOKEN" ] || [ "$TOKEN" = "None" ]; then
  curl -s -m 8 -X POST $GW/user/send -H "Content-Type: application/json" -d "{\"phone\":\"$PHONE\"}" >/dev/null
  sleep 3
  CODE=$(get_code)
  TOKEN=$(curl -s -m 10 -X POST $GW/user/verify -H "Content-Type: application/json" -d "{\"phone\":\"$PHONE\",\"code\":\"$CODE\"}" | python -c "import json,sys; t=json.load(sys.stdin)['data']; print(t if t else '')" 2>/dev/null)
fi
if [ -z "$TOKEN" ] || [ "$TOKEN" = "None" ]; then echo "LOGIN FAILED"; exit 1; fi
echo "login ok"

echo "== comment list user hydration =="
curl -s -m 8 "$GW/comment/getCommentList/1001?page=1&pageSize=5" -H "token: $TOKEN" | python -c "
import json,sys
d = json.load(sys.stdin)['data'] or []
users = [(c['user'].get('nickname'), c['user'].get('number')) for c in d if c.get('user')]
print('comments:', len(d), '| hydrated users:', len(users), users[:2])
"

echo "== note detail vo.user =="
curl -s -m 8 "$GW/note/getNote/1001" -H "token: $TOKEN" | python -c "
import json,sys
d = json.load(sys.stdin)['data']
u = d.get('user') or {}
print('nickname:', u.get('nickname'), '| number:', repr(u.get('number')))
"
