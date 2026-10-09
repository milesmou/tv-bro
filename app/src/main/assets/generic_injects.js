// Transfer blobs in bounded chunks; native backpressure keeps bridge memory constant.
if (!window.tvBroClicksListener) {
    window.tvBroClicksListener = function(e) {
        var link = e.target.closest && e.target.closest('a[href]');
        if (!link || !link.href.toLowerCase().startsWith('blob:')) return;
        e.preventDefault();
        e.stopPropagation();
        var xhr = new XMLHttpRequest();
        xhr.open('GET', link.href, true);
        xhr.responseType = 'blob';
        xhr.timeout = 30000;
        xhr.onload = function() {
            if (xhr.status !== 200) return;
            var blob = xhr.response;
            var token = TVBro.beginBlobDownload(link.download, link.href, blob.type, blob.size);
            if (!token) return;
            var offset = 0, stopped = false;
            function abort() {
                if (stopped) return;
                stopped = true;
                TVBro.abortBlobDownload(token);
                window.removeEventListener('pagehide', abort);
            }
            window.addEventListener('pagehide', abort);
            function next() {
                if (stopped) return;
                if (offset === blob.size) {
                    TVBro.finishBlobDownload(token);
                    stopped = true;
                    window.removeEventListener('pagehide', abort);
                    return;
                }
                var end = Math.min(offset + 65536, blob.size);
                var reader = new FileReader();
                reader.onerror = abort;
                reader.onload = function() {
                    var data = reader.result.split(',')[1];
                    var deadline = Date.now() + 120000;
                    function send() {
                        if (stopped) return;
                        var result = TVBro.appendBlobChunk(token, data);
                        if (result < 0 || Date.now() > deadline) { abort(); return; }
                        if (result === 0) { setTimeout(send, 25); return; }
                        offset = end;
                        next();
                    }
                    send();
                };
                reader.readAsDataURL(blob.slice(offset, end));
            }
            next();
        };
        xhr.send();
    };
    document.addEventListener('click', window.tvBroClicksListener);
}

// video playback control support
if (!Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'playing')) {
Object.defineProperty(HTMLMediaElement.prototype, 'playing', {
    get: function(){
        return !!(this.currentTime > 0 && !this.paused && !this.ended && this.readyState > 2);
    }
})
}

window.tvBroTogglePlayback = function() {
  var media = document.querySelector('video') || document.querySelector('audio');
  if (media) {
      if (media.playing) {
        media.pause();
      } else {
        media.play();
      }
  }
}

window.tvBroStopPlayback = function() {
  var media = document.querySelector('video') || document.querySelector('audio');
  if (media) {
      media.pause();
      media.currentTime = 0;
  }
}

window.tvBroRewind = function() {
    var media = document.querySelector('video') || document.querySelector('audio');
    if (media) {
        media.currentTime -= 10;
    }
}

window.tvBroFastForward = function() {
    var media = document.querySelector('video') || document.querySelector('audio');
    if (media) {
        media.currentTime += 10;
    }
}

// context menu support
window.addEventListener("touchstart", function(e) {
    window.TVBRO_activeElement = e.target;
    window.TVBRO_touchStartX = e.touches[0].clientX;
    window.TVBRO_touchStartY = e.touches[0].clientY;
});
