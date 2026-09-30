/**
 * Shared floating controls for the docx/pptx/xlsx WebView viewers:
 *  - zoom in / out buttons (bottom-right)
 *  - a page/slide/sheet indicator pill (top-right)
 *  - a slim draggable scrollbar thumb on the right edge
 *  - single tap anywhere on the content = hide/show all bars (native top bar included,
 *    via the OmniAndroid bridge; falls back to hiding only the in-page controls)
 */
(function () {
  var visible = true;
  var els = [];

  function fireTap() {
    if (window.OmniAndroid && window.OmniAndroid.onTap) {
      window.OmniAndroid.onTap();          // Kotlin toggles its top bar, then calls setVisible()
    } else {
      window.OmniViewerControls.setVisible(!visible);
    }
  }

  // Tap detection: one finger, barely moved, short press, not on a control.
  var tStart = null, multi = false;
  document.addEventListener('touchstart', function (e) {
    if (e.touches.length > 1) { multi = true; tStart = null; return; }
    multi = false;
    var t = e.touches[0];
    tStart = { x: t.clientX, y: t.clientY, time: Date.now(), target: e.target };
  }, { passive: true });
  document.addEventListener('touchend', function (e) {
    if (!tStart || multi) return;
    var t = e.changedTouches[0];
    var moved = Math.sqrt(Math.pow(t.clientX - tStart.x, 2) + Math.pow(t.clientY - tStart.y, 2));
    var onControl = tStart.target && tStart.target.closest && tStart.target.closest('[data-notap]');
    if (moved < 10 && Date.now() - tStart.time < 350 && !onControl) fireTap();
    tStart = null;
  }, { passive: true });

  window.OmniViewerControls = {
    setVisible: function (v) {
      visible = v;
      els.forEach(function (el) { el.style.display = v ? '' : 'none'; });
      if (v && window.OmniViewerControls.refreshThumb) window.OmniViewerControls.refreshThumb();
    },
    init: function (opts) {
      opts = opts || {};
      var zoomTarget = document.querySelector(opts.zoomTargetSelector || 'body');
      var scale = opts.initialScale || 1;
      var minScale = opts.minScale || 0.5;
      var maxScale = opts.maxScale || 3;
      var step = opts.step || 0.15;

      function applyScale() {
        // CSS "zoom" (Chromium WebView) scales the layout itself, so the scrollable
        // area grows with it — unlike transform:scale.
        zoomTarget.style.zoom = scale;
      }
      applyScale();

      // --- Zoom buttons ---
      var zoomBox = document.createElement('div');
      zoomBox.setAttribute('data-notap', '1');
      zoomBox.style.cssText = 'position:fixed;bottom:16px;right:16px;z-index:1000;display:flex;flex-direction:column;background:rgba(0,0,0,0.65);border-radius:24px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.3);';
      function makeBtn(label, onClick) {
        var b = document.createElement('div');
        b.innerText = label;
        b.style.cssText = 'width:44px;height:44px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:22px;font-family:sans-serif;cursor:pointer;user-select:none;';
        b.onclick = onClick;
        return b;
      }
      zoomBox.appendChild(makeBtn('+', function () {
        scale = Math.min(maxScale, +(scale * 1.2).toFixed(3)); applyScale(); refreshThumb();
      }));
      zoomBox.appendChild(makeBtn('\u2013', function () {
        scale = Math.max(minScale, +(scale / 1.2).toFixed(3)); applyScale(); refreshThumb();
      }));
      document.body.appendChild(zoomBox);

      // --- Page / slide / sheet indicator ---
      var indicator = document.createElement('div');
      indicator.style.cssText = 'position:fixed;top:' + (opts.indicatorTop || '10px') + ';right:10px;z-index:1000;background:rgba(0,0,0,0.65);color:#fff;font-family:sans-serif;font-size:12px;padding:5px 10px;border-radius:12px;pointer-events:none;';
      document.body.appendChild(indicator);
      window.OmniViewerControls.setLabel = function (text) { indicator.innerText = text; };

      var pages = [];
      function refreshPages() {
        if (opts.pageSelector) pages = Array.prototype.slice.call(document.querySelectorAll(opts.pageSelector));
      }
      function updateFromScroll() {
        if (!opts.pageSelector || pages.length === 0) return;
        // The page showing the most pixels on screen is the "current" one.
        var vh = window.innerHeight, best = 0, bestVis = -1;
        pages.forEach(function (p, idx) {
          var r = p.getBoundingClientRect();
          var vis = Math.max(0, Math.min(r.bottom, vh) - Math.max(r.top, 0));
          if (vis > bestVis) { bestVis = vis; best = idx; }
        });
        indicator.innerText = (opts.label || 'Page') + ' ' + (best + 1) + ' / ' + pages.length;
      }
      refreshPages(); updateFromScroll();

      // --- Right-edge scrollbar thumb ---
      var thumb = document.createElement('div');
      thumb.setAttribute('data-notap', '1');
      thumb.style.cssText = 'position:fixed;right:2px;width:6px;border-radius:3px;background:rgba(0,0,0,0.4);z-index:999;';
      document.body.appendChild(thumb);

      function refreshThumb() {
        if (!visible) return;
        var docHeight = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);
        var viewHeight = window.innerHeight;
        if (docHeight <= viewHeight + 4) { thumb.style.display = 'none'; return; }
        thumb.style.display = 'block';
        var thumbHeight = Math.max(36, viewHeight * (viewHeight / docHeight));
        var maxThumbTop = viewHeight - thumbHeight;
        var ratio = window.scrollY / (docHeight - viewHeight);
        thumb.style.height = thumbHeight + 'px';
        thumb.style.top = (ratio * maxThumbTop) + 'px';
      }
      window.addEventListener('scroll', function () { updateFromScroll(); refreshThumb(); }, { passive: true });
      window.addEventListener('resize', refreshThumb);
      refreshThumb();

      var dragging = false, startY = 0, startScroll = 0;
      thumb.addEventListener('touchstart', function (e) {
        dragging = true; startY = e.touches[0].clientY; startScroll = window.scrollY;
      }, { passive: true });
      document.addEventListener('touchmove', function (e) {
        if (!dragging) return;
        var docHeight = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);
        var viewHeight = window.innerHeight;
        var deltaY = e.touches[0].clientY - startY;
        window.scrollTo(0, startScroll + deltaY * ((docHeight - viewHeight) / Math.max(1, viewHeight - 36)));
      }, { passive: true });
      document.addEventListener('touchend', function () { dragging = false; });

      els = [zoomBox, indicator, thumb];
      window.OmniViewerControls.refreshPages = function () { refreshPages(); updateFromScroll(); refreshThumb(); };
      window.OmniViewerControls.refreshThumb = refreshThumb;
      if (!visible) window.OmniViewerControls.setVisible(false);
    }
  };
})();
