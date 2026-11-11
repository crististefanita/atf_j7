const fs = require('node:fs');
const { createHash } = require('node:crypto');
const { dirname, join, resolve } = require('node:path');

const [modulePath, inputPath, oraclePath] = process.argv.slice(2);
if (!modulePath || !inputPath) throw Error('Usage: node verify-quest-oracle.cjs <chess.js CJS file> <quest TSV or -> [quest-oracle.yaml]');
const engine = resolve(modulePath);
let folder = dirname(engine);
while (!fs.existsSync(join(folder, 'package.json')) && folder !== dirname(folder)) folder = dirname(folder);
const manifest = join(folder, 'package.json');
const pkg = fs.existsSync(manifest) ? JSON.parse(fs.readFileSync(manifest, 'utf8')) : {};
if (pkg.name !== 'chess.js' || pkg.version !== '1.4.0') throw Error('Use the CommonJS file from the chess.js 1.4.0 package.');
const { Chess } = require(engine);
const source = fs.readFileSync(inputPath === '-' ? 0 : inputPath, 'utf8').trim();
if (!source) throw Error('Empty mate-in-one oracle input');
const rows = source.split(/\r?\n/).map(line => {
  const fields = line.split('\t');
  const [id, fen, solution, theme] = fields;
  if (fields.length !== 4 || !/^mate-\d{3,}$/.test(id) || !fen ||
      !/^[a-h][1-8][a-h][1-8][qrbn]?$/.test(solution) || !/^(TACTIC|PROMOTION|EN_PASSANT|CASTLING)$/.test(theme)) {
    throw Error('Malformed mate-in-one oracle entry: ' + id);
  }
  return fields;
});
rows.sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0);
const ids = new Set();
const keys = new Set();
const normalized = [];
const explanations = [];
for (const [id, fen, expected, theme] of rows) {
  if (ids.has(id)) throw Error('Repeated ID: ' + id);
  ids.add(id);
  const key = fen.split(' ').slice(0, 4).join(' ');
  if (keys.has(key)) throw Error('Repeated position: ' + id);
  keys.add(key);
  const board = new Chess(fen);
  if ((id === 'mate-101' || id === 'mate-102') && !board.isCheck()) throw Error('Countermate must start in check at ' + id);
  const legal = board.moves({ verbose: true });
  const mates = legal.filter(move => move.san.endsWith('#'));
  if (mates.length !== 1 || mates[0].lan !== expected) throw Error('Wrong mate at ' + id);
  const mate = mates[0];
  const actualTheme = mate.isEnPassant() ? 'EN_PASSANT' : mate.isKingsideCastle() || mate.isQueensideCastle() ? 'CASTLING' : mate.isPromotion() ? 'PROMOTION' : 'TACTIC';
  if (actualTheme !== theme) throw Error('Wrong theme at ' + id);
  board.move({ from: mate.from, to: mate.to, promotion: mate.promotion });
  if (!board.isCheckmate() || board.moves().length) throw Error('Mate has a defense at ' + id);
  normalized.push(id + ' ' + legal.map(move => move.lan).sort().join(','));
  const king = board.board().flat().find(piece => piece?.type === 'k' && piece.color === board.turn()).square;
  const attackers = square => board.attackers(square, mate.color).sort().join(',');
  const exits = [];
  for (let file = king.charCodeAt(0) - 1; file <= king.charCodeAt(0) + 1; file++) {
    for (let rank = Number(king[1]) - 1; rank <= Number(king[1]) + 1; rank++) {
      const square = String.fromCharCode(file) + rank;
      if (!/^[a-h][1-8]$/.test(square) || square === king) continue;
      const occupant = board.get(square);
      if (occupant?.color === board.turn()) {
        exits.push(square + '=own');
      } else {
        const step = new Chess(board.fen());
        step.remove(king);
        if (occupant) step.remove(square);
        if (!step.put({ type: 'k', color: board.turn() }, square)) throw Error('Could not model king flight at ' + id);
        const threats = step.attackers(square, mate.color).sort();
        if (!threats.length) throw Error('Unexplained king flight at ' + id + ' ' + square);
        exits.push(square + '=' + threats.join(','));
      }
    }
  }
  explanations.push(id + ' ' + king + ' ' + attackers(king) + ' ' + exits.join(' '));
}
const sha = createHash('sha256').update(normalized.join('\n') + '\n').digest('hex');
const proofSha = createHash('sha256').update(explanations.join('\n') + '\n').digest('hex');
if (oraclePath) {
  const oracle = fs.readFileSync(oraclePath, 'utf8');
  if (!oracle.includes('legal-moves-sha256: ' + sha)) throw Error('Legal move fingerprint changed');
  if (!oracle.includes('mate-proof-sha256: ' + proofSha)) throw Error('Mate explanation fingerprint changed');
}
console.log('Verified ' + rows.length + ' unique mates and their complete legal move sets.');
console.log('legal-moves-sha256: ' + sha);
console.log('mate-proof-sha256: ' + proofSha);
