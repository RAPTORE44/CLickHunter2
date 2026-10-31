// À exécuter uniquement sur une copie jetable : les paramètres de combat et le terrain sont modifiés.
if(process.env.CLICKHUNTER_DISPOSABLE_TEST !== '1') throw new Error('Définis CLICKHUNTER_DISPOSABLE_TEST=1 uniquement pour un serveur jetable.');
const m=require(process.env.MINEFLAYER_MODULE || 'mineflayer');
const a=require('node:assert/strict'),fs=require('node:fs');
const options={host:'127.0.0.1',port:25581,auth:'offline',version:'1.21.4'};
const wait=t=>new Promise(r=>setTimeout(r,t));
const checks=[],chat=[],dimensions=[];
const resultFile=process.env.CLICKHUNTER_AUDIT_RESULTS || 'audit-paper-results.json';
let leader,member;
async function connect(name){const bot=m.createBot({...options,username:name});bot.on('messagestr',s=>chat.push(name+': '+s));bot._client.on('respawn',d=>dimensions.push({name,world:d.worldName}));await new Promise((resolve,reject)=>{bot.once('spawn',resolve);bot.once('error',reject)});bot.physicsEnabled=false;return bot;}
async function cmd(bot,text,delay=900){bot.chat(text);await wait(delay)}
function close(bot){if(bot.currentWindow)bot.closeWindow(bot.currentWindow)}
(async()=>{
try {
 leader=await connect('AuditSenior');member=await connect('AuditMember');await wait(3500);
 await cmd(leader,'/menu');a.equal(leader.currentWindow.inventoryStart,54);checks.push('Menu principal : 54 cases');close(leader);
 await cmd(leader,'/daily');a.equal(leader.currentWindow.inventoryStart,54);a(JSON.stringify(leader.currentWindow.slots).includes('100 Écus'));checks.push('Daily : calendrier et premier jour réduit à 100 Écus');close(leader);
 await cmd(leader,'/daily pnj remove',1200);await cmd(leader,'/daily pnj spawn',2500);a(chat.some(s=>s.includes('PNJ des récompenses journalières a été créé')));checks.push('PNJ ZNPCsPlus Daily créé avec les propriétés de regard individuel');
 await cmd(leader,'/raid admin param mobs.degats 0');await cmd(leader,'/raid admin param boss.degats 0');await cmd(leader,'/raid admin param mobs.pv 1');await cmd(leader,'/raid admin param boss.tous-les-mobs 1');await cmd(leader,'/raid admin param boss.pv 1000000');
 await cmd(leader,'/raid parchemin vache',1800);
 const scroll=leader.inventory.items().find(i=>i.name==='paper');a(scroll,'parchemin livré dans inventaire');await leader.equip(scroll,'hand');
 await cmd(leader,'/raid inviter AuditMember');await cmd(member,'/raid accepter AuditSenior');
 a(chat.some(s=>s.includes('a rejoint ton groupe')));checks.push('Invitation acceptée et groupe partagé');close(member);close(leader);
 await cmd(leader,'/raid lancer',3800);
 a(chat.some(s=>s.startsWith('AuditMember:')&&s.includes('Bienvenue dans le raid')));a(chat.some(s=>s.includes('Raid lancé')));
 checks.push('Les deux joueurs sont téléportés au lancement');
 const cows=Object.values(leader.entities).filter(e=>e.name==='cow');a(cows.length>0,'vaches de raid visibles');checks.push('Créatures du raid Vache : vaches réellement reçues par le client');
 const cow=cows[0];
 await cmd(leader,`/tp AuditSenior ${cow.position.x+1.4} ${cow.position.y} ${cow.position.z}`,1200);
 await wait(1600);
 const sword=leader.inventory.items().find(i=>i.name.endsWith('_sword'));a(sword,'épée présente');await leader.equip(sword,'hand');
 const target=Object.values(leader.entities).filter(e=>e.name==='cow').sort((x,y)=>x.position.distanceTo(leader.entity.position)-y.position.distanceTo(leader.entity.position))[0];
 a(target);await leader.lookAt(target.position.offset(0,.8,0),true);leader.attack(target);await wait(4000);
 const boss=Object.values(leader.entities).filter(e=>e.name==='cow');
 a(boss.length>0,'boss vache après élimination');a(chat.some(s=>s.includes('boss de la zone arrive')));checks.push('Boss de fin de raid : Vache invoquée après le premier kill');
 await cmd(member,'/raid quitter',1500);await cmd(leader,'/raid quitter',1500);
 a(chat.filter(s=>s.includes('Tu as quitté le groupe')).length>=2);checks.push('Sortie des deux joueurs et nettoyage du groupe');
 await cmd(leader,'/boss admin pnj entree spawn',1500);a(chat.some(s=>s.includes('PNJ placé')||s.includes('PNJ déjà présent')));checks.push('PNJ d’accès au boss public sauvegardé');
 await cmd(leader,'/diagnostic',1400);a.equal(leader.currentWindow.inventoryStart,54);a(JSON.stringify(leader.currentWindow.slots).includes('SQLite : ok'));checks.push('Diagnostic administrateur : SQLite accessible');close(leader);
 const pos=leader.entity.position;
 await cmd(leader,`/tp AuditMember ${pos.x} ${pos.y} ${pos.z-3}`,1000);
 await cmd(leader,'/fill ~-21 ~-1 ~-18 ~21 ~-1 ~5 minecraft:stone',1000);
 await cmd(leader,'/buyzone remove cochon',800);
 await cmd(leader,'/buyzone spawn cochon',1200);
 await cmd(leader,`/tp AuditMember ${pos.x} ${pos.y} ${pos.z+3}`,1500);
 a(member.currentWindow && member.currentWindow.inventoryStart===9,'BuyZone doit ouvrir le menu de portail au joueur sans accès');
 checks.push('BuyZone sans LuckPerms : événement de téléportation capté et achat proposé');close(member);
 fs.writeFileSync(resultFile,JSON.stringify({success:true,checks,dimensions,chat},null,2));console.log(JSON.stringify({success:true,checks}));
 leader.quit();member.quit();
}catch(error){fs.writeFileSync(resultFile,JSON.stringify({success:false,error:error.stack,checks,dimensions,chat},null,2));console.error(error);if(leader)leader.quit();if(member)member.quit();process.exitCode=1;}
})();setTimeout(()=>process.exit(2),90000).unref();
