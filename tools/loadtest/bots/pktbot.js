const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: 'Pkt1', version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
const seen = {};
bot._client.on('packet', (d, meta) => { if (/action|chat|title|boss/.test(meta.name)) { seen[meta.name] = (seen[meta.name] || 0) + 1; if (seen[meta.name] <= 2) console.log(meta.name, JSON.stringify(d).slice(0, 300)); } });
bot.on('actionBar', (m) => { console.log('ACTIONBAR EVT', m.toString().length); });
bot.once('spawn', async () => { await new Promise(r => setTimeout(r, 4000)); if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); } await new Promise(r => setTimeout(r, 6000)); console.log(JSON.stringify(seen)); bot.quit(); });
bot.on('end', () => process.exit(0));
