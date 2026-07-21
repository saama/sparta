/**
 * pages/signup.js — 회원가입
 * 백엔드 DTO에 validation이 없으므로 클라이언트에서 형식 검증을 수행한다.
 */
import { api } from '/js/api.js';
import { isLoggedIn } from '/js/auth.js';
import { toast, withButtonLoading } from '/js/ui.js';

if (isLoggedIn()) location.replace('/index.html');

const form = document.getElementById('signup-form');
const alertBox = document.getElementById('signup-alert');

/** 필드별 검증 규칙: [필드 id, 검증 함수, 에러 메시지] */
const RULES = [
  ['username', (v) => /^[a-zA-Z0-9]{4,20}$/.test(v), '아이디는 영문/숫자 4~20자로 입력해주세요.'],
  ['name', (v) => v.length >= 2, '이름을 2자 이상 입력해주세요.'],
  ['phone', (v) => /^01[016789]-?\d{3,4}-?\d{4}$/.test(v), '휴대폰 번호 형식이 올바르지 않습니다. (예: 010-1234-5678)'],
  ['email', (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v), '이메일 형식이 올바르지 않습니다.'],
  ['password', (v) => v.length >= 8, '비밀번호는 8자 이상이어야 합니다.'],
  ['password2', (v) => v === document.getElementById('password').value, '비밀번호가 일치하지 않습니다.'],
];

function setFieldError(id, msg) {
  const input = document.getElementById(id);
  const errEl = document.querySelector(`.field-error[data-for="${id}"]`);
  input.classList.toggle('invalid', !!msg);
  if (errEl) errEl.textContent = msg ?? '';
}

/** 전체 필드 검증. 통과하면 true. */
function validate() {
  let ok = true;
  for (const [id, test, msg] of RULES) {
    const v = document.getElementById(id).value.trim();
    if (!test(v)) { setFieldError(id, msg); ok = false; }
    else setFieldError(id, '');
  }
  return ok;
}

// 입력 즉시 해당 필드 에러 해제 (재검증은 제출 시)
for (const [id] of RULES) {
  document.getElementById(id).addEventListener('input', () => setFieldError(id, ''));
}

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  alertBox.classList.add('hidden');
  if (!validate()) return;

  const body = {
    username: document.getElementById('username').value.trim(),
    name: document.getElementById('name').value.trim(),
    phone: document.getElementById('phone').value.trim(),
    email: document.getElementById('email').value.trim(),
    password: document.getElementById('password').value,
  };

  const btn = document.getElementById('signup-btn');
  try {
    await withButtonLoading(btn, () =>
      api('/api/auth/registration', { method: 'POST', body, auth: false }));
    toast('회원가입이 완료되었습니다. 로그인해주세요.', 'success');
    setTimeout(() => (location.href = '/login.html'), 800);
  } catch (err) {
    // 중복 이메일 등 서버 에러 메시지를 그대로 노출
    alertBox.textContent = err.message || '회원가입에 실패했습니다.';
    alertBox.classList.remove('hidden');
  }
});
