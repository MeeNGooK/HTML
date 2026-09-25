import { Capacitor, registerPlugin } from '@capacitor/core';
import { normalizePostUrl, normalizeMedia, progressLabel, loadHistory } from './model.js';
import './style.css';

const Native = registerPlugin('PocketMedia');
const native = Capacitor.isNativePlatform();
const icons = {
  pocket: '<path d="M5 4h14v9a7 7 0 0 1-14 0V4Z"/><path d="m8 10 4 4 4-4"/>',
  arrow: '<path d="M12 4v14m-5-5 5 5 5-5M5 20h14"/>',
  link: '<path d="m10 13 4-4m-6 7-1 1a4 4 0 0 1-6-6l4-4a4 4 0 0 1 6 0m2 1 1-1a4 4 0 0 1 6 6l-4 4a4 4 0 0 1-6 0" transform="translate(1 0)"/>',
  paste: '<rect x="6" y="5" width="14" height="16" rx="3"/><path d="M15 5V3H3v14h3"/>',
  photo: '<rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="8" cy="8" r="1"/><path d="m3 16 5-5 4 4 3-3 6 6"/>',
  play: '<rect x="3" y="3" width="18" height="18" rx="4"/><path d="m10 8 6 4-6 4V8Z"/>',
  settings: '<path d="M4 7h16M4 17h16"/><circle cx="9" cy="7" r="3"/><circle cx="15" cy="17" r="3"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  close: '<path d="m6 6 12 12M6 18 18 6"/>',
  chevron: '<path d="m9 5 7 7-7 7"/>',
};
const icon = (name, extra = '') => `<svg class="icon ${extra}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name] || icons.photo}</svg>`;
const escape = value => String(value).replace(/[&<>"']/g, x => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[x]));
let history = loadHistory(localStorage);
let busy = false;
let tab = 'home';
let filter = 'all';
let toastTimer;

document.querySelector('#app').innerHTML = `
  <div class="shell">
    <header class="header"><a class="brand" href="#" aria-label="Insta Pocket 홈"><span class="brand-mark">${icon('pocket')}</span><span>insta<span class="brand-light">pocket</span><small>SAVE YOUR LITTLE FINDS</small></span></a><button class="icon-button" id="settings" aria-label="설정">${icon('settings')}</button></header>
    <main id="home-page">
      <section class="hero"><span class="eyebrow"><span class="dot"></span> 마음에 든 순간, 내 폰에 쏙.</span><h1>발견은 인스타에서.<br>소장은 <span>포켓에.</span></h1><p>사진도, 릴스도. 링크 하나로<br>좋아하는 순간을 간직하세요.</p><div class="hero-art" aria-hidden="true"><div class="art-card back"><span>little moments</span><div class="sun"></div><div class="hill one"></div><div class="hill two"></div></div><div class="art-card front"><div class="art-sky"><div class="sun"></div><div class="mountain"></div><div class="mountain second"></div></div><span>KEEP THE GOOD STUFF <b>✦</b></span></div><div class="floating-download">${icon('arrow')}</div><span class="spark">✳</span></div></section>
      <section class="input-card"><div class="section-label"><span class="step">01</span><h2>링크를 넣어 주세요</h2><span class="tag">사진 · 동영상</span></div><form id="post-form"><label class="sr-only" for="post-input">인스타그램 게시물 링크</label><div class="url-field">${icon('link')}<input id="post-input" inputmode="url" type="text" placeholder="https://www.instagram.com/..." autocomplete="off" spellcheck="false"><button id="paste" type="button" aria-label="클립보드에서 붙여넣기">${icon('paste')}</button></div><p class="error" id="input-error" role="alert" hidden></p><button class="primary" id="find-button" type="submit">다운로드 ${icon('chevron')}</button></form><p class="input-hint">인스타그램의 <b>공유 → 링크 복사</b>로 가져오세요.</p></section>
      <section class="recent-section"><div class="section-heading"><h2>최근 저장</h2><button class="text-button" id="view-all">모두 보기 ${icon('chevron')}</button></div><div id="recent-list"></div></section>
      <section class="howto"><span class="howto-symbol">✳</span><div><h3>다음엔 공유 메뉴에서 바로</h3><p>인스타그램에서 공유할 때<br><b>Insta Pocket</b>을 선택해 보세요.</p></div></section>
      <p class="footnote">본인 소유이거나 저장 허락을 받은 콘텐츠를 이용하세요.<br>Instagram과 관련 없는 독립 앱입니다.</p>
    </main>
    <main id="library-page" hidden><div class="page-title"><span class="eyebrow">YOUR COLLECTION</span><h1>내 포켓</h1><p>좋아하는 순간들이 모이는 곳.</p></div><div class="filters"><button data-filter="all" class="active">전체</button><button data-filter="image">사진</button><button data-filter="video">동영상</button></div><p class="storage-note">저장 위치 · Download / InstaPocket</p><div id="history-list"></div></main>
    <nav class="bottom-nav" aria-label="주 메뉴"><button class="active" data-tab="home">${icon('pocket')}<span>저장하기</span></button><button data-tab="library">${icon('photo')}<span>내 포켓</span><span class="count" id="history-count">0</span></button></nav>
  </div>
  <dialog id="settings-dialog"><div class="dialog-heading"><h2>포켓 설정</h2><button class="icon-button" id="close-settings" aria-label="닫기">${icon('close')}</button></div><p>사진과 동영상은 이 기기의 <b>Download/InstaPocket</b>에 저장됩니다.</p><p><b>링크 붙여넣기 → 다운로드</b><br>공개 게시물의 사진·동영상을 자동으로 찾아 모두 저장합니다. 인스타그램 로그인은 사용하지 않습니다.</p><p>비공개 게시물과 스토리는 지원하지 않습니다. 공개 게시물이라도 인스타그램의 접근 제한이나 원본 주소 미제공으로 저장하지 못할 수 있습니다.</p><button class="text-button" id="clear-history">완료된 저장 기록 지우기 (파일 유지)</button><small>Insta Pocket 1.2 · Android 10 이상</small></dialog>
  <div id="toast" class="toast" role="status" hidden></div>`;

const $ = selector => document.querySelector(selector);
function toast(message) { $('#toast').textContent = message; $('#toast').hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => $('#toast').hidden = true, 4500); }
function saveHistory() { try { localStorage.setItem('pocket.history', JSON.stringify(history.slice(0, 200))); } catch { toast('저장 기록을 보관할 공간이 부족해요.'); } }
function setBusy(value) { busy = value; $('#find-button').disabled = value; $('#find-button').innerHTML = value ? '원본을 찾는 중…' : `다운로드 ${icon('chevron')}`; }
function switchTab(value) { tab = value; $('#home-page').hidden = value !== 'home'; $('#library-page').hidden = value !== 'library'; document.querySelectorAll('[data-tab]').forEach(el => el.classList.toggle('active', el.dataset.tab === value)); renderHistory(); window.scrollTo(0, 0); }
function historyMarkup(rows) {
  if (!rows.length) return `<div class="empty"><span class="empty-icon">${icon('pocket')}</span><h3>아직 비어 있는 포켓</h3><p>마음에 드는 게시물의 링크를 넣어<br>첫 번째 순간을 저장해 보세요.</p></div>`;
  return rows.map(item => `<button class="history-row" data-open="${escape(item.id)}" ${item.status === 'complete' ? '' : 'disabled'}><span class="file-icon ${item.type}">${icon(item.type === 'video' ? 'play' : 'photo')}</span><span class="file-details"><b>${escape(item.name)}</b><small class="${item.status === 'failed' ? 'failure' : ''}">${escape(progressLabel(item))}</small></span><span class="file-end">${item.status === 'complete' ? icon('check') : '<span class="status-dot"></span>'}</span></button>`).join('');
}
function renderHistory() { $('#recent-list').innerHTML = historyMarkup(history.slice(0, 3)); $('#history-list').innerHTML = historyMarkup(history.filter(x => filter === 'all' || x.type === filter)); $('#history-count').textContent = history.filter(x => x.status === 'complete').length; }
async function pollDownloads() {
  if (!native || !history.length) return;
  try {
    const { downloads } = await Native.getDownloads({ ids: history.map(x => x.id) });
    const map = new Map(downloads.map(x => [x.id, x]));
    let changed = false;
    history = history.map(item => { const update = map.get(item.id); if (!update) return item; if (update.status !== item.status || update.bytes !== item.bytes) changed = true; return {...item, ...update}; });
    if (changed) { saveHistory(); renderHistory(); }
  } catch { /* Retry on the next foreground refresh. */ }
}
async function acceptShared() { if (!native) return; const { text } = await Native.consumeSharedText(); if (text) { $('#post-input').value = text; switchTab('home'); toast('공유한 링크를 가져왔어요. 다운로드를 누르세요.'); } }

$('#post-form').addEventListener('submit', async event => {
  event.preventDefault(); if (busy) return;
  $('#input-error').hidden = true;
  let added = 0;
  try {
    const url = normalizePostUrl($('#post-input').value); $('#post-input').value = url;
    if (!native) { toast('다운로드는 Android 앱에서 사용할 수 있어요.'); return; }
    setBusy(true);
    const result = await Native.resolvePost({url});
    const items = normalizeMedia(result.items);
    if (!items.length) throw new Error('저장 가능한 원본을 찾지 못했어요.');
    $('#find-button').textContent = items.length + '개 파일 저장을 시작하는 중…';
    for (const item of items) {
      const saved = await Native.download({url:item.url, type:item.type, postUrl:url});
      history.unshift({...saved, type:item.type, status:'pending', createdAt:Date.now()});
      added++; saveHistory(); renderHistory();
    }
    toast(added + '개 파일 저장을 시작했어요. 내 포켓에서 확인하세요.');
    switchTab('library');
    await pollDownloads();
  } catch (error) {
    $('#input-error').textContent = (added ? added + '개는 저장을 시작했어요. 나머지 항목: ' : '') + (error.message || '다운로드하지 못했어요. 잠시 후 다시 시도해 주세요.');
    $('#input-error').hidden = false;
  } finally { setBusy(false); }
});
$('#paste').addEventListener('click', async () => { try { const text = native ? (await Native.readClipboard()).text : await navigator.clipboard.readText(); if (!text) return toast('클립보드가 비어 있어요.'); $('#post-input').value = text; $('#input-error').hidden = true; } catch { toast('입력칸을 길게 눌러 링크를 붙여넣어 주세요.'); } });
document.querySelectorAll('[data-tab]').forEach(el => el.addEventListener('click', () => switchTab(el.dataset.tab)));
document.querySelectorAll('[data-filter]').forEach(el => el.addEventListener('click', () => { filter = el.dataset.filter; document.querySelectorAll('[data-filter]').forEach(x => x.classList.toggle('active', x === el)); renderHistory(); }));
$('.brand').addEventListener('click', event => { event.preventDefault(); switchTab('home'); });
$('#view-all').addEventListener('click', () => switchTab('library'));
document.addEventListener('click', async event => { const row = event.target.closest('[data-open]'); if (!row || !native) return; try { await Native.openDownload({id:row.dataset.open}); } catch(error) { toast(error.message || '파일을 열 앱을 찾지 못했어요.'); } });
$('#settings').addEventListener('click', () => $('#settings-dialog').showModal());
$('#close-settings').addEventListener('click', () => $('#settings-dialog').close());
$('#clear-history').addEventListener('click', () => { history = history.filter(x => ['pending','running','paused'].includes(x.status)); saveHistory(); renderHistory(); toast('완료된 기록을 지웠어요. 저장한 파일은 유지됩니다.'); });
if (native) { Native.addListener('sharedText', () => acceptShared().catch(() => {})); Native.addListener('resume', () => { acceptShared().catch(() => {}); pollDownloads(); }); acceptShared().catch(() => {}); }
document.addEventListener('visibilitychange', () => { if (!document.hidden) { pollDownloads(); acceptShared().catch(() => {}); } });
setInterval(() => { if (!document.hidden) pollDownloads(); }, 2500);
renderHistory(); pollDownloads();
