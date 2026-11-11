const fs = require('node:fs');
const { createHash } = require('node:crypto');
const { join, resolve } = require('node:path');

const [modulePath, input, output, imported] = process.argv.slice(2);
if (!modulePath || !input) throw Error('Usage: node verify-mate-in-two.cjs <chess.js> <quests.yaml> [sha256] or <chess.js> --import <source-pages> <quests.yaml>');
const { Chess } = require(resolve(modulePath));
const move = (board, uci) => board.move({ from: uci.slice(0, 2), to: uci.slice(2, 4), promotion: uci[4] });
const square = index => 'abcdefgh'[index & 7] + (8 - (index >> 4));
const uci = candidate => square(candidate.from) + square(candidate.to) + (candidate.promotion || '');
const legal = board => board._moves();
const mates = (board, moves) => moves.filter(candidate => after(board, candidate, () => board.isCheckmate()));
const byId = (a, b) => a < b ? -1 : a > b ? 1 : 0;
const key = fen => fen.split(' ').slice(0, 4).join(' ');
const upperRatings = [1200, 1450, 1700, 1900, Infinity];
const chapterSize = 300;

function forcedLines(board, opening) {
  return after(board, opening, () => {
    const proof = [];
    const replies = legal(board);
    if (!replies.length) return [];
    for (const reply of replies) {
      const ends = after(board, reply, () => mates(board, legal(board)).map(uci).sort());
      if (!ends.length) return [];
      proof.push([uci(reply), ends]);
    }
    return proof.sort((a, b) => byId(a[0], b[0]));
  });
}

// Use the same chess.js 1.4.0 legal generator as the mate-in-three verifier.
function after(board, candidate, visit) {
  board._makeMove(candidate);
  try { return visit(); } finally { board._undoMove(); }
}

function validate(board, [opening, defense, finish]) {
  const options = legal(board);
  const key = options.find(candidate => uci(candidate) === opening);
  if (!key || mates(board, options).length) return null;
  const proof = forcedLines(board, key);
  if (!proof.some(([reply, ends]) => reply === defense && ends.includes(finish))) return null;
  if (options.some(candidate => candidate !== key && forcedLines(board, candidate).length)) return null;
  return proof;
}

function select(source) {
  const pages = fs.readdirSync(source).filter(name => /^rows-\d+\.json$/.test(name)).sort((a, b) => a.slice(5, -5) - b.slice(5, -5));
  const counts = new Map();
  const positions = new Set();
  const entries = [];
  const rows = pages.flatMap(page => JSON.parse(fs.readFileSync(join(source, page), 'utf8')).rows.map(item => item.row));
  const cutoff = upperRatings.at(-2);
  const advanced = rows.filter(row => row.Rating >= cutoff).sort((a, b) => b.Rating - a.Rating || byId(a.PuzzleId, b.PuzzleId));
  for (const row of advanced.concat(rows.filter(row => row.Rating < cutoff))) {
    const [setup, ...line] = row.Moves.split(' ');
    if (!row.Themes.split(' ').includes('mateIn2') || line.length !== 3 || row.Popularity < 60) continue;
    const board = new Chess(row.FEN);
    move(board, setup);
    const fen = board.fen();
    const bracket = upperRatings.findIndex(upper => row.Rating < upper);
    const bucket = board.turn() + bracket;
    const used = counts.get(bucket) || 0;
    if (used === chapterSize / 10 || positions.has(key(fen)) || !validate(board, line)) continue;
    positions.add(key(fen));
    counts.set(bucket, used + 1);
    entries.push({ id: 'mate2-' + row.PuzzleId, fen, line, rating: row.Rating });
    if (entries.length === chapterSize) return entries.sort((a, b) => a.rating - b.rating || byId(a.id, b.id));
  }
  throw Error('Incomplete selection: ' + JSON.stringify(Object.fromEntries(counts)));
}

if (input === '--import') {
  const entries = select(output);
  const rows = entries.map(({ id, fen, line, rating }) => `  - id: ${id}\n    fen: "${fen}"\n    line: [${line.join(', ')}]\n    rating: ${rating}`);
  fs.writeFileSync(imported, 'schema: 1\nobjective: MATE_IN_TWO\nquests:\n' + rows.join('\n') + '\n');
} else {
  const rows = [...fs.readFileSync(input, 'utf8').replaceAll('\r\n', '\n').matchAll(/^  - id: (mate2-[A-Za-z0-9]{5})\n    fen: "([^"\n]+)"\n    line: \[([a-h1-8qrbn, ]+)\]\n    rating: (\d+)$/gm)];
  if (rows.length !== chapterSize) throw Error('Expected exactly ' + chapterSize + ' canonical mate-in-two entries');
  const positions = new Set();
  const ids = new Set();
  const fingerprints = [];
  let alternatives = 0;
  for (const [, id, fen, line] of rows) {
    if (ids.has(id) || positions.has(key(fen))) throw Error('Duplicate entry: ' + id);
    ids.add(id);
    positions.add(key(fen));
    const [opening, defense, finish] = line.split(', ');
    const proof = validate(new Chess(fen), [opening, defense, finish]);
    if (!proof) throw Error('Mate in two is not unique and forced at ' + id);
    if (proof.length > 1) alternatives++;
    fingerprints.push(id + ' ' + opening + ' ' + proof.map(([reply, ends]) => reply + '=' + ends.join(',')).join(' '));
  }
  const hash = createHash('sha256').update(fingerprints.join('\n') + '\n').digest('hex');
  if (output && fs.readFileSync(output, 'utf8').trim() !== hash) throw Error('Mate-in-two proof fingerprint changed');
  console.log(`Verified ${rows.length} unique forced mates in two (${alternatives} with multiple defenses).`);
  console.log(hash);
}
