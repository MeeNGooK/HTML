export function normalizePostUrl(text) {
  const match = String(text ?? '').match(/https?:\/\/(?:v\.douyin\.com|douyin\.com|www\.douyin\.com|iesdouyin\.com|www\.iesdouyin\.com)\/[^\s<>"'，。]+/i);
  if (!match) throw new Error('Douyin 공유 메시지에서 영상 링크를 찾지 못했어요.');
  let url;
  try { url = new URL(match[0]); } catch { throw new Error('올바른 링크를 입력해 주세요.'); }
  if (!['douyin.com', 'www.douyin.com', 'v.douyin.com', 'iesdouyin.com', 'www.iesdouyin.com'].includes(url.hostname) || url.username || url.password || url.port) {
    throw new Error('Douyin 영상 링크만 사용할 수 있어요.');
  }
  if (url.hostname === 'v.douyin.com') {
    if (!/^\/[A-Za-z0-9_-]+\/?$/.test(url.pathname)) throw new Error('Douyin 공유 링크 형식을 확인해 주세요.');
    return url.toString();
  }
  const path = url.pathname.match(/^\/(?:share\/)?(?:video|note|slides)\/(\d+)\/?$/);
  if (!path) throw new Error('Douyin 동영상 공유 링크를 사용해 주세요.');
  return `https://www.douyin.com/video/${path[1]}`;
}

export function isMediaUrl(value) {
  try {
    const u = new URL(value);
    return u.protocol === 'https:' && !u.username && !u.password && !u.port &&
      ['douyinvod.com', 'douyinvod.net', 'bytecdn.cn', 'douyin.com', 'douyinpic.com', 'zjcdn.com'].some(d => u.hostname === d || u.hostname.endsWith(`.${d}`));
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
  }).map(item => ({ url: item.url, type: item.type, quality: String(item.quality || ''), thumbnail: isMediaUrl(item.thumbnail) ? item.thumbnail : '', selected: true }));
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
