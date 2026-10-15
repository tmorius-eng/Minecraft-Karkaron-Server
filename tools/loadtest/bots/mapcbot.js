// «Сүлдийн Атлас» part C (docs/WORLD_MAP.md): the corner minimap, pins and the waypoint, one offline bot:
//   node mapcbot.js <srvctl.sh> [port]
// With the pack loaded the server hangs the minimap's map in four owner-only glow item frames facing up, 7.5 blocks
// out around the look (14° up ahead, 50° to either side, 24° under it); the pack's text shader draws that map in the screen's corner. The bot checks
// what the server can be held to: the frames follow the player when it is teleported and when it turns, none is on
// the line of fire, an arrow shot through one flies on (the frame steps aside), the map's first row carries the
// shader's marker colours, `/map minimap` hides and shows it, the atlas hides it while open. Pins: `/map pin` stores one in the database, it is there after a
// relog, `/map unpin` removes it. The waypoint: `/map guide <x> <z>` shows a yellow bar with the distance and a
// see-through card toward the place (an edge card with an arrow once turned away), `/map stop` ends both.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const [ctl, port = '25572'] = process.argv.slice(2);
// MinimapService.SLOTS: three over the look, one under it
const FRAMES = 4;
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'C' + Math.floor(Math.random() * 90000 + 10000);
const console_ = (cmd) => execFileSync(ctl, ['cmd', cmd]);
// Minimap.MARK: colour id × 4 + shade (magenta HIGH, diamond LOWEST, gold LOW, red LOWEST)
const MARK = [16 * 4 + 2, 31 * 4 + 3, 30 * 4 + 0, 28 * 4 + 3];
const FACING_UP = 1;

function join() {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
  b.on('error', () => {});
  b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
  b.facing = new Map(); // entity id -> facing from the spawn packet (hanging entities)
  b._client.on('spawn_entity', (pk) => b.facing.set(pk.entityId, pk.objectData));
  b.marked = []; // map ids whose picture starts with MARK
  b._client.on('map', (pk) => {
    if (!pk.data || pk.columns !== 128 || pk.x !== 0 || pk.y !== 0) return;
    if (MARK.every((v, i) => pk.data[i] === v) && !b.marked.includes(pk.itemDamage)) b.marked.push(pk.itemDamage);
  });
  b.chats = [];
  b.on('messagestr', (m) => b.chats.push(m));
  return b;
}
const spawned = (b) => new Promise(r => b.once('spawn', r));
// the minimap's frames: glow item frames facing up, near the ground (the atlas stage is far above)
const miniFrames = (b) => Object.values(b.entities).filter(e => e.name === 'glow_item_frame' && b.facing.get(e.id) === FACING_UP && e.position.y < 400);
const near = (b, frames, r) => frames.filter(f => f.position.distanceTo(b.entity.position.offset(0, 1.62, 0)) <= r);
const said = (b, s, since) => b.chats.slice(since).some(m => m.includes(s));

async function pickClass(b) {
  await sleep(3500);
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(2500);
}

(async () => {
  let b = join();
  await spawned(b);
  await pickClass(b);
  // 1. the minimap appears: four frames facing up, out of reach, along the look; its map carries the marker row
  let fr = miniFrames(b);
  const eye = b.entity.position.offset(0, 1.62, 0);
  const dists = fr.map(f => f.position.distanceTo(eye));
  check(fr.length === FRAMES && dists.every(d => d > 5.5 && d < 9.5), 'the minimap hangs in ' + FRAMES + ' owner-only frames facing up, out of reach',
    fr.length + ' frames at ' + dists.map(d => d.toFixed(1)).join(', ') + ' blocks');
  check(b.marked.length > 0, 'the minimap\'s picture starts with the shader\'s marker colours', 'marked map ids ' + JSON.stringify(b.marked));
  // 2. the frames follow a teleport (30 blocks east) and a turn (yaw + 180)
  const p0 = b.entity.position.clone();
  console_(`tp ${name} ${(p0.x + 30).toFixed(1)} ${(p0.y + 0.5).toFixed(1)} ${p0.z.toFixed(1)}`);
  await sleep(3000);
  fr = near(b, miniFrames(b), 10);
  check(fr.length === FRAMES && b.entity.position.distanceTo(p0) > 25, 'the frames follow the player 30 blocks east',
    'moved ' + b.entity.position.distanceTo(p0).toFixed(1) + ', frames within 10 blocks ' + fr.length + ' of ' + miniFrames(b).length);
  const ahead = (yawDeg) => {
    const yaw = yawDeg * Math.PI / 180; // mineflayer: yaw 0 looks north (-z), positive turns west
    return { x: -Math.sin(yaw), z: -Math.cos(yaw) };
  };
  await b.look(b.entity.yaw + Math.PI, 0, true); await sleep(2500);
  const dir = ahead(b.entity.yaw * 180 / Math.PI);
  const e2 = b.entity.position.offset(0, 1.62, 0);
  // the look is level after b.look(…, 0): three frames must stand above the eye — an arrow leaves 0.1 under it and
  // only drops a few hundredths in 7.5 blocks (a frame snaps to its block, so a side frame 1.3 up can sit as low as +0.33);
  // the fourth, 24° under the look for quick looks down, at least 2 blocks under it, far off any shot's path; one ahead
  const angles = miniFrames(b).map(f => { const dx = f.position.x - e2.x, dy = f.position.y - e2.y, dz = f.position.z - e2.z, l = Math.hypot(dx, dy, dz) || 1;
    return { a: Math.acos(Math.max(-1, Math.min(1, (dx * dir.x + dz * dir.z) / l))) * 180 / Math.PI, up: dy }; });
  check(angles.length === FRAMES && angles.filter(x => x.up > 0.25).length === FRAMES - 1 && angles.filter(x => x.up < -2).length === 1
    && angles.some(x => x.a < 30), 'after a turn the frames follow, off the line of fire, one ahead',
    angles.map(x => x.a.toFixed(0) + '° ' + (x.up >= 0 ? '+' : '') + x.up.toFixed(1)).join(', '));
  // an arrow shot through a frame flies on (the frame steps aside instead of stopping it)
  const f0 = miniFrames(b)[0];
  const fp = f0.position.clone(), hx = fp.x - e2.x, hz = fp.z - e2.z, hl = Math.hypot(hx, hz);
  const ux = hx / hl, uz = hz / hl, sx = fp.x - ux * 3, sz = fp.z - uz * 3;
  console_(`summon minecraft:arrow ${sx.toFixed(2)} ${fp.y.toFixed(3)} ${sz.toFixed(2)} {Motion:[${(ux * 1.5).toFixed(3)},0.0,${(uz * 1.5).toFixed(3)}],NoGravity:1b,Tags:["mapc_arrow"]}`);
  await sleep(1500);
  const arrows = Object.values(b.entities).filter(e => e.name === 'arrow' && Math.hypot(e.position.x - sx, e.position.z - sz) < 80);
  const past = arrows.map(a => (a.position.x - fp.x) * ux + (a.position.z - fp.z) * uz);
  // a frame that stopped it would hold it on its face (0 blocks past); a wall behind may stop it a few blocks on
  check(past.length > 0 && past.every(d => d > 2), 'an arrow shot through a frame flies on past it',
    arrows.length + ' arrow(s), ' + past.map(d => d.toFixed(1)).join(', ') + ' blocks past the frame');
  console_('kill @e[type=minecraft:arrow,tag=mapc_arrow]');
  // 3. /map minimap hides it and shows it again
  let n = b.chats.length;
  b.chat('/map minimap'); await sleep(2000);
  const off = miniFrames(b).length;
  b.chat('/map minimap'); await sleep(2500);
  const on = miniFrames(b).length;
  check(off === 0 && on === FRAMES && said(b, 'унтраалттай', n) && said(b, 'асаалттай', n), '/map minimap turns it off and on', 'off ' + off + ' frames, on ' + on);
  // 4. the atlas hides it while open
  b.chat('/map'); await sleep(4000);
  const inAtlas = miniFrames(b).length, seated = !!b.vehicle;
  b._client.write('player_input', { inputs: { shift: true } }); await sleep(800); b._client.write('player_input', { inputs: {} }); await sleep(3000);
  check(seated && inAtlas === 0 && miniFrames(b).length === FRAMES, 'the atlas hides the minimap while open', 'seated ' + seated + ', frames in the atlas '
    + inAtlas + ', after ' + miniFrames(b).length);
  // 5. the waypoint: a bar with the distance and a screen card (ScreenMarkers): in view it stands 24 blocks out on the
  // line to the place, turned away it is an edge card 10 blocks out with an arrow; /map stop ends both
  const here = b.entity.position;
  const tx = Math.floor(here.x) + 200, tz = Math.floor(here.z);
  let bar = null;
  b.on('bossBarCreated', (x) => { bar = x; });
  await b.lookAt(here.offset(200, b.entity.height, 0), true); await sleep(500);
  b.chat(`/map guide ${tx} ${tz}`); await sleep(2500);
  const barText = bar ? String(bar.title) : '';
  const card = () => Object.values(b.entities).find(e => e.name === 'text_display' && e.position.distanceTo(b.entity.position) < 40 && JSON.stringify(e.metadata).includes('◆'));
  const label = card();
  check(!!bar && /\d+ м/.test(barText), 'the waypoint bar names the distance', JSON.stringify(barText));
  check(!!label && label.position.distanceTo(b.entity.position) > 15 && label.position.x > here.x + 15, 'the waypoint card stands toward the place',
    label ? label.position.distanceTo(b.entity.position).toFixed(1) + ' blocks away' : 'none');
  await b.lookAt(here.offset(-200, b.entity.height, 0), true); await sleep(1500); // the place now behind
  const edge = card();
  const edgeText = edge ? JSON.stringify(edge.metadata) : '';
  check(!!edge && edge.position.distanceTo(b.entity.position) < 13 && /[⬅➡]/.test(edgeText), 'turned away, the card waits at the screen edge with an arrow',
    edge ? edge.position.distanceTo(b.entity.position).toFixed(1) + ' blocks away, arrow ' + /[⬅➡]/.test(edgeText) : 'none');
  let gone = false;
  b.on('bossBarDeleted', () => { gone = true; });
  b.chat('/map stop'); await sleep(2000);
  const left = Object.values(b.entities).filter(e => e.name === 'text_display' && JSON.stringify(e.metadata).includes('◆')).length;
  check(gone && left === 0, '/map stop removes the bar and the label', 'bar removed ' + gone + ', labels left ' + left);
  // 6. pins: stored, still there after a relog, removed
  n = b.chats.length;
  b.chat('/map pin Тест'); await sleep(2500);
  check(said(b, 'тэмдэг тавигдлаа', n), '/map pin stores a pin', b.chats.slice(n).join(' | ').slice(0, 160));
  b.quit(); await sleep(4000);
  b = join();
  await spawned(b); await sleep(4000);
  n = b.chats.length;
  b.chat('/map pins'); await sleep(2000);
  check(said(b, 'Тест', n), 'the pin is there after a relog (database)', b.chats.slice(n).join(' | ').slice(0, 160));
  n = b.chats.length;
  b.chat('/map unpin Тест'); await sleep(2500);
  b.chat('/map pins'); await sleep(1500);
  check(said(b, 'устлаа', n) && said(b, 'Тэмдэг алга', n), '/map unpin removes it', b.chats.slice(n).join(' | ').slice(0, 160));
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})().catch((e) => { console.log('FAIL crashed :: ' + e.stack); process.exit(1); });
