import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const [bundle] = process.argv.slice(2);
assert.ok(bundle, 'Supply the emitted Web site');

let next = 0;
const pending = new Map();
globalThis.self = {
    postMessage(message) {
        if ('progress' in message) return;
        const completion = pending.get(message.id);
        if (!completion) return;
        pending.delete(message.id);
        if (message.error) completion.reject(new Error(message.error));
        else completion.resolve(message);
    },
};
await import(pathToFileURL(resolve(bundle, 'engine-worker.mjs')));

async function call(type, fields = {}) {
    const id = String(++next);
    return new Promise((resolve, reject) => {
        pending.set(id, { resolve, reject });
        self.onmessage({ data: { id, type, ...fields } });
    });
}

async function study(feature, action, values = {}) {
    return (await call('study', { feature, action, value: '', index: 0, saved: '', option: '', locale: 'ro', ...values })).study;
}

const puzzles = await study('quests', 'menu');
assert.deepEqual(puzzles.chapters.map(chapter => chapter.key),
    ['mate-one', 'mate-two', 'mate-three', 'tactics', 'training', 'intermezzo']);
assert.equal(puzzles.overall.total, puzzles.chapters.reduce((total, chapter) => total + chapter.totals.total, 0));
for (const chapter of puzzles.chapters) {
    assert.ok(chapter.totals.total > 0);
    const opened = await study('quests', 'open', { value: chapter.key });
    assert.equal(opened.data.board.length, 64);
    assert.equal(typeof opened.saved, 'string');
    const reopened = await study('quests', 'view', { saved: opened.saved });
    assert.equal(reopened.data.board, opened.data.board);
}
console.log('PASS six puzzle chapters and portable progress snapshot');

const openingsRo = await study('openings', 'CATALOG');
const openingsEn = await study('openings', 'CATALOG', { locale: 'en' });
assert.ok(openingsRo.entries.length > 5);
assert.deepEqual(openingsRo.entries.map(row => row.id), openingsEn.entries.map(row => row.id));
assert.ok(openingsRo.entries.some((row, index) => row.title !== openingsEn.entries[index].title));
console.log('PASS opening catalog identity and translations');

const studyBoard = await study('analysis', 'open', { value: '1|f2f3 e7e5', index: 1, option: 'black' });
assert.equal(studyBoard.ply, 1);
assert.equal(studyBoard.blackBelow, true);
const positioned = await study('analysis', 'seek', { index: 2 });
assert.equal(positioned.lastMove, 'e7e5');
const restored = await study('analysis', 'resume', { saved: positioned.saved });
assert.equal(restored.board, positioned.board);
console.log('PASS analysis position, history and restoration');

const { archive } = await call('review-analyze', { checkpoint: positioned.checkpoint });
const ordinary = await call('review-open', { archive });
const ephemeral = await call('study-review-open', { archive: { ...archive, id: 'separate-study-review' }, blackBelow: true, ply: 1 });
assert.equal(ephemeral.view.ply, 1);
assert.equal(ephemeral.view.blackBelow, true);
const studyReviewChanged = await call('review-action', { expectedRevision: ephemeral.view.revision, action: 'SEEK', ply: 0, studyReview: true });
assert.equal(studyReviewChanged.outcome, 'APPLIED');
const ordinaryChanged = await call('review-action', { expectedRevision: ordinary.view.revision, action: 'SEEK', ply: 0 });
assert.equal(ordinaryChanged.outcome, 'APPLIED');
assert.equal(ordinaryChanged.view.blackBelow, false);
console.log('PASS study review retains the selected ply and leaves the ordinary review independent');

await call('ledger-open', { ledger: null });
const snapshot = await study('analysis', 'continue');
assert.ok(snapshot.continuation);
let full;
for (let slot = 1; slot <= 10; slot++) {
    full = await call('study-match-continue', { checkpoint: snapshot.continuation, automatic: false, blackBelow: true });
    assert.equal(full.outcome, 'APPLIED');
    assert.equal(full.match.slot, slot);
    assert.equal(full.match.blackBelow, true);
}
const unavailable = await call('study-match-continue', { checkpoint: snapshot.continuation, automatic: false, blackBelow: true });
assert.equal(unavailable.outcome, 'NO_FREE_SLOT');
assert.equal(unavailable.ledger, full.ledger);
assert.equal(unavailable.saved.length, 10);
console.log('PASS study continuation preserves orientation and a full game ledger');
