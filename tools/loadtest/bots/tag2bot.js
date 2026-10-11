const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const [ctl] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const N = 'tg' + (Date.now() % 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: N, version: '1.21.11', auth: 'offline', hideErrors: true });
const cps = (o) => [...JSON.stringify(o)].filter(c => c >= '' && c <= '').map(c => c.charCodeAt(0).toString(16)).concat([...JSON.stringify(o).matchAll(/\\u(e[0-9a-f]{3})/gi)].map(m => m[1]));
const team = () => Object.values(bot.teams || {}).find(t => t.members && t.members.includes(N));
bot.once('spawn', async () => {
  await sleep(5000);
  console.log('before: prefix', cps(team()?.prefix));
  cmd(`suld badge ${N} streamer`); await sleep(2000);
  console.log('after badge: prefix', cps(team()?.prefix), 'suffix', cps(team()?.suffix));
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => process.exit(1), 40000);
