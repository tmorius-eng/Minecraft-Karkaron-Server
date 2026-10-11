// D1 live QA: node khasarbot.js <srv2ctl> <name> <phase: render|fight>
const mineflayer = require('mineflayer'); const fs = require('fs'); const { execFileSync } = require('child_process');
const [ctl, name, phase, nArg] = process.argv.slice(2); const N = +(nArg || 1);
const LOG = '/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2/logs/latest.log';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const lines = () => fs.readFileSync(LOG, 'utf8').split('\n');
async function server(c, re, wait = 1500) { const n = lines().length - 1; cmd(c); const end = Date.now() + Math.max(wait, 3500); await sleep(Math.min(wait, 400)); let out = []; while (Date.now() < end) { out = lines().slice(n).filter(l => !re || re.test(l)); if (re && out.length) break; await sleep(150); } return out.map(l => l.replace(/^\[[\d:]+\] \[[^\]]+\]: /, '')); }
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const bars = [], chat = [];
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot._client.on('system_chat', (pk) => { const t = flat(pk.content); if (pk.isActionBar || pk.overlay) bars.push(t); else chat.push(t); });
bot._client.on('set_action_bar_text', (pk) => bars.push(flat(pk.text)));
function nflat(n) { if (n == null) return ''; if (typeof n === 'string') return n; if (n.type === 'string') return n.value; if (n.type === 'compound') { const v = n.value; return nflat(v.text) + (v.extra ? nflat(v.extra) : ''); } if (n.type === 'list') return (n.value.value || []).map(x => nflat({ type: n.value.type, value: x })).join(''); return ''; }
bot._client.on('action_bar', (pk) => { const t = nflat(pk.text); if (/[А-Яа-яӨөҮү]/.test(t)) bars.push(t.replace(/[^\u0400-\u04FF ⚠!—\-]/g, '').replace(/\s+/g, ' ').trim()); });
let hurt = 0, minHp = 99; bot.on('health', () => { minHp = Math.min(minHp, bot.health); }); bot.on('entityHurt', (e) => { if (e === bot.entity) hurt++; });
function step(k, v) { console.log(k, JSON.stringify(v)); }
const count = async (sel) => { const r = (await server(`execute if entity ${sel}`, /Test (passed|failed)/, 900)).join(' '); const m = r.match(/[Cc]ount: (\d+)/); return m ? +m[1] : 0; };
bot.once('spawn', async () => {
  try {
    await sleep(5000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2500); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    cmd('scoreboard objectives add suldqa dummy');
    cmd('fill 2985 99 2985 3021 99 3021 stone'); cmd('fill 2985 100 2985 3021 106 3021 air'); await sleep(600);
    cmd(`tp ${name} 3003 100 3003`); await sleep(2500);
    if (phase === 'render') {
      step('perf', await server(`suldperf model ${N} 15 ${name}`, /perf/, 1200));
      await sleep(4000);
      step('modelDisplaysDuring', await count('@e[type=item_display,tag=suld_model]'));
      step('hostsInvisible', await count('@e[type=ravager,tag=suld_model_host]'));
      await sleep(14000);
      step('perfDone', lines().filter(l => new RegExp('\\[perf\\] ' + N + ' × khasar').test(l)).slice(-1));
      step('modelDisplaysAfter', await count('@e[type=item_display,tag=suld_model]'));
    } else if (phase === 'fight') {
      cmd(`suld exp ${name} 20000`); cmd(`effect give ${name} minecraft:resistance 600 3 true`); cmd(`effect give ${name} minecraft:regeneration 600 3 true`); await sleep(1500);
      chat.length = 0; bot.chat('/dungeon enter khasar_den'); await sleep(3000); step('enter', chat.slice(-3));
      // clear the waves quickly (console kills count as wave deaths)
      for (let w = 0; w < 12; w++) { cmd(`execute at ${name} run minecraft:kill @e[tag=suld_dungeon,type=!ravager,distance=..60]`); await sleep(2500);
        const r = await count('@e[type=ravager,tag=suld_dungeon]'); if (r === 1) { step('bossAfterWave', w); break; } }
      await sleep(2000);
      step('rigDisplays', await count('@e[type=item_display,tag=suld_model]'));
      // fight: stay close so the brain uses its abilities; push the boss through its phases
      const hp = async () => { const r = (await server(`execute as @e[type=ravager,tag=suld_dungeon,limit=1] run data get entity @s Health`, /has the following entity data/, 800)).join(' '); const m = r.match(/([\d.]+)f/); return m ? +m[1] : -1; };
      const max = await hp(); step('bossMaxHp', max);
      for (let i = 0; i < 11; i++) {
        cmd(`execute at @e[type=ravager,tag=suld_dungeon,limit=1] run tp ${name} ^ ^ ^3`);
        await sleep(5000);
        if (i === 3 || i === 6) { const h = await hp(); cmd(`execute as @e[type=ravager,tag=suld_dungeon,limit=1] run damage @s ${Math.max(1, Math.round(h - max * (i === 3 ? 0.5 : 0.2)))} minecraft:generic`); await sleep(1500); }
        step('t' + i, { hp: await hp(), botHp: Math.round(bot.health), minHp: Math.round(minHp), hurt, bars: [...new Set(bars.splice(0))].slice(-4) });
        if (i === 7) { cmd(`execute at ${name} run tp ${name} ~10 ~ ~`); await sleep(4000); step('pounceRange', [...new Set(bars.splice(0))].slice(-3)); }
      }
      step('chat', chat.filter(m => /шат|Босс|ХАЛУУР|галзуур|чонo|Хасар/i.test(m)).slice(-6));
      step('wolvesSummoned', await count('@e[type=wolf,tag=suld_dungeon]'));
      cmd(`execute as @e[type=ravager,tag=suld_dungeon,limit=1] run damage @s 9999 minecraft:generic`); await sleep(1500);
      step('displaysDuringDeath', await count('@e[type=item_display,tag=suld_model]'));
      await sleep(6000);
      step('displaysAfterDeath', await count('@e[type=item_display,tag=suld_model]'));
      step('complete', chat.filter(m => /АГУЙ|EXP|Шагнал|ДУУСЛАА/i.test(m)).slice(-4));
      step('probes', await server('suldperf report', /model\.|server\.tick/, 1500));
    }
  } catch (e) { console.log('ERR', e && e.stack || e); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 260000);
