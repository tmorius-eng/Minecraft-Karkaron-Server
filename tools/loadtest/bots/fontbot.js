const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: 'Ft' + Math.floor(Math.random() * 1000), version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
// walk an NBT text component: report every text run with its effective font (children inherit the parent's font)
const walk = (c, font, out) => { if (c == null) return; if (typeof c === 'string') { out.push([font, c]); return; } const v = c.value || c; let f = font; if (v.font) f = v.font.value ?? v.font; if (v.text) out.push([f, String(v.text.value ?? v.text)]); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => walk(e.value ? e : { value: e }, f, out)); };
const bad = []; const seen = [];
const check = (label, comp) => { const runs = []; walk(comp, 'default', runs); for (const [f, t] of runs) { seen.push(label + ' [' + f + '] ' + JSON.stringify(t).slice(0, 40)); if (String(f).includes('suld:ui') && /[0-9A-Za-zА-Яа-яӨөҮү]/.test(t)) bad.push(label + ': "' + t + '" in font ' + f); } };
bot._client.on('action_bar', (pk) => check('actionbar', pk.text));
bot._client.on('scoreboard_score', (pk) => { if (pk.display_name || pk.displayName) check('sidebar', pk.display_name || pk.displayName); });
bot.once('spawn', async () => {
  await sleep(5000); if (bot.currentWindow) await bot.clickWindow(10, 0, 0).catch(() => {});
  await sleep(6000);
  console.log('runs seen:', seen.length); console.log(seen.slice(0, 14).join('\n')); console.log(bad.length ? 'BAD:\n' + [...new Set(bad)].join('\n') : 'OK: no text inherits the pixel font');
  bot.quit(); process.exit(0);
});
setTimeout(() => process.exit(1), 40000);
