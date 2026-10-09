// Run on the debug home WebView after adb forwards its devtools socket to port 9222.
// Exercises the real native search bridge; restores the original search setting afterward.
import {readFile} from 'node:fs/promises';
const port = Number(process.argv[2] || 9222);
const pages = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
const page = pages.find(p => p.url.includes('/appcontent/home/'));
if (!page) throw Error('Home page is not open');
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
const videoScript = await readFile(new URL('../app/src/main/assets/video_controls.js', import.meta.url), 'utf8');
const expression = `(async () => {
 const results=[];
 const check=(ok,message)=>{if(!ok)throw Error(message);results.push(message)};
 const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
 const savedEngine=searchEngine, savedUrl=searchEngineUrl;
 const unusual='https://example.test/?quote="&slash='+String.fromCharCode(92)+'&apostrophe='+String.fromCharCode(39)+'&q=[query]';
 let frame;
 try {
   TVBro.setSearchEngine('custom',unusual);
   TVBro.onHomePageLoaded();
   await wait(250);
   check(searchEngineUrl===unusual,'Native search bridge preserves quotes and backslashes');
   frame=document.createElement('iframe'); document.body.appendChild(frame);
   const doc=frame.contentDocument, win=frame.contentWindow;
   doc.body.innerHTML='<div id="player"><video></video><div class="vjs-control-bar" style="display:none">Controls</div></div>';
   win.eval(${JSON.stringify(videoScript)});
   const root=doc.getElementById('player'), bar=root.lastElementChild;
   check(win.getComputedStyle(bar).display==='flex','Hidden video controls retain flex layout');
   let scans=0;
   const query=root.querySelectorAll.bind(root);
   root.querySelectorAll=function(s){scans++;return query(s)};
   for(let i=0;i<500;i++)bar.style.opacity=String(i%2);
   await wait(350);
   check(bar.style.opacity==='1','Player auto-hide is repaired');
   check(scans<=2,'Mutation burst is batched without observing own writes');
   frame.remove(); frame=null;
   frame=document.createElement('iframe'); document.body.appendChild(frame);
   frame.contentDocument.body.innerHTML='<div><video></video><div class="vjs-control-bar" style="display:none">Controls</div></div>';
   frame.contentWindow.tvBroDisablePersistentControls=true;
   frame.contentWindow.eval(${JSON.stringify(videoScript)});
   check(frame.contentDocument.querySelector('.vjs-control-bar').style.display==='none','Site opt-out leaves player controls untouched');
   return results;
 } finally {
   if(frame)frame.remove();
   TVBro.setSearchEngine(savedEngine,savedUrl);TVBro.onHomePageLoaded();
 }
})()`;
try {
 const response=await new Promise((resolve,reject)=>{
   ws.onmessage=({data})=>{const r=JSON.parse(data);if(r.id===1)resolve(r)};
   ws.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression,returnByValue:true,awaitPromise:true}}));
   setTimeout(()=>reject(Error('CDP timeout')),10000).unref();
 });
 if(response.result?.exceptionDetails)throw Error(JSON.stringify(response.result.exceptionDetails));
 console.log(JSON.stringify(response.result.result.value,null,2));
} finally {ws.close()}
