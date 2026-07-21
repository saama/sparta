#!/usr/bin/env bash
# 측정 C: 객실 목록 조회 API의 Redis 캐시 미스 vs 히트 응답시간 비교
#
# 방식:
#  - 미스: 캐시 키(rooms::{arrDate}_{depDate}_{guestCount})가 매번 다르도록 arrDate를 바꿔가며 30회 호출
#  - 히트: 동일 파라미터로 30회 호출 (첫 호출로 워밍업 후 측정)
#  - curl의 %{time_total}로 응답시간(ms) 수집 후 평균/최대 계산
#
# 사전 조건: perf/seed.sh 실행 완료 (객실 500건 시딩), 앱이 8081에서 응답
# 사용법: bash perf/test-cache.sh
set -u
BASE="${BASE_URL:-http://localhost:8081}"
N="${N:-30}"

# 기존 객실 캐시 삭제 (rooms::* 키만 - refresh token 등 다른 키는 건드리지 않음)
docker exec redis sh -c "redis-cli --scan --pattern 'rooms::*' | xargs -r redis-cli del" >/dev/null 2>&1
echo "=== 캐시 측정 시작 (요청 ${N}회씩) ==="

measure() {  # $1=URL : 응답시간(ms) 1건 출력
  curl -s -o /dev/null -w "%{time_total}" "$1" | awk '{printf "%.1f\n", $1 * 1000}'
}
stats() {  # stdin=ms 목록 : 평균/최대 출력
  awk '{sum+=$1; if($1>max) max=$1} END {printf "평균 %.1f ms / 최대 %.1f ms\n", sum/NR, max}'
}

# ---------- 1. 캐시 미스: 매 요청 다른 날짜(=다른 캐시 키) ----------
MISS_FILE=$(mktemp)
for i in $(seq 1 "$N"); do
  ARR=$(date -d "2026-08-01 +$i day" +%F)
  DEP=$(date -d "2026-08-01 +$((i + 2)) day" +%F)
  measure "$BASE/api/rooms?arrDate=$ARR&depDate=$DEP&guestCount=2" >> "$MISS_FILE"
done
echo "[캐시 미스] $(stats < "$MISS_FILE")"

# ---------- 2. 캐시 히트: 동일 파라미터 반복 (워밍업 1회 후 측정) ----------
HIT_URL="$BASE/api/rooms?arrDate=2026-09-01&depDate=2026-09-03&guestCount=2"
curl -s -o /dev/null "$HIT_URL"   # 워밍업 (이 호출이 캐시를 채움)
HIT_FILE=$(mktemp)
for i in $(seq 1 "$N"); do
  measure "$HIT_URL" >> "$HIT_FILE"
done
echo "[캐시 히트] $(stats < "$HIT_FILE")"

# ---------- 3. 개선율 ----------
MISS_AVG=$(awk '{sum+=$1} END {print sum/NR}' "$MISS_FILE")
HIT_AVG=$(awk '{sum+=$1} END {print sum/NR}' "$HIT_FILE")
awk -v m="$MISS_AVG" -v h="$HIT_AVG" 'BEGIN {printf "[개선율] 평균 응답시간 %.1f ms -> %.1f ms (%.1f%% 단축, %.1f배 개선)\n", m, h, (m-h)/m*100, m/h}'
rm -f "$MISS_FILE" "$HIT_FILE"
