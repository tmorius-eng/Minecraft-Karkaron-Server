// The Bonum credit store end to end against the local stand-in (tools/payments/bonum_stub.py), one offline bot P:
//   node paybot.js <srvctl.sh> [port] [stubPort]
// Needs the dev server started with payment.bonum on, base-url http://127.0.0.1:<stubPort> and the stub's three dev
// variables (docs/PAYMENT_ARCHITECTURE.md §Testing). The stub is not Bonum: this proves SÜLD's half of the path only.
// /buy → a package → the QR map in hand (and it decodes to the stub's code) → an unsigned, a tampered and a
// wrong-amount webhook grant nothing → the signed payment grants the credits once, with the toast line, and takes the
// map → three replays change nothing → the map cannot be dropped → two open intents at most → the staff line → after a
// relog the credits stand.
const mineflayer = require('mineflayer');
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const http = require('http');
const [ctl, port = '25572', stubPort = '18086'] = process.argv.slice(2);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
const results = [];
const check = (ok, what, info) => { results.push(ok); console.log((ok ? 'PASS ' : 'FAIL ') + what + ' :: ' + info); };
const name = 'Py' + Math.floor(Math.random() * 90000 + 10000);
const cmd = (c) => execFileSync(ctl, ['cmd', c]);
const LOG = (() => { const t = fs.readFileSync(ctl, 'utf8').match(/SRV=(\S+)/); return t ? path.join(t[1], 'logs', 'latest.log') : null; })();
const logLines = () => { const a = fs.readFileSync(LOG, 'utf8').split('\n'); a.pop(); return a; };
const ask = async (command, re, wait = 1500) => { const n = logLines().length; cmd(command); await sleep(wait); return logLines().slice(n).filter(l => re.test(l)); };
const QRDIR = process.env.QR_OUT || '/tmp';

function stub(method, p, body) {
  return new Promise((res, rej) => {
    const data = body ? Buffer.from(JSON.stringify(body)) : null;
    const req = http.request({ host: '127.0.0.1', port: Number(stubPort), path: p, method, headers: data ? { 'Content-Type': 'application/json', 'Content-Length': data.length } : {} }, (r) => {
      let s = ''; r.on('data', (c) => s += c); r.on('end', () => { try { res({ code: r.statusCode, json: JSON.parse(s || '{}') }); } catch (e) { res({ code: r.statusCode, json: {} }); } });
    });
    req.on('error', rej);
    if (data) req.write(data);
    req.end();
  });
}

function join(n) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: n, version: '1.21.11', auth: 'offline', hideErrors: true });
  b.on('error', () => {});
  b.on('resourcePack', () => { try { b.acceptResourcePack(); } catch (e) {} });
  b.chatLog = [];
  b.on('messagestr', (m) => b.chatLog.push(m));
  b.maps = new Map();
  b._client.on('map', (pk) => { if (pk.data && pk.columns === 128 && pk.rows === 128) b.maps.set(pk.itemDamage, Buffer.from(pk.data)); });
  return new Promise((r, j) => { b.once('spawn', () => r(b)); setTimeout(() => j(new Error('no spawn ' + n)), 60000); });
}
async function pickClass(b) {
  for (let i = 0; i < 40 && !b.currentWindow; i++) await sleep(500);
  if (b.currentWindow) { await b.clickWindow(10, 0, 0).catch(() => {}); await sleep(600); await b.clickWindow(11, 0, 0).catch(() => {}); await sleep(1200); }
  if (b.currentWindow) b.closeWindow(b.currentWindow);
}
const said = (b, from, re) => b.chatLog.slice(from).some(l => re.test(l));
const credits = async (b) => { const k = b.chatLog.length; b.chat('/credits'); await sleep(1200); const l = b.chatLog.slice(k).find(x => /Сүлд Кредит: \d+/.test(x)); return l ? +l.match(/Сүлд Кредит: (\d+)/)[1] : -1; };
const qrMaps = (b) => b.inventory.items().filter(i => i.name === 'filled_map' && /Bonum QR/.test(JSON.stringify(i.customName || '')));
async function buy(b, slot) {
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(300);
  const k = b.chatLog.length;
  const opened = new Promise((r) => { b.once('windowOpen', r); setTimeout(() => r(null), 5000); });
  b.chat('/buy');
  if (await opened) { await sleep(300); await b.clickWindow(slot, 0, 0).catch(() => {}); } // the window /buy just opened
  for (let i = 0; i < 24 && !said(b, k, /Төлбөр: |дуусаагүй|хариу өгсөнгүй|нээгдээгүй|секунд хүлээнэ/); i++) await sleep(250);
  return k;
}
/** The map's 128×128 colour indices as a PGM (two colours: the lighter is the light module). */
function pgm(buf, file) {
  const counts = new Map();
  for (const v of buf) counts.set(v, (counts.get(v) || 0) + 1);
  const corner = buf[0];
  const out = Buffer.alloc(128 * 128);
  for (let i = 0; i < out.length; i++) out[i] = buf[i] === corner ? 255 : 0;
  const s = 4, w = 128 * s, img = Buffer.alloc(w * w);
  for (let y = 0; y < w; y++) for (let x = 0; x < w; x++) img[y * w + x] = out[(y >> 2) * 128 + (x >> 2)];
  fs.writeFileSync(file, Buffer.concat([Buffer.from('P5\n' + w + ' ' + w + '\n255\n'), img]));
  return counts.size;
}

(async () => {
  let b = await join(name);
  await pickClass(b);
  await sleep(2500);
  const c0 = await credits(b);
  check(c0 >= 0, 'the player reads their credits', 'credits ' + c0);

  // --- /buy → the first package: an intent, the QR on a map in hand, bank links in chat
  let k = await buy(b, 10);
  await sleep(1500);
  const maps0 = qrMaps(b);
  const last = await stub('GET', '/stub/last');
  check(said(b, k, /Төлбөр: 100 Сүлд Кредит/) && maps0.length === 1 && b.heldItem && b.heldItem.name === 'filled_map',
    '/buy gives the QR map in hand and the price line', 'maps ' + maps0.length + ' · held ' + (b.heldItem && b.heldItem.name) + ' · ' + b.chatLog.slice(k).join(' | ').slice(0, 160));
  check(last.code === 200 && /^[0-9a-f]{32}$/.test(last.json.transactionId || '') && last.json.amount === 5000,
    'the invoice carries the intent reference and the package amount only', JSON.stringify(last.json).slice(0, 160));
  check(said(b, k, /Банкны апп: \[Stub bank\]/) && !said(b, k, /\[bad\]/), 'only safe bank links are shown', b.chatLog.slice(k).filter(l => /Банкны/.test(l)).join(' | '));

  // --- the map the player sees is a QR code that decodes to the stub's code
  await sleep(1500);
  let decoded = '';
  const mapId = maps0[0] && (maps0[0].componentMap && maps0[0].componentMap.get('map_id') ? maps0[0].componentMap.get('map_id').data : null);
  const buf = mapId != null ? b.maps.get(mapId) : [...b.maps.values()].pop();
  if (buf) {
    const f = path.join(QRDIR, 'paybot-map.pgm');
    const colours = pgm(buf, f);
    try { decoded = execFileSync(process.env.QR_PY || 'python3', [path.join(__dirname, '..', '..', 'payments', 'qr_decode.py'), f]).toString().trim(); } catch (e) { decoded = 'decoder failed: ' + e.message.split('\n')[0]; }
    console.log('map colours ' + colours);
  }
  const want = 'SULD-STUB|' + last.json.invoiceId + '|5000|' + last.json.transactionId;
  check(decoded === want, 'the QR on the map decodes to the code Bonum gave', JSON.stringify(decoded).slice(0, 120));

  // --- what never pays: no signature, a changed body, another amount
  const inv = last.json.invoiceId;
  let r = await stub('POST', '/stub/pay', { invoiceId: inv, unsigned: true });
  check(r.json.codes && r.json.codes[0] === 401 && (await credits(b)) === c0, 'an unsigned webhook is refused and grants nothing', JSON.stringify(r.json.codes));
  r = await stub('POST', '/stub/pay', { invoiceId: inv, tamper: true });
  check(r.json.codes && r.json.codes[0] === 401 && (await credits(b)) === c0, 'a tampered body is refused and grants nothing', JSON.stringify(r.json.codes));
  r = await stub('POST', '/stub/pay', { invoiceId: inv, amount: 50 });
  check(r.json.codes && r.json.codes[0] === 200 && /MISMATCH/.test(r.json.bodies[0]) && (await credits(b)) === c0,
    'a signed payment of another amount grants nothing', (r.json.bodies || []).join(' '));

  // --- the map stays the player's: it cannot be dropped
  if (b.heldItem && b.heldItem.name === 'filled_map') { await b.tossStack(b.heldItem).catch(() => {}); await sleep(1200); }
  check(qrMaps(b).length === 1, 'the QR map cannot be dropped', 'maps ' + qrMaps(b).length);

  // --- the signed payment: credits once, the toast line, the map taken
  k = b.chatLog.length;
  r = await stub('POST', '/stub/pay', { invoiceId: inv });
  await sleep(1500);
  const c1 = await credits(b);
  check(r.json.codes[0] === 200 && /CONFIRMED/.test(r.json.bodies[0]) && c1 === c0 + 100 && said(b, k, /Төлбөр баталгаажлаа: \+100 Сүлд Кредит/),
    'the signed payment grants 100 credits with the confirmation line', 'credits ' + c0 + ' -> ' + c1 + ' · ' + (r.json.bodies || []).join(' '));
  check(qrMaps(b).length === 0, 'the QR map is taken once paid', 'maps ' + qrMaps(b).length);
  r = await stub('POST', '/stub/pay', { invoiceId: inv, repeat: 3 });
  await sleep(800);
  check(r.json.codes.every(c => c === 200) && r.json.bodies.every(x => /DUPLICATE/.test(x)) && (await credits(b)) === c0 + 100,
    'three replays of the paid webhook change nothing', JSON.stringify(r.json.codes) + ' ' + (r.json.bodies || []).map(x => (x.match(/"outcome":"(\w+)"/) || [])[1]).join(','));

  // --- two open intents at most (one purchase every 20 s)
  k = await buy(b, 11);
  check(said(b, k, /секунд хүлээнэ/), 'a second purchase within 20 s waits', b.chatLog.slice(k).join(' | ').slice(0, 120));
  await sleep(20500);
  await buy(b, 11); await sleep(21000);
  await buy(b, 11); await sleep(21000);
  k = await buy(b, 11); await sleep(1500);
  check(said(b, k, /дуусаагүй 2 төлбөр/), 'a third open intent is refused', b.chatLog.slice(k).join(' | ').slice(0, 160));

  // --- staff see the player's intents; after a relog the credits stand and stray QR maps are gone
  const lines = await ask(`suld payment ${name}`, /payments |CREDITS_/);
  check(lines.some(l => /gateway on, [3-9] intent/.test(l)) && lines.some(l => /CREDITS_100 5000 MNT CONFIRMED fulfilled/.test(l)),
    'the staff line lists the intents, the paid one fulfilled', lines.slice(0, 3).join(' | ').slice(0, 200));
  b.quit(); await sleep(3000);
  b = await join(name); await sleep(4000);
  check((await credits(b)) === c0 + 100 && qrMaps(b).length === 0, 'after a relog the credits stand and no QR map is left', 'maps ' + qrMaps(b).length);

  console.log(results.every(Boolean) ? `ALL ${results.length} PASSED` : `${results.filter(x => !x).length} FAILED`);
  b.quit(); process.exit(results.every(Boolean) ? 0 : 1);
})().catch((e) => { console.log('FAIL crashed :: ' + e.stack); process.exit(1); });
