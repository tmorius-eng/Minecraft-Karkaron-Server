const mineflayer = require('mineflayer'); const fs = require('fs'); const { execFileSync } = require('child_process');
const ctl = process.argv[2]; const name = process.argv[3] || ('Sk' + Math.floor(Math.random() * 10000)); const phase = process.argv[4] || 'full';
const LOG = '/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2/logs/latest.log';
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const out = []; const P = (s) => { out.push(s); console.log(s); };
const chat = []; bot.on('messagestr', (m) => { if (m.trim()) chat.push(m); });
const bars = []; for (const ev of ['action_bar', 'set_action_bar_text']) bot._client.on(ev, (pk) => { const s = flat(pk.text); if (bars[bars.length - 1] !== s) bars.push(s); }); bot._client.on('system_chat', (pk) => { if (pk.isActionBar || pk.overlay) { const s = flat(pk.content); if (bars[bars.length - 1] !== s) bars.push(s); } });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const lines = () => fs.readFileSync(LOG, 'utf8').split('\n');
async function con(c, wait = 900) { const n = lines().length - 1; cmd(c); await sleep(wait); return lines().slice(n).filter(l => /SULD|\[Server thread\/INFO\]: (?!.*(UUID|Done))/.test(l)).map(l => l.replace(/^\[[\d:]+\] \[[^\]]+\]: /, '')).join(' | '); }
const title = () => { const w = bot.currentWindow; if (!w) return 'none'; return [...JSON.stringify(w.title).matchAll(/"text":\{"type":"string","value":"([^"]*)"/g)].map(x => x[1]).join('|').replace(/[^\u0000-￿]/g, '?'); };
const nm = (s) => { if (!s) return null; let n = s.displayName; try { if (s.customName) n = flat(s.customName); } catch (e) {} return n; };
const glint = (s) => !!(s && s.components && s.components.some(c => c.type === 'enchantment_glint_override'));
const model = (s) => { const c = s && s.components && s.components.find(c => c.type === 'item_model'); return c ? (c.data && c.data.toString()) : null; };
const maxhp = () => { const a = bot.entity.attributes && bot.entity.attributes['minecraft:max_health']; return a ? Math.round(a.value + (a.modifiers || []).reduce((x, m) => x + (m.operation === 0 ? m.amount : 0), 0) * 0) : null; };
const attr = (k) => { const at = bot.entity.attributes || {}; const a = at[k] || at[k.replace('minecraft:', 'generic.')] || at[k.replace('minecraft:', '')]; if (!a) return 'keys:' + Object.keys(at).join(','); return +(a.modifiers && a.modifiers.length ? a.modifiers.reduce((v, m) => v + m.amount, a.value) : a.value).toFixed(3); };
const slotInfo = (i) => { const s = bot.currentWindow.slots[i]; return s ? `${i}:${s.name}x${s.count}${glint(s) ? '*' : ''}[${nm(s)}]${model(s) ? '{' + model(s) + '}' : ''}` : `${i}:-`; };

bot.once('spawn', async () => {
  try {
    await sleep(5000);
    if (bot.currentWindow && phase !== 'rejoin') { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(1500); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    await sleep(1500);
    if (phase === 'rejoin') {
      P('REJOIN inspect: ' + await con('skillsadmin inspect ' + name));
      P('REJOIN max_health=' + attr('minecraft:max_health') + ' armor=' + attr('minecraft:armor') + ' bars=' + bars.slice(-2).join('/'));
      for (const c of ['skillsadmin unlock %N l2', 'skillsadmin unlock %N l3', 'skillsadmin unlock %N l4']) await con(c.replace('%N', name));
      P('fire: ' + await con('skillsadmin fire ' + name + ' LOW_HP', 1500)); P('absorb: ' + await con('data get entity ' + name + ' AbsorptionAmount', 1500));
      await sleep(2500); P('max_health with l1x2,l2,l3,l4: ' + attr('minecraft:max_health'));
      bot.chat('/skills'); await sleep(1500); P('REJOIN map title: ' + title());
      for (const i of [2, 20, 12, 21]) P('  ' + slotInfo(i));
      bot.quit(); return;
    }
    P('class chat: ' + chat.filter(c => /Анги|анги|оноо/.test(c)).slice(-4).join(' | '));
    P('LEVEL1 inspect: ' + await con('skillsadmin inspect ' + name));
    bot.chat('/skills'); await sleep(1800);
    P('MAP title: ' + title());
    P('MAP window slots: ' + [2, 12, 20, 22, 24, 11, 13, 45, 49, 50, 51, 52, 53].map(slotInfo).join('  '));
    // level 1: unlock should be refused
    bot.currentWindow && await bot.clickWindow(20, 0, 0); await sleep(800);
    P('click l1 at level 1 -> bar: ' + bars.slice(-2).join(' / '));
    // grow
    await con('suld exp ' + name + ' 9000000'); await sleep(1500);
    P('LEVEL60 inspect: ' + await con('skillsadmin inspect ' + name));
    bot.currentWindow && bot.closeWindow(bot.currentWindow); await sleep(500);
    bot.chat('/skills'); await sleep(1800);
    P('MAP after level-up: ' + [20, 22, 24].map(slotInfo).join('  ') + ' info:' + slotInfo(52));
    // unlock l1 twice (ranks), r1
    await bot.clickWindow(20, 0, 0); await sleep(700); await bot.clickWindow(20, 0, 0); await sleep(700); await bot.clickWindow(24, 0, 0); await sleep(900);
    P('after unlocks: ' + [20, 24, 12, 14, 22].map(slotInfo).join('  ') + ' bar=' + bars.slice(-1));
    P('max_health attr now: ' + attr('minecraft:max_health') + ' (base 20, l1 x2 => 28)');
    P('inspect: ' + await con('skillsadmin inspect ' + name));
    // refund l1 once (right click)
    await bot.clickWindow(20, 1, 0); await sleep(800);
    P('after refund: ' + slotInfo(20) + ' max_health=' + attr('minecraft:max_health'));
    // exclusive: build the path to l2 / m2 conflict through admin
    await con('skillsadmin unlock ' + name + ' l2'); 
    await con('skillsadmin unlock ' + name + ' m1');
    P('unlock m2 while l2 learned (should work: l2 excl m2? yes blocked): ' + await con('skillsadmin unlock ' + name + ' m2'));
    // proc via admin fire: build l3 -> l4 (LOW_HP shield)
    await con('skillsadmin unlock ' + name + ' l3'); P('l4: ' + await con('skillsadmin unlock ' + name + ' l4'));
    P('fire LOW_HP: ' + await con('skillsadmin fire ' + name + ' LOW_HP'));
    P('absorption: ' + await con('data get entity ' + name + ' AbsorptionAmount'));
    // search
    bot.currentWindow && bot.closeWindow(bot.currentWindow);
    bot.chat('/skills search амь'); await sleep(2000); P('search chat: ' + chat.slice(-2).join(' | ') + ' title: ' + title());
    // zoom / filter / pan buttons
    await bot.clickWindow(49, 0, 0); await sleep(600); P('zoom2 slots: ' + [0, 1, 2, 9, 10, 11].map(slotInfo).join(' '));
    await bot.clickWindow(49, 0, 0); await sleep(600);
    await bot.clickWindow(51, 0, 0); await sleep(600); P('filter: ' + slotInfo(51));
    await bot.clickWindow(48, 0, 0); await sleep(600); P('pan right: ' + [0, 2, 4].map(slotInfo).join(' '));
    bot.closeWindow(bot.currentWindow); await sleep(500);
    // builds
    bot.chat('/skills build save tank'); await sleep(900); P('save: ' + chat.slice(-1));
    bot.chat('/skills build list'); await sleep(700); P('list: ' + chat.slice(-1));
    bot.chat('/skill l1'); await sleep(800); P('skill l1: ' + chat.slice(-6).join(' | '));
    bot.chat('/skills info'); await sleep(800); P('info: ' + chat.slice(-7).join(' | '));
    // reset via orb
    await con('skillsadmin orb ' + name + ' 1');
    const orb = bot.inventory.slots.findIndex(i => i && i.name === 'heart_of_the_sea'); P('orb slot ' + orb + ' ' + (orb >= 0 ? nm(bot.inventory.slots[orb]) + ' {' + model(bot.inventory.slots[orb]) + '}' : ''));
    if (orb >= 36) { bot.setQuickBarSlot(orb - 36); await sleep(300); bot.activateItem(); await sleep(1200); P('orb confirm title: ' + title()); await bot.clickWindow(11, 0, 0); await sleep(1500); P('after orb: ' + chat.slice(-2).join(' | ')); }
    P('inspect after reset: ' + await con('skillsadmin inspect ' + name));
    P('orb left: ' + bot.inventory.slots.filter(i => i && i.name === 'heart_of_the_sea').length);
    P('max_health after reset: ' + attr('minecraft:max_health'));
    // rebuild something for the rejoin phase
    bot.chat('/skills build load tank'); await sleep(900); P('load tank: ' + chat.slice(-1));
    await con('skillsadmin unlock ' + name + ' l1'); await con('skillsadmin unlock ' + name + ' l1');
    P('final inspect: ' + await con('skillsadmin inspect ' + name));
    P('validate: ' + await con('skillsadmin validate')); P('reload: ' + await con('skillsadmin reload'));
    bot.quit();
  } catch (e) { P('ERROR ' + e.stack); bot.quit(); }
});
bot.on('end', () => process.exit(0));
setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 150000);
