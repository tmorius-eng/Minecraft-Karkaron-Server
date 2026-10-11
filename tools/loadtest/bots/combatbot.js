const mineflayer = require('mineflayer'); const fs = require('fs'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const slot = +process.argv[3] || 10; const path = (process.argv[4] || '').split(','); const mods = (process.argv[5] || '').split(',').filter(Boolean);
const name = 'Cb' + Math.floor(Math.random() * 10000);
const LOG = '/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2/logs/latest.log';
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const P = (s) => console.log(s);
const bars = []; for (const ev of ['action_bar', 'set_action_bar_text']) bot._client.on(ev, (pk) => { const s = flat(pk.text); if (bars[bars.length - 1] !== s) bars.push(s); });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
const effects = () => Object.values(bot.entity.effects || {}).map(e => e.id + ':' + (e.amplifier + 1) + '/' + e.duration).join(',');
const mobs = () => Object.values(bot.entities).filter(e => e.type !== 'player' && ['wolf', 'polar_bear', 'zombie', 'skeleton', 'spider'].includes(e.name));
bot.once('spawn', async () => {
  try {
    await sleep(5000);
    if (bot.currentWindow) await bot.clickWindow(slot, 0, 0).catch(() => {}); await sleep(1500);
    cmd('suld exp ' + name + ' 9000000'); cmd('gamemode survival ' + name); cmd('effect give ' + name + ' minecraft:resistance 600 4 true');
    for (const n of path.concat(mods)) { cmd('skillsadmin unlock ' + name + ' ' + n); await sleep(350); }
    cmd('tp ' + name + ' -16 90 -440'); await sleep(4500);
    const hold = bot.inventory.slots.findIndex(i => i && /_sword$|^bow$|^trident$|_axe$|^mace$|^stick$|_pickaxe$|^blaze_rod$/.test(i.name) && i.customName);
    const w = hold >= 0 ? hold : bot.inventory.slots.findIndex(i => i && /sword|bow|trident|axe|blaze|stick|mace/.test(i.name));
    P('weapon slot ' + w + ' ' + (bot.inventory.slots[w] && bot.inventory.slots[w].name)); if (w >= 36) bot.setQuickBarSlot(w - 36);
    await sleep(500);
    for (let k = 0; k < 20 && mobs().filter(m => m.position.distanceTo(bot.entity.position) < 10).length === 0; k++) await sleep(1000);
    const near = () => mobs().sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0];
    P('mobs near: ' + mobs().length);
    if (near()) await bot.lookAt(near().position.offset(0, 0.5, 0));
    // F = swap hands
    bars.length = 0; bot._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 }); await sleep(1500);
    P('after F: effects=' + effects() + ' bars=' + bars.slice(-3).join(' / '));
    bars.length = 0; bot._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 }); await sleep(900);
    P('second F (cooldown): bars=' + bars.slice(-2).join(' / '));
    const R = () => bot.activateItem(), L = () => bot.swingArm('right');
    if (near()) await bot.lookAt(near().position.offset(0, 0.5, 0));
    for (const combo of ['RLR', 'RRR', 'RLL', 'RRL']) { for (const c of combo) { (c === 'R' ? R : L)(); await sleep(250); if (c === 'R') bot.deactivateItem(); } await sleep(1300); }
    P('after spells: bars=' + bars.slice(-4).join(' / '));
    // melee swings for HIT procs
    for (let i = 0; i < 12 && near(); i++) { const m = near(); await bot.lookAt(m.position.offset(0, 0.5, 0)); if (m.position.distanceTo(bot.entity.position) < 4) bot.attack(m); await sleep(500); }
    P('final bars: ' + bars.slice(-2).join(' / '));
    cmd('skillsadmin inspect ' + name); await sleep(1500);
    bot.quit();
  } catch (e) { P('ERROR ' + e.stack); bot.quit(); }
});
bot.on('end', () => { console.log('NAME ' + name); process.exit(0); });
setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 110000);
