var player, ready = false, wanted = null, startAt = 0, session = 0;
var loadedSession = -1, played = false, finished = false, suspended = false;
var bufferingSince = 0, recoveryCount = 0;

function loadWanted() {
    if (!ready || !wanted || suspended || loadedSession === session) return;
    loadedSession = session;
    played = false;
    finished = false;
    bufferingSince = 0;
    recoveryCount = 0;
    player.loadVideoById({videoId: wanted, startSeconds: startAt});
}

function selectVideo(id, seconds, generation) {
    wanted = id;
    startAt = Math.max(0, Number(seconds) || 0);
    session = generation;
    suspended = false;
    if (!id) { if (ready) player.stopVideo(); return; }
    loadWanted();
}

function playbackSnapshot(pause) {
    if (pause) suspended = true;
    if (!ready) return null;
    recoverStalledPlayback(Date.now());
    var data = player.getVideoData(), result = null;
    if (played && data && data.video_id === wanted && !data.isLive) {
        var position = player.getCurrentTime(), duration = player.getDuration();
        if (isFinite(position) && isFinite(duration) && duration > 0) {
            result = {id: wanted, session: session, position: position, duration: duration, ended: finished};
        }
    }
    if (pause) player.pauseVideo();
    return result;
}

// Android TV WebView can occasionally remain in BUFFERING even after the
// connection has recovered. Retry once from the last known position instead
// of leaving the child on a permanently spinning player.
function recoverStalledPlayback(now) {
    if (!ready || suspended || !wanted || !bufferingSince || recoveryCount >= 1
            || now - bufferingSince < 15000) return false;
    var data = player.getVideoData(), position = player.getCurrentTime();
    if (!data || data.video_id !== wanted || !isFinite(position)) return false;
    recoveryCount++;
    bufferingSince = 0;
    player.loadVideoById({videoId: wanted, startSeconds: Math.max(0, position - 2)});
    return true;
}

function resumeTracking() { suspended = false; }

function onYouTubeIframeAPIReady() {
    player = new YT.Player('player', {
        width: '100%', height: '100%',
        playerVars: {playsinline: 1, rel: 0, hl: 'cs', cc_load_policy: 0,
            origin: 'https://cz.petr.bezpecnevideo.app'},
        events: {
            onReady: function() { ready = true; loadWanted(); },
            onStateChange: function(event) {
                if (event.data === 1 && suspended) { player.pauseVideo(); return; }
                var data = player.getVideoData();
                if (!data || data.video_id !== wanted) return;
                if (event.data === 1) { played = true; finished = false; }
                if (event.data === 1) bufferingSince = 0;
                if (event.data === 3 && !bufferingSince) bufferingSince = Date.now();
                if (event.data === 0 && played) finished = true;
            }
        }
    });
}
