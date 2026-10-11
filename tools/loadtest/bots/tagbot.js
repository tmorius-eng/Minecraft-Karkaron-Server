const mineflayer = require('mineflayer');
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const N = 'tag' + (Date.now() % 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: N, version: '1.21.11', auth: 'offline', hideErrors: true });
let packUrl = null;
bot._client.on('add_resource_pack', (p) => { packUrl = p.url; });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.once('spawn', async () => {
  await sleep(6000);
  const t = Object.values(bot.teams || {}).find(t => t.members && t.members.includes(N));
  const pre = t ? JSON.stringify(t.prefix) : null;
  const cps = pre ? [...pre.matchAll(/\\u(e0[0-9a-f]{2})|([-])/gi)].map(m => m[1] || m[2].charCodeAt(0).toString(16)) : [];
  console.log('team:', t && t.team, 'prefix glyphs:', cps, 'font suld:ui:', !!pre && pre.includes('suld:ui'));
  console.log('pack url offered:', !!packUrl);
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => { console.log('timeout'); process.exit(1); }, 40000);
