/* 温言桌面版 · 前端逻辑
   纯原生 JS：hash 路由 + fetch SSE 流式渲染（EventSource 不支持 POST，故用 fetch ReadableStream）。
   后端契约：见 ApiRoutes.kt。聊天 SSE 事件帧 data:{type:chat|thinking|card|done|error}
*/
'use strict';

const APP_VERSION = '1.8.1';

// ===== 工具 =====
const $ = id => document.getElementById(id);
const el = (tag, cls, html) => {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (html !== undefined) e.innerHTML = html;
  return e;
};
const esc = s => String(s ?? '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
const api = {
  async get(p){ const r = await fetch(p); return r.json(); },
  async send(method, p, body){
    const r = await fetch(p, { method, headers:{'Content-Type':'application/json'}, body: body ? JSON.stringify(body) : undefined });
    return r.json();
  },
  post(p,b){ return api.send('POST',p,b); },
  put(p,b){ return api.send('PUT',p,b); },
  del(p){ return api.send('DELETE',p); },
};
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
  pendingImages: [],          // 已上传的 dataUrl 列表
  theme: localStorage.getItem('wenyan.theme') || 'light',
  route: 'chat',
  pendingTargetId: Number(localStorage.getItem('wenyan.pendingTargetId')) || null, // 未建会话时的待绑定档案
  sessionTargetId: undefined,   // 当前会话已绑定的档案（undefined=尚未加载）
};

// ===== 主题 =====
function applyTheme(){
  document.documentElement.dataset.theme = S.theme;
  localStorage.setItem('wenyan.theme', S.theme);
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
      if (S.sessionId === s.id){ S.sessionId = null; S.streamSeq++; }  // 使该会话的在途流事件失效
      await refreshSessions(); renderSidebar(); renderChat();
    };
    item.appendChild(del);
    item.onclick = () => {
      if (S.streaming && S.streamSessionId != null && S.streamSessionId !== s.id){
        toast('军师还在奋笔疾书，写完这一轮再切换');
        return;
      }
      S.sessionId = s.id; S.streamSeq++; renderSidebar(); renderChat();
    };
    list.appendChild(item);
  });
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

function renderModelSheet(){
  setSheetHead('选择模型', '点按切换，实时生效');
  const body = $('modelSheetBody'); body.innerHTML = '';
  if (!S.models.length){
    body.appendChild(el('div','sb-empty','还没有可用模型<br>请到 设置 → 模型管理 配置'));
    return;
  }
  S.models.forEach(m => {
    const p = providerById(m.providerId);
    const row = el('div','mrow' + (m.id === S.currentModelId ? ' on' : ''));
    row.appendChild(el('span','sic', esc(shortName(m.name))));
    const tx = el('span','tx');
    tx.appendChild(el('span','t', esc(m.name)));
    tx.appendChild(el('span','d', esc(p ? p.name : '') + (m.supportsVision ? ' · 支持图片' : '')));
    row.appendChild(tx);
    row.appendChild(el('span','check'));
    row.onclick = () => {
      S.currentModelId = m.id;
      localStorage.setItem('wenyan.modelId', m.id);
      renderModelPill(); renderModelSheet();
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
async function renderChat(){
  const col = $('chatCol'); col.innerHTML = '';
  if (S.sessionId == null){
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
  const sid = S.sessionId;                      // await 期间用户可能已切走
  const msgs = await api.get(`/api/sessions/${sid}/messages`);
  if (sid !== S.sessionId) return;              // 迟到响应：丢弃，避免写回旧会话消息
  if (!msgs.length){
    $('emptyState').classList.remove('hidden');
    $('chatScroll').classList.add('hidden');
    renderEmpty();
    return;
  }
  // 该会话正在流式：容器已含在途气泡/卡片，只补卡片之前的落库消息，避免双份
  const streamingHere = S.streaming && S.streamSessionId === sid;
  const renderMsgs = streamingHere
    ? msgs.filter(m => !(m.role === 'USER' && msgs.indexOf(m) === msgs.length - 1))
    : msgs;
  renderMsgs.forEach(m => {
    if (m.role === 'USER') appendUserBubble(m.content, m.type);
    else if (m.type === 'analysis') appendAnalysisCard(m.content, false);
  });
  scrollBottom();
}
function scrollBottom(){
  const sc = $('chatScroll');
  requestAnimationFrame(()=>{ sc.scrollTop = sc.scrollHeight; });
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
  } else {
    b.appendChild(el('span','',esc(content)));
  }
  col.appendChild(b);
}

// analysis content 是四段 JSON 原文
function appendAnalysisCard(raw, animate){
  let a;
  try { a = JSON.parse(raw); } catch(e){ a = null; }
  const col = $('chatCol');
  if (!a){
    col.appendChild(el('div','msg-ai glass edge','<div class="lead">（回复解析失败）</div>'));
    return;
  }
  col.appendChild(buildCard(a));
}
function buildCard(a){
  const card = el('div','msg-ai glass edge sheen');
  card.appendChild(el('div','sheen-layer'));
  if (a.empathy) card.appendChild(el('div','lead', esc(a.empathy)));
  // facts
  const facts = a.facts || {};
  const chips = [];
  (facts.known||[]).forEach(t => chips.push(['已知',t]));
  (facts.assumed||[]).forEach(t => chips.push(['推测',t]));
  (facts.unknown||[]).forEach(t => chips.push(['未知',t]));
  if (chips.length){
    const wrap = el('div');
    wrap.appendChild(el('span','sec','先分清事实'));
    const row = el('div','facts-row'); row.style.marginTop = '6px';
    chips.forEach(([k,t]) => row.appendChild(el('span','fchip', esc(`${k} · ${t}`))));
    wrap.appendChild(row);
    card.appendChild(wrap);
  }
  // advice
  const adv = a.advice || {};
  const styles = adv.styles || [];
  if (adv.core || styles.length){
    const ac = el('div','advice-card');
    ac.appendChild(el('span','sec', adv.tag ? `军师建议 · ${esc(adv.tag)}` : '军师建议'));
    if (adv.core) ac.appendChild(el('div','core-txt', esc(adv.core)));
    if ((adv.reasons||[]).length){
      const ul = el('ul','reason-list');
      adv.reasons.forEach(r => ul.appendChild(el('li','',esc(r))));
      ac.appendChild(ul);
    }
    if (styles.length){
      const tabs = el('div','style-tabs');
      const coreTxt = el('div','core-txt', esc(styles[0].text || ''));
      styles.forEach((st,i) => {
        const b = el('button','sty' + (i===0?' on':''), esc(st.label || `风格${i+1}`));
        b.onclick = () => {
          tabs.querySelectorAll('.sty').forEach((x,j)=>x.classList.toggle('on', j===i));
          coreTxt.textContent = st.text || '';
        };
        tabs.appendChild(b);
      });
      ac.appendChild(tabs); ac.appendChild(coreTxt);
      // 复制成品话术
      const copy = el('button','copy-btn','复制话术');
      copy.onclick = () => {
        navigator.clipboard.writeText(coreTxt.textContent).then(()=>toast('已复制'));
      };
      ac.appendChild(copy);
    }
    card.appendChild(ac);
  }
  // actions
  if ((a.actions||[]).length){
    const wrap = el('div');
    wrap.appendChild(el('span','sec','现在可以做什么'));
    const list = el('div','actions-list'); list.style.marginTop='6px';
    a.actions.forEach(it => {
      const row = el('div','action-item');
      if (it.label) row.appendChild(el('span','lbl', esc(it.label)));
      row.appendChild(document.createTextNode(it.text || ''));
      list.appendChild(row);
    });
    wrap.appendChild(list);
    card.appendChild(wrap);
  }
  // reply（首选风格成品话术，无 styles 时兜底）
  if (a.reply && !styles.length){
    const ac = el('div','advice-card');
    ac.appendChild(el('span','sec','可以回'));
    const txt = el('div','core-txt', esc(a.reply));
    ac.appendChild(txt);
    if (a.replyTiming) ac.appendChild(el('div','cite', esc(a.replyTiming)));
    const copy = el('button','copy-btn','复制话术');
    copy.onclick = () => navigator.clipboard.writeText(a.reply).then(()=>toast('已复制'));
    ac.appendChild(copy);
    card.appendChild(ac);
  }
  // 引用 / 安全
  if ((a.citations||[]).length)
    card.appendChild(el('div','cite', '参考知识库：' + a.citations.join('、')));
  if (a.safetyOverride && a.safetyMessage){
    const s = el('div','core-txt', esc(a.safetyMessage));
    s.style.color = 'var(--danger)';
    card.appendChild(s);
  }
  return card;
}

// ===== 空状态 =====
const EXAMPLES = [
  ['关系升温','他为什么突然冷淡了？'],
  ['表白时机','要不要主动表白？'],
  ['关系判断','他是不是在养鱼？'],
  ['自然推进','怎么自然地推进关系？'],
];
function renderEmpty(){
  $('mottoDate').textContent = new Date().toLocaleDateString('zh-CN',{month:'long',day:'numeric',weekday:'long'});
  const g = $('exampleGrid'); g.innerHTML = '';
  EXAMPLES.forEach(([tag,q]) => {
    const c = el('button','ecard glass edge');
    c.appendChild(el('span','tag', esc(tag)));
    c.appendChild(el('span','q', esc(q)));
    c.onclick = () => { $('inputBox').value = q; $('inputBox').focus(); autoGrow(); };
    g.appendChild(c);
  });
}

// ===== 输入 / 发送 =====
const inputBox = $('inputBox');
function autoGrow(){
  inputBox.style.height = 'auto';
  inputBox.style.height = Math.min(inputBox.scrollHeight, 160) + 'px';
}
inputBox.addEventListener('input', autoGrow);
inputBox.addEventListener('keydown', e => {
  if (e.key === 'Enter' && !e.shiftKey){ e.preventDefault(); sendMessage(); }
});
$('btnSend').onclick = sendMessage;

// 图片上传
$('btnAttach').onclick = () => $('fileInput').click();
$('fileInput').addEventListener('change', async e => {
  const files = Array.from(e.target.files || []).slice(0, 10 - S.pendingImages.length);
  if (!files.length){ e.target.value=''; return; }
  const fd = new FormData();
  files.forEach(f => fd.append('images', f));
  try {
    const r = await fetch('/api/images/upload', { method:'POST', body: fd });
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
  S.sessionId = r.id;
  await refreshSessions(); renderSidebar();
  return S.sessionId;
}

async function sendMessage(){
  const text = inputBox.value.trim();
  if ((!text && !S.pendingImages.length) || S.streaming) return;
  if (S.currentModelId == null){ toast('请先选择模型'); openSheet(); renderModelSheet(); return; }
  const m = modelById(S.currentModelId);
  const p = m ? providerById(m.providerId) : null;
  if (!p || !p.hasApiKey){ toast('该模型未配置 API Key，请到设置配置'); go('settings'); return; }

  const wasNew = S.sessionId == null;
  const sid = await ensureSession();
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

  // 思考占位
  const think = el('div','think-bubble glass edge','正在翻知识库，梳理你的处境…<span class="dots"><i></i><i></i><i></i></span>');
  col.appendChild(think);
  scrollBottom();

  setStreaming(true);
  S.streamSessionId = sid;
  const mySeq = ++S.streamSeq;                 // 本轮流的令牌；切会话/删除会使其过期
  const live = () => mySeq === S.streamSeq;    // 事件落地前校验
  const aiCard = el('div','msg-ai glass edge');
  const lead = el('div','lead streaming');
  let thinkingBox = null;
  let gotCard = false;
  let settled = false;                // error/done 已收尾：流尾兜底不再触发（防误报「回复中断」+ 防 renderChat 抹掉已渲染内容）

  try {
    const resp = await fetch('/api/chat/stream', {
      method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({ sessionId: sid, modelId: S.currentModelId, text: text || '[图片]', imageDataUrls: images }),
    });
    if (!resp.ok || !resp.body) throw new Error('HTTP ' + resp.status);
    const reader = resp.body.getReader();
    const dec = new TextDecoder();
    let buf = '';
    for(;;){
      const { done, value } = await reader.read();
      if (done) break;
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
    if (!gotCard && !settled){                     // 真·异常中断（无 error/done 帧）：收尾解锁
      think.remove();
      if (!lead.parentNode) col.appendChild(el('div','msg-ai glass edge',`<div class="lead" style="color:var(--danger)">回复中断，请重试</div>`));
      else lead.classList.remove('streaming');
      setStreaming(false);
      refreshSessions().then(renderSidebar);       // 只刷侧栏；不 renderChat（清场会抹掉刚 append 的回复中断气泡）
    }
  } catch(err){
    if (!live()){ setStreaming(false); return; }   // 过期流的异常不回写 UI，但同样要解锁防死锁
    think.remove();
    col.appendChild(el('div','msg-ai glass edge',`<div class="lead" style="color:var(--danger)">连接中断，请重试</div>`));
    setStreaming(false);
    return;
  }

  function handleEvent(ev){
    if (!live()) return;                        // 切会话/删除后的迟到事件一律丢弃
    if (ev.type === 'chat'){
      if (think.parentNode){ think.remove(); col.appendChild(aiCard); aiCard.appendChild(lead); }
      lead.textContent += ev.text;
      scrollBottom();
    } else if (ev.type === 'thinking'){
      if (!thinkingBox){
        thinkingBox = el('div','thinking-box');
        if (think.parentNode) think.replaceChildren(thinkingBox); else col.insertBefore(thinkingBox, aiCard);
      }
      thinkingBox.textContent += ev.text;
      scrollBottom();
    } else if (ev.type === 'card'){
      gotCard = true;
      think.remove(); aiCard.remove();
      col.appendChild(buildCard(ev.card));
      scrollBottom();
    } else if (ev.type === 'done'){
      settled = true;
      setStreaming(false);
      think.remove();
      if (!gotCard && lead.parentNode) lead.classList.remove('streaming');
      // 只刷侧栏标题；不 renderChat（卡片已在 DOM，重画会抹掉危机预检等未落库卡片）
      refreshSessions().then(renderSidebar);
    } else if (ev.type === 'error'){
      settled = true;
      setStreaming(false);
      think.remove(); aiCard.remove();
      col.appendChild(el('div','msg-ai glass edge',`<div class="lead" style="color:var(--danger)">${esc(ev.message||'出错了')}</div>`));
      scrollBottom();
    }
  }
}

function setStreaming(v){
  S.streaming = v;
  $('btnSend').disabled = v;
  inputBox.disabled = v;
  $('tbDot').className = 'tb-dot' + (v ? ' think' : '');
}

// ===== 侧栏 =====
$('btnNewSession').onclick = () => {
  if (S.streaming){ toast('军师还在奋笔疾书，写完这一轮再开新会话'); return; }
  S.sessionId = null; renderSidebar(); renderChat(); inputBox.focus();
};
$('btnToggleSb').onclick = () => $('sidebar').classList.toggle('closed');
$('btnSettings').onclick = () => go('settings');

// ===== 设置页 =====
let settingsSeq = 0;  // 设置页渲染令牌：并发/重复触发时作废旧执行，防分组重复 append
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
  curRow.onclick = () => { closePage(); openSheet(); renderModelSheet(); };
  g2.appendChild(curRow);
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
  col.appendChild(g3);

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
      S.sessionId = null;
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
  const chk = el('span','ch','v' + APP_VERSION + ' · 检查更新');
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

async function renderProviderDetail(col, pid){
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
    if (isNew){
      const r = await api.post('/api/providers', { name, baseUrl, apiKey: kf.input.value.trim() || null });
      toast('已添加厂商');
      await refreshProviders();
      renderProviderDetail(col, r.id);
    } else {
      const body = { name, baseUrl };
      if (kf.input.value.trim()) body.apiKey = kf.input.value.trim();
      await api.put('/api/providers/' + pid, body);
      toast('已保存 · Key 加密存储于本地');
      await refreshProviders();
      renderProviderDetail(col, pid);
    }
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
    const r = await api.post('/api/targets', { codeName: name });
    await refreshTargets();
    renderTargetDetail(col, r.id);
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
    row.appendChild(el('span','txt', esc(fct.text)));
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
  await Promise.all([refreshProviders(), refreshModels(), refreshTargets(), refreshSessions()]);
  renderSidebar();
  renderModelPill();

  const ob = await api.get('/api/onboarding');
  const m = location.hash.match(/^#\/(\w+)(?:\/(\d+))?/);
  if (m){ S.route = m[1]; S.routeArg = m[2] ? Number(m[2]) : null; }
  else if (ob.needsOnboarding){ S.route = 'onboarding'; location.hash = '#/onboarding'; }
  await render();
  renderChat();
})();
