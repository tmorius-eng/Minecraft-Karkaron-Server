// node cmdbot.js <name> <port> <cmd1|cmd2|...>: join, run each command 2.5 s apart, print chat, quit.
const mineflayer = require('mineflayer');
const [name, port, cmds] = process.argv.slice(2);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const out = { name, chat: [], windows: [] };
bot.on('messagestr', (m) => { if (m.trim()) out.chat.push(m); });
bot.on('windowOpen', (w) => { out.windows.push(String(w.title)); });
bot.once('spawn', async () => {
  const list = (cmds || '').split('|').filter(Boolean);
  await new Promise(r => setTimeout(r, 4000));
  for (const c of list) { out.chat.push('>>> ' + c); bot.chat(c); await new Promise(r => setTimeout(r, 2500)); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); }
  const p = bot.entity.position; out.pos = [p.x, p.y, p.z].map(v => Math.round(v));
  bot.quit();
});
bot.on('end', () => { console.log(JSON.stringify(out, null, 1)); process.exit(0); });
bot.on('kicked', (r) => { out.kicked = String(r); });
setTimeout(() => { out.timeout = true; console.log(JSON.stringify(out, null, 1)); process.exit(0); }, 90000);
