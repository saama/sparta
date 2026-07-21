/**
 * ui.js — 토스트, 모달, 스켈레톤, 빈 상태, 확인 다이얼로그, 버튼 로딩
 */

/* --- 토스트 --- */
function ensureToastContainer() {
  let c = document.querySelector('.toast-container');
  if (!c) {
    c = document.createElement('div');
    c.className = 'toast-container';
    document.body.appendChild(c);
  }
  return c;
}

/**
 * 우상단 토스트 알림. 3초 뒤 자동 소멸.
 * @param {'success'|'error'|'warning'|'info'} type
 */
export function toast(message, type = 'info') {
  const icons = { success: '✓', error: '✕', warning: '!', info: 'ℹ' };
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.innerHTML = `<span class="fw-bold">${icons[type] ?? ''}</span><span></span>`;
  el.lastElementChild.textContent = message; // textContent로 XSS 방지
  ensureToastContainer().appendChild(el);
  setTimeout(() => {
    el.classList.add('leaving');
    setTimeout(() => el.remove(), 300);
  }, 3000);
}

/* --- 모달 --- */

/** id의 .modal-backdrop을 연다. dim 클릭/ESC로 닫힌다. */
export function openModal(id) {
  const backdrop = document.getElementById(id);
  if (!backdrop) return;
  backdrop.classList.add('open');
  document.body.style.overflow = 'hidden';
}

export function closeModal(id) {
  const backdrop = document.getElementById(id);
  if (!backdrop) return;
  backdrop.classList.remove('open');
  document.body.style.overflow = '';
}

// dim 클릭·닫기 버튼·ESC 공통 처리 (모든 페이지에서 1회 등록)
document.addEventListener('click', (e) => {
  if (e.target.classList?.contains('modal-backdrop')) closeModal(e.target.id);
  const closer = e.target.closest?.('[data-close-modal]');
  if (closer) closeModal(closer.closest('.modal-backdrop')?.id);
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    document.querySelectorAll('.modal-backdrop.open').forEach((m) => closeModal(m.id));
  }
});

/**
 * window.confirm 대체 확인 모달. 확인 시 true를 resolve.
 * @param {{title?:string, message:string, confirmText?:string, danger?:boolean}} opt
 */
export function confirmDialog({ title = '확인', message, confirmText = '확인', danger = false }) {
  return new Promise((resolve) => {
    const id = 'confirm-dialog-' + Date.now();
    const wrap = document.createElement('div');
    wrap.className = 'modal-backdrop';
    wrap.id = id;
    wrap.innerHTML = `
      <div class="modal" role="dialog" aria-modal="true">
        <div class="modal-header"><h3></h3></div>
        <div class="modal-body"><p class="text-sm"></p></div>
        <div class="modal-footer">
          <button class="btn btn-ghost" data-act="cancel">취소</button>
          <button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" data-act="ok"></button>
        </div>
      </div>`;
    wrap.querySelector('h3').textContent = title;
    wrap.querySelector('p').textContent = message;
    wrap.querySelector('[data-act="ok"]').textContent = confirmText;
    document.body.appendChild(wrap);
    requestAnimationFrame(() => wrap.classList.add('open'));

    const done = (result) => {
      wrap.classList.remove('open');
      setTimeout(() => wrap.remove(), 200);
      document.body.style.overflow = '';
      resolve(result);
    };
    wrap.querySelector('[data-act="ok"]').onclick = () => done(true);
    wrap.querySelector('[data-act="cancel"]').onclick = () => done(false);
    wrap.onclick = (e) => { if (e.target === wrap) done(false); };
  });
}

/* --- 로딩/빈 상태 HTML 조각 --- */

/** 객실 카드 자리의 스켈레톤 n개 */
export function skeletonCards(n = 6) {
  return Array.from({ length: n }, () => '<div class="skeleton skeleton-card"></div>').join('');
}

/** 텍스트 줄 스켈레톤 블록 */
export function skeletonLines(n = 3) {
  return `<div>${Array.from({ length: n }, (_, i) =>
    `<div class="skeleton skeleton-line" style="width:${100 - i * 18}%"></div>`).join('')}</div>`;
}

export function emptyState(message, icon = '🏨') {
  return `<div class="empty-state"><div class="icon">${icon}</div><p>${message}</p></div>`;
}

/** 로드 실패 시 표시할 인라인 에러 + 재시도 안내 */
export function errorState(message) {
  return `<div class="empty-state"><div class="icon">⚠️</div><p>${message}</p>
    <button class="btn btn-outline btn-sm" onclick="location.reload()">다시 시도</button></div>`;
}

/* --- 이미지 fallback --- */

/**
 * 썸네일 이미지 HTML. url이 없으면 CSS 플레이스홀더, 로드 실패 시에도 교체.
 * (외부 이미지 URL이 죽어 있어도 레이아웃이 깨지지 않게 한다)
 */
export function thumbImg(url, alt = '') {
  const placeholder = '<div class="thumb-placeholder">HOTEL</div>';
  if (!url) return placeholder;
  const safe = String(url).replace(/"/g, '&quot;');
  return `<img src="${safe}" alt="${String(alt).replace(/"/g, '&quot;')}" loading="lazy"
    onerror="this.outerHTML='<div class=&quot;thumb-placeholder&quot;>HOTEL</div>'">`;
}

/* --- 버튼 로딩 상태 (중복 제출 방지) --- */

/**
 * 비동기 작업 동안 버튼을 비활성화하고 스피너를 표시한다.
 * @example await withButtonLoading(btn, () => api(...))
 */
export async function withButtonLoading(btn, fn) {
  const original = btn.innerHTML;
  btn.disabled = true;
  btn.innerHTML = '<span class="spinner"></span>';
  try {
    return await fn();
  } finally {
    btn.disabled = false;
    btn.innerHTML = original;
  }
}
