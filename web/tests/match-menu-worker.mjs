import assert from 'node:assert/strict';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { runtimeDirectory } from '../tools/package-web.mjs';

const site = process.argv[2];
let receive;
globalThis.self = { postMessage: reply => receive(reply) };
await import(pathToFileURL(join(await runtimeDirectory(site), 'engine-worker.mjs')));

async function call(type, data) {
    const response = await new Promise(resolve => {
        receive = resolve;
        self.onmessage({ data: { id: type, type, ...data } });
    });
    assert.equal(response.error, undefined, response.error);
    return response;
}

const decision = (state, action) => call('match-decision', { action, expectedRevision: state.match.revision });
const change = (state, type, data) => call(type, { expectedRevision: state.match.revision, ...data });
const start = (analysis = false) => call('match-open', { setup: { mode: 'AI', humanSide: 'WHITE', level: 'BASIC', analysis } });
const navigation = ['FLIP', 'HOME', 'SAVED_GAMES', 'ANALYSIS_BOARD'];
const live = [...navigation, 'GAME_REVIEW', 'OFFER_DRAW', 'RESIGN'];

await call('ledger-open');
const fresh = await start(true);
assert.deepEqual(Object.keys(fresh.match.menu), live);
assert.equal(fresh.match.menu.GAME_REVIEW, false);
const pending = await change(fresh, 'match-play', { move: 'e2e4' });
assert.deepEqual([pending.match.menu.GAME_REVIEW, pending.match.menu.OFFER_DRAW, pending.match.menu.RESIGN], [true, false, true]);
assert.equal((await decision(pending, 'OFFER_DRAW')).outcome, 'UNAVAILABLE');
const history = await change(pending, 'match-history', { ply: 0 });
assert.deepEqual(Object.keys(history.match.menu), navigation);
assert.equal((await decision(history, 'RESIGN')).outcome, 'UNAVAILABLE');
assert.equal((await decision(pending, 'RESIGN')).outcome, 'STALE');
const latest = await change(history, 'match-history', { ply: 1 });
const loss = await decision(latest, 'RESIGN');
assert.deepEqual([loss.outcome, loss.match.result, loss.match.resigned, loss.match.ply], ['APPLIED', 'BLACK_WINS', true, 1]);
assert.equal((await decision(loss, 'RESIGN')).outcome, 'UNAVAILABLE');
assert.deepEqual(Object.keys(loss.match.menu), [...navigation, 'GAME_REVIEW']);
const id = loss.match.archiveId;
await call('ledger-open', { ledger: loss.ledger });
const archived = await call('match-open', { archiveId: id });
assert.equal(archived.match.checkpoint, loss.match.checkpoint);
assert.equal(archived.finished.filter(entry => entry.id === id && entry.status === 'LOST').length, 1);
console.log('PASS History and stale decisions are guarded and a resignation is archived once');

await call('ledger-open', { legacy: { ai: 'ai|1|e2e4 d7d5 a2a3 d5e4' } });
const ahead = await call('match-open', { route: 'AI' });
const declined = await decision(ahead, 'OFFER_DRAW');
assert.equal(declined.outcome, 'DECLINED');
for (const key of ['ledger', 'match', 'saved', 'finished']) assert.deepEqual(declined[key], ahead[key]);
const plain = await start();
assert.deepEqual(Object.keys(plain.match.menu), [...navigation, 'OFFER_DRAW', 'RESIGN']);
const draw = await decision(plain, 'OFFER_DRAW');
assert.deepEqual([draw.outcome, draw.match.result, draw.match.ply, draw.finished[0].status], ['APPLIED', 'AGREED_DRAW', 0, 'DRAW']);
assert.equal((await decision(draw, 'OFFER_DRAW')).outcome, 'UNAVAILABLE');
console.log('PASS Draw refusal keeps the saved game and agreement archives a draw');
