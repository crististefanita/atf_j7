import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { runtimeDirectory } from '../tools/package-web.mjs';

const runtime = await runtimeDirectory(process.argv[2]);
const examples = JSON.parse(await readFile(new URL('../../atf-tests/src/test/resources/match-history-cases.json', import.meta.url), 'utf8'));
let receive;
globalThis.self = { postMessage: reply => receive(reply) };
await import(pathToFileURL(join(runtime, 'engine-worker.mjs')));
const { selectedMatchMove } = await import(pathToFileURL(join(runtime, 'match-history.mjs')));
async function call(type, data = {}) {
    const response = await new Promise(resolve => { receive = resolve; self.onmessage({ data: { id: type, type, ...data } }); });
    assert.equal(response.error, undefined, response.error);
    return response;
}
for (const example of examples.filter(item => !item.fen)) {
    const ledger = await call('ledger-open', { ledger: null, legacy: { local: '1|' + example.moves.join(' ') } });
    let state = await call('match-open', { slot: ledger.saved[0].slot });
    if (example.ply !== state.match.ply) state = await call('match-history', { expectedRevision: state.match.revision, ply: example.ply });
    const selected = selectedMatchMove(state.match);
    assert.equal(selected.compact, example.compact, example.id);
    assert.equal(selected.boundary, example.boundary, example.id);
    const legacy = { ...state.match }; delete legacy.navigation;
    assert.deepEqual(selectedMatchMove(legacy), selected, example.id);
    const flipped = await call('match-flip', { expectedRevision: state.match.revision });
    assert.deepEqual(selectedMatchMove(flipped.match), selected, 'orientation leaves the move identity unchanged');
    console.log('PASS ' + example.id);
}
