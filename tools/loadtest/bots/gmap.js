const mineflayer = require('mineflayer'); const { Vec3 } = require('vec3');
const { execFileSync } = require('child_process');
const [ctl, port, x, y, z, r] = process.argv.slice(2);
const sleep = (ms) => new Promise(res => setTimeout(res, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: +port, username: 'Gm' + Math.floor(Math.random() * 900 + 100), version: '1.21.11', auth: 'offline', hideErrors: true });
bot.once('spawn', async () => {
  await sleep(4000);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  execFileSync(ctl, ["cmd", `effect give ${bot.username} minecraft:levitation 30 255 true`]); execFileSync(ctl, ["cmd", `minecraft:tp ${bot.username} ${x} ${+y + 30} ${z}`]); await sleep(12000);
  const R = +r || 12; const rows = [];
  for (let dz = -R; dz <= R; dz += 2) {
    let row = '';
    for (let dx = -R; dx <= R; dx += 2) {
      let ch = '?';
      for (let yy = +y + 20; yy > +y - 20; yy--) {
        const b = bot.blockAt(new Vec3(+x + dx, yy, +z + dz)); if (!b) break;
        if (b.boundingBox === 'block' && !/leaves/.test(b.name)) { const h = yy - +y; ch = /grass|dirt|sand|stone$|gravel|snow|podzol|coarse|mud|clay|terracotta|andesite|granite|diorite|tuff/.test(b.name) ? (h > 2 ? '^' : h < -2 ? 'v' : '.') : (/water/.test(b.name) ? '~' : '#'); break; }
        if (/water/.test(b.name)) { ch = '~'; break; }
      }
      row += (dx === 0 && dz === 0) ? 'X' : ch;
    }
    rows.push(row);
  }
  console.log(rows.join('\n'));
  bot.quit(); setTimeout(() => process.exit(0), 300);
});
