// The resource pack only dresses the UI; it must never gate the class system (regression for fc0c47e).
//   node packmodesbot.js <srvctl.sh> <mode> [port]     mode: nil | foreign | none
// nil:     the client answers with the nil pack id (Mineflayer, some clients) -> glyph HUD
// foreign: the client only answers for ANOTHER pack id -> SÜLD's status unknown -> plain HUD fallback
// none:    the client never answers -> the onboarding gate times out (30 s) -> plain HUD fallback
// In every mode: the class menu opens, a class can be picked (class weapon given) and /class opens the academy.
const mineflayer = require('mineflayer');
const crypto = require('crypto');
const [ctl, mode = 'nil', port = '25572'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + '[' + mode + '] ' + what + ' :: ' + info); };
const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: 'P' + mode[0] + Math.floor(Math.random() * 900 + 100), version: '1.21.11', auth: 'offline', hideErrors: true });
b.on('error', () => {});
b.on('resourcePack', () => {
  if (mode === 'nil') { try { b.acceptResourcePack(); } catch (e) {} }
  else if (mode === 'foreign') {
    const id = crypto.randomUUID();
    b._client.write('resource_pack_receive', { uuid: id, result: 3 }); // accepted
    b._client.write('resource_pack_receive', { uuid: id, result: 0 }); // successfully loaded
  } // none: silence
});
let lastBar = '';
b._client.on('action_bar', (pk) => { lastBar = JSON.stringify(pk.text); });
const glyphs = () => (lastBar.match(/\\\\u[eE]0[0-9a-fA-F]{2}|[-]/g) || []).length;
const texts = () => Object.values(b.entities).filter(e => e.name === 'text_display' && e.position.y > 300).map(e => JSON.stringify(e.metadata));

b.once('spawn', async () => {
  const t0 = Date.now();
  for (let i = 0; i < 100 && !b.currentWindow; i++) await sleep(500);
  check(!!b.currentWindow, 'the class menu opens', b.currentWindow ? ((Date.now() - t0) / 1000).toFixed(0) + ' s after spawn' : 'no window in 50 s');
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(700); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1500); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(2500);
  check(b.inventory.items().some(i => i.name.includes('sword')), 'a class can be picked (the Баатар sword is given)', b.inventory.items().map(i => i.name).join(','));
  const g = glyphs();
  check(mode === 'nil' ? g > 20 : g === 0 && lastBar.length > 10, mode === 'nil' ? 'the glyph HUD is drawn (pack loaded)' : 'the plain HUD fallback is drawn (pack status unknown)', 'glyphs ' + g + ', bar ' + lastBar.length + ' chars');
  b.chat('/class'); await sleep(3500);
  check(texts().some(t => t.includes('АНГИЙН АКАДЕМИ')), '/class opens «Ангийн Академи»', texts().length + ' window texts');
  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
});
