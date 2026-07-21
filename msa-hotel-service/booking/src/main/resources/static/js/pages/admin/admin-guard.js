/**
 * pages/admin/admin-guard.js — 어드민 페이지 공통 진입 가드 + 사이드바 주입
 *
 * 각 어드민 페이지에서 가장 먼저 import한다.
 *  - requireAdmin(): 비로그인 → 로그인 페이지, 일반 회원 → 메인으로 리다이렉트
 *  - #admin-side 요소에 공통 사이드바 메뉴를 렌더링 (현재 페이지 active)
 * 실제 데이터 보호는 서버의 /api/admin/** hasRole(ADMIN) 규칙이 담당한다.
 */
import { requireAdmin } from '/js/auth.js';

if (!requireAdmin()) throw new Error('not admin');

const MENUS = [
  ['/admin/rooms.html', '🛏️ 객실 관리'],
  ['/admin/stock.html', '📦 재고 등록'],
  ['/admin/coupons.html', '🎟️ 쿠폰 관리'],
];

const side = document.getElementById('admin-side');
if (side) {
  side.innerHTML =
    '<span class="side-title">Admin</span>' +
    MENUS.map(([href, label]) =>
      `<a href="${href}" class="${location.pathname === href ? 'active' : ''}">${label}</a>`).join('');
}
