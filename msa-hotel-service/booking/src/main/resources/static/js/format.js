/**
 * format.js — 금액/날짜 포맷과 서버 enum → 한글 라벨/배지 색 매핑
 */

/** 12345 → "₩12,345" */
export function won(n) {
  if (n == null) return '-';
  return '₩' + Number(n).toLocaleString('ko-KR');
}

/** Date → "YYYY-MM-DD" (input[type=date] 값 형식) */
export function toDateStr(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function todayStr() { return toDateStr(new Date()); }
export function tomorrowStr() {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return toDateStr(d);
}

/** "2026-07-19" → "2026.07.19 (일)" */
export function fmtDate(s) {
  if (!s) return '-';
  const d = new Date(s);
  const dow = ['일', '월', '화', '수', '목', '금', '토'][d.getDay()];
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')} (${dow})`;
}

/** ISO datetime → "2026.07.19 14:30" */
export function fmtDateTime(s) {
  if (!s) return '-';
  const d = new Date(s);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')} ` +
         `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

/** 체크인/아웃 날짜 문자열로 박수 계산 */
export function nights(arrDate, depDate) {
  const ms = new Date(depDate) - new Date(arrDate);
  return Math.max(0, Math.round(ms / 86400000));
}

/* --- enum 매핑 테이블: [한글 라벨, 배지 색 클래스] --- */

export const BOOKING_STATUS = {
  PENDING:   { label: '결제대기', badge: 'badge-amber' },
  CONFIRMED: { label: '예약확정', badge: 'badge-green' },
  CANCELLED: { label: '취소됨',   badge: 'badge-gray' },
  COMPLETED: { label: '이용완료', badge: 'badge-blue' },
};

export const PAYMENT_STATUS = {
  PENDING:          { label: '결제대기', badge: 'badge-amber' },
  PAID:             { label: '결제완료', badge: 'badge-green' },
  REFUNDED:         { label: '환불완료', badge: 'badge-gray' },
  PARTIAL_REFUNDED: { label: '부분환불', badge: 'badge-gray' },
  FAILED:           { label: '결제실패', badge: 'badge-red' },
};

export const ROOM_TYPE = {
  STANDARD: { label: '스탠다드', badge: 'badge-navy' },
  DELUXE:   { label: '디럭스',   badge: 'badge-gold' },
  SUITE:    { label: '스위트',   badge: 'badge-gold' },
};

export const PAYMENT_METHOD = {
  CARD:          { label: '신용/체크카드', icon: '💳' },
  BANK_TRANSFER: { label: '계좌이체',      icon: '🏦' },
  KAKAO_PAY:     { label: '카카오페이',    icon: '🟡' },
  NAVER_PAY:     { label: '네이버페이',    icon: '🟢' },
  TOSS:          { label: '토스',          icon: '🔵' },
};

export const DISCOUNT_TYPE = { FIXED: '정액 할인', PERCENT: '정률 할인' };

/** enum 값 → 배지 HTML. 매핑에 없는 값은 회색 배지로 표시. */
export function badge(map, value) {
  const m = map[value] ?? { label: value ?? '-', badge: 'badge-gray' };
  return `<span class="badge ${m.badge}">${m.label}</span>`;
}

/** 평점(1~5) → 별점 HTML (채워진 별 + 흐린 별) */
export function stars(rating) {
  const r = Math.max(0, Math.min(5, rating ?? 0));
  return `<span class="stars">${'★'.repeat(r)}<span class="off">${'★'.repeat(5 - r)}</span></span>`;
}

/** 쿠폰 할인 표기: FIXED → "₩5,000", PERCENT → "10%" */
export function couponDiscountLabel(coupon) {
  return coupon.discountType === 'PERCENT'
    ? `${coupon.discountValue}%`
    : won(coupon.discountValue);
}

/**
 * 쿠폰 예상 할인액 계산 (서버 로직 미리보기 용도 — 최종 금액은 서버 totPrice 기준)
 * PERCENT는 maxDiscount 상한을 적용한다.
 */
export function estimateDiscount(coupon, price) {
  if (!coupon) return 0;
  if (coupon.discountType === 'FIXED') return Math.min(coupon.discountValue, price);
  let d = Math.floor(price * coupon.discountValue / 100);
  if (coupon.maxDiscount != null) d = Math.min(d, coupon.maxDiscount);
  return d;
}

/** HTML 이스케이프 — 사용자 입력(리뷰 내용 등) 렌더링 시 XSS 방지 */
export function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}
