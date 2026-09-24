(() => {
  const items = [];
  const seen = new Set();
  const code = location.pathname.split('/')[2];
  let streaming = false;
  const valid = value => {
    try { const u = new URL(value); return u.protocol === 'https:' && !u.username && !u.password && !u.port && ['cdninstagram.com','fbcdn.net','instagram.com'].some(d => u.hostname === d || u.hostname.endsWith('.' + d)); } catch { return false; }
  };
  const add = (url, type, thumbnail = '') => {
    if (!valid(url) || items.length >= 100) return;
    const key = new URL(url).pathname;
    if (!seen.has(key)) { seen.add(key); items.push({url, type, thumbnail:valid(thumbnail) ? thumbnail : ''}); }
  };
  const best = candidates => Array.isArray(candidates) ? [...candidates].sort((a,b) => (b.width || 0) * (b.height || 0) - (a.width || 0) * (a.height || 0))[0]?.url : '';
  const readMedia = node => {
    if (!node || typeof node !== 'object') return;
    const image = best(node.image_versions2?.candidates) || node.display_url;
    const video = best(node.video_versions) || node.video_url;
    if (video) add(video, 'video', image);
    else if (image && node.media_type !== 2 && !node.is_video) add(image, 'image');
    (node.carousel_media || []).forEach(readMedia);
    (node.edge_sidecar_to_children?.edges || []).forEach(edge => readMedia(edge.node));
  };
  let walked = 0;
  const walk = (node, depth = 0) => {
    if (!node || typeof node !== 'object' || depth > 40 || ++walked > 80000) return;
    if (node.code === code || node.shortcode === code) { readMedia(node); return; }
    for (const value of Object.values(node)) if (value && typeof value === 'object') walk(value, depth + 1);
  };
  document.querySelectorAll('script[type="application/json"]').forEach(script => {
    if (script.textContent.includes(code)) { try { walk(JSON.parse(script.textContent)); } catch {} }
  });
  // Scope DOM fallback to the first post so recommendations and avatars are not collected.
  const root = document.querySelector('article') || document.querySelector('main');
  if (root && !items.length) {
    const videos = [...root.querySelectorAll('video')];
    videos.forEach(video => {
      const src = video.currentSrc || video.src;
      if (src?.startsWith('blob:')) streaming = true;
      add(src, 'video', video.poster);
    });
    // A video poster must never be reported as the downloadable video itself.
    if (!videos.length) root.querySelectorAll('img').forEach(img => {
      const rect = img.getBoundingClientRect();
      if (img.naturalWidth < 240 || img.naturalHeight < 240 || rect.width < 180 || rect.height < 180) return;
      if (rect.right < 0 || rect.left > innerWidth) return;
      add(img.currentSrc || img.src, 'image');
    });
  }
  return JSON.stringify({items, streaming});
})();
