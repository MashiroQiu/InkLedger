const {chromium}=require('playwright');const fs=require('fs'),path=require('path'),assert=require('assert');

(async()=>{const b=await chromium.launch({executablePath:process.env.CHROME_PATH||undefined,headless:true});const p=await b.newPage({viewport:{width:1440,height:960}});await p.route('https://inkledger.local/**',r=>{const f=new URL(r.request().url()).pathname.slice(1)||'index.html';return r.fulfill({body:fs.readFileSync(path.resolve(__dirname,'../app/src/main/assets',f)),contentType:f.endsWith('.js')?'application/javascript':f.endsWith('.css')?'text/css':'text/html'})});await p.goto('https://inkledger.local/?demo=1');

for(const position of ['center','left','right']){await p.evaluate(position=>{db.settings.tabletDockPosition=position;render()},position);

 assert.deepEqual(await p.locator('#main').boundingBox(),{x:0,y:0,width:1440,height:960});assert.equal((await p.locator('#displayArea').boundingBox()).x,256);

 for(const enter of [true,false]){const before=await p.locator('#dock').boundingBox();const result=await p.evaluate(enter=>{switchSettings(enter);navigationMotion.animations.forEach(a=>{a.pause();a.currentTime=0});const d=$('#dock').getBoundingClientRect();return {x:d.x,main:$('#main').getBoundingClientRect().width,frames:navigationMotion.animations.slice(0,2).map(a=>a.effect.getKeyframes())}},enter);assert.ok(Math.abs(result.x-before.x)<1,`${position} ${enter}: start continuity`);assert.equal(result.main,1440);assert.ok(result.frames.every(f=>f.some(k=>k.filter.includes('12px'))));

 const points=[];for(const fraction of [0,.2,.6,1])points.push(await p.evaluate(f=>{navigationMotion.animations.forEach(a=>a.currentTime=480*f);return $('#dock').getBoundingClientRect().x},fraction));if(position!=='right'){assert.ok(Math.abs(points[0]-points[1])>1);assert.ok(Math.abs(points[1]-points[2])>1);assert.ok(Math.abs(points[2]-points[3])>0.1)}else assert.ok(points.every(x=>Math.abs(x-points[0])<.1));

 await p.evaluate(()=>navigationMotion.animations.forEach(a=>a.finish()));await p.waitForFunction(()=>navigationMotion===null);assert.equal((await p.locator('#displayArea').boundingBox()).x,enter?0:256);

 }

 // Interrupt at a known intermediate frame: the new animation must start there.

 const delta=await p.evaluate(()=>{switchSettings(true);navigationMotion.animations.forEach(a=>{a.pause();a.currentTime=100});const before=$('#dock').getBoundingClientRect().x;switchSettings(false);navigationMotion.animations.forEach(a=>{a.pause();a.currentTime=0});return Math.abs(before-$('#dock').getBoundingClientRect().x)});assert.ok(delta<1,`${position} interruption delta ${delta}`);await p.evaluate(()=>navigationMotion.animations.forEach(a=>a.finish()));await p.waitForFunction(()=>navigationMotion===null);

}

await p.locator('#dock .add').click();await p.waitForFunction(()=>sheetMotion===null);assert.equal((await p.locator('.sheet-mask').boundingBox()).x,256);await p.locator('.sheet [data-action=close]').click();await p.waitForFunction(()=>!modalOpen);

await p.setViewportSize({width:432,height:960});await p.waitForTimeout(100);assert.equal((await p.locator('#displayArea').boundingBox()).x,0);await p.locator('#toolbar [data-action=menu]').click();await p.waitForTimeout(350);assert.equal((await p.locator('#main').boundingBox()).width,432);assert.equal((await p.locator('#sidebar').boundingBox()).x,0);

console.log('PASS: full-screen canvas, overlay sidebar, all dock positions start/end/interruption continuity, Gaussian retained, tablet mask excludes sidebar, portrait overlay');await b.close();})().catch(e=>{console.error(e);process.exit(1)});

