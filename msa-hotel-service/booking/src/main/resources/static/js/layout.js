/**
 * layout.js — 공통 헤더/푸터 DOM 주입
 *
 * 각 페이지 <body> 최상단에서 import하면:
 *  - <header> 를 body 맨 앞에, <footer> 를 body 맨 뒤에 삽입
 *  - 로그인 상태/어드민 여부에 따라 네비 구성이 달라진다
 *  - 현재 경로와 일치하는 메뉴에 active 클래스 부여
 */
import { isLoggedIn, isAdmin, getUser, logout } from '/js/auth.js';

function navLinks() {
  const path = location.pathname === '/' ? '/index.html' : location.pathname;
  const link = (href, label, cls = '') =>
    `<a href="${href}" class="${cls} ${path === href ? 'active' : ''}">${label}</a>`;

  let html = link('/index.html', '객실');

  if (isLoggedIn()) {
    const email = getUser()?.email ?? '';
    html += link('/bookings.html', '예약 내역');
    html += link('/mypage.html', '마이페이지');
    if (isAdmin()) html += link('/admin/rooms.html', '관리자', 'nav-cta');
    html += `<span class="nav-user text-xs" title="${email}">${email.split('@')[0]}님</span>`;
    html += `<button type="button" id="nav-logout">로그아웃</button>`;
  } else {
    html += link('/login.html', '로그인');
    html += link('/signup.html', '회원가입', 'nav-cta');
  }
  return html;
}

/** 별도 .ico 파일 없이 SVG 데이터 URI로 파비콘 주입 (전 페이지 공통) */
function injectFavicon() {
  const link = document.createElement('link');
  link.rel = 'icon';
  link.href = 'data:image/svg+xml,' + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">' +
    '<rect width="32" height="32" rx="6" fill="#1a2332"/>' +
    '<path d="M16 6 L26 16 L16 26 L6 16 Z" fill="#c8a45c"/></svg>'
  );
  document.head.appendChild(link);
}

function render() {
  injectFavicon();
  const header = document.createElement('header');
  header.className = 'site-header';
  header.innerHTML = `
    <div class="container">
      <a href="/index.html" class="brand"><span class="brand-mark">◆</span> GRAND STAY</a>
      <nav class="nav">${navLinks()}</nav>
    </div>`;
  document.body.prepend(header);

  const footer = document.createElement('footer');
  footer.className = 'site-footer';
  footer.innerHTML = `
    <div class="container">
      <span class="brand"><span class="brand-mark">◆</span> GRAND STAY</span>
      <span>Spring Boot · MySQL · Redis · Kafka 기반 호텔 예약 플랫폼 포트폴리오</span>
    </div>`;
  document.body.append(footer);

  document.getElementById('nav-logout')?.addEventListener('click', logout);
}

render();
