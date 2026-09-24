import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import fs from 'node:fs';
const source = fs.readFileSync(new URL('../android/app/src/main/assets/collect-media.js',import.meta.url),'utf8');
const cdn = name => 'https://scontent.cdninstagram.com/' + name;
function collect({data, videos=[], images=[]}={}) {
  const document = {
    querySelectorAll: () => data ? [{textContent:JSON.stringify(data)}] : [],
    querySelector: () => ({querySelectorAll: tag => tag === 'video' ? videos : images}),
  };
  return JSON.parse(vm.runInNewContext(source, {document, location:{pathname:'/p/ABC/'}, URL, innerWidth:400}));
}
test('extracts photo and video carousel from only the requested shortcode', () => {
  const data = {payload:[{code:'OTHER',image_versions2:{candidates:[{url:cdn('wrong.jpg')}]}},{code:'ABC',carousel_media:[{media_type:1,image_versions2:{candidates:[{width:100,height:100,url:cdn('small.jpg')},{width:1000,height:1000,url:cdn('large.jpg')}]}},{media_type:2,video_versions:[{width:1080,height:1920,url:cdn('full.mp4')}],image_versions2:{candidates:[{url:cdn('poster.jpg')}]}}]}]};
  const result = collect({data}); assert.equal(result.items.length,2); assert.equal(result.items[0].url,cdn('large.jpg')); assert.equal(result.items[1].type,'video');
});
test('supports GraphQL sidecar payload', () => {
  const result = collect({data:{shortcode:'ABC',edge_sidecar_to_children:{edges:[{node:{display_url:cdn('a.jpg')}},{node:{is_video:true,video_url:cdn('v.mp4'),display_url:cdn('p.jpg')}}]}}});
  assert.deepEqual(result.items.map(x=>x.type),['image','video']);
});
test('blob video is reported as unsupported streaming and not as a photo', () => {
  const result = collect({videos:[{currentSrc:'blob:abc',poster:cdn('poster.jpg')}],images:[{}]});
  assert.equal(result.streaming,true); assert.deepEqual(result.items,[]);
});
test('DOM fallback excludes avatars and offscreen carousel neighbors', () => {
  const img = (name,w,left=0) => ({currentSrc:cdn(name),naturalWidth:w,naturalHeight:w,getBoundingClientRect:()=>({width:w,height:w,left,right:left+w})});
  const result = collect({images:[img('avatar.jpg',80),img('visible.jpg',300),img('hidden.jpg',300,500)]});
  assert.deepEqual(result.items.map(x=>x.url),[cdn('visible.jpg')]);
});
test('untrusted embedded URLs are discarded', () => {
  assert.deepEqual(collect({data:{code:'ABC',video_url:'https://evil.test/v.mp4'}}).items,[]);
});
