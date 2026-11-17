import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const [worker, fixture] = process.argv.slice(2);
assert.ok(worker && fixture, 'Supply the packaged worker and the shared automation fixture');
const cases = JSON.parse(readFileSync(fixture, 'utf8'));
let nextId = 0;
let reply;
globalThis.self = {
    postMessage(message) {
        if (!Object.hasOwn(message, 'progress')) reply(message);
    }
};
await import(pathToFileURL(worker));

async function call(type, data = {}) {
    const id = ++nextId;
    const result = await new Promise(resolve => {
        reply = resolve;
        self.onmessage({ data: { id, type, ...data } });
    });
    assert.equal(result.id, id);
    return result;
}

async function request(type, data) {
    const result = await call(type, data);
    assert.equal(result.error, undefined, result.error);
    return result;
}

function automate(state, automatic) {
    return request('match-automation', { expectedRevision: state.match.revision, automatic });
}

function play(state, move) {
    return request('match-play', { expectedRevision: state.match.revision, move });
}

function applyBot(state, move) {
    return request('match-bot-apply', { expectedRevision: state.match.revision, move });
}

function report(id, ...values) {
    console.log(id + '\t' + values.map(String).join('/'));
}

function assertSameGame(before, after, id) {
    for (const field of ['mode', 'slot', 'checkpoint', 'reviewCheckpoint', 'fen', 'sideToMove', 'ply', 'viewPly', 'blackBelow']) {
        assert.deepEqual(after.match[field], before.match[field], id + ' preserves ' + field);
    }
    assert.equal(after.saved.find(game => game.slot === after.match.slot)?.id, before.saved.find(game => game.slot === before.match.slot)?.id);
}

function assertUnchanged(before, after, id) {
    for (const field of ['ledger', 'match', 'saved', 'finished']) assert.deepEqual(after[field], before[field], id + ' preserves ' + field);
}

let completed;
for (const example of cases) {
    const id = example.id;
    // The two older storage keys can contain the same raw game. Origin comes from the original entry, not the moves.
    const library = await request('ledger-open', {
        ledger: null, legacy: { local: example.initialCheckpoint, ai: 'ai|' + example.initialCheckpoint }
    });
    const initial = await request('match-open', { route: 'AI' });
    const manual = await automate(initial, false);
    assert.equal(manual.outcome, 'APPLIED', id);
    assert.deepEqual(initial.saved.map(game => game.mode).sort(), ['AI', 'LOCAL'], id);
    assert.deepEqual([initial.match.mode, initial.match.automatic, initial.match.awaitingBot, initial.match.sideToMove],
        ['AI', true, example.manualSide === 'BLACK', example.manualSide], id);
    assertSameGame(initial, manual, id);
    assert.equal(manual.match.revision, initial.match.revision + 1, id);
    assert.deepEqual(manual.match.automation, { checked: false, enabled: true, visible: true }, id);
    assert.deepEqual([manual.match.automatic, manual.match.awaitingBot, manual.match.manualUsed], [false, false, false], id);
    assert.deepEqual([manual.match.manualAiTurn, manual.match.rated], [example.manualSide === 'BLACK', true], id);
    assert.ok(manual.match.legalMoves.includes(example.manualMove), id);

    const stale = await request('match-automation', { expectedRevision: initial.match.revision, automatic: true });
    assert.equal(stale.outcome, 'STALE', id);
    assertUnchanged(manual, stale, id);
    const staleBot = await applyBot(initial, example.manualMove);
    assert.equal(staleBot.outcome, 'STALE', id);
    assertUnchanged(manual, staleBot, id);
    const manualBot = await applyBot(manual, example.manualMove);
    assert.equal(manualBot.outcome, 'UNAVAILABLE', id);
    assertUnchanged(manual, manualBot, id);
    const redundant = await automate(manual, false);
    assert.equal(redundant.outcome, 'UNAVAILABLE', id);
    assertUnchanged(manual, redundant, id);
    report(id + ':guards', manual.outcome, stale.outcome, staleBot.outcome, manualBot.outcome, redundant.outcome);

    await request('ledger-open', { ledger: manual.ledger });
    const manualRestored = await request('match-open', { route: 'AI' });
    assertSameGame(manual, manualRestored, id);
    assert.deepEqual(
        [manualRestored.match.automatic, manualRestored.match.awaitingBot, manualRestored.match.automation, manualRestored.match.manualUsed],
        [false, false, manual.match.automation, false], id
    );
    const manualMove = await play(manualRestored, example.manualMove);
    assert.equal(manualMove.outcome, 'APPLIED', id);
    assert.equal(manualMove.match.manualUsed, example.manualUsed, id);
    const enabled = await automate(manualMove, true);
    assert.equal(enabled.outcome, 'APPLIED', id);
    assertSameGame(manualMove, enabled, id);
    assert.deepEqual([enabled.match.automatic, enabled.match.manualUsed, enabled.match.automation],
        [true, example.manualUsed, { checked: true, enabled: true, visible: true }], id);

    await request('ledger-open', { ledger: enabled.ledger });
    const autoRestored = await request('match-open', { route: 'AI' });
    assertSameGame(enabled, autoRestored, id);
    assert.deepEqual([autoRestored.match.automatic, autoRestored.match.manualUsed], [true, example.manualUsed], id);
    const history = await request('match-history', { expectedRevision: autoRestored.match.revision, ply: 0 });
    assert.equal(history.outcome, 'APPLIED', id);
    assert.deepEqual(history.match.automation, { checked: true, enabled: false, visible: true }, id);
    const deniedHistory = await automate(history, false);
    assert.equal(deniedHistory.outcome, 'UNAVAILABLE', id);
    assertUnchanged(history, deniedHistory, id);
    report(id + ':restore', manualRestored.match.mode, manualRestored.match.automatic, manualRestored.match.manualUsed,
        autoRestored.match.mode, autoRestored.match.automatic, autoRestored.match.manualUsed, deniedHistory.outcome);

    let pending = await request('match-history', { expectedRevision: history.match.revision, ply: autoRestored.match.ply });
    for (const move of example.humanMovesAfterReopen) {
        pending = await play(pending, move);
        assert.equal(pending.outcome, 'APPLIED', id);
    }
    assert.equal(pending.match.awaitingBot, true, id);
    const premature = await applyBot(pending, 'a1a8');
    assert.equal(premature.outcome, 'UNAVAILABLE', id);
    assertUnchanged(pending, premature, id);
    const computed = await request('match-bot-compute', { checkpoint: pending.match.reviewCheckpoint });
    assert.equal(computed.move, 'd8h4', id);
    assert.equal(Object.hasOwn(computed, 'ledger'), false, id);
    completed = await applyBot(pending, computed.move);
    assert.equal(completed.outcome, 'APPLIED', id);
    const reused = await applyBot(pending, computed.move);
    assert.equal(reused.outcome, 'STALE', id);
    assertUnchanged(completed, reused, id);
    const [archived] = completed.finished;
    assert.equal(archived.id, initial.saved.find(game => game.slot === initial.match.slot).id, id);
    const review = await request('archive-review-open', { archiveId: archived.id });
    assert.deepEqual([archived.mode, completed.match.reviewCheckpoint, completed.match.result, completed.match.manualUsed, review.rated],
        ['AI', example.expectedFinalCheckpoint, 'BLACK_WINS', example.manualUsed, example.rated], id);
    assert.equal(library.saved.find(game => game.mode === 'LOCAL').id, completed.saved.find(game => game.mode === 'LOCAL').id, id);
    report(id + ':archive', archived.mode, completed.match.reviewCheckpoint, completed.match.result, completed.match.manualUsed,
        review.rated, premature.outcome, computed.move, reused.outcome);
}

const archived = await request('match-open', { archiveId: completed.finished[0].id });
assert.equal(archived.match.automation.visible, false);
const deniedArchive = await automate(archived, false);
assert.equal(deniedArchive.outcome, 'UNAVAILABLE');
assertUnchanged(archived, deniedArchive, 'archive');

const preview = await request('match-history', { expectedRevision: archived.match.revision, ply: 3 });
const continuation = await request('match-continue', { expectedRevision: preview.match.revision, automatic: true });
assert.equal(continuation.outcome, 'APPLIED');
assert.deepEqual(continuation.finished, archived.finished, 'Continuing a historical position preserves the archived game');
assert.notEqual(continuation.saved.find(game => game.slot === continuation.match.slot).id, completed.finished[0].id);
const continuedFacts = [
    continuation.match.mode, continuation.match.automatic, continuation.match.manualUsed, continuation.match.rated,
    continuation.match.awaitingBot, continuation.match.reviewCheckpoint
];
assert.deepEqual(continuedFacts, ['AI', true, true, false, true, cases.at(-1).continuationCheckpoint]);
report('assisted-archive-continuation', ...continuedFacts);

const reviewInput = await request('archive-review-open', { archiveId: archived.match.archiveId });
assert.deepEqual([reviewInput.outcome, reviewInput.rated], ['NEEDS_ANALYSIS', false]);
const review = await request('review-analyze', { checkpoint: reviewInput.checkpoint, rated: reviewInput.rated });
const cached = await request('archive-review-save', {
    archiveId: archived.match.archiveId, checkpoint: reviewInput.checkpoint, review: review.archive.review
});
const viewFacts = [cached.outcome, cached.view.mode, cached.view.automaticContinuation, cached.view.players.WHITE.rating,
    cached.view.players.BLACK.rating];
assert.deepEqual(viewFacts, ['OPENED', 'REVIEW_MANUAL', true, null, null]);
await request('ledger-open', { ledger: cached.ledger });
const reopenedReview = await request('archive-review-open', { archiveId: archived.match.archiveId });
assert.deepEqual([reopenedReview.outcome, reopenedReview.view.mode, reopenedReview.view.automaticContinuation,
    reopenedReview.view.players.WHITE.rating, reopenedReview.view.players.BLACK.rating], viewFacts);
const historyReview = await request('review-action', {
    archiveId: archived.match.archiveId, expectedRevision: reopenedReview.view.revision, action: 'SEEK', ply: 3
});
assert.equal(historyReview.view.canContinue, true);
const reviewFork = await request('archive-review-continue', {
    archiveId: archived.match.archiveId, expectedRevision: historyReview.view.revision, automatic: true
});
assert.deepEqual([reviewFork.outcome, reviewFork.match.mode, reviewFork.match.automatic, reviewFork.match.rated],
    ['APPLIED', 'AI', true, false]);
assert.equal(reviewFork.match.reviewCheckpoint, cases.at(-1).continuationCheckpoint);
report('assisted-cached-review', ...viewFacts, reviewFork.outcome, reviewFork.match.automatic, reviewFork.match.rated);

const local = await request('match-open', { route: 'LOCAL' });
assert.equal(local.match.automation.visible, false);
assert.equal(local.match.reviewCheckpoint, cases.at(-1).initialCheckpoint);
const deniedLocal = await automate(local, true);
assert.equal(deniedLocal.outcome, 'UNAVAILABLE');
assertUnchanged(local, deniedLocal, 'local game');
const invalid = await call('match-automation', { expectedRevision: local.match.revision, automatic: 'false' });
const afterInvalid = await automate(local, true);
assertUnchanged(local, afterInvalid, 'malformed automation command');
report('unavailable', deniedArchive.outcome, deniedLocal.outcome, invalid.error);
