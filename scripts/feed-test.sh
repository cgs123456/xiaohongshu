#!/usr/bin/env bash
# Push-pull feed probe:
#  1) E2E user (3) follows seed user 1
#  2) login as seed user 1, post a note
#  3) verify Redis inbox of user 3 contains the noteId (push side)
#  4) verify /note/getNoteListByAttention as user 3 returns it (read side)
set -u
cd "$(dirname "$0")/.."
source .env
GW=http://localhost:10010

login() { # $1=phone -> echoes token
  local code
  code=$(docker exec redbook-redis sh -c "redis-cli -a '$REDIS_PASSWORD' --no-auth-warning GET redbook:user:phone_code:$1")
  if [ -z "$code" ]; then
    curl -s -m 8 -X POST $GW/user/send -H "Content-Type: application/json" -d "{\"phone\":\"$1\"}" >/dev/null
    sleep 3
    code=$(docker exec redbook-redis sh -c "redis-cli -a '$REDIS_PASSWORD' --no-auth-warning GET redbook:user:phone_code:$1")
  fi
  curl -s -m 10 -X POST $GW/user/verify -H "Content-Type: application/json" -d "{\"phone\":\"$1\",\"code\":\"$code\"}" \
    | python -c "import json,sys; print(json.load(sys.stdin)['data'])" 2>/dev/null
}

PHONE3="${E2E_PHONE:?set E2E_PHONE in .env}"
PHONE1=$(docker exec redbook-mysql sh -c "mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" rb_user -N -e 'SELECT phone FROM rb_user WHERE id=1;'" 2>/dev/null | tr -d '\r\n ')
echo "user1 phone len: ${#PHONE1}"

T3=$(login "$PHONE3")
if [ -z "$T3" ] || [ "$T3" = "None" ]; then echo "LOGIN user3 FAILED"; exit 1; fi
T1=$(login "$PHONE1")
if [ -z "$T1" ] || [ "$T1" = "None" ]; then echo "LOGIN user1 FAILED"; exit 1; fi
echo "both logins ok"

echo "== user3 follows user1 =="
curl -s -m 8 -X POST $GW/user/attention/1 -H "token: $T3"; echo

echo "== user1 posts note =="
printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\x0aIDATx\x9cc\x00\x01\x00\x00\x05\x00\x01\r\n-\xb4\x00\x00\x00\x00IEND\xaeB\x60\x82' > .run/feed.png
POST_RESP=$(curl -s -m 40 -X POST "$GW/note/postNote" -H "token: $T1" \
  -F "image=@.run/feed.png;type=image/png" -F "title=FeedPush Probe" \
  -F "content=push-pull inbox probe content" -F "longitude=121.5" -F "latitude=31.2")
echo "$POST_RESP"

sleep 4  # fanout async
echo "== inbox of user3 (push side) =="
docker exec redbook-redis sh -c "redis-cli -a '$REDIS_PASSWORD' --no-auth-warning LRANGE redbook:note:feed_inbox:3 0 4"

echo "== attention feed as user3 (read side) =="
curl -s -m 10 "$GW/note/getNoteListByAttention?page=1&pageSize=20" -H "token: $T3" \
  | python -c "
import json,sys
d = json.load(sys.stdin)['data'] or []
hits = [n for n in d if 'FeedPush' in (n['note'].get('title') or '')]
print('attention feed notes:', len(d), '| contains probe note:', bool(hits), hits[0]['note']['id'] if hits else '')
"
