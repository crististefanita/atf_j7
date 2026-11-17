import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const [worker, fixture] = process.argv.slice(2);
assert.ok(worker && fixture, 'Supply the packaged worker and the shared match-analysis fixture');
const cases = JSON.parse(readFileSync(fixture, 'utf8'));
let nextId = 0;
let reply;
globalThis.self = { postMessage: message => reply(message) };
await import(pathToFileURL(worker));

async function call(type, data = {}) {
    const id = ++nextId;
    const result = await new Promise(resolve => {
        reply = resolve;
        self.onmessage({ data: { id, type, ...data } });
    });
    assert.equal(result.id, id, type);
    return result;
}

async function request(type, data) {
    const result = await call(type, data);
    assert.equal(result.error, undefined, result.error);
    return result;
}

function report(id, ...facts) {
    console.log(id + '\t' + facts.map(String).join('/'));
}

function decimal(number) {
    return Number.isInteger(number) ? number.toFixed(1) : number;
}

function action(expectedRevision, action, index) {
    return request('match-analysis-action', { expectedRevision, action, index });
}

function openFrame(checkpoint, ply, expectedRevision) {
    return request('match-analysis-open', { checkpoint, ply, expectedRevision });
}

function boardFor(fen) {
    return fen.split(' ')[0].replaceAll('/', '').replace(/[1-8]/g, digit => ' '.repeat(Number(digit)));
}

const beforeAnyFrame = await action(0, 'hint');
assert.equal(beforeAnyFrame.outcome, 'UNAVAILABLE');
assert.equal(beforeAnyFrame.analysis, null);

for (const example of cases) {
    const { id } = example;
    await request('ledger-open', { ledger: null });
    const setup = { mode: 'AI', humanSide: example.humanSide, level: example.level };
    if (example.analysis !== null) setup.analysis = example.analysis;
    let state = await request('match-open', { setup });
    assert.equal(state.match.analysisEnabled, example.analysis === true, id);
    const gameId = state.saved[0].id;
    state = await request('match-automation', { expectedRevision: state.match.revision, automatic: false });
    assert.equal(state.outcome, 'APPLIED', id);
    for (const move of example.moves.split(' ')) {
        state = await request('match-play', { expectedRevision: state.match.revision, move });
        assert.equal(state.outcome, 'APPLIED', id);
    }
    const ended = example.result !== 'IN_PROGRESS';
    const select = ended ? { archiveId: gameId } : { route: 'AI' };
    const ledger = state.ledger;
    await request('ledger-open', { ledger });
    const resumed = await request('match-open', select);
    assert.equal(resumed.ledger, ledger, id + ' keeps the same saved record');
    const resumedId = ended ? resumed.match.archiveId : resumed.saved.find(game => game.slot === resumed.match.slot).id;
    assert.equal(resumedId, gameId, id + ' preserves the same game identity');
    assert.deepEqual([resumed.match.mode, resumed.match.analysisEnabled, resumed.match.reviewCheckpoint, resumed.match.result],
        ['AI', example.analysis === true, example.checkpoint, example.result], id);
    report(id + ':saved', resumed.match.mode, resumed.match.analysisEnabled, resumed.match.reviewCheckpoint, resumed.match.result);

    const displayed = example.displayedPly === resumed.match.ply ? resumed
        : await request('match-history', { expectedRevision: resumed.match.revision, ply: example.displayedPly });
    assert.equal(displayed.match.viewPly, example.displayedPly, id);
    assert.equal(displayed.match.sideToMove, example.sideToMove, id);
    const expectedRevision = displayed.match.revision;
    const opened = await openFrame(example.checkpoint, example.displayedPly, expectedRevision);
    assert.equal(opened.outcome, 'APPLIED', id);
    assert.deepEqual([opened.analysis.revision, opened.analysis.ply, opened.analysis.preview], [expectedRevision, example.displayedPly, null], id);
    const { evaluation, hint } = opened.analysis;
    assert.equal(evaluation?.ending ?? null, example.ending, id);
    const counts = hint?.count ?? 0;
    let recommendation = null;
    let previewLength = 0;
    let previewSide = null;
    let mateAfterFirstMove = false;
    if (example.recommendation === null) {
        assert.equal(hint, null, id);
        const unavailable = await action(expectedRevision, 'hint');
        assert.equal(unavailable.outcome, 'UNAVAILABLE', id);
        assert.deepEqual(unavailable.analysis, opened.analysis, id);
    } else {
        assert.ok(counts > 0, id);
        assert.deepEqual([hint.number, hint.move], [0, null], id + ' begins with the suggestion hidden');
        const reveal = await action(expectedRevision, 'hint');
        assert.equal(reveal.outcome, 'APPLIED', id);
        assert.deepEqual([reveal.analysis.revision, reveal.analysis.ply, reveal.analysis.hint.move],
            [expectedRevision, example.displayedPly, example.recommendation], id);
        recommendation = reveal.analysis.hint.move;
        const preview = await action(expectedRevision, 'preview');
        assert.equal(preview.outcome, 'APPLIED', id);
        assert.equal(preview.analysis.preview.board, boardFor(displayed.match.fen), id + ' opens from the visible historical board');
        assert.equal(preview.analysis.preview.ply, 0, id);
        assert.equal(preview.analysis.preview.sideToMove, example.sideToMove, id);
        assert.equal(preview.analysis.preview.branches[preview.analysis.preview.branch], recommendation, id);
        assert.deepEqual(preview.analysis.preview.marks, [recommendation.slice(0, 2), recommendation.slice(2, 4)], id);
        const step = await action(expectedRevision, 'preview-seek', 1);
        assert.equal(step.outcome, 'APPLIED', id);
        assert.equal(step.analysis.preview.ply, 1, id);
        assert.equal(step.analysis.preview.lastMove, recommendation, id);
        assert.notEqual(step.analysis.preview.board, preview.analysis.preview.board, id);
        previewLength = step.analysis.preview.total;
        previewSide = step.analysis.preview.sideToMove;
        if (example.ending === 'CHECKMATE') {
            assert.equal(previewLength, 1, id);
            assert.equal(step.analysis.preview.board, boardFor(resumed.match.fen), id + ' replays the actual saved mating move');
            mateAfterFirstMove = true;
        }
        const close = await action(expectedRevision, 'preview-close');
        assert.equal(close.outcome, 'APPLIED', id);
        assert.deepEqual([close.analysis.ply, close.analysis.preview], [example.displayedPly, null], id);
        const stale = await action(expectedRevision - 1, 'hint');
        const mismatched = await openFrame(example.checkpoint, 0, expectedRevision);
        const reopened = await openFrame(example.checkpoint, example.displayedPly, expectedRevision);
        assert.deepEqual([stale.outcome, mismatched.outcome, reopened.outcome], ['STALE', 'STALE', 'APPLIED'], id);
        assert.deepEqual(reopened.analysis, close.analysis, id + ' rejects obsolete commands without resetting the visible suggestion');
        report(id + ':revision', stale.outcome, mismatched.outcome, reopened.outcome);
    }
    report(id + ':frame', evaluation?.ending ?? null, evaluation?.centipawns ?? null, evaluation?.mate ?? null,
        decimal(evaluation?.whiteShare ?? null), counts, recommendation, previewLength, previewSide, mateAfterFirstMove);
    if (ended && example.displayedPly < resumed.match.ply) {
        const latest = await request('match-history', { expectedRevision, ply: resumed.match.ply });
        assert.equal(latest.outcome, 'APPLIED', id);
        const finalFrame = await openFrame(example.checkpoint, latest.match.viewPly, latest.match.revision);
        const obsolete = await action(expectedRevision, 'preview');
        assert.equal(finalFrame.outcome, 'APPLIED', id);
        assert.deepEqual([finalFrame.analysis.revision, finalFrame.analysis.ply, finalFrame.analysis.evaluation.ending,
            finalFrame.analysis.hint, finalFrame.analysis.preview],
        [latest.match.revision, resumed.match.ply, 'FINISHED', null, null], id);
        assert.equal(obsolete.outcome, 'STALE', id);
        assert.deepEqual(obsolete.analysis, finalFrame.analysis, id + ' the old replay cannot reappear on the terminal position');
        report('advanced-to-terminal', finalFrame.analysis.evaluation.ending, obsolete.outcome, obsolete.analysis.preview);
    }
    const unmodified = await request('match-open', select);
    assert.deepEqual([unmodified.ledger, unmodified.match.reviewCheckpoint, unmodified.match.result],
        [ledger, example.checkpoint, example.result], id + ' analysis never saves the branch or rewrites the ledger');
    report(id + ':read-only', unmodified.ledger === ledger);
}

const malformed = await call('match-open', {
    setup: { mode: 'AI', humanSide: 'WHITE', level: 'BASIC', analysis: 'true' }
});
const invalidPly = await call('match-analysis-open', {
    checkpoint: cases[0].checkpoint, ply: -2, expectedRevision: 2147483647
});
const beyondLastPly = await call('match-analysis-open', {
    checkpoint: cases[0].checkpoint, ply: 3, expectedRevision: 2147483647
});
assert.match(malformed.error, /Invalid chess worker request/);
assert.match(invalidPly.error, /Invalid chess worker request/);
assert.match(beyondLastPly.error, /Analysis position is outside the match/);
report('input-guards', beforeAnyFrame.outcome, malformed.error, invalidPly.error, Boolean(beyondLastPly.error));
