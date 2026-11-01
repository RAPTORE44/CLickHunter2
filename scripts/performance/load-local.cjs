// Copie Paper locale uniquement. NODE_PATH doit contenir mineflayer.
const fs=require('node:fs'),assert=require('node:assert/strict'),m=require('mineflayer');
const wait=t=>new Promise(r=>setTimeout(r,t));let bots=[];const out=[];
async function connect(name){return new Promise((resolve,reject)=>{const b=m.createBot({host:'127.0.0.1',port:25582,username:name,auth:'offline',version:'1.21.4'});bots.push(b);b.once('spawn',()=>{b.physicsEnabled=false;resolve(b)});b.once('error',reject);setTimeout(()=>reject(Error('login timeout '+name)),20000).unref()})}
(async()=>{try{for(const count of [1,10,25,50,100]){
const before=Date.now(),clients=[];for(let i=0;i<count;i++)clients.push(await connect('Load'+String(i).padStart(3,'0')));
await wait(1500);let packets=0,headers=0;for(const b of clients)b._client.on('packet',(data,meta)=>{packets++;if(meta.name==='playerlist_header')headers++});
const start=Date.now();for(const b of clients)b.chat('/stats');await wait(3500);const opened=clients.filter(b=>b.currentWindow).length;for(const b of clients)if(b.currentWindow)b.closeWindow(b.currentWindow);await wait(1500);
out.push({clients:count,connectionMs:start-before,observationMs:Date.now()-start,statsOpened:opened,decodedPackets:packets,tabHeaderPackets:headers});fs.writeFileSync(process.env.LOAD_RESULT||'/tmp/clickhunter-load107.json',JSON.stringify(out,null,2));console.log(JSON.stringify(out.at(-1)));assert.equal(opened,count);for(const b of clients)b.quit();bots=[];await wait(2500);
} }catch(e){console.error(e);bots.forEach(b=>b.quit());process.exitCode=1}})();setTimeout(()=>{bots.forEach(b=>b.quit());process.exit(2)},300000).unref();
