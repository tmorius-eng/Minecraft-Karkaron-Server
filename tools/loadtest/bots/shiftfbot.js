const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const N = 'sf' + (Date.now() % 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: N, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
const msgs = []; bot.on('messagestr', m => msgs.push(m)); bot.on('actionBar', m => msgs.push('AB ' + JSON.stringify(m).slice(0, 200)));
const hpOf = (e) => { const s = JSON.stringify(e.metadata && e.metadata[2]); const m = s && s.match(/❤ ?(\d+)\/(\d+)/); return m ? +m[1] : null; };
const mobs = () => Object.values(bot.entities).filter(e => e !== bot.entity && e.position.distanceTo(bot.entity.position) < 8 && hpOf(e) != null);
bot.once('spawn', async () => {
  try {
    await sleep(4000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2500); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    cmd(`skillsadmin qakit ${N} rich`); await sleep(1500);
    cmd(`tp ${N} 640 120 640`); cmd(`effect give ${N} minecraft:resistance 600 4 true`); cmd(`effect give ${N} minecraft:slow_falling 600 0 true`);
    await sleep(8000);
    const slot = bot.inventory.slots.findIndex((it, i) => i >= 36 && i <= 44 && it && /sword|axe|bow|trident|stick|rod|shovel|crossbow/.test(it.name));
    console.log('weapon slot', slot, slot >= 0 ? bot.inventory.slots[slot].name : '');
    if (slot >= 0) bot.setQuickBarSlot(slot - 36);
    await sleep(500);
    cmd(`suld spawnmob ${N} mob.saaral_chono 4`); await sleep(2500);
    const before = mobs().map(e => [e.id, hpOf(e)]);
    console.log('mobs near before', before);
    msgs.length = 0;
    bot.setControlState('sneak', true); await sleep(400);
    const off0 = JSON.stringify(bot.inventory.slots[45]);
    bot._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 });
    await sleep(2500);
    bot.setControlState('sneak', false);
    const after = new Map(mobs().map(e => [e.id, hpOf(e)]));
    console.log('mobs after', before.map(([id, h]) => [id, h, after.has(id) ? after.get(id) : 'gone']));
    console.log('offhand unchanged:', off0 === JSON.stringify(bot.inventory.slots[45]));
    console.log('msgs:', msgs.slice(0, 6));
    // a second Shift+F at once: cooldown refusal
    bot.setControlState('sneak', true); await sleep(300); msgs.length = 0;
    bot._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 }); await sleep(800);
    console.log('recast msgs:', msgs.slice(0, 3));
  } catch (e) { console.log('ERR', e.stack); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => { console.log('timeout'); process.exit(1); }, 90000);
