import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const [worker, fixture] = process.argv.slice(2);
assert.ok(worker && fixture, 'Supply the packaged worker entry and the shared orientation fixture');
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
    assert.equal(result.id, id);
    return result;
}

async function request(type, data) {
    const result = await call(type, data);
    assert.equal(result.error, undefined, result.error);
    return result;
}

let reopened;
for (const example of cases) {
    const archived = example.result !== 'IN_PROGRESS';
    const ai = example.mode === 'AI';
    const legacy = ai ? { ai: 'ai|' + example.checkpoint } : { local: example.checkpoint };
    const library = await request('ledger-open', { ledger: null, legacy });
    const [row] = archived ? library.finished : library.saved;
    assert.match(row.id, /^[0-9a-f-]{36}$/, example.id);
    const selector = archived ? { archiveId: row.id } : { slot: row.slot };
    let before = await request('match-open', selector);
    assert.equal(before.match.blackBelow, example.blackBelow, example.id);
    if (example.viewPly !== example.ply) {
        before = await request('match-history', { expectedRevision: before.match.revision, ply: example.viewPly });
        assert.equal(before.outcome, 'APPLIED', example.id);
    }
    const expectedRevision = before.match.revision;
    const flipped = await request('match-flip', { expectedRevision });
    assert.equal(flipped.outcome, 'APPLIED', example.id);
    const { blackBelow } = flipped.match;
    assert.deepEqual(flipped.match, { ...before.match, revision: expectedRevision + 1, blackBelow: example.flippedBlackBelow }, example.id);
    assert.equal(flipped.ledger === before.ledger, archived, example.id);
    assert.deepEqual([flipped.saved, flipped.finished], [before.saved, before.finished], example.id);
    assert.deepEqual([flipped.match.mode, flipped.match.reviewCheckpoint, flipped.match.result, flipped.match.ply,
        flipped.match.viewPly, flipped.match.sideToMove, flipped.match.lastMove, blackBelow],
    [example.mode, example.checkpoint, example.result, example.ply, example.viewPly,
        example.sideToMove, example.lastMove, example.flippedBlackBelow], example.id);

    const stale = await request('match-flip', { expectedRevision });
    assert.equal(stale.outcome, 'STALE', example.id);
    assert.deepEqual([stale.ledger, stale.match], [flipped.ledger, flipped.match], example.id);

    await request('ledger-open', { ledger: flipped.ledger });
    reopened = await request('match-open', selector);
    assert.equal(reopened.match.blackBelow, example.reopenedBlackBelow, example.id);
    assert.equal(reopened.match.reviewCheckpoint, example.checkpoint, example.id);
    assert.deepEqual([reopened.saved, reopened.finished], [before.saved, before.finished], example.id);
    console.log(example.id + '\t' + [flipped.outcome, blackBelow, flipped.match.viewPly, flipped.match.sideToMove,
        stale.outcome, reopened.match.blackBelow].join('/'));

    if (ai) {
        assert.equal(reopened.match.awaitingBot, true, example.id);
        const latestFacing = await request('match-flip', { expectedRevision: reopened.match.revision });
        const delayedReply = await request('match-bot', { expectedRevision: reopened.match.revision });
        assert.equal(latestFacing.outcome, 'APPLIED', example.id);
        assert.deepEqual([delayedReply.ledger, delayedReply.match], [latestFacing.ledger, latestFacing.match], example.id);
        console.log('old-bot-revision\t' + [delayedReply.outcome, delayedReply.match.ply, delayedReply.match.awaitingBot].join('/'));
    }

    if (archived) {
        const preview = await request('match-history', { expectedRevision: reopened.match.revision, ply: example.viewPly });
        const facing = await request('match-flip', { expectedRevision: preview.match.revision });
        const fork = await request('match-continue', { expectedRevision: facing.match.revision, automatic: false });
        assert.equal(fork.outcome, 'APPLIED', example.id);
        assert.notEqual(fork.saved[0].id, row.id, example.id);
        assert.equal(fork.match.reviewCheckpoint, example.continuationCheckpoint, example.id);
        assert.equal(fork.match.blackBelow, example.flippedBlackBelow, example.id);
        assert.deepEqual(fork.finished, before.finished, example.id);
        reopened = await request('match-open', selector);
        assert.equal(reopened.match.blackBelow, example.reopenedBlackBelow, example.id);
        assert.equal(reopened.match.reviewCheckpoint, example.checkpoint, example.id);
        console.log('archive-continuation\t' + [fork.outcome, fork.match.reviewCheckpoint, fork.match.blackBelow, reopened.match.blackBelow].join('/'));
    }
}

const invalid = await call('match-flip', { expectedRevision: -1 });
const opened = await request('ledger-open', { ledger: reopened.ledger });
const unavailable = await request('match-flip', { expectedRevision: reopened.match.revision + 1 });
assert.deepEqual([unavailable.ledger, unavailable.match], [opened.ledger, null]);
console.log('invalid-revision\t' + invalid.error);
console.log('missing-board\t' + unavailable.outcome);
