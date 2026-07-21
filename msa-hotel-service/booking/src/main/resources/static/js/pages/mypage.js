/**
 * pages/mypage.js — 마이페이지: 쿠폰 탭 / 리뷰 탭 (인증 필수)
 *  - 쿠폰: POST /api/coupons/{id}/issue (번호 직접 입력), GET /api/coupons/me
 *  - 리뷰: GET /api/reviews/me, PUT /api/reviews/{id}, DELETE /api/reviews/{id} (204)
 */
import { api } from '/js/api.js';
import { requireLogin, getUser, isAdmin } from '/js/auth.js';
import {
  fmtDate, fmtDateTime, stars, couponDiscountLabel, won, DISCOUNT_TYPE, esc,
} from '/js/format.js';
import {
  toast, openModal, closeModal, confirmDialog,
  emptyState, errorState, skeletonLines, withButtonLoading,
} from '/js/ui.js';

if (!requireLogin()) throw new Error('unauthenticated');

/* --- 프로필 헤더 --- */
const user = getUser();
const email = user?.email ?? '회원';
document.getElementById('profile-email').textContent = email;
document.getElementById('profile-avatar').textContent = email[0]?.toUpperCase() ?? 'G';
document.getElementById('profile-role').textContent = isAdmin() ? '관리자 계정' : '일반 회원';

/* --- 탭 전환 (?tab= 쿼리 지원) --- */
const tabsEl = document.getElementById('mypage-tabs');

function switchTab(name) {
  tabsEl.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === name));
  document.getElementById('tab-coupons').classList.toggle('hidden', name !== 'coupons');
  document.getElementById('tab-reviews').classList.toggle('hidden', name !== 'reviews');
  history.replaceState(null, '', '?tab=' + name);
  if (name === 'reviews') loadReviews();
}

tabsEl.addEventListener('click', (e) => {
  const tab = e.target.closest('.tab');
  if (tab) switchTab(tab.dataset.tab);
});

/* ==================== 쿠폰 탭 ==================== */

const couponList = document.getElementById('coupon-list');

function couponCard(c) {
  const expired = c.validUntil < new Date().toISOString().slice(0, 10);
  const dead = c.isUsed || expired;
  return `
    <div class="coupon-card ${dead ? 'used' : ''}">
      <div class="discount">${couponDiscountLabel(c)}</div>
      <div class="flex-1">
        <div class="flex items-center gap-2 mb-1">
          <strong>${esc(c.couponName)}</strong>
          ${c.isUsed ? '<span class="badge badge-gray">사용완료</span>'
            : expired ? '<span class="badge badge-gray">기간만료</span>'
            : '<span class="badge badge-gold">사용가능</span>'}
        </div>
        <p class="text-xs text-muted">
          ${DISCOUNT_TYPE[c.discountType] ?? c.discountType}
          ${c.minPrice ? ` · ${won(c.minPrice)} 이상 예약 시` : ''}
          · ${fmtDate(c.validFrom)} ~ ${fmtDate(c.validUntil)}
          ${c.usedAt ? ` · ${fmtDateTime(c.usedAt)} 사용` : ''}
        </p>
      </div>
    </div>`;
}

async function loadCoupons() {
  couponList.innerHTML = skeletonLines(4);
  try {
    const coupons = (await api('/api/coupons/me')) ?? [];
    couponList.innerHTML = coupons.length === 0
      ? emptyState('보유한 쿠폰이 없습니다.<br>쿠폰 번호를 등록해보세요.', '🎟️')
      : coupons.map(couponCard).join('');
  } catch (err) {
    couponList.innerHTML = errorState(err.message);
  }
}

document.getElementById('issue-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const input = document.getElementById('coupon-id-input');
  const couponId = input.value.trim();
  if (!couponId) { toast('쿠폰 번호를 입력해주세요.', 'warning'); return; }
  try {
    await withButtonLoading(document.getElementById('issue-btn'), () =>
      api(`/api/coupons/${couponId}/issue`, { method: 'POST' }));
    toast('쿠폰이 발급되었습니다!', 'success');
    input.value = '';
    loadCoupons();
  } catch (err) {
    // 존재하지 않는 쿠폰/소진/중복 발급 등 서버 메시지 그대로 노출
    toast(err.message, 'error');
  }
});

/* ==================== 리뷰 탭 ==================== */

const reviewList = document.getElementById('review-list');
let myReviews = [];
let editing = null; // 수정 중인 리뷰
let editRating = 5;

function reviewCard(r) {
  return `
    <div class="card">
      <div class="card-body">
        <div class="flex items-center justify-between flex-wrap gap-2 mb-2">
          <strong class="font-serif">${esc(r.roomProductName)}</strong>
          <span class="text-xs text-muted">${fmtDateTime(r.createdAt)}</span>
        </div>
        <div class="mb-2">${stars(r.rating)}</div>
        <p class="text-sm mb-3" style="white-space:pre-line">${esc(r.content ?? '')}</p>
        <div class="flex gap-2 justify-between items-center">
          ${r.isVisible === false ? '<span class="badge badge-gray">비공개 처리됨</span>' : '<span></span>'}
          <div class="flex gap-2">
            <button class="btn btn-outline btn-sm" data-edit="${r.id}">수정</button>
            <button class="btn btn-danger-outline btn-sm" data-delete="${r.id}">삭제</button>
          </div>
        </div>
      </div>
    </div>`;
}

async function loadReviews() {
  reviewList.innerHTML = skeletonLines(4);
  try {
    myReviews = (await api('/api/reviews/me')) ?? [];
    reviewList.innerHTML = myReviews.length === 0
      ? emptyState('작성한 리뷰가 없습니다.<br>이용 완료된 예약에서 후기를 남겨보세요.', '📝')
      : myReviews.map(reviewCard).join('');
  } catch (err) {
    reviewList.innerHTML = errorState(err.message);
  }
}

const editStarInput = document.getElementById('edit-star-input');

function paintEditStars() {
  editStarInput.querySelectorAll('button').forEach((btn) =>
    btn.classList.toggle('on', Number(btn.dataset.v) <= editRating));
}

editStarInput.addEventListener('click', (e) => {
  const btn = e.target.closest('button');
  if (!btn) return;
  editRating = Number(btn.dataset.v);
  paintEditStars();
});

// 목록의 수정/삭제 버튼 위임 처리
reviewList.addEventListener('click', async (e) => {
  const editBtn = e.target.closest('[data-edit]');
  const deleteBtn = e.target.closest('[data-delete]');

  if (editBtn) {
    editing = myReviews.find((r) => r.id === Number(editBtn.dataset.edit));
    editRating = editing.rating;
    document.getElementById('edit-review-content').value = editing.content ?? '';
    paintEditStars();
    openModal('edit-review-modal');
  }

  if (deleteBtn) {
    const ok = await confirmDialog({
      title: '리뷰 삭제',
      message: '이 리뷰를 삭제하시겠습니까? 삭제 후에는 되돌릴 수 없습니다.',
      confirmText: '삭제',
      danger: true,
    });
    if (!ok) return;
    try {
      // 리뷰 삭제는 204 No Content — api()가 null을 반환한다
      await api(`/api/reviews/${deleteBtn.dataset.delete}`, { method: 'DELETE' });
      toast('리뷰가 삭제되었습니다.', 'success');
      loadReviews();
    } catch (err) {
      toast(err.message, 'error');
    }
  }
});

document.getElementById('edit-review-submit').addEventListener('click', async (e) => {
  const content = document.getElementById('edit-review-content').value.trim();
  if (!content) { toast('후기 내용을 입력해주세요.', 'warning'); return; }
  try {
    await withButtonLoading(e.currentTarget, () =>
      api(`/api/reviews/${editing.id}`, { method: 'PUT', body: { rating: editRating, content } }));
    closeModal('edit-review-modal');
    toast('리뷰가 수정되었습니다.', 'success');
    loadReviews();
  } catch (err) {
    toast(err.message, 'error');
  }
});

/* --- 초기 로드 --- */
const initialTab = new URLSearchParams(location.search).get('tab');
if (initialTab === 'reviews') switchTab('reviews');
loadCoupons();
