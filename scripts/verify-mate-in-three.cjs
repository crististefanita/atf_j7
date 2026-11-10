const fs = require('node:fs');
const { createHash } = require('node:crypto');
const { join, resolve } = require('node:path');

const [modulePath, input, output, oracle] = process.argv.slice(2);
if (!modulePath || !input) throw Error('Usage: node verify-mate-in-three.cjs <chess.js 1.4.0> <catalog> [proof] or <chess.js> --import <source-pages> <catalog> [proof]');
const { Chess } = require(resolve(modulePath));
const compare = (a, b) => a < b ? -1 : a > b ? 1 : 0;
const square = index => 'abcdefgh'[index & 7] + (8 - (index >> 4));
const uci = move => square(move.from) + square(move.to) + (move.promotion || '');
const key = fen => fen.split(' ').slice(0, 4).join(' ');

// chess.js 1.4.0 raw moves use 0x88 squares. Search with its legal generator
// directly so the exhaustive proof does not format SAN at every position.
function after(board, move, visit) {
  board._makeMove(move);
  try { return visit(); } finally { board._undoMove(); }
}

function wins(board, move, budget) {
  return after(board, move, () => {
    if (budget === 1) return board.isCheckmate();
    const replies = board._moves();
    if (!replies.length) return board.isCheck();
    return replies.every(reply => after(board, reply, () => board._moves().some(next => wins(board, next, budget - 1))));
  });
}

function proof(board, line) {
  const openings = board._moves();
  const expected = openings.find(move => uci(move) === line[0]);
  if (!expected || !wins(board, expected, 3) || wins(board, expected, 2)) return null;
  if (openings.some(move => move !== expected && wins(board, move, 3))) return null;
  return after(board, expected, () => {
    const rows = board._moves().map(reply => after(board, reply, () => {
      const keys = board._moves().filter(move => wins(board, move, 2));
      if (uci(reply) === line[1]) {
        const second = keys.find(move => uci(move) === line[2]);
        if (keys.length !== 1 || !second || !after(board, second, () => {
          const defense = board._moves().find(move => uci(move) === line[3]);
          return defense && after(board, defense, () => board._moves().some(move => uci(move) === line[4] && wins(board, move, 1)));
        })) return null;
      }
      return uci(reply) + '=' + keys.map(uci).sort().join(',');
    }));
    if (rows.includes(null) || !rows.some(row => row.startsWith(line[1] + '='))) return null;
    return rows.sort().join(' ');
  });
}

function select(source) {
  const pages = fs.readdirSync(source).filter(name => /^rows-\d+\.json$/.test(name)).sort((a, b) => a.slice(5, -5) - b.slice(5, -5));
  const counts = { w: 0, b: 0 };
  const entries = [];
  const positions = new Set();
  for (const page of pages) {
    for (const { row } of JSON.parse(fs.readFileSync(join(source, page), 'utf8')).rows) {
      const [setup, ...line] = row.Moves.split(' ');
      if (line.length !== 5 || !row.Themes.split(' ').includes('mateIn3') || row.Popularity < 60) continue;
      const board = new Chess(row.FEN);
      const side = board.turn() === 'w' ? 'b' : 'w';
      if (counts[side] === 50) continue;
      board.move({ from: setup.slice(0, 2), to: setup.slice(2, 4), promotion: setup[4] });
      const fen = board.fen();
      if (positions.has(key(fen))) continue;
      const branches = proof(board, line);
      if (!branches) continue;
      positions.add(key(fen));
      counts[side]++;
      entries.push({ id: 'mate3-' + row.PuzzleId, fen, line, rating: row.Rating, branches });
      if (entries.length === 100) return entries.sort((a, b) => a.rating - b.rating || compare(a.id, b.id));
    }
  }
  throw Error('Incomplete selection: ' + JSON.stringify(counts));
}

const digest = entries => createHash('sha256').update(entries.map(entry => `${entry.id} ${entry.line[0]} ${entry.branches}\n`).join('')).digest('hex');

if (input === '--import') {
  const entries = select(output);
  const rows = entries.map(({ id, fen, line, rating }) => `  - id: ${id}\n    fen: "${fen}"\n    line: [${line.join(', ')}]\n    rating: ${rating}`);
  fs.writeFileSync(oracle, 'schema: 1\nobjective: MATE_IN_THREE\nquests:\n' + rows.join('\n') + '\n');
  console.log(digest(entries));
} else {
  const rows = [...fs.readFileSync(input, 'utf8').replaceAll('\r\n', '\n').matchAll(/^  - id: (mate3-[A-Za-z0-9]{5})\n    fen: "([^"\n]+)"\n    line: \[([a-h1-8qrbn, ]+)\]\n    rating: (\d+)$/gm)];
  if (rows.length !== 100) throw Error('Expected exactly 100 canonical mate-in-three entries');
  const entries = rows.map(([, id, fen, moves]) => {
    const line = moves.split(', ');
    const branches = proof(new Chess(fen), line);
    if (!branches) throw Error('Mate in three is not unique and forced at ' + id);
    return { id, line, branches };
  });
  const hash = digest(entries);
  if (output && fs.readFileSync(output, 'utf8').trim() !== hash) throw Error('Mate-in-three proof fingerprint changed');
  console.log(`Verified ${entries.length} unique forced mates in three.\n${hash}`);
}
