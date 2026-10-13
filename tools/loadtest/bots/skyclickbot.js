// A click in a sky window is the window's own, one offline bot:
//   node skyclickbot.js <srvctl.sh> [port]
// The windows cancel the interaction, but the arm swing and the bow's right click still reached the spell engine: a
// click on a tab could make a combo and cast up on the stage, and Мэргэн's right click shot an arrow from the sky. The
// bot plays Мэргэн: on the ground a right click shoots (the check works), in the atlas and in /menu it shoots nothing,
// and an R-L-R typed in the atlas casts nothing either; back on the ground it shoots again.
const mineflayer = require('mineflayer');
const [ctl, port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'S' + Math.floor(Math.random() * 90000 + 10000);
const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
b.on('error', () => {});
b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
let arrows = 0;
b.on('entitySpawn', (e) => { if (e.name === 'arrow') arrows++; });
const R = async () => { b.activateItem(); await sleep(60); b.deactivateItem(); };
const L = async () => { b.swingArm('right'); };
const shots = async (n) => { const a0 = arrows; for (let i = 0; i < n; i++) { await R(); await sleep(1100); } await sleep(800); return arrows - a0; };

b.once('spawn', async () => {
  try {
    for (let i = 0; i < 100 && !b.currentWindow; i++) await sleep(500);
    if (b.currentWindow) { await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(700); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500); }
    if (b.currentWindow) b.closeWindow(b.currentWindow);
    await sleep(2000);
    const bow = b.inventory.items().find(i => i.name === 'bow');
    if (bow) await b.equip(bow, 'hand').catch(() => {});
    await sleep(1500);
    check(!!bow && await shots(1) === 1, 'Мэргэн on the ground: a right click shoots', bow ? 'bow in hand' : 'no bow');
    // the atlas
    b.chat('/map'); await sleep(4500);
    const seated = !!b.vehicle;
    const inAtlas = await shots(3);
    const a0 = arrows;
    for (const k of ['R', 'L', 'R', 'L', 'R', 'L']) { if (k === 'R') await R(); else await L(); await sleep(250); }
    await sleep(1500);
    check(seated && inAtlas === 0 && arrows === a0, 'in the atlas a right click shoots nothing and R-L-R casts nothing',
      'seated ' + seated + ', arrows ' + inAtlas + ' + ' + (arrows - a0));
    b._client.write('player_input', { inputs: { shift: true } }); await sleep(800); b._client.write('player_input', { inputs: {} }); await sleep(2500);
    // the menu's sky window
    b.chat('/menu'); await sleep(3500);
    const menuSeated = !!b.vehicle;
    const inMenu = await shots(2);
    check(menuSeated && inMenu === 0, 'in /menu a right click shoots nothing', 'seated ' + menuSeated + ', arrows ' + inMenu);
    b._client.write('player_input', { inputs: { shift: true } }); await sleep(800); b._client.write('player_input', { inputs: {} }); await sleep(2500);
    // mineflayer keeps a stale vehicle after the seat goes: the height tells (the stages stand over y 400)
    const y = b.entity.position.y;
    check(y < 300 && await shots(1) === 1, 'back on the ground it shoots again', 'y ' + y.toFixed(0));
  } catch (e) {
    check(false, 'crashed', e.stack);
  }
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
});
