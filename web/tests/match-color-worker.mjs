import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const [worker, fixture] = process.argv.slice(2);
assert.ok(worker && fixture, 'Supply the packaged worker entry and the shared color fixture');
const cases = JSON.parse(readFileSync(fixture, 'utf8'));
let nextId = 0;
let reply;
let randomBlack;
let draws;

globalThis.self = { postMessage: message => reply(message) };
// Replace browser entropy only in this process. No worker message can choose its own draw.
Math.random = () => {
    draws++;
    return randomBlack ? 0.25 : 0.75;
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

for (const example of cases) {
    randomBlack = example.randomBlack;
    draws = 0;
    await request('ledger-open', { ledger: null });
    const opened = await request('match-open', {
        setup: { mode: 'AI', humanSide: example.choice, level: example.level }
    });
    const before = opened.match;
    assert.equal(before.ply, 0, example.id);
    assert.equal(before.sideToMove, 'WHITE', example.id);
    assert.equal(before.legalMoves.length, example.awaitingBot ? 0 : 20, example.id);
    assert.equal(opened.saved[0].humanSide, before.humanSide, example.id);
    assert.ok(!before.reviewCheckpoint.includes('RANDOM'), example.id);

    randomBlack = !example.randomBlack;
    await request('ledger-open', { ledger: opened.ledger });
    const resumed = (await request('match-open', { slot: before.slot })).match;
    assert.deepEqual([resumed.checkpoint, resumed.humanSide, resumed.blackBelow, resumed.awaitingBot],
        [before.checkpoint, before.humanSide, before.blackBelow, before.awaitingBot], example.id);
    const resolved = [before.humanSide, before.level, before.blackBelow, before.awaitingBot, draws];
    assert.deepEqual(resolved, [example.side, example.level, example.blackBelow, example.awaitingBot, example.draws], example.id);
    console.log(example.id + '\t' + resolved.join('/'));

    if (example.id === 'random-black') {
        const completed = await request('match-bot', { expectedRevision: resumed.revision });
        assert.match(completed.match.lastMove, /^[a-h][1-8][a-h][1-8]$/);
        const replay = await request('match-bot', { expectedRevision: resumed.revision });
        assert.equal(replay.match.checkpoint, completed.match.checkpoint);
        console.log('random-black-single-reply\t' + [completed.outcome, completed.match.ply, completed.match.sideToMove,
            replay.outcome, replay.match.ply, draws].join('/'));
    }
}

const previous = await request('match-open', { route: 'AI' });
const invalid = await call('match-open', { setup: { mode: 'AI', humanSide: 'GREEN', level: 'BASIC' } });
const retained = await request('match-open', { route: 'AI' });
assert.equal(previous.ledger, retained.ledger);
console.log('invalid-choice\t' + invalid.error);
