/**
 * pages/admin/coupons.js — 어드민 쿠폰 생성
 *  - POST /api/admin/coupons → CouponResponse(id 포함)
 *  - 쿠폰 목록 조회 API가 없으므로 생성 응답을 localStorage에 누적 보관하고,
 *    사용자에게 전달할 쿠폰 번호(id)를 복사 버튼으로 제공한다.
 */
import '/js/pages/admin/admin-guard.js';
import { api } from '/js/api.js';
import { won, todayStr, fmtDate, couponDiscountLabel, esc } from '/js/format.js';
import { toast, emptyState, withButtonLoading } from '/js/ui.js';

const HISTORY_KEY = 'hotel.admin.createdCoupons';
const tbody = document.getElementById('history-tbody');
const emptyEl = document.getElementById('history-empty');
const typeSelect = document.getElementById('c-discountType');

// 유효기간 기본값: 오늘부터 30일
document.getElementById('c-validFrom').value = todayStr();
const until = new Date();
until.setDate(until.getDate() + 30);
document.getElementById('c-validUntil').value = until.toISOString().slice(0, 10);

/* --- 할인 방식에 따라 단위/최대할인 필드 전환 --- */
function syncTypeUI() {
  const isPercent = typeSelect.value === 'PERCENT';
  document.getElementById('c-unit').textContent = isPercent ? '(%)' : '(원)';
  // 최대 할인 금액은 정률 할인에서만 의미가 있다
  document.getElementById('c-maxDiscount-wrap').classList.toggle('hidden', !isPercent);
}
typeSelect.addEventListener('change', syncTypeUI);
syncTypeUI();

/* --- 생성 이력 (localStorage) --- */

function getHistory() {
  try { return JSON.parse(localStorage.getItem(HISTORY_KEY)) ?? []; } catch { return []; }
}

function saveHistory(list) {
  localStorage.setItem(HISTORY_KEY, JSON.stringify(list));
}

function historyRow(c) {
  return `
    <tr>
      <td class="fw-bold text-gold">${c.id}</td>
      <td>${esc(c.code)}</td>
      <td>${esc(c.name)}</td>
      <td>${couponDiscountLabel(c)}${c.minPrice ? ` <span class="text-xs text-muted">(${won(c.minPrice)} 이상)</span>` : ''}</td>
      <td>${c.issueLimit ?? '무제한'}</td>
      <td class="text-xs">${fmtDate(c.validFrom)} ~ ${fmtDate(c.validUntil)}</td>
      <td><button class="btn btn-outline btn-sm" data-copy="${c.id}">번호 복사</button></td>
    </tr>`;
}

function renderHistory() {
  const list = getHistory();
  if (list.length === 0) {
    tbody.innerHTML = '';
    emptyEl.innerHTML = emptyState('아직 생성 이력이 없습니다.', '🎟️');
    return;
  }
  emptyEl.innerHTML = '';
  tbody.innerHTML = list.slice().reverse().map(historyRow).join('');
}

tbody.addEventListener('click', async (e) => {
  const btn = e.target.closest('[data-copy]');
  if (!btn) return;
  try {
    await navigator.clipboard.writeText(btn.dataset.copy);
    toast(`쿠폰 번호 ${btn.dataset.copy}가 복사되었습니다.`, 'success');
  } catch {
    toast('복사에 실패했습니다. 번호를 직접 확인해주세요: ' + btn.dataset.copy, 'warning');
  }
});

document.getElementById('clear-history-btn').addEventListener('click', () => {
  saveHistory([]);
  renderHistory();
});

/* --- 쿠폰 생성 --- */

document.getElementById('coupon-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const validFrom = document.getElementById('c-validFrom').value;
  const validUntil = document.getElementById('c-validUntil').value;
  if (validUntil < validFrom) { toast('유효기간 종료일은 시작일 이후여야 합니다.', 'warning'); return; }

  const isPercent = typeSelect.value === 'PERCENT';
  const maxDiscount = document.getElementById('c-maxDiscount').value;
  const issueLimit = document.getElementById('c-issueLimit').value;

  const body = {
    code: document.getElementById('c-code').value.trim(),
    name: document.getElementById('c-name').value.trim(),
    discountType: typeSelect.value,
    discountValue: Number(document.getElementById('c-discountValue').value),
    minPrice: Number(document.getElementById('c-minPrice').value),
    maxDiscount: isPercent && maxDiscount ? Number(maxDiscount) : null,
    issueLimit: issueLimit ? Number(issueLimit) : null,
    validFrom,
    validUntil,
  };
  if (isPercent && (body.discountValue < 1 || body.discountValue > 100)) {
    toast('정률 할인은 1~100% 사이로 입력해주세요.', 'warning');
    return;
  }

  try {
    const created = await withButtonLoading(document.getElementById('coupon-btn'), () =>
      api('/api/admin/coupons', { method: 'POST', body }));
    // 생성 응답을 이력에 보관 — 쿠폰 번호(id)가 사용자 발급의 열쇠
    saveHistory([...getHistory(), created]);
    renderHistory();
    toast(`쿠폰이 생성되었습니다. 쿠폰 번호: ${created.id}`, 'success');
    e.target.reset();
    document.getElementById('c-validFrom').value = todayStr();
    document.getElementById('c-validUntil').value = until.toISOString().slice(0, 10);
    syncTypeUI();
  } catch (err) {
    toast(err.message, 'error');
  }
});

renderHistory();
