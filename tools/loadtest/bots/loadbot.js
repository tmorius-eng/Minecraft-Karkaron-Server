// Load generator for the performance baseline (local dev server only).
//   node loadbot.js wander <count> <seconds> <prefix>   count players join, pick a class, wander (walk/turn/jump)
//   node loadbot.js churn  <count> <prefix>             count sequential join -> 4 s -> quit cycles (profile load/save)
const mineflayer = require('mineflayer');
const [mode, countS, a3, a4] = process.argv.slice(2);
const count = Number(countS);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
function join(name) {
  return new Promise((resolve) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
    bot.on('error', () => {});
    bot.once('spawn', async () => {
      await sleep(3500);
      if (bot.currentWindow) { await bot.clickWindow(10 + (name.length % 5) * 0, 0, 0).catch(() => {}); await sleep(800); }
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
      resolve(bot);
    });
    bot.once('end', () => resolve(null));
    setTimeout(() => resolve(null), 30000);
  });
}
async function wander(bot, secs) {
  const end = Date.now() + secs * 1000;
  bot.setControlState('forward', true);
  while (Date.now() < end && bot.entity) {
    await bot.look(Math.random() * Math.PI * 2, 0, true).catch(() => {});
    bot.setControlState('jump', Math.random() < 0.3);
    bot.setControlState('sprint', Math.random() < 0.5);
    await sleep(1500 + Math.random() * 2000);
  }
  bot.clearControlStates();
}
(async () => {
  if (mode === 'wander') {
    const secs = Number(a3), prefix = a4 || 'Load';
    const bots = [];
    for (let i = 0; i < count; i++) { bots.push(await join(prefix + i)); await sleep(500); }
    const live = bots.filter(Boolean);
    console.log('joined ' + live.length + '/' + count);
    await Promise.all(live.map(b => wander(b, secs)));
    live.forEach(b => b.quit());
  } else if (mode === 'churn') {
    const prefix = a3 || 'Churn'; let ok = 0;
    for (let i = 0; i < count; i++) { const b = await join(prefix + i); if (b) { ok++; await sleep(500); b.quit(); } await sleep(1200); }
    console.log('churn cycles ' + ok + '/' + count);
  }
  await sleep(1500); process.exit(0);
})();
