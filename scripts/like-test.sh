#!/usr/bin/env bash
# Like/unlike round-trip verification (PUT /note/like/{id}), includes count delta check
set -u
cd "$(dirname "$0")/.."
source .env
GW=http://localhost:10010

# real phone digits extracted at runtime from e2e script (avoid placeholder leakage)
PHONE="${E2E_PHONE:?set E2E_PHONE in .env}"
if [ -z "$PHONE" ]; then echo "no phone found"; exit 1; fi

get_code() { docker exec redbook-redis sh -c "redis-cli -a '$REDIS_PASSWORD' --no-auth-warning GET redbook:user:phone_code:$PHONE"; }
CODE=$(get_code)
if [ -z "$CODE" ]; then
  curl -s -m 8 -X POST $GW/user/send -H "Content-Type: application/json" -d "{\"phone\":\"$PHONE\"}" > /dev/null
  sleep 2
  CODE=$(get_code)
fi
if [ -z "$CODE" ]; then
  echo "waiting 60s for sms interval ..." >&2
  sleep 62
  curl -s -m 8 -X POST $GW/user/send -H "Content-Type: application/json" -d "{\"phone\":\"$PHONE\"}" > /dev/null
  sleep 2
  CODE=$(get_code)
fi
TOKEN=$(curl -s -m 10 -X POST $GW/user/verify -H "Content-Type: application/json" -d "{\"phone\":\"$PHONE\",\"code\":\"$CODE\"}" | python -c "import json,sys; print(json.load(sys.stdin)['data'])" 2>/dev/null)
if [ -z "$TOKEN" ] || [ "$TOKEN" = "None" ]; then echo "LOGIN FAILED"; exit 1; fi
echo "login ok (token head: ${TOKEN:0:12})"

NOTE_ID=${1:-1001}
before=$(curl -s -m 8 "$GW/note/getNote/$NOTE_ID" -H "token: $TOKEN" | python -c "import json,sys; print(json.load(sys.stdin)['data']['note']['like'])")
echo "note $NOTE_ID like count before: $before"

echo "--- PUT like ---"
curl -s -m 10 -X PUT "$GW/note/like/$NOTE_ID" -H "token: $TOKEN"; echo
echo "isLike: $(curl -s -m 8 "$GW/note/isLike/$NOTE_ID" -H "token: $TOKEN" | python -c "import json,sys; print(json.load(sys.stdin)['data'])")"
after_like=$(curl -s -m 8 "$GW/note/getNote/$NOTE_ID" -H "token: $TOKEN" | python -c "import json,sys; print(json.load(sys.stdin)['data']['note']['like'])")
echo "count after like: $after_like (expect $((before+1)))"

sleep 1
echo "--- PUT like again (toggle off) ---"
curl -s -m 10 -X PUT "$GW/note/like/$NOTE_ID" -H "token: $TOKEN"; echo
echo "isLike: $(curl -s -m 8 "$GW/note/isLike/$NOTE_ID" -H "token: $TOKEN" | python -c "import json,sys; print(json.load(sys.stdin)['data'])")"
after_unlike=$(curl -s -m 8 "$GW/note/getNote/$NOTE_ID" -H "token: $TOKEN" | python -c "import json,sys; print(json.load(sys.stdin)['data']['note']['like'])")
echo "count after unlike: $after_unlike (expect $before)"

if [ "$after_like" = "$((before+1))" ] && [ "$after_unlike" = "$before" ]; then
  echo "LIKE ROUND-TRIP: PASS"
else
  echo "LIKE ROUND-TRIP: FAIL"
fi
