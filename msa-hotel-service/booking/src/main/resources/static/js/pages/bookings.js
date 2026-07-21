/**
 * pages/bookings.js — 내 예약 목록 (인증 필수)
 * GET /api/bookings → 상태 탭으로 클라이언트 필터링.
 * PENDING 예약에는 "결제하기" 버튼을 노출한다 (결제 이탈 복구 경로).
 */
import { api } from '/js/api.js';
import { requireLogin } from '/js/auth.js';
import { won, fmtDate, nights, badge, BOOKING_STATUS, esc } from '/js/format.js';
import { emptyState, errorState, skeletonLines } from '/js/ui.js';

if (!requireLogin()) throw new Error('unauthenticated');

const listEl = document.getElementById('booking-list');
const tabs = document.getElementById('status-tabs');

let bookings = [];
let currentFilter = '';

function bookingCard(b) {
  const n = nights(b.arrDate, b.depDate);
  return `
    <div class="card card-hover">
      <div class="card-body">
        <div class="flex items-center justify-between flex-wrap gap-2 mb-3">
          <div class="flex items-center gap-3">
            ${badge(BOOKING_STATUS, b.status)}
            <span class="text-xs text-muted">예약번호 ${esc(b.bookingNumber)}</span>
          </div>
          <span class="text-xs text-muted">${fmtDate(b.bookingDate?.slice(0, 10))} 예약</span>
        </div>
        <h3 class="font-serif mb-2">${esc(b.roomProductName)}</h3>
        <p class="text-sm text-muted mb-3">
          ${fmtDate(b.arrDate)} ~ ${fmtDate(b.depDate)} · ${n}박 ·
          성인 ${b.adultCount}명${b.childCount ? ` · 아동 ${b.childCount}명` : ''}
        </p>
        <div class="flex items-center justify-between flex-wrap gap-3">
          <span class="price fw-bold" style="font-size:var(--fs-lg)">${won(b.totPrice)}</span>
          <div class="flex gap-2">
            ${b.status === 'PENDING'
              ? `<a class="btn btn-primary btn-sm" href="/payment.html?bookingId=${b.id}">결제하기</a>` : ''}
            <a class="btn btn-outline btn-sm" href="/booking-detail.html?id=${b.id}">상세 보기</a>
          </div>
        </div>
      </div>
    </div>`;
}

function render() {
  const filtered = currentFilter ? bookings.filter((b) => b.status === currentFilter) : bookings;
  if (filtered.length === 0) {
    listEl.innerHTML = emptyState(
      currentFilter ? '해당 상태의 예약이 없습니다.' : '아직 예약 내역이 없습니다.<br><a href="/index.html" class="text-gold fw-bold">객실 둘러보기</a>',
      '🛎️');
    return;
  }
  // 최근 예약이 위로 오도록 정렬
  listEl.innerHTML = filtered
    .slice()
    .sort((a, b) => (b.bookingDate ?? '').localeCompare(a.bookingDate ?? ''))
    .map(bookingCard).join('');
}

async function load() {
  listEl.innerHTML = skeletonLines(6);
  try {
    bookings = (await api('/api/bookings')) ?? [];
    render();
  } catch (err) {
    listEl.innerHTML = errorState(err.message);
  }
}

tabs.addEventListener('click', (e) => {
  const tab = e.target.closest('.tab');
  if (!tab) return;
  tabs.querySelectorAll('.tab').forEach((t) => t.classList.remove('active'));
  tab.classList.add('active');
  currentFilter = tab.dataset.status;
  render();
});

document.getElementById('refresh-btn').addEventListener('click', load);

load();
