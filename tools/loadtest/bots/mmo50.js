// Realistic MMORPG load for the 50-player benchmark (local dev server only; offline-mode test bots).
//   node mmo50.js <srv2ctl.sh> <seconds> <role=count,...> [prefix]
// roles: city (plaza walk + GUIs + chat), explore (sprint outward into new terrain, periodic 48-block hops),
//        combat (steppe ring-1 fights + skill clicks), dungeon (solo wave dungeons, re-entered), boss (Khasar's Den),
//        quest (/quest, /tasks, walking the city edge), travel (teleports between area hearts and /spawn),
//        idle (AFK in the city), churn (join, 20 s, quit, rejoin).
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, secsS, rolesS, prefixA] = process.argv.slice(2);
const secs = Number(secsS), prefix = prefixA || 'P';
const SPAWN = { x: -16, z: -46 };
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => { try { execFileSync(ctl, ['cmd', c]); } catch (e) {} };
const roles = [];
for (const part of (rolesS || '').split(',').filter(Boolean)) { const [r, n] = part.split('='); for (let i = 0; i < Number(n); i++) roles.push(r); }
const stats = { joined: 0, failed: 0, kicked: [], deaths: 0, cmds: 0, dungeonsEntered: 0, teleports: 0 };
const polar = (bearingDeg, r) => ({ x: Math.round(SPAWN.x + Math.sin(bearingDeg * Math.PI / 180) * r), z: Math.round(SPAWN.z - Math.cos(bearingDeg * Math.PI / 180) * r) });
const end = Date.now() + secs * 1000;
const alive = () => Date.now() < end;

function join(name, idx) {
  return new Promise((resolve) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
    bot.on('error', () => {});
    bot.on('kicked', (r) => stats.kicked.push(name + ': ' + String(r).slice(0, 120)));
    bot.on('death', () => stats.deaths++);
    bot.once('spawn', async () => {
      await sleep(3000);
      if (bot.currentWindow) { await bot.clickWindow(10 + (idx % 5), 0, 0).catch(() => {}); await sleep(700); }
      if (bot.currentWindow) { await bot.clickWindow(11, 0, 0).catch(() => {}); await sleep(700); } // «Батлах»
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
      resolve(bot);
    });
    bot.once('end', () => resolve(null));
    setTimeout(() => resolve(null), 40000);
  });
}

function nearestHostile(bot, range) {
  let best = null, bd = range;
  for (const e of Object.values(bot.entities)) {
    if (e === bot.entity || e.type === 'player' || !e.position) continue;
    if (!['hostile', 'mob', 'animal'].includes(e.type) && e.kind !== 'Hostile mobs') continue;
    if (e.name === 'item_display' || e.name === 'text_display' || e.name === 'block_display' || e.name === 'interaction') continue;
    const d = e.position.distanceTo(bot.entity.position);
    if (d < bd) { bd = d; best = e; }
  }
  return best;
}

async function fight(bot, ms) {
  const stop = Date.now() + ms;
  while (Date.now() < stop && alive() && bot.entity) {
    const m = nearestHostile(bot, 14);
    if (m) {
      await bot.lookAt(m.position.offset(0, 0.6, 0), true).catch(() => {});
      const d = m.position.distanceTo(bot.entity.position);
      bot.setControlState('forward', d > 2.6);
      if (d < 3.6) bot.attack(m);
      if (Math.random() < 0.15) { bot.activateItem(); await sleep(120); bot.deactivateItem(); }
    } else {
      bot.setControlState('forward', true);
      await bot.look(Math.random() * Math.PI * 2, 0, true).catch(() => {});
    }
    bot.setControlState('jump', Math.random() < 0.2);
    await sleep(450);
  }
  bot.clearControlStates();
}

async function wander(bot, ms, sprint) {
  const stop = Date.now() + ms;
  bot.setControlState('forward', true);
  while (Date.now() < stop && alive() && bot.entity) {
    await bot.look(Math.random() * Math.PI * 2, 0, true).catch(() => {});
    bot.setControlState('jump', Math.random() < 0.3);
    bot.setControlState('sprint', sprint && Math.random() < 0.6);
    await sleep(1500 + Math.random() * 2000);
  }
  bot.clearControlStates();
}

function protect(name) {
  cmd(`effect give ${name} minecraft:resistance 100000 4 true`);
  cmd(`effect give ${name} minecraft:regeneration 100000 2 true`);
  cmd(`effect give ${name} minecraft:saturation 100000 0 true`);
}

// a dungeon party of one: walk up to the gate (tp + slow fall), enter, fight in the hall, come back out, again
async function gateRun(bot, id, bearing, radius, strength) {
  const g = polar(bearing, radius);
  cmd(`suld exp ${bot.username} 400000`); protect(bot.username); cmd(`effect give ${bot.username} minecraft:strength 100000 ${strength} true`);
  while (alive() && bot.entity) {
    cmd(`effect give ${bot.username} minecraft:slow_falling 30 0 true`);
    cmd(`tp ${bot.username} ${g.x + 0.5} 200 ${g.z + 3.5}`); stats.teleports++;
    await sleep(9000);
    bot.chat('/dungeon enter ' + id); stats.dungeonsEntered++; stats.cmds++;
    await sleep(4000);
    await fight(bot, 75000);
    bot.chat('/dungeon leave'); await sleep(4000);
  }
}

const ROLE = {
  async city(bot) {
    const guis = ['/profile', '/help', '/quest', '/skills chest', '/tasks', '/daily'];
    while (alive() && bot.entity) {
      await wander(bot, 12000, false);
      bot.chat(guis[Math.floor(Math.random() * guis.length)]); stats.cmds++;
      await sleep(2500);
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
      if (Math.random() < 0.3) bot.chat('Сайн байцгаана уу ' + Math.floor(Math.random() * 100));
    }
  },
  async explore(bot, i) {
    const b = (i * 47) % 360, p = polar(b, 1700 + (i % 4) * 250);
    protect(bot.username); cmd(`effect give ${bot.username} minecraft:slow_falling 40 0 true`);
    cmd(`tp ${bot.username} ${p.x} 190 ${p.z}`); stats.teleports++;
    await sleep(8000);
    const yaw = Math.PI - b * Math.PI / 180; // mineflayer yaw: 0 = -Z (north) facing; turn to the outward bearing
    let hop = Date.now() + 20000;
    while (alive() && bot.entity) {
      await bot.look(yaw + (Math.random() - 0.5) * 0.4, 0, true).catch(() => {});
      bot.setControlState('forward', true); bot.setControlState('sprint', true); bot.setControlState('jump', Math.random() < 0.5);
      if (Date.now() > hop) { // a rider or a fast traveller: 48 blocks further out
        const dx = Math.round(Math.sin(b * Math.PI / 180) * 48), dz = Math.round(-Math.cos(b * Math.PI / 180) * 48);
        cmd(`execute as ${bot.username} at @s run tp @s ~${dx} 200 ~${dz}`); cmd(`effect give ${bot.username} minecraft:slow_falling 20 0 true`);
        stats.teleports++; hop = Date.now() + 20000;
      }
      await sleep(1200);
    }
  },
  async combat(bot, i) {
    const p = polar((i * 71) % 360, 300 + (i % 3) * 120);
    protect(bot.username); cmd(`effect give ${bot.username} minecraft:strength 100000 2 true`); cmd(`effect give ${bot.username} minecraft:slow_falling 30 0 true`);
    cmd(`tp ${bot.username} ${p.x} 160 ${p.z}`); stats.teleports++;
    await sleep(6000);
    await fight(bot, secs * 1000);
  },
  async dungeon(bot, i) {
    const ids = [['govi_bulsh', 165, 650], ['baavgain_uur', 20, 1000], ['mosun_orgil', 255, 1400]];
    const [id, b, r] = ids[i % ids.length];
    if (process.env.HALLS === '1') return gateRun(bot, id, b, r, 4);
    const p = polar((i * 37 + 15) % 360, 650 + (i % 5) * 90); // the pre-halls workload (benchmarks stay comparable)
    cmd(`suld exp ${bot.username} 400000`); protect(bot.username); cmd(`effect give ${bot.username} minecraft:strength 100000 4 true`);
    cmd(`effect give ${bot.username} minecraft:slow_falling 30 0 true`);
    cmd(`tp ${bot.username} ${p.x} 160 ${p.z}`); stats.teleports++;
    await sleep(7000);
    while (alive() && bot.entity) {
      bot.chat('/dungeon enter ' + id); stats.dungeonsEntered++; stats.cmds++;
      await sleep(3000);
      await fight(bot, 60000);
      bot.chat('/dungeon leave'); await sleep(2500);
    }
  },
  async boss(bot, i) {
    if (process.env.HALLS === '1') return gateRun(bot, 'khasar_den', 75, 380, 1);
    const p = polar((i * 72 + 30) % 360, 900);
    cmd(`suld exp ${bot.username} 400000`); protect(bot.username); cmd(`effect give ${bot.username} minecraft:strength 100000 1 true`);
    cmd(`effect give ${bot.username} minecraft:slow_falling 30 0 true`);
    cmd(`tp ${bot.username} ${p.x} 160 ${p.z}`); stats.teleports++;
    await sleep(7000);
    while (alive() && bot.entity) {
      bot.chat('/dungeon enter khasar_den'); stats.dungeonsEntered++; stats.cmds++;
      await sleep(3000);
      await fight(bot, 90000);
      bot.chat('/dungeon leave'); await sleep(2500);
    }
  },
  async quest(bot) {
    while (alive() && bot.entity) {
      bot.chat(Math.random() < 0.5 ? '/quest' : '/tasks'); stats.cmds++;
      await sleep(2000);
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
      await wander(bot, 15000, true);
    }
  },
  async travel(bot, i) {
    protect(bot.username);
    let k = i;
    while (alive() && bot.entity) {
      const p = polar((k * 45 + 22) % 360, [400, 1100, 2600][k % 3]); k++;
      cmd(`effect give ${bot.username} minecraft:slow_falling 15 0 true`);
      cmd(`tp ${bot.username} ${p.x} 180 ${p.z}`); stats.teleports++;
      await wander(bot, 20000, true);
      if (k % 4 === 0) { bot.chat('/spawn'); stats.cmds++; await sleep(7000); }
    }
  },
  async idle(bot) { while (alive() && bot.entity) await sleep(5000); },
};

(async () => {
  const t0 = Date.now();
  const bots = [];
  for (let k = 0; k < roles.length; k += 5) { // five at a time, like a busy evening
    await Promise.all(roles.slice(k, k + 5).map(async (role, j) => {
      const i = k + j, name = prefix + i;
      if (role === 'churn') { bots.push({ churn: true, name, i }); return; }
      await sleep(j * 300);
      const b = await join(name, i);
      if (b) { stats.joined++; bots.push({ bot: b, role, i }); } else stats.failed++;
    }));
  }
  stats.joinSeconds = Math.round((Date.now() - t0) / 1000);
  console.error('joined ' + stats.joined + ' failed ' + stats.failed + ' in ' + stats.joinSeconds + 's');
  const jobs = bots.map(async (x) => {
    if (x.churn) {
      let cycles = 0;
      while (alive()) { const b = await join(x.name, x.i); if (b) { cycles++; await sleep(20000); b.quit(); } await sleep(3000); }
      stats.churnCycles = (stats.churnCycles || 0) + cycles; return;
    }
    try { await ROLE[x.role](x.bot, x.i); } catch (e) { }
  });
  await Promise.race([Promise.all(jobs), sleep(secs * 1000 + 15000)]);
  bots.forEach(x => { try { x.bot && x.bot.quit(); } catch (e) {} });
  await sleep(2000);
  console.log(JSON.stringify(stats));
  process.exit(0);
})();
