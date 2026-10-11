const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const name = 'Ph' + Math.floor(Math.random() * 10000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.once('spawn', async () => {
  await sleep(5000); if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(1500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  console.log('physics', bot.physicsEnabled, 'pos', bot.entity.position.toString());
  execFileSync(ctl, ['cmd', 'forceload add 2990 2990 3020 3020']); execFileSync(ctl, ['cmd', 'fill 2995 199 2995 3015 199 3015 stone']); await sleep(1500); execFileSync(ctl, ['cmd', `tp ${name} 3000.5 200 3000.5`]); await sleep(3000);
  console.log('after tp', bot.entity.position.toString(), 'col', !!bot.world.getColumnAt(bot.entity.position));
  execFileSync(ctl, ['cmd', `skillsadmin qakit ${name} rich`]); await sleep(1000);
  console.log('onGround', bot.entity.onGround, 'pos', bot.entity.position.toString()); bot.entity.velocity.set(0, 0.15, 1.6); await sleep(1000); console.log('after manual velocity', bot.entity.position.toString());
  bot.setControlState('forward', true); await sleep(1000); bot.setControlState('forward', false);
  console.log('after walk', bot.entity.position.toString());
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 40000);
