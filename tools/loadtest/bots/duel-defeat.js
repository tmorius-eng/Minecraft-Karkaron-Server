// A consented duel never ends in a hardcore death (GAME_DESIGN.md «PvP policy»): the killing blow, and the burn a duel
// hit started, become a defeat; outside a duel the same damage is a real death. Dev server, offline dev flag:
//   node duel-defeat.js <srvctl.sh> <logfile> [port]
const mineflayer = require('mineflayer');
const fs = require('fs');
const { execFileSync } = require('child_process');
const [ctl, logfile, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
// the last element after the final newline is '' (or a half-written line): never count it, or slice(n) skips a line
const lines = () => { const a = fs.readFileSync(logfile, 'utf8').split('\n'); a.pop(); return a; };
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };

function join(name) {
  return new Promise((res) => {
    const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    b.chatLog = [];
    b.on('messagestr', m => b.chatLog.push(m));
    b.on('actionBar', m => b.chatLog.push('[bar] ' + m.toString()));
    b.on('error', () => {});
    b.deaths = 0;
    b.on('death', () => b.deaths++);
    b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
    b.once('spawn', async () => {
      await sleep(3500);
      for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
      if (b.currentWindow) { // class pick, then the confirm page
        await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600);
        await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200);
      }
      res(b);
    });
  });
}

const diedSince = (n, name) => [...lines().slice(n)].some(l => l.includes(name) && /(died|was slain|burned|үхэв|нас бар|сүнс)/i.test(l));

// the steppe has mobs: one could finish a defeated fighter and spoil the next check
// minecraft:kill: EssentialsX takes over a bare /kill and reads the selector as a player name
const clearMobs = () => cmd('execute at @a run minecraft:kill @e[type=!player,type=!item,distance=..48]');

async function duel(A, B) {
  const k = B.chatLog.length;
  A.chat('/duel ' + B.username); await sleep(1500);
  B.chat('/duel accept'); await sleep(2000);
  const started = B.chatLog.slice(k).some(l => l.includes('тулаанд орлоо'));
  if (!started) console.log('  (duel did not start: ' + B.chatLog.slice(k).join(' | ') + ')');
  return started;
}

(async () => {
  const an = 'A' + Math.floor(Math.random() * 900 + 100), bn = 'B' + Math.floor(Math.random() * 900 + 100);
  const A = await join(an), B = await join(bn);
  for (let i = 0; i < 3 && !(Math.abs(B.entity.position.x) > 800); i++) {
    cmd(`spreadplayers 1500 1500 1 12 false @a`); await sleep(5000);
  }
  // the steppe's mobs hit a level-1 fighter hard: Resistance IV takes 80 % off every hit (the 1000-point test hits
  // still kill), so a stray mob cannot finish a defeated fighter between two checks
  cmd('effect give @a minecraft:resistance 600 3 true');
  const far = Math.abs(A.entity.position.x) > 800 && Math.abs(B.entity.position.x) > 800;
  check(far, 'both fighters stand outside Kharkhorum', 'A ' + A.entity.position.floored() + ' B ' + B.entity.position.floored());

  check(await duel(A, B), 'the duel starts', B.chatLog.slice(-1).join(''));
  clearMobs();
  let n = lines().length;
  const full = B.health;
  cmd(`damage ${bn} 1000 minecraft:player_attack by ${an}`); await sleep(2500);
  const hp1 = B.health;
  const yielded = B.chatLog.some(l => l.includes('тулаанд ялав'));
  // the defeat cancels the hit, so /damage reports the target as invulnerable: the yield line is the proof
  check(yielded && !diedSince(n, bn) && hp1 > 0 && hp1 < full * 0.35, 'a lethal duel hit is a defeat, not a death',
      'hp ' + full + ' -> ' + hp1 + ' | ' + A.chatLog.filter(l => l.includes('ялав')).join(''));

  n = lines().length;
  clearMobs();
  cmd(`damage ${bn} 1000 minecraft:player_attack by ${an}`); await sleep(1000);
  // the PvP policy cancelled the hit; the server log can lag a few seconds behind, so wait for the line
  let refused = false;
  for (let i = 0; i < 12 && !refused; i++) { refused = lines().slice(n).some(l => l.includes('invulnerable')); if (!refused) await sleep(500); }
  check(refused && B.health > hp1 - 20 && !diedSince(n, bn), 'after the defeat the duel is over: a hit does nothing', 'hp=' + B.health + ' refused=' + refused);

  // a duel hit that leaves a burn, then the burn would kill: still the duel
  cmd(`effect give ${bn} minecraft:instant_health 1 10`); await sleep(1500);
  check(await duel(A, B), 'a second duel starts', '');
  clearMobs();
  const k = B.chatLog.length;
  for (let i = 0, before = B.health; i < 6 && B.health >= before; i++) { // a class dodge may turn the hit away
    cmd(`damage ${bn} 2 minecraft:player_attack by ${an}`); await sleep(800);
  }
  n = lines().length;
  cmd(`damage ${bn} 1000 minecraft:on_fire`); await sleep(2500);
  check(!diedSince(n, bn) && B.health > 0 && B.chatLog.slice(k).some(l => l.includes('тулаанд ялав')), 'a burn after a duel hit is a defeat too', 'hp=' + B.health);

  // no duel and no recent duel hit: the same burn is a real hardcore death
  // past the defeat's 60 ticks of protection (a slow dev server ticks below 20/s); keep the wild mobs off B meanwhile
  // (a mob kill here would make B a soul, and a soul cannot be burnt)
  for (let i = 0; i < 4; i++) { clearMobs(); await sleep(2000); }
  const d0 = B.deaths;
  cmd(`damage ${bn} 1000 minecraft:on_fire`); await sleep(2500);
  check(B.deaths > d0, 'outside a duel the burn kills for real', 'deaths ' + d0 + ' -> ' + B.deaths);

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  A.quit(); B.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})();
