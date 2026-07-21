/**
 * pages/booking.js — 예약 작성 (인증 필수)
 *  - GET /api/rooms/{roomId} : 객실 요약
 *  - GET /api/coupons/me     : 사용 가능 쿠폰만 필터해 select에 표시
 *  - POST /api/bookings      : 성공 시 payment.html?bookingId= 로 이동
 */
import { api } from '/js/api.js';
import { requireLogin } from '/js/auth.js';
import {
  won, todayStr, tomorrowStr, nights, fmtDate,
  badge, ROOM_TYPE, couponDiscountLabel, estimateDiscount, esc,
} from '/js/format.js';
import { toast, errorState, thumbImg, withButtonLoading } from '/js/ui.js';

if (!requireLogin()) throw new Error('unauthenticated');

const qs = new URLSearchParams(location.search);
const roomId = qs.get('roomId');

const summaryEl = document.getElementById('room-summary');
const form = document.getElementById('booking-form');
const arrInput = document.getElementById('arrDate');
const depInput = document.getElementById('depDate');
const adultSelect = document.getElementById('adultCount');
const childSelect = document.getElementById('childCount');
const couponSelect = document.getElementById('coupon-select');

let room = null;
let myCoupons = []; // 사용 가능 쿠폰 목록 (select와 index로 연결)

if (!roomId) {
  summaryEl.innerHTML = errorState('객실 정보가 없습니다. 객실 목록에서 다시 시도해주세요.');
  form.classList.add('hidden');
  throw new Error('missing roomId');
}

// 상세 페이지에서 넘어온 조건 프리필
arrInput.value = qs.get('arrDate') || todayStr();
depInput.value = qs.get('depDate') || tomorrowStr();
arrInput.min = todayStr();

/* --- 금액 계산 --- */

function basePrice() {
  const n = nights(arrInput.value, depInput.value);
  return n > 0 ? room.price * n : 0;
}

/** 선택된 쿠폰(없으면 null) */
function selectedCoupon() {
  const idx = couponSelect.value;
  return idx === '' ? null : myCoupons[Number(idx)];
}

function updatePrice() {
  if (!room) return;
  const n = nights(arrInput.value, depInput.value);
  const base = basePrice();
  document.getElementById('price-detail').textContent = n > 0 ? `${won(room.price)} × ${n}박` : '날짜를 확인해주세요';
  document.getElementById('base-price').textContent = n > 0 ? won(base) : '-';

  const coupon = selectedCoupon();
  const discountRow = document.getElementById('discount-row');
  let discount = 0;
  if (coupon && n > 0) {
    discount = estimateDiscount(coupon, base);
    discountRow.classList.remove('hidden');
    document.getElementById('discount-amount').textContent = `-${won(discount)}`;
  } else {
    discountRow.classList.add('hidden');
  }
  document.getElementById('total-price').textContent = n > 0 ? won(base - discount) : '-';
}

/* --- 쿠폰 로드: 미사용 + 유효기간 내 + 최소 주문금액 충족만 표시 --- */

async function loadCoupons() {
  try {
    const coupons = await api('/api/coupons/me');
    const today = todayStr();
    const base = basePrice();
    myCoupons = (coupons ?? []).filter((c) =>
      !c.isUsed &&
      c.validFrom <= today && today <= c.validUntil &&
      (c.minPrice == null || c.minPrice <= base)
    );
    if (myCoupons.length === 0) {
      document.getElementById('coupon-hint').textContent = '이 예약에 사용 가능한 쿠폰이 없습니다.';
      couponSelect.disabled = true;
      return;
    }
    couponSelect.innerHTML = '<option value="">사용 안 함</option>' +
      myCoupons.map((c, i) =>
        `<option value="${i}">${esc(c.couponName)} (${couponDiscountLabel(c)} 할인)</option>`).join('');
  } catch {
    // 쿠폰 로드 실패는 예약 자체를 막지 않는다
    document.getElementById('coupon-hint').textContent = '쿠폰 정보를 불러오지 못했습니다.';
    couponSelect.disabled = true;
  }
}

/* --- 객실 요약 렌더 --- */

function renderSummary() {
  summaryEl.innerHTML = `
    <div class="flex" style="gap:0">
      <div class="thumb" style="width:160px;flex-shrink:0;overflow:hidden">
        ${thumbImg(room.thumbnailUrl, room.name)}
      </div>
      <div class="card-body flex-1">
        <div class="mb-2">${badge(ROOM_TYPE, room.roomType)}</div>
        <h3 class="font-serif">${esc(room.name)}</h3>
        <p class="text-xs text-muted mt-1">기준 ${room.baseCapacity}인 · 최대 ${room.maxCapacity}인 · 1박 ${won(room.price)}</p>
      </div>
    </div>`;

  // 성인 1~max, 아동 0~(max-1) — 합계는 제출 시 maxCapacity로 검증
  adultSelect.innerHTML = Array.from({ length: room.maxCapacity }, (_, i) =>
    `<option value="${i + 1}">${i + 1}명</option>`).join('');
  childSelect.innerHTML = Array.from({ length: room.maxCapacity }, (_, i) =>
    `<option value="${i}">${i}명</option>`).join('');
  const wanted = Math.min(Number(qs.get('guestCount') || 2), room.maxCapacity);
  adultSelect.value = wanted;
}

/** 인원 합계가 최대 수용 인원을 넘지 않는지 검증 */
function validateCapacity() {
  const total = Number(adultSelect.value) + Number(childSelect.value);
  const over = total > room.maxCapacity;
  document.getElementById('capacity-error').textContent =
    over ? `최대 수용 인원(${room.maxCapacity}명)을 초과했습니다.` : '';
  return !over;
}

/* --- 제출 --- */

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  const n = nights(arrInput.value, depInput.value);
  if (n <= 0) { toast('체크아웃 날짜는 체크인 다음 날 이후여야 합니다.', 'warning'); return; }
  if (!validateCapacity()) return;

  const guestName = document.getElementById('guestName').value.trim();
  const guestPhone = document.getElementById('guestPhone').value.trim();
  if (!guestName || !guestPhone) { toast('투숙객 이름과 연락처를 입력해주세요.', 'warning'); return; }

  const body = {
    roomProductId: Number(roomId),
    arrDate: arrInput.value,
    depDate: depInput.value,
    adultCount: Number(adultSelect.value),
    childCount: Number(childSelect.value),
    guestName,
    guestPhone,
    requestMemo: document.getElementById('requestMemo').value.trim() || null,
    userCouponId: selectedCoupon()?.id ?? null,
  };

  const btn = document.getElementById('submit-btn');
  try {
    const booking = await withButtonLoading(btn, () =>
      api('/api/bookings', { method: 'POST', body }));
    toast('예약이 접수되었습니다. 결제를 진행해주세요.', 'success');
    location.href = `/payment.html?bookingId=${booking.id}`;
  } catch (err) {
    // 재고 소진/쿠폰 조건 미충족 등 서버 메시지를 그대로 안내
    toast(err.message, 'error');
  }
});

/* --- 이벤트 바인딩 --- */
arrInput.addEventListener('change', () => { updatePrice(); loadCoupons(); });
depInput.addEventListener('change', () => { updatePrice(); loadCoupons(); });
adultSelect.addEventListener('change', validateCapacity);
childSelect.addEventListener('change', validateCapacity);
couponSelect.addEventListener('change', updatePrice);

/* --- 초기 로드 --- */
(async () => {
  try {
    room = await api(`/api/rooms/${roomId}`, { auth: false });
    renderSummary();
    updatePrice();
    await loadCoupons();
  } catch (err) {
    summaryEl.innerHTML = errorState(err.message);
    form.classList.add('hidden');
  }
})();
