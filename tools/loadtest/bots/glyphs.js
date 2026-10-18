// The pack's glyph strings, read from ui/Glyphs.java at run time: a background the pack cuts into tiles
// (tools/pack/glyph_tiles.py) is a string of tile glyphs and shifts, and a bot that copied an old code point by hand
// would look for a glyph the pack no longer has.
const fs = require('fs');
const path = require('path');
const SRC = path.join(__dirname, '..', '..', '..', 'suld-plugin', 'src', 'main', 'java', 'mn', 'suld', 'plugin', 'ui', 'Glyphs.java');
const glyphs = {};
for (const m of fs.readFileSync(SRC, 'utf8').matchAll(/static final String (\w+) = "((?:\\u[0-9A-Fa-f]{4})+)"/g)) {
  glyphs[m[1]] = m[2].replace(/\\u([0-9A-Fa-f]{4})/g, (_, h) => String.fromCharCode(parseInt(h, 16)));
}
module.exports = glyphs;
