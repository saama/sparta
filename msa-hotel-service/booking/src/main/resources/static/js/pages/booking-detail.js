/**
 * pages/booking-detail.js — 예약 상세 (인증 필수)
 *  - 상태 타임라인 + 전체 정보 표시
 *  - PENDING/CONFIRMED → 취소 모달 (POST /api/bookings/{id}/cancel)
 *  - COMPLETED → 리뷰 작성 모달 (POST /api/reviews)
 */
import { api } from '/js/api.js';
import { requireLogin } from '/js/auth.js';
import { won, fmtDate, fmtDateTime, nights, badge, BOOKING_STATUS, esc } from '/js/format.js';
import { toast, openModal, closeModal, errorState, withButtonLoading } from '/js/ui.js';

if (!requireLogin()) throw new Error('unauthenticated');

const bookingId = new URLSearchParams(location.search).get('id');
const root = document.getElementById('detail-root');
let booking = null;
let reviewRating = 5;

if (!bookingId) {
  root.innerHTML = errorState('예약 정보를 찾을 수 없습니다.');
  throw new Error('missing id');
}

/** 상태 타임라인: 결제대기 → 예약확정 → 이용완료 (취소되면 취소 표시로 대체) */
function timeline(status) {
  if (status === 'CANCELLED') {
    return `<div class="flex items-center gap-3">${badge(BOOKING_STATUS, 'CANCELLED')}
      <span class="text-sm text-muted">${booking.cancelDate ? fmtDateTime(booking.cancelDate) + ' 취소' : ''}</span></div>`;
  }
  const steps = ['PENDING', 'CONFIRMED', 'COMPLETED'];
  const idx = steps.indexOf(status);
  return `<div class="timeline">${steps.map((s, i) => `
    ${i > 0 ? `<div class="bar ${i <= idx ? 'done' : ''}"></div>` : ''}
    <div class="step ${i <= idx ? 'done' : ''}">
      <div class="dot"></div>
      <span class="step-label">${BOOKING_STATUS[s].label}</span>
    </div>`).join('')}</div>`;
}

function render() {
  const b = booking;
  const n = nights(b.arrDate, b.depDate);
  const canCancel = b.status === 'PENDING' || b.status === 'CONFIRMED';
  const canReview = b.status === 'COMPLETED';

  root.innerHTML = `
    <div class="card mb-4">
      <div class="card-body">
        <div class="flex items-center justify-between flex-wrap gap-2 mb-4">
          <h2 class="font-serif">${esc(b.roomProductName)}</h2>
          ${badge(BOOKING_STATUS, b.status)}
        </div>
        ${timeline(b.status)}
      </div>
    </div>

    <div class="card mb-4">
      <div class="card-body flex flex-col gap-2">
        <h3 class="mb-2">예약 정보</h3>
        <div class="summary-row"><span class="label">예약 번호</span><span class="value">${esc(b.bookingNumber)}</span></div>
        <div class="summary-row"><span class="label">예약 일시</span><span class="value">${fmtDateTime(b.bookingDate)}</span></div>
        <div class="summary-row"><span class="label">체크인</span><span class="value">${fmtDate(b.arrDate)}</span></div>
        <div class="summary-row"><span class="label">체크아웃</span><span class="value">${fmtDate(b.depDate)} · ${n}박</span></div>
        <div class="summary-row"><span class="label">인원</span><span class="value">성인 ${b.adultCount}명${b.childCount ? ` · 아동 ${b.childCount}명` : ''}</span></div>
        <div class="summary-row"><span class="label">투숙객</span><span class="value">${esc(b.guestName)} (${esc(b.guestPhone)})</span></div>
        ${b.requestMemo ? `<div class="summary-row"><span class="label">요청 사항</span><span class="value">${esc(b.requestMemo)}</span></div>` : ''}
        <hr class="divider" style="margin:8px 0">
        <div class="summary-row total"><span class="label">결제 금액</span><span class="value">${won(b.totPrice)}</span></div>
      </div>
    </div>

    ${b.status === 'CANCELLED' && b.cancelReason ? `
    <div class="card mb-4"><div class="card-body">
      <h3 class="mb-2">취소 사유</h3>
      <p class="text-sm text-muted">${esc(b.cancelReason)}</p>
    </div></div>` : ''}

    <div class="flex gap-3 justify-center flex-wrap">
      ${b.status === 'PENDING' ? `<a class="btn btn-primary" href="/payment.html?bookingId=${b.id}">결제하기</a>` : ''}
      ${canReview ? `<button class="btn btn-primary" id="open-review-btn">리뷰 작성</button>` : ''}
      ${canCancel ? `<button class="btn btn-danger-outline" id="open-cancel-btn">예약 취소</button>` : ''}
    </div>`;

  document.getElementById('open-cancel-btn')?.addEventListener('click', () => {
    // 결제 완료(CONFIRMED) 건은 환불 안내 추가
    document.getElementById('cancel-notice').textContent =
      b.status === 'CONFIRMED'
        ? '예약을 취소하시겠습니까? 완료된 결제는 환불 처리됩니다.'
        : '예약을 취소하시겠습니까?';
    openModal('cancel-modal');
  });
  document.getElementById('open-review-btn')?.addEventListener('click', () => openModal('review-modal'));
}

/* --- 예약 취소 --- */

document.getElementById('cancel-confirm-btn').addEventListener('click', async (e) => {
  const reason = document.getElementById('cancel-reason').value.trim();
  try {
    booking = await withButtonLoading(e.currentTarget, () =>
      api(`/api/bookings/${bookingId}/cancel`, { method: 'POST', body: { cancelReason: reason || '고객 요청' } }));
    closeModal('cancel-modal');
    toast('예약이 취소되었습니다.', 'success');
    render();
  } catch (err) {
    toast(err.message, 'error');
  }
});

/* --- 리뷰 작성 --- */

const starInput = document.getElementById('star-input');

function paintStars() {
  starInput.querySelectorAll('button').forEach((btn) =>
    btn.classList.toggle('on', Number(btn.dataset.v) <= reviewRating));
}

starInput.addEventListener('click', (e) => {
  const btn = e.target.closest('button');
  if (!btn) return;
  reviewRating = Number(btn.dataset.v);
  paintStars();
});

document.getElementById('review-submit-btn').addEventListener('click', async (e) => {
  const content = document.getElementById('review-content').value.trim();
  if (!content) { toast('후기 내용을 입력해주세요.', 'warning'); return; }
  try {
    await withButtonLoading(e.currentTarget, () =>
      api('/api/reviews', { method: 'POST', body: { bookingId: Number(bookingId), rating: reviewRating, content } }));
    closeModal('review-modal');
    toast('소중한 후기가 등록되었습니다.', 'success');
  } catch (err) {
    // 이미 작성한 예약 등 서버 메시지 그대로 안내
    toast(err.message, 'error');
  }
});

/* --- 초기 로드 --- */
(async () => {
  try {
    booking = await api(`/api/bookings/${bookingId}`);
    render();
    paintStars();
  } catch (err) {
    root.innerHTML = errorState(err.message);
  }
})();
