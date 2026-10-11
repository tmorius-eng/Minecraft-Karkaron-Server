// Live checks of the Naadam horse race, the ovoo walk and the steppe-horse saddle lock (dev server, offline bots).
//   node racebot.js <ctl.sh> <logfile> <serverdir> <port>
const mineflayer = require('mineflayer');
const fs = require('fs');
const { execFileSync } = require('child_process');
const [ctl, logfile, srv, port] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const results = [];
const check = (name, ok, detail) => { results.push({ name, ok: !!ok, detail }); console.error((ok ? 'PASS ' : 'FAIL ') + name + (detail ? ' :: ' + detail : '')); };
function join(name) {
  return new Promise((resolve) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: +port, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
    bot.chatLog = [];
    bot.on('messagestr', (m) => bot.chatLog.push(m));
    bot.on('actionBar', (j) => bot.chatLog.push('[AB] ' + j.toString()));
    bot.on('error', () => {});
    bot.once('spawn', async () => { await sleep(3500); resolve(bot); });
    setTimeout(() => resolve(null), 30000);
  });
}
async function pickClass(bot, slot) {
  for (let i = 0; i < 10 && !bot.currentWindow; i++) await sleep(500);
  if (bot.currentWindow) { await bot.clickWindow(slot, 0, 0).catch(() => {}); await sleep(1200); }
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
}
const heard = (bot, re) => bot.chatLog.find(l => re.test(l));
// ride the horse like a client does: the rider sends the vehicle's position (vehicle_move), ~11 blocks/s over the
// ground of the loaded chunks
function groundY(bot, x, z, fallback) {
  const { Vec3 } = require('vec3');
  for (let y = 140; y > -40; y--) {
    const b = bot.blockAt(new Vec3(x, y, z));
    if (b == null) return fallback;
    if (b.boundingBox === 'block' && !/leaves/.test(b.name)) return y + 1; // the server's checkpoints ignore leaves too
    if (/water|lava/.test(b.name)) return y + 1;
  }
  return fallback;
}
async function drive(bot, path) {
  let [cx, cy, cz] = [bot.vehicle.position.x, bot.vehicle.position.y, bot.vehicle.position.z];
  let fixes = 0, stuck = 0, side = 1, hop = 0, detours = 0;
  const onFix = (pk) => { cx = pk.x; cy = pk.y; cz = pk.z; fixes++; stuck++; };
  bot._client.on('vehicle_move', onFix); // the server's correction: where the horse really is
  bot.driveFixes = () => fixes;
  const step = async (tx, tz) => {
    const dx = tx - cx, dz = tz - cz, d = Math.hypot(dx, dz);
    if (d < 0.05) { // over the waypoint: come down to the ground
      cy = Math.max(groundY(bot, Math.floor(cx), Math.floor(cz), cy), cy - 3);
      bot._client.write('vehicle_move', { x: cx, y: cy, z: cz, yaw: 0, pitch: 0, onGround: true });
      await sleep(50);
      return;
    }
    const st = Math.min(0.5, d), nx = cx + dx / d * st, nz = cz + dz / d * st;
    let gy = groundY(bot, Math.floor(nx), Math.floor(nz), cy);
    if (hop > 0) { hop--; gy = Math.min(Math.max(gy + 4, cy), gy + 6); } // test-only hop over a wall or a canal (scratch server allows flight)
    if (gy > cy + 0.6) cy += 1.05; // a rise ahead: jump straight up first, then step on
    else if (gy < cy - 3) cy -= 3; // a drop: go down in a few steps (a big jump is 'moved too quickly')
    else { cx = nx; cz = nz; cy = gy; } // forward
    bot._client.write('vehicle_move', { x: cx, y: cy, z: cz, yaw: Math.atan2(-dx, dz) * 180 / Math.PI, pitch: 0, onGround: true });
    await sleep(50);
  };
  try {
    for (const [px, pz] of path) {
      const tx = px + 0.5, tz = pz + 0.5;
      let guard = 0;
      for (; guard < 4000 && (Math.hypot(tx - cx, tz - cz) > 0.6 || cy > groundY(bot, px, pz, cy) + 1.5); guard++) {
        if (!bot.vehicle) { console.error('lost the vehicle at', cx.toFixed(1), cy.toFixed(1), cz.toFixed(1)); return false; }
        if (stuck > 3 && ++detours % 3 === 0) { hop = 40; stuck = 0; continue; }
        if (stuck > 3) {
          // blocked (a tree, a wall): ride sideways a few blocks, alternating sides and widening, then go on
          const dx = tx - cx, dz = tz - cz, d = Math.hypot(dx, dz) || 1;
          const w = 3 + 2 * Math.floor(guard / 400);
          const ox = cx + (-dz / d) * side * w, oz = cz + (dx / d) * side * w;
          side = -side; stuck = 0;
          for (let k = 0; k < 2 * w + 2 && stuck < 3; k++) await step(ox, oz);
          continue;
        }
        await step(tx, tz);
      }
      if (guard >= 4000) console.error('gave up on waypoint', px, pz, 'at', cx.toFixed(1), cy.toFixed(1), cz.toFixed(1));
      if (bot.marks && bot.marks.has(px + ',' + pz)) console.error('at checkpoint', px, pz, 'horse', cx.toFixed(1), cy.toFixed(1), cz.toFixed(1), 'fixes', fixes);
    }
    return true;
  } finally {
    bot._client.removeListener('vehicle_move', onFix);
  }
}

(async () => {
  const name = 'Rc' + Math.floor(Math.random() * 900 + 100);
  const R = await join(name);
  if (!R) { console.error('join failed'); process.exit(2); }
  await pickClass(R, 10);
  cmd(`suld exp ${name} 400000`); await sleep(1500);
  // ---------------------------------------------------------------- ovoo (area.tuul: 331, -190)
  const OX = 331, OZ = -190;
  cmd(`effect give ${name} minecraft:slow_falling 60 0 true`);
  cmd(`tp ${name} ${OX + 6} 200 ${OZ}`);
  let y = null;
  for (let i = 0; i < 30 && y == null; i++) {
    await sleep(1000);
    try { const m = fs.readFileSync(srv + '/plugins/SULD/ovoo.yml', 'utf8').match(/tuul:\s*\n\s*x: (-?\d+)\s*\n\s*y: (-?\d+)\s*\n\s*z: (-?\d+)/); if (m) y = +m[2]; } catch (e) {}
  }
  check('the tuul ovoo is built when its chunk loads', y != null, 'y=' + y);
  if (y != null) {
    for (let k = 0; k <= 80; k++) {
      const b = k * 15 * Math.PI / 180;
      cmd(`tp ${name} ${(OX + 0.5 + 5 * Math.sin(b)).toFixed(2)} ${y} ${(OZ + 0.5 - 5 * Math.cos(b)).toFixed(2)}`);
      await sleep(650);
      if (heard(R, /тойрлоо/)) break;
    }
    check('three clockwise turns grant the blessing', heard(R, /тойрлоо/), heard(R, /тойрлоо|овоо/i));
  }
  // ---------------------------------------------------------------- horse + saddle lock
  await sleep(3000); R.chat('/spawn'); await sleep(5000);
  // control (a second bot): a plain tamed, saddled vanilla horse opens its inventory on the same packet
  const C = await join(name + 'c');
  await pickClass(C, 11);
  cmd(`execute at ${name}c run summon minecraft:horse ~2 ~ ~ {Tame:1b,Tags:["ctlhorse"],equipment:{saddle:{id:"minecraft:saddle",count:1}}}`);
  await sleep(800);
  cmd(`ride ${name}c mount @e[tag=ctlhorse,limit=1]`); await sleep(1200);
  let ctl = false;
  C.once('windowOpen', (w) => { ctl = true; });
  try { C._client.write('entity_action', { entityId: C.entity.id, actionId: 'open_inventory', jumpBoost: 0 }); } catch (e) { console.error('entity_action', e.message); }
  await sleep(1500);
  check('control: a vanilla horse inventory opens on the packet', ctl, 'vehicle=' + !!C.vehicle);
  C.quit(); cmd('minecraft:kill @e[tag=ctlhorse]');
  R.chat('/mori'); await sleep(3000);
  check('/mori mounts the steppe horse', !!R.vehicle, R.chatLog.slice(-3).join(' | '));
  let opened = false;
  R.once('windowOpen', () => { opened = true; });
  try { R._client.write('entity_action', { entityId: R.entity.id, actionId: 'open_inventory', jumpBoost: 0 }); } catch (e) { console.error('entity_action', e.message); }
  await sleep(1500);
  check('the steppe horse inventory does not open (saddle locked)', !opened);
  // ---------------------------------------------------------------- race
  cmd('uraldaan start'); await sleep(5000);
  const opening = heard(R, /Морин уралдаан эхэллээ/);
  check('the race opens (chunks loaded async)', opening, opening);
  const SX = -16, SZ = -46; let RAD = 140;
  const pts = [...Array(8).keys()].map(i => { const a = (180 + i * 45) * Math.PI / 180; return [SX + Math.round(Math.sin(a) * RAD), SZ + Math.round(-Math.cos(a) * RAD)]; });
  // the real checkpoints (dry ground), from the admin listing in the server log
  cmd('uraldaan'); await sleep(1200);
  const listing = fs.readFileSync(logfile, 'utf8').split('\n').filter(l => l.includes('Цэгүүд:')).pop() || '';
  const cps = [...listing.matchAll(/(\d)=(-?\d+),(-?\d+),(-?\d+)/g)].map(m => [+m[2], +m[3], +m[4]]);
  check('the admin listing shows all 8 checkpoints', cps.length === 8, listing.slice(-160));
  RAD = Math.round(cps.reduce((t, c) => t + Math.hypot(c[0] - SX, c[2] - SZ), 0) / cps.length); console.error('ring radius ~', RAD);
  // stand on checkpoint 1 and summon the horse there (the old horse went away with the teleport)
  cmd(`minecraft:tp ${name} ${cps[0][0] + 0.5} ${cps[0][1]} ${cps[0][2] + 0.5}`); await sleep(3500);
  R.chat('/mori'); await sleep(2500);
  const path = [];
  const ang = (x, z) => Math.atan2(x - SX, -(z - SZ)) * 180 / Math.PI;
  for (let i = 0; i < 8; i++) {
    const [ax, , az] = cps[i], [bx, , bz] = cps[(i + 1) % 8];
    let a0 = ang(ax, az), a1 = ang(bx, bz); if (a1 <= a0) a1 += 360;
    for (let a = a0 + 5; a < a1 - 2; a += 5) { const r = a * Math.PI / 180; path.push([SX + Math.round(Math.sin(r) * RAD), SZ + Math.round(-Math.cos(r) * RAD)]); }
    path.push([bx, bz]);
  }
  cmd('uraldaan'); await sleep(800);
  console.error('after start:', fs.readFileSync(logfile, 'utf8').split('\n').filter(l => l.includes('Замд:')).pop());
  R.marks = new Set(cps.map(c => c[0] + ',' + c[2]));
  const drove = R.vehicle ? await drive(R, path) : false;
  cmd('uraldaan'); await sleep(800);
  console.error('after drive:', fs.readFileSync(logfile, 'utf8').split('\n').filter(l => l.includes('Замд:')).pop());
  check('the rider stays mounted for the whole loop', drove, 'server corrections ' + (R.driveFixes ? R.driveFixes() : '?'));
  console.error('checkpoints seen:', [...new Set(R.chatLog.filter(l => /Цэг \d\/8|Эхэллээ/.test(l)).map(l => l.replace(/ · .*/, '')))].join(', '));
  const reached = R.chatLog.filter(l => /Цэг \d\/8/.test(l)).length;
  // anti-hop: a player teleport mid-race resets the rider
  R.chat('/uraldaan'); await sleep(800);
  await sleep(1500);
  const fin = heard(R, /1-р байр/);
  check('closing the loop finishes in 1st place with a prize', fin && heard(R, /\+300/), fin);
  // a second racer who teleports mid-course is sent back to the start
  const T = await join(name + 't'); await pickClass(T, 12);
  cmd(`suld exp ${name}t 400000`); await sleep(1500);
  cmd(`minecraft:tp ${name}t ${cps[0][0] + 0.5} ${cps[0][1]} ${cps[0][2] + 0.5}`); await sleep(3500);
  T.chat('/mori'); await sleep(2500);
  const tp = [[cps[0][0] + 1, cps[0][2] + 1]];
  if (T.vehicle) await drive(T, tp);
  await sleep(1000);
  cmd(`execute positioned ${SX + 30} 0 ${SZ + RAD} positioned over motion_blocking_no_leaves run minecraft:tp ${name}t ~0.5 ~ ~0.5`); await sleep(1500);
  check('a teleport mid-race sends the rider back to the start', heard(T, /1-р цэгээс дахин эхэл/), T.chatLog.slice(-2).join(' | '));
  T.quit();
  fs.writeFileSync(srv + '/../racebot.json', JSON.stringify(results, null, 1));
  console.error(`${results.filter(r => r.ok).length}/${results.length} PASS`);
  R.quit(); await sleep(500); process.exit(0);
})();
