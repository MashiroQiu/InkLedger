'use strict';
// Start at maximum speed and decelerate continuously to rest.
const MOTION_EASING='cubic-bezier(.16,1,.3,1)';
const BUDGET_EXPAND_EASING='cubic-bezier(.08,1,.2,1)';
const $=s=>document.querySelector(s), esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const paths={check:'m5 12 4 4 10-10',menu:'M4 7h16M4 12h16M4 17h10',home:'m3 10 9-7 9 7v10h-6v-7H9v7H3z',settings:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1z',plus:'M12 4v16M4 12h16',chart:'M4 20V5M4 20h17M8 15v-4M13 15V8M18 15V4',arrow:'m9 5 7 7-7 7',back:'m15 5-7 7 7 7',close:'m6 6 12 12M6 18 18 6',book:'M5 3h14v18H5zM8 3v18M11 7h5M11 11h5',wallet:'M3 6h17v14H3zM3 6l14-3v3M15 11h6v5h-6z',trash:'M4 6h16M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7M14 10v7',food:'M5 3v7M9 3v7M5 7h4M7 10v11M17 3c-3 3-3 8 0 8h2V3M19 11v10',transport:'M6 4h12l2 12H4zM7 16v4M17 16v4M7 8h10M7 12h1M16 12h1',shop:'M4 7h16l-1 14H5zM8 7V5a4 4 0 0 1 8 0v2',daily:'M4 9h16v12H4zM3 9l9-6 9 6M9 21v-7h6v7',salary:'M3 7h18v13H3zM8 7V4h8v3M3 12h18M10 12v3h4v-3',health:'M9 3h6v6h6v6h-6v6H9v-6H3V9h6z',fun:'M6 6h12l3 12h-4l-3-3h-4l-3 3H3zM7 9v5M5 11h4M16 10h.01M18 13h.01',other:'M5 5h5v5H5zM14 5h5v5h-5zM5 14h5v5H5zM14 14h5v5h-5z',search:'M10 3a7 7 0 1 0 0 14 7 7 0 0 0 0-14m5 12 6 6',download:'M12 3v12m-5-5 5 5 5-5M4 17v4h16v-4',user:'M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8M4 21v-3a8 8 0 0 1 16 0v3'};
const iconMarkup=new Map(Object.entries(paths).map(([name,path])=>[name,`<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${path}"/></svg>`]));
const icon=name=>iconMarkup.get(name)||iconMarkup.get('other');
const cats=[{id:'food',name:'餐饮',type:'expense'},{id:'transport',name:'交通',type:'expense'},{id:'shop',name:'购物',type:'expense'},{id:'daily',name:'生活',type:'expense'},{id:'health',name:'医疗',type:'expense'},{id:'fun',name:'娱乐',type:'expense'},{id:'other',name:'其他',type:'expense'},{id:'salary',name:'工资',type:'income'},{id:'bonus',name:'奖金',type:'income'}];
const category=id=>db.categories.find(c=>c.id===id)||{id:'other',name:'其他',type:'expense'};
const moneyFormatter=new Intl.NumberFormat('en-US',{minimumFractionDigits:2,maximumFractionDigits:2});
const fmt=cents=>moneyFormatter.format(cents/100);
const inputMoney=cents=>(cents/100).toFixed(2);
function parseMoney(value){if(!/^\d{1,10}(\.\d{1,2})?$/.test(String(value)))throw Error('请输入最多两位小数的非负金额');const [a,b='']=String(value).split('.');return Number(a)*100+Number(b.padEnd(2,'0'));}
const dateISO=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
const today=()=>dateISO(new Date()), localTime=()=>`${String(new Date().getHours()).padStart(2,'0')}:${String(new Date().getMinutes()).padStart(2,'0')}`;
const uid=()=>globalThis.crypto?.randomUUID?.()||`${Date.now()}-${Math.random().toString(36).slice(2)}`;
const blank=()=>({version:1,books:[{id:'default',name:'日常账本'}],book:'default',entries:[],trash:[],budgets:{},categories:structuredClone(cats),settings:{effect:'balanced',budgetMode:'month',tabletDockPosition:'center'},demo:false});
function demo(){const d=blank();d.demo=true;d.books[0].name='日常 · 演示账本';const now=new Date(),y=now.getFullYear(),m=now.getMonth();const add=(day,cat,cents,note,type='expense')=>d.entries.push({id:uid(),book:'default',date:dateISO(new Date(y,m,day)),time:'12:30',cat,cents,note,type});add(now.getDate(),'food',3850,'午餐');add(now.getDate(),'transport',600,'地铁通勤');add(now.getDate(),'shop',12900,'生活用品');if(now.getDate()>1){add(now.getDate()-1,'food',2800,'咖啡与早餐');add(now.getDate()-1,'daily',8600,'日常生活用品');}if(now.getDate()>2){add(now.getDate()-2,'salary',1250000,'月度工资','income');add(now.getDate()-2,'fun',6800,'周末电影');}for(let day=1;day<now.getDate()-2;day++){add(day,'food',day%2?4800:3250,'日常餐饮');if(day%3===0)add(day,'shop',15600,'衣物与日用品');}const key=`default:month:${y}-${String(m+1).padStart(2,'0')}`;d.budgets[key]={total:600000,categories:{food:180000,transport:80000,shop:160000,daily:100000}};d.budgets[`default:year:${y}`]={total:7200000,categories:{food:2160000,transport:960000,shop:1920000,daily:1200000}};return d;}
let storageError='';function readDB(){try{const raw=window.AndroidStore?AndroidStore.read():localStorage.getItem('inkledger');return raw?validateDB(JSON.parse(raw)):null;}catch(e){storageError='已有数据无法读取，已保留原数据；请先导入有效备份。';return blank();}}
function validateDB(d){if(!d||d.version!==1||!Array.isArray(d.entries)||!Array.isArray(d.books)||!d.books.length||!Array.isArray(d.categories)||!Array.isArray(d.trash)||!d.settings||!d.budgets||!d.books.some(b=>b.id===d.book))throw Error('备份格式不正确');const ids=new Set();for(const b of d.books){if(typeof b.id!=='string'||typeof b.name!=='string')throw Error('账本格式不正确');}for(const c of d.categories){if(typeof c.id!=='string'||typeof c.name!=='string'||!['income','expense'].includes(c.type))throw Error('分类格式不正确');}for(const e of [...d.entries,...d.trash]){if(typeof e.id!=='string'||ids.has(e.id)||!Number.isSafeInteger(e.cents)||e.cents<=0||typeof e.note!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(e.date)||!/^\d{2}:\d{2}$/.test(e.time)||!d.books.some(b=>b.id===e.book)||!d.categories.some(c=>c.id===e.cat)||!['income','expense'].includes(e.type))throw Error('账单数据不完整');ids.add(e.id);}for(const [key,b] of Object.entries(d.budgets)){if(!Number.isSafeInteger(b.total)||b.total<0||!b.categories||Object.values(b.categories).some(n=>!Number.isSafeInteger(n)||n<0))throw Error('预算数据不完整');}return d;}
function assertBackupSafety(d){
 if(d.settings.tabletDockPosition===undefined)d.settings.tabletDockPosition='center';
 if(!['left','center','right'].includes(d.settings.tabletDockPosition))throw Error('平板底栏位置不正确');
 const safeId=s=>typeof s==='string'&&/^[A-Za-z0-9_-]{1,100}$/.test(s);
 if(!['strong','balanced','weak'].includes(d.settings.effect)||!['month','year'].includes(d.settings.budgetMode))throw Error('设置格式不正确');
 if(new Set(d.books.map(b=>b.id)).size!==d.books.length||new Set(d.categories.map(c=>c.id)).size!==d.categories.length)throw Error('标识符重复');
 for(const b of d.books)if(!safeId(b.id)||b.name.length>80)throw Error('账本标识符或名称不正确');
 for(const c of d.categories)if(!safeId(c.id)||!c.name||[...c.name].reduce((a,s)=>a+(/[a-zA-Z0-9]/.test(s)?1:2),0)>10)throw Error('分类名称或标识符不正确');
 for(const e of [...d.entries,...d.trash]){if(!safeId(e.id)||!safeId(e.book)||!safeId(e.cat)||e.note.length>200||isNaN(new Date(e.date+'T12:00:00').getTime())||dateISO(new Date(e.date+'T12:00:00'))!==e.date||!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(e.time)||d.categories.find(c=>c.id===e.cat).type!==e.type)throw Error('账单日期、分类或标识符不正确');}
 for(const [key,b] of Object.entries(d.budgets)){const parts=key.split(':');if(parts.length!==3||!d.books.some(book=>book.id===parts[0])||!['month','year'].includes(parts[1])||(parts[1]==='month'?!/^\d{4}-(0[1-9]|1[0-2])$/.test(parts[2]):!/^\d{4}$/.test(parts[2])))throw Error('预算周期格式不正确');for(const id of Object.keys(b.categories))if(!safeId(id)||!d.categories.some(c=>c.id===id&&c.type==='expense'))throw Error('预算分类不正确');if(!Number.isSafeInteger(Object.values(b.categories).reduce((a,n)=>a+n,0)))throw Error('分类预算合计超出精确范围');}
 if(!Number.isSafeInteger([...d.entries,...d.trash].reduce((a,e)=>a+e.cents,0)))throw Error('金额总计超出精确计算范围');
 return d;
}
const originalValidate=validateDB;validateDB=d=>assertBackupSafety(originalValidate(d));
const stored=readDB();let db=stored||blank();if(new URLSearchParams(location.search).get('demo')==='1'&&!stored)db=demo();
let committed=JSON.stringify(db);let page='home',rangeMode='month',range=today().slice(0,7),budgetMode='month',budgetRange=range,search='',modalOpen=false;
function persist(){try{if(storageError)throw Error('数据损坏，需导入备份恢复');const raw=JSON.stringify(db);if(window.AndroidStore){if(!AndroidStore.write(raw))throw Error('写入失败');}else localStorage.setItem('inkledger',raw);storageError='';committed=raw;return true;}catch(e){db=JSON.parse(committed);toast(storageError||'保存失败：请检查设备剩余空间，并导出备份');return false;}}
function toast(s){$('#toast').textContent=s;$('#toast').classList.add('show');clearTimeout(toast.timer);toast.timer=setTimeout(()=>$('#toast').classList.remove('show'),2600);}
function selectedEntries(mode=rangeMode,r=range){return db.entries.filter(e=>e.book===db.book&&e.date.startsWith(r.slice(0,mode==='month'?7:4)));}
const sum=(entries,type)=>{let total=0;for(const e of entries)if(e.type===type)total+=e.cents;return total;};
function expenseByCategory(entries){const totals=new Map();for(const e of entries)if(e.type==='expense'){let value=totals.get(e.cat);if(!value){value={cents:0,count:0};totals.set(e.cat,value);}value.cents+=e.cents;value.count++;}return totals;}
function budgetKey(mode=budgetMode,r=budgetRange){return `${db.book}:${mode}:${r.slice(0,mode==='month'?7:4)}`;}
function budget(mode=budgetMode,r=budgetRange){return db.budgets[budgetKey(mode,r)]||{total:0,categories:{}};}
function percentage(used,total){return total?Math.max(0,Math.min(100,used/total*100)):0;}
let highlightBatch=null;
function renderSidebar(){const root=$('#sidebar'),key=JSON.stringify([db.book,db.books.find(b=>b.id===db.book)?.name,db.demo]);const previous=highlightBatch?highlightBatch.previous.get(root):highlightPosition(root);if(root.dataset.renderKey===key){root.querySelectorAll('.side-item').forEach(el=>el.classList.toggle('active',el.dataset.page===page));moveHighlight(root,'.side-item.active',previous);return;}root.dataset.renderKey=key;const items=[['home','home','账单一览'],['budget','wallet','预算管理'],['stats','chart','收支统计'],['search','search','搜索账单'],['categories','other','分类管理']];$('#sidebar').innerHTML=`<button class="side-close" data-action="menu" aria-label="关闭菜单">${icon('close')}</button><div class="brand"><div class="brand-mark">墨</div><div><b>墨账</b><small>INK LEDGER</small></div></div><div class="book-label">当前账本</div><button class="book-switch" data-action="books">${icon('book')}<div><b>${esc(db.books.find(b=>b.id===db.book)?.name)}</b><small>${db.demo?'独立演示数据 · 可随时清空':'离线保存'}</small></div></button>${items.map(([p,i,t])=>`<button class="side-item ${page===p?'active':''}" data-action="nav" data-page="${p}">${icon(i)}${t}</button>`).join('')}<div class="sidebar-foot">离线账本 · 数据仅保存在此设备</div>`;moveHighlight($('#sidebar'),'.side-item.active',previous);}
function renderToolbar(){if(page==='trash'&&settingsSubpage){$('#toolbar').innerHTML=`<button class=icon-btn data-action=settings aria-label=返回设置>${icon('back')}</button><div class=toolbar-center>回收站</div><div class=icon-btn></div>`;return;}if(page==='settings'){$('#toolbar').innerHTML='<div class=toolbar-center>设置页</div>';return;}let center=page==='budget'?`<div class="tabs glass"><button data-action="budget-mode" data-mode="month" class="${budgetMode==='month'?'selected':''}">月度</button><button data-action="budget-mode" data-mode="year" class="${budgetMode==='year'?'selected':''}">年度</button></div>`:page==='home'||page==='search'||page==='stats'?`<button class="period" data-action="range"><span>${rangeMode==='month'?range.replace('-','.'):range.slice(0,4)} ${icon('arrow').replace('<svg','<svg style="width:12px;height:12px;transform:rotate(90deg)"')}</span><small>${rangeMode==='month'?'月度账单':'年度账单'}${db.demo?' · 演示':''}</small></button>`:`<span style="font-size:17px">${({settings:'设置',trash:'回收站',categories:'分类管理'})[page]||'墨账'}</span>`;$('#toolbar').innerHTML=`<button class="icon-btn" data-action="${page==='budget'?'home':'menu'}" aria-label="${page==='budget'?'返回主页':'切换侧边栏'}">${icon(page==='budget'?'back':'menu')}</button><div class="toolbar-center">${center}</div><button class="icon-btn" data-action="${page==='home'?'stats':'menu'}" aria-label="${page==='home'?'查看统计':'切换侧边栏'}">${icon(page==='home'?'chart':'menu')}</button>`;}
function renderDock(){
 if(selectionActive){renderSelectionDock();return;}
 const previous=highlightBatch?highlightBatch.previous.get($('#dock')):highlightPosition($('#dock'));
 $('#dock').classList.remove('selection-dock');
 const position=matchMedia('(min-width:840px)').matches?db.settings.tabletDockPosition:'center';
 if($('#dock').dataset.position===position&&$('#dock [data-action=functions]')){$('#dock [data-action=functions]').classList.toggle('selected',!isSettingsPage());$('#dock [data-action=settings]').classList.toggle('selected',isSettingsPage());moveHighlight($('#dock'),'button.selected',previous);return;}
 const home=`<button data-action="functions" class="${!isSettingsPage()?'selected':''}">${icon('home')}<span>功能页</span></button>`;
 const settings=`<button data-action="settings" class="${isSettingsPage()?'selected':''}">${icon('settings')}<span>设置页</span></button>`;
 const add=`<button class="add" data-action="add" aria-label="记一笔">${icon('plus')}</button>`;
 $('#dock').dataset.position=position;
 $('#dock').innerHTML=(position==='left'?[add,home,settings]:position==='right'?[home,settings,add]:[home,add,settings]).join('');
 moveHighlight($('#dock'),'button.selected',previous);
}
function render(keepScroll=false,timing=null){
 const previousTiming=selectionMotionTiming;selectionMotionTiming=timing;cancelPageTransition();
 const top=keepScroll?$('#scroll').scrollTop:0;
 const html=page==='home'?homeHTML():page==='budget'?budgetHTML():page==='settings'?settingsHTML():page==='trash'?trashHTML():page==='stats'?statsHTML():page==='categories'?categoriesHTML():searchHTML();
 const roots=[$('#sidebar'),$('#dock')];highlightBatch={previous:new Map(roots.map(root=>[root,highlightPosition(root)])),moves:[]};
 try{
  document.body.dataset.effect=db.settings.effect;document.body.classList.toggle('settings-open',isSettingsPage());
  renderSidebar();renderToolbar();renderDock();$('#content').dataset.page=page;$('#content').innerHTML=html;$('#scroll').scrollTop=top;updateFade();
  const moves=highlightBatch.moves.map(move=>{const target=move.root.querySelector(move.selector);return {...move,geometry:target?{x:target.offsetLeft,y:target.offsetTop,w:target.offsetWidth,h:target.offsetHeight}:null};});
  highlightBatch=null;for(const move of moves)moveHighlight(move.root,move.selector,move.previous,move.geometry);
 }finally{highlightBatch=null;selectionMotionTiming=previousTiming;}
}
function budgetCard(title,mode,r){const b=budget(mode,r),used=sum(selectedEntries(mode,r),'expense'),placeholder='-'.repeat(String(Math.floor(used/100)).length)+'.--';return `<button class="card budget-card" data-action="${page==='budget'?'set-total':'budget'}"><div class="card-title">${title}${icon('arrow')}</div><div class="progress" role="progressbar" aria-valuenow="${percentage(used,b.total).toFixed(0)}" aria-valuemin="0" aria-valuemax="100"><i style="width:${percentage(used,b.total)}%"></i></div><div class="metrics"><span>用量<b>${fmt(used)}</b></span><span>总量<b>${b.total?fmt(b.total):placeholder}</b></span><span>余量<b>${b.total?fmt(b.total-used):placeholder}</b></span></div></button>`;}
function homeHTML(){const entries=selectedEntries(),expense=sum(entries,'expense'),income=sum(entries,'income'),mode=db.settings.budgetMode,r=mode==='month'?(rangeMode==='month'?range:range.slice(0,4)+'-'+today().slice(5,7)):range.slice(0,4);return `<section class="hero"><div class="eyebrow">合计支出</div><div class="total"><small>¥</small>${fmt(expense)}</div><div class="hero-foot"><span>合计收入<strong>${fmt(income)}</strong></span><span>合计结余<strong>${fmt(income-expense)}</strong></span></div></section><div class="home-layout"><div class="home-left">${budgetCard(mode==='month'?'月度预算':'年度预算',mode,r)}</div><div class="home-right">${daysHTML(entries)}</div></div>`;}
function dayLabel(date){const dif=Math.round((new Date(today()+'T12:00:00')-new Date(date+'T12:00:00'))/86400000),d=new Date(date+'T12:00:00');return `<span class=day-date>${date.slice(5).replace('-','.')}</span><span class=day-week>星期${'日一二三四五六'[d.getDay()]}</span>${dif>=0&&dif<=2?'<span class=day-relative>'+['今天','昨天','前天'][dif]+'</span>':''}`;}
function daysHTML(entries){
 if(!entries.length)return '<div class="card empty">暂无账单<br><br><button class="secondary" data-action="add">记第一笔</button></div>';
 const groups=new Map();for(const e of entries){let group=groups.get(e.date);if(!group){group=[];groups.set(e.date,group);}group.push(e);}
 const dates=[...groups.keys()].sort().reverse();
 return dates.map(date=>{
  const es=groups.get(date).sort((a,b)=>b.time.localeCompare(a.time));
  return `<div class="swipe-shell" data-day-shell="${date}"><button class="delete-reveal" data-action="delete-day" data-date="${date}" aria-label="删除当天账单">${icon('trash')}删除</button><section class="card day-card swipe-target" data-swipe-day="${date}"><button class="day-head" data-action="${selectionActive?'day-select':'day-add'}" data-date="${date}" ${selectionActive?`aria-pressed="${es.every(e=>selectedIds.has(e.id))}"`:''}><span class="date-title">${dayLabel(date)}</span><span class="day-sum"><b class="expense">支 ${fmt(sum(es,'expense'))}</b><b class="income">收 ${fmt(sum(es,'income'))}</b></span></button>${es.map(e=>{
   const chosen=selectionActive&&selectedIds.has(e.id),action=selectionActive?'toggle-selection':'details';
   return `<div class="entry-wrap"><button class="delete-reveal" data-action="delete" data-id="${e.id}" aria-label="删除账单">${icon('trash')}</button><div class="entry swipe-target ${chosen?'is-selected':''}" data-swipe-id="${e.id}"><button class="cat-icon" data-action="${selectionActive?'toggle-selection':'quick-cat'}" data-id="${e.id}" aria-label="${selectionActive?'选择账单':'修改'+esc(category(e.cat).name)+'分类'}" ${selectionActive?`aria-pressed="${chosen}"`:''}>${selectionActive?`<span class="selection-check">${chosen?icon('check'):''}</span>`:icon(e.cat)}</button><button class="entry-hit" data-action="${action}" data-id="${e.id}" ${selectionActive?`aria-pressed="${chosen}"`:''}><span class="entry-copy"><b>${esc(category(e.cat).name)}</b><small>${esc(e.note||'未填写备注')}</small></span><span class="amount ${e.type}">${e.type==='income'?'+':'−'}${fmt(e.cents)}</span></button></div></div>`;
  }).join('')}</section></div>`;
 }).join('');
}
function budgetHTML(){const b=budget(),es=selectedEntries(budgetMode,budgetRange),totals=expenseByCategory(es),names=Object.keys(b.categories);return `<div class="budget-period"><button data-action="shift" data-dir="-1" aria-label="上一期">${icon('back')}</button><button data-action="budget-range">${budgetMode==='month'?budgetRange.replace('-','.'):budgetRange.slice(0,4)}</button><button data-action="shift" data-dir="1" aria-label="下一期">${icon('arrow')}</button></div><div class="budget-grid">${budgetCard('合计预算',budgetMode,budgetRange)}<section class="card category-card"><h2 class="card-title">分类预算<button data-action="add-category-budget" aria-label="新增分类预算">${icon('plus')}</button></h2><p class="category-sub">合计：${fmt(Object.values(b.categories).reduce((a,v)=>a+v,0))}</p>${names.length?names.map(id=>{const matches=totals.get(id)||{cents:0,count:0},used=matches.cents,total=b.categories[id];return `<button class="budget-row" data-action="set-category-budget" data-cat="${id}"><div class="budget-cat"><div class="cat-icon">${icon(id)}</div><div class="entry-copy"><b>${esc(category(id).name)}</b><small>额度：${fmt(total)}</small></div></div><div class="budget-info"><div class="progress"><i style="width:${percentage(used,total)}%"></i></div><div class="metrics"><span>用量：${fmt(used)}（共${matches.count}笔）</span><span>余量：${fmt(total-used)}</span></div></div></button>`;}).join(''):'<div class="empty">暂未设置分类预算</div>'}</section><section class="card trend-card"><h2 class="card-title">预算趋势</h2>${b.total?`<div class="legend"><span><i style="background:#37745c"></i>余量</span><span><i style="background:#b34c4c"></i>用量</span></div>${trendSVG(es,b.total)}`:'<div class="empty">暂未设置合计预算</div>'}</section></div>`;}
function trendSVG(es,total){const daily=new Map();for(const e of es)if(e.type==='expense')daily.set(e.date,(daily.get(e.date)||0)+e.cents);const y=Number(budgetRange.slice(0,4)),m=Number(budgetRange.slice(5,7))-1,start=new Date(y,budgetMode==='month'?m:0,1),end=budgetMode==='month'?new Date(y,m+1,0):new Date(y,11,31),n=Math.round((end-start)/86400000)+1,t=new Date(today()+'T12:00:00'),last=Math.min(n-1,Math.floor((t-new Date(dateISO(start)+'T12:00:00'))/86400000));let acc=0,points=[];for(let i=0;i<=last;i++){const d=new Date(start);d.setDate(d.getDate()+i);const date=dateISO(d);acc+=daily.get(date)||0;points.push({i,used:acc,left:total-acc,date});}const max=Math.max(total,acc,100),min=Math.min(0,total-acc),height=130,base=15+(max/(max-min))*height;const px=i=>36+i/Math.max(1,n-1)*340,py=v=>15+(max-v)/(max-min)*height;const line=field=>points.map((p,i)=>`${i?'L':'M'}${px(p.i).toFixed(2)},${py(p[field]).toFixed(2)}`).join(' '),area=field=>points.length?`${line(field)} L${px(points.at(-1).i)},${base} L36,${base}Z`:'';return `<svg class="chart" viewBox="0 0 390 175" role="img" aria-label="预算用量和余量每日累计趋势">${[0,.5,1].map(f=>`<line class="grid" x1="36" y1="${15+f*height}" x2="376" y2="${15+f*height}"/><text x="0" y="${19+f*height}">${((max-(max-min)*f)/100).toFixed(2)}</text>`).join('')}<path d="${area('left')}" fill="#37745c" fill-opacity=".5" stroke="none"/><path d="${area('used')}" fill="#b34c4c" fill-opacity=".5" stroke="none"/><path d="${line('left')}" fill="none" stroke="#37745c" stroke-width="1.8"/><path d="${line('used')}" fill="none" stroke="#b34c4c" stroke-width="1.8"/>${budgetMode==='month'?points.map(p=>['left','used'].map(field=>`<circle cx="${px(p.i)}" cy="${py(p[field])}" r="2.4" fill="${field==='left'?'#37745c':'#b34c4c'}" stroke="#fff" stroke-width="1"><title>${p.date} ${field==='left'?'余量':'用量'}：${fmt(p[field])}</title></circle>`).join('')).join(''):''}<text x="36" y="168">${budgetMode==='month'?'01':'01.01'}</text><text x="195" y="168">${budgetMode==='month'?'16':'07.01'}</text><text x="352" y="168">${budgetMode==='month'?n:'12.31'}</text></svg>`;}
function settingsHTML(){return `<div class="card profile"><div class="avatar">${icon('user')}</div><div><b>本地使用者</b><small>尚未接入账号服务 · 离线即可记账</small></div></div><div class="card settings-card"><div class="setting-row"><div>沉浸光感<small>调节通透材质、模糊与高光</small></div><select id="effect" aria-label="沉浸光感强度"><option value="strong" ${db.settings.effect==='strong'?'selected':''}>强</option><option value="balanced" ${db.settings.effect==='balanced'?'selected':''}>均衡</option><option value="weak" ${db.settings.effect==='weak'?'selected':''}>弱</option></select></div><div class="setting-row"><div>主页预算<small>选择预算卡片的展示周期</small></div><select id="homeBudget" aria-label="主页预算周期"><option value="month" ${db.settings.budgetMode==='month'?'selected':''}>月度预算</option><option value="year" ${db.settings.budgetMode==='year'?'selected':''}>年度预算</option></select></div><div class="setting-row"><div>平板底栏位置<small>记一笔随位置调整；手机始终居中</small></div><select id="tabletDockPosition" aria-label="平板底栏位置"><option value="left" ${db.settings.tabletDockPosition==='left'?'selected':''}>左下</option><option value="center" ${db.settings.tabletDockPosition==='center'?'selected':''}>居中</option><option value="right" ${db.settings.tabletDockPosition==='right'?'selected':''}>右下</option></select></div><button class="setting-row" style="width:100%;text-align:left" data-action="trash"><span>回收站<small>手动恢复或永久删除，不自动清理</small></span>${icon('arrow')}</button></div><div class="card settings-card"><button class="setting-row" style="width:100%;text-align:left" data-action="export"><span>导出完整备份<small>包含账本、账单、分类、预算和回收站</small></span>${icon('download')}</button><button class="setting-row" style="width:100%;text-align:left" data-action="import"><span>导入备份<small>检查格式后确认替换当前数据</small></span>${icon('arrow')}</button>${db.demo?'<button class="setting-row" style="width:100%;text-align:left" data-action="clear-demo"><span>结束演示，创建空白账本<small>会清空当前演示数据，需确认</small></span></button>':''}</div><p class="note" style="text-align:center">墨账 0.1.20-webview · 所有金额按“分”保存<br>沉浸光感为 Android 近似实现，非鸿蒙系统组件</p>`;}
function trashHTML(){const es=db.trash.filter(e=>e.book===db.book).sort((a,b)=>b.deletedAt-a.deletedAt);return `<div class="card category-card"><h2 class="card-title">已删除 · ${es.length} 笔</h2><p class="budget-note">暂不自动清理。永久删除操作无法撤销。</p>${es.map(e=>`<div class="recycled"><div class="cat-icon">${icon(e.cat)}</div><div class="entry-copy"><b>${esc(category(e.cat).name)} · ${fmt(e.cents)}</b><small>${e.date} ${esc(e.note)}</small></div><button data-action="restore" data-id="${e.id}">恢复</button><button data-action="purge" data-id="${e.id}" aria-label="永久删除">${icon('trash')}</button></div>`).join('')||'<div class="empty">回收站是空的</div>'}</div>`;}
function statsHTML(){const es=selectedEntries(),expense=sum(es,'expense'),income=sum(es,'income'),totals=expenseByCategory(es),ids=[...totals.keys()].sort((a,b)=>totals.get(b).cents-totals.get(a).cents);return `<section class="hero"><div class="eyebrow">收支结余</div><div class="total"><small>¥</small>${fmt(income-expense)}</div><div class="hero-foot"><span>收入<strong>${fmt(income)}</strong></span><span>支出<strong>${fmt(expense)}</strong></span></div></section><section class="card category-card"><h2 class="card-title">支出构成</h2>${ids.map(id=>{const used=totals.get(id).cents;return `<div class="stat-row"><div class="cat-icon">${icon(id)}</div><b>${esc(category(id).name)}</b><div class="progress"><i style="width:${percentage(used,expense)}%"></i></div><span>${fmt(used)}</span></div>`;}).join('')||'<p class="empty">暂无支出记录</p>'}</section>`;}
function categoriesHTML(){return `<section class="card category-card"><h2 class="card-title">记账分类<button data-action="new-category" aria-label="新增分类">${icon('plus')}</button></h2><p class="budget-note">分类名称最多 10 字符宽度，汉字计 2。</p><div class="categories">${db.categories.map(c=>`<button data-action="rename-category" data-cat="${c.id}"><span class="cat-icon">${icon(c.id)}</span>${esc(c.name)}<small style="color:#999">${c.type==='income'?'收入':'支出'}</small></button>`).join('')}</div></section>`;}
function searchHTML(){const es=selectedEntries().filter(e=>`${category(e.cat).name} ${e.note} ${inputMoney(e.cents)}`.toLowerCase().includes(search.toLowerCase()));return `<input class="search-input" id="searchInput" value="${esc(search)}" placeholder="搜索分类、备注或金额" aria-label="搜索账单"><div id="searchResults">${daysHTML(es)}</div>`;}
const navigationOrder=['home','budget','stats','search','categories','trash','settings'];
let navigationMotion=null,lastFunctionState=null,selectionMotionTiming=null,settingsSubpage=false;
let sidebarResizeTimer=0;
function clearSidebarResize(){clearTimeout(sidebarResizeTimer);document.body.classList.remove('sidebar-resizing');}
function toggleTabletSidebar(){
 document.body.classList.add('sidebar-resizing');
 // Commit the initial geometry before changing both transition targets together.
 void $('#displayArea').offsetWidth;
 document.body.classList.toggle('sidebar-hidden');
 clearTimeout(sidebarResizeTimer);sidebarResizeTimer=setTimeout(clearSidebarResize,350);
}
function isSettingsPage(){return page==='settings'||(page==='trash'&&settingsSubpage);}
let sheetOrigin=null,sheetClosing=false,sheetMotion=null,contextEntryId=null,selectionActive=false,selectedIds=new Set();


let lastMotionBoost=-Infinity;
function boostMotionRate(){const now=performance.now();if(now-lastMotionBoost<250)return;lastMotionBoost=now;window.AndroidStore?.requestMotionRate?.();}
let layerWarmupTimer=0;
function prepareHorizontalMotion(event){
 const button=event.target.closest('#dock [data-action=settings],#dock [data-action=functions],#sidebar [data-action=nav],.budget-card,[data-action=add]');if(!button)return;if(['settings','functions'].includes(button.dataset.action)&&(button.dataset.action==='settings')===isSettingsPage())return;if(button.dataset.action==='nav'&&button.dataset.page===page)return;
 const surface=$('#pageSurface');if(surface.motionOriginalWillChange===undefined)surface.motionOriginalWillChange=surface.style.willChange;
 surface.style.willChange=button.matches('.budget-card[data-action=budget]')?'transform, opacity':'transform, opacity, filter';clearTimeout(layerWarmupTimer);
 layerWarmupTimer=setTimeout(()=>{if(!modalOpen&&!surface.motionLayerToken&&surface.motionOriginalWillChange!==undefined){surface.style.willChange=surface.motionOriginalWillChange;delete surface.motionOriginalWillChange;}},1200);
}
document.addEventListener('pointerdown',prepareHorizontalMotion,{passive:true});
// Animate the actual Gaussian radius, preserving continuous blur gradients.
function animateBlurred(element,frames,options){
 const oldWillChange=element.motionOriginalWillChange??element.style.willChange,token={};
 element.motionOriginalWillChange=oldWillChange;element.motionLayerToken=token;
 element.style.willChange='transform, opacity, filter';
 const animation=element.animate(frames,options);
 animation.finished.then(cleanup,cleanup);
 function cleanup(){if(element.motionLayerToken===token){element.style.willChange=oldWillChange;delete element.motionOriginalWillChange;delete element.motionLayerToken;}}
 return animation;
}
function motionAnimations(primary){return primary;}
function createScrollViewport(){
 const scroll=document.createElement('div');scroll.id='scroll';scroll.className='page-scroll';
 const content=document.createElement('div');content.id='content';content.className='page-content';scroll.appendChild(content);return scroll;
}
function takePageSnapshot(full){
 let snapshot;
 if(full){
  snapshot=$('#pageSurface');const rect=snapshot.getBoundingClientRect(),background=getComputedStyle($('#main')).backgroundImage;
  snapshot.removeAttribute('id');snapshot.className='surface-snapshot';Object.assign(snapshot.style,{width:rect.width+'px',height:rect.height+'px',background});
  snapshot.querySelectorAll('[id]').forEach(el=>el.removeAttribute('id'));
  const surface=document.createElement('div');surface.id='pageSurface';const header=document.createElement('header');header.id='toolbar';header.className='page-toolbar';surface.append(header,createScrollViewport());snapshot.after(surface);
 }else{
  snapshot=document.createElement('div');snapshot.className='page-outgoing';const content=document.createElement('div');content.className='page-content';content.dataset.page=page;content.style.paddingTop=getComputedStyle($('#content')).paddingTop;const range=document.createRange();range.selectNodeContents($('#content'));content.appendChild(range.extractContents());snapshot.appendChild(content);snapshot.querySelectorAll('[id]').forEach(el=>el.removeAttribute('id'));
 }
 snapshot.inert=true;snapshot.setAttribute('aria-hidden','true');return snapshot;
}

// Preserve the actual displayed state, including a press/rebound still in flight.
// The backdrop may darken and blur, but its layout and text must remain stationary.
function freezeBackdrop(surface){
 const animations=surface.getAnimations({subtree:true});
 const states=animations.map(animation=>{const target=animation.effect?.target;if(!target)return null;const style=getComputedStyle(target),keys=new Set(animation.effect.getKeyframes().flatMap(frame=>Object.keys(frame)));return {animation,target,values:[...keys].filter(key=>key in target.style).map(key=>[key,style[key]])};});
 const card=surface.querySelector('[data-action="budget"]');
 const press=card?{transform:getComputedStyle(card).transform,filter:getComputedStyle(card).filter}:null;
 states.filter(Boolean).forEach(({animation,target,values})=>{values.forEach(([key,value])=>target.style[key]=value);animation.cancel();});
 if(card)Object.assign(card.style,press);
 surface.classList.add('frozen-backdrop');
}

function highlightPosition(root){const el=root.querySelector('.moving-highlight');if(!el)return null;const r=el.getBoundingClientRect(),parent=root.getBoundingClientRect();return {x:r.left-parent.left-root.clientLeft,y:r.top-parent.top-root.clientTop+root.scrollTop,width:r.width,height:r.height};}
function moveHighlight(root,selector,previous,geometry=null){
 if(highlightBatch){highlightBatch.moves.push({root,selector,previous});return;}
 const target=root.querySelector(selector);if(!target){root.querySelector('.moving-highlight')?.remove();return;}
 const el=root.querySelector('.moving-highlight')||document.createElement('div');el.getAnimations().forEach(a=>a.cancel());el.className='moving-highlight';el.setAttribute('aria-hidden','true');
 const {x,y,w,h}=geometry||{x:target.offsetLeft,y:target.offsetTop,w:target.offsetWidth,h:target.offsetHeight};
 Object.assign(el.style,{left:x+'px',top:y+'px',width:w+'px',height:h+'px',transform:''});root.prepend(el);
 if(previous&&(Math.abs(previous.x-x)>.1||Math.abs(previous.y-y)>.1||Math.abs(previous.width-w)>.1||Math.abs(previous.height-h)>.1)&&!matchMedia('(prefers-reduced-motion: reduce)').matches){
  const timing=selectionMotionTiming||{duration:360,easing:MOTION_EASING},dx=previous.x-x,dy=previous.y-y;
  const frames=[{transform:`translate(${dx}px,${dy}px) scale(${previous.width/w},${previous.height/h})`}];
  frames.push({transform:'translate(0,0) scale(1,1)'});el.animate(frames,{duration:timing.duration,easing:timing.easing});
 }
}
function selectionAnimations(){return ['#sidebar .moving-highlight','#dock .moving-highlight'].flatMap(selector=>$(selector)?.getAnimations()||[]);}
function sidebarSettingsMotion(enter,oldRect,options,prepared=null){
 const sidebar=$('#sidebar'),rect=sidebar.getBoundingClientRect();
 if(enter&&oldRect.left>=-1&&oldRect.width>0){
  const copy=prepared||sidebar.cloneNode(true);copy.removeAttribute('id');copy.className='sidebar-motion';copy.inert=true;copy.setAttribute('aria-hidden','true');copy.querySelectorAll('[id]').forEach(el=>el.removeAttribute('id'));
  const css=getComputedStyle(sidebar);Object.assign(copy.style,{left:oldRect.left+'px',width:oldRect.width+'px',height:oldRect.height+'px',padding:css.padding,background:css.background,borderRight:css.borderRight});$('#app').appendChild(copy);
  const animation=copy.animate([{transform:'translateX(0)'},{transform:`translateX(${-oldRect.width}px)`}],{...options,fill:'both'});return {animation,copy};
 }
 if(!enter&&matchMedia('(min-width:840px)').matches)return {animation:sidebar.animate([{transform:`translateX(${-rect.width}px)`},{transform:'translateX(0)'}],{...options,fill:'both'})};
 return null;
}
function switchSettings(enter,requested=null){
 boostMotionRate();
 const oldDock=$('#dock').getBoundingClientRect();
 closeSheet();closeContextMenu();exitSelection(false);cancelPageTransition();
 const from=page,oldRect=$('#displayArea').getBoundingClientRect(),oldSidebar=$('#sidebar').getBoundingClientRect(),top=$('#scroll').scrollTop,wasSettings=isSettingsPage();
 clearSidebarResize();
 const sidebarSnapshot=enter&&!wasSettings&&oldSidebar.left>=-1?$('#sidebar').cloneNode(true):null;
 const ghost=takePageSnapshot(true);ghost.classList.add('settings-snapshot');
 if(enter){if(!wasSettings)lastFunctionState={page,top,budgetMode,budgetRange};page=requested||'settings';settingsSubpage=page==='trash';}
 else {settingsSubpage=false;page=requested||lastFunctionState?.page||'home';if(page===lastFunctionState?.page){budgetMode=lastFunctionState.budgetMode;budgetRange=lastFunctionState.budgetRange;}}
 document.body.classList.remove('drawer-open','sidebar-hidden');render(false,{duration:480,easing:MOTION_EASING});
 if(!enter&&page===lastFunctionState?.page){$('#scroll').scrollTop=lastFunctionState.top;updateFade();}
 if(matchMedia('(prefers-reduced-motion: reduce)').matches){ghost.remove();return;}
 const main=$('#displayArea').getBoundingClientRect();Object.assign(ghost.style,{left:(oldRect.left-main.left)+'px',right:'auto',width:oldRect.width+'px',zIndex:'2'});$('#displayArea').appendChild(ghost);ghost.querySelector('.page-scroll').scrollTop=top;
 const direction=enter?1:-1,distance=Math.max(180,main.width*.38),duration=480,options={duration,easing:MOTION_EASING};
 const outgoing=animateBlurred(ghost,[{transform:'translateX(0)',opacity:1,filter:'blur(0px)'},{transform:`translateX(${-direction*distance}px)`,opacity:0,filter:'blur(12px)'}],options);
 const incoming=animateBlurred($('#pageSurface'),[{transform:`translateX(${direction*distance}px)`,opacity:0,filter:'blur(12px)'},{transform:'translateX(0)',opacity:1,filter:'blur(0px)'}],options);
 const sidebarMotion=wasSettings!==isSettingsPage()?sidebarSettingsMotion(enter,oldSidebar,options,sidebarSnapshot):null;
 const dock=$('#dock'),dockRect=dock.getBoundingClientRect(),baseTransform=getComputedStyle(dock).transform;
 const dx=oldDock.left-dockRect.left,dy=oldDock.top-dockRect.top;
 const dockMotion=Math.abs(dx)+Math.abs(dy)>.1?dock.animate([{transform:`translate(${dx}px,${dy}px) ${baseTransform==='none'?'':baseTransform}`},{transform:baseTransform}],options):null;
 const motion={from,to:page,type:'settings',direction,distance,duration,ghost,sidebarMotion,dockMotion,animations:[outgoing,incoming,...(dockMotion?[dockMotion]:[]),...selectionAnimations(),...(sidebarMotion?[sidebarMotion.animation]:[])]};navigationMotion=motion;
 Promise.allSettled(motion.animations.map(a=>a.finished)).then(()=>{if(navigationMotion===motion){ghost.remove();sidebarMotion?.copy?.remove();sidebarMotion?.animation.cancel();navigationMotion=null;}});
}

function cancelPageTransition(){
 if(!navigationMotion)return;
 const motion=navigationMotion;navigationMotion=null;
 motion.animations.forEach(a=>{const target=a.effect?.target;if(target?.classList.contains('moving-highlight')){const r=highlightPosition(target.parentElement);a.cancel();if(r)Object.assign(target.style,{left:r.x+'px',top:r.y+'px',width:r.width+'px',height:r.height+'px',transform:''});}else a.cancel();});motion.ghost.remove();motion.sidebarMotion?.copy?.remove();
}
function go(p,origin=null){
 boostMotionRate();
 if(p==='trash'){if(page==='trash'){render(true);return;}switchSettings(true,'trash');return;}
 if(p==='settings'&&page!=='settings'){switchSettings(true);return;}
 if(isSettingsPage()&&p!=='settings'){switchSettings(false,p);return;}
 if(!navigationOrder.includes(p))return;
 closeSheet();closeContextMenu();exitSelection(false);document.body.classList.remove('drawer-open');
 if(p===page){render(true);return;}
 cancelPageTransition();
 let sc=$('#scroll');const from=page,oldTop=sc.scrollTop,delta=navigationOrder.indexOf(p)-navigationOrder.indexOf(page),direction=Math.sign(delta),steps=Math.abs(delta),distance=104*steps,duration=Math.max(260,430-30*(steps-1));
 const surfaceSnapshot=origin?takePageSnapshot(true):null,ghost=origin?null:takePageSnapshot(false);
 page=p;
 if(p==='budget'){budgetMode=db.settings.budgetMode;budgetRange=budgetMode==='month'?(rangeMode==='month'?range:range.slice(0,4)+'-'+today().slice(5,7)):range.slice(0,4);}
 render(false,origin?{duration:480,easing:MOTION_EASING}:{duration,easing:MOTION_EASING});
 sc=$('#scroll');if(matchMedia('(prefers-reduced-motion: reduce)').matches||!sc.animate){(surfaceSnapshot||ghost)?.remove();return;}
 if(origin){
  $('#displayArea').appendChild(surfaceSnapshot);surfaceSnapshot.querySelector('.page-scroll').scrollTop=oldTop;
  const main=$('#displayArea').getBoundingClientRect(),left=Math.max(0,origin.left-main.left),top=Math.max(0,origin.top-main.top),right=Math.max(0,main.right-origin.right),bottom=Math.max(0,main.bottom-origin.bottom);
  const reveal=$('#pageSurface').animate([{clipPath:`inset(${top}px ${right}px ${bottom}px ${left}px round 18px)`},{clipPath:'inset(0px 0px 0px 0px round 0px)'}],{duration:480,easing:BUDGET_EXPAND_EASING});
  const shade=document.createElement('div');shade.className='budget-expand-shade';surfaceSnapshot.appendChild(shade);
  freezeBackdrop(surfaceSnapshot);
  const backdropOptions={duration:480,easing:'cubic-bezier(.33,.66,.66,1)',fill:'forwards'};
  const fade=shade.animate([{opacity:0},{opacity:.40}],backdropOptions);
  const backgroundBlur=animateBlurred(surfaceSnapshot,[{filter:'blur(0px)'},{filter:'blur(24px)'}],backdropOptions);
  const motion={from,to:p,type:'expand-budget',duration:480,origin,ghost:surfaceSnapshot,animations:motionAnimations([reveal,fade,backgroundBlur])};navigationMotion=motion;
  Promise.allSettled(motion.animations.map(a=>a.finished)).then(()=>{if(navigationMotion===motion){surfaceSnapshot.remove();navigationMotion=null;}});
  return;
 }
 $('#pageSurface').appendChild(ghost);ghost.scrollTop=oldTop;
 const options={duration,easing:MOTION_EASING,fill:'none'};
 const outgoing=animateBlurred(ghost,[
  {transform:'translateY(0)',opacity:1,filter:'blur(0px)'},
  {transform:`translateY(${-direction*distance}px)`,opacity:0,filter:'blur(10px)'}
 ],options);
 const incoming=animateBlurred(sc,[
  {transform:`translateY(${direction*distance}px)`,opacity:0,filter:'blur(10px)'},
  {transform:'translateY(0)',opacity:1,filter:'blur(0px)'}
 ],options);
 const motion={from,to:p,type:'navigate',direction,steps,distance,duration,ghost,animations:[outgoing,incoming,...selectionAnimations()]};
 navigationMotion=motion;
 Promise.allSettled(motion.animations.map(a=>a.finished)).then(()=>{
  if(navigationMotion===motion){motion.ghost.remove();navigationMotion=null;}
 });
}
let sheetBackdropSurface=null;
function holdSheetBackdrop(){
 const surface=$('#pageSurface');if(surface.motionLayerToken)return;
 if(surface.motionOriginalWillChange===undefined)surface.motionOriginalWillChange=surface.style.willChange;
 surface.style.willChange='transform';sheetBackdropSurface=surface;
}
function releaseSheetBackdrop(){
 const surface=sheetBackdropSurface;sheetBackdropSurface=null;
 if(surface&&!surface.motionLayerToken){surface.style.willChange=surface.motionOriginalWillChange||'';delete surface.motionOriginalWillChange;}
}
function sheet(title,body,cls=''){
 boostMotionRate();
 closeContextMenu();closeSheet();modalOpen=true;holdSheetBackdrop();
 $('#modalRoot').innerHTML=`<button class="sheet-mask" data-action="close" aria-label="关闭弹窗"></button><section class="sheet ${cls}" role="dialog" aria-modal="true" aria-label="${esc(title)}"><div class="handle"></div><div class="detail-inner"><div class="sheet-heading"><h2>${title}</h2><button data-action="close" aria-label="关闭">${icon('close')}</button></div>${body}</div></section>`;
 $('#scroll').inert=true;$('#toolbar').inert=true;$('#dock').inert=true;
 const dialog=$('#modalRoot .sheet');
 const focus=()=>{if(document.contains(dialog)&&!sheetClosing)dialog.querySelector('input,button:not([data-action=close])')?.focus({preventScroll:true});};
 // Let the final expansion frame be presented before IME resize begins.
 const afterPresentation=()=>requestAnimationFrame(()=>requestAnimationFrame(focus));
 setTimeout(()=>{if(sheetMotion){const motion=sheetMotion;Promise.all([motion.animation.finished,motion.maskAnimation.finished]).then(afterPresentation,()=>{});}else afterPresentation();},460);
}
function removeSheet(){
 if(sheetMotion){sheetMotion.animation.cancel();sheetMotion.maskAnimation.cancel();sheetMotion.extra?.forEach(a=>a.cancel());sheetMotion=null;}
 releaseSheetBackdrop();sheetOrigin=null;sheetClosing=false;modalOpen=false;$('#modalRoot').innerHTML='';$('#scroll').inert=false;$('#toolbar').inert=false;$('#dock').inert=false;
}
function closeSheet(animate=false){
 const panel=$('#modalRoot .sheet');if(!animate||!panel||matchMedia('(prefers-reduced-motion: reduce)').matches){removeSheet();return;}if(sheetClosing)return;
 sheetClosing=true;const mask=$('.sheet-mask');panel.inert=true;
 const ongoing=sheetMotion;let animations=[];
 if(ongoing&&ongoing.animation.playState!=='finished'){
  // Reversing an ease-out animation produces ease-in. Restart from the
  // displayed values instead, so interrupted dismissal also decelerates.
  const originals=[ongoing.animation,ongoing.maskAnimation,...(ongoing.extra||[])];
  const progress=ongoing.animation.effect.getComputedTiming().progress||0;
  const states=originals.map(a=>{const target=a.effect.target,css=getComputedStyle(target),end=a.effect.getKeyframes()[0],start={};for(const prop of ['transform','transformOrigin','opacity','backgroundColor','borderRadius'])if(prop in end)start[prop]=css[prop];return {target,start,end:Object.fromEntries(Object.entries(end).filter(([key])=>!['offset','computedOffset','easing','composite'].includes(key)))};});
  originals.forEach(a=>a.cancel());
  animations=states.map(({target,start,end})=>{const handoff=('opacity' in end&&target.matches('.plus-morph,.handle,.detail-inner'))||'backgroundColor' in end;return target.animate(handoff?editorHandoffFrames(start,end):[start,end],{duration:Math.max(120,320*progress),easing:handoff?'linear':MOTION_EASING,fill:'forwards'});});
 }else if(sheetOrigin){
  const rect=panel.getBoundingClientRect(),origin=$('#dock .add')?.getBoundingClientRect()||sheetOrigin,shape=editorShape(rect,origin);
  const glyph=plusGlyphMotion(rect,origin,true,{duration:320,easing:MOTION_EASING,fill:'forwards'});
  const panelMotion=animateEditorPanel(panel,[shape[1],shape[0]],{duration:320,easing:MOTION_EASING,fill:'forwards'});
  animations=[panelMotion.animation,mask.animate([{opacity:1},{opacity:0}],{duration:320,easing:MOTION_EASING,fill:'forwards'}),glyph.animation,...glyph.contentAnimations,...panelMotion.appearance];
 }else{
  const current=getComputedStyle(panel).transform,maskOpacity=getComputedStyle(mask).opacity;panel.classList.add('origin-expand');animations=[panel.animate([{transform:current},{transform:'translateY(100%)'}],{duration:280,easing:MOTION_EASING,fill:'forwards'}),mask.animate([{opacity:maskOpacity},{opacity:0}],{duration:280,easing:MOTION_EASING,fill:'forwards'})];
 }
 const motion={animation:animations[0],maskAnimation:animations[1],extra:animations.slice(2),closing:true};sheetMotion=motion;
 Promise.allSettled(animations.map(a=>a.finished)).then(()=>{if(sheetMotion===motion)removeSheet();});
}
// Keep one physical panel and the exact original geometry. Unsupported
// paint properties must not force its transform animation onto the main thread.
// Keep icon/page crossfade in a 20%-50% interval of elapsed animation time.
// Geometry retains the original deceleration curve independently.
function editorHandoffFrames(start,end){return [{...start,offset:0},{...start,offset:.20},{...end,offset:.50},{...end,offset:1}];}
function animateEditorPanel(panel,frames,options){
 const pick=keys=>frames.map(frame=>Object.fromEntries(Object.entries(frame).filter(([key])=>keys.includes(key)||['offset','easing','composite'].includes(key))));
 const animation=panel.animate(pick(['transform','transformOrigin']),options);
 const colors=pick(['backgroundColor']);
 const appearance=[panel.animate(editorHandoffFrames(colors[0],colors.at(-1)),{...options,easing:'linear'}),panel.animate(pick(['borderRadius']),options)];
 return {animation,appearance};
}
function editorShape(rect,origin){return [{backgroundColor:'#888',transformOrigin:'0 0',transform:`translate(${origin.left-rect.left}px,${origin.top-rect.top}px) scale(${origin.width/rect.width},${origin.height/rect.height})`,borderRadius:`${rect.width/2}px / ${rect.height/2}px`},{backgroundColor:'#f7f7f7',transformOrigin:'0 0',transform:'translate(0px,0px) scale(1,1)',borderRadius:'28px 28px 0px 0px'}];}
function plusGlyphMotion(rect,origin,closing,options){
 const glyph=document.createElement('div');glyph.className='plus-morph';glyph.style.opacity='0';glyph.innerHTML=icon('plus');glyph.setAttribute('aria-hidden','true');const main=$('#displayArea').getBoundingClientRect();
 Object.assign(glyph.style,{left:(origin.left-main.left)+'px',top:(origin.top-main.top)+'px',width:origin.width+'px',height:origin.height+'px'});$('#modalRoot').appendChild(glyph);
 const dx=rect.left+rect.width/2-origin.left-origin.width/2,dy=rect.top+rect.height/2-origin.top-origin.height/2;
 const frames=closing?[{transform:`translate(${dx}px,${dy}px) scale(2)`},{transform:'translate(0,0) scale(1)'}]:[{transform:'translate(0,0) scale(1)'},{transform:`translate(${dx}px,${dy}px) scale(2)`}];
 const handoffOptions={...options,easing:'linear'};
 const glyphFade=glyph.animate(editorHandoffFrames({opacity:closing?0:1},{opacity:closing?1:0}),handoffOptions);
 const contentFrames=editorHandoffFrames({opacity:closing?1:0},{opacity:closing?0:1});
 const contentAnimations=[glyphFade,...Array.from($('#modalRoot .sheet').querySelectorAll('.handle,.detail-inner')).map(el=>el.animate(contentFrames,handoffOptions))];
 return {glyph,animation:glyph.animate(frames,options),contentAnimations};
}
function expandEditor(origin){
 const panel=$('#modalRoot .sheet');panel.classList.add('origin-expand');sheetOrigin=origin;
 if(matchMedia('(prefers-reduced-motion: reduce)').matches||!panel.animate)return;
 const rect=panel.getBoundingClientRect(),options={duration:460,easing:MOTION_EASING};
 // Preserve the original whole-panel geometry, content scaling and curve.
 // Predeclare the transform layer so text is not rasterized for every scale.
 const panelMotion=animateEditorPanel(panel,editorShape(rect,origin),options),animation=panelMotion.animation;
 const maskAnimation=$('.sheet-mask').animate([{opacity:0},{opacity:1}],{...options,easing:'cubic-bezier(.33,.66,.66,1)'});
 const glyph=plusGlyphMotion(rect,origin,false,options);
 const motion={animation,maskAnimation,extra:[glyph.animation,...glyph.contentAnimations,...panelMotion.appearance],origin,duration:460};sheetMotion=motion;
 Promise.allSettled([animation.finished,maskAnimation.finished,...motion.extra.map(a=>a.finished)]).then(()=>{if(sheetMotion===motion&&!sheetClosing){sheetMotion=null;glyph.glyph.remove();}});
}
function closeContextMenu(){contextEntryId=null;$('#contextRoot').innerHTML='';}
function openEntryMenu(id,x,y){
 if(!db.entries.some(e=>e.id===id))return;
 closeContextMenu();contextEntryId=id;
 const main=$('#displayArea').getBoundingClientRect();
 const left=Math.max(8,Math.min(main.width-160,x-main.left)),top=Math.max(8,Math.min(main.height-148,y-main.top));
 $('#contextRoot').innerHTML=`<button class="context-backdrop" data-action="context-close" aria-label="关闭账单菜单"></button><div class="entry-menu" role="menu" aria-label="账单操作" style="left:${left}px;top:${top}px"><button role="menuitem" data-action="context-edit" data-id="${id}">编辑</button><button role="menuitem" data-action="context-delete" data-id="${id}">删除</button><button role="menuitem" data-action="context-select" data-id="${id}">多选</button></div>`;
 $('.entry-menu button').focus({preventScroll:true});
}
function visibleEntries(){return selectedEntries().filter(e=>page!=='search'||`${category(e.cat).name} ${e.note} ${inputMoney(e.cents)}`.toLowerCase().includes(search.toLowerCase()));}
function exitSelection(refresh=true){selectionActive=false;selectedIds.clear();if(refresh)render(true);}
function startSelection(id){closeContextMenu();selectionActive=true;selectedIds=new Set([id]);render(true);}
function toggleSelection(id){if(selectedIds.has(id))selectedIds.delete(id);else selectedIds.add(id);render(true);}
function renderSelectionDock(){
 $('#dock').classList.add('selection-dock');$('#dock').dataset.position=matchMedia('(min-width:840px)').matches?db.settings.tabletDockPosition:'center';
 const all=visibleEntries().length>0&&visibleEntries().every(e=>selectedIds.has(e.id));
 $('#dock').innerHTML=`<span class="selection-count">已选<br>${selectedIds.size} 笔</span><button class="batch-btn" data-action="select-all">${icon('other')}<span>${all?'全不选':'全选'}</span></button><button class="batch-btn" data-action="batch-category">${icon('book')}<span>分类</span></button><button class="batch-btn" data-action="batch-delete">${icon('trash')}<span>删除</span></button><button class="batch-btn" data-action="selection-exit">${icon('close')}<span>取消</span></button>`;
}
function batchCategory(){
 const entries=db.entries.filter(e=>selectedIds.has(e.id));
 if(!entries.length){toast('请先选择账单');return;}
 if(new Set(entries.map(e=>e.type)).size>1){toast('请分别选择收入或支出账单');return;}
 sheet('批量修改分类',`<div class="categories">${db.categories.filter(c=>c.type===entries[0].type).map(c=>`<button data-action="apply-batch-category" data-cat="${c.id}"><span class="cat-icon">${icon(c.id)}</span>${esc(c.name)}</button>`).join('')}</div>`);
}
function confirmSheet(title,message,action){sheet(title,`<p class="intro-copy">${message}</p><div class="button-row"><button class="secondary" data-action="close">取消</button><button class="primary" id="confirmAction">确认</button></div>`);$('#confirmAction').onclick=action;}
function rangeSheet(isBudget=false){const mode=isBudget?budgetMode:rangeMode,r=isBudget?budgetRange:range;sheet('选择时间范围',`<form class="form-grid" id="rangeForm">${!isBudget?`<label>查看范围<select id="rangeMode"><option value="month" ${mode==='month'?'selected':''}>月度</option><option value="year" ${mode==='year'?'selected':''}>年度</option></select></label>`:''}<label>年月<input id="rangeValue" type="${mode==='month'?'month':'number'}" value="${r.slice(0,mode==='month'?7:4)}" min="${mode==='month'?'1900-01':'1900'}" max="${mode==='month'?'2100-12':'2100'}" required></label><button class="primary">查看</button></form>`);$('#rangeMode')?.addEventListener('change',e=>{const field=$('#rangeValue');field.type=e.target.value==='month'?'month':'number';field.min=e.target.value==='month'?'1900-01':'1900';field.max=e.target.value==='month'?'2100-12':'2100';field.value=e.target.value==='month'?r.slice(0,4)+'-'+today().slice(5,7):r.slice(0,4);});$('#rangeForm').onsubmit=e=>{e.preventDefault();const nextMode=isBudget?mode:$('#rangeMode').value,v=$('#rangeValue').value;if(nextMode==='month'?!/^\d{4}-(0[1-9]|1[0-2])$/.test(v):!/^(19\d\d|20\d\d|2100)$/.test(v)){toast('请选择有效时间范围');return;}if(isBudget){budgetMode=nextMode;budgetRange=v;}else{rangeMode=nextMode;range=v;}closeSheet();render();};}
function editor(date=today(),entry=null,origin=null){let type=entry?.type||'expense',cat=entry?.cat||'food';sheet(entry?'编辑账单':'记一笔',`<form id="entryForm" class="form-grid"><div class="type-select"><button type="button" id="expenseType" class="${type==='expense'?'selected':''}">支出</button><button type="button" id="incomeType" class="${type==='income'?'selected':''}">收入</button></div><label>金额（¥）<input class="money-input" id="entryMoney" inputmode="decimal" placeholder="0.00" value="${entry?inputMoney(entry.cents):''}" required maxlength="13"></label><label>分类<select id="entryCategory"></select></label><label>备注<input id="entryNote" value="${esc(entry?.note||'')}" placeholder="填写备注" maxlength="200"></label><div style="display:grid;grid-template-columns:1fr 1fr;gap:12px"><label>日期<input id="entryDate" type="date" value="${entry?.date||date}" required min="1900-01-01" max="2100-12-31"></label><label>时间<input id="entryTime" type="time" value="${entry?.time||localTime()}" required></label></div><button class="primary">${entry?'保存修改':'保存这笔记录'}</button></form>`,origin?'origin-expand':'');function fill(){const options=db.categories.filter(c=>c.type===type);if(!options.some(c=>c.id===cat))cat=options[0].id;$('#entryCategory').innerHTML=options.map(c=>`<option value="${c.id}" ${c.id===cat?'selected':''}>${esc(c.name)}</option>`).join('');$('#expenseType').classList.toggle('selected',type==='expense');$('#incomeType').classList.toggle('selected',type==='income');}fill();if(origin)expandEditor(origin);$('#expenseType').onclick=()=>{type='expense';fill();};$('#incomeType').onclick=()=>{type='income';fill();};$('#entryForm').onsubmit=e=>{e.preventDefault();try{const cents=parseMoney($('#entryMoney').value);if(cents<=0)throw Error('金额需要大于 0.00');const next={id:entry?.id||uid(),book:entry?.book||db.book,type,cat:$('#entryCategory').value,cents,note:$('#entryNote').value.trim(),date:$('#entryDate').value,time:$('#entryTime').value};if(entry)db.entries=db.entries.map(v=>v.id===entry.id?next:v);else db.entries.push(next);if(!persist())return;closeSheet(true);render(true);toast('已保存');}catch(err){toast(err.message);}};}
function detail(id){const e=db.entries.find(e=>e.id===id);if(!e)return;sheet('账单详情',`<div class="detail-amount ${e.type}">${e.type==='income'?'+':'−'}${fmt(e.cents)}</div><div class="detail-line"><span>分类</span><span>${esc(category(e.cat).name)}</span></div><div class="detail-line"><span>时间</span><span>${e.date} ${e.time}</span></div><div class="detail-line"><span>备注</span><span>${esc(e.note||'未填写备注')}</span></div><div class="detail-actions"><button class="secondary" data-action="delete" data-id="${id}">移入回收站</button><button class="primary" id="editEntry">编辑账单</button></div>`,'details');$('#editEntry').onclick=()=>editor(e.date,e);}
function quickCategory(id){const e=db.entries.find(e=>e.id===id);sheet('快速修改分类',`<div class="categories">${db.categories.filter(c=>c.type===e.type).map(c=>`<button class="${e.cat===c.id?'selected':''}" data-action="apply-cat" data-id="${id}" data-cat="${c.id}"><span class="cat-icon">${icon(c.id)}</span>${esc(c.name)}</button>`).join('')}</div>`);}
function moneySheet(cat=null){const b=budget(),v=cat?b.categories[cat]||0:b.total;sheet(cat?`${esc(category(cat).name)} · 分类预算`:'设置合计预算',`<form class="form-grid" id="moneyForm"><label>预算金额（¥）<input id="budgetMoney" class="money-input" inputmode="decimal" value="${inputMoney(v)}" required></label><p class="note">合计预算与分类预算独立设置，不自动相互覆盖。</p><button class="primary">保存预算</button>${cat?'<button type="button" class="secondary" id="removeBudget">移除此分类预算</button>':''}</form>`);$('#moneyForm').onsubmit=e=>{e.preventDefault();try{const n=parseMoney($('#budgetMoney').value),next=structuredClone(b);if(cat)next.categories[cat]=n;else next.total=n;db.budgets[budgetKey()]=next;if(!persist())return;closeSheet();render(true);}catch(err){toast(err.message);}};if(cat)$('#removeBudget').onclick=()=>{const next=structuredClone(b);delete next.categories[cat];db.budgets[budgetKey()]=next;if(!persist())return;closeSheet();render(true);};}
function removeEntries(ids,target){
 const chosen=new Set(ids),move=db.entries.filter(e=>chosen.has(e.id)).map(e=>({...e,deletedAt:Date.now()}));
 if(!move.length)return;
 db.trash.push(...move);db.entries=db.entries.filter(e=>!chosen.has(e.id));
 if(!persist())return;
 closeSheet();const sourcePage=page;
 const targets=(Array.isArray(target)?target:[target]).filter(el=>el&&document.contains(el));if(targets.length){targets.forEach(el=>el.inert=true);dissolve(targets,()=>{if(page===sourcePage)render(true);});}
 else render(true);
 toast(`已将 ${move.length} 笔账单移入回收站`);
}
function dissolve(elements,done){
 const canvas=$('#particles'),main=$('#displayArea').getBoundingClientRect(),dpr=Math.min(devicePixelRatio,2);
 canvas.width=main.width*dpr;canvas.height=main.height*dpr;canvas.style.width=main.width+'px';canvas.style.height=main.height+'px';
 const ctx=canvas.getContext('2d');ctx.scale(dpr,dpr);
 if(matchMedia('(prefers-reduced-motion: reduce)').matches){done();return;}
 const particles=[],step=(db.settings.effect==='strong'?7:db.settings.effect==='weak'?13:9)*Math.max(1,Math.sqrt(elements.length/8));
 for(const el of elements){
  const r=el.getBoundingClientRect();
  const top=Math.max(0,r.top-main.top),bottom=Math.min(main.height,r.bottom-main.top),left=Math.max(0,r.left-main.left),right=Math.min(main.width,r.right-main.left);
  for(let y=top;y<bottom;y+=step)for(let x=left;x<right;x+=step){const ox=x-(r.left-main.left+r.width/2),oy=y-(r.top-main.top+r.height/2),len=Math.hypot(ox,oy)||1;particles.push({x,y,vx:ox/len*(12+Math.random()*42)+(Math.random()-.5)*28,vy:oy/len*(12+Math.random()*42)+(Math.random()-.5)*28,size:1+Math.random()*2.2,shade:Math.random()>.7?'#777':'#eee'});}
 }
 const easeOut=t=>1-Math.pow(1-Math.max(0,Math.min(1,t)),3);
 elements.forEach(el=>el.style.transition='none');
 const start=performance.now();
 function frame(now){
  const t=(now-start)/1050;ctx.clearRect(0,0,main.width,main.height);elements.forEach(el=>el.style.opacity=String(1-easeOut(t/.38)));
  const phase=easeOut((t-.35)/.65);ctx.globalAlpha=easeOut(t/.32)*Math.max(0,1-phase);let shade=null;
  for(const p of particles){if(p.shade!==shade){ctx.fillStyle=p.shade;shade=p.shade;}ctx.beginPath();ctx.arc(p.x+p.vx*phase*3,p.y+p.vy*phase*3,p.size,0,Math.PI*2);ctx.fill();}
  if(t<1)requestAnimationFrame(frame);else{ctx.clearRect(0,0,main.width,main.height);done();}
 }
 requestAnimationFrame(frame);
}
function booksSheet(){sheet('我的账本',`${db.books.map(b=>`<button class="book-row" data-action="select-book" data-book="${b.id}"><span>${esc(b.name)}</span><span>${b.id===db.book?'当前':''}</span></button>`).join('')}<form id="bookForm" class="form-grid"><label>新建账本<input id="bookName" placeholder="例如：旅行账本" maxlength="20" required></label><button class="primary">创建账本</button></form>`);$('#bookForm').onsubmit=e=>{e.preventDefault();const name=$('#bookName').value.trim();if(!name)return;const id=uid();db.books.push({id,name});db.book=id;if(!persist())return;closeSheet();render();};}
function categoryEditor(cat=null){const c=cat?category(cat):null;sheet(c?'修改分类':'新增分类',`<form id="categoryForm" class="form-grid"><label>分类名称<input id="categoryName" value="${esc(c?.name||'')}" required></label><label>收支类型<select id="categoryType" ${c?'disabled':''}><option value="expense" ${c?.type==='expense'?'selected':''}>支出</option><option value="income" ${c?.type==='income'?'selected':''}>收入</option></select></label><p class="note">字母、数字宽度为 1；汉字及其他符号为 2。总宽度不超过 10。</p><button class="primary">保存分类</button></form>`);$('#categoryForm').onsubmit=e=>{e.preventDefault();const name=$('#categoryName').value.trim(),width=[...name].reduce((a,s)=>a+(/[a-zA-Z0-9]/.test(s)?1:2),0);if(!name||width>10){toast('名称不能为空，且最多 10 字符宽度');return;}if(db.categories.some(x=>x.name===name&&x.id!==cat)){toast('分类名称已存在');return;}if(c)c.name=name;else db.categories.push({id:uid(),name,type:$('#categoryType').value});if(!persist())return;closeSheet();render(true);};}
function exportDB(){const raw=JSON.stringify(db,null,2);if(window.AndroidStore){AndroidStore.exportBackup(raw);toast('请选择备份保存位置');}else{const blob=new Blob([raw],{type:'application/json'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=`墨账备份-${today()}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);toast('已导出备份');}}
function importDB(){sheet('导入完整备份',`<p class="intro-copy">导入将替换此设备上的所有墨账数据。建议先导出备份。</p><input id="backupFile" type="file" accept="application/json,.json" aria-label="选择备份文件"><p class="note">选择文件后将校验数据，并显示确认窗口。</p>`);$('#backupFile').onchange=async e=>{const file=e.target.files[0];if(!file)return;if(file.size>10*1024*1024){toast('备份文件不能超过 10 MB');return;}try{const next=validateDB(JSON.parse(await file.text()));confirmSheet('确认导入备份',`已校验：${next.books.length} 个账本、${next.entries.length} 笔账单。确认替换当前数据？`,()=>{db=next;storageError='';if(!persist())return;closeSheet();render();toast('备份已导入');});}catch(err){toast('导入失败：'+err.message);}};}
document.addEventListener('click',e=>{const b=e.target.closest('[data-action]');if(!b)return;const a=b.dataset.action,id=b.dataset.id,cat=b.dataset.cat;switch(a){case'menu':if(matchMedia('(min-width:840px)').matches)toggleTabletSidebar();else document.body.classList.toggle('drawer-open');break;case'nav':go(b.dataset.page);break;case'context-close':closeContextMenu();break;
case'context-edit':{const entry=db.entries.find(e=>e.id===id);if(entry)editor(entry.date,entry);break;}
case'context-delete':closeContextMenu();removeEntries([id],document.querySelector(`#scroll [data-swipe-id="${id}"]`));break;
case'context-select':startSelection(id);break;
case'toggle-selection':toggleSelection(id);break;
case'day-select':{const es=visibleEntries().filter(e=>e.date===b.dataset.date),all=es.every(e=>selectedIds.has(e.id));es.forEach(e=>all?selectedIds.delete(e.id):selectedIds.add(e.id));render(true);break;}
case'select-all':{const es=visibleEntries(),all=es.every(e=>selectedIds.has(e.id));selectedIds=new Set(all?[]:es.map(e=>e.id));render(true);break;}
case'selection-exit':exitSelection();break;
case'batch-category':batchCategory();break;
case'apply-batch-category':db.entries.forEach(e=>{if(selectedIds.has(e.id))e.cat=cat;});if(!persist())return;closeSheet();exitSelection();toast('已修改所选账单分类');break;
case'batch-delete':{if(!selectedIds.size){toast('请先选择账单');break;}const ids=[...selectedIds],targets=[...document.querySelectorAll('#scroll [data-swipe-id]')].filter(e=>selectedIds.has(e.dataset.swipeId));removeEntries(ids,targets);if(!db.entries.some(e=>ids.includes(e.id))){exitSelection(false);renderDock();}break;}
case'functions':if(isSettingsPage())switchSettings(false);break;case'budget':go(a,b.getBoundingClientRect());break;case'home':case'settings':case'stats':case'trash':go(a);break;case'close':closeSheet(true);break;case'add':editor(today(),null,b.classList.contains('add')?b.getBoundingClientRect():null);break;case'day-add':editor(b.dataset.date);break;case'details':detail(id);break;case'quick-cat':quickCategory(id);break;case'apply-cat':db.entries.find(e=>e.id===id).cat=cat;if(!persist())return;closeSheet();render(true);break;case'range':rangeSheet();break;case'budget-range':rangeSheet(true);break;case'budget-mode':budgetMode=b.dataset.mode;budgetRange=budgetMode==='year'?budgetRange.slice(0,4):budgetRange.slice(0,4)+'-'+(budgetRange.slice(5,7)||today().slice(5,7));render();break;case'shift':{const d=new Date(Number(budgetRange.slice(0,4)),budgetMode==='month'?Number(budgetRange.slice(5,7))-1:0,1);if(budgetMode==='month')d.setMonth(d.getMonth()+Number(b.dataset.dir));else d.setFullYear(d.getFullYear()+Number(b.dataset.dir));budgetRange=dateISO(d).slice(0,budgetMode==='month'?7:4);render();break;}case'set-total':moneySheet();break;case'set-category-budget':moneySheet(cat);break;case'add-category-budget':sheet('选择预算分类',`<div class="categories">${db.categories.filter(c=>c.type==='expense').map(c=>`<button data-action="set-category-budget" data-cat="${c.id}"><span class="cat-icon">${icon(c.id)}</span>${esc(c.name)}</button>`).join('')}</div>`);break;case'delete-day':{const date=b.dataset.date,ids=db.entries.filter(e=>e.book===db.book&&e.date===date).map(e=>e.id);removeEntries(ids,document.querySelector(`[data-day-shell="${date}"]`));break;}case'delete':removeEntries([id],document.querySelector(`[data-swipe-id="${id}"]`));break;case'restore':{const rec=db.trash.find(e=>e.id===id);const {deletedAt,...entry}=rec;db.entries.push(entry);db.trash=db.trash.filter(e=>e.id!==id);if(!persist())return;render(true);toast('已恢复账单');break;}case'purge':confirmSheet('永久删除','这笔账单将无法恢复。确认永久删除？',()=>{db.trash=db.trash.filter(e=>e.id!==id);if(!persist())return;closeSheet();render(true);});break;case'books':booksSheet();break;case'select-book':db.book=b.dataset.book;if(!persist())return;closeSheet();render();break;case'new-category':categoryEditor();break;case'rename-category':categoryEditor(cat);break;case'export':exportDB();break;case'import':importDB();break;case'clear-demo':confirmSheet('清空演示数据','会清空此应用当前的所有账本和记录，开始使用空白账本。建议先导出备份。',()=>{db=blank();if(!persist())return;closeSheet();go('home');});break;}});
document.addEventListener('change',e=>{if(e.target.id==='effect'){db.settings.effect=e.target.value;if(!persist())return;document.body.dataset.effect=e.target.value;}if(e.target.id==='homeBudget'){db.settings.budgetMode=e.target.value;if(!persist())return;}if(e.target.id==='tabletDockPosition'){db.settings.tabletDockPosition=e.target.value;if(!persist()){e.target.value=db.settings.tabletDockPosition;return;}renderDock();}});
document.addEventListener('input',e=>{if(e.target.id==='searchInput'){search=e.target.value;if(selectionActive){const allowed=new Set(visibleEntries().map(e=>e.id));selectedIds=new Set([...selectedIds].filter(id=>allowed.has(id)));renderDock();}$('#searchResults').innerHTML=daysHTML(selectedEntries().filter(v=>`${category(v.cat).name} ${v.note} ${inputMoney(v.cents)}`.toLowerCase().includes(search.toLowerCase())));}});
$('#drawerMask').onclick=()=>document.body.classList.remove('drawer-open');
let gesture=null,suppressClick=false,ignoredClickTarget=null,drawerGesture=null;
// Rightward navigation shares the row gesture stream; leftward deletion stays intact.
document.addEventListener('pointerdown',e=>{
 drawerGesture=null;
 if(e.button!==0||modalOpen||selectionActive||contextEntryId||isSettingsPage()||navigationMotion)return;
 const tablet=matchMedia('(min-width:840px)').matches;
 if(tablet?!document.body.classList.contains('sidebar-hidden'):document.body.classList.contains('drawer-open'))return;
 if(!e.target.closest('#displayArea')||e.target.closest('input,textarea,select,.swiped'))return;
 drawerGesture={id:e.pointerId,x:e.clientX,y:e.clientY,target:e.target,horizontal:false};
});
document.addEventListener('pointermove',e=>{
 if(!drawerGesture||e.pointerId!==drawerGesture.id)return;
 const dx=e.clientX-drawerGesture.x,dy=e.clientY-drawerGesture.y;
 if(Math.abs(dy)>12&&Math.abs(dy)>Math.abs(dx)){drawerGesture=null;return;}
 if(dx>20&&dx>Math.abs(dy)*1.5){
  drawerGesture.horizontal=true;
  if(gesture){clearTimeout(gesture.timer);gesture.target.style.transform='';gesture=null;}
  suppressClick=true;ignoredClickTarget=drawerGesture.target.closest('[data-swipe-id],.day-head')||drawerGesture.target;
 }
});
document.addEventListener('pointerup',e=>{
 if(!drawerGesture||e.pointerId!==drawerGesture.id)return;
 const dx=e.clientX-drawerGesture.x,dy=e.clientY-drawerGesture.y;
 if(drawerGesture.horizontal&&dx>=56&&dx>Math.abs(dy)*1.5){
  if(matchMedia('(min-width:840px)').matches)toggleTabletSidebar();
  else document.body.classList.add('drawer-open');
 }
 drawerGesture=null;
});
document.addEventListener('pointercancel',()=>{drawerGesture=null;});
document.addEventListener('pointerdown',e=>{suppressClick=false;ignoredClickTarget=null;if(modalOpen||selectionActive||contextEntryId||e.button!==0)return;const row=e.target.closest('[data-swipe-id]'),head=e.target.closest('.day-head'),target=row||(head?head.closest('[data-swipe-day]'):null);if(!target)return;gesture={x:e.clientX,y:e.clientY,target,id:row?.dataset.swipeId,date:head?.dataset.date,moved:false};gesture.timer=setTimeout(()=>{if(!gesture||gesture.moved)return;suppressClick=true;ignoredClickTarget=gesture.target;if(gesture.id)openEntryMenu(gesture.id,gesture.x,gesture.y);else editor(gesture.date);},550);});
document.addEventListener('pointermove',e=>{if(!gesture)return;const dx=e.clientX-gesture.x,dy=e.clientY-gesture.y;if(Math.abs(dx)>9||Math.abs(dy)>9){gesture.moved=true;clearTimeout(gesture.timer);}if(Math.abs(dx)>Math.abs(dy)&&Math.abs(dx)>20){gesture.target.style.transform=`translateX(${Math.max(-76,Math.min(0,dx))}px)`;}});
document.addEventListener('pointerup',e=>{if(!gesture)return;clearTimeout(gesture.timer);const dx=e.clientX-gesture.x,dy=e.clientY-gesture.y;if(gesture.moved){if(Math.abs(dx)>Math.abs(dy)){gesture.target.classList.toggle('swiped',dx<-32);gesture.target.parentElement.classList.toggle('reveal-ready',dx<-32);suppressClick=true;ignoredClickTarget=gesture.target;}gesture.target.style.transform='';}gesture=null;});
document.addEventListener('pointercancel',()=>{suppressClick=false;ignoredClickTarget=null;if(gesture){clearTimeout(gesture.timer);gesture.target.style.transform='';gesture=null;}});
document.addEventListener('click',e=>{const ignore=suppressClick&&e.detail!==0&&ignoredClickTarget?.contains(e.target);suppressClick=false;ignoredClickTarget=null;if(ignore){e.stopImmediatePropagation();e.preventDefault();}},true);
let fadeStart=-1,fadeEnd=-1,fadeRequest=0;function updateFade(){const scroll=$('#scroll').scrollTop,start=Math.max(76,160-scroll),end=Math.max(145,360-scroll);if(start!==fadeStart){$('#main').style.setProperty('--fade-start',start+'px');fadeStart=start;}if(end!==fadeEnd){$('#main').style.setProperty('--fade-end',end+'px');fadeEnd=end;}}
document.addEventListener('scroll',e=>{if(e.target!==$('#scroll'))return;boostMotionRate();if(!fadeRequest)fadeRequest=requestAnimationFrame(()=>{fadeRequest=0;updateFade();});},{passive:true,capture:true});document.addEventListener('pointerdown',boostMotionRate,{passive:true});
let edge=null;document.addEventListener('touchstart',e=>{if(!$('#scroll').contains(e.target))return;edge={x:e.touches[0].clientX,y:e.touches[0].clientY,top:$('#scroll').scrollTop};},{passive:true});document.addEventListener('touchmove',e=>{if(!edge||Math.abs(e.touches[0].clientX-edge.x)>Math.abs(e.touches[0].clientY-edge.y))return;const sc=$('#scroll'),dy=e.touches[0].clientY-edge.y,atBottom=sc.scrollHeight-sc.clientHeight-sc.scrollTop<2;if((sc.scrollTop<=0&&dy>0)||(atBottom&&dy<0)){$('#content').style.transition='none';$('#content').style.transform=`translateY(${Math.sign(dy)*Math.min(65,Math.abs(dy)*.24)}px)`;}},{passive:true});function releaseEdge(){$('#content').style.transition=`transform .6s ${MOTION_EASING}`;$('#content').style.transform='';edge=null;}document.addEventListener('touchend',()=>{if(edge)releaseEdge();});document.addEventListener('touchcancel',()=>{if(edge)releaseEdge();});
const layout=matchMedia('(min-width:840px)');layout.addEventListener('change',()=>{clearSidebarResize();document.body.classList.remove('drawer-open','sidebar-hidden');renderDock();});
window.onNativeBack=()=>{if(contextEntryId){closeContextMenu();return true;}if(selectionActive&&!modalOpen){exitSelection();return true;}if(modalOpen){closeSheet(true);return true;}if(document.body.classList.contains('drawer-open')){document.body.classList.remove('drawer-open');return true;}if(page==='trash'&&settingsSubpage){go('settings');return true;}if(page==='settings'){switchSettings(false);return true;}if(page!=='home'){go('home');return true;}return false;};
document.addEventListener('keydown',e=>{if(e.key==='Escape')window.onNativeBack();if(e.key==='Tab'&&modalOpen){const focusable=[...document.querySelectorAll('#modalRoot button,#modalRoot input,#modalRoot select')].filter(el=>!el.disabled);if(!focusable.length)return;const first=focusable[0],last=focusable.at(-1);if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}}});
render();if(!stored&&!new URLSearchParams(location.search).has('demo')){sheet('欢迎来到墨账',`<p class="intro-copy">账单仅保存于这台设备，开始前可以选择体验独立演示数据，或直接创建空白账本。</p><div class="button-row"><button class="secondary" id="startDemo">体验演示</button><button class="primary" id="startBlank">开始记账</button></div>`);$('#startDemo').onclick=()=>{db=demo();if(!persist())return;closeSheet();render();};$('#startBlank').onclick=()=>{if(!persist())return;closeSheet();render();};}if(storageError)setTimeout(()=>toast(storageError),300);

document.addEventListener('contextmenu',e=>{const row=e.target.closest('[data-swipe-id]');if(row&&!modalOpen&&!selectionActive){e.preventDefault();openEntryMenu(row.dataset.swipeId,e.clientX,e.clientY);}});
document.addEventListener('keydown',e=>{
 if(contextEntryId&&['ArrowDown','ArrowUp','Home','End'].includes(e.key)){e.preventDefault();const buttons=[...document.querySelectorAll('.entry-menu button')],index=buttons.indexOf(document.activeElement),next=e.key==='Home'?0:e.key==='End'?buttons.length-1:(index+(e.key==='ArrowDown'?1:-1)+buttons.length)%buttons.length;buttons[next].focus();}
 if(contextEntryId&&e.key==='Tab')closeContextMenu();
 if((e.shiftKey&&e.key==='F10')||e.key==='ContextMenu'){const row=document.activeElement?.closest('[data-swipe-id]');if(row&&!modalOpen&&!selectionActive){e.preventDefault();const r=row.getBoundingClientRect();openEntryMenu(row.dataset.swipeId,r.left+20,r.top+20);}}
});
