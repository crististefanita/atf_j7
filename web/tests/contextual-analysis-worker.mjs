import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { runtimeDirectory } from '../tools/package-web.mjs';

const cases = JSON.parse(readFileSync(new URL('../../atf-tests/src/test/resources/contextual-analysis.json', import.meta.url), 'utf8'));
const site = process.argv[2];
assert.ok(site, 'Provide the built TeaVM site');
const { studyCommand, openLedger, ledgerState } = await import(pathToFileURL(join(await runtimeDirectory(site), 'chess-engine.mjs')));
const invoke = (action, { value = '', index = 0, saved = '', option = '' } = {}) =>
    JSON.parse(studyCommand('analysis', action, value, index, saved, option, 'en'));

openLedger(null, cases[2].checkpoint, `ai|${cases[0].checkpoint}`);
const originalLedger = ledgerState();
assert.ok(originalLedger.includes('ADVANCED'), 'The worker ledger contains the active AI source');
const standalone = invoke('open', { value: '1|d2d4 d7d5', index: 2 });
for (const example of cases) {
    const opening = { value: example.checkpoint, index: example.ply, option: example.blackBelow ? 'black' : '' };
    const entry = invoke('open', opening);
    assert.deepEqual([entry.ply, entry.sideToMove, entry.blackBelow, entry.lastMove, entry.result],
        [example.ply, example.side, example.blackBelow, example.lastMove, example.studyResult], example.id);
    if (example.branchMove) {
        invoke('tap', { value: example.branchMove.slice(0, 2) });
        const branch = invoke('tap', { value: example.branchMove.slice(2, 4) });
        assert.deepEqual(branch.history, example.branchHistory, example.id);
    } else {
        assert.equal(invoke('continue').continuation, null, example.id);
    }
    assert.throws(() => invoke('open', { ...opening, index: entry.total + 1 }), undefined, example.id);
    assert.equal(ledgerState(), originalLedger, example.id);
    const restored = invoke('resume', { saved: standalone.saved });
    assert.deepEqual([restored.saved, restored.checkpoint, restored.board],
        [standalone.saved, standalone.checkpoint, standalone.board], example.id);
    console.log(`PASS ${example.id}`);
}
