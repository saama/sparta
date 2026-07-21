#!/usr/bin/env bash
# 측정 A: 동시 예약 요청 시 오버부킹(재고 초과 예약) 발생 여부 검증
#
# 방식:
#  - 재고 STOCK개짜리 객실을 새로 만들고, 동일 날짜에 CONCURRENCY건의 예약 요청을 병렬로 발사
#  - 성공(PENDING) 건수 / DB의 실제 예약 행 수 / 남은 재고를 집계
#  - 정상(비관적 락)이라면: 성공 = STOCK, 남은 재고 = 0
#  - 락 제거 상태라면: lost update로 성공 > STOCK (오버부킹 재현)
#
# 사전 조건: perf/seed.sh 실행 완료(perfadmin/perfuser1 계정), 앱이 8081에서 응답
# 사용법: bash perf/test-overbooking.sh   (환경변수 CONCURRENCY, STOCK으로 조정 가능)
set -u
BASE="${BASE_URL:-http://localhost:8081}"
CONCURRENCY="${CONCURRENCY:-100}"
STOCK="${STOCK:-10}"
PASSWORD="Password1!"
TS=$(date +%s)
ARR="2026-11-01"; DEP="2026-11-02"   # 시딩 재고(8~10월)와 겹치지 않는 날짜 사용

json_num() { echo "$1" | grep -o "\"$2\":[0-9]*" | head -1 | cut -d: -f2; }
json_str() { echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }

# ---------- 1. 토큰 발급 + 테스트 전용 객실 생성 ----------
ADMIN_TOKEN=$(json_str "$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"perfadmin@perf.test\",\"password\":\"$PASSWORD\"}")" accessToken)
USER_TOKEN=$(json_str "$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"perfuser1@perf.test\",\"password\":\"$PASSWORD\"}")" accessToken)
[ -z "$ADMIN_TOKEN" ] || [ -z "$USER_TOKEN" ] && { echo "FAIL: 로그인 실패 (seed.sh 먼저 실행)"; exit 1; }

R=$(curl -s -X POST "$BASE/api/admin/rooms" -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"Overbook Room $TS\",\"roomType\":\"STANDARD\",\"price\":100000,\"baseCapacity\":2,\"maxCapacity\":4,\"description\":\"overbooking test\"}")
ROOM_ID=$(json_num "$R" id)
curl -s -o /dev/null -X POST "$BASE/api/admin/rooms/$ROOM_ID/stock" -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"startDate\":\"$ARR\",\"endDate\":\"$DEP\",\"stock\":$STOCK}"
echo "=== 오버부킹 테스트: 재고 ${STOCK}개 객실(id=$ROOM_ID)에 동시 ${CONCURRENCY}건 예약 요청 ==="

# ---------- 2. 병렬 예약 요청 발사 ----------
OUT_DIR=$(mktemp -d)
export BASE USER_TOKEN ROOM_ID ARR DEP OUT_DIR
START=$(date +%s.%N)
seq 1 "$CONCURRENCY" | xargs -P "$CONCURRENCY" -I{} bash -c '
  curl -s -X POST "$BASE/api/bookings" -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER_TOKEN" \
    -d "{\"roomProductId\":$ROOM_ID,\"arrDate\":\"$ARR\",\"depDate\":\"$DEP\",\"adultCount\":2,\"guestName\":\"Perf User\",\"guestPhone\":\"010-9000-0001\"}" \
    > "$OUT_DIR/{}.json"'
ELAPSED=$(date +%s.%N | awk -v s="$START" '{printf "%.1f", $1 - s}')

# ---------- 3. 결과 집계 (API 응답 + DB 실측) ----------
SUCCESS=$(grep -l '"status":"PENDING"' "$OUT_DIR"/*.json 2>/dev/null | wc -l)
SOLD_OUT=$(grep -l 'OUT_OF_STOCK' "$OUT_DIR"/*.json 2>/dev/null | wc -l)
ETC=$((CONCURRENCY - SUCCESS - SOLD_OUT))
DB_BOOKINGS=$(docker exec user-db mysql -uroot -prootpassword userdb -N \
  -e "SELECT COUNT(*) FROM booking WHERE room_product_id=$ROOM_ID" 2>/dev/null)
DB_STOCK=$(docker exec user-db mysql -uroot -prootpassword userdb -N \
  -e "SELECT stock FROM room_stock WHERE room_product_id=$ROOM_ID AND date='$ARR'" 2>/dev/null)

echo "[결과] 소요 ${ELAPSED}초"
echo "  - 예약 성공(PENDING) 응답 : $SUCCESS 건"
echo "  - 재고 소진(OUT_OF_STOCK) : $SOLD_OUT 건 / 기타 실패 : $ETC 건"
echo "  - DB booking 행 수        : $DB_BOOKINGS 건 (기대: $STOCK)"
echo "  - DB 남은 재고            : $DB_STOCK (기대: 0)"
if [ "$DB_BOOKINGS" -gt "$STOCK" ]; then
  echo "  => 오버부킹 발생! 재고 ${STOCK}개에 예약 ${DB_BOOKINGS}건 ($(($DB_BOOKINGS - $STOCK))건 초과)"
else
  echo "  => 정합성 유지 (재고 ${STOCK}개 = 예약 ${DB_BOOKINGS}건)"
fi
rm -rf "$OUT_DIR"
