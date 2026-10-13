// The death reform of 2026-10-13 on the dev server (docs/DEATH_AND_RECOVERY.md), one offline bot:
//   node soulreform.js <srvctl.sh> [port]
// 1. A level ≤ 10 death costs nothing: the same bag, the same EXP (database), no soul («❤ Шинэ баатрын хамгаалалт»).
// 2. A level-20 death: −10 % of the level's progress (database), a soul without a timer (its boss bar says
//    «/trial эсвэл овооны цацал»), a «Сүнсний Гэрэлт» row in suld_soul_beacons, and the soul rises beside an ovoo,
//    nearer to where it fell than the city is.
// 3. Walking back to the beam pays 70 % of the lost EXP once (database EXP, claimed_at set); a second visit pays nothing.
// 4. «Овооны цацал»: leaving the ovoo breaks it; three sunwise rounds and 90 s by the cairn bring the soul back to life,
//    and a soul is never blessed by the ovoo meanwhile.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const psql = (q) => execFileSync('runuser', ['-u', 'postgres', '--', 'psql', '-d', process.env.SULD_DB || 'suldit', '-At', '-c', q], { cwd: '/tmp' }).toString().trim();
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'R' + Math.floor(Math.random() * 90000 + 10000);
const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
const chat = [];
b.on('messagestr', m => chat.push(m));
b.on('error', () => {});
b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
const said = (k, s) => chat.slice(k).some(l => l.includes(s));
const lastWith = (k, s) => chat.slice(k).filter(l => l.includes(s)).pop() || '';
// boss bar titles (the soul's bar), from the raw packet: the title is an NBT text component in 1.21.11
const flat = (c) => { if (c == null) return ''; if (typeof c === 'string') return c; const v = c.value || c; let s = ''; if (v.text) s += (v.text.value ?? v.text); const ex = v.extra && (v.extra.value?.value || v.extra.value || v.extra); if (Array.isArray(ex)) ex.forEach(e => s += flat(e.value ? e : { value: e })); return s; };
const bars = [];
b._client.on('boss_bar', (pk) => { if (pk.title) bars.push(flat(pk.title)); });
const exp = () => { const r = psql(`select level, exp_into_level from suld_profiles where name='${name}'`).split('|'); return { level: Number(r[0]), into: Number(r[1]) }; };
const beacon = () => psql(`select s.exp_lost, s.claimed_at is not null, s.x, s.y, s.z from suld_soul_beacons s join suld_profiles p on p.player_uuid = s.player_uuid `
  + `where p.name='${name}' order by s.created_at desc limit 1`);
const counts = () => { const m = {}; for (const i of b.inventory.items()) m[i.name] = (m[i.name] || 0) + i.count; return m; };
const STUFF = ['cobblestone', 'bread', 'oak_log', 'torch'];
const dist = (a, c) => Math.hypot(a.x - c.x, a.z - c.z);
async function die() {
  cmd(`minecraft:kill ${name}`); await sleep(1500);
  if (b.health <= 0 || b.isAlive === false) b.respawn();
  await sleep(4000);
}

b.once('spawn', async () => {
  try {
    await sleep(3500);
    for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
    if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
    if (b.currentWindow) b.closeWindow(b.currentWindow);
    const home = b.entity.position.clone(); // the city spawn

    // 1. a beginner's death costs nothing
    cmd(`suld exp ${name} 600`); await sleep(1500);
    for (const it of ['cobblestone 64', 'bread 16', 'oak_log 32', 'torch 20']) cmd(`minecraft:give ${name} ${it}`);
    cmd(`execute in minecraft:overworld run tp ${name} 900 200 900`); await sleep(7000);
    const e0 = exp(), inv0 = counts();
    let k = chat.length;
    await die();
    const e1 = exp(), inv1 = counts();
    check(e0.level >= 2 && e0.level <= 10, 'the bot is a beginner (level 2–10)', 'level ' + e0.level);
    check(said(k, 'Шинэ баатрын хамгаалалт'), 'a beginner\'s death says it cost nothing', lastWith(k, 'хамгаалалт').slice(0, 120));
    check(STUFF.every(s => (inv1[s] || 0) === (inv0[s] || 0)), 'every stack is kept', JSON.stringify(STUFF.map(s => [s, inv0[s] || 0, inv1[s] || 0])));
    check(e1.level === e0.level && e1.into === e0.into, 'no EXP is lost (database)', `${e0.level}/${e0.into} -> ${e1.level}/${e1.into}`);
    k = chat.length; b.chat('/trial'); await sleep(1500);
    check(said(k, 'та амьд байна'), 'no soul state after a beginner\'s death (/trial: alive)', chat.slice(k).join(' | ').slice(0, 100));

    // 2. a level-20 death: a soul without a timer, the beam, the rise at an ovoo
    cmd(`suld exp ${name} 200000`); await sleep(1500);
    cmd(`execute in minecraft:overworld run tp ${name} 900 200 900`); await sleep(7000);
    const fell = b.entity.position.clone();
    const f0 = exp();
    k = chat.length;
    const bk = bars.length;
    await die();
    const f1 = exp();
    const cost = lastWith(k, 'Үхлийн үнэ');
    const lost = Number((cost.match(/-(\d+) EXP/) || [])[1] || -1);
    const promised = Number((lastWith(k, 'Сүнсний Гэрэлт').match(/\+(\d+) EXP/) || [])[1] || -1);
    check(f0.level >= 11, 'the bot is past the protection (level 11+)', 'level ' + f0.level);
    check(lost > 0 && Math.abs(lost - f0.into * 0.10) <= 1 && f1.level === f0.level && f1.into === f0.into - lost,
      'the death takes 10 % of the level\'s progress (database)', `${cost.slice(0, 80)} :: ${f0.level}/${f0.into} -> ${f1.level}/${f1.into}`);
    check(promised === Math.floor(lost * 0.70), 'the summary promises 70 % back at the beam', `+${promised} of ${lost}`);
    const row = beacon().split('|');
    check(row.length === 5 && Number(row[0]) === lost && row[1] === 'f', 'the beam is stored (suld_soul_beacons, not claimed)', row.join('|'));
    const soulBar = bars.slice(bk).filter(t => t.includes('Сүнс')).pop() || '';
    check(soulBar.includes('/trial эсвэл овооны цацал') && soulBar.includes('Гэрэлт'), 'the soul\'s bar: no timer, how to come back, where the beam is', soulBar.slice(0, 120));
    const rise = b.entity.position.clone();
    check(dist(rise, home) > 100 && dist(rise, fell) < dist(home, fell), 'the soul rises by an ovoo, nearer to where it fell than the city',
      `rise ${rise.x.toFixed(0)} ${rise.z.toFixed(0)}, fell ${fell.x.toFixed(0)} ${fell.z.toFixed(0)}, ${dist(rise, fell).toFixed(0)} m vs city ${dist(home, fell).toFixed(0)} m`);

    // 3. walking back to the beam pays 70 % once
    k = chat.length;
    cmd(`tp ${name} ${Number(row[2]) + 0.5} ${row[3]} ${Number(row[4]) + 0.5}`); await sleep(3500);
    const f2 = exp();
    const paid = lastWith(k, 'буцлаа');
    check(said(k, 'алдсан EXP-ийн 70 % буцлаа') && f2.into === f1.into + promised, 'reaching the beam gives the 70 % back (database)',
      `${paid.slice(0, 100)} :: ${f1.into} -> ${f2.into}`);
    check(beacon().split('|')[1] === 't', 'the beam is claimed (claimed_at set)', beacon());
    k = chat.length;
    cmd(`tp ${name} ${Number(row[2]) + 20.5} ${row[3]} ${Number(row[4]) + 0.5}`); await sleep(1500);
    cmd(`tp ${name} ${Number(row[2]) + 0.5} ${row[3]} ${Number(row[4]) + 0.5}`); await sleep(3000);
    check(!said(k, 'буцлаа') && exp().into === f2.into, 'a second visit pays nothing', `${f2.into} -> ${exp().into}`);

    // 4. the ovoo ritual: the cairn is five blocks north of where the soul rose
    const cx = Math.floor(rise.x) + 0.5, cz = Math.floor(rise.z) - 5 + 0.5, y = rise.y;
    const at = (deg) => { const a = deg * Math.PI / 180; return [cx + Math.sin(a) * 5, cz - Math.cos(a) * 5]; };
    cmd(`tp ${name} ${rise.x.toFixed(2)} ${y.toFixed(2)} ${rise.z.toFixed(2)}`); await sleep(6000); // in the ring: the ritual starts
    k = chat.length;
    cmd(`tp ${name} ${(rise.x + 30).toFixed(2)} ${(y + 1).toFixed(2)} ${rise.z.toFixed(2)}`); await sleep(2500);
    check(said(k, 'Цацал тасарлаа'), 'leaving the ovoo breaks the ritual', chat.slice(k).filter(l => l.length > 3).join(' | ').slice(0, 120));

    k = chat.length;
    cmd(`tp ${name} ${rise.x.toFixed(2)} ${y.toFixed(2)} ${rise.z.toFixed(2)}`);
    const t0 = Date.now();
    for (let i = 0; i <= 40; i++) { // 40 × 30° = 1200°: three full sunwise rounds and a little more
      const [x, z] = at(180 + 30 * i);
      cmd(`tp ${name} ${x.toFixed(2)} ${y.toFixed(2)} ${z.toFixed(2)}`);
      await sleep(300);
    }
    const [sx, sz] = at(180);
    while (Date.now() - t0 < 105000 && !said(k, 'цацал өргөлөө')) { // the offering: by the cairn until 90 s
      cmd(`tp ${name} ${sx.toFixed(2)} ${y.toFixed(2)} ${sz.toFixed(2)}`);
      await sleep(2000);
    }
    const secs = (Date.now() - t0) / 1000;
    check(said(k, 'цацал өргөлөө') && secs >= 88, 'three sunwise rounds and 90 s by the cairn bring the soul back',
      secs.toFixed(0) + ' s :: ' + lastWith(k, 'цацал').slice(0, 110));
    check(!said(k, 'Тэнгэр ивээг'), 'a soul is not blessed by the ovoo meanwhile', lastWith(k, 'ивээ').slice(0, 80) || 'no blessing');
    k = chat.length; b.chat('/trial'); await sleep(1500);
    check(said(k, 'та амьд байна'), 'after the ritual the player is alive (/trial refused)', chat.slice(k).join(' | ').slice(0, 100));
    const state = psql(`select d.revive_state from suld_death_state d join suld_profiles p on p.player_uuid = d.player_uuid where p.name='${name}' order by d.death_seq desc limit 1`);
    check(state === 'RECOVERED', 'the death row is closed (RECOVERED)', state);
  } catch (e) {
    check(false, 'no exception', String(e && e.stack || e).slice(0, 300));
  }
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
});
setTimeout(() => { console.log('FAIL timeout'); process.exit(2); }, 300000);
