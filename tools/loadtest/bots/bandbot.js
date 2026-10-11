// node bandbot.js <ctl> <port> <x> <z> <chapter>: pick a class, set the chapter, stand in the region, count prey + tracker text.
const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl, port, x, z, ch] = process.argv.slice(2);
const name = 'Band' + Math.floor(Math.random() * 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const out = { name, chat: [], bars: {}, samples: [] };
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
bot._client.on('boss_bar', (pk) => { if (pk.title) out.bars[pk.entityUUID] = flat(pk.title); });
bot.on('messagestr', (m) => { if (/Эрэл|эрэл|→/.test(m)) out.chat.push(m); });
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(2000);
  cmd('suld quest ' + name + ' ' + ch); await sleep(1500);
  cmd('effect give ' + name + ' minecraft:resistance 600 4 true');
  cmd('effect give ' + name + ' minecraft:slow_falling 120 0 true'); cmd('minecraft:tp ' + name + ' ' + x + ' 140 ' + z); await sleep(9000);
  for (let i = 0; i < 6; i++) {
    await sleep(6000);
    const near = Object.values(bot.entities).filter(e => e !== bot.entity && e.position.distanceTo(bot.entity.position) < 48);
    const kinds = {}; near.forEach(e => kinds[e.name] = (kinds[e.name] || 0) + 1);
    const p = bot.entity.position;
    out.samples.push({ t: (i + 1) * 6, pos: [p.x, p.y, p.z].map(Math.round), kinds, bars: Object.values(out.bars) });
  }
  bot.quit();
});
bot.on('end', () => { console.log(JSON.stringify(out, null, 1)); process.exit(0); });
setTimeout(() => { console.log(JSON.stringify(out, null, 1)); process.exit(0); }, 90000);
