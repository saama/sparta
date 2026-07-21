#!/usr/bin/env bash
# 성능 측정용 데이터 시딩 스크립트
#
# 시딩 내용:
#  - room_product 500건 + room_stock 90일치(2026-08-01 ~ 2026-10-29, 각 10개) 약 45,000행
#  - 측정용 일반 유저 100명(perfuser1~100@perf.test) + 어드민 1명(perfadmin@perf.test)
#
# 사전 조건: 전체 스택 기동(msa-hotel-service/에서 docker compose up -d), 앱이 8081에서 응답
# 사용법: bash perf/seed.sh
set -u
BASE="${BASE_URL:-http://localhost:8081}"
PASSWORD="Password1!"

# ---------- 1. 객실/재고 시딩 (MySQL 직접 INSERT - 대량 데이터라 API 대신 SQL 사용) ----------
SEEDED=$(docker exec user-db mysql -uroot -prootpassword userdb -N \
  -e "SELECT COUNT(*) FROM room_product WHERE description='perf-seed'" 2>/dev/null)
if [ "${SEEDED:-0}" -ge 500 ]; then
  echo "[1] 객실 시딩 스킵 (이미 ${SEEDED}건 존재)"
else
  echo "[1] 객실 500건 + 재고 45,000행 시딩 중..."
  docker exec -i user-db mysql -uroot -prootpassword userdb 2>/dev/null <<'SQL'
-- 객실 상품 500건 (STANDARD/DELUXE/SUITE 순환, 가격 10만~22만 분포)
INSERT INTO room_product (name, room_type, price, base_capacity, max_capacity, description)
WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 500)
SELECT CONCAT('Perf Room ', i),
       ELT(1 + (i % 3), 'STANDARD', 'DELUXE', 'SUITE'),
       100000 + (i % 5) * 30000, 2, 4, 'perf-seed'
FROM n;

-- 시딩 객실 전체에 90일치 재고(각 10개) 생성: 500 x 90 = 45,000행
INSERT INTO room_stock (room_product_id, date, stock)
WITH RECURSIVE d AS (
  SELECT DATE '2026-08-01' AS dt
  UNION ALL SELECT dt + INTERVAL 1 DAY FROM d WHERE dt < DATE '2026-10-29'
)
SELECT rp.id, d.dt, 10
FROM room_product rp CROSS JOIN d
WHERE rp.description = 'perf-seed';
SQL
  echo "  완료: room_product $(docker exec user-db mysql -uroot -prootpassword userdb -N \
    -e "SELECT COUNT(*) FROM room_product WHERE description='perf-seed'" 2>/dev/null)건, room_stock $(docker exec user-db mysql -uroot -prootpassword userdb -N \
    -e "SELECT COUNT(*) FROM room_stock rs JOIN room_product rp ON rp.id=rs.room_product_id WHERE rp.description='perf-seed'" 2>/dev/null)행"
fi

# ---------- 2. 측정용 유저 100명 + 어드민 1명 회원가입 ----------
echo "[2] 측정용 계정 생성 (중복 가입은 무시)"
for i in $(seq 1 100); do
  curl -s -o /dev/null -X POST "$BASE/api/auth/registration" -H "Content-Type: application/json" \
    -d "{\"username\":\"perfuser$i\",\"name\":\"Perf User $i\",\"phone\":\"010-9000-$(printf '%04d' $i)\",\"email\":\"perfuser$i@perf.test\",\"password\":\"$PASSWORD\"}"
done
curl -s -o /dev/null -X POST "$BASE/api/auth/registration" -H "Content-Type: application/json" \
  -d "{\"username\":\"perfadmin\",\"name\":\"Perf Admin\",\"phone\":\"010-9999-0000\",\"email\":\"perfadmin@perf.test\",\"password\":\"$PASSWORD\"}"

# 어드민 권한 부여 (e2e 스크립트와 동일하게 DB 직접 변경)
docker exec user-db mysql -uroot -prootpassword userdb \
  -e "UPDATE user SET role='ADMIN' WHERE email='perfadmin@perf.test'" 2>/dev/null
USERS=$(docker exec user-db mysql -uroot -prootpassword userdb -N \
  -e "SELECT COUNT(*) FROM user WHERE email LIKE '%@perf.test'" 2>/dev/null)
echo "  완료: perf.test 계정 ${USERS}명 (어드민 권한 부여 포함)"
echo "=== 시딩 완료 ==="
