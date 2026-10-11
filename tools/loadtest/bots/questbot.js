const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const ctl = process.argv[2];
const name = 'Saran' + Math.floor(Math.random() * 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const out = { name, chat: [], titles: [], bars: [], kills: 0 };
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
bot._client.on('set_title_subtitle', (pk) => out.titles.push(flat(pk.text)));
bot._client.on('system_chat', (pk) => { const s = flat(pk.content); if (pk.isActionBar || pk.overlay) { if (s.includes('Эрэл')) out.bars.push(s); } else out.chat.push(s); });
const mark = (t) => out.chat.push('### ' + t);
async function hunt(want, ms) {
  const end = Date.now() + ms; let last = null;
  while (Date.now() < end) {
    const t = Object.values(bot.entities).filter(e => e.name === want && e.position.distanceTo(bot.entity.position) < 40)
      .sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0];
    if (!t) { bot.clearControlStates(); await sleep(500); continue; }
    await bot.lookAt(t.position.offset(0, 0.4, 0), true);
    const d = t.position.distanceTo(bot.entity.position);
    bot.setControlState('forward', d > 2.2); bot.setControlState('sprint', d > 4); bot.setControlState('jump', d > 2.2 && bot.entity.isCollidedHorizontally);
    if (d < 3.2) bot.attack(t);
    await sleep(350);
    if (!Object.values(bot.entities).some(e => e.name === want && e.position.distanceTo(bot.entity.position) < 40)) break;
  }
  bot.clearControlStates();
}
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(2000);
  mark('after class'); bot.chat('/quest'); await sleep(1500);
  cmd('effect give ' + name + ' minecraft:resistance 600 4 true');
  cmd('suld exp ' + name + ' 2000000'); await sleep(1500);
  cmd('spreadplayers 160 0 0 1 false ' + name); await sleep(3000);
  const sw = bot.inventory.slots.findIndex(i => i && /sword|rod|axe|bow/.test(i.name)); if (sw >= 36) bot.setQuickBarSlot(sw - 36);
  cmd('op ' + name); await sleep(500);
  mark('hunting');
  for (let i = 0; i < 3; i++) { bot.chat('/suld spawnmob'); await sleep(800); cmd('execute as ' + name + ' at @s run damage @e[type=wolf,limit=1,sort=nearest] 1000 minecraft:player_attack by @s'); await sleep(1500); }
  await hunt('wolf', 1);
  await sleep(1500);
  mark('ch2 now'); bot.chat('/quest'); await sleep(1500);
  cmd('suld quest ' + name + ' 4'); await sleep(2000);
  mark('after set 4'); bot.chat('/quest'); await sleep(1500);
  cmd('suld quest ' + name + ' 6'); await sleep(1000);
  cmd('effect give ' + name + ' minecraft:slow_falling 60 0 true'); cmd('tp ' + name + ' 0 140 320'); await sleep(6000);
  mark('after gobi'); bot.chat('/quest'); await sleep(1500);
  bot.quit();
});
bot.on('end', () => { console.log(JSON.stringify(out, null, 1)); process.exit(0); });
setTimeout(() => { console.log(JSON.stringify(out, null, 1)); process.exit(0); }, 150000);
