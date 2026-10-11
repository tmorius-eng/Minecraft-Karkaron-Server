const mineflayer = require('mineflayer'); const { Vec3 } = require('vec3');
const { execFileSync } = require('child_process');
const [ctl, port, x, y, z] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: +port, username: 'Gp' + Math.floor(Math.random() * 900 + 100), version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('messagestr', (m) => console.error('chat:', m));
bot.once('spawn', async () => {
  await sleep(4000);
  for (let i = 0; i < 10 && !bot.currentWindow; i++) await sleep(500);
  if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(1200); }
  execFileSync(ctl, ['cmd', `suld exp ${bot.username} 400000`]); await sleep(1000);
  execFileSync(ctl, ['cmd', `minecraft:tp ${bot.username} ${+x + 0.5} ${y} ${+z + 0.5}`]); await sleep(3500);
  for (let dy = -8; dy <= 1; dy++) { const b = bot.blockAt(bot.entity.position.offset(0, dy, 0)); console.error('dy', dy, b && b.name); }
  console.error('pos', bot.entity.position, 'onGround', bot.entity.onGround, 'vehicle', !!bot.vehicle);
  bot.chat('/mori'); await sleep(2000);
  console.error('vehicle after /mori', !!bot.vehicle);
  bot.quit(); setTimeout(() => process.exit(0), 500);
});
