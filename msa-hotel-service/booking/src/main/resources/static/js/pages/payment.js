/**
 * pages/payment.js — 결제 + Kafka 비동기 예약 확정 폴링 (인증 필수)
 *
 * 흐름:
 *  1. GET /api/bookings/{id} 로 예약 요약 표시 (PENDING이 아니면 상세로 돌려보냄)
 *  2. POST /api/payments {bookingId, paymentMethod}
 *  3. 결제 성공(PAID) 후 예약 확정은 Kafka 컨슈머가 비동기로 처리하므로,
 *     GET /api/bookings/{id} 를 2초 간격 최대 15회(30초) 폴링:
 *       CONFIRMED → 성공 화면 / CANCELLED → 실패 안내 / 타임아웃 → 안내 톤으로 예약 내역 유도
 */
import { api } from '/js/api.js';
import { requireLogin } from '/js/auth.js';
import { won, fmtDate, nights, PAYMENT_METHOD, esc } from '/js/format.js';
import { toast, errorState, withButtonLoading } from '/js/ui.js';

if (!requireLogin()) throw new Error('unauthenticated');

const bookingId = new URLSearchParams(location.search).get('bookingId');
const summaryEl = document.getElementById('booking-summary');
const methodsEl = document.getElementById('pay-methods');
const payBtn = document.getElementById('pay-btn');

const POLL_INTERVAL_MS = 2000;
const POLL_MAX_TRIES = 15;

if (!bookingId) {
  summaryEl.innerHTML = errorState('결제할 예약 정보가 없습니다.');
  throw new Error('missing bookingId');
}

/* --- 예약 요약 --- */

function renderSummary(b) {
  const n = nights(b.arrDate, b.depDate);
  summaryEl.innerHTML = `
    <h2 class="mb-4">예약 정보</h2>
    <div class="flex flex-col gap-2">
      <div class="summary-row"><span class="label">예약 번호</span><span class="value">${esc(b.bookingNumber)}</span></div>
      <div class="summary-row"><span class="label">객실</span><span class="value">${esc(b.roomProductName)}</span></div>
      <div class="summary-row"><span class="label">일정</span><span class="value">${fmtDate(b.arrDate)} ~ ${fmtDate(b.depDate)} · ${n}박</span></div>
      <div class="summary-row"><span class="label">인원</span><span class="value">성인 ${b.adultCount}명${b.childCount ? ` · 아동 ${b.childCount}명` : ''}</span></div>
      <div class="summary-row"><span class="label">투숙객</span><span class="value">${esc(b.guestName)} (${esc(b.guestPhone)})</span></div>
      <hr class="divider" style="margin:8px 0">
      <div class="summary-row total"><span class="label">결제 금액</span><span class="value">${won(b.totPrice)}</span></div>
    </div>`;
}

/* --- 결제 수단 라디오 카드 --- */

function renderMethods() {
  methodsEl.innerHTML = Object.entries(PAYMENT_METHOD).map(([value, m], i) => `
    <label class="pay-method">
      <input type="radio" name="paymentMethod" value="${value}" ${i === 0 ? 'checked' : ''}>
      <span class="pm-card"><span class="pm-icon">${m.icon}</span>${m.label}</span>
    </label>`).join('');
  payBtn.disabled = false;
}

/* --- 확정 폴링 --- */

function show(stepId) {
  for (const id of ['pay-step', 'confirm-step', 'done-step']) {
    document.getElementById(id).classList.toggle('hidden', id !== stepId);
  }
  window.scrollTo(0, 0);
}

function showDone({ confirmed, booking, message }) {
  show('done-step');
  const title = document.getElementById('done-title');
  const msg = document.getElementById('done-message');
  document.getElementById('done-detail-link').href = `/booking-detail.html?id=${bookingId}`;
  if (confirmed) {
    title.textContent = '예약이 확정되었습니다';
    msg.textContent = `예약 번호 ${booking.bookingNumber} — 예약 내역에서 언제든 확인할 수 있습니다.`;
  } else {
    // 타임아웃: 결제는 끝났으므로 에러가 아니라 안내 톤으로 처리
    document.querySelector('.check-anim').style.background = 'var(--warning)';
    document.querySelector('.check-anim').textContent = '…';
    title.textContent = '결제가 완료되었습니다';
    msg.textContent = message;
  }
}

/** 결제 성공 후 예약 상태가 CONFIRMED로 바뀔 때까지 폴링 */
async function pollConfirmation() {
  show('confirm-step');
  for (let i = 0; i < POLL_MAX_TRIES; i++) {
    await new Promise((r) => setTimeout(r, POLL_INTERVAL_MS));
    try {
      const b = await api(`/api/bookings/${bookingId}`);
      if (b.status === 'CONFIRMED') { showDone({ confirmed: true, booking: b }); return; }
      if (b.status === 'CANCELLED') {
        show('pay-step');
        summaryEl.innerHTML = errorState('예약 확정에 실패하여 예약이 취소되었습니다. 결제는 환불 처리됩니다.');
        return;
      }
    } catch { /* 일시적 조회 실패는 다음 폴링에서 재시도 */ }
  }
  showDone({
    confirmed: false,
    message: '예약 확정 처리가 진행 중입니다. 잠시 후 예약 내역에서 확정 상태를 확인해주세요.',
  });
}

/* --- 결제 실행 --- */

payBtn.addEventListener('click', async () => {
  const method = document.querySelector('input[name="paymentMethod"]:checked')?.value;
  if (!method) { toast('결제 수단을 선택해주세요.', 'warning'); return; }

  try {
    const payment = await withButtonLoading(payBtn, () =>
      api('/api/payments', { method: 'POST', body: { bookingId: Number(bookingId), paymentMethod: method } }));
    if (payment.paymentStatus === 'PAID') {
      pollConfirmation();
    } else {
      toast('결제가 정상 처리되지 않았습니다. 다시 시도해주세요.', 'error');
    }
  } catch (err) {
    toast(err.message, 'error');
  }
});

/* --- 초기 로드 --- */
(async () => {
  try {
    const b = await api(`/api/bookings/${bookingId}`);
    if (b.status !== 'PENDING') {
      // 이미 결제/취소된 예약이면 결제 화면 대신 상세로 안내
      toast('결제 대기 상태의 예약이 아닙니다.', 'info');
      location.replace(`/booking-detail.html?id=${bookingId}`);
      return;
    }
    renderSummary(b);
    renderMethods();
  } catch (err) {
    summaryEl.innerHTML = errorState(err.message);
  }
})();
