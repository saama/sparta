/**
 * pages/admin/stock.js — 객실 재고 등록
 *  - POST /api/admin/rooms/{id}/stock {startDate, endDate, stock}
 *  - 등록 후 같은 기간을 공개 조회 API로 다시 조회해 잔여 재고(minStock)를 확인시켜준다
 */
import '/js/pages/admin/admin-guard.js';
import { api } from '/js/api.js';
import { todayStr, tomorrowStr, fmtDate, esc } from '/js/format.js';
import { toast, withButtonLoading } from '/js/ui.js';

const roomSelect = document.getElementById('s-room');
const startInput = document.getElementById('s-start');
const endInput = document.getElementById('s-end');

startInput.value = todayStr();
endInput.value = tomorrowStr();

/** 객실 셀렉트 채우기 (공개 목록 API 재활용) */
async function loadRooms() {
  try {
    const rooms = await api(`/api/rooms?arrDate=${todayStr()}&depDate=${tomorrowStr()}&guestCount=1`);
    roomSelect.innerHTML = '<option value="">객실을 선택하세요</option>' +
      (rooms ?? []).map((r) => `<option value="${r.id}">#${r.id} ${esc(r.name)}</option>`).join('');
    // rooms.html에서 "재고" 버튼으로 넘어온 경우 해당 객실 미리 선택
    const preset = new URLSearchParams(location.search).get('roomId');
    if (preset) roomSelect.value = preset;
  } catch {
    toast('객실 목록을 불러오지 못했습니다. 재고가 있는 객실만 목록에 표시됩니다 — 객실 ID를 아는 경우 관리자 객실 페이지에서 이동하세요.', 'warning');
  }
}

/** 등록 직후 실제 조회로 재고 반영 확인 */
async function verify(roomId, startDate, endDate) {
  const panel = document.getElementById('verify-panel');
  const body = document.getElementById('verify-body');
  panel.classList.remove('hidden');
  body.innerHTML = '<div class="skeleton skeleton-line"></div>';
  try {
    const rooms = await api(`/api/rooms?arrDate=${startDate}&depDate=${endDate}&guestCount=1`);
    const room = (rooms ?? []).find((r) => r.id === Number(roomId));
    body.innerHTML = room
      ? `<p class="text-sm">✅ <strong>${esc(room.name)}</strong> — ${fmtDate(startDate)} ~ ${fmtDate(endDate)} 기간
         최소 잔여 <strong class="text-gold">${room.minStock}실</strong>로 검색에 노출됩니다.</p>`
      : `<p class="text-sm text-danger">해당 기간 조회 결과에 객실이 보이지 않습니다. 기간/재고를 다시 확인해주세요.</p>`;
  } catch (err) {
    body.innerHTML = `<p class="text-sm text-danger">확인 조회 실패: ${esc(err.message)}</p>`;
  }
}

document.getElementById('stock-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const roomId = roomSelect.value;
  const startDate = startInput.value;
  const endDate = endInput.value;
  const stock = Number(document.getElementById('s-stock').value);

  if (!roomId) { toast('객실을 선택해주세요.', 'warning'); return; }
  if (endDate < startDate) { toast('종료일은 시작일 이후여야 합니다.', 'warning'); return; }

  try {
    await withButtonLoading(document.getElementById('stock-btn'), () =>
      api(`/api/admin/rooms/${roomId}/stock`, { method: 'POST', body: { startDate, endDate, stock } }));
    toast('재고가 등록되었습니다.', 'success');
    verify(roomId, startDate, endDate);
  } catch (err) {
    toast(err.message, 'error');
  }
});

loadRooms();
