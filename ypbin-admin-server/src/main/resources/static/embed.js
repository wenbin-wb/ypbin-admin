/*!
 * ypbin AI 知识库问答挂件（M3.4）
 * 用法：<script src="https://<api-host>/widget/embed.js" data-token="<知识库令牌>" data-title="可选标题"></script>
 * 说明：无框架依赖；API 地址自动从脚本 src 推导；令牌由知识库「网页挂件」功能生成。
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

  var styles = `
.ypw-fab{position:fixed;right:24px;bottom:24px;z-index:999990;display:flex;align-items:center;justify-content:center;width:52px;height:52px;border:none;border-radius:50%;cursor:pointer;background:linear-gradient(135deg,#4f6df5,#7c5cf0);color:#fff;box-shadow:0 6px 20px rgba(79,109,245,.4);transition:transform .2s,box-shadow .2s}
.ypw-fab:hover{transform:scale(1.08);box-shadow:0 8px 26px rgba(79,109,245,.5)}
.ypw-fab svg{width:24px;height:24px}
.ypw-panel{position:fixed;right:24px;bottom:90px;z-index:999991;display:flex;flex-direction:column;width:360px;max-width:calc(100vw - 40px);height:520px;max-height:calc(100vh - 130px);overflow:hidden;background:#fff;border:1px solid #e4e6ef;border-radius:14px;box-shadow:0 16px 48px rgba(15,20,45,.16);font:14px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"PingFang SC","Microsoft YaHei",sans-serif;color:#1f2430}
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
          bubble.className = m.streaming ? 'ypw-ai' : 'ypw-ai';
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
        inputEl.value = '';
        inputEl.focus();
      });
  }

  function paint() {
    var panel = document.getElementById('ypw-panel');
    if (!panel) return;
    var body = render();
    panel.replaceChild(body, panel.querySelector('.ypw-body'));
    body.scrollTop = body.scrollHeight;
    var sendBtn = panel.querySelector('.ypw-send button');
    if (sendBtn) sendBtn.disabled = busy;
  }

  function init() {
    var fab = document.createElement('button');
    fab.className = 'ypw-fab';
    fab.title = title || 'AI 助手';
    fab.innerHTML = '<svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" stroke-linecap="round" stroke-linejoin="round"><path d="M12 8V4H8"/><rect width="16" height="12" x="4" y="8" rx="2"/><path d="M2 14h2M20 14h2M15 13v2M9 13v2"/></svg>';
    var panel = document.createElement('div');
    panel.id = 'ypw-panel';
    panel.className = 'ypw-panel ypw-panel--hide';
    document.body.appendChild(fab);
    document.body.appendChild(panel);

    fab.addEventListener('click', function () {
      open = !open;
      panel.classList.toggle('ypw-panel--hide', !open);
      if (open) paint();
    });

    function boot() {
      api('/widget/' + encodeURIComponent(token) + '/config').then(function (res) {
        if (res && res.success && res.data && res.data.name && !title) {
          title = res.data.name;
          fab.title = title || 'AI 助手';
        }
      }).catch(function () { /* 静默：配置失败不影响基础问答 */ });

      panel.innerHTML = '<div class="ypw-head"><span class="ypw-head__logo"><i></i>' +
        escapeHtml(title || 'AI 助手') + '</span><button class="ypw-close" onclick="window.__YPBIN_WIDGET_CLOSE__()">×</button></div>' +
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