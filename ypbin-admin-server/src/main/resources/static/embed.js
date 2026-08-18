/*!
 * ypbin AI 知识库问答挂件（M3.4）
 * 用法：<script src="https://<api-host>/widget/embed.js" data-token="<知识库令牌>" data-title="可选标题"></script>
 * 说明：无框架依赖；API 地址自动从脚本 src 推导；令牌由知识库「网页挂件」功能生成。
 * 特性：悬浮按钮可拖动，松手自动吸边隐藏（鼠标悬停或点击时滑出）。
 */
(function () {
  'use strict';

  if (window.__YPBIN_WIDGET__) return;
  window.__YPBIN_WIDGET__ = true;

  var script = document.currentScript || document.scripts[document.scripts.length - 1];
  var token = script.getAttribute('data-token') || '';
  var title = script.getAttribute('data-title') || '';
  var apiBase = script.getAttribute('data-api') ||
    (script.src ? script.src.replace(/\/widget\/embed\.js.*$/, '') : '');

  if (!token || !apiBase) return;

  var open = false;
  var messages = [];
  var busy = false;
  var inputEl;
  var fab, panel;

  // 悬浮按钮位置（距视口右/下像素），默认右下角
  var FAB_SIZE = 52;
  var fabRight = 24;
  var fabBottom = 24;

  var styles = `
.ypw-fab{position:fixed;right:24px;bottom:24px;z-index:999990;display:flex;align-items:center;justify-content:center;width:52px;height:52px;border:none;border-radius:50%;cursor:pointer;background:linear-gradient(135deg,#4f6df5,#7c5cf0);color:#fff;box-shadow:0 6px 20px rgba(79,109,245,.4);transition:transform .25s,box-shadow .2s,right .25s,bottom .25s,opacity .2s}
.ypw-fab:hover{transform:scale(1.08);box-shadow:0 8px 26px rgba(79,109,245,.5)}
.ypw-fab--dragging{transition:none !important;cursor:grabbing;user-select:none}
.ypw-fab svg{width:24px;height:24px;pointer-events:none}
.ypw-panel{position:fixed;right:24px;bottom:90px;z-index:999991;display:flex;flex-direction:column;width:360px;max-width:calc(100vw - 40px);height:520px;max-height:calc(100vh - 130px);overflow:hidden;background:#fff;border:1px solid #e4e6ef;border-radius:14px;box-shadow:0 16px 48px rgba(15,20,45,.16);font:14px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"PingFang SC","Microsoft YaHei",sans-serif;color:#1f2430;transition:right .25s,left .25s,bottom .25s}
.ypw-panel--hide{display:none}
.ypw-head{display:flex;align-items:center;justify-content:space-between;height:48px;padding:0 14px;border-bottom:1px solid #eef0f6;font-weight:600}
.ypw-head__logo{display:flex;align-items:center;gap:8px}
.ypw-head__logo i{display:inline-block;width:8px;height:8px;border-radius:50%;background:#4f6df5}
.ypw-close{border:none;background:none;cursor:pointer;color:#8a90a5;font-size:18px;line-height:1}
.ypw-body{flex:1;overflow-y:auto;padding:14px}
.ypw-empty{display:flex;align-items:center;justify-content:center;height:100%;color:#9aa0b5;font-size:13px}
.ypw-msg{margin-bottom:12px;display:flex}
.ypw-msg--user{justify-content:flex-end}
.ypw-bubble{max-width:84%;padding:8px 12px;border-radius:10px;white-space:pre-wrap;word-break:break-word}
.ypw-bubble--user{color:#fff;background:linear-gradient(135deg,#4f6df5,#7c5cf0);border-bottom-right-radius:3px}
.ypw-bubble--ai{color:#1f2430;background:#f3f4fa;border-bottom-left-radius:3px}
.ypw-ai .ypw-bubble{background:#f3f4fa;max-width:84%;padding:8px 12px;border-radius:10px;border-bottom-left-radius:3px}
.ypw-tip{font-style:italic;color:#9aa0b5}
.ypw-foot{padding:10px 12px;border-top:1px solid #eef0f6}
.ypw-input{width:100%;min-height:40px;max-height:120px;padding:8px 10px;box-sizing:border-box;border:1px solid #e0e3ee;border-radius:8px;font:14px/1.5 inherit;resize:vertical;outline:none}
.ypw-input:focus{border-color:#4f6df5}
.ypw-send{margin-top:8px;display:flex;justify-content:flex-end}
.ypw-send button{border:none;border-radius:8px;padding:7px 16px;cursor:pointer;font-size:13px;color:#fff;background:#4f6df5}
.ypw-send button:disabled{opacity:.5;cursor:not-allowed}
`;
  var styleEl = document.createElement('style');
  styleEl.textContent = styles;
  document.head.appendChild(styleEl);

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  function api(path, body) {
    var url = apiBase + path;
    var opt = { headers: { 'Content-Type': 'application/json' } };
    if (body !== undefined) opt.body = JSON.stringify(body);
    return fetch(url, opt).then(function (r) { return r.json(); });
  }

  function render() {
    var list = document.createElement('div');
    list.setAttribute('class', 'ypw-body');
    if (messages.length === 0) {
      list.innerHTML = '<div class="ypw-empty">' + (title || '有什么可以帮你？') + '</div>';
    } else {
      messages.forEach(function (m) {
        var row = document.createElement('div');
        row.className = 'ypw-msg' + (m.role === 'user' ? ' ypw-msg--user' : '');
        var bubble = document.createElement('div');
        if (m.role === 'user') {
          bubble.className = 'ypw-bubble ypw-bubble--user';
          bubble.textContent = m.content;
        } else {
          bubble.className = 'ypw-ai';
          bubble.innerHTML = m.streaming
            ? '<div class="ypw-tip">正在思考…</div>'
            : '<div class="ypw-bubble">' + escapeHtml(m.content).replace(/\n/g, '<br>') + '</div>';
        }
        row.appendChild(bubble);
        list.appendChild(row);
      });
    }
    return list;
  }

  function sendQuestion(text) {
    if (busy || !text.trim()) return;
    busy = true;
    messages.push({ role: 'user', content: text });
    messages.push({ role: 'ai', content: '', streaming: true });
    paint();
    api('/widget/' + encodeURIComponent(token) + '/ask', { question: text })
      .then(function (res) {
        var last = messages[messages.length - 1];
        if (res && res.success) {
          last.content = res.data || '';
        } else {
          last.content = (res && res.message) ? '出错了：' + res.message : '出错了，请稍后再试';
        }
        last.streaming = false;
      })
      .catch(function () {
        var last = messages[messages.length - 1];
        if (last) { last.content = '出错了，请稍后再试'; last.streaming = false; }
      })
      .finally(function () {
        busy = false;
        paint();
        if (inputEl) { inputEl.value = ''; inputEl.focus(); }
      });
  }

  function paint() {
    if (!panel) return;
    var body = render();
    var oldBody = panel.querySelector('.ypw-body');
    if (oldBody) panel.replaceChild(body, oldBody);
    body.scrollTop = body.scrollHeight;
    var sendBtn = panel.querySelector('.ypw-send button');
    if (sendBtn) sendBtn.disabled = busy;
  }

  // ---- 位置与面板跟随 ----

  function positionFab() {
    fab.style.right = fabRight + 'px';
    fab.style.bottom = fabBottom + 'px';
    fab.style.left = 'auto';
    fab.style.top = 'auto';
  }

  function positionPanel() {
    var vw = window.innerWidth;
    var pw = 360;
    // 面板水平位置跟随按钮：按钮距右较近 → 面板靠右；否则面板左边缘与按钮左边缘对齐
    var fabLeft = vw - fabRight - FAB_SIZE;
    var pwLeft = Math.min(fabLeft, vw - pw - 8);
    if (pwLeft < 8) pwLeft = 8;
    panel.style.left = pwLeft + 'px';
    panel.style.right = 'auto';
    panel.style.bottom = (fabBottom + FAB_SIZE + 12) + 'px';
  }

  // 吸边隐藏：贴向最近侧边，仅露一条 12px 窄边
  var edgeSide = null; // 'left' | 'right' | null

  function snapToEdge() {
    var vw = window.innerWidth;
    var fabLeft = vw - fabRight - FAB_SIZE;
    var sliver = 12; // 露出的宽度
    edgeSide = fabLeft < vw / 2 ? 'left' : 'right';
    if (edgeSide === 'left') {
      // 按钮左边缘移出屏外，右边缘恰好露出 sliver 宽
      fabRight = vw - sliver;
    } else {
      fabRight = sliver;
    }
    positionFab();
    fab.style.opacity = '0.6';
  }

  function restoreFab() {
    if (edgeSide) {
      edgeSide = null;
      fab.style.opacity = '1';
    }
  }

  // ---- 拖拽 ----

  function enableDrag() {
    var dragging = false;
    var moved = false;
    var startX = 0, startY = 0, startR = 0, startB = 0;

    fab.addEventListener('mousedown', function (e) {
      if (e.button !== 0) return;
      dragging = true;
      moved = false;
      startX = e.clientX;
      startY = e.clientY;
      startR = fabRight;
      startB = fabBottom;
      fab.classList.add('ypw-fab--dragging');
      e.preventDefault();
    });

    document.addEventListener('mousemove', function (e) {
      if (!dragging) return;
      var dx = e.clientX - startX; // 向右为正
      var dy = e.clientY - startY; // 向下为正
      if (Math.abs(e.clientX - startX) + Math.abs(e.clientY - startY) > 4) moved = true;
      var vw = window.innerWidth;
      var vh = window.innerHeight;
      // 距右 = startR - dx；距下 = startB - dy；限制在视口内
      fabRight = Math.max(0, Math.min(vw - FAB_SIZE, startR - dx));
      fabBottom = Math.max(0, Math.min(vh - FAB_SIZE, startB - dy));
      positionFab();
      if (open) positionPanel();
    });

    document.addEventListener('mouseup', function () {
      if (!dragging) return;
      dragging = false;
      fab.classList.remove('ypw-fab--dragging');
      if (moved) {
        snapToEdge();
      }
    });
  }

  function init() {
    fab = document.createElement('button');
    fab.className = 'ypw-fab';
    fab.title = title || 'AI 助手';
    fab.innerHTML = '<svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" stroke-linecap="round" stroke-linejoin="round"><path d="M12 8V4H8"/><rect width="16" height="12" x="4" y="8" rx="2"/><path d="M2 14h2M20 14h2M15 13v2M9 13v2"/></svg>';
    panel = document.createElement('div');
    panel.id = 'ypw-panel';
    panel.className = 'ypw-panel ypw-panel--hide';
    document.body.appendChild(fab);
    document.body.appendChild(panel);
    window.__YPBIN_FAB__ = fab;

    // 悬停时滑出（吸边态）；移开且面板关闭时重新吸边
    fab.addEventListener('mouseenter', function () {
      if (edgeSide) restoreFab();
    });
    fab.addEventListener('mouseleave', function () {
      if (!open && edgeSide) snapToEdge();
    });

    // 点击展开/收起
    fab.addEventListener('click', function () {
      open = !open;
      panel.classList.toggle('ypw-panel--hide', !open);
      if (open) {
        restoreFab();
        positionFab();
        positionPanel();
        paint();
      }
    });

    enableDrag();
    positionFab();

    function boot() {
      api('/widget/' + encodeURIComponent(token) + '/config').then(function (res) {
        if (res && res.success && res.data && res.data.name && !title) {
          title = res.data.name;
          fab.title = title || 'AI 助手';
        }
      }).catch(function () { /* 静默：配置失败不影响基础问答 */ });

      panel.innerHTML = '<div class="ypw-head"><span class="ypw-head__logo"><i></i>' +
        escapeHtml(title || 'AI 助手') + '</span><button class="ypw-close" type="button">×</button></div>' +
        '<div class="ypw-body"></div>' +
        '<div class="ypw-foot"><textarea class="ypw-input" placeholder="输入问题，Enter 发送" rows="1"></textarea>' +
        '<div class="ypw-send"><button>发送</button></div></div>';

      inputEl = panel.querySelector('.ypw-input');
      var sendBtn = panel.querySelector('.ypw-send button');
      var closeBtn = panel.querySelector('.ypw-close');
      closeBtn.addEventListener('click', function () {
        open = false;
        panel.classList.add('ypw-panel--hide');
      });
      inputEl.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendQuestion(inputEl.value); }
      });
      sendBtn.addEventListener('click', function () { sendQuestion(inputEl.value); });
    }
    boot();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
