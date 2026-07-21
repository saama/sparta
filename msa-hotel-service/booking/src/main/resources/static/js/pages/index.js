/**
 * pages/index.js — 메인: 날짜/인원 검색 → 예약 가능 객실 그리드
 * GET /api/rooms?arrDate=&depDate=&guestCount= (공개 API)
 */
import { api } from '/js/api.js';
import { won, todayStr, tomorrowStr, nights, badge, ROOM_TYPE, esc } from '/js/format.js';
import { toast, skeletonCards, emptyState, errorState, thumbImg } from '/js/ui.js';

const grid = document.getElementById('room-grid');
const form = document.getElementById('search-form');
const summary = document.getElementById('result-summary');
const arrInput = document.getElementById('arrDate');
const depInput = document.getElementById('depDate');
const guestInput = document.getElementById('guestCount');

// URL 쿼리(뒤로가기로 돌아온 경우) → 없으면 오늘/내일 기본값
const qs = new URLSearchParams(location.search);
arrInput.value = qs.get('arrDate') || todayStr();
depInput.value = qs.get('depDate') || tomorrowStr();
if (qs.get('guestCount')) guestInput.value = qs.get('guestCount');
arrInput.min = todayStr();

/** 검색 조건 검증: 체크아웃은 체크인 다음 날 이후 */
function validDates() {
  if (!arrInput.value || !depInput.value) return false;
  return new Date(depInput.value) > new Date(arrInput.value);
}

/** 객실 카드 HTML — 카드 클릭 시 검색 조건을 유지한 채 상세로 이동 */
function roomCard(room) {
  const n = nights(arrInput.value, depInput.value);
  const detailUrl = `/room.html?id=${room.id}&arrDate=${arrInput.value}&depDate=${depInput.value}&guestCount=${guestInput.value}`;
  // 잔여 객실 2 이하이면 긴급 배지 표시
  const stockWarn = room.minStock != null && room.minStock <= 2
    ? `<span class="badge badge-red stock-warn">잔여 ${room.minStock}실</span>` : '';

  return `
    <a href="${detailUrl}" class="card card-hover room-card">
      <div class="thumb">
        ${thumbImg(room.thumbnailUrl, room.name)}
        ${badge(ROOM_TYPE, room.roomType)}
        ${stockWarn}
      </div>
      <div class="card-body">
        <h3 class="font-serif mb-2">${esc(room.name)}</h3>
        <p class="text-sm text-muted clamp-2 mb-3">${esc(room.description ?? '')}</p>
        <div class="flex items-center justify-between">
          <span class="text-xs text-muted">기준 ${room.baseCapacity}인 · 최대 ${room.maxCapacity}인</span>
          <span class="price">${won(room.price)}<span class="unit"> /박${n > 1 ? ` · ${n}박` : ''}</span></span>
        </div>
      </div>
    </a>`;
}

async function search() {
  if (!validDates()) {
    toast('체크아웃 날짜는 체크인 다음 날 이후여야 합니다.', 'warning');
    return;
  }
  grid.innerHTML = skeletonCards(6);
  summary.textContent = '';

  try {
    const rooms = await api(
      `/api/rooms?arrDate=${arrInput.value}&depDate=${depInput.value}&guestCount=${guestInput.value}`,
      { auth: false }
    );
    if (!rooms || rooms.length === 0) {
      grid.innerHTML = emptyState('선택하신 조건에 예약 가능한 객실이 없습니다.<br>날짜나 인원을 변경해보세요.');
      summary.textContent = '0개 객실';
      return;
    }
    grid.innerHTML = rooms.map(roomCard).join('');
    summary.textContent = `${rooms.length}개 객실 · ${nights(arrInput.value, depInput.value)}박`;
  } catch (err) {
    grid.innerHTML = errorState(err.message);
  }
}

form.addEventListener('submit', (e) => {
  e.preventDefault();
  // 검색 조건을 URL에 반영해 뒤로가기/공유 시에도 유지
  history.replaceState(null, '',
    `?arrDate=${arrInput.value}&depDate=${depInput.value}&guestCount=${guestInput.value}`);
  search();
});

// 체크인 변경 시 체크아웃 최소값 보정
arrInput.addEventListener('change', () => {
  const next = new Date(arrInput.value);
  next.setDate(next.getDate() + 1);
  const minDep = next.toISOString().slice(0, 10);
  depInput.min = minDep;
  if (depInput.value <= arrInput.value) depInput.value = minDep;
});

search(); // 첫 진입 시 기본 조건으로 즉시 검색
