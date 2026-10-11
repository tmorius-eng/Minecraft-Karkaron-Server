// Full-screen skill tree (SkillSky) live QA: node skybot.js <ctl> <name>
const mineflayer = require('mineflayer'); const fs = require('fs'); const { execFileSync } = require('child_process'); const { Vec3 } = require('vec3');
const [ctl, name] = process.argv.slice(2); const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const LOG = '/tmp/claude-0/-home-user-Minecraft-Karkaron-Server/f299e7b1-34c0-5873-afc5-97208b93251d/scratchpad/srv2/logs/latest.log';
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const lines = () => fs.readFileSync(LOG, 'utf8').split('\n');
async function server(c, re, wait = 1500) { const n = lines().length - 1; cmd(c); const end = Date.now() + Math.max(wait, 3500); await sleep(Math.min(wait, 400)); let out = []; while (Date.now() < end) { out = lines().slice(n).filter(l => !re || re.test(l)); if (re && out.length) break; await sleep(150); } return out.map(l => l.replace(/^\[[\d:]+\] \[[^\]]+\]: /, '')); }
const count = async (sel) => { const r = (await server(`execute if entity ${sel}`, /Test (passed|failed)/, 900)).join(' '); const m = r.match(/[Cc]ount: (\d+)/); return m ? +m[1] : 0; };
const chat = []; let clicks = 0;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25571, username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', () => { try { bot.acceptResourcePack(); } catch (e) {} });
bot.on('messagestr', (m) => chat.push(m));
bot.on('soundEffectHeard', () => clicks++); bot.on('hardcodedSoundEffectHeard', () => clicks++); bot._client.on('sound_effect', () => clicks++);
const step = (k, v) => console.log(k, JSON.stringify(v));
bot.once('spawn', async () => {
  try {
    await sleep(5000);
    if (bot.currentWindow) { await bot.clickWindow(10, 0, 0).catch(() => {}); await sleep(2500); }
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    cmd('fill 2985 99 2985 3021 99 3021 stone'); cmd('fill 2985 100 2985 3021 106 3021 air');
    cmd(`tp ${name} 3003 100 3003`); await sleep(2000);
    cmd(`suld exp ${name} 6000`); await sleep(1500);
    const before = bot.entity.position.clone(); step('before', before);
    const dispBefore = await count('@e[tag=suld_skysky]');
    chat.length = 0; bot.chat('/skills tree'); await sleep(2500);
    step('chatOnOpen', chat.slice(-3));
    step('skyEntities', await count('@e[tag=suld_skysky]'));
    step('riding', !!bot.vehicle, 'pos', bot.entity.position);
    const seen = Object.values(bot.entities).filter(e => e.name === 'item_display' || e.name === 'text_display' || e.name === 'interaction').length;
    step('entitiesVisibleToBot', seen);
    // aim: find the node via the interaction entities the bot can see (sorted by distance to where m1 should be)
    const hits = Object.values(bot.entities).filter(e => e.name === 'interaction');
    step('interactions', hits.length);
    const eye = bot.entity.position.offset(0, bot.entity.height * 0.9, 0);
    // each interaction box: centre = pos + (0, h/2, 0); pick the one nearest the root+1 ring straight up (smallest |dx|, lowest y above the root)
    const centres = hits.map(h => h.position.offset(0, 0.31, 0));
    const rootY = Math.min(...centres.filter(c => Math.abs(c.x - eye.x) < 0.2).map(c => c.y));
    const m1 = centres.filter(c => Math.abs(c.x - eye.x) < 0.2 && c.y > rootY + 0.5).sort((a, b) => a.y - b.y)[0];
    step('rootY', rootY, 'target', m1);
    const insp0 = await server(`skillsadmin inspect ${name}`, /нод:|оноо:/, 1200);
    step('inspectBefore', insp0);
    // NB: a console command does not close the player's tree; check it is still open
    step('stillRiding', !!bot.vehicle);
    // mineflayer sends no rotation while riding: write the look packet like a real client does
    const eyeY = rootY + 1.9, dz = m1.z - eye.z; const look = (yawDeg, pitchDeg) => bot._client.write('look', { yaw: yawDeg, pitch: pitchDeg, flags: { onGround: false, hasHorizontalCollision: false } });
    const pitch = Math.atan2(eyeY - m1.y, dz) * 180 / Math.PI; step('pitch', pitch);
    const c0 = clicks; look(0, pitch); await sleep(600);
    step('hoverSound', clicks > c0);
    await sleep(400);
    { const e2 = bot.entity.position.offset(0, 0, 0); const tds = Object.values(bot.entities).filter(e => e.name === 'text_display');
      const near = tds.map(t => ({ d: +t.position.distanceTo(eye).toFixed(2), dx: +(t.position.x - eye.x).toFixed(2), dy: +(t.position.y - eye.y).toFixed(2), dz: +(t.position.z - eye.z).toFixed(2) }));
      step('textDisplays', near); }
    bot.swingArm('right'); await sleep(900);
    const insp1 = await server(`skillsadmin inspect ${name}`, /нод:|оноо:/, 1200);
    step('inspectAfterClick', insp1);
    // right click the same node: refund
    const hit = hits.sort((a, b) => a.position.offset(0, 0.31, 0).distanceTo(m1) - b.position.offset(0, 0.31, 0).distanceTo(m1))[0];
    bot._client.write('use_entity', { target: hit.id, mouse: 0, hand: 0, sneaking: false }); await sleep(900);
    step('inspectAfterRightClick', await server(`skillsadmin inspect ${name}`, /нод:/, 1200));
    look(0, pitch); await sleep(300); bot.swingArm('right'); await sleep(700); // learn it again for the visuals
    // pan with W for 1 s
    const p0 = bot.entity.position.clone(); bot._client.write('player_input', { inputs: { forward: true } }); await sleep(1000); bot._client.write('player_input', { inputs: {} }); await sleep(400);
    step('panUp', +(bot.entity.position.y - p0.y).toFixed(2));
    // zoom with the wheel (hotbar change)
    const z0 = bot.entity.position.z; bot.setQuickBarSlot(3); await sleep(600); step('zoomDz', +(bot.entity.position.z - z0).toFixed(2), 'heldSlot', bot.quickBarSlot);
    // close with sneak
    bot.setControlState('sneak', true); await sleep(800); bot.setControlState('sneak', false); await sleep(1200);
    step('afterClose', { riding: !!bot.vehicle, pos: bot.entity.position, back: bot.entity.position.distanceTo(before) < 0.6 });
    step('skyEntitiesAfter', await count('@e[tag=suld_skysky]'), 'before', dispBefore);
    step('errors', lines().filter(l => /Exception|SEVERE|ERROR\]: .*SULD/.test(l)).slice(-5));
  } catch (e) { console.log('ERR', e.stack); }
  bot.quit();
});
bot.on('end', () => process.exit(0)); setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 90000);
