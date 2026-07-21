/**
 * pages/room.js — 객실 상세 + 리뷰(페이징/평점 필터) + 예약 패널
 *  - GET /api/rooms/{id} (공개)
 *  - GET /api/rooms/{id}/reviews?rating=&page=&size=5 (공개, PageResult)
 */
import { api } from '/js/api.js';
import { isLoggedIn, gotoLogin } from '/js/auth.js';
import {
  won, fmtDate, fmtDateTime, todayStr, tomorrowStr, nights,
  badge, stars, ROOM_TYPE, esc,
} from '/js/format.js';
import { toast, emptyState, errorState, thumbImg, skeletonLines } from '/js/ui.js';

const qs = new URLSearchParams(location.search);
const roomId = qs.get('id');

const infoEl = document.getElementById('room-info');
const arrInput = document.getElementById('arrDate');
const depInput = document.getElementById('depDate');
const guestSelect = document.getElementById('guestCount');
const reserveBtn = document.getElementById('reserve-btn');
const reviewList = document.getElementById('review-list');
const reviewPagination = document.getElementById('review-pagination');
const ratingFilter = document.getElementById('rating-filter');

let room = null;
let reviewPage = 0;

if (!roomId) {
  infoEl.innerHTML = errorState('객실 정보를 찾을 수 없습니다.');
  throw new Error('missing room id');
}

// 검색 페이지에서 넘어온 조건 프리필 (없으면 오늘/내일)
arrInput.value = qs.get('arrDate') || todayStr();
depInput.value = qs.get('depDate') || tomorrowStr();
arrInput.min = todayStr();

/* --- 객실 정보 --- */

function renderRoom() {
  document.title = `${room.name} — GRAND STAY`;
  const inactive = room.isActive === false;

  infoEl.innerHTML = `
    <div class="card">
      <div class="thumb" style="aspect-ratio:16/8;overflow:hidden;position:relative">
        ${thumbImg(room.thumbnailUrl, room.name)}
      </div>
      <div class="card-body">
        <div class="flex items-center gap-3 mb-2">
          ${badge(ROOM_TYPE, room.roomType)}
          ${inactive ? '<span class="badge badge-red">판매 중지</span>' : ''}
        </div>
        <h1 class="mb-2">${esc(room.name)}</h1>
        <p class="text-sm text-muted mb-4">기준 ${room.baseCapacity}인 · 최대 ${room.maxCapacity}인 · 1박 ${won(room.price)}</p>
        <hr class="divider">
        <p class="text-sm" style="white-space:pre-line">${esc(room.description ?? '객실 소개가 준비 중입니다.')}</p>
      </div>
    </div>`;

  // 인원 셀렉트: 1 ~ maxCapacity
  guestSelect.innerHTML = Array.from({ length: room.maxCapacity }, (_, i) =>
    `<option value="${i + 1}">${i + 1}명</option>`).join('');
  const wanted = Number(qs.get('guestCount') || 2);
  guestSelect.value = Math.min(wanted, room.maxCapacity);

  reserveBtn.disabled = inactive;
  if (inactive) reserveBtn.textContent = '현재 판매 중지된 객실입니다';
  updatePrice();
}

/** 날짜/인원 변경 시 예상 금액 갱신 */
function updatePrice() {
  const n = nights(arrInput.value, depInput.value);
  const valid = n > 0;
  document.getElementById('price-per-night').textContent = `${won(room.price)} × ${valid ? n : '-'}박`;
  document.getElementById('nights-label').textContent = valid ? `${n}박` : '날짜 확인';
  document.getElementById('total-price').textContent = valid ? won(room.price * n) : '-';
  if (room.isActive !== false) reserveBtn.disabled = !valid;
}

/* --- 리뷰 --- */

function reviewItem(r) {
  return `
    <div class="review-item">
      <div class="flex items-center justify-between mb-2">
        <span>${stars(r.rating)}</span>
        <span class="text-xs text-muted">${fmtDateTime(r.createdAt)}</span>
      </div>
      <p class="text-sm" style="white-space:pre-line">${esc(r.content ?? '')}</p>
    </div>`;
}

async function loadReviews() {
  reviewList.innerHTML = skeletonLines(4);
  reviewPagination.innerHTML = '';
  try {
    const rating = ratingFilter.value;
    const page = await api(
      `/api/rooms/${roomId}/reviews?page=${reviewPage}&size=5${rating ? '&rating=' + rating : ''}`,
      { auth: false }
    );
    if (!page || page.content.length === 0) {
      reviewList.innerHTML = emptyState(rating ? '해당 평점의 후기가 없습니다.' : '아직 작성된 후기가 없습니다.', '📝');
      return;
    }
    reviewList.innerHTML = page.content.map(reviewItem).join('');
    // PageResult의 isFirst/isLast로 이동 버튼 활성화 제어
    reviewPagination.innerHTML = `
      <button class="btn btn-outline btn-sm" id="prev-page" ${page.isFirst ? 'disabled' : ''}>이전</button>
      <span class="text-sm text-muted">${page.pageNumber + 1} / ${page.totalPages} 페이지 · 총 ${page.totalElements}건</span>
      <button class="btn btn-outline btn-sm" id="next-page" ${page.isLast ? 'disabled' : ''}>다음</button>`;
    document.getElementById('prev-page').onclick = () => { reviewPage--; loadReviews(); };
    document.getElementById('next-page').onclick = () => { reviewPage++; loadReviews(); };
  } catch (err) {
    reviewList.innerHTML = errorState(err.message);
  }
}

ratingFilter.addEventListener('change', () => { reviewPage = 0; loadReviews(); });

/* --- 예약 이동 --- */

reserveBtn.addEventListener('click', () => {
  const n = nights(arrInput.value, depInput.value);
  if (n <= 0) { toast('체크아웃 날짜는 체크인 다음 날 이후여야 합니다.', 'warning'); return; }
  const target = `/booking.html?roomId=${roomId}&arrDate=${arrInput.value}&depDate=${depInput.value}&guestCount=${guestSelect.value}`;
  if (!isLoggedIn()) {
    toast('로그인 후 예약할 수 있습니다.', 'info');
    // 로그인 후 예약 페이지로 바로 복귀하도록 redirect 지정
    location.href = '/login.html?redirect=' + encodeURIComponent(target);
    return;
  }
  location.href = target;
});

arrInput.addEventListener('change', () => {
  if (depInput.value <= arrInput.value) {
    const next = new Date(arrInput.value);
    next.setDate(next.getDate() + 1);
    depInput.value = next.toISOString().slice(0, 10);
  }
  updatePrice();
});
depInput.addEventListener('change', updatePrice);

/* --- 초기 로드 --- */
(async () => {
  try {
    room = await api(`/api/rooms/${roomId}`, { auth: false });
    renderRoom();
  } catch (err) {
    infoEl.innerHTML = errorState(err.message);
    document.getElementById('booking-panel').classList.add('hidden');
  }
  loadReviews();
})();
