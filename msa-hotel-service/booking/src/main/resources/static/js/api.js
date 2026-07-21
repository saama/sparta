/**
 * api.js — 백엔드 호출 공통 래퍼
 *
 * 서버 응답 규약:
 *  - 성공: {"data": ...}  (Void면 빈 객체 {})
 *  - 실패: {"error": {"errorCode": "...", "errorMessage": "..."}}
 *  - 예외: DELETE /api/reviews/{id} 는 204 No Content (본문 없음)
 *
 * 동작:
 *  - auth=true(기본)면 Authorization: Bearer <accessToken> 자동 부착
 *  - 401 응답이면 refresh 토큰으로 재발급(동시 요청은 단일 비행으로 1회만) 후 원 요청 1회 재시도
 *  - refresh 실패 시 토큰을 지우고 로그인 페이지로 이동
 */
import { getAccessToken, getRefreshToken, saveTokens, clearTokens, gotoLogin } from '/js/auth.js';

/** errorCode/HTTP 상태를 실어 나르는 API 전용 에러 */
export class ApiError extends Error {
  constructor(errorCode, errorMessage, status) {
    super(errorMessage || errorCode || `HTTP ${status}`);
    this.errorCode = errorCode;
    this.status = status;
  }
}

// 동시에 여러 요청이 401을 받아도 refresh는 한 번만 수행하기 위한 공유 Promise
let refreshPromise = null;

/** refresh 토큰으로 새 토큰 쌍을 발급받아 저장한다(회전). 실패 시 throw. */
async function refreshTokens() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) throw new ApiError('NO_REFRESH_TOKEN', '로그인이 필요합니다.', 401);

  const res = await fetch('/api/auth/refresh', {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + refreshToken },
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok || json.error) {
    throw new ApiError(json.error?.errorCode ?? 'REFRESH_FAILED', json.error?.errorMessage, res.status);
  }
  saveTokens(json.data); // LoginResponse: 새 access/refresh + userId/email
  return json.data.accessToken;
}

/**
 * @param {string} path  '/api/...' 경로
 * @param {object} [opt]
 * @param {string}  [opt.method='GET']
 * @param {object}  [opt.body]        JSON 직렬화되어 전송
 * @param {boolean} [opt.auth=true]   Bearer 자동 부착 여부 (공개 API는 false 가능하나 true여도 무해)
 * @param {boolean} [opt._retried]    내부용 — refresh 후 재시도 여부
 * @returns 성공 시 json.data (204면 null)
 */
export async function api(path, { method = 'GET', body, auth = true, _retried = false } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = getAccessToken();
  if (auth && token) headers['Authorization'] = 'Bearer ' + token;

  let res;
  try {
    res = await fetch(path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    // 서버 다운/네트워크 단절
    throw new ApiError('NETWORK_ERROR', '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.', 0);
  }

  // 액세스 토큰 만료 등 401 → refresh 후 1회 재시도
  if (res.status === 401 && auth && !_retried && getRefreshToken()) {
    try {
      refreshPromise = refreshPromise ?? refreshTokens();
      await refreshPromise;
    } catch {
      clearTokens();
      gotoLogin();
      throw new ApiError('SESSION_EXPIRED', '세션이 만료되었습니다. 다시 로그인해주세요.', 401);
    } finally {
      refreshPromise = null;
    }
    return api(path, { method, body, auth, _retried: true });
  }

  if (res.status === 204) return null; // 리뷰 삭제 등 본문 없는 성공

  const json = await res.json().catch(() => null);
  if (json?.error) {
    throw new ApiError(json.error.errorCode, json.error.errorMessage, res.status);
  }
  if (!res.ok) {
    throw new ApiError('HTTP_' + res.status, '요청 처리에 실패했습니다.', res.status);
  }
  return json?.data ?? null;
}
