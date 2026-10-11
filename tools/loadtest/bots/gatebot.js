// node gatebot.js <ctl> <port> <x> <y> <z>: stand by the gate as a survival player and try to dig its blocks; report.
const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process'); const { Vec3 } = require('vec3');
const [ctl, port, gx, gy, gz] = process.argv.slice(2).map((v, i) => i < 2 ? v : Number(v));
const name = 'Gate' + Math.floor(Math.random() * 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const out = { name, tried: [], bars: [], names: [] };
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
bot.on('actionBar', (m) => out.bars.push(String(m)));
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(1500);
  cmd('minecraft:tp ' + name + ' ' + (gx + 0.5) + ' ' + (gy + 1) + ' ' + (gz + 3.5)); await sleep(3000);
  cmd('minecraft:gamemode survival ' + name); cmd('effect give ' + name + ' minecraft:haste 60 5 true'); await sleep(500);
  const p = bot.entity.position.floored();
  const blocks = [];
  for (let dx = -3; dx <= 3; dx++) for (let dy = -1; dy <= 3; dy++) for (let dz = -3; dz <= 3; dz++) {
    const b = bot.blockAt(p.offset(dx, dy, dz)); if (b && b.name !== 'air' && b.diggable && blocks.length < 3 && bot.canDigBlock(b)) blocks.push(b);
  }
  for (const b of blocks) {
    const before = b.name;
    try { await bot.dig(b, true); } catch (e) {}
    await sleep(1200);
    out.tried.push({ at: [b.position.x, b.position.y, b.position.z], before, after: bot.blockAt(b.position).name });
  }
  // nameplates: spawn a SÜLD wolf next to the bot and read its custom name
  cmd('op ' + name); await sleep(500); bot.chat('/suld spawnmob'); await sleep(2500); for (const e of Object.values(bot.entities)) if (e.name==='wolf') { bot.attack(e); } await sleep(1500);
  for (const e of Object.values(bot.entities)) {
    const meta = e.metadata && e.metadata[2];
    if (meta && e !== bot.entity) out.names.push(JSON.stringify(meta).slice(0, 220));
  }
  bot.quit();
});
bot.on('end', () => { console.log(JSON.stringify(out, null, 1)); process.exit(0); });
setTimeout(() => { console.log(JSON.stringify(out, null, 1)); process.exit(0); }, 60000);
