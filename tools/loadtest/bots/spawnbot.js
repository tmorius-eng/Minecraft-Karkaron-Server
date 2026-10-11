// node spawnbot.js <name> <port>: join, report spawn position and the blocks under/around it, then quit.
const mineflayer = require('mineflayer');
const [name, port] = process.argv.slice(2);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const out = { name, chat: [] };
bot.on('messagestr', (m) => out.chat.push(m));
bot.once('spawn', () => setTimeout(() => {
  const p = bot.entity.position;
  out.pos = [p.x, p.y, p.z].map(v => Math.round(v * 10) / 10);
  out.yaw = Math.round(bot.entity.yaw * 180 / Math.PI);
  const b = (dx, dy, dz) => { const k = bot.blockAt(p.offset(dx, dy, dz)); return k ? k.name : null; };
  out.floor = b(0, -1, 0); out.feet = b(0, 0, 0); out.head = b(0, 1, 0);
  out.monumentAhead = b(0, 5, -18);
  bot.quit();
}, 6000));
bot.on('end', () => { console.log(JSON.stringify(out)); process.exit(0); });
bot.on('error', (e) => { out.error = String(e.message || e); });
setTimeout(() => { out.timeout = true; console.log(JSON.stringify(out)); process.exit(0); }, 40000);
