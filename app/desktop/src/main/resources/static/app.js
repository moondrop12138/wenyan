/* 温言桌面版 · 前端逻辑
   纯原生 JS：hash 路由 + fetch SSE 流式渲染（EventSource 不支持 POST，故用 fetch ReadableStream）。
   后端契约：见 ApiRoutes.kt。聊天 SSE 事件帧 data:{type:chat|thinking|card|done|error}
*/
'use strict';

// L6: 版本号从 /api/health 拉取（避免与后端 DESKTOP_VERSION 漂移），此处为兜底默认
let APP_VERSION = '1.9.6';
async function loadVersion(){
  try { const h = await (await fetch('/api/health')).json(); if (h && h.version) APP_VERSION = h.version.replace('-desktop',''); } catch(e) {}
}

// ===== 工具 =====
const $ = id => document.getElementById(id);
const el = (tag, cls, html) => {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (html !== undefined) e.innerHTML = html;
  return e;
};
const esc = s => String(s ?? '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
let WENYAN_TOKEN = '';
// H5: 统一注入 CSRF token 头（与 Ktor 侧校验一致）
const authHeaders = (extra) => {
  const h = Object.assign({}, extra);
  if (WENYAN_TOKEN) h['X-Wenyan-Token'] = WENYAN_TOKEN;
  return h;
};
// 安全（SSRF/开放跳转防御）：页面由桌面端本机服务托管，所有请求只允许本服务同源
// 的 /api/ 相对路径——显式拒绝绝对 URL、协议相对地址与外域路径，防任何调用点
// 把用户可控数据拼进请求地址后发往别处
const assertApiPath = (p) => {
  if (typeof p !== 'string' || !p.startsWith('/api/')) throw new Error('非法 API 路径: ' + p);
  return p;
};
const api = {
  async get(p){ const r = await fetch(assertApiPath(p), { headers: authHeaders() }); return r.json(); },
  async send(method, p, body){
    const r = await fetch(assertApiPath(p), { method, headers: authHeaders({'Content-Type':'application/json'}), body: body ? JSON.stringify(body) : undefined });
    return r.json();
  },
  post(p,b){ return api.send('POST',p,b); },
  put(p,b){ return api.send('PUT',p,b); },
  del(p){ return api.send('DELETE',p); },
};
// H5: 启动时从 /api/bootstrap 获取随机 token（同源可读）
async function loadToken(){
  try {
    const r = await fetch('/api/bootstrap');
    const j = await r.json();
    if (j && j.token) WENYAN_TOKEN = j.token;
  } catch(e) { /* 获取失败则写请求 403，属预期防护 */ }
}
let toastTimer;
function toast(msg){
  const t = $('toast'); t.textContent = msg; t.classList.add('show');
  clearTimeout(toastTimer); toastTimer = setTimeout(()=>t.classList.remove('show'), 2000);
}

// ===== 全局状态 =====
const S = {
  providers: [], models: [], targets: [], sessions: [],
  sessionId: null,            // 当前会话（null=未创建，首发时建）
  currentModelId: Number(localStorage.getItem('wenyan.modelId')) || null,
  streaming: false,
  streamSeq: 0,               // 流式令牌：切会话/删除会话后自增，使在途 SSE 的迟到事件失效
  streamSessionId: null,      // 在途流所属的会话
  controller: null,           // 在途 fetch 的 AbortController（删除会话时 abort，取消后端 LLM 流）
  pendingImages: [],          // 已上传的 dataUrl 列表
  // Web 端玻璃主题增强（默认关闭：现有 UI 为默认）
  glassEnabled: localStorage.getItem('wenyan.glassEnabled') === '1',
  glassMode: localStorage.getItem('wenyan.glassMode') || 'mica',
  glassBlur: localStorage.getItem('wenyan.glass2.blur') === null ? 4 : Number(localStorage.getItem('wenyan.glass2.blur')),
  glassFrost: localStorage.getItem('wenyan.glass2.frost') === null ? 30 : Number(localStorage.getItem('wenyan.glass2.frost')),
  fluidHue: localStorage.getItem('wenyan.glass2.hue') === null ? 0 : Number(localStorage.getItem('wenyan.glass2.hue')),
  bgBrightness: localStorage.getItem('wenyan.glass2.brightness') === null ? 50 : Number(localStorage.getItem('wenyan.glass2.brightness')),
  bgSource: localStorage.getItem('wenyan.bgSource') || 'fluid',
  wallpaper: localStorage.getItem('wenyan.wallpaper') || '',
  wallpaperBlur: Number(localStorage.getItem('wenyan.wallpaperBlur')) || 0,
  wallpaperFrost: Number(localStorage.getItem('wenyan.wallpaperFrost')) || 0,
  edgeFades: localStorage.getItem('wenyan.edgeFades') !== '0',
  theme: localStorage.getItem('wenyan.theme') || 'light',
  route: 'chat',
  pendingTargetId: Number(localStorage.getItem('wenyan.pendingTargetId')) || null, // 未建会话时的待绑定档案
  sessionTargetId: undefined,   // 当前会话已绑定的档案（undefined=尚未加载）
  visionModelId: null,          // 视觉模型槽位（后端 Properties 持久化；主模型不支持视觉时走通道 B 转述）
  memoryAutoEnabled: true,      // v1.9.0 自动记忆开关（默认开；/api/settings 加载后覆盖）
  knowledgeRouting: 'llm',     // 知识路由模式（"llm" 默认 | "offline" 显式关闭；/api/settings 加载后覆盖）
};

// v1.9.4: 当前会话冷启动恢复 —— 写穿见 persistSessionId，启动校验见 restoreSession
const SESSION_KEY = 'wenyan.sessionId';
/** 写穿当前会话 id（null = 清 key，回未建会话状态） */
function persistSessionId(id){
  if (id == null) localStorage.removeItem(SESSION_KEY);
  else localStorage.setItem(SESSION_KEY, String(id));
}
/** 冷启动恢复：仅当存留 id 仍在会话列表中才恢复，否则清 key 回空态（防悬挂 id） */
function restoreSession(){
  const saved = Number(localStorage.getItem(SESSION_KEY)) || null;
  if (saved == null) return false;
  if (S.sessions.some(s => s.id === saved)){ S.sessionId = saved; return true; }
  localStorage.removeItem(SESSION_KEY);
  return false;
}

// ===== 主题 & Web 玻璃主题增强 =====
function persistGlassSettings(){
  localStorage.setItem('wenyan.glassEnabled', S.glassEnabled ? '1' : '0');
  localStorage.setItem('wenyan.glassMode', S.glassMode);
  localStorage.setItem('wenyan.glass2.blur', String(S.glassBlur));
  localStorage.setItem('wenyan.glass2.frost', String(S.glassFrost));
  localStorage.setItem('wenyan.glass2.hue', String(S.fluidHue));
  localStorage.setItem('wenyan.glass2.brightness', String(S.bgBrightness));
  localStorage.setItem('wenyan.bgSource', S.bgSource);
  if (localStorage.getItem('wenyan.wallpaper') !== S.wallpaper) localStorage.setItem('wenyan.wallpaper', S.wallpaper);
  localStorage.setItem('wenyan.wallpaperBlur', String(S.wallpaperBlur));
  localStorage.setItem('wenyan.wallpaperFrost', String(S.wallpaperFrost));
  localStorage.setItem('wenyan.edgeFades', S.edgeFades ? '1' : '0');
}

let fluidHandle = null;
let fluidTheme = null;

function applyTheme(){
  document.documentElement.dataset.theme = S.theme;
  localStorage.setItem('wenyan.theme', S.theme);
  applyGlass();
}

function applyGlass(){
  const root = document.documentElement;
  if (S.glassEnabled) root.setAttribute('data-wy-glass', 'on');
  else root.removeAttribute('data-wy-glass');
  root.setAttribute('data-wy-glass-mode', S.glassMode);
  root.setAttribute('data-wy-bg', S.bgSource);

  root.style.setProperty('--wy-glass-blur', S.glassBlur + 'px');
  root.style.setProperty('--wy-glass-frost', (S.glassFrost / 100).toFixed(3));
  root.style.setProperty('--wy-fluid-hue', S.fluidHue + 'deg');
  root.style.setProperty('--wy-wallpaper-blur', S.wallpaperBlur + 'px');
  root.style.setProperty('--wy-wallpaper-frost', (S.wallpaperFrost / 100).toFixed(3));

  const dark = S.theme === 'dark';
  const bright = S.bgBrightness;
  const white = dark ? 0 : Math.max(0, (bright - 50) / 50);
  const black = dark ? Math.max(0, (50 - bright) / 50) : 0;
  root.style.setProperty('--wy-brightness-white', white.toFixed(3));
  root.style.setProperty('--wy-brightness-black', black.toFixed(3));

  const ambient = $('wyAmbient');
  const wallpaper = $('wyWallpaper');
  const fadeTop = $('wyFadeTop');
  const fadeBottom = $('wyFadeBottom');
  const useWallpaper = S.glassEnabled && S.bgSource === 'wallpaper' && !!S.wallpaper;
  const useFluid = S.glassEnabled && (S.bgSource === 'fluid' || (S.bgSource === 'wallpaper' && !S.wallpaper));
  if (ambient) ambient.hidden = !useFluid;
  if (wallpaper){
    wallpaper.hidden = !useWallpaper;
    const img = $('wyWallpaperImg');
    if (img && S.wallpaper) img.src = S.wallpaper;
  }
  if (fadeTop) fadeTop.hidden = !(S.glassEnabled && S.edgeFades);
  if (fadeBottom) fadeBottom.hidden = !(S.glassEnabled && S.edgeFades);

  if (useFluid) startFluid();
  else stopFluid();

  persistGlassSettings();
}

// ---- 流体背景（WebGL2，1:1 复刻 DSH-Transparent-UI-Plugin fluid-shader） ----
function startFluid(){
  const canvas = $('wyFluidCanvas');
  const ambient = $('wyAmbient');
  if (!canvas || !ambient || (fluidHandle && fluidTheme === S.theme)) return;
  stopFluid();
  fluidTheme = S.theme;
  if (window.AquaFluid) fluidHandle = window.AquaFluid.attach(canvas, S.theme);
}

function stopFluid(){
  if (fluidHandle){
    try { fluidHandle.dispose(); } catch (e) { /* ignore */ }
    fluidHandle = null;
  }
}



$('btnTheme').onclick = () => { S.theme = S.theme === 'light' ? 'dark' : 'light'; applyTheme(); };

// ===== 路由 =====
function go(route, arg){
  S.route = route;
  // 只改 hash，由 hashchange 统一触发 render；避免 go 直接 render + hashchange 再 render 双触发
  location.hash = arg ? `#/${route}/${arg}` : `#/${route}`;
}
window.addEventListener('hashchange', () => {
  const m = location.hash.match(/^#\/(\w+)(?:\/(\d+))?/);
  S.route = m ? m[1] : 'chat';
  S.routeArg = m && m[2] ? Number(m[2]) : null;
  render();
});

// ===== 数据加载 =====
async function refreshProviders(){ S.providers = await api.get('/api/providers'); }
async function refreshModels(){ S.models = await api.get('/api/models'); }
async function refreshSettings(){
  const s = await api.get('/api/settings');
  S.visionModelId = s.visionModelId || null;
  S.memoryAutoEnabled = s.memoryAutoEnabled !== false;   // v1.9.0 自动记忆开关（默认开）
  S.knowledgeRouting = s.knowledgeRouting === 'offline' ? 'offline' : 'llm';  // 只有显式 offline 才关（与双端 normalize 同语义）
}
async function refreshTargets(){ S.targets = await api.get('/api/targets'); }
async function refreshSessions(){ S.sessions = await api.get('/api/sessions'); }
function modelById(id){ return S.models.find(m => m.id === id); }
function providerById(id){ return S.providers.find(p => p.id === id); }
function targetById(id){ return S.targets.find(t => t.id === id); }

// 连接状态 → 红绿灯点 class（connectionStatus: ok/fail/""）
function statusDotClass(p){
  if (!p.hasApiKey) return 'r';
  if (p.connectionStatus === 'ok') return 'g';
  if (p.connectionStatus === 'fail') return 'r';
  return 'n';
}

// ===== 侧栏会话列表 =====
function renderSidebar(){
  const list = $('sessionList'); list.innerHTML = '';
  if (!S.sessions.length){
    list.appendChild(el('div','sb-empty','还没有会话<br>把你的处境说给军师听'));
    return;
  }
  S.sessions.forEach(s => {
    const tgt = s.targetId ? targetById(s.targetId) : null;
    const item = el('div','sb-item' + (s.id === S.sessionId ? ' on' : ''));
    item.appendChild(el('span','t', esc(s.title || '新会话')));
    const sub = [fmtTime(s.createdAt), tgt ? tgt.codeName : ''].filter(Boolean).join(' · ');
    item.appendChild(el('span','s', esc(sub)));
    const del = el('span','del','×');
    del.title = '删除会话';
    del.onclick = async e => {
      e.stopPropagation();
      if (!confirm('删除这个会话及其全部消息？')) return;
      await api.del('/api/sessions/' + s.id);
      // v1.8.2-fix（审查 P3-11）：删除会话同时 abort 在途 fetch → 后端 SSE 写入失败 →
      // 取消传播到 LLM 请求（不再浪费 token 写孤儿消息）
      if (S.sessionId === s.id){ S.sessionId = null; persistSessionId(null); abortStream(); }
      // v1.9.6：删除刷新非进入会话，不滚动
      await refreshSessions(); renderSidebar(); renderChat();
    };
    item.appendChild(del);
    item.onclick = () => {
      if (S.streaming && S.streamSessionId != null && S.streamSessionId !== s.id){
        toast('军师还在奋笔疾书，写完这一轮再切换');
        return;
      }
      // F119 修复：流式中点击的正是在流式的当前会话——直接忽略。
      // 原实现落到下方 S.streamSeq++ 会使在途流令牌过期，此后每个 SSE 帧都被丢弃，
      // renderChat 还会清掉思考占位，本轮回复从界面静默消失（服务端已落库，须重新点进才可见）。
      if (S.streaming && s.id === S.sessionId) return;
      // v1.9.6：侧栏切换会话 = 进入会话，渲染后落底一次
      S.sessionId = s.id; persistSessionId(s.id); S.streamSeq++; renderSidebar(); renderChat({ scroll: true });
    };
    list.appendChild(item);
  });
}
// O3: 会话/消息全文搜索（Enter 触发，跳转到命中会话）
function wireSearch(){
  const inp = $('sbSearch');
  if (!inp) return;
  inp.onkeydown = async e => {
    if (e.key !== 'Enter') return;
    const q = inp.value.trim();
    if (!q) return;
    try {
      const r = await api.get('/api/search?q=' + encodeURIComponent(q));
      const ids = [...new Set((r.results||[]).map(x => x.sessionId))];
      if (!ids.length){ toast('没有匹配的消息'); return; }
      const target = S.sessions.find(s => ids.includes(s.id));
      if (target){
        // F119 修复（同侧栏点击）：流式中命中的正是正在流式的当前会话时不跳转，
        // S.streamSeq++ / renderChat 会吞掉在途流的回复；提示文案保持一致
        if (!(S.streaming && target.id === S.sessionId)){
          // v1.9.6：搜索跳转会话 = 进入会话，渲染后落底一次
          S.sessionId = target.id; persistSessionId(target.id); S.streamSeq++; renderSidebar(); renderChat({ scroll: true });
        }
        toast('找到 ' + ids.length + ' 个相关会话，已跳转最近一个');
      }
    } catch(err){ toast('搜索失败'); }
  };
}
function fmtTime(ts){
  const d = new Date(ts), now = new Date();
  const sameDay = d.toDateString() === now.toDateString();
  const hm = `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
  if (sameDay) return `今天 ${hm}`;
  return `${d.getMonth()+1}月${d.getDate()}日 ${hm}`;
}

// ===== 模型 pill / 弹层 =====
function renderModelPill(){
  const m = modelById(S.currentModelId);
  $('modelName').textContent = m ? m.name : '选择模型';
  const p = m ? providerById(m.providerId) : null;
  $('modelDot').className = 'sdot' + (p && p.hasApiKey ? '' : ' fail');
}
$('modelPill').onclick = () => { openSheet(); renderModelSheet(); };
function openSheet(){ $('scrim').classList.add('open'); $('modelSheet').classList.add('open'); }
function closeSheet(){ $('scrim').classList.remove('open'); $('modelSheet').classList.remove('open'); }
$('scrim').onclick = closeSheet;

// ===== 档案绑定 pill（复用底部 sheet，切换 model/target 两种模式） =====
function renderTargetPill(){
  const pill = $('targetPill');
  const effId = S.sessionId != null ? S.sessionTargetId : S.pendingTargetId;
  const t = effId ? targetById(effId) : null;
  $('targetName').textContent = t ? t.codeName : '未绑定档案';
  pill.classList.remove('hidden');
}
$('targetPill').onclick = async () => {
  await refreshTargets();
  if (!S.targets.length){ toast('还没有档案，先到 设置 → 记忆档案 创建'); go('settings'); return; }
  openSheet(); renderTargetSheet();
};
function setSheetHead(t, d){ $('sheetTitle').textContent = t; $('sheetDesc').textContent = d; }
function renderTargetSheet(){
  setSheetHead('绑定咨询对象', '档案里的事实会注入每轮对话');
  const body = $('modelSheetBody'); body.innerHTML = '';
  const curId = S.sessionId != null ? S.sessionTargetId : S.pendingTargetId;
  // 解绑项
  const none = el('div','mrow' + (!curId ? ' on' : ''));
  none.appendChild(el('span','sic','—'));
  const ntx = el('span','tx');
  ntx.appendChild(el('span','t','不绑定'));
  ntx.appendChild(el('span','d','本轮对话不注入档案记忆'));
  none.appendChild(ntx);
  none.appendChild(el('span','check'));
  none.onclick = () => chooseTarget(null);
  body.appendChild(none);
  S.targets.forEach(t => {
    const row = el('div','mrow' + (t.id === curId ? ' on' : ''));
    row.appendChild(el('span','sic', esc((t.codeName || '?').slice(0,2))));
    const tx = el('span','tx');
    tx.appendChild(el('span','t', esc(t.codeName)));
    tx.appendChild(el('span','d', esc([t.mbti, t.relationStatus].filter(Boolean).join(' · ') || '未完善资料')));
    row.appendChild(tx);
    row.appendChild(el('span','check'));
    row.onclick = () => chooseTarget(t.id);
    body.appendChild(row);
  });
}
async function chooseTarget(tid){
  if (S.sessionId != null){
    await api.put(`/api/sessions/${S.sessionId}/target`, { targetId: tid });
    S.sessionTargetId = tid;
    await refreshSessions(); renderSidebar();
  } else {
    S.pendingTargetId = tid;
    localStorage.setItem('wenyan.pendingTargetId', tid || '');
  }
  renderTargetPill();
  setTimeout(closeSheet, 150);
}

/**
 * 模型选择 sheet（双模式，对齐手机端 PickerTarget）：
 * mode='main'   主模型：列全部模型，写 localStorage（wenyan.modelId）
 * mode='vision' 视觉槽位：只列 supportsVision 模型 + 「不设置」项，写后端 /api/settings
 */
function renderModelSheet(mode){
  mode = mode || 'main';
  const isVision = mode === 'vision';
  setSheetHead(
    isVision ? '选择视觉模型' : '选择模型',
    isVision ? '主模型不支持图片时，用它先把截图转述成文字' : '点按切换，实时生效',
  );
  const body = $('modelSheetBody'); body.innerHTML = '';
  const list = isVision ? S.models.filter(m => m.supportsVision) : S.models;
  if (!list.length){
    body.appendChild(el('div','sb-empty',
      isVision
        ? '还没有支持图片的模型<br>请到 设置 → 模型管理 添加（勾选「支持图片」）'
        : '还没有可用模型<br>请到 设置 → 模型管理 配置'));
    return;
  }
  if (isVision){
    // 清除槽位项
    const none = el('div','mrow' + (!S.visionModelId ? ' on' : ''));
    none.appendChild(el('span','sic','—'));
    const ntx = el('span','tx');
    ntx.appendChild(el('span','t','不设置'));
    ntx.appendChild(el('span','d','主模型不支持图片时将无法发送截图'));
    none.appendChild(ntx);
    none.appendChild(el('span','check'));
    none.onclick = async () => {
      await api.put('/api/settings', { visionModelId: null });
      S.visionModelId = null;
      renderModelSheet('vision');
      setTimeout(closeSheet, 150);
    };
    body.appendChild(none);
  }
  list.forEach(m => {
    const p = providerById(m.providerId);
    const onId = isVision ? S.visionModelId : S.currentModelId;
    const row = el('div','mrow' + (m.id === onId ? ' on' : ''));
    row.appendChild(el('span','sic', esc(shortName(m.name))));
    const tx = el('span','tx');
    tx.appendChild(el('span','t', esc(m.name)));
    tx.appendChild(el('span','d', esc(p ? p.name : '') + (m.supportsVision ? ' · 支持图片' : '')));
    row.appendChild(tx);
    row.appendChild(el('span','check'));
    row.onclick = async () => {
      if (isVision){
        await api.put('/api/settings', { visionModelId: m.id });
        S.visionModelId = m.id;
        renderModelSheet('vision');
      } else {
        S.currentModelId = m.id;
        localStorage.setItem('wenyan.modelId', m.id);
        renderModelPill(); renderModelSheet('main');
      }
      setTimeout(closeSheet, 150);
    };
    body.appendChild(row);
  });
}
function shortName(name){
  const n = name.replace(/^(deepseek|glm|qwen|kimi|minimax|mimo|gpt|claude)[- ]?/i,'');
  return (n || name).slice(0,3).toUpperCase();
}

// ===== 聊天渲染 =====
/**
 * 重画当前会话全部消息。
 * v1.9.6（滚动不打扰）：仅会话导航入口传 {scroll:true} 才落底一次
 * （侧栏切换/搜索跳转/冷启动进入）；其余调用一律不动视口。
 * @param {Object} [opts] 选项；opts.scroll=true = 进入会话，渲染后滚到底一次
 */
async function renderChat(opts){
  finishReveal();   // v1.9.6：重画前收尾在途渐显（清计时器、残留单元全量），防泄漏到已清空 DOM
  // v1.9.4-fix（P0 白屏）：d6352e8 曾误删本行，使下方消息拉取与迟到守卫引用未定义的 sid——
  // 点侧栏会话 / 搜索跳转 / 删除刷新时聊天区已清空却抛 ReferenceError，历史消息白屏。
  const sid = S.sessionId;                      // await 期间用户可能已切走
  const col = $('chatCol'); col.innerHTML = '';
  if (sid == null){
    S.sessionTargetId = undefined;
    renderTargetPill();
    $('emptyState').classList.remove('hidden');
    $('chatScroll').classList.add('hidden');
    renderEmpty();
    return;
  }
  // 同步当前会话的档案绑定（会话列表里有 targetId）
  const sess = S.sessions.find(s => s.id === S.sessionId);
  S.sessionTargetId = sess ? (sess.targetId ?? null) : null;
  renderTargetPill();
  $('emptyState').classList.add('hidden');
  $('chatScroll').classList.remove('hidden');
  // v1.8.2-fix（审查 P1-3）：此前 streamingHere 时过滤「最后一条 USER」，前提假设是
  // sendMessage 已把该气泡 append 进 DOM——但 renderChat 开头已清空 col，过滤反而
  // 把「流式中切走再切回」的用户消息从界面上抹掉。DOM 已清空，直接渲染全部落库消息。
  const msgs = await api.get(`/api/sessions/${sid}/messages`);
  if (sid !== S.sessionId) return;              // 迟到响应：丢弃，避免写回旧会话消息
  if (!msgs.length){
    $('emptyState').classList.remove('hidden');
    $('chatScroll').classList.add('hidden');
    renderEmpty();
    return;
  }
  msgs.forEach(m => {
    if (m.role === 'USER') appendUserBubble(m.content, m.type);
    else if (m.type === 'analysis') appendAnalysisCard(m.content, false, m.createdAt, m.id);
  });
  // v1.9.6 滚动不打扰：历史/切会话铺陈直出不渐显；仅进入会话（opts.scroll）落底一次
  if (opts && opts.scroll) scrollBottom();
}
function scrollBottom(){
  const sc = $('chatScroll');
  requestAnimationFrame(()=>{ sc.scrollTop = sc.scrollHeight; });
}
// v1.9.6：用户滚动即全量（wheel/touchmove 被动监听，不拦截滚动本身；
// 对齐 Android「用户拖动滚动」收尾）。监听挂 #chatScroll（实际滚动容器）。
// 滚动条拖拽/键盘滚动不产生 wheel/touchmove，用 scroll 事件兜底（程序化滚动
// scrollBottom 经 rAF 置 scrollTop 同样会触发，但此时 revealState 已随新会话
// 重画被 finishReveal 收尾，或新卡尚未 startReveal，故无误伤）。
$('chatScroll').addEventListener('wheel', finishReveal, { passive: true });
$('chatScroll').addEventListener('touchmove', finishReveal, { passive: true });
$('chatScroll').addEventListener('scroll', finishReveal, { passive: true });
// 非按钮复制（Ctrl+C/右键菜单）与部分文本选择即全量（对齐 Android 复制/部分选择）。
// copy/selectstart/contextmenu 冒泡到 document 即可捕获卡片内操作。
document.addEventListener('copy', finishReveal);
document.addEventListener('selectstart', finishReveal);
document.addEventListener('contextmenu', finishReveal);

/* ===== v1.9.6 回答逐段渐显（对齐 Android RevealController / ChatScreen）=====
 * 触发面：仅新回答卡片（card 事件直挂）与重答替换卡（done 时 replaceWith 换入旧卡位）
 * 经 startReveal(cardEl) 渐显；历史/切会话/刷新经 renderChat 直出（animate=false）。
 * 单元粒度与 Android coachCardRevealPlan key 表同渲染顺序（刊头 brand/time → 标题 core →
 * ①接住你（kicker+正文）→ ②事实（kicker+各组标签+各行）→ ③军师建议（kicker+tag+
 * 理由行+话术tab+话术框+发送时机）→ ④行动（kicker+各行）→ 尾注（引用/安全）；
 * 桌面端不渲染记忆依据/ token 估算位点，故 key 表为 Android 表的子集（顺序一致），
 * 入表条件同样与渲染条件同源。
 * 文本渲染全文不切片；节奏：每段淡入 320ms、段间错峰约 140ms、总时长钳制 [0.9s, 2.6s]
 * （段数多时压缩错峰；单单元淡入窗口拉伸到总时长，对齐 Android REVEAL_MIN/MAX_MS
 * 与 alpha() fadeIn 拉伸语义）。
 * 收尾面（任一 → finishReveal()，对齐 Android 文件头触发面注释）：点按卡片任意处、
 * 滚动（wheel/touchmove/scroll）、复制（含非按钮 Ctrl+C/右键菜单）、部分文本选择
 * （selectstart/contextmenu）、转述确认、输入框输入/粘贴/聚焦、发送、重试、
 * 话术 tab 切换、新一轮流式开始（setStreaming(true)）、renderChat 重画。
 * prefers-reduced-motion 与危机卡（safetyOverride）直接全量；渐显期整卡 aria-hidden、
 * 末段淡入播完/全量后解除（对齐 Android invisibleToUser），既有 aria-label 不动。 */
const REVEAL_FADE_MS = 320;      // 单段淡入时长（CSS transition 同值；单单元时拉伸到总时长）
const REVEAL_STAGGER_MS = 140;   // 段间错峰基准（对齐 Android REVEAL_STAGGER_MS）
const REVEAL_MIN_MS = 900;       // 总时长下限
const REVEAL_MAX_MS = 2600;      // 总时长上限
let revealState = null;          // 在途渐显：{card, units, timers}，null = 无渐显
function reducedMotion(){
  return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}
/** 指定卡片无条件全量（历史直出/危机卡/reduced-motion 收尾用，不依赖在途状态） */
function revealAll(cardEl){
  if (!cardEl) return;
  cardEl.querySelectorAll('.rv').forEach(u => {
    u.style.transitionDuration = '';   // 清掉单单元拉伸的行内时长，打断即瞬间全量
    u.classList.add('rv-in');
  });
  cardEl.classList.remove('rv-card');
  cardEl.removeAttribute('aria-hidden');
}
/** 立即全量：清所有计时器、剩余单元立刻显现、解除 aria-hidden（交互打断/重画/新轮开始用） */
function finishReveal(){
  if (!revealState) return;
  const st = revealState; revealState = null;
  st.timers.forEach(t => clearTimeout(t));
  revealAll(st.card);
}
/**
 * 对一张已挂载的回答卡启动逐段渐显。
 * 单元登记条件与 buildCard 渲染条件同源：空文本不渲染 → 不入表（避免为空耗时长）。
 * @param {Element} cardEl buildCard 产出的卡片元素（已在 DOM 中）
 * @param {Object} a 解析后的 analysis JSON（与 buildCard 同一对象语义）
 * @returns {boolean} true=已启动渐显，false=直接全量（危机卡/reduced-motion/无单元）
 */
function startReveal(cardEl, a){
  finishReveal();   // 新一轮开始，上一轮未完渐显立即全量
  if (!cardEl || !a) return false;
  // 危机卡/移除动画直接全量：先清掉 buildCard 已打的 .rv 隐藏态（否则刊头永久不可见）
  if (a.safetyOverride || reducedMotion()){ revealAll(cardEl); return false; }
  // 按渲染顺序收集渐显单元：buildCard 内用 markReveal(el) 逐处打标（顺序=DOM 顺序=入表顺序）
  const units = Array.from(cardEl.querySelectorAll('.rv'));
  if (!units.length){ revealAll(cardEl); return false; }
  cardEl.classList.add('rv-card');      // 关 :pop 入场动画，渐显替代（历史卡无此类保留 pop）
  cardEl.setAttribute('aria-hidden','true');
  const n = units.length;
  const total = Math.min(REVEAL_MAX_MS, Math.max(REVEAL_MIN_MS, n * REVEAL_STAGGER_MS));
  // 对齐 Android alpha()：单单元淡入窗口拉伸到整个总时长（≥900ms），不为单段退化成
  // 320ms 一闪即出；多段保持 320ms 窗口错峰。末段淡入播完（+FADE）才解除 aria-hidden，
  // 读屏在视觉播完后才读到全文。
  const fadeIn = n <= 1 ? total : REVEAL_FADE_MS;
  const step = n <= 1 ? 0 : Math.max(1, (total - fadeIn) / (n - 1));  // 段多时压缩错峰
  const st = { card: cardEl, units, timers: [] };
  revealState = st;
  // 单单元淡入窗口拉伸到总时长：行内覆盖 transition 时长（CSS 默认 320ms），
  // 收尾同样等足总时长，保证最小时长契约（内容再短也播足 900ms 才解除 aria-hidden）。
  if (n <= 1) units.forEach(u => { u.style.transitionDuration = total + 'ms'; });
  st.timers = units.map((u, i) => setTimeout(() => {
    u.classList.add('rv-in');
    if (i === n - 1){
      // 末段淡入播完才收尾：多段等一个淡入窗口，单单元等整个总时长
      const tail = n <= 1 ? total : fadeIn;
      st.timers.push(setTimeout(() => {
        if (revealState === st) finishReveal();
      }, tail));
    }
  }, Math.round(i * step)));
  return true;
}
/** buildCard 内打标渐显单元：顺序即 DOM 追加顺序，即错峰淡入顺序。
 * 非渐显路径（历史直出/危机卡/移除动画）由 appendAnalysisCard 或 startReveal
 * 经 revealAll 清掉 .rv 隐藏态，单元默认不自带可见性。 */
function markReveal(elm){
  elm.classList.add('rv');
  return elm;
}

function appendUserBubble(content, type){
  const col = $('chatCol');
  const b = el('div','msg-user');
  if (type === 'image'){
    // content 形如 [图片] data:...;data:...（后端通道 A 落库格式，前端兼容）
    const parts = content.split(/\s+/).filter(x => x.startsWith('data:image'));
    if (parts.length){
      const th = el('div','thumbs');
      parts.forEach(d => { const img = el('img'); img.src = d; th.appendChild(img); });
      b.appendChild(th);
    }
    const text = content.replace(/\s*data:image\S+/g,'').replace('[图片]','').trim();
    if (text) b.appendChild(el('span','',esc(text)));
  } else if (type === 'transcription'){
    // 通道 B 转述消息：带前缀标识，区别于普通文本
    b.appendChild(el('span','transcription-label','截图转述'));
    b.appendChild(el('span','',esc(content)));
  } else {
    b.appendChild(el('span','',esc(content)));
  }
  col.appendChild(b);
}

// analysis content 是四段 JSON 原文；animate=true 时新回答/重答卡逐段渐显
// （startReveal，文本全文渲染），false=历史/切会话直出；ts = 消息创建时间（毫秒），
// 历史渲染时传入，否则用当前时间（流式刚完成的卡片）。msgId = 该 AI 卡片的消息 id，
// 重跑时按该条所在轮次定位（缺省时后端回退到最后一轮；流式刚完成的卡片尚无落库 id，按钮不带 id）。
function appendAnalysisCard(raw, animate, ts, msgId){
  let a;
  try { a = JSON.parse(raw); } catch(e){ a = null; }
  const col = $('chatCol');
  if (!a){
    const fail = el('div','msg-ai glass edge','<div class="lead">（回复解析失败）</div>');
    fail.appendChild(makeRetryButton(msgId));
    col.appendChild(fail);
    return;
  }
  // v1.9.6：animate 参数正式启用——true=新回答/重答渐显，false=历史直出（revealAll 清隐藏态）
  const cardEl = buildCard(a, ts, msgId);
  col.appendChild(cardEl);
  if (animate) startReveal(cardEl, a);
  else revealAll(cardEl);
}
// 重跑按钮：icon-only 纯图标（内联刷新 SVG，与 Android 同风格；无可见中文，
// 读屏经 aria-label 读出「重新生成」）。msgId 经 dataset.mid 存底，供重跑成功后
// 绑定真实落库 id 与定位旧卡；流式/重跑中禁用（setStreaming 统一刷新全部 .retry-btn）
const RETRY_SVG = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M8 16H3v5"/></svg>';
function makeRetryButton(msgId){
  const b = el('button','retry-btn', RETRY_SVG);
  b.type = 'button';
  b.setAttribute('aria-label','重新生成');
  b.dataset.mid = msgId != null ? String(msgId) : '';
  b.disabled = S.streaming;
  b.onclick = () => retryMessage(msgId);
  return b;
}
/** 重跑成功后把新卡按钮绑定到真实落库 id（二次重跑定位本轮，不漂移到最后一轮） */
function bindRetryId(cardEl, mid){
  if (!cardEl || mid == null) return;
  cardEl.dataset.mid = String(mid);
  const btn = cardEl.querySelector('.retry-btn');
  if (btn){ btn.dataset.mid = String(mid); btn.onclick = () => retryMessage(mid); }
}
// v1.8.2：回答渲染改为 editorial 回信文章（刊头 + 衬线大标题 + 四段结构）
function secKicker(cn, en){
  const k = el('div','sec-kicker');
  k.appendChild(markReveal(el('span','kicker', cn)));
  k.appendChild(markReveal(el('span','en label', en)));
  return k;
}
function factGroup(label, cls, items){
  const g = el('div','fact-group');
  g.appendChild(markReveal(el('div','fg-label label', label)));
  items.forEach(t => {
    const it = markReveal(el('div','fact-item'));
    it.appendChild(el('span','mk ' + cls));
    it.appendChild(el('span','txt', esc(t)));
    g.appendChild(it);
  });
  return g;
}
function nowHM(){
  const d = new Date();
  return String(d.getHours()).padStart(2,'0') + ':' + String(d.getMinutes()).padStart(2,'0');
}
// 消息时间戳 → HH:mm；ts 缺失时回退当前时间（流式刚完成的卡片无落库时间）
function fmtHM(ts){
  if (ts == null) return nowHM();
  const d = new Date(ts);
  return String(d.getHours()).padStart(2,'0') + ':' + String(d.getMinutes()).padStart(2,'0');
}
function buildCard(a, ts, msgId){
  const card = el('div','msg-ai editorial');
  // v1.9.6 渐显单元打标（markReveal）：刊头 brand/time → 标题 core → ①②③④各段 →
  // 尾注，与 Android coachCardRevealPlan 同渲染顺序（桌面无记忆依据/token 估算位点，
  // 为其子集）；登记条件与渲染条件同源（空文本不渲染即不打标，避免为空耗时长）。
  // 交互收尾见下方卡片点击接线。
  card.addEventListener('click', finishReveal);   // 点按卡片任意处即全量
  // 刊头：短规则线 + 温言·回信 + 时间（v1.8.2-fix：历史消息显示消息时间而非渲染时刻）
  const top = el('div','coach-top');
  const l = el('div','l');
  l.appendChild(el('hr','rule-short'));
  l.appendChild(markReveal(el('span','kicker','温言 · 回信')));
  top.appendChild(l);
  top.appendChild(markReveal(el('span','caption', fmtHM(ts))));
  card.appendChild(top);
  // 衬线大标题 = 军师建议核心句
  const adv = a.advice || {};
  const core = adv.core || '';
  if (core) card.appendChild(markReveal(el('h2','displayLg coach-headline', esc(core))));
  // ① 接住你
  if (a.empathy){
    const s = el('section','coach-sec');
    s.appendChild(secKicker('接住你','EMPATHY'));
    s.appendChild(markReveal(el('p','body empathy-body', esc(a.empathy))));
    card.appendChild(s);
  }
  // ② 先分清事实
  const facts = a.facts || {};
  const hasFacts = (facts.known||[]).length || (facts.assumed||[]).length || (facts.unknown||[]).length;
  if (hasFacts){
    const s = el('section','coach-sec');
    s.appendChild(secKicker('先分清事实','FACT CHECK'));
    const box = el('div','factbox');
    if ((facts.known||[]).length) box.appendChild(factGroup('已知','known', facts.known));
    if ((facts.assumed||[]).length) box.appendChild(factGroup('推测','assumed', facts.assumed));
    if ((facts.unknown||[]).length) box.appendChild(factGroup('未知','unknown', facts.unknown));
    s.appendChild(box);
    card.appendChild(s);
  }
  // ③ 军师建议
  const styles = adv.styles || [];
  if (adv.tag || (adv.reasons||[]).length || styles.length || adv.replyTiming){
    const s = el('section','coach-sec');
    s.appendChild(secKicker('军师建议','COLUMN'));
    if (adv.tag) s.appendChild(markReveal(el('span','tag', esc(adv.tag))));
    if ((adv.reasons||[]).length){
      const ul = el('ol','reason-list');
      adv.reasons.forEach((r, i) => {
        const li = markReveal(el('li'));
        li.appendChild(el('span','no', (i + 1) + '.'));
        li.appendChild(el('span','tx', esc(r)));
        ul.appendChild(li);
      });
      s.appendChild(ul);
    }
    if (styles.length){
      const tabs = el('div','style-tabs');
      const box = el('div','script-box');
      // v1.8.2-fix（审查 P2-7）：澄清场景（UNCERTAIN）显示「先确认一下」且不提供复制话术，
      // 与手机端 ScriptBox 语义对齐
      const isClar = !!(a.isClarification || a.inputKind === 'uncertain');
      box.appendChild(markReveal(el('div','caption', isClar ? '先确认一下' : '可以直接发')));
      const txt = el('p','txt', esc(styles[0].text || ''));
      box.appendChild(markReveal(txt));
      if (!isClar){
        const copy = el('button','copy-link','复制话术');
        copy.onclick = () => { finishReveal(); navigator.clipboard.writeText(txt.textContent).then(()=>toast('已复制')); };
        box.appendChild(markReveal(copy));
      }
      styles.forEach((st, i) => {
        const b = el('button','style-tab' + (i === 0 ? ' active' : ''), esc(st.label || ('风格' + (i + 1))));
        b.onclick = () => {
          finishReveal();   // v1.9.6：话术风格 tab 切换即全量
          tabs.querySelectorAll('.style-tab').forEach((x, j) => x.classList.toggle('active', j === i));
          txt.textContent = st.text || '';
        };
        tabs.appendChild(markReveal(b));
      });
      s.appendChild(tabs);
      s.appendChild(box);
    }
    if (adv.replyTiming) s.appendChild(markReveal(el('div','caption','发送时机：' + esc(adv.replyTiming))));
    card.appendChild(s);
  }
  // ④ 行动清单
  if ((a.actions||[]).length){
    const s = el('section','coach-sec');
    s.appendChild(secKicker('行动清单','TAKEAWAYS'));
    const ul = el('ol','todo-list');
    a.actions.forEach((it, i) => {
      const li = markReveal(el('li'));
      li.appendChild(el('span','no', String(i + 1).padStart(2,'0') + '.'));
      li.appendChild(el('span','tx', esc(it.text || '')));
      ul.appendChild(li);
    });
    s.appendChild(ul);
    s.appendChild(el('div','todo-end'));
    card.appendChild(s);
  }
  // reply 兜底（无 styles 时）
  if (a.reply && !styles.length){
    const s = el('section','coach-sec');
    s.appendChild(secKicker('可以回','REPLY'));
    const box = el('div','script-box');
    box.appendChild(markReveal(el('p','txt', esc(a.reply))));
    const copy = el('button','copy-link','复制话术');
    copy.onclick = () => { finishReveal(); navigator.clipboard.writeText(a.reply).then(()=>toast('已复制')); };
    box.appendChild(markReveal(copy));
    s.appendChild(box);
    card.appendChild(s);
  }
  // 引用 / 安全
  if ((a.citations||[]).length)
    // v1.9.4 安全修复：citations 是模型输出（可经提示注入携带 HTML），并入 innerHTML 前必须转义
    card.appendChild(markReveal(el('div','cite', '参考知识库：' + esc(a.citations.join('、')))));
  if (a.safetyOverride && a.safetyMessage){
    const s = el('div','core-txt', esc(a.safetyMessage));
    s.style.color = 'var(--danger)';
    card.appendChild(s);
  }
  // 常驻重发按钮（icon-only 纯图标）：AI 气泡下方，失败解析气泡同样有（见 appendAnalysisCard）；
  // msgId 存卡片 dataset.mid + 透传到重跑请求，按该卡片所在轮次定位
  if (msgId != null) card.dataset.mid = String(msgId);
  card.appendChild(makeRetryButton(msgId));
  return card;
}

// ===== 空状态（v1.8.2 editorial：中文数字日期 + 壹贰叁索引） =====
const EMPTY_INDEX = [
  '帮我分析一段聊天记录',
  '这句话该怎么回比较好',
  '我们之间最近有点不对劲',
];
const CN_NUMERALS = ['壹','贰','叁'];
const CN_DIGITS = ['〇','一','二','三','四','五','六','七','八','九'];
function toCnNumber(n){
  if (n < 10) return CN_DIGITS[n];
  const t = Math.floor(n / 10), o = n % 10;
  return (t > 1 ? CN_DIGITS[t] : '') + '十' + (o > 0 ? CN_DIGITS[o] : '');
}
function editorialDate(){
  const d = new Date();
  const y = String(d.getFullYear()).split('').map(c => CN_DIGITS[+c]).join('');
  const h = d.getHours();
  const when = h <= 4 ? '夜' : h <= 10 ? '晨' : h <= 16 ? '午' : h <= 18 ? '夕' : '夜';
  return `${y}年${toCnNumber(d.getMonth() + 1)}月${toCnNumber(d.getDate())}日 · ${when}`;
}
function renderEmpty(){
  $('mottoDate').textContent = editorialDate();
  const g = $('exampleGrid'); g.innerHTML = '';
  EMPTY_INDEX.forEach((q, i) => {
    const item = el('button','empty-item');
    item.appendChild(el('span','no', CN_NUMERALS[i]));
    item.appendChild(el('span','tx', esc(q)));
    item.onclick = () => { $('inputBox').value = q; $('inputBox').focus(); autoGrow(); };
    g.appendChild(item);
  });
}

// ===== 输入 / 发送 =====
const inputBox = $('inputBox');
function autoGrow(){
  inputBox.style.height = 'auto';
  inputBox.style.height = Math.min(inputBox.scrollHeight, 160) + 'px';
}
inputBox.addEventListener('input', e => { finishReveal(); autoGrow(e); });   // v1.9.6：输入即全量
inputBox.addEventListener('paste', finishReveal);   // v1.9.6：粘贴即全量
inputBox.addEventListener('focus', finishReveal);   // v1.9.6：聚焦即全量
inputBox.addEventListener('keydown', e => {
  if (e.key === 'Enter' && !e.shiftKey){ e.preventDefault(); sendMessage(); }
});
$('btnSend').onclick = () => { finishReveal(); sendMessage(); };   // v1.9.6：发送即全量

// 图片上传
$('btnAttach').onclick = () => $('fileInput').click();
$('fileInput').addEventListener('change', async e => {
  const files = Array.from(e.target.files || []).slice(0, 10 - S.pendingImages.length);
  if (!files.length){ e.target.value=''; return; }
  const fd = new FormData();
  files.forEach(f => fd.append('images', f));
  try {
    const r = await fetch('/api/images/upload', { method:'POST', headers: authHeaders(), body: fd });
    const j = await r.json();
    if (j.ok){ S.pendingImages.push(...j.dataUrls); renderPending(); }
    else toast(j.error || '图片上传失败');
  } catch(err){ toast('图片上传失败'); }
  e.target.value = '';
});
function renderPending(){
  const box = $('pendingThumbs');
  box.classList.toggle('hidden', !S.pendingImages.length);
  box.innerHTML = '';
  S.pendingImages.forEach((d,i) => {
    const pt = el('div','pt');
    const img = el('img'); img.src = d; pt.appendChild(img);
    const rm = el('span','rm','×');
    rm.onclick = () => { S.pendingImages.splice(i,1); renderPending(); };
    pt.appendChild(rm);
    box.appendChild(pt);
  });
}

async function ensureSession(){
  if (S.sessionId != null) return S.sessionId;
  const r = await api.post('/api/sessions', { targetId: S.pendingTargetId || null });
  S.sessionId = r.id; persistSessionId(S.sessionId);
  await refreshSessions(); renderSidebar();
  return S.sessionId;
}

async function sendMessage(){
  const text = inputBox.value.trim();
  if ((!text && !S.pendingImages.length) || S.streaming) return;
  if (S.pendingSend) return;                    // v1.8.2-fix（审查 P3-12）：ensureSession 异步窗口内防并发建双会话
  if (S.currentModelId == null){ toast('请先选择模型'); openSheet(); renderModelSheet('main'); return; }
  const m = modelById(S.currentModelId);
  const p = m ? providerById(m.providerId) : null;
  if (!p || !p.hasApiKey){ toast('该模型未配置 API Key，请到设置配置'); go('settings'); return; }
  // 通道 B 前置校验（对齐手机端 NO_VISION）：带图 + 主模型不支持视觉 + 未配视觉槽位 → 直接拦截
  if (S.pendingImages.length && m && !m.supportsVision && !S.visionModelId){
    toast('当前模型不支持图片，请先配置视觉模型');
    openSheet(); renderModelSheet('vision');
    return;
  }

  S.pendingSend = true;
  const wasNew = S.sessionId == null;
  let sid;
  try { sid = await ensureSession(); }
  catch(err){ S.pendingSend = false; toast('创建会话失败，请重试'); return; }
  S.pendingSend = false;
  if (wasNew){ S.sessionTargetId = S.pendingTargetId || null; renderTargetPill(); }
  const images = S.pendingImages.slice();
  S.pendingImages = []; renderPending();
  inputBox.value = ''; autoGrow();

  // 用户气泡
  const col = $('chatCol');
  $('emptyState').classList.add('hidden');
  $('chatScroll').classList.remove('hidden');
  const ub = el('div','msg-user');
  if (images.length){
    const th = el('div','thumbs');
    images.forEach(d => { const img = el('img'); img.src = d; th.appendChild(img); });
    ub.appendChild(th);
  }
  if (text) ub.appendChild(el('span','',esc(text)));
  col.appendChild(ub);

  // 思考占位（通道 B 时文案对齐「转述中」语义）
  const willTranscribe = images.length && m && !m.supportsVision;
  const think = el('div','think-bubble glass edge',
    (willTranscribe ? '视觉模型正在提取截图文字…' : '正在翻知识库，梳理你的处境…') + '<span class="dots"><i></i><i></i><i></i></span>');
  col.appendChild(think);
  // v1.9.6 滚动不打扰：发送/流式期间不动视口（用户自己滑下去看）

  // F121：SSE 读取/解析/收尾骨架提炼为 runChatStream（与 confirmTranscription 共用）；
  // transcription 帧仅本链路存在，经回调转 buildTranscriptionCard
  await runChatStream('/api/chat/stream',
    { sessionId: sid, modelId: S.currentModelId, text: text || '[图片]', imageDataUrls: images },
    think,
    t => col.appendChild(buildTranscriptionCard(t, sid)));
}

// ===== 通道 B：转述确认卡片（可编辑 → 确认后走主模型纯文本分析） =====
function buildTranscriptionCard(text, sid){
  const card = el('div','msg-ai glass edge');
  card.appendChild(el('span','sec','截图转述（可修改）'));
  const ta = el('textarea','transcription-edit');
  ta.value = text;
  ta.rows = Math.min(12, Math.max(4, text.split('\n').length + 1));
  card.appendChild(ta);
  const row = el('div','transcription-actions');
  const confirm = el('button','btn-primary','确认，让军师分析');
  confirm.onclick = () => {
    const edited = ta.value.trim();
    if (!edited){ toast('转述内容不能为空'); return; }
    finishReveal();   // v1.9.6：转述确认即全量（对齐 Android 转述确认/重选）
    confirm.disabled = true;
    card.remove();
    confirmTranscription(sid, edited);
  };
  row.appendChild(confirm);
  card.appendChild(row);
  return card;
}

/**
 * F121：SSE 流式读取公共骨架（sendMessage 与 confirmTranscription 原各持约 55 行逐字重复）。
 * 建流式令牌与 AbortController → POST {url}（body 为 JS 对象）→ 逐帧解析 data: JSON 分发：
 * card/transcription/done/error 四类事件的收尾逻辑两链路完全一致；
 * transcription 帧仅主链路存在，经 [onTranscription] 回调（传 null 则忽略该帧）。
 * 含 !live 过期帧丢弃、无收尾帧兜底「回复中断」、异常「连接中断」气泡与 finally 清理。
 * [think] 为调用方先建好并 append 的思考占位气泡（两链路文案不同）。
 * [opts.replaceMid] 重跑替换语义：成功后把新卡原位换入旧卡位置（后端为原位更新，
 * id/顺序不变；'last' = 取最后一张回答卡）；失败/取消/中断时丢弃待定新卡、保留旧卡（见 retryMessage）。
 */
async function runChatStream(url, body, think, onTranscription, opts){
  const col = $('chatCol');
  opts = opts || {};
  const replaceMid = opts.replaceMid != null ? String(opts.replaceMid) : null;
  let retriedCardEl = null;   // 重跑待定新卡：done 成功才转正，失败/取消则移除
  let retriedCardJson = null; // 重跑待定新卡的 analysis 对象（done 帧无卡片内容，渐显用；后端 ChatEngine 只在 done 带 messageId）
  const findOldCard = mid => {
    if (mid == null) return null;
    if (mid === 'last'){
      // 未绑定 id 的旧调用：取最后一张三卡中带重跑按钮的回答卡作为替换目标
      const cards = col.querySelectorAll('.msg-ai');
      for (let i = cards.length - 1; i >= 0; i--){
        if (cards[i].querySelector('.retry-btn')) return cards[i];
      }
      return null;
    }
    return col.querySelector('.msg-ai[data-mid="' + mid + '"]')
      || col.querySelector('.retry-btn[data-mid="' + mid + '"]')?.closest('.msg-ai');
  };
  setStreaming(true);
  S.streamSessionId = body.sessionId;
  const mySeq = ++S.streamSeq;                 // 本轮流的令牌；切会话/删除会使其过期
  const live = () => mySeq === S.streamSeq;    // 事件落地前校验
  let settled = false;                // error/done/transcription 已收尾：流尾兜底不再触发（防误报「回复中断」+ 防 renderChat 抹掉已渲染内容）

  // v1.8.2-fix（审查 P3-11）：删除会话时 abort 在途流 → 后端 SSE 写入失败 → 取消传播到 LLM 请求
  const ctl = new AbortController();
  S.controller = ctl;

  try {
    const resp = await fetch(assertApiPath(url), {
      method:'POST', headers: authHeaders({'Content-Type':'application/json'}),
      body: JSON.stringify(body),
      signal: ctl.signal,
    });
    if (!resp.ok || !resp.body) throw new Error('HTTP ' + resp.status);
    const reader = resp.body.getReader();
    const dec = new TextDecoder();
    let buf = '';
    for(;;){
      const { done, value } = await reader.read();
      if (done) break;
      if (ctl.signal.aborted) break;           // 已删除会话：中断读取
      buf += dec.decode(value, { stream:true });
      let idx;
      while ((idx = buf.indexOf('\n\n')) >= 0){
        const frame = buf.slice(0, idx); buf = buf.slice(idx+2);
        // 兼容多行 data:（SSE 规范）：逐行拼回
        const data = frame.split('\n').filter(l => l.startsWith('data:'))
          .map(l => l.slice(5).replace(/^ /, '')).join('\n');
        if (!data) continue;
        let ev; try { ev = JSON.parse(data); } catch(e){ continue; }
        handleEvent(ev);
      }
    }
    if (!live()){ setStreaming(false); return; }   // 流尾但已切走：解锁全局 streaming 防死锁，其余 UI 不动
    if (!settled){                                 // 真·异常中断（无 error/done/transcription 帧）：收尾解锁
      think.remove();
      // 重跑取消保留旧卡：移除待定新卡（card 先到但 done 未到），旧卡不动
      if (retriedCardEl){ retriedCardEl.remove(); retriedCardEl = null; retriedCardJson = null; }
      col.appendChild(el('div','msg-ai glass edge',`<div class="lead" style="color:var(--danger)">回复中断，请重试</div>`));
      setStreaming(false);
      refreshSessions().then(renderSidebar);       // 只刷侧栏；不 renderChat（清场会抹掉刚 append 的回复中断气泡）
    }
  } catch(err){
    if (!live()){ setStreaming(false); return; }   // 过期流的异常不回写 UI，但同样要解锁防死锁
    think.remove();
    // 重跑取消保留旧卡：同上，移除待定新卡
    if (retriedCardEl){ retriedCardEl.remove(); retriedCardEl = null; retriedCardJson = null; }
    col.appendChild(el('div','msg-ai glass edge',`<div class="lead" style="color:var(--danger)">连接中断，请重试</div>`));
    setStreaming(false);
  } finally {
    if (S.controller === ctl) S.controller = null;
  }

  function handleEvent(ev){
    if (!live()) return;                        // 切会话/删除后的迟到事件一律丢弃
    if (ev.type === 'card'){
      think.remove();
      // 后端 card 帧的 card 字段是 analysis JSONObject 直传的对象（ChatEngine.kt），
      // buildCard(a) 接受对象；startReveal 同用该对象判危机卡（safetyOverride）
      const cardEl = buildCard(ev.card);
      if (replaceMid != null){
        // 重跑替换：暂存新卡，done 时原位换入（后端为原位更新，id/顺序不变）
        retriedCardEl = cardEl;
        retriedCardJson = ev.card;
      } else {
        col.appendChild(cardEl);
        // v1.9.6：新回答卡片挂载即逐段渐显（文本全文渲染）；滚动不动
        startReveal(cardEl, ev.card);
      }
      if (ev.messageId != null) bindRetryId(cardEl, ev.messageId);
    } else if (ev.type === 'transcription'){
      // 通道 B 第一步完成：替换思考占位为可编辑转述卡片，本轮流结束（无 done 帧）
      if (!onTranscription) return;             // 确认链路无 transcription 帧，防御忽略
      settled = true;
      setStreaming(false);
      think.remove();
      onTranscription(ev.text || '');
      // v1.9.6 滚动不打扰：转述卡出现不动视口
    } else if (ev.type === 'done'){
      settled = true;
      setStreaming(false);
      think.remove();
      if (replaceMid != null){
        // 完全重答语义：成功后原位替换——后端已把新内容更新进旧行本身（id/顺序不变），
        // 前端把新卡换到旧卡所在位置并保留原时间；旧卡已不在（被人为删除）则追加到尾部。
        const doneMid = (ev.messageId != null) ? String(ev.messageId)
          : (retriedCardEl ? (retriedCardEl.dataset.mid || null) : null);
        if (retriedCardEl && doneMid) bindRetryId(retriedCardEl, doneMid);
        if (retriedCardEl){
          const oldCard = findOldCard(replaceMid);
          if (oldCard && oldCard !== retriedCardEl){
            const oldCap = oldCard.querySelector('.coach-top .caption');
            const newCap = retriedCardEl.querySelector('.coach-top .caption');
            if (oldCap && newCap) newCap.textContent = oldCap.textContent;
            oldCard.replaceWith(retriedCardEl);
          } else if (!oldCard){
            col.appendChild(retriedCardEl);
          }
          // v1.9.6：重答替换后的卡片逐段渐显（与新回答同口径；done 帧无卡片内容，
          // 用 card 帧暂存的 analysis 对象）；滚动不动
          if (retriedCardJson) startReveal(retriedCardEl, retriedCardJson);
          retriedCardEl = null;
          retriedCardJson = null;
        } else if (!doneMid){
          // 极端兜底（无卡片也无 id）：从库重画，保证界面与落库一致
          renderChat();
        }
        // v1.9.6 滚动不打扰：done 落定不动视口
      }
      // 只刷侧栏标题；不 renderChat（卡片已在 DOM，重画会抹掉危机预检等未落库卡片；
      // 重跑兜底分支除外，其已主动刷新）
      refreshSessions().then(renderSidebar);
    } else if (ev.type === 'error'){
      settled = true;
      setStreaming(false);
      think.remove();
      if (replaceMid != null){
        // 失败保留旧卡：移除待定新卡（若 card 先到），旧卡不动；错误气泡照常提示
        if (retriedCardEl){ retriedCardEl.remove(); retriedCardEl = null; retriedCardJson = null; }
        if (ev.code === 'RETRY_RUNNING'){ toast('正在重新生成，稍候…'); return; }
      }
      col.appendChild(el('div','msg-ai glass edge',`<div class="lead" style="color:var(--danger)">${esc(ev.message||'出错了')}</div>`));
      // v1.9.6 滚动不打扰：错误气泡不动视口
    }
  }
}

/** 通道 B 第二步：确认转述 → 主模型分析（SSE 帧格式与 chat/stream 相同） */
async function confirmTranscription(sid, transcription){
  const col = $('chatCol');
  appendUserBubble(transcription, 'transcription');
  const think = el('div','think-bubble glass edge','军师分析中…<span class="dots"><i></i><i></i><i></i></span>');
  col.appendChild(think);
  // v1.9.6 滚动不打扰：转述确认发送不动视口

  // F121：流式骨架与 sendMessage 共用；确认链路不产生 transcription 帧，回调传 null
  await runChatStream('/api/chat/confirm-transcription',
    { sessionId: sid, modelId: S.currentModelId, transcription },
    think,
    null);
}

function setStreaming(v){
  // v1.9.6：新一轮流式开始，未完渐显立即全量（对齐 Android LaunchedEffect(streaming)）
  if (v) finishReveal();
  S.streaming = v;
  $('btnSend').disabled = v;
  inputBox.disabled = v;
  // 流式/重跑中禁用全部重发按钮（含历史气泡），结束后恢复
  document.querySelectorAll('.retry-btn').forEach(b => { b.disabled = v; });
  $('tbDot').className = 'tb-dot' + (v ? ' think' : '');
}

/** 重跑该 AI 卡片所在轮次：messageId 透传到后端 /api/chat/retry 按轮次窗口取素材重答，
 *  不重复落 USER；未绑定 id 的旧调用由后端取最后一条 ASSISTANT 作为替换目标。
 *  完全重答语义：成功后原位替换（后端把新内容更新进旧行本身，id/顺序不变；前端 done 后
 *  把新卡换到旧卡位置并保留原时间）；失败/取消保留旧卡、不写库。流式或重跑中直接拦截
 *  （按钮 disabled 的双保险），取消（连接中断/回复中断）时丢弃待定新卡保留旧卡。 */
async function retryMessage(messageId){
  if (S.streaming) return;
  finishReveal();   // v1.9.6：点重新生成即全量（旧卡直出，新卡 done 后另起渐显）
  if (S.sessionId == null){ toast('还没有可重发的消息'); return; }
  if (S.currentModelId == null){ toast('请先选择模型'); openSheet(); renderModelSheet('main'); return; }
  const col = $('chatCol');
  const think = el('div','think-bubble glass edge','正在重新组织回信…<span class="dots"><i></i><i></i><i></i></span>');
  col.appendChild(think);
  // v1.9.6 滚动不打扰：重试发送不动视口（重试本身即交互，setStreaming(true) 已收尾在途渐显）
  const body = { sessionId: S.sessionId, modelId: S.currentModelId };
  if (messageId != null) body.messageId = messageId;
  try {
    await runChatStream('/api/chat/retry',
      body,
      think,
      // 重跑不再走转述通道：后端对转述轮直接按已确认转述文本重答，图片轮不支持视觉时报错，
      // 回调传 null 防御忽略
      null,
      // 未绑定 id 的旧调用传 'last'：后端取最后一条 ASSISTANT 作为替换目标，前端原位换卡
      { replaceMid: messageId != null ? messageId : 'last' });
  } finally {
    // 取消路径（catch/兜底分支已移除待定新卡）：此处仅保底移除思考占位残留，
    // 旧卡保留；异常抛给 runChatStream 内部收尾，不再额外 renderChat
    if (think.isConnected) think.remove();
  }
}

/** 中断在途 SSE 流（删除会话时调用）：abort fetch + 使在途事件令牌过期 */
function abortStream(){
  if (S.controller){ try { S.controller.abort(); } catch(e){ /* 忽略 */ } S.controller = null; }
  S.streamSeq++;
}

// ===== 侧栏 =====
$('btnNewSession').onclick = () => {
  if (S.streaming){ toast('军师还在奋笔疾书，写完这一轮再开新会话'); return; }
  // v1.9.6：新建会话切到空态非进入会话，不滚动
  S.sessionId = null; persistSessionId(null); renderSidebar(); renderChat(); inputBox.focus();
};
$('btnToggleSb').onclick = () => $('sidebar').classList.toggle('closed');
$('btnSettings').onclick = () => go('settings');

// ===== 设置页 =====
let settingsSeq = 0;  // 设置页渲染令牌：并发/重复触发时作废旧执行，防分组重复 append
let wyWallpaperInput = null;

// ---- 设置页通用小部件（仅 Web 玻璃主题使用） ----
function wySettingRow(icon, title, desc){
  const row = el('div','setrow glass edge');
  row.appendChild(el('span','ic', icon));
  const tx = el('span','tx');
  tx.appendChild(el('span','t', title));
  tx.appendChild(el('span','d', desc || ''));
  row.appendChild(tx);
  return row;
}
function wySwitchRow(icon, title, desc, checked, onChange){
  const row = wySettingRow(icon, title, desc);
  const sw = el('span','sw' + (checked ? ' on' : ''));
  sw.onclick = e => { e.stopPropagation(); onChange(!checked); };
  row.appendChild(sw);
  row.onclick = () => onChange(!checked);
  return row;
}
function wyRangeRow(icon, title, min, max, step, value, suffix, onChange){
  const row = el('div','setrow glass edge');
  row.appendChild(el('span','ic', icon));
  const tx = el('span','tx');
  tx.appendChild(el('span','t', title));
  const val = el('span','d', value + (suffix || ''));
  tx.appendChild(val);
  row.appendChild(tx);
  const input = el('input');
  input.type = 'range';
  input.min = min;
  input.max = max;
  input.step = step || 1;
  input.value = value;
  input.style.flex = '1';
  input.style.minWidth = '80px';
  input.oninput = () => {
    const v = Number(input.value);
    val.textContent = v + (suffix || '');
    onChange(v);
  };
  row.appendChild(input);
  return row;
}
function wyModeRow(){
  const row = wySettingRow('▦', '玻璃模式', S.glassMode === 'mica' ? 'Mica · 悬浮玻璃卡片' : '兼容 · 保持原布局');
  const seg = el('span','wy-seg');
  const mica = el('button','wy-seg-btn' + (S.glassMode === 'mica' ? ' on' : ''), 'Mica');
  const compat = el('button','wy-seg-btn' + (S.glassMode === 'compat' ? ' on' : ''), '兼容');
  mica.onclick = e => { e.stopPropagation(); S.glassMode = 'mica'; applyGlass(); renderSettings($('pageCol')); };
  compat.onclick = e => { e.stopPropagation(); S.glassMode = 'compat'; applyGlass(); renderSettings($('pageCol')); };
  seg.appendChild(mica);
  seg.appendChild(compat);
  row.appendChild(seg);
  return row;
}
function wyWallpaperPicker(){
  const row = wySettingRow('▧', '壁纸', S.wallpaper ? '已选择图片' : '选择本地图片');
  const btn = el('button','wy-btn', S.wallpaper ? '更换' : '选择');
  if (!wyWallpaperInput){
    wyWallpaperInput = el('input');
    wyWallpaperInput.type = 'file';
    wyWallpaperInput.accept = 'image/*';
    wyWallpaperInput.style.display = 'none';
    document.body.appendChild(wyWallpaperInput);
  }
  const input = wyWallpaperInput;
  input.onchange = () => {
    const file = input.files && input.files[0];
    input.value = '';
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const max = 1600;
        const scale = Math.min(1, max / Math.max(img.width, img.height));
        const c = document.createElement('canvas');
        c.width = Math.max(1, Math.round(img.width * scale));
        c.height = Math.max(1, Math.round(img.height * scale));
        c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
        S.wallpaper = c.toDataURL('image/jpeg', 0.82);
        applyGlass();
        renderSettings($('pageCol'));
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  };
  btn.onclick = e => { e.stopPropagation(); input.click(); };
  row.appendChild(btn);
  return row;
}

async function renderSettings(col){
  const mySeq = ++settingsSeq;
  col.innerHTML = '';
  await Promise.all([refreshProviders(), refreshModels(), refreshTargets()]);
  if (mySeq !== settingsSeq) return;  // await 期间又有新触发，本次执行作废（新执行已重新 clear）

  // 外观
  const g1 = el('div','setgrp');
  g1.appendChild(el('span','gl','外观'));
  const themeRow = el('div','setrow glass edge');
  themeRow.appendChild(el('span','ic','◐'));
  const ttx = el('span','tx');
  ttx.appendChild(el('span','t','主题'));
  ttx.appendChild(el('span','d', S.theme === 'light' ? '浅色' : '深色'));
  themeRow.appendChild(ttx);
  const sw = el('span','sw' + (S.theme === 'dark' ? ' on' : ''));
  sw.onclick = e => { e.stopPropagation(); S.theme = S.theme === 'light' ? 'dark' : 'light'; applyTheme(); renderSettings(col); };
  themeRow.appendChild(sw);
  themeRow.onclick = () => { S.theme = S.theme === 'light' ? 'dark' : 'light'; applyTheme(); renderSettings(col); };
  g1.appendChild(themeRow);

  // 玻璃主题增强：默认关闭，现有 UI 为默认
  const glassRow = wySwitchRow(
    '❖',
    '玻璃主题',
    S.glassEnabled ? '已开启 · 可实时调节' : '默认关闭 · 开启后使用可调玻璃',
    S.glassEnabled,
    v => { S.glassEnabled = v; applyGlass(); renderSettings(col); }
  );
  g1.appendChild(glassRow);

  if (S.glassEnabled){
    g1.appendChild(wyModeRow());
    g1.appendChild(wyRangeRow('◌', '玻璃模糊度', 0, 60, 1, S.glassBlur, 'px', v => { S.glassBlur = v; applyGlass(); }));
    g1.appendChild(wyRangeRow('▤', '磨砂度', 0, 100, 1, S.glassFrost, '%', v => { S.glassFrost = v; applyGlass(); }));

    const bgRow = el('div','setrow glass edge');
    bgRow.appendChild(el('span','ic','◉'));
    const btx = el('span','tx');
    btx.appendChild(el('span','t','背景'));
    btx.appendChild(el('span','d', S.bgSource === 'fluid' ? '流体背景' : '自定义壁纸'));
    bgRow.appendChild(btx);
    const seg = el('span','wy-seg');
    const fluidBtn = el('button','wy-seg-btn' + (S.bgSource === 'fluid' ? ' on' : ''), '流体');
    const wallBtn = el('button','wy-seg-btn' + (S.bgSource === 'wallpaper' ? ' on' : ''), '壁纸');
    fluidBtn.onclick = e => { e.stopPropagation(); S.bgSource = 'fluid'; applyGlass(); renderSettings(col); };
    wallBtn.onclick = e => { e.stopPropagation(); S.bgSource = 'wallpaper'; applyGlass(); renderSettings(col); };
    seg.appendChild(fluidBtn);
    seg.appendChild(wallBtn);
    bgRow.appendChild(seg);
    g1.appendChild(bgRow);

    if (S.bgSource === 'fluid'){
      g1.appendChild(wyRangeRow('◐', '背景流体颜色', 0, 360, 1, S.fluidHue, '°', v => { S.fluidHue = v; applyGlass(); }));
    } else {
      g1.appendChild(wyWallpaperPicker());
      g1.appendChild(wyRangeRow('▤', '壁纸模糊', 0, 40, 1, S.wallpaperBlur, 'px', v => { S.wallpaperBlur = v; applyGlass(); }));
      g1.appendChild(wyRangeRow('▦', '壁纸磨砂', 0, 100, 1, S.wallpaperFrost, '%', v => { S.wallpaperFrost = v; applyGlass(); }));
    }

    g1.appendChild(wyRangeRow('☀', '背景亮度', 0, 100, 1, S.bgBrightness, '', v => { S.bgBrightness = v; applyGlass(); }));
    const brightNote = el('div','note');
    brightNote.textContent = '深色模式：0 压暗至纯黑，50 原样';
    brightNote.style.textAlign = 'left';
    brightNote.style.padding = '2px 8px 4px';
    g1.appendChild(brightNote);
    g1.appendChild(wySwitchRow('◫', '边缘渐变模糊', '页面上下边缘柔化', S.edgeFades, v => { S.edgeFades = v; applyGlass(); renderSettings(col); }));
  }

  col.appendChild(g1);

  // 模型
  const g2 = el('div','setgrp');
  g2.appendChild(el('span','gl','模型'));
  const cur = modelById(S.currentModelId);
  const curRow = el('div','setrow glass edge');
  curRow.appendChild(el('span','ic', esc(cur ? shortName(cur.name) : '—')));
  const ctx = el('span','tx');
  ctx.appendChild(el('span','t','当前模型'));
  ctx.appendChild(el('span','d', esc(cur ? cur.name : '未选择')));
  curRow.appendChild(ctx);
  curRow.appendChild(el('span','ch','切换'));
  curRow.onclick = () => { closePage(); openSheet(); renderModelSheet('main'); };
  g2.appendChild(curRow);
  // 视觉模型槽位（对齐手机端设置页「视觉模型」行：主模型不支持图片时走通道 B 转述）
  const vis = modelById(S.visionModelId);
  const visRow = el('div','setrow glass edge');
  visRow.appendChild(el('span','ic', esc(vis ? shortName(vis.name) : '◉')));
  const vtx = el('span','tx');
  vtx.appendChild(el('span','t','视觉模型'));
  vtx.appendChild(el('span','d', esc(vis ? vis.name : '未设置 · 主模型不支持图片时需要')));
  visRow.appendChild(vtx);
  visRow.appendChild(el('span','ch','切换'));
  visRow.onclick = () => { closePage(); openSheet(); renderModelSheet('vision'); };
  g2.appendChild(visRow);
  const mgRow = el('div','setrow glass edge');
  mgRow.appendChild(el('span','ic','⚙'));
  const mtx = el('span','tx');
  mtx.appendChild(el('span','t','模型管理'));
  mtx.appendChild(el('span','d','厂商 · Base URL · API Key'));
  mgRow.appendChild(mtx);
  mgRow.appendChild(el('span','ch','管理'));
  mgRow.onclick = () => renderProvidersPage($('pageCol'), '模型管理');
  g2.appendChild(mgRow);
  col.appendChild(g2);

  // 记忆档案
  const g3 = el('div','setgrp');
  g3.appendChild(el('span','gl','记忆档案'));
  const tRow = el('div','setrow glass edge');
  tRow.appendChild(el('span','ic','❤'));
  const ttx2 = el('span','tx');
  ttx2.appendChild(el('span','t','咨询对象档案'));
  ttx2.appendChild(el('span','d', `${S.targets.length} 个档案 · 跨会话记忆`));
  tRow.appendChild(ttx2);
  tRow.appendChild(el('span','ch','管理'));
  tRow.onclick = () => renderTargetsPage($('pageCol'));
  g3.appendChild(tRow);
  // v1.9.0 自动记忆开关（默认开；关闭后回复完成不再提炼）
  const autoRow = el('div','setrow glass edge');
  autoRow.appendChild(el('span','ic','✦'));
  const autoTx = el('span','tx');
  autoTx.appendChild(el('span','t','自动记忆'));
  autoTx.appendChild(el('span','d','回复后自动提炼新事实写入当前档案'));
  autoRow.appendChild(autoTx);
  const asw = el('span','sw' + (S.memoryAutoEnabled !== false ? ' on' : ''));
  asw.onclick = async e => {
    e.stopPropagation();
    S.memoryAutoEnabled = !(S.memoryAutoEnabled !== false);
    try { await api.put('/api/settings', { memoryAutoEnabled: S.memoryAutoEnabled }); }
    catch(err){ toast('保存失败：' + (err.message||err)); }
    renderSettings(col);
  };
  autoRow.onclick = () => { asw.onclick({ stopPropagation(){}, }); };
  g3.appendChild(autoRow);
  // 知识路由开关行（照「自动记忆」行模板，默认开 = llm；关闭（offline）后端聊天链路
  // 完全不发起 LLM 路由请求，仅本地关键词路由；行文案与手机端设置页一致）
  const routeRow = el('div','setrow glass edge');
  routeRow.appendChild(el('span','ic','◈'));
  const routeTx = el('span','tx');
  routeTx.appendChild(el('span','t','智能知识路由'));
  routeTx.appendChild(el('span','d', S.knowledgeRouting === 'offline' ? '已关闭 · 仅本地关键词路由' : '对话模型优先挑选知识文档'));
  routeRow.appendChild(routeTx);
  const rsw = el('span','sw' + (S.knowledgeRouting !== 'offline' ? ' on' : ''));
  rsw.onclick = async e => {
    e.stopPropagation();
    S.knowledgeRouting = S.knowledgeRouting === 'offline' ? 'llm' : 'offline';
    try { await api.put('/api/settings', { knowledgeRouting: S.knowledgeRouting }); }
    catch(err){ toast('保存失败：' + (err.message||err)); }
    renderSettings(col);
  };
  routeRow.onclick = () => { rsw.onclick({ stopPropagation(){}, }); };
  g3.appendChild(routeRow);
  // v1.9.0 撤销最近一次自动记忆
  const undoRow = el('div','setrow glass edge');
  undoRow.appendChild(el('span','ic','↩'));
  const undoRowTx = el('span','tx');
  undoRowTx.appendChild(el('span','t','撤销最近一次自动记忆'));
  undoRowTx.appendChild(el('span','d','删除最近一轮自动提炼写入的事实'));
  undoRow.appendChild(undoRowTx);
  undoRow.appendChild(el('span','ch','撤销'));
  undoRow.onclick = async () => {
    try {
      const r = await api.post('/api/memory/undo-last-write', {});
      toast(r.removed > 0 ? `已撤销最近一次自动记忆（${r.removed} 条）` : '没有可撤销的自动记忆');
    } catch(err){ toast('撤销失败：' + (err.message||err)); }
  };
  g3.appendChild(undoRow);
  col.appendChild(g3);

  // O6: 用量 / 诊断
  const gMetrics = el('div','setgrp');
  gMetrics.appendChild(el('span','gl','用量 / 诊断'));
  const mRow = el('div','setrow glass edge');
  mRow.appendChild(el('span','ic','📊'));
  const mTx = el('span','tx');
  mTx.appendChild(el('span','t','本次运行用量'));
  mTx.appendChild(el('span','d','请求数 · 输入/输出 token · 首字延迟 · 失败分类'));
  mRow.appendChild(mTx);
  mRow.appendChild(el('span','ch','查看'));
  mRow.onclick = async () => {
    try {
      const m = await api.get('/api/metrics');
      const failStr = Object.entries(m.failures||{}).map(([k,v])=>k+'×'+v).join('、') || '无';
      toast('请求 ' + m.totalRequests + ' 次 · 输入 ' + m.totalInputTokens + ' tok · 输出 ' + m.totalOutputTokens + ' tok · 首字 ' + m.avgTtftMs + 'ms · 失败: ' + failStr);
    } catch(err){ toast('读取用量失败'); }
  };
  gMetrics.appendChild(mRow);
  col.appendChild(gMetrics);

  // 数据管理
  const g5 = el('div','setgrp');
  g5.appendChild(el('span','gl','数据管理'));
  const exRow = el('div','setrow glass edge');
  exRow.appendChild(el('span','ic','⇩'));
  const exTx = el('span','tx');
  exTx.appendChild(el('span','t','导出数据'));
  exTx.appendChild(el('span','d','全部会话 · 档案 · 记忆 → JSON 备份（Key 不出密文）'));
  exRow.appendChild(exTx);
  exRow.appendChild(el('span','ch','导出'));
  exRow.onclick = () => { location.href = '/api/export'; toast('正在导出…'); };
  g5.appendChild(exRow);

  // v1.9.4: 记忆档案导出（仅档案 + 记忆事实；与上方「全量备份」用途不同，可合并导入）
  const memExRow = el('div','setrow glass edge');
  memExRow.appendChild(el('span','ic','⇩'));
  const memExTx = el('span','tx');
  memExTx.appendChild(el('span','t','导出记忆档案'));
  memExTx.appendChild(el('span','d','全部档案与记忆事实 → JSON（可合并导入到其他设备）'));
  memExRow.appendChild(memExTx);
  memExRow.appendChild(el('span','ch','导出'));
  memExRow.onclick = async () => {
    try {
      const data = await api.get('/api/memory/export');
      // 成功响应是裸记忆 JSON（无 ok 字段，必有 targets 数组）；403/失败体没有 targets
      if (!data || data.error || !Array.isArray(data.targets)){ toast((data && data.error) || '导出失败'); return; }
      const d = new Date();
      const stamp = `${d.getFullYear()}${String(d.getMonth()+1).padStart(2,'0')}${String(d.getDate()).padStart(2,'0')}`;
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = el('a');
      a.href = url; a.download = `wenyan-memory-${stamp}.json`;
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(()=>URL.revokeObjectURL(url), 1000);
      toast('记忆档案已导出');
    } catch(err){ toast('导出失败：' + (err.message||err)); }
  };
  g5.appendChild(memExRow);

  // v1.9.4: 合并导入记忆档案（合并语义：只新增/合并，绝不删除现有数据）
  const memImRow = el('div','setrow glass edge');
  memImRow.appendChild(el('span','ic','⇧'));
  const memImTx = el('span','tx');
  memImTx.appendChild(el('span','t','合并导入记忆'));
  memImTx.appendChild(el('span','d','选择记忆档案 JSON，合并进现有数据（不删除任何现有内容）'));
  memImRow.appendChild(memImTx);
  memImRow.appendChild(el('span','ch','导入'));
  const memInput = el('input');
  memInput.type = 'file'; memInput.accept = '.json,application/json'; memInput.style.display = 'none';
  document.body.appendChild(memInput);
  memInput.onchange = async () => {
    const file = memInput.files && memInput.files[0];
    memInput.value = '';
    if (!file) return;
    if (!confirm('合并导入记忆档案？\n只新增/合并，不会删除任何现有数据。')) return;
    let json;
    try { json = JSON.parse(await file.text()); }
    catch(err){ toast('文件不是合法 JSON'); return; }
    if (!json || typeof json !== 'object'){ toast('文件内容不是记忆档案 JSON'); return; }
    try {
      const r = await api.post('/api/memory/import', json);
      // 后端契约（ApiRoutes.kt POST /api/memory/import）：成功 {ok:true,...}；失败 {ok:false,...} 或 403 {error:...}（无 ok 字段，按真值判定）
      if (!r || !r.ok){ toast((r && r.error) || '导入失败'); return; }
      await Promise.all([refreshTargets(), refreshSessions()]);
      renderSidebar(); renderSettings(col);
      toast((r && r.message) || '记忆已合并导入');
    } catch(err){ toast('导入失败：' + (err.message||err)); }
  };
  memImRow.onclick = () => memInput.click();
  g5.appendChild(memImRow);

  // O1: 从备份恢复
  const impRow = el('div','setrow glass edge');
  impRow.appendChild(el('span','ic','⇧'));
  const impTx = el('span','tx');
  impTx.appendChild(el('span','t','从备份恢复'));
  impTx.appendChild(el('span','d','选择导出的 JSON 备份，覆盖当前数据（Key 需重新输入）'));
  impRow.appendChild(impTx);
  impRow.appendChild(el('span','ch','恢复'));
  const impInput = el('input');
  impInput.type = 'file'; impInput.accept = '.json,application/json'; impInput.style.display = 'none';
  document.body.appendChild(impInput);
  impInput.onchange = async () => {
    const file = impInput.files && impInput.files[0];
    impInput.value = '';
    if (!file) return;
    if (!confirm('从备份恢复会清空当前全部数据并覆盖，确定继续？')) return;
    try {
      const text = await file.text();
      const r = await fetch('/api/import', { method:'POST', headers: authHeaders({'Content-Type':'application/json'}), body: text });
      const j = await r.json();
      if (j.ok){
        S.sessionId = null; persistSessionId(null);
        await Promise.all([refreshProviders(), refreshModels(), refreshTargets(), refreshSessions()]);
        renderSidebar(); renderSettings(col);
        toast('导入完成');
      } else toast(j.error || '导入失败');
    } catch(err){ toast('导入失败：' + (err.message||err)); }
  };
  impRow.onclick = () => impInput.click();
  g5.appendChild(impRow);

  const clRow = el('div','setrow glass edge');
  clRow.appendChild(el('span','ic','⌫'));
  const clTx = el('span','tx');
  clTx.appendChild(el('span','t','清空全部数据'));
  clTx.appendChild(el('span','d','会话 · 档案 · 记忆 · 厂商配置 全部删除，不可恢复'));
  clRow.appendChild(clTx);
  const clBtn = el('span','ch','清空');
  clBtn.style.color = 'var(--danger)';
  clRow.appendChild(clBtn);
  clRow.onclick = async () => {
    if (!confirm('清空全部数据？\n会话、档案、记忆、厂商配置都会删除，不可恢复。建议先导出备份。')) return;
    if (!confirm('最后确认：真的要清空吗？')) return;
    const r = await api.post('/api/data/clear');
    if (r.ok){
      S.sessionId = null; persistSessionId(null);
      await Promise.all([refreshProviders(), refreshModels(), refreshTargets(), refreshSessions()]);
      renderSidebar(); renderSettings(col);
      toast('已清空，预设厂商已重置');
    } else toast('清空失败');
  };
  g5.appendChild(clRow);
  col.appendChild(g5);

  // 关于
  const g4 = el('div','setgrp');
  g4.appendChild(el('span','gl','关于'));
  const aRow = el('div','setrow glass edge');
  aRow.appendChild(el('span','ic','温'));
  const atx = el('span','tx');
  atx.appendChild(el('span','t','版本'));
  atx.appendChild(el('span','d','温言桌面版 · 液态玻璃'));
  aRow.appendChild(atx);
  const chk = el('span','ch','v' + esc(APP_VERSION) + ' · 检查更新');
  chk.style.color = 'var(--accent)';
  chk.onclick = async e => {
    e.stopPropagation();
    chk.textContent = '检查中…';
    try {
      const r = await api.get('/api/update');
      if (r.status === 'new'){
        chk.textContent = 'v' + APP_VERSION + ' · 有新版本 ' + r.latest;
        chk.style.color = 'var(--danger)';
        if (r.downloadUrl && confirm('发现新版本 v' + r.latest + '，去下载？'))
          window.open(r.downloadUrl, '_blank');
        else toast('新版本 v' + r.latest);
      } else if (r.status === 'latest'){
        chk.textContent = 'v' + APP_VERSION + ' · 已是最新';
        chk.style.color = 'var(--muted)';
        toast('已是最新版本');
      } else {
        chk.textContent = 'v' + APP_VERSION + ' · 检查更新';
        toast(r.error || '检查更新失败');
      }
    } catch(err){ chk.textContent = 'v' + APP_VERSION + ' · 检查更新'; toast('网络异常'); }
  };
  aRow.appendChild(chk);
  g4.appendChild(aRow);
  const ctRow = el('div','setrow glass edge');
  ctRow.appendChild(el('span','ic','✉'));
  const cttx = el('span','tx');
  cttx.appendChild(el('span','t','联系作者'));
  cttx.appendChild(el('span','d','hyf136696647672021@126.com'));
  ctRow.appendChild(cttx);
  ctRow.appendChild(el('span','ch','复制'));
  ctRow.onclick = () => navigator.clipboard.writeText('hyf136696647672021@126.com').then(()=>toast('邮箱已复制'));
  g4.appendChild(ctRow);
  col.appendChild(g4);
}

// ===== 模型管理（厂商列表红绿灯 → 厂商详情） =====
async function renderProvidersPage(col, title){
  $('pageTitle').textContent = title || '模型管理';
  col.innerHTML = '';
  await Promise.all([refreshProviders(), refreshModels()]);
  col.appendChild(el('div','note','绿灯 = 已配置可用 · 红灯 = 未配置或测连接失败 · 点按厂商进入配置'));

  const listWrap = el('div','setgrp');
  S.providers.forEach(p => {
    const row = el('div','setrow glass edge');
    const dot = el('span','rdot ' + statusDotClass(p));
    row.appendChild(dot);
    row.appendChild(el('span','ic', esc(shortName(p.name))));
    const tx = el('span','tx');
    tx.appendChild(el('span','t', esc(p.name)));
    const host = p.baseUrl.replace(/^https?:\/\//,'').replace(/\/.*$/,'');
    tx.appendChild(el('span','d', esc(host) + (p.hasApiKey ? ' · 已配置' : ' · 未配置')));
    row.appendChild(tx);
    // 测连接按钮
    const test = el('span','ch','测连接');
    test.style.color = 'var(--accent)';
    test.onclick = async e => {
      e.stopPropagation();
      dot.className = 'rdot c';
      test.textContent = '…';
      const r = await api.post(`/api/providers/${p.id}/test`);
      await refreshProviders();
      const np = providerById(p.id);
      dot.className = 'rdot ' + statusDotClass(np);
      test.textContent = '测连接';
      toast(r.ok ? `连接成功（${r.model}）` : (r.error || '连接失败'));
    };
    row.appendChild(test);
    row.appendChild(el('span','ch','›'));
    row.onclick = () => renderProviderDetail(col, p.id);
    listWrap.appendChild(row);
  });
  col.appendChild(listWrap);

  const addBtn = el('button','btn-primary','+ 添加自定义厂商');
  addBtn.onclick = () => renderProviderDetail(col, null);
  col.appendChild(addBtn);
}

async function renderProviderDetail(col, pid, autoTest){
  const isNew = pid == null;
  const p = isNew ? { name:'', baseUrl:'', hasApiKey:false } : providerById(pid);
  $('pageTitle').textContent = isNew ? '添加厂商' : p.name;
  col.innerHTML = '';
  const back = el('div','back-row');
  back.innerHTML = '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>';
  back.appendChild(el('span','','模型管理'));
  back.onclick = () => renderProvidersPage(col);
  col.appendChild(back);

  // 模型列表（编辑态）
  if (!isNew){
    const grp = el('div','setgrp');
    grp.appendChild(el('span','gl','模型'));
    const models = await api.get(`/api/providers/${pid}/models`);
    models.forEach(m => {
      const row = el('div','setrow glass edge');
      row.appendChild(el('span','ic', esc(shortName(m.name))));
      const tx = el('span','tx');
      tx.appendChild(el('span','t', esc(m.name)));
      tx.appendChild(el('span','d', m.supportsVision ? '支持图片' : '纯文本'));
      row.appendChild(tx);
      const del = el('span','ch','删除');
      del.style.color = 'var(--danger)';
      del.onclick = async e => {
        e.stopPropagation();
        if (!confirm(`删除模型 ${m.name}？`)) return;
        await api.del('/api/models/' + m.id);
        renderProviderDetail(col, pid);
      };
      row.appendChild(del);
      grp.appendChild(row);
    });
    // 加模型
    const addM = el('div','form-card glass edge');
    addM.appendChild(el('span','sec','添加模型'));
    const mf = field('模型名（如 deepseek-chat）');
    addM.appendChild(mf.wrap);
    const vis = el('label','',`<input type="checkbox"> 支持图片（视觉模型）`);
    vis.style.fontSize = '12px'; vis.style.color = 'var(--muted)';
    const visCb = vis.querySelector('input');
    addM.appendChild(vis);
    const addBtn2 = el('button','btn-primary','添加');
    addBtn2.onclick = async () => {
      const name = mf.input.value.trim();
      if (!name){ toast('填模型名'); return; }
      await api.post('/api/models', { providerId: pid, name, supportsVision: visCb.checked });
      await refreshModels();
      toast('已添加');
      renderProviderDetail(col, pid);
    };
    addM.appendChild(addBtn2);
    grp.appendChild(addM);
    col.appendChild(grp);
  }

  // 连接表单
  const form = el('div','form-card glass edge');
  form.appendChild(el('span','sec','连接配置'));
  const nf = field('名称');
  nf.input.value = p.name || '';
  const uf = field('Base URL');
  uf.input.value = p.baseUrl || '';
  uf.input.placeholder = 'https://api.example.com/v1';
  const kf = field('API Key' + (p.hasApiKey ? '（已配置，留空则保持不变）' : ''));
  kf.input.type = 'password';
  kf.input.placeholder = p.hasApiKey ? '••••••••' : 'sk-…';
  form.appendChild(nf.wrap); form.appendChild(uf.wrap); form.appendChild(kf.wrap);
  const save = el('button','btn-primary','保存配置');
  save.onclick = async () => {
    const name = nf.input.value.trim(), baseUrl = uf.input.value.trim();
    if (!name || !baseUrl){ toast('名称和 Base URL 必填'); return; }
    // F120 修复：api.send 不查 r.ok，失败响应（403 {error:forbidden} / 500 非 JSON 体）
    // 原先照样 toast 成功，且 r.id 为 undefined 重绘回空白表单丢输入；非 JSON 响应 r.json() 抛错静默。
    // 现检查响应体，失败时提示具体错误并保留表单不重绘。
    try {
      if (isNew){
        const r = await api.post('/api/providers', { name, baseUrl, apiKey: kf.input.value.trim() || null });
        if (!r || !r.id){ toast('添加失败：' + ((r && r.error) || '请求被拒绝')); return; }
        toast('已添加厂商');
        await refreshProviders();
        renderProviderDetail(col, r.id, true);   // 重绘为编辑态并自动测连接
      } else {
        const body = { name, baseUrl };
        if (kf.input.value.trim()) body.apiKey = kf.input.value.trim();
        const r = await api.put('/api/providers/' + pid, body);
        if (!r || !r.ok){ toast('保存失败：' + ((r && r.error) || '请求被拒绝')); return; }
        toast('已保存 · Key 加密存储于本地');
        await refreshProviders();
        renderProviderDetail(col, pid, true);    // 重绘并自动测连接
      }
    } catch(err){ toast('保存失败，请重试'); }
  };
  form.appendChild(save);
  form.appendChild(el('span','note','Key 仅保存在本机加密存储中，不会上传服务器'));
  col.appendChild(form);

  if (!isNew){
    const delBtn = el('button','btn-ghost','删除该厂商');
    delBtn.style.color = 'var(--danger)';
    delBtn.onclick = async () => {
      if (!confirm(`删除厂商 ${p.name} 及其全部模型？`)) return;
      await api.del('/api/providers/' + pid);
      await Promise.all([refreshProviders(), refreshModels()]);
      renderProvidersPage(col);
    };
    col.appendChild(delBtn);
  }

  // 保存配置成功 → 自动测连接（表单内红/绿灯；列表页红绿灯由 refreshProviders 联动）
  if (autoTest) runAutoTest(col, pid);
}
// 自动测连接：保存后调用 /test，表单内显示 连接中 → 成功(绿)/失败(红) + 文案
async function runAutoTest(col, id){
  const form = col.querySelector('.form-card');
  const save = form ? form.querySelector('button.btn-primary') : null;
  if (!form || !save) return;
  const row = el('div','conn-status');
  row.appendChild(el('span','rdot c'));
  row.appendChild(el('span','','正在检测连接…'));
  save.after(row);
  let r;
  try { r = await api.post(`/api/providers/${id}/test`); }
  catch(e){ r = { ok:false, error:(e && e.message) || '连接请求失败' }; }
  await refreshProviders();
  row.innerHTML = '';
  row.appendChild(el('span','rdot ' + (r.ok ? 'g' : 'r')));
  row.appendChild(el('span','', r.ok ? '连接成功' + (r.model ? `（${esc(r.model)}）` : '') : esc(r.error || '连接失败')));
}
function field(label){
  const wrap = el('div','form-field');
  wrap.appendChild(el('label','',esc(label)));
  const input = el('input','form-in');
  wrap.appendChild(input);
  return { wrap, input };
}

// ===== 记忆档案 =====
async function renderTargetsPage(col){
  $('pageTitle').textContent = '记忆档案';
  col.innerHTML = '';
  await refreshTargets();
  col.appendChild(el('div','note','为每个咨询对象建档：档案里的事实会在每次对话时注入，让军师越来越懂你们'));

  const list = el('div','setgrp');
  S.targets.forEach(t => {
    const row = el('div','target-card glass edge');
    row.appendChild(el('span','ic','❤'));
    const tx = el('span','tx');
    tx.appendChild(el('span','t', esc(t.codeName)));
    tx.appendChild(el('span','d', esc([t.mbti, t.relationStatus].filter(Boolean).join(' · ') || '未完善资料')));
    row.appendChild(tx);
    row.appendChild(el('span','ch','›'));
    row.onclick = () => renderTargetDetail(col, t.id);
    list.appendChild(row);
  });
  col.appendChild(list);

  const nf = el('div','form-card glass edge');
  nf.appendChild(el('span','sec','新建档案'));
  const f = field('代号（如 小雨）');
  nf.appendChild(f.wrap);
  const btn = el('button','btn-primary','创建');
  btn.onclick = async () => {
    const name = f.input.value.trim();
    if (!name){ toast('填个代号'); return; }
    // F120 修复：同厂商保存——失败响应原样 renderTargetDetail(col, undefined) 会 TypeError 崩溃；
    // 检查 r.id，失败提示具体错误并保留表单；非 JSON 响应（500）兜底 catch
    try {
      const r = await api.post('/api/targets', { codeName: name });
      if (!r || !r.id){ toast('创建失败：' + ((r && r.error) || '请求被拒绝')); return; }
      await refreshTargets();
      renderTargetDetail(col, r.id);
    } catch(err){ toast('创建失败，请重试'); }
  };
  nf.appendChild(btn);
  col.appendChild(nf);
}

async function renderTargetDetail(col, tid){
  const t = targetById(tid);
  $('pageTitle').textContent = t ? t.codeName : '档案';
  col.innerHTML = '';
  const back = el('div','back-row');
  back.innerHTML = '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>';
  back.appendChild(el('span','','记忆档案'));
  back.onclick = () => renderTargetsPage(col);
  col.appendChild(back);

  // 资料表单
  const form = el('div','form-card glass edge');
  form.appendChild(el('span','sec','资料'));
  const nm = field('代号'); nm.input.value = t.codeName || '';
  const mb = field('MBTI'); mb.input.value = t.mbti || '';
  const rs = field('关系状态'); rs.input.value = t.relationStatus || ''; rs.input.placeholder = '暧昧 / 追求中 / 在一起…';
  form.appendChild(nm.wrap); form.appendChild(mb.wrap); form.appendChild(rs.wrap);
  const save = el('button','btn-primary','保存资料');
  save.onclick = async () => {
    await api.put('/api/targets/' + tid, { codeName: nm.input.value.trim(), mbti: mb.input.value.trim() || null, relationStatus: rs.input.value.trim() || null });
    await refreshTargets();
    toast('已保存');
  };
  form.appendChild(save);
  col.appendChild(form);

  // 记忆事实
  const factsGrp = el('div','setgrp');
  factsGrp.appendChild(el('span','gl','记住的事实（对话时自动注入）'));
  const facts = await api.get(`/api/targets/${tid}/facts`);
  const fl = el('div','', '');
  fl.style.display = 'flex'; fl.style.flexDirection = 'column'; fl.style.gap = '6px';
  if (!facts.length) fl.appendChild(el('div','sb-empty','还没有记住的事实<br>聊过新话题后军师会自动提炼'));
  facts.forEach(fct => {
    const row = el('div','fact-row');
    const txtWrap = el('div','','');
    txtWrap.style.display = 'flex'; txtWrap.style.flexDirection = 'column'; txtWrap.style.gap = '3px';
    txtWrap.appendChild(el('span','txt', esc(fct.text)));
    // v1.9.1 徽标行：临时（有过期时间）/ 推测（模型推断）/ 来源（素材类型）
    const badges = [];
    if (fct.expiresAt) badges.push(['临时','var(--accent)']);
    if (fct.kind === 'hypothesis') badges.push(['推测','var(--muted)']);
    if (fct.source === 'paste') badges.push(['粘贴记录','var(--accent)']);
    if (fct.source === 'transcription') badges.push(['截图转述','var(--warn, var(--muted))']);
    if (fct.source === 'chat') badges.push(['口述','var(--muted)']);
    if (badges.length) {
      const bd = el('div','','');
      bd.style.display = 'flex'; bd.style.gap = '4px';
      badges.forEach(([label, color]) => {
        const b = el('span','', label);
        b.style.cssText = `font-size:10.5px;line-height:1;color:${color};background:var(--chip);border-radius:3px;padding:2.5px 5px;`;
        bd.appendChild(b);
      });
      txtWrap.appendChild(bd);
    }
    row.appendChild(txtWrap);
    const acts = el('span','acts');
    const edit = el('span','mini-btn','✎');
    edit.title = '编辑';
    edit.onclick = async () => {
      const nv = prompt('编辑事实', fct.text);
      if (nv == null || !nv.trim()) return;
      await api.put('/api/facts/' + fct.id, { text: nv.trim() });
      renderTargetDetail(col, tid);
    };
    const del = el('span','mini-btn','×');
    del.title = '删除';
    del.onclick = async () => {
      if (!confirm('删除这条事实？')) return;
      await api.del('/api/facts/' + fct.id);
      renderTargetDetail(col, tid);
    };
    acts.appendChild(edit); acts.appendChild(del);
    // v1.9.1 临时事实可一键转永久（文字按钮，禁 emoji）
    if (fct.expiresAt) {
      const perm = el('span','mini-btn','永久');
      perm.title = '转为永久记忆';
      perm.onclick = async () => {
        await api.post('/api/facts/' + fct.id + '/permanent', {});
        toast('已转为永久记忆');
        renderTargetDetail(col, tid);
      };
      acts.insertBefore(perm, acts.firstChild);
    }
    row.appendChild(acts);
    fl.appendChild(row);
  });
  factsGrp.appendChild(fl);
  // 手动加事实
  const addF = el('div','form-card glass edge');
  const ff = field('手动记一条');
  addF.appendChild(ff.wrap);
  const ab = el('button','btn-primary','记下');
  ab.onclick = async () => {
    const tx = ff.input.value.trim();
    if (!tx) return;
    await api.post(`/api/targets/${tid}/facts`, { text: tx });
    renderTargetDetail(col, tid);
  };
  addF.appendChild(ab);
  factsGrp.appendChild(addF);
  col.appendChild(factsGrp);

  const delBtn = el('button','btn-ghost','删除该档案（会话将解绑）');
  delBtn.style.color = 'var(--danger)';
  delBtn.onclick = async () => {
    if (!confirm(`删除档案 ${t.codeName}？其会话会解绑但保留`)) return;
    await api.del('/api/targets/' + tid);
    await refreshTargets();
    renderTargetsPage(col);
  };
  col.appendChild(delBtn);
}

// ===== 引导页 =====
async function renderOnboarding(col){
  $('pageTitle').textContent = '欢迎使用温言';
  $('pageBack').style.visibility = 'hidden';
  col.innerHTML = '';
  const wrap = el('div','ob-wrap');
  const logo = el('div','ob-logo');
  logo.innerHTML = '<svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.38 8.38 0 0 1-8.5 8.5 8.5 8.5 0 0 1-3.8-.9L3 21l1.9-5.7a8.5 8.5 0 1 1 16.1-3.8Z"/><path d="M8.5 11.5h.01M12 11.5h.01M15.5 11.5h.01"/></svg>';
  wrap.appendChild(logo);
  wrap.appendChild(el('h1','','温言'));
  wrap.appendChild(el('p','ob-sub','恋爱里的每个问号<br>都有军师陪你想清楚'));
  const cards = el('div','ob-cards');
  [
    ['四段式回答','先接住你，再分清事实、给建议、定行动'],
    ['三套话术','稳健 / 会撩 / 强势，随时切换'],
    ['本地加密','Key 本地加密存储，你的心事只属于你'],
  ].forEach(([t,d]) => {
    const c = el('div','ob-card glass edge');
    c.appendChild(el('span','ic','✦'));
    c.appendChild(el('span','t', `<b>${esc(t)}</b> · ${esc(d)}`));
    cards.appendChild(c);
  });
  wrap.appendChild(cards);
  wrap.appendChild(el('p','ob-sub','开始前，先配置一个模型服务商（自带 7 家预设，填 Key 即可）'));
  const btn = el('button','btn-primary','去配置模型');
  btn.style.maxWidth = '480px';
  btn.onclick = async () => {
    $('pageBack').style.visibility = 'visible';
    renderProvidersPage(col);
  };
  wrap.appendChild(btn);
  const skip = el('button','btn-ghost','先随便看看');
  skip.onclick = () => { $('pageBack').style.visibility = 'visible'; closePage(); };
  wrap.appendChild(skip);
  col.appendChild(wrap);
}

// ===== 页面开关 =====
function openPage(){ $('page').classList.add('on'); }
function closePage(){
  $('page').classList.remove('on');
  $('pageBack').style.visibility = 'visible';
  go('chat');
}
$('pageBack').onclick = closePage;

// ===== 主渲染 =====
async function render(){
  const r = S.route;
  if (r === 'settings'){ openPage(); $('pageTitle').textContent='设置'; await renderSettings($('pageCol')); }
  else if (r === 'onboarding'){ openPage(); await renderOnboarding($('pageCol')); }
  else { $('page').classList.remove('on'); }
}

// ===== 启动 =====
(async function init(){
  applyTheme();
  await loadToken();
  await loadVersion();
  wireSearch();
  await Promise.all([refreshProviders(), refreshModels(), refreshTargets(), refreshSessions(), refreshSettings()]);
  // v1.9.4: 冷启动恢复上次会话——须在 refreshSessions 之后（拿列表校验），末尾 renderChat 落到该会话
  restoreSession();
  renderSidebar();
  renderModelPill();

  const ob = await api.get('/api/onboarding');
  const m = location.hash.match(/^#\/(\w+)(?:\/(\d+))?/);
  if (m){ S.route = m[1]; S.routeArg = m[2] ? Number(m[2]) : null; }
  else if (ob.needsOnboarding){ S.route = 'onboarding'; location.hash = '#/onboarding'; }
  await render();
  // v1.9.6：冷启动进入（恢复上次会话）= 进入会话，渲染后落底一次
  renderChat({ scroll: true });
})();
