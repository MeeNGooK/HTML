import test from 'node:test';
import assert from 'node:assert/strict';
import {normalizePostUrl, normalizeMedia, isMediaUrl, loadHistory, progressLabel} from '../src/model.js';

test('extracts the Douyin URL from shared caption text and normalizes full video links', () => {
  assert.equal(normalizePostUrl('6.61 复制打开抖音，看看【小敏小敏的作品】千万不能评论 # 谁还不是小腰精 https://v.douyin.com/0rxK1KgNtAA/ 05/13 I@I.Vy :4pm OXz:/'), 'https://v.douyin.com/0rxK1KgNtAA/');
  assert.equal(normalizePostUrl('https://www.douyin.com/video/7341234567890123456?from=copy'), 'https://www.douyin.com/video/7341234567890123456');
  assert.equal(normalizePostUrl('https://www.douyin.com/share/video/7341234567890123456?previous_page=app_code_link'), 'https://www.douyin.com/video/7341234567890123456');
});
test('rejects spoofed hosts, credentials, ports and unsupported routes', () => {
  for (const url of ['https://douyin.com.evil.test/video/123', 'https://douyin.com@evil.test/video/123', 'https://user@douyin.com/video/123', 'https://douyin.com:444/video/123', 'https://www.douyin.com/user/123', 'https://www.douyin.com/video/abc', 'javascript:alert(1)', '']) assert.throws(() => normalizePostUrl(url), url);
});
test('media hosts require HTTPS and a domain boundary', () => {
  assert.ok(isMediaUrl('https://v3-default.douyinvod.com/path/video.mp4?sign=1'));
  for (const url of ['http://v3-default.douyinvod.com/file', 'https://evildouyinvod.com/file', 'https://bytecdn.cn.evil.test/file', 'blob:123', 'file:///tmp/a']) assert.equal(isMediaUrl(url), false);
});
test('media normalization removes duplicates and rejects invalid types', () => {
  const url = 'https://cdn.douyinvod.com/video.mp4';
  const items = normalizeMedia([{url,type:'video',quality:'1080×1920 · 60fps'}, {url:url+'?v=2',type:'video'}, {url,type:'script'}, {url:'https://evil.test/a',type:'video'}, null]);
  assert.equal(items.length,1); assert.equal(items[0].selected,true);
});
test('corrupt history is recoverable and statuses distinguish failure and success', () => {
  assert.deepEqual(loadHistory({getItem:()=>'{broken'}), []);
  assert.deepEqual(loadHistory({getItem:()=>'{}'}), []);
  assert.equal(progressLabel({status:'running',bytes:25,total:100}), '저장 중 · 25%');
  assert.equal(progressLabel({status:'complete'}), '저장 완료');
  assert.match(progressLabel({status:'failed'}), /실패/);
});
