import assert from 'node:assert/strict';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { runtimeDirectory } from '../tools/package-web.mjs';

const site = process.argv[2];
assert.ok(site, 'Provide the external build site directory');
const moduleUrl = pathToFileURL(join(await runtimeDirectory(site), 'chess-engine.mjs'));
const { studyCommand } = await import(moduleUrl);
const request = (command, action, { value = '', index = 0, saved = '', option = '' } = {}) =>
    JSON.parse(command('analysis', action, value, index, saved, option, 'ro'));
const execute = (action, inputs) => request(studyCommand, action, inputs);

const initial = execute('open');
assert.equal(initial.board.length, 64);
assert.equal(initial.sideToMove, 'WHITE');
assert.equal(initial.evaluation, null);
assert.equal(execute('evaluate', { index: initial.revision - 1 }).evaluation, null);
const evaluated = execute('evaluate', { index: initial.revision });
assert.ok(Number.isFinite(evaluated.evaluation.whiteShare));
assert.equal(evaluated.hint.number, 0);
assert.ok(evaluated.hint.count > 0);
const hinted = execute('hint');
assert.equal(hinted.hint.number, 1);
assert.equal(typeof hinted.hint.family.ro, 'string');
assert.deepEqual(hinted.marks, [hinted.hint.move.slice(0, 2), hinted.hint.move.slice(2, 4)]);
const preview = execute('preview').preview;
assert.equal(preview.kind, 'THEORY');
assert.equal(preview.board, initial.board);
assert.ok(preview.eco);
const advanced = execute('preview-seek', { index: 1 });
assert.equal(advanced.preview.lastMove, hinted.hint.move);
assert.notEqual(advanced.preview.board, initial.board);
if (preview.branches.length > 1) {
    const other = preview.branch === 0 ? 1 : 0;
    assert.equal(execute('preview-branch', { index: other }).preview.ply, 0);
    assert.equal(execute('preview-branch', { index: preview.branch }).preview.ply, 1);
}
const returned = execute('preview-close');
assert.equal(returned.preview, null);
assert.equal(returned.board, initial.board);
assert.equal(returned.checkpoint, initial.checkpoint);
assert.equal(execute('tap', { value: hinted.marks[1] }).lastMove, hinted.hint.move);

const source = '1|e2e4 e7e5 g1f3 b8c6';
const entry = execute('open', { value: source, index: 1, option: 'black' });
assert.deepEqual([entry.ply, entry.total, entry.blackBelow], [1, 4, true]);
assert.equal(execute('seek', { index: 3 }).lastMove, 'g1f3');
execute('seek', { index: 1 });
assert.deepEqual(execute('tap', { value: 'c7' }).targets.sort(), ['c5', 'c6']);
const diverged = execute('tap', { value: 'c5' });
assert.deepEqual(diverged.history, ['e2e4', 'c7c5']);
assert.equal(diverged.sideToMove, 'WHITE');
assert.equal(execute('continue').continuation, '2|analysis|e2e4 c7c5');
assert.equal(execute('analysis', { option: 'off' }).analysis, false);
assert.equal(execute('continue').continuation, '1|e2e4 c7c5');
const durable = execute('flip');
const restoredModule = await import(moduleUrl.href + '?restore-manual-study');
const restored = request(restoredModule.studyCommand, 'resume', { saved: durable.saved });
assert.deepEqual([restored.checkpoint, restored.ply, restored.blackBelow, restored.analysis],
    [durable.checkpoint, durable.ply, false, false]);
assert.equal(request(restoredModule.studyCommand, 'reset').checkpoint, source);

const outsideBook = '1|a2a3 h7h6 b2b3 g7g6 h2h3 c7c6 e2e4 d7d6';
const tactical = execute('open', { value: outsideBook, index: 8 });
const ready = execute('evaluate', { index: tactical.revision });
assert.ok(ready.hint.count > 0);
const recommendation = execute('hint').hint;
assert.equal(recommendation.family, null);
const tacticalPreview = execute('preview').preview;
assert.equal(tacticalPreview.kind, 'TACTICAL');
assert.equal(tacticalPreview.board, tactical.board);
assert.equal(typeof tacticalPreview.proof, 'string');
assert.equal(execute('preview-seek', { index: 1 }).preview.lastMove, recommendation.move);
assert.equal(execute('preview-close').checkpoint, outsideBook);

const mate = execute('open', { value: '1|f2f3 e7e5 g2g4 d8h4', index: 4 });
assert.equal(mate.result, 'BLACK_WINS');
assert.deepEqual(mate.evaluation, { ending: 'FINISHED', whiteShare: 0 });
assert.equal(execute('continue').continuation, null);
assert.equal(execute('tap', { value: 'a2' }).selected, null);
assert.equal(execute('seek', { index: 3 }).result, 'IN_PROGRESS');
console.log('Manual analysis worker: legal play, branching, orientation, theory, tactical replay, resume, terminal and saved continuation passed.');
