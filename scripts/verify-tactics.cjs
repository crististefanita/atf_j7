const fs = require('node:fs');
const { createHash } = require('node:crypto');
const { resolve } = require('node:path');

const [modulePath, catalog, proof] = process.argv.slice(2);
if (!modulePath || !catalog) throw Error('Usage: node verify-tactics.cjs <chess.js 1.4.0> <catalog> [proof]');
const { Chess } = require(resolve(modulePath));
const values = { p: 100, n: 320, b: 330, r: 500, q: 900, k: 0 };
const mate = 100000;

function material(board) {
  const side = board.turn();
  return board._board.reduce((total, piece) =>
    piece ? total + (piece.color === side ? 1 : -1) * values[piece.type] : total, 0);
}

// chess.js 1.4.0 raw legal moves avoid SAN formatting at every node of the exhaustive tree.
function after(board, move, visit) {
  board._makeMove(move);
  try { return visit(); } finally { board._undoMove(); }
}

function score(board, remaining, distance, alpha, beta) {
  const moves = board._moves();
  if (!moves.length) return board.isCheck() ? distance - mate : 0;
  if (!remaining) return material(board);
  let best = -Infinity;
  for (const move of moves) {
    best = Math.max(best, after(board, move, () => -score(board, remaining - 1, distance + 1, -beta, -alpha)));
    if (best >= beta) return best;
    alpha = Math.max(alpha, best);
  }
  return best;
}

function verify(id, fen, solution) {
  const board = new Chess(fen);
  const moves = board.moves({ verbose: true });
  if (moves.some(move => move.san.endsWith('#'))) throw Error(id + ' has an immediate mate');
  const ranked = moves.map(move => {
    board.move(move);
    try { return { move: move.lan, score: -score(board, 2, 1, -Infinity, Infinity) }; } finally { board.undo(); }
  }).sort((a, b) => b.score - a.score);
  const [best, next] = ranked;
  const gain = best.score - material(board);
  if (best.move !== solution || best.score === next.score || Math.abs(best.score) >= mate - 3 || gain < 200) {
    throw Error(id + ' does not have the expected unique material-winning solution');
  }
  return id + ' ' + solution + ' ' + gain + '\n';
}

const source = fs.readFileSync(catalog, 'utf8').replaceAll('\r\n', '\n');
const rows = [...source.matchAll(/^  - id: ((?:tactic|training|holdout|root|intermezzo|reply|challenge|newcase|pvcase|attack|pressure|promotion|drillcheck)-[0-9]{3})\n    fen: "([^"\n]+)"\n    solution: ([a-h][1-8][a-h][1-8][qrbn]?)\n    difficulty: [1-9][0-9]*$/gm)];
if (!rows.length || rows.length !== (source.match(/^  - id:/gm) || []).length) throw Error('Malformed tactics catalog');
rows.sort(([, a], [, b]) => a.localeCompare(b));
const hash = createHash('sha256').update(rows.map(([, id, fen, solution]) => verify(id, fen, solution)).join('')).digest('hex');
if (proof && fs.readFileSync(proof, 'utf8').trim() !== hash) throw Error('Tactics proof fingerprint changed');
console.log(`Verified ${rows.length} unique material-winning tactics.\n${hash}`);
