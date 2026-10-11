// C3b live QA: node c3bbot.js <srv2ctl> <name> <classSlot> <phase>
const mineflayer = require('mineflayer'); const fs = require('fs'); const { execFileSync } = require('child_process');
const [ctl, name, slot, phase] = process.argv.slice(2);
const LOG = '/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2/logs/latest.log';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const lines = () => fs.readFileSync(LOG, 'utf8').split('\n');
async function server(c, re, wait = 2200) { const n = lines().length; cmd(c); await sleep(wait); return lines().slice(n).filter(l => !re || re.test(l)).map(l => l.replace(/^\[[\d:]+\] \[[^\]]+\]: /, '')); }
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const chat = [];
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot._client.on('system_chat', (pk) => { if (!(pk.isActionBar || pk.overlay)) chat.push(flat(pk.content)); });
function step(k, v) { console.log(k, JSON.stringify(v)); }
const ids = async (what) => { const d = (await server(`data get entity ${name} ${what}`, /entity data/)).join(' ');
  return [...new Set((d.match(/"id":"(armor|weapon)\.class\.[a-z0-9.]+","uuid":"[0-9a-f]{8}/g) || []).concat(d.match(/asset_id: "[^"]+"/g) || []))]; };
bot.once('spawn', async () => {
  try {
    await sleep(5000);
    if (bot.currentWindow) { await bot.clickWindow(Number(slot), 0, 0).catch(() => {}); await sleep(2500); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    await sleep(1000);
    if (phase === 'gear') {
      step('armor', await ids('equipment'));
      step('weapon1', (await ids('Inventory')).filter(x => x.includes('weapon')));
      cmd(`suld exp ${name} 99999999`); cmd(`suld exp ${name} 99999999`); cmd(`suld exp ${name} 99999999`); await sleep(4000);
      step('weapon60', (await ids('Inventory')).filter(x => x.includes('weapon')));
      chat.length = 0; bot.chat('/classgear'); await sleep(1500); step('info', chat.slice(-1)[0]);
      chat.length = 0; bot.chat('/classgear upgrade'); await sleep(1000); step('playerUpgradeCmd', chat.slice(-1));
    } else if (phase === 'farm') {
      cmd('fill 2995 99 2995 3011 99 3011 stone'); cmd('fill 2995 100 2995 3011 104 3011 air'); await sleep(500); cmd(`tp ${name} 3003 100 3003`); await sleep(2500);
      const exps = [];
      bot.on('messagestr', (m) => { const mm = /\+(\d+) EXP \(Говийн Чоно\)(.*)/.exec(m); if (mm) exps.push(Number(mm[1]) + (mm[2] ? '*' : '')); });
      for (let w = 0; w < 9; w++) {
        cmd(`suld spawnmob ${name} mob.goviin_chono 25`); await sleep(1200);
        cmd(`execute at ${name} as @e[type=wolf,distance=..20] run damage @s 9999 minecraft:player_attack by ${name}`); await sleep(1500);
        cmd(`execute at ${name} run minecraft:kill @e[type=wolf,distance=..30]`); await sleep(300);
        // each wave is roughly one active minute of fighting for the tracker
      }
      step('expPerKill', { n: exps.length, first: exps.slice(0, 3), at60: exps.slice(58, 61), at150: exps.slice(148, 151), last: exps.slice(-3) });
      cmd(`tp ${name} 3403 100 3403`); cmd('fill 3395 99 3395 3411 99 3411 stone'); await sleep(2500);
      exps.length = 0;
      cmd(`suld spawnmob ${name} mob.goviin_chono 3`); await sleep(1200);
      cmd(`execute at ${name} as @e[type=wolf,distance=..20] run damage @s 9999 minecraft:player_attack by ${name}`); await sleep(1500);
      step('afterMovingOn', exps.slice(0, 3));
      chat.length = 0; bot.chat('/classgear'); await sleep(1500); step('info', chat.slice(-1)[0]);
    } else if (phase === 'smith') {
      cmd(`spawn ${name}`); await sleep(2500);
      cmd(`tp ${name} 30.5 72 0.5 90 0`); await sleep(3000);
      const npc = Object.values(bot.entities).filter(e => e.name === 'mannequin').sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0];
      step('npcDist', npc ? npc.position.distanceTo(bot.entity.position).toFixed(1) : null);
      const openMenu = async () => { await bot.lookAt(npc.position.offset(0, 1.6, 0)); bot.activateEntity(npc); await sleep(1500); };
      await openMenu(); step('repairTitle', bot.currentWindow && bot.currentWindow.title);
      await bot.clickWindow(22, 0, 0).catch(() => {}); await sleep(1500);
      const w = bot.currentWindow; step('smithTitle', w && w.title);
      const s22 = w && w.slots[22]; step('upgradeBtn', s22 && s22.name);
      // meet the T2 gates: armour level 12, Говийн Булш cleared, coins, materials
      cmd(`classgear set ${name} level 12`); cmd(`classgear set ${name} cleared dungeon.govi_bulsh`); cmd(`suld coins ${name} 5000`);
      cmd(`itemsadmin give ${name} item.khilentsiin_khor common 1`); await sleep(500);
      for (let i = 0; i < 9; i++) cmd(`itemsadmin give ${name} item.khilentsiin_khor common 1`);
      await sleep(1500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(500);
      await openMenu(); await bot.clickWindow(22, 0, 0).catch(() => {}); await sleep(1500);
      step('upgradeBtnNow', bot.currentWindow && bot.currentWindow.slots[22] && bot.currentWindow.slots[22].name);
      await bot.clickWindow(22, 0, 0).catch(() => {}); await sleep(1500);
      step('confirmTitle', bot.currentWindow && bot.currentWindow.title);
      step('beforeConfirm', (await ids('equipment')).filter(x => x.includes('chestplate')));
      await bot.clickWindow(11, 0, 0).catch(() => {}); await sleep(2000);
      step('afterConfirm', (await ids('equipment')).filter(x => x.includes('chestplate') || x.includes('asset')).slice(0, 3));
      // far away: open the confirmation, walk off 20 blocks, confirm → refused
      cmd(`classgear set ${name} level 24`); cmd(`classgear set ${name} cleared dungeon.mosun_orgil`); cmd(`classgear set ${name} mastery 1`); cmd(`suld coins ${name} 20000`);
      for (let i = 0; i < 15; i++) cmd(`itemsadmin give ${name} item.mosun_chuluu rare 1`);
      await sleep(1500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(500);
      await openMenu(); await bot.clickWindow(22, 0, 0).catch(() => {}); await sleep(1200); await bot.clickWindow(22, 0, 0).catch(() => {}); await sleep(1200);
      step('confirm2', bot.currentWindow && bot.currentWindow.title);
      cmd(`execute at ${name} run tp ${name} ~20 ~ ~`); await sleep(1500);
      chat.length = 0; await bot.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500);
      step('farConfirm', chat.slice(-2)); step('stillT2', (await ids('equipment')).filter(x => x.includes('chestplate')));
    }
  } catch (e) { console.log('ERR', e && e.stack || e); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 240000);
