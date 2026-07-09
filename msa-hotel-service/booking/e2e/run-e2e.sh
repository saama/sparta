#!/usr/bin/env bash
# 호텔 예약 서비스 E2E 자동 실행 스크립트 (curl 기반, jq 불필요)
#
# 사전 조건:
#  - docker compose -f docker-compose.local.yml up -d (user-db 컨테이너 필요)
#  - ./gradlew bootRun 으로 앱이 8081에서 기동 중
#
# 사용법: bash e2e/run-e2e.sh

set -u
BASE="${BASE_URL:-http://localhost:8081}"
TS=$(date +%s)                       # 실행마다 고유 계정을 만들기 위한 타임스탬프
USER_EMAIL="e2e-user-${TS}@test.com"
ADMIN_EMAIL="e2e-admin-${TS}@test.com"
PASSWORD="Password1!"
PASS=0; FAIL=0

# ---------- 헬퍼 ----------
json_str() {  # $1=json $2=field : 문자열 필드 추출
  echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4
}
json_num() {  # $1=json $2=field : 숫자 필드 추출
  echo "$1" | grep -o "\"$2\":[0-9]*" | head -1 | cut -d: -f2
}
check() {  # $1=설명 $2=실제값 $3=기대값
  if [ "$2" = "$3" ]; then
    echo "  PASS: $1 (= $3)"; PASS=$((PASS+1))
  else
    echo "  FAIL: $1 (기대: $3, 실제: $2)"; FAIL=$((FAIL+1))
  fi
}
check_not_empty() {  # $1=설명 $2=값
  if [ -n "$2" ]; then
    echo "  PASS: $1 (= $2)"; PASS=$((PASS+1))
  else
    echo "  FAIL: $1 (값 없음)"; FAIL=$((FAIL+1))
  fi
}

echo "=== E2E 시나리오 시작 (BASE=$BASE) ==="

# ---------- 1. 회원가입 (유저/어드민) ----------
echo "[1] 회원가입"
R=$(curl -s -X POST "$BASE/api/auth/registration" -H "Content-Type: application/json" \
  -d "{\"username\":\"e2euser$TS\",\"name\":\"E2E User\",\"phone\":\"010-1111-2222\",\"email\":\"$USER_EMAIL\",\"password\":\"$PASSWORD\"}")
check "유저 회원가입 (에러 없음)" "$(json_str "$R" errorCode)" ""
R=$(curl -s -X POST "$BASE/api/auth/registration" -H "Content-Type: application/json" \
  -d "{\"username\":\"e2eadmin$TS\",\"name\":\"E2E Admin\",\"phone\":\"010-3333-4444\",\"email\":\"$ADMIN_EMAIL\",\"password\":\"$PASSWORD\"}")
check "어드민 회원가입 (에러 없음)" "$(json_str "$R" errorCode)" ""

# ---------- 2. 어드민 권한 부여 (DB 직접 변경) ----------
echo "[2] 어드민 권한 부여"
docker exec user-db mysql -uroot -p"${MYSQL_ROOT_PASSWORD:-rootpassword}" userdb \
  -e "UPDATE user SET role='ADMIN' WHERE email='$ADMIN_EMAIL'" 2>/dev/null \
  && echo "  role=ADMIN 변경 완료" || { echo "  FAIL: DB role 변경 실패"; FAIL=$((FAIL+1)); }

# ---------- 3. 로그인 ----------
echo "[3] 로그인"
R=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$PASSWORD\"}")
ADMIN_TOKEN=$(json_str "$R" accessToken)
check_not_empty "어드민 accessToken 발급" "$ADMIN_TOKEN"
R=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$PASSWORD\"}")
USER_TOKEN=$(json_str "$R" accessToken)
check_not_empty "유저 accessToken 발급" "$USER_TOKEN"

# ---------- 4. 객실 등록 + 재고 초기화 (어드민) ----------
echo "[4] 객실 등록/재고 초기화"
R=$(curl -s -X POST "$BASE/api/admin/rooms" -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"E2E Deluxe","roomType":"DELUXE","price":150000,"baseCapacity":2,"maxCapacity":4,"description":"E2E test room"}')
ROOM_ID=$(json_num "$R" id)
check_not_empty "객실 등록 (roomId)" "$ROOM_ID"
R=$(curl -s -X POST "$BASE/api/admin/rooms/$ROOM_ID/stock" -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"startDate":"2026-08-01","endDate":"2026-08-10","stock":5}')
check "재고 초기화 (에러 없음)" "$(json_str "$R" errorCode)" ""

# ---------- 5. 객실 목록 조회 (공개 API) ----------
echo "[5] 객실 목록 조회"
R=$(curl -s "$BASE/api/rooms?arrDate=2026-08-01&depDate=2026-08-03&guestCount=2")
echo "$R" | grep -q "E2E Deluxe" \
  && { echo "  PASS: 등록한 객실이 목록에 노출"; PASS=$((PASS+1)); } \
  || { echo "  FAIL: 객실이 목록에 없음"; FAIL=$((FAIL+1)); }

# ---------- 6. 쿠폰 생성(어드민) + 발급(유저) ----------
echo "[6] 쿠폰 생성/발급"
R=$(curl -s -X POST "$BASE/api/admin/coupons" -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"code\":\"E2E-$TS\",\"name\":\"E2E Coupon\",\"discountType\":\"FIXED\",\"discountValue\":10000,\"minPrice\":100000,\"issueLimit\":100,\"validFrom\":\"2026-01-01\",\"validUntil\":\"2026-12-31\"}")
COUPON_ID=$(json_num "$R" id)
check_not_empty "쿠폰 생성 (couponId)" "$COUPON_ID"
R=$(curl -s -X POST "$BASE/api/coupons/$COUPON_ID/issue" -H "Authorization: Bearer $USER_TOKEN")
USER_COUPON_ID=$(json_num "$R" id)
check_not_empty "쿠폰 발급 (userCouponId)" "$USER_COUPON_ID"

# ---------- 7. 예약 생성 (쿠폰 적용) ----------
echo "[7] 예약 생성"
R=$(curl -s -X POST "$BASE/api/bookings" -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"roomProductId\":$ROOM_ID,\"arrDate\":\"2026-08-01\",\"depDate\":\"2026-08-03\",\"adultCount\":2,\"guestName\":\"E2E User\",\"guestPhone\":\"010-1111-2222\",\"userCouponId\":$USER_COUPON_ID}")
BOOKING_ID=$(json_num "$R" id)
check_not_empty "예약 생성 (bookingId)" "$BOOKING_ID"
check "예약 초기 상태" "$(json_str "$R" status)" "PENDING"
# 2박 x 150,000 - 10,000(쿠폰) = 290,000 검증
check "쿠폰 적용 결제 금액" "$(json_num "$R" totPrice)" "290000"

# ---------- 8. 결제 ----------
echo "[8] 결제"
R=$(curl -s -X POST "$BASE/api/payments" -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"bookingId\":$BOOKING_ID,\"paymentMethod\":\"CARD\"}")
PAYMENT_ID=$(json_num "$R" id)
check_not_empty "결제 생성 (paymentId)" "$PAYMENT_ID"
check "결제 상태" "$(json_str "$R" paymentStatus)" "PAID"

# ---------- 9. Kafka 컨슈머 처리 대기 후 예약 CONFIRMED 확인 ----------
echo "[9] 예약 확정 확인 (Kafka 이벤트 처리 대기)"
CONFIRMED=""
for i in 1 2 3 4 5 6 7 8 9 10; do
  R=$(curl -s "$BASE/api/bookings/$BOOKING_ID" -H "Authorization: Bearer $USER_TOKEN")
  STATUS=$(json_str "$R" status)
  [ "$STATUS" = "CONFIRMED" ] && { CONFIRMED="yes"; break; }
  sleep 1
done
check "결제 완료 후 예약 상태" "${STATUS:-없음}" "CONFIRMED"

# ---------- 10. 결제 취소 (환불 + 재고 원복 + 쿠폰 복구) ----------
echo "[10] 결제 취소/환불"
R=$(curl -s -X POST "$BASE/api/payments/$PAYMENT_ID/cancel" -H "Authorization: Bearer $USER_TOKEN")
check "환불 상태" "$(json_str "$R" paymentStatus)" "REFUNDED"
R=$(curl -s "$BASE/api/bookings/$BOOKING_ID" -H "Authorization: Bearer $USER_TOKEN")
echo "  결제 취소 후 예약 상태: $(json_str "$R" status)"

# ---------- 결과 ----------
echo ""
echo "=== E2E 결과: PASS $PASS / FAIL $FAIL ==="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
