export function normalizePostUrl(text) {
  const match = String(text ?? '').match(/https?:\/\/[^\s<>"']+/i);
  if (!match) throw new Error('인스타그램 게시물 링크를 붙여넣어 주세요.');
  let url;
  try { url = new URL(match[0]); } catch { throw new Error('올바른 링크를 입력해 주세요.'); }
  if (!['instagram.com', 'www.instagram.com', 'm.instagram.com'].includes(url.hostname) || url.username || url.password || url.port) {
    throw new Error('instagram.com 게시물 링크만 사용할 수 있어요.');
  }
  const path = url.pathname.match(/^\/(p|reel|reels|tv)\/([A-Za-z0-9_-]+)\/?$/);
  if (!path) throw new Error('사진 게시물 또는 릴스의 링크를 사용해 주세요. (프로필·스토리·공유 단축 링크 제외)');
  return `https://www.instagram.com/${path[1] === 'reels' ? 'reel' : path[1]}/${path[2]}/`;
}

export function isMediaUrl(value) {
  try {
    const u = new URL(value);
    return u.protocol === 'https:' && !u.username && !u.password && !u.port &&
      ['cdninstagram.com', 'fbcdn.net', 'instagram.com'].some(d => u.hostname === d || u.hostname.endsWith(`.${d}`));
  } catch { return false; }
}

export function normalizeMedia(items) {
  const seen = new Set();
  return (Array.isArray(items) ? items : []).filter(item => {
    if (!item || !isMediaUrl(item.url) || !['image', 'video'].includes(item.type)) return false;
    const key = new URL(item.url).pathname;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  }).map(item => ({ url: item.url, type: item.type, thumbnail: isMediaUrl(item.thumbnail) ? item.thumbnail : '', selected: true }));
}

export function progressLabel(item) {
  if (item.status === 'complete') return '저장 완료';
  if (item.status === 'failed') return item.reason || '저장 실패 · 링크를 다시 다운로드해 주세요';
  if (item.status === 'missing') return '파일 또는 다운로드 기록이 없어요';
  if (item.status === 'paused') return '연결 대기 중';
  const percent = item.total > 0 ? Math.min(100, Math.floor(item.bytes / item.total * 100)) : 0;
  return item.status === 'running' ? `저장 중${percent ? ` · ${percent}%` : ''}` : '다운로드 대기 중';
}

export function loadHistory(storage) {
  try {
    const rows = JSON.parse(storage.getItem('pocket.history') || '[]');
    return Array.isArray(rows) ? rows.filter(x => x && typeof x.id === 'string' && typeof x.name === 'string').slice(0, 200) : [];
  } catch { return []; }
}
