// Persistent death lock: phase "die" (die, check soul, quit), phase "back" (rejoin after restart: lock kept, admin revive),
// phase "natural" (a level-1 death whose 30 s lock runs out by itself).
const mineflayer = require('mineflayer'); const fs = require('fs'); const { execFileSync } = require('child_process');
const ctl = process.argv[2], phase = process.argv[3], name = process.argv[4] || 'Lock1';
const LOG = '/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2/logs/latest.log';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const logLen = () => fs.readFileSync(LOG, 'utf8').split('\n').length;
const logFrom = (n) => fs.readFileSync(LOG, 'utf8').split('\n').slice(n).map(l => l.replace(/^\[[\d:]+\] \[[^\]]+\]: /, ''));
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const out = { phase, titles: [], subtitles: [], bossbars: [], chat: [], checks: {} };
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot._client.on('set_title_text', (pk) => out.titles.push(flat(pk.text)));
bot._client.on('set_title_subtitle', (pk) => out.subtitles.push(flat(pk.text)));
bot._client.on('boss_bar', (pk) => { if (pk.title) out.bossbars.push(flat(pk.title)); });
bot._client.on('system_chat', (pk) => { if (!(pk.isActionBar || pk.overlay)) out.chat.push(flat(pk.content)); });
async function status(tag) {
  const n = logLen(); cmd(`deathstatus ${name}`); await sleep(1500);
  out.checks[tag] = logFrom(n).filter(l => l.includes('☠') || l.includes('#')).slice(0, 6);
}
bot.once('spawn', async () => {
  await sleep(5000);
  if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(1500); }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(2000);
  if (phase === 'die' || phase === 'natural') {
    if (phase === 'die') { cmd(`suld exp ${name} 30000`); await sleep(1500); }
    cmd(`minecraft:kill ${name}`); await sleep(1500);
    bot._client.write('client_command', { actionId: 0 }); await sleep(3000);
    const hp = bot.health; cmd(`minecraft:damage ${name} 6 minecraft:generic`); await sleep(1000);
    out.checks.soulTakesNoDamage = bot.health === hp;
    cmd(`minecraft:summon item ~ ~ ~ {Item:{id:"minecraft:diamond",count:1}}`); // cannot be picked up by a soul… (summoned at console origin)
    await status('afterDeath');
    if (phase === 'natural') {
      await sleep(36000);
      await status('afterExpiry');
    }
  } else if (phase === 'back') {
    await sleep(2000);
    await status('afterRestart');
    const hp = bot.health; cmd(`minecraft:damage ${name} 4 minecraft:generic`); await sleep(1000);
    out.checks.stillSoulAfterRestart = bot.health === hp;
    cmd(`deathrevive ${name}`); await sleep(2500);
    const hp2 = bot.health; cmd(`minecraft:damage ${name} 4 minecraft:generic`); await sleep(1000);
    out.checks.mortalAfterRevive = bot.health < hp2;
    await status('afterRevive');
  } else if (phase === 'reset') {
    cmd(`deathreset ${name}`); await sleep(2500);
    await status('afterReset');
  }
  bot.quit();
});
bot.on('end', () => { console.log(JSON.stringify(out, null, 1)); process.exit(0); });
setTimeout(() => { console.log(JSON.stringify(out, null, 1)); process.exit(1); }, 120000);
