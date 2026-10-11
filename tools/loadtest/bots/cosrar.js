const mineflayer = require('mineflayer'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const name = 'Cr' + Math.floor(Math.random() * 1000);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25572, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms)); const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const log = [];
bot._client.on('system_chat', (pk) => { const s = flat(pk.content); if (/кредит|Кредит|зоос|Зоос|авлаа|Худалдаж|зүүлээ/.test(s)) log.push('chat: ' + s); });
const lore = (slot) => { const it = bot.currentWindow && bot.currentWindow.slots[slot]; return it ? JSON.stringify(it.components || it.nbt || '').replace(/\\u[0-9a-f]{4}/g, '') : ''; };
bot.once('spawn', async () => {
  await sleep(5000); if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(2500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  cmd('suld coins ' + name + ' 5000'); await sleep(800);
  bot.chat('/cosmetics'); await sleep(1500);
  await bot.clickWindow(11, 0, 0); await sleep(1500);
  for (const s of [0, 3]) { const l = lore(s); log.push('slot ' + s + ' hint: ' + ((l.match(/(Зүүн:[^"]*|Дарж[^"]*кредитээр[^"]*|Зөвхөн кредитээр[^"]*)/g) || []).join(' | '))); }
  await bot.clickWindow(3, 0, 0); await sleep(1500); // Талын (RARE): left click = coins
  await bot.clickWindow(0, 0, 0); await sleep(1500); // Улаан (COMMON): left click = coins
  bot.chat('/balance'); await sleep(1000);
  console.log(log.join('\n')); bot.quit(); process.exit(0);
});
setTimeout(() => { console.log(log.join('\n')); process.exit(1); }, 60000);
