const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: 'Scout', version: '1.21.11', auth: 'offline', hideErrors: true });
const pts = [[-16,-300],[-300,-46],[-16,240],[-250,200],[200,-260],[-250,-260],[230,180],[-400,-46],[-16,-450]];
bot.once('spawn', async () => {
  execFileSync(process.argv[2], ['cmd', 'gamemode spectator Scout']);
  for (const [x, z] of pts) {
    execFileSync(process.argv[2], ['cmd', `tp Scout ${x} 200 ${z}`]);
    await new Promise(r => setTimeout(r, 4000));
    let y = 199, name = null;
    for (; y > 40; y--) { const b = bot.blockAt(bot.entity.position.offset(0, y - bot.entity.position.y, 0)); if (b && b.name !== 'air' && b.name !== 'void_air' && b.name !== 'cave_air') { name = b.name; break; } }
    console.log(x, z, '->', y, name);
  }
  bot.quit(); process.exit(0);
});
