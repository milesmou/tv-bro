// Keep website playback controls reachable while using a TV remote.
(function () {
    if (window.tvBroPersistentVideoControls) return;
    window.tvBroPersistentVideoControls = true;

    var selectors = [
        '.txp_bottom', '.txp_controlbar', '.txp_controls',
        '.bpx-player-control-wrap', '.bpx-player-control-bottom',
        '.bilibili-player-video-control', '.ykplayer-control', '.ykplayer-controls',
        '.youku-player-control', '.iqp-player-control', '.iqp-bottom',
        '.mgtv-player-controls', '.vjs-control-bar', '.xgplayer-controls',
        'xg-controls', '.dplayer-controller', '.plyr__controls', '.jw-controlbar',
        '.shaka-controls-container', '.shaka-bottom-controls',
        '[class*="control-bar"]', '[class*="controlbar"]', '[class*="controlBar"]'
    ].join(',');
    var installedDocuments = new WeakSet();

    function install(doc) {
        if (installedDocuments.has(doc) || !doc.documentElement) return;
        installedDocuments.add(doc);
        var view = doc.defaultView;
        var roots = new WeakSet();
        var pending = false;
        var style = doc.createElement('style');
        style.textContent = 'video[controls]::-webkit-media-controls-panel {' +
            'opacity:1 !important;visibility:visible !important;}' ;
        (doc.head || doc.documentElement).appendChild(style);

        function keepVisible(bar) {
            // Preserve each player's layout, including flex control bars.
            if (!bar.dataset.tvbroControlsDisplay) {
                var display = view.getComputedStyle(bar).display;
                bar.dataset.tvbroControlsDisplay = display !== 'none' ? display :
                    (bar.matches('.vjs-control-bar,.xgplayer-controls,xg-controls,.plyr__controls') ? 'flex' : 'block');
            }
            var properties = {
                display: bar.dataset.tvbroControlsDisplay,
                opacity: '1', visibility: 'visible', 'pointer-events': 'auto',
                transform: 'none', transition: 'none'
            };
            Object.keys(properties).forEach(function (name) {
                if (bar.style.getPropertyValue(name) !== properties[name] ||
                    bar.style.getPropertyPriority(name) !== 'important') {
                    bar.style.setProperty(name, properties[name], 'important');
                }
            });
        }

        function pin(root) {
            function refresh() {
                root.querySelectorAll(selectors).forEach(keepVisible);
            }
            refresh();
            if (roots.has(root)) return;
            roots.add(root);
            // Restore controls when the website's idle timer hides them again.
            new MutationObserver(refresh).observe(root, {
                subtree: true, childList: true, attributes: true,
                attributeFilter: ['class', 'style', 'hidden']
            });
        }

        function scan() {
            pending = false;
            doc.querySelectorAll('video').forEach(function (video) {
                var root = video.parentElement;
                for (var level = 0; root && root !== doc.body && level < 6; level++, root = root.parentElement) {
                    if (root.querySelector(selectors)) {
                        pin(root);
                        break;
                    }
                }
            });
            doc.querySelectorAll('iframe').forEach(function (frame) {
                try { if (frame.contentDocument) install(frame.contentDocument); } catch (_) {}
                if (!frame.tvBroControlsLoadListener) {
                    frame.tvBroControlsLoadListener = true;
                    frame.addEventListener('load', schedule);
                }
            });
        }
        function schedule() {
            if (pending) return;
            pending = true;
            view.setTimeout(scan, 80);
        }
        new MutationObserver(function (changes) {
            if (changes.some(function (change) { return change.addedNodes.length || change.removedNodes.length; })) schedule();
        }).observe(doc.documentElement, { subtree: true, childList: true });
        doc.addEventListener('play', schedule, true);
        doc.addEventListener('fullscreenchange', schedule);
        scan();
    }
    install(document);
})();
