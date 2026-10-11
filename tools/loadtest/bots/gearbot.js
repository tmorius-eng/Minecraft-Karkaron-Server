// C2 live QA: every escape vector of a soulbound class weapon. node gearbot.js <srv2ctl> <name> <phase>
const mineflayer = require('mineflayer'); const fs = require('fs'); const { execFileSync } = require('child_process');
const { Vec3 } = require('vec3');
const [ctl, name, phase] = process.argv.slice(2);
const LOG = '/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2/logs/latest.log';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const lines = () => fs.readFileSync(LOG, 'utf8').split('\n');
async function server(c, re, wait = 1200) { const n = lines().length; cmd(c); await sleep(wait);
  return lines().slice(n).filter(l => !re || re.test(l)).map(l => l.replace(/^\[[\d:]+\] \[[^\]]+\]: /, '')); }
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const chat = [];
const res = {};
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot._client.on('system_chat', (pk) => { if (!(pk.isActionBar || pk.overlay)) chat.push(flat(pk.content)); });
const isWeapon = (i) => i && i.components && JSON.stringify(i.components).includes('weapon.class');
const weapon = () => bot.inventory.slots.findIndex(isWeapon);
const countW = () => bot.inventory.slots.filter(isWeapon).length;
const inv = () => bot.inventory.slots.map((i, n) => i ? n + ':' + i.name + (isWeapon(i) ? '*' : '') : null).filter(Boolean).join(' ');
function step(k, v) { res[k] = v; console.log(k, JSON.stringify(v)); }
async function hold() { const w = weapon(); if (w < 0) return false; if (w >= 36 && w <= 44) { bot.setQuickBarSlot(w - 36); } else { await bot.clickWindow(w, 0, 2); await sleep(400); bot.setQuickBarSlot(0); } await sleep(500); return true; }
bot.once('spawn', async () => {
  try {
    await sleep(5000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2500); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    await sleep(800);
    step('inv0', inv());
    if (phase === 'vectors' || phase === 'frame') { cmd('fill 2995 99 2995 3011 99 3011 stone'); cmd('fill 2995 100 2995 3011 104 3011 air'); await sleep(500); cmd(`tp ${name} 3003 100 3003`); await sleep(2500); }
    const p = bot.entity.position.floored();
    if (phase === 'vectors') {
      // drop (Q)
      await hold(); const w0 = weapon();
      try { await bot.tossStack(bot.inventory.slots[w0]); } catch (e) {}
      await sleep(1000); step('drop.kept', countW());
      // /item destroy
      await hold(); chat.length = 0; bot.chat('/item destroy'); await sleep(1000); step('destroy.kept', countW()); step('destroy.msg', chat.slice(-2));
      // chest
      cmd(`setblock ${p.x + 2} ${p.y} ${p.z} chest`); await sleep(600);
      let win = await bot.openContainer(bot.blockAt(new Vec3(p.x + 2, p.y, p.z)));
      let ws = win.slots.findIndex((s, n) => n >= win.inventoryStart && isWeapon(s));
      await bot.clickWindow(ws, 0, 1).catch(() => {}); await sleep(600); // shift-click into the chest
      await bot.clickWindow(ws, 0, 0).catch(() => {}); await sleep(400); await bot.clickWindow(0, 0, 0).catch(() => {}); await sleep(600); // pick + place in slot 0
      if (win.selectedItem) { await bot.clickWindow(ws, 0, 0).catch(() => {}); await sleep(300); }
      // number-key swap from the hotbar into chest slot 1
      await bot.clickWindow(1, bot.quickBarSlot, 2).catch(() => {}); await sleep(600);
      step('chest.containerHasWeapon', win.containerItems().filter(isWeapon).length);
      win.close(); await sleep(600);
      step('chest.server', await server(`data get block ${p.x + 2} ${p.y} ${p.z} Items`, /Items|no items|has the following/));
      step('chest.kept', countW());
      // shulker box block
      cmd(`setblock ${p.x - 2} ${p.y} ${p.z} shulker_box`); await sleep(600);
      win = await bot.openContainer(bot.blockAt(new Vec3(p.x - 2, p.y, p.z)));
      ws = win.slots.findIndex((s, n) => n >= win.inventoryStart && isWeapon(s));
      await bot.clickWindow(ws, 0, 1).catch(() => {}); await sleep(600);
      step('shulker.containerHasWeapon', win.containerItems().filter(isWeapon).length);
      win.close(); await sleep(600); step('shulker.kept', countW());
      // hopper GUI
      cmd(`setblock ${p.x} ${p.y} ${p.z + 2} hopper`); await sleep(600);
      win = await bot.openContainer(bot.blockAt(new Vec3(p.x, p.y, p.z + 2)));
      ws = win.slots.findIndex((s, n) => n >= win.inventoryStart && isWeapon(s));
      await bot.clickWindow(ws, 0, 1).catch(() => {}); await sleep(600);
      step('hopper.containerHasWeapon', win.containerItems().filter(isWeapon).length);
      win.close(); await sleep(600);
      // anvil
      cmd(`setblock ${p.x} ${p.y} ${p.z - 2} anvil`); await sleep(600);
      await bot.lookAt(new Vec3(p.x + 0.5, p.y + 0.5, p.z - 1.5)); await bot.activateBlock(bot.blockAt(new Vec3(p.x, p.y, p.z - 2))); await sleep(1200);
      win = bot.currentWindow;
      if (win) { ws = win.slots.findIndex((s, n) => n >= win.inventoryStart && isWeapon(s)); await bot.clickWindow(ws, 0, 1).catch(() => {}); await sleep(600);
        step('anvil.inputHasWeapon', [0, 1].filter(n => isWeapon(win.slots[n])).length); bot.closeWindow(win); await sleep(600); } else step('anvil', 'no window');
      // bundle (own inventory): weapon on cursor, right-click the bundle
      cmd(`minecraft:give ${name} bundle 1`); await sleep(1000);
      const bs = bot.inventory.slots.findIndex(s => s && s.name === 'bundle');
      ws = weapon();
      await bot.clickWindow(ws, 0, 0).catch(() => {}); await sleep(400);
      await bot.clickWindow(bs, 1, 0).catch(() => {}); await sleep(600);
      if (bot.inventory.selectedItem) { await bot.clickWindow(ws, 0, 0).catch(() => {}); await sleep(400); }
      step('bundle.server', await server(`data get entity ${name} Inventory`, /bundle/));
      step('bundle.kept', countW());
      // item frame + armour stand
      await hold();
      cmd(`setblock ${p.x + 1} ${p.y} ${p.z + 3} stone`); cmd(`summon item_frame ${p.x + 1} ${p.y} ${p.z + 2} {Facing:2b,Tags:["gq"]}`); cmd(`summon armor_stand ${p.x - 1} ${p.y} ${p.z - 1} {Tags:["gq"]}`); await sleep(1000);
      for (const e of Object.values(bot.entities)) if (e.name === 'item_frame' || e.name === 'armor_stand') { await bot.lookAt(e.position.offset(0, 0.5, 0)); try { await bot.activateEntityAt(e, e.position.offset(0, 0.5, 0)); } catch (x) { bot.activateEntity(e); } await sleep(600); }
      step('frame.server', await server(`execute as @e[tag=gq] run data get entity @s`, /entity data/));
      step('frameStand.kept', countW());
      // 2x2 crafting grid: allowed to sit there, never an ingredient (no recipe output)
      ws = weapon(); await bot.clickWindow(ws, 0, 0).catch(() => {}); await sleep(300); await bot.clickWindow(1, 0, 0).catch(() => {}); await sleep(600);
      step('craft.result', bot.inventory.slots[0] && bot.inventory.slots[0].name);
      await bot.clickWindow(1, 0, 0).catch(() => {}); await sleep(300); const free = bot.inventory.slots.findIndex((s, n) => n >= 9 && n < 45 && !s); await bot.clickWindow(free, 0, 0).catch(() => {}); await sleep(600);
      step('craft.kept', countW());
      // death keeps it
      cmd(`kill ${name}`); await sleep(1500); bot.respawn ? bot.respawn() : bot._client.write('client_command', { actionId: 0 }); await sleep(3000);
      step('death.kept', countW());
      cmd(`deathreset ${name}`); await sleep(1500);
      step('chat', chat.filter(m => /салахгүй|устгах|хаях|өгөх/.test(m)));
      cmd(`kill @e[tag=gq]`); cmd(`setblock ${p.x + 2} ${p.y} ${p.z} air`); cmd(`setblock ${p.x - 2} ${p.y} ${p.z} air`); cmd(`setblock ${p.x} ${p.y} ${p.z + 2} air`); cmd(`setblock ${p.x} ${p.y} ${p.z - 2} air`);
    } else if (phase === 'frame') {
      cmd(`setblock ${p.x} ${p.y} ${p.z + 2} stone`); cmd(`minecraft:give ${name} item_frame 1`); await sleep(1000);
      const fs_ = bot.inventory.slots.findIndex(s => s && s.name === 'item_frame');
      await bot.clickWindow(fs_, 2, 2).catch(() => {}); await sleep(400); bot.setQuickBarSlot(2); await sleep(400);
      const wall = bot.blockAt(new Vec3(p.x, p.y, p.z + 2));
      await bot.lookAt(new Vec3(p.x + 0.5, p.y + 0.5, p.z + 2)); await bot.activateBlock(wall, new Vec3(0, 0, -1)).catch(e => step('placeErr', String(e))); await sleep(1200);
      await hold();
      cmd('tag @e[type=item_frame,distance=..0] add gf'); cmd(`execute at ${name} run tag @e[type=item_frame,distance=..5] add gf`); await sleep(300);
      const fr = Object.values(bot.entities).filter(e => e.name === 'item_frame');
      step('frames', fr.length);
      for (const e of fr) { await bot.lookAt(e.position); bot.activateEntity(e); await sleep(800); }
      step('frame.server', await server(`execute as @e[tag=gf] run data get entity @s`, /entity data/));
      step('kept', countW()); cmd('kill @e[tag=gf]');
    } else if (phase === 'recover') {
      step('has', countW());
      chat.length = 0; bot.chat('/classgear recover'); await sleep(1200); step('recover.whileHeld', chat.slice(-1));
      const ids = async () => (await server(`data get entity ${name} Inventory`, /entity data/)).join(' ').match(/item_uuid": "[0-9a-f-]+", "suld:item_id": "weapon[^"]*|item_uuid": "[0-9a-f-]+/g);
      step('uuid.before', await ids());
      cmd(`minecraft:clear ${name} iron_sword`); await sleep(1000); step('afterClear', countW());
      chat.length = 0; bot.chat('/classgear recover'); await sleep(1500); step('recover.lost', chat.slice(-1)); step('afterRecover', countW());
      step('uuid.after', await ids());
      // staff path bypasses the player cooldown and is idempotent
      cmd(`minecraft:clear ${name} iron_sword`); await sleep(800);
      chat.length = 0; bot.chat('/classgear recover'); await sleep(1200); step('recover.cooldown', chat.slice(-1));
      step('staff', await server(`classgear recover ${name}`, /сэргээ|Ангийн|зэвсг/));
      step('staff.again', await server(`classgear recover ${name}`, /сэргээ|Ангийн|зэвсг/));
      step('afterStaff', countW()); step('uuid.staff', await ids());
      step('audit', await server(`suld audit tail 3`, /classgear/));
    } else if (phase === 'armor') {
      const armorSlots = () => [5, 6, 7, 8].map(n => bot.inventory.slots[n] && bot.inventory.slots[n].name);
      step('armorSlots', armorSlots());
      const dump = async () => (await server(`data get entity ${name} equipment`, /entity data/)).join(' ');
      let d = await dump();
      step('ids', d.match(/"id":"armor\.class\.[a-z.0-9]+","uuid":"[0-9a-f]{8}[^"]*","r":"[a-z]+","lvl":\d+/g));
      step('assets', d.match(/asset_id: "[^"]+"/g));
      chat.length = 0; bot.chat('/classgear'); await sleep(1200); step('info', chat.slice(-1));
      chat.length = 0; bot.chat('/classgear upgrade'); await sleep(1200); step('upgrade', chat.slice(-2));
      chat.length = 0; bot.chat('/classgear enhance'); await sleep(1200); step('enhance', chat.slice(-1));
      cmd(`suld exp ${name} 20000`); await sleep(1500);
      cmd(`classgear set ${name} level 9`); await sleep(1500);
      d = await dump(); step('afterSetLevel', d.match(/"id":"armor\.class\.[a-z.0-9]+","uuid":"[0-9a-f]{8}[^"]*","r":"[a-z]+","lvl":\d+/g));
      cmd(`classgear set ${name} tier 3`); await sleep(1500);
      d = await dump(); step('afterSetTier', d.match(/"id":"armor\.class\.[a-z.0-9]+","uuid":"[0-9a-f]{8}[^"]*","r":"[a-z]+","lvl":\d+/g)); step('assets3', d.match(/asset_id: "[^"]+"/g));
      // recover: lose the helmet
      cmd(`minecraft:item replace entity ${name} armor.head with air`); await sleep(1000);
      step('afterLoss', armorSlots());
      chat.length = 0; bot.chat('/classgear recover'); await sleep(1500); step('recover', chat.slice(-2)); step('afterRecover', armorSlots());
      d = await dump(); step('afterRecoverIds', d.match(/"id":"armor\.class\.[a-z.0-9]+","uuid":"[0-9a-f]{8}/g));
      // death keeps it
      cmd(`kill ${name}`); await sleep(1500); bot.respawn ? bot.respawn() : bot._client.write('client_command', { actionId: 0 }); await sleep(3000);
      step('afterDeath', armorSlots());
      cmd(`deathreset ${name}`); await sleep(1500);
      // two active minutes: fight zombies
      cmd(`execute at ${name} run summon zombie ~2 ~ ~ {Tags:["gz"],NoAI:1b}`); await sleep(800);
      const end = Date.now() + 130000;
      while (Date.now() < end) {
        const z = Object.values(bot.entities).find(e => e.name === 'zombie' && e.position.distanceTo(bot.entity.position) < 6);
        if (!z) { cmd(`execute at ${name} run summon zombie ~2 ~ ~ {Tags:["gz"],NoAI:1b}`); await sleep(1500); continue; }
        await bot.lookAt(z.position.offset(0, 1.5, 0)); bot.attack(z); await sleep(450 + Math.floor(Math.random() * 300));
      }
      cmd('minecraft:kill @e[tag=gz]');
      chat.length = 0; bot.chat('/classgear'); await sleep(1200); step('infoAfterPlay', chat.slice(-1));
    } else if (phase === 'afk') {
      chat.length = 0; bot.chat('/classgear'); await sleep(1500); step('before', chat.slice(-1)[0].split('\n').pop());
      await sleep(135000);
      chat.length = 0; bot.chat('/classgear'); await sleep(1500); step('after', chat.slice(-1)[0].split('\n').pop());
    } else if (phase === 'info') {
      chat.length = 0; bot.chat('/classgear'); await sleep(1500); step('info', chat.slice(-1));
    } else if (phase === 'check') {
      step('has', countW());
    } else if (phase === 'idle') {
      await hold(); step('has', countW()); await sleep(25000); step('hasAfter', countW());
    } else if (phase === 'foreign') {
      cmd(`minecraft:item replace entity ${name} inventory.5 from entity GearOne weapon.mainhand`); await sleep(1500);
      step('weaponsBefore', countW());
      cmd(`suld exp ${name} 20000`); await sleep(3000);
      const d = (await server(`data get entity ${name} Inventory`, /entity data/)).join(' ');
      step('defs', (d.match(/"id":"weapon\.class\.[a-z]+\.\d","uuid":"[0-9a-f-]{8}[^"]*","r":"[a-z]+","lvl":\d+[^']*?"bt":"[0-9a-f-]{8}/g) || []).map(x => x.replace(/"s":.*"bt"/, '"bt"')));
      step('weaponsAfter', countW());
    }
  } catch (e) { console.log('ERR', e && e.stack || e); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 300000);
