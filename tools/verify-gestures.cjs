const {chromium}=require('playwright');
const fs=require('fs'),path=require('path'),assert=require('assert');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true});
 const p=await browser.newPage({viewport:{width:432,height:960},hasTouch:true,isMobile:true});
 await p.route('https://inkledger.local/**',r=>{const f=new URL(r.request().url()).pathname.slice(1)||'index.html';return r.fulfill({body:fs.readFileSync(path.resolve(__dirname,'../app/src/main/assets',f)),contentType:f.endsWith('.js')?'application/javascript':f.endsWith('.css')?'text/css':'text/html'});});
 await p.goto('https://inkledger.local/?demo=1');
 const cdp=await p.context().newCDPSession(p);
 async function swipe(x,y,dx,dy=0){await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x,y}]});for(let i=1;i<=8;i++){await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:x+dx*i/8,y:y+dy*i/8}]});await p.waitForTimeout(16);}await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await p.waitForTimeout(350);}
 const row=await p.locator('[data-swipe-id]').first().boundingBox();
 await swipe(row.x+100,row.y+30,120);
 assert.equal(await p.evaluate(()=>document.body.classList.contains('drawer-open')),true);
 assert.equal(Math.round((await p.locator('#sidebar').boundingBox()).width),240);
 assert.equal(await p.locator('.sheet').count(),0,'swipe must not open bill detail');
 await p.locator('#drawerMask').click({position:{x:420,y:500}});await p.waitForTimeout(350);
 await swipe(row.x+180,row.y+30,-100);
 assert.equal(await p.locator('[data-swipe-id]').first().evaluate(e=>e.classList.contains('swiped')),true,'left swipe still reveals delete');
 assert.equal(await p.evaluate(()=>document.body.classList.contains('drawer-open')),false);
 await swipe(row.x+100,row.y+30,100); // Close an already exposed deletion row.
 assert.equal(await p.evaluate(()=>document.body.classList.contains('drawer-open')),false);
 await p.locator('#dock [data-action="settings"]').click();await p.waitForTimeout(550);
 await swipe(40,180,120);assert.equal(await p.evaluate(()=>document.body.classList.contains('drawer-open')),false,'settings remains full-screen');
 await p.locator('#dock [data-action="functions"]').click();await p.waitForTimeout(550);
 const plus=await p.locator('#dock .add').boundingBox();assert.equal(plus.width,64);
 await p.locator('#dock .add').click();
 const editor=await p.evaluate(()=>{const times=[46,230,414];const mask=sheetMotion.maskAnimation;const result=times.map(t=>{[sheetMotion.animation,mask,...sheetMotion.extra].forEach(a=>{a.pause();a.currentTime=t;});return +getComputedStyle(document.querySelector('.sheet-mask')).opacity;});[sheetMotion.animation,mask,...sheetMotion.extra].forEach(a=>a.finish());return result;});
 assert.ok(editor[0]<.25&&editor[0]>0&&editor[0]<editor[1]&&editor[1]<editor[2],JSON.stringify(editor));
 await p.waitForTimeout(50);await p.evaluate(()=>closeSheet());await p.waitForTimeout(400);
 await p.setViewportSize({width:1440,height:960});
 const budget=await p.locator('#content [data-action="budget"]').boundingBox();
 await p.mouse.move(budget.x+60,budget.y+25);await p.mouse.down();await p.waitForTimeout(50);await p.mouse.up();
 const result=await p.evaluate(()=>{
  const source=document.querySelector('.surface-snapshot'),animations=navigationMotion.animations;
  const geometry=()=>[source.querySelector('.hero .total'),source.querySelector('.budget-card'),source.querySelector('.day-head')].map(el=>{const r=el.getBoundingClientRect();return [r.x,r.y,r.width,r.height];});
  const samples=[0,48,240,432].map(t=>{animations.forEach(a=>{a.pause();a.currentTime=t;});return {geometry:geometry(),darkness:+getComputedStyle(source.querySelector('.budget-expand-shade')).opacity,clip:getComputedStyle(document.querySelector('#pageSurface')).clipPath};});
  animations.forEach(a=>a.finish());return samples;
 });
 for(const s of result)assert.deepEqual(s.geometry,result[0].geometry,'background text/cards must not move while dimming');
 assert.ok(result[1].darkness>0&&result[1].darkness<.1,JSON.stringify(result));
 assert.ok(result[1].darkness<result[2].darkness&&result[2].darkness<result[3].darkness);
 await p.waitForTimeout(50);
 console.log('PASS: real touch right swipe, compact sidebar, preserved left delete, settings exclusion, larger plus, gradual dim, stationary background at 4 animation timestamps');
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1);});
