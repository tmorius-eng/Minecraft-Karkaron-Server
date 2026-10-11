const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const ctl = process.argv[2];
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: 'Batbayar', version: '1.21.11', auth: 'offline', hideErrors: true });
const out = { bars: [], chat: [], mobsBefore: [], mobsAfter: [] };
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
for (const ev of ['action_bar', 'set_action_bar_text']) bot._client.on(ev, (pk) => { const s = flat(pk.text); if (out.bars[out.bars.length - 1] !== s) out.bars.push(s); });
bot._client.on('system_chat', (pk) => { if (pk.isActionBar || pk.overlay) { const s = flat(pk.content); if (out.bars[out.bars.length - 1] !== s) out.bars.push(s); } });
bot.on('messagestr', (m) => { if (m.trim()) out.chat.push(m); });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
const mobs = () => Object.values(bot.entities).filter(e => e.type !== 'player' && ['wolf', 'polar_bear', 'zombie', 'skeleton', 'spider', 'creeper'].includes(e.name))
  .map(e => e.name + '@' + Math.round(e.position.distanceTo(bot.entity.position)) + ':hp' + (e.metadata && e.metadata[9]));
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});   // Баатар
  await sleep(1500);
  execFileSync(ctl, ['cmd', 'suld exp Batbayar 9000000']);
  execFileSync(ctl, ['cmd', 'gamemode survival Batbayar']);
  execFileSync(ctl, ['cmd', 'effect give Batbayar minecraft:resistance 300 4 true']);
  execFileSync(ctl, ['cmd', 'tp Batbayar -16 90 -440']);
  await sleep(4000);
  const sword = bot.inventory.slots.findIndex(i => i && i.name === 'iron_sword');
  if (sword >= 36) bot.setQuickBarSlot(sword - 36);
  for (let w = 0; w < 25 && mobs().filter(m => +m.split('@')[1].split(':')[0] < 8).length === 0; w++) await sleep(1000);
  out.mobsBefore = mobs();
  const near = Object.values(bot.entities).filter(e => ['wolf', 'polar_bear'].includes(e.name)).sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0];
  if (near) await bot.lookAt(near.position.offset(0, 0.5, 0));
  const R = () => bot.activateItem(), L = () => bot.swingArm('right');
  for (const combo of ['RLR', 'RRR', 'RLL']) {
    for (const c of combo) { (c === 'R' ? R : L)(); await sleep(250); if (c === 'R') bot.deactivateItem(); }
    await sleep(1500);
  }
  out.mobsAfter = mobs();
  bot.quit();
});
bot.on('end', () => { console.log(JSON.stringify(out, null, 1)); process.exit(0); });
setTimeout(() => { console.log(JSON.stringify(out, null, 1)); process.exit(0); }, 90000);
