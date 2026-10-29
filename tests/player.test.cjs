const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const script = fs.readFileSync('app/src/main/assets/player.js', 'utf8');
function setup() {
  let events, id = '', position = 0, duration = 600, paused = 0;
  const loads = [];
  const fake = {
    loadVideoById(args) { loads.push(args); id = args.videoId; position = args.startSeconds; },
    stopVideo() { paused++; }, pauseVideo() { paused++; },
    getVideoData() { return {video_id: id}; },
    getCurrentTime() { return position; }, getDuration() { return duration; }
  };
  const context = vm.createContext({YT: {Player: function(_, config) { events = config.events; return fake; }}});
  vm.runInContext(script, context);
  context.onYouTubeIframeAPIReady();
  return {context, loads, ready: () => events.onReady(), playing: () => events.onStateChange({data: 1}),
    ended: () => events.onStateChange({data: 0}), position: v => position = v, id: v => id = v,
    paused: () => paused};
}
{
  const t = setup();
  t.context.selectVideo('16LuvR2CARA', 123, 1);
  assert.equal(t.loads.length, 0);
  t.ready();
  assert.equal(t.loads[0].startSeconds, 123);
  t.context.selectVideo('16LuvR2CARA', 123, 1);
  assert.equal(t.loads.length, 1, 'page-finished must not restart playback');
  assert.equal(t.context.playbackSnapshot(false), null, 'do not overwrite history before playback');
  t.playing(); t.position(200);
  assert.equal(t.context.playbackSnapshot(false).position, 200);
  t.id('anotherId01');
  assert.equal(t.context.playbackSnapshot(false), null, 'ignore recommendation/ad identity');
}
{
  const t = setup();
  t.context.selectVideo('16LuvR2CARA', 100, 1);
  t.context.playbackSnapshot(true);
  t.ready();
  assert.equal(t.loads.length, 0, 'closing before player ready must not start hidden playback');
  t.context.selectVideo('16LuvR2CARA', 250, 2);
  t.playing(); t.position(300);
  const snapshot = t.context.playbackSnapshot(true);
  assert.equal(snapshot.session, 2);
  assert.equal(snapshot.position, 300);
  assert.equal(t.paused(), 1);
  t.playing();
  assert.equal(t.paused(), 2, 'late playing event stays paused in background');
  t.context.resumeTracking(); t.playing();
  assert.equal(t.paused(), 2);
  t.ended();
  assert.equal(t.context.playbackSnapshot(false).ended, true);
  t.context.selectVideo('16LuvR2CARA', 0, 3);
  assert.equal(t.context.playbackSnapshot(false), null, 'new profile/session has no old completion state');
}
console.log('Player tests passed: resume, duplicate initialization, identity, delayed ready, pause, session isolation, completion.');
