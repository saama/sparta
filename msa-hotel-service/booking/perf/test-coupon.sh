#!/usr/bin/env bash
# 측정 B: 선착순 쿠폰 동시 발급 정합성 검증 (낙관적 락 @Version + uq_user_coupon)
#
# 방식:
#  - 발급 한도 LIMIT개짜리 쿠폰을 새로 만들고, 유저 USERS명이 동시에 발급 요청
#  - coupon.issued_count와 user_coupon 행 수가 정확히 LIMIT인지 DB로 검증
#  - 낙관적 락 충돌로 실패한 요청 수도 함께 집계 (재시도 로직이 없으므로 충돌 = 실패 응답)
#
# 사전 조건: perf/seed.sh 실행 완료(perfuser1~100, perfadmin), 앱이 8081에서 응답
# 사용법: bash perf/test-coupon.sh   (환경변수 USERS, LIMIT으로 조정 가능)
set -u
BASE="${BASE_URL:-http://localhost:8081}"
USERS="${USERS:-100}"
LIMIT="${LIMIT:-10}"
PASSWORD="Password1!"
TS=$(date +%s)

json_num() { echo "$1" | grep -o "\"$2\":[0-9]*" | head -1 | cut -d: -f2; }
json_str() { echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }

# ---------- 1. 쿠폰 생성 (어드민) ----------
ADMIN_TOKEN=$(json_str "$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"perfadmin@perf.test\",\"password\":\"$PASSWORD\"}")" accessToken)
[ -z "$ADMIN_TOKEN" ] && { echo "FAIL: 어드민 로그인 실패 (seed.sh 먼저 실행)"; exit 1; }
R=$(curl -s -X POST "$BASE/api/admin/coupons" -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"code\":\"PERF-$TS\",\"name\":\"Perf Coupon\",\"discountType\":\"FIXED\",\"discountValue\":10000,\"minPrice\":100000,\"issueLimit\":$LIMIT,\"validFrom\":\"2026-01-01\",\"validUntil\":\"2026-12-31\"}")
COUPON_ID=$(json_num "$R" id)
[ -z "$COUPON_ID" ] && { echo "FAIL: 쿠폰 생성 실패: $R"; exit 1; }

# ---------- 2. 유저 100명 로그인 (토큰 수집) ----------
TOKEN_FILE=$(mktemp)
for i in $(seq 1 "$USERS"); do
  json_str "$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"email\":\"perfuser$i@perf.test\",\"password\":\"$PASSWORD\"}")" accessToken >> "$TOKEN_FILE"
done
TOKENS=$(wc -l < "$TOKEN_FILE")
echo "=== 쿠폰 선착순 테스트: 한도 ${LIMIT}개 쿠폰(id=$COUPON_ID)에 유저 ${TOKENS}명 동시 발급 요청 ==="

# ---------- 3. 병렬 발급 요청 발사 (유저별 토큰 사용) ----------
OUT_DIR=$(mktemp -d)
export BASE COUPON_ID OUT_DIR
START=$(date +%s.%N)
awk '{print NR" "$0}' "$TOKEN_FILE" | xargs -P "$USERS" -L1 bash -c '
  curl -s -X POST "$BASE/api/coupons/$COUPON_ID/issue" -H "Authorization: Bearer $1" > "$OUT_DIR/$0.json"'
ELAPSED=$(date +%s.%N | awk -v s="$START" '{printf "%.1f", $1 - s}')

# ---------- 4. 결과 집계 (API 응답 + DB 실측) ----------
SUCCESS=$(grep -l '"userCouponId"\|"couponId"\|"id":' "$OUT_DIR"/*.json 2>/dev/null | xargs -r grep -L 'errorCode' | wc -l)
SOLD_OUT=$(grep -l 'SOLD_OUT' "$OUT_DIR"/*.json 2>/dev/null | wc -l)
CONFLICT=$((TOKENS - SUCCESS - SOLD_OUT))
DB_ISSUED=$(docker exec user-db mysql -uroot -prootpassword userdb -N \
  -e "SELECT issued_count FROM coupon WHERE id=$COUPON_ID" 2>/dev/null)
DB_ROWS=$(docker exec user-db mysql -uroot -prootpassword userdb -N \
  -e "SELECT COUNT(*) FROM user_coupon WHERE coupon_id=$COUPON_ID" 2>/dev/null)
DB_DUP=$(docker exec user-db mysql -uroot -prootpassword userdb -N \
  -e "SELECT COUNT(*) FROM (SELECT user_id FROM user_coupon WHERE coupon_id=$COUPON_ID GROUP BY user_id HAVING COUNT(*)>1) t" 2>/dev/null)

echo "[결과] 소요 ${ELAPSED}초"
echo "  - 발급 성공 응답            : $SUCCESS 건"
echo "  - 소진(SOLD_OUT) 응답       : $SOLD_OUT 건 / 낙관적 락 충돌 등 실패 : $CONFLICT 건"
echo "  - DB coupon.issued_count    : $DB_ISSUED (기대: $LIMIT 이하)"
echo "  - DB user_coupon 행 수      : $DB_ROWS (기대: $LIMIT 이하, issued_count와 일치)"
echo "  - 중복 발급된 유저 수        : $DB_DUP (기대: 0)"
if [ "$DB_ROWS" -le "$LIMIT" ] && [ "$DB_ROWS" = "$DB_ISSUED" ] && [ "$DB_DUP" = "0" ]; then
  echo "  => 정합성 유지 (한도 초과 0건, 중복 0건)"
else
  echo "  => 정합성 깨짐!"
fi
rm -f "$TOKEN_FILE"; rm -rf "$OUT_DIR"
