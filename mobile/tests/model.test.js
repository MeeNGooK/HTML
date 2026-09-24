import test from 'node:test';
import assert from 'node:assert/strict';
import {normalizePostUrl, normalizeMedia, isMediaUrl, loadHistory, progressLabel} from '../src/model.js';

test('normalizes shared captions, reels and tracking parameters', () => {
  assert.equal(normalizePostUrl('함께 봐요 https://www.instagram.com/reels/Ab_c-12/?igsh=abc'), 'https://www.instagram.com/reel/Ab_c-12/');
  assert.equal(normalizePostUrl('https://instagram.com/p/ABC/'), 'https://www.instagram.com/p/ABC/');
});
test('rejects impersonation, credentials, ports, profiles and unsupported stories', () => {
  for (const url of ['https://instagram.com.evil.test/p/ABC/', 'https://instagram.com@evil.test/p/ABC/', 'https://user@instagram.com/p/ABC/', 'https://instagram.com:444/p/ABC/', 'https://instagram.com/person/', 'https://instagram.com/stories/person/123/', 'javascript:alert(1)', '', 'https://instagram.com/p/ABC/more']) assert.throws(() => normalizePostUrl(url), url);
});
test('media hosts require HTTPS and a domain boundary', () => {
  assert.ok(isMediaUrl('https://scontent.cdninstagram.com/v/image.jpg?x=1'));
  for (const url of ['http://scontent.cdninstagram.com/file', 'https://evilcdninstagram.com/file', 'https://fbcdn.net.evil.test/file', 'blob:123', 'file:///tmp/a']) assert.equal(isMediaUrl(url), false);
});
test('media normalization removes duplicates and rejects invalid types', () => {
  const url = 'https://scontent.cdninstagram.com/media.jpg';
  const items = normalizeMedia([{url,type:'image'}, {url:url+'?v=2',type:'image'}, {url,type:'script'}, {url:'https://evil.test/a',type:'video'}, null]);
  assert.equal(items.length,1); assert.equal(items[0].selected,true);
});
test('corrupt history is recoverable and statuses distinguish failure and success', () => {
  assert.deepEqual(loadHistory({getItem:()=>'{broken'}), []);
  assert.deepEqual(loadHistory({getItem:()=>'{}'}), []);
  assert.equal(progressLabel({status:'running',bytes:25,total:100}), '저장 중 · 25%');
  assert.equal(progressLabel({status:'complete'}), '저장 완료');
  assert.match(progressLabel({status:'failed'}), /실패/);
});
