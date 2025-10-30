/*
 * Android 4.4.4 WebView id 映射补丁 v3（更强约束）
 * 目标：
 *  1) 为常用 id 定义“非可配置”的 window 属性（getter：document.getElementById(name)），阻止后续 var 覆盖；
 *  2) 监听 DOMNodeInserted（同步），新插入节点即时挂到 window；
 *  3) 首秒内高频短时轮询（每 20ms 共 50 次），尽快把关键 id 绑定到 window；
 *  4) DOMContentLoaded 兜底全量扫描；
 *  5) onerror 捕获 "XXX is not defined" 动态补齐 window.XXX getter。
 */
(function(){
  function defineIdAccessorStrict(name){
    if (!name || name in window) return;
    try {
      Object.defineProperty(window, name, {
        configurable: false,
        enumerable: false,
        get: function(){ return document.getElementById(name); },
        set: function(_){ /* ignore */ }
      });
    } catch(_){}
  }

  function bindNodeToWindow(el){
    if (!el || el.nodeType !== 1) return;
    var id = el.id;
    if (!id) return;
    if (!(id in window)) {
      try { window[id] = el; } catch(_) {}
    }
  }

  function bindAllWithId(root){
    try {
      var list = (root || document).querySelectorAll('[id]');
      for (var i = 0; i < list.length; i++) bindNodeToWindow(list[i]);
    } catch(_) {}
  }

  var COMMON_IDS = [
    'top12','toptu','divDDMXS','divDDM','divMenu2','btnMenu','svgMenu',
    'topMainLeft','topMainRight','Mainleft','Mainright',
    'BottomTop','InBottomMain','vp','divPlayList',
    'aPrev','aNext','bq','tbNav','spIndex','spContent','help',
    'd0','d1','d2','d3','d4','d5','td0','td1','td2','td3','td4','td5','td6',
    'circle','demo','demo1','demo2','item','btn-left','btn-right'
  ];
  for (var i = 1; i <= 80; i++) COMMON_IDS.push('inMainleft' + i);

  for (var i = 0; i < COMMON_IDS.length; i++) defineIdAccessorStrict(COMMON_IDS[i]);

  try {
    document.addEventListener('DOMNodeInserted', function(ev){
      var el = ev && ev.target;
      if (el && el.nodeType === 1) {
        bindNodeToWindow(el);
        if (el.querySelectorAll) bindAllWithId(el);
      }
    }, true);
  } catch(_) {}

  (function eagerPoll(){
    var tries = 0, MAX = 50, STEP = 20;
    var timer = setInterval(function(){
      tries++;
      bindAllWithId(document);
      var ok = !!(window.top12 && window.d0);
      if (ok || tries >= MAX) clearInterval(timer);
    }, STEP);
  })();

  try {
    document.addEventListener('DOMContentLoaded', function(){ bindAllWithId(document); }, false);
  } catch(_) {}

  try {
    window.addEventListener('error', function(e){
      var msg = (e && e.message) || '';
      var m = /(?:ReferenceError: )?([A-Za-z_][A-Za-z0-9_]*) is not defined/.exec(msg);
      if (m && m[1]) {
        defineIdAccessorStrict(m[1]);
        bindAllWithId(document);
      }
    }, true);
  } catch(_) {}
})();
