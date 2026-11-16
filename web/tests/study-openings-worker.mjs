import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const site = process.argv[2];
assert.ok(site, 'Provide the external build site directory');
const { studyCommand } = await import(pathToFileURL(resolve(site, 'chess-engine.mjs')));
const execute = (action, { value = '', index = 0, saved = '', option = 'ro' } = {}) =>
  JSON.parse(studyCommand('openings', action, value, index, saved, option, option));

const ro = execute('CATALOG').entries;
const en = execute('CATALOG', { option: 'en' }).entries;
assert.ok(ro.length > 5);
assert.deepEqual(ro.map(entry => entry.id), en.map(entry => entry.id));
assert.notEqual(ro[0].title, en[0].title);
assert.ok(ro.every(entry => entry.source.startsWith('https://')));

const initial = execute('START', { value: ro[0].id });
assert.equal(initial.learner, 'WHITE');
assert.equal(initial.canPlay, true);
assert.equal(initial.nextStep.move, 'e2e4');
const other = execute('PLAY', { saved: initial.saved, value: 'd2d4' });
assert.equal(other.offLine, true);
assert.equal(other.fen, initial.fen);
const piece = execute('THEORY', { saved: other.saved });
const solution = execute('THEORY', { saved: piece.saved });
assert.equal(piece.hint, 'PIECE');
assert.equal(piece.hintPiece, 'PAWN');
assert.equal(solution.hint, 'SOLUTION');
assert.equal(solution.nextStep.move, 'e2e4');
assert.equal(execute('THEORY', { saved: solution.saved }).hint, 'NONE');

const played = execute('PLAY', { saved: solution.saved, value: solution.nextStep.move });
assert.equal(played.canAutoReply, true);
const history = execute('SEEK', { saved: played.saved });
assert.equal(history.reviewing, true);
assert.equal(history.fen, initial.fen);
assert.equal(execute('REPLY', { saved: history.saved }).saved, history.saved);
const paused = execute('FORWARD', { saved: history.saved });
assert.equal(paused.replyPaused, true);
assert.equal(execute('REPLY', { saved: paused.saved }).saved, paused.saved);
const resumed = execute('RESUME', { saved: paused.saved });
assert.equal(resumed.canAutoReply, true);
const replied = execute('REPLY', { saved: resumed.saved });
assert.equal(replied.ply, 2);
assert.equal(replied.lastMove, 'e7e5');
assert.equal(execute('RESTORE', { saved: replied.saved, option: 'en' }).fen, replied.fen);
assert.equal(execute('REPLAY', { saved: execute('SEEK', { saved: replied.saved }).saved }).practicePly, 0);

const black = execute('START', { value: ro[0].id, index: 1 });
assert.equal(black.learner, 'BLACK');
assert.equal(black.canAutoReply, true);
assert.equal(execute('REPLY', { saved: black.saved }).canPlay, true);
assert.throws(() => execute('RESTORE', { saved: 'bad' }));

if (process.argv.includes('--analysis')) {
  const enabled = execute('AI_TOGGLE', { saved: replied.saved });
  const analysis = execute('ANALYZE', { saved: enabled.saved });
  assert.ok(Number.isFinite(analysis.evaluation.whiteShare));
  assert.ok(analysis.variants.length > 0);
  for (const candidate of analysis.variants) {
    assert.equal(candidate.frames[0].fen, replied.fen);
    assert.equal(candidate.frames[1].lastMove, candidate.move);
    assert.ok(candidate.previewMoves.includes(candidate.move));
    if (candidate.decisive) {
      const proof = candidate.decisive;
      assert.ok(candidate.previewMoves.includes(proof.branch));
      const target = analysis.variants.find(line => line.move === proof.branch);
      assert.equal(target.proof.addedDefense, false);
      assert.equal(target.frames[proof.ply].lastMove, proof.move);
    }
  }
}
console.log('Opening study worker: bilingual catalog, legal/off-line outcomes, hints, pause, resume, review and both seats passed.');
