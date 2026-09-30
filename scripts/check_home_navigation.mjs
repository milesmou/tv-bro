// Run against a debug WebView forwarded by adb; all test links exist only in the DOM.
const port = Number(process.argv[2] || 9222);
const pages = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
const page = pages.find(p => p.url.includes('/appcontent/home/'));
if (!page) throw Error('Home page is not open');
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject; });
const expression = `(() => {
 const results=[];
 const check=(value,message)=>{if(!value) throw Error(message); results.push(message);};
 const savedMode=homePageLinksMode, savedLinks=recommendationsToDisplay.slice();
 const savedFocus=sessionStorage.getItem('home-focus');
 try {
   const original=document.querySelectorAll('.site');
   check(original.length > 0,'Home cards are available');
   for (const card of original) {
     focusHomeControl(card);
     const r=card.getBoundingClientRect();
     check(r.left>=0 && r.right<=innerWidth && r.top>=0 && r.bottom<=innerHeight,'Card remains inside viewport while focused');
   }
   const links=Array.from({length:17},(_,i)=>({order:i,title:'长标题遥控器回归验证 '+i+' ABCDEFGHIJKLMNOPQRSTUVWXYZ',url:'https://v.qq.com/',favoriteId:1000+i}));
   renderLinks('BOOKMARKS',links);
   const cards=Array.from(document.querySelectorAll('.site'));
   const columns=getComputedStyle(document.getElementById('suggestions')).gridTemplateColumns.split(' ').length;
   const lastRow=Math.floor((cards.length-1)/columns)*columns;
   const source=lastRow-1;
   focusHomeControl(cards[source]); tvBroHandleRemoteKey('ArrowDown');
   check(document.activeElement===cards[cards.length-1],'Down reaches nearest card in incomplete final row');
   focusHomeControl(cards[0]);
   const id=cards[0].dataset.favoriteId;
   renderLinks('BOOKMARKS',links.slice().reverse().map((link,i)=>({...link,order:i})));
   check(document.activeElement.dataset.favoriteId===id,'Card focus follows bookmark identity after reorder');
   const edit=document.activeElement.querySelector('.site-edit');
   check(!!edit,'Edit action exists');
   focusHomeControl(edit); check(document.activeElement===edit,'Edit control can receive remote focus');
   return {viewport:[innerWidth,innerHeight],devicePixelRatio,columns,checks:results.length,results};
 } finally {
   renderLinks(savedMode,savedLinks);
   focusHomeControl(document.getElementById('search-input'));
   if(savedFocus===null) sessionStorage.removeItem('home-focus'); else sessionStorage.setItem('home-focus',savedFocus);
 }
})()`;
const result=await new Promise((resolve,reject)=>{
 ws.onmessage=({data})=>{const message=JSON.parse(data); if(message.id===1) resolve(message);};
 ws.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression,returnByValue:true}}));
 setTimeout(()=>reject(Error('CDP timeout')),10000).unref();
});
ws.close();
if(result.result?.exceptionDetails) throw Error(JSON.stringify(result.result.exceptionDetails));
console.log(JSON.stringify(result.result.result.value,null,2));
