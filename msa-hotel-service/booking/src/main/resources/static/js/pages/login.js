/**
 * pages/login.js — 로그인 처리
 * 성공 시 redirect 쿼리가 있으면 그 페이지로, 없으면 메인으로 이동한다.
 */
import { api } from '/js/api.js';
import { saveTokens, isLoggedIn } from '/js/auth.js';
import { withButtonLoading } from '/js/ui.js';

// 이미 로그인된 상태로 접근하면 메인으로
if (isLoggedIn()) location.replace('/index.html');

const form = document.getElementById('login-form');
const alertBox = document.getElementById('login-alert');

function showAlert(msg) {
  alertBox.textContent = msg;
  alertBox.classList.remove('hidden');
}

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  alertBox.classList.add('hidden');

  const email = document.getElementById('email').value.trim();
  const password = document.getElementById('password').value;
  if (!email || !password) {
    showAlert('이메일과 비밀번호를 입력해주세요.');
    return;
  }

  const btn = document.getElementById('login-btn');
  try {
    await withButtonLoading(btn, async () => {
      // 로그인은 비인증 호출 (auth:false)
      const data = await api('/api/auth/login', { method: 'POST', body: { email, password }, auth: false });
      saveTokens(data);
    });
    // redirect 쿼리 왕복: 보호 페이지 → 로그인 → 원래 페이지 복귀
    const redirect = new URLSearchParams(location.search).get('redirect');
    // open redirect 방지: 내부 경로만 허용
    location.href = redirect && redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/index.html';
  } catch (err) {
    showAlert(err.message || '로그인에 실패했습니다.');
  }
});
