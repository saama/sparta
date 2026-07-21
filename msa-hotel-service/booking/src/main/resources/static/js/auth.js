/**
 * auth.js — 토큰 저장/조회, JWT 디코딩, 페이지 접근 가드, 로그아웃
 *
 * localStorage 키:
 *  - hotel.accessToken / hotel.refreshToken : JWT 문자열
 *  - hotel.user : {userId, email} JSON (프로필 API가 없어 로그인 응답을 보관)
 *
 * 페이지 가드는 UI 편의용일 뿐이며, 실제 보안은 서버의 401/403이 담당한다.
 */

const KEY_ACCESS = 'hotel.accessToken';
const KEY_REFRESH = 'hotel.refreshToken';
const KEY_USER = 'hotel.user';

/** 로그인 응답(LoginResponse)을 저장한다. refresh 회전 시에도 재사용. */
export function saveTokens({ accessToken, refreshToken, userId, email }) {
  localStorage.setItem(KEY_ACCESS, accessToken);
  localStorage.setItem(KEY_REFRESH, refreshToken);
  if (userId != null) {
    localStorage.setItem(KEY_USER, JSON.stringify({ userId, email }));
  }
}

export function clearTokens() {
  localStorage.removeItem(KEY_ACCESS);
  localStorage.removeItem(KEY_REFRESH);
  localStorage.removeItem(KEY_USER);
}

export function getAccessToken() { return localStorage.getItem(KEY_ACCESS); }
export function getRefreshToken() { return localStorage.getItem(KEY_REFRESH); }

/** 로그인 시 저장한 {userId, email}. 비로그인 시 null. */
export function getUser() {
  try { return JSON.parse(localStorage.getItem(KEY_USER)); } catch { return null; }
}

/**
 * JWT payload를 디코딩한다 (서명 검증은 하지 않음 — 표시/분기 용도).
 * base64url → base64 변환 및 패딩을 처리한다.
 */
export function parseJwt(token) {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    // 한글 등 멀티바이트 클레임 대응 디코딩
    const json = decodeURIComponent(
      atob(padded).split('').map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0')).join('')
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

/** accessToken 존재 여부. 만료됐어도 true — 만료는 api.js의 refresh가 처리한다. */
export function isLoggedIn() { return !!getAccessToken(); }

/** JWT의 role 클레임으로 어드민 여부 판별 */
export function isAdmin() {
  const token = getAccessToken();
  if (!token) return false;
  return parseJwt(token)?.role === 'ADMIN';
}

/** 로그인 페이지로 이동하며, 로그인 후 돌아올 현재 URL을 redirect 쿼리로 넘긴다. */
export function gotoLogin() {
  const here = location.pathname + location.search;
  location.href = '/login.html?redirect=' + encodeURIComponent(here);
}

/** 인증 필수 페이지 최상단에서 호출. 비로그인 시 로그인으로 보내고 false 반환. */
export function requireLogin() {
  if (!isLoggedIn()) { gotoLogin(); return false; }
  return true;
}

/** 어드민 전용 페이지 가드. 비로그인→로그인, 일반 회원→메인으로 돌려보낸다. */
export function requireAdmin() {
  if (!isLoggedIn()) { gotoLogin(); return false; }
  if (!isAdmin()) {
    alert('관리자 전용 페이지입니다.');
    location.href = '/index.html';
    return false;
  }
  return true;
}

/** 서버 로그아웃(Redis refresh 삭제)을 시도하고, 실패 여부와 무관하게 토큰을 지운다. */
export async function logout() {
  const token = getAccessToken();
  if (token) {
    try {
      await fetch('/api/auth/logout', {
        method: 'POST',
        headers: { Authorization: 'Bearer ' + token },
      });
    } catch { /* 서버 로그아웃 실패는 무시 — 클라이언트 토큰 삭제가 우선 */ }
  }
  clearTokens();
  location.href = '/index.html';
}
