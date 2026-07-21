/**
 * pages/admin/rooms.js — 어드민 객실 관리
 *  - 목록: GET /api/rooms?arrDate=오늘&depDate=내일&guestCount=1 (공개 API 재활용 — 어드민 목록 API 없음)
 *  - 등록: POST /api/admin/rooms / 수정: PUT /api/admin/rooms/{id} / 삭제: DELETE /api/admin/rooms/{id}
 *  - 등록/수정은 같은 모달을 재사용한다 (editingId로 구분)
 */
import '/js/pages/admin/admin-guard.js';
import { api } from '/js/api.js';
import { won, todayStr, tomorrowStr, badge, ROOM_TYPE, esc } from '/js/format.js';
import { toast, openModal, closeModal, confirmDialog, emptyState, errorState, withButtonLoading } from '/js/ui.js';

const tbody = document.getElementById('room-tbody');
const emptyEl = document.getElementById('room-empty');
let editingId = null; // null이면 등록 모드, 값이 있으면 수정 모드

/* --- 목록 --- */

function row(r) {
  return `
    <tr>
      <td>${r.id}</td>
      <td class="fw-bold">${esc(r.name)}</td>
      <td>${badge(ROOM_TYPE, r.roomType)}</td>
      <td>${won(r.price)}</td>
      <td>${r.baseCapacity} / ${r.maxCapacity}인</td>
      <td>${r.minStock ?? '-'}실</td>
      <td>
        <div class="flex gap-1">
          <button class="btn btn-outline btn-sm" data-edit="${r.id}">수정</button>
          <a class="btn btn-ghost btn-sm" href="/admin/stock.html?roomId=${r.id}">재고</a>
          <button class="btn btn-danger-outline btn-sm" data-delete="${r.id}" data-name="${esc(r.name)}">삭제</button>
        </div>
      </td>
    </tr>`;
}

async function load() {
  tbody.innerHTML = '<tr><td colspan="7"><div class="skeleton skeleton-line"></div></td></tr>';
  emptyEl.innerHTML = '';
  try {
    const rooms = await api(`/api/rooms?arrDate=${todayStr()}&depDate=${tomorrowStr()}&guestCount=1`);
    if (!rooms || rooms.length === 0) {
      tbody.innerHTML = '';
      emptyEl.innerHTML = emptyState('표시할 객실이 없습니다.<br>객실을 등록하고 재고를 넣어보세요.', '🛏️');
      return;
    }
    tbody.innerHTML = rooms.map(row).join('');
  } catch (err) {
    tbody.innerHTML = '';
    emptyEl.innerHTML = errorState(err.message);
  }
}

/* --- 모달 폼 --- */

const FIELDS = ['name', 'roomType', 'price', 'baseCapacity', 'maxCapacity', 'thumbnailUrl', 'description'];

function fillForm(room) {
  for (const f of FIELDS) {
    document.getElementById('f-' + f).value = room?.[f] ?? (f === 'roomType' ? 'STANDARD' : '');
  }
  if (!room) {
    document.getElementById('f-baseCapacity').value = 2;
    document.getElementById('f-maxCapacity').value = 2;
  }
  updateThumbPreview();
}

function updateThumbPreview() {
  const url = document.getElementById('f-thumbnailUrl').value.trim();
  const wrap = document.getElementById('thumb-preview-wrap');
  wrap.classList.toggle('hidden', !url);
  if (url) document.getElementById('thumb-preview').src = url;
}

document.getElementById('f-thumbnailUrl').addEventListener('input', updateThumbPreview);
document.getElementById('thumb-preview').addEventListener('error', () =>
  document.getElementById('thumb-preview-wrap').classList.add('hidden'));

document.getElementById('create-btn').addEventListener('click', () => {
  editingId = null;
  document.getElementById('room-modal-title').textContent = '객실 등록';
  fillForm(null);
  openModal('room-modal');
});

/* --- 테이블 액션 (이벤트 위임) --- */

tbody.addEventListener('click', async (e) => {
  const editBtn = e.target.closest('[data-edit]');
  const deleteBtn = e.target.closest('[data-delete]');

  if (editBtn) {
    try {
      // 수정 모드: 상세 조회로 최신 값 프리필
      const room = await api(`/api/rooms/${editBtn.dataset.edit}`);
      editingId = room.id;
      document.getElementById('room-modal-title').textContent = `객실 수정 — #${room.id}`;
      fillForm(room);
      openModal('room-modal');
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  if (deleteBtn) {
    const ok = await confirmDialog({
      title: '객실 삭제',
      message: `"${deleteBtn.dataset.name}" 객실을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.`,
      confirmText: '삭제',
      danger: true,
    });
    if (!ok) return;
    try {
      await api(`/api/admin/rooms/${deleteBtn.dataset.delete}`, { method: 'DELETE' });
      toast('객실이 삭제되었습니다.', 'success');
      load();
    } catch (err) {
      toast(err.message, 'error');
    }
  }
});

/* --- 저장 (등록/수정 공용) --- */

document.getElementById('room-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const body = {
    name: document.getElementById('f-name').value.trim(),
    roomType: document.getElementById('f-roomType').value,
    price: Number(document.getElementById('f-price').value),
    baseCapacity: Number(document.getElementById('f-baseCapacity').value),
    maxCapacity: Number(document.getElementById('f-maxCapacity').value),
    thumbnailUrl: document.getElementById('f-thumbnailUrl').value.trim() || null,
    description: document.getElementById('f-description').value.trim() || null,
  };
  if (body.maxCapacity < body.baseCapacity) {
    toast('최대 인원은 기준 인원 이상이어야 합니다.', 'warning');
    return;
  }

  try {
    await withButtonLoading(document.getElementById('room-save-btn'), () =>
      editingId
        ? api(`/api/admin/rooms/${editingId}`, { method: 'PUT', body })
        : api('/api/admin/rooms', { method: 'POST', body }));
    closeModal('room-modal');
    toast(editingId ? '객실이 수정되었습니다.' : '객실이 등록되었습니다. 재고를 등록해야 검색에 노출됩니다.', 'success');
    load();
  } catch (err) {
    toast(err.message, 'error');
  }
});

load();
