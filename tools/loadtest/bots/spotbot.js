const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl, ox, oz] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const N = 'spot' + (Date.now() % 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: N, version: '1.21.11', auth: 'offline', hideErrors: true });
const cands = JSON.parse(process.argv[5]);
bot.once('spawn', async () => {
  await sleep(3000);
  cmd(`gamemode spectator ${N}`);
  console.log('spawn', bot.entity.position.floored().toString());
  for (const [dx, dz, r] of cands) {
    const x = +ox + dx, z = +oz + dz;
    cmd(`tp ${N} ${x} 140 ${z}`); await sleep(2500);
    let ground = null, blocked = 0, total = 0, gy = [];
    for (let ix = -r; ix <= r; ix++) for (let iz = -r; iz <= r; iz++) {
      let y = 130; while (y > -60) { const b = bot.blockAt(bot.entity.position.offset(0, 0, 0).floored().set(x + ix, y, z + iz)); if (b && b.name !== 'air' && b.name !== 'cave_air' && b.name !== 'void_air') break; y--; }
      const b = bot.blockAt(bot.entity.position.floored().set(x + ix, y, z + iz)); gy.push(y);
      total++; if (b && !/grass_block|dirt|stone|gravel|sand|path|coarse|podzol|mud|smooth_stone|stone_bricks|polished|andesite|cobble|packed|terracotta|snow/.test(b.name)) blocked++;
    }
    const min = Math.min(...gy), max = Math.max(...gy);
    console.log(`(${dx},${dz}) r${r}: ground ${min}..${max}, non-ground tops ${blocked}/${total}`);
  }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => { console.log('timeout'); process.exit(1); }, 120000);
