const {chromium}=require('playwright');const fs=require('fs'),path=require('path'),assert=require('assert');

(async()=>{const b=await chromium.launch({executablePath:process.env.CHROME_PATH||undefined,headless:true});const p=await b.newPage({viewport:{width:360,height:800},deviceScaleFactor:2});await p.route('https://inkledger.local/**',r=>{const f=new URL(r.request().url()).pathname.slice(1)||'index.html';return r.fulfill({body:fs.readFileSync(path.resolve(__dirname,'../app/src/main/assets',f)),contentType:f.endsWith('.js')?'application/javascript':f.endsWith('.css')?'text/css':'text/html'})});await p.goto('https://inkledger.local/?demo=1');

const baseline=await p.evaluate(()=>{editor(today(),null,document.querySelector('#dock .add').getBoundingClientRect());const r={target:sheetMotion.animation.effect.target.className,easing:sheetMotion.animation.effect.getTiming().easing,duration:sheetMotion.animation.effect.getTiming().duration};closeSheet();return r;});assert.ok(baseline.target.includes('sheet'));assert.equal(baseline.easing,'cubic-bezier(0.16, 1, 0.3, 1)');assert.equal(baseline.duration,460);

for(const viewport of [{width:320,height:720},{width:432,height:960},{width:800,height:1280},{width:960,height:1440},{width:1440,height:960}]){

 await p.setViewportSize(viewport);await p.locator('#dock .add').click();await p.waitForFunction(()=>sheetMotion===null);

 const dimensions=await p.evaluate(()=>{const panel=document.querySelector('.sheet'),main=document.querySelector('#main').getBoundingClientRect(),r=panel.getBoundingClientRect();return {left:r.left,right:r.right,mainLeft:main.left,mainRight:main.right,client:panel.clientWidth,scroll:panel.scrollWidth,fields:[...panel.querySelectorAll('input,select')].map(e=>({left:e.getBoundingClientRect().left,right:e.getBoundingClientRect().right})),transform:getComputedStyle(document.querySelector('#entryForm')).transform};});

 assert.ok(dimensions.left>=dimensions.mainLeft-.5&&dimensions.right<=dimensions.mainRight+.5);assert.ok(dimensions.scroll<=dimensions.client+1);assert.ok(dimensions.fields.every(f=>f.left>=dimensions.left&&f.right<=dimensions.right));assert.equal(dimensions.transform,'none');assert.equal(await p.locator('.sheet').evaluate(e=>getComputedStyle(e).willChange),'transform');

 await p.locator('.sheet [data-action=close]').click();assert.equal(await p.evaluate(()=>sheetClosing),true);assert.equal(await p.locator('.sheet').count(),1);assert.ok(await p.evaluate(()=>sheetMotion.animation.effect.getKeyframes().at(-1).transform.includes('scale')));await p.waitForFunction(()=>!modalOpen);assert.equal(await p.locator('.sheet,.sheet-mask').count(),0);assert.equal(await p.locator('#dock').evaluate(e=>e.inert),false);

}

await p.locator('#dock .add').click();await p.waitForFunction(()=>sheetMotion===null);await p.locator('#entryMoney').fill('1.23');await p.locator('#entryForm .primary').click();assert.equal(await p.evaluate(()=>sheetClosing),true);await p.waitForFunction(()=>!modalOpen);assert.ok(await p.evaluate(()=>db.entries.some(e=>e.cents===123)));

// Reversing during expansion must finish and free input, without a second click.

await p.locator('#dock .add').click();await p.evaluate(()=>{sheetMotion.animation.currentTime=120;closeSheet(true)});await p.waitForFunction(()=>!modalOpen);assert.equal(await p.locator('.sheet').count(),0);

// Replacing a closing dialog must not let old completion remove the new panel.

await p.locator('#dock .add').click();await p.waitForFunction(()=>sheetMotion===null);await p.evaluate(()=>{closeSheet(true);moneySheet()});await p.waitForTimeout(450);assert.equal(await p.locator('#moneyForm').count(),1);await p.evaluate(()=>closeSheet());

await p.emulateMedia({reducedMotion:'reduce'});await p.locator('#dock .add').click();assert.equal(await p.evaluate(()=>sheetMotion),null);await p.locator('.sheet [data-action=close]').click();assert.equal(await p.evaluate(()=>modalOpen),false);

console.log('PASS: 5 viewport widths, original whole-panel expansion, return-to-plus closing, interrupted expansion, dialog replacement, reduced motion');await b.close();})().catch(e=>{console.error(e);process.exit(1)});

