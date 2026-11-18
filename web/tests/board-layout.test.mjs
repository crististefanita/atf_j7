import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { cssPixels } from './support/css-length.mjs';

test('Analysis reference sizing matches independent screen, rail and font fixtures', async () => {
    const cases = JSON.parse(await readFile(new URL('fixtures/analysis-board-layout.json', import.meta.url), 'utf8'));
    const actual = JSON.parse(execFileSync(process.execPath, [fileURLToPath(new URL('board-layout-probe.mjs', import.meta.url))], { encoding: 'utf8' }));
    assert.equal(actual.length, cases.length);
    for (const [index, example] of cases.entries()) assert.deepEqual(actual[index],
        { id: example.id, limit: example.limit.toFixed(3), square: example.square.toFixed(3) });
});

test('The arithmetic harness rejects unresolved, cyclic and unsupported CSS instead of guessing', () => {
    const environment = { variables: {}, rem: 16, viewportHeight: 420 };
    assert.equal(cssPixels('calc(2 * min(10px, 1rem) + 3px)', environment), 23);
    assert.throws(() => cssPixels('var(--missing)', environment));
    assert.throws(() => cssPixels('var(--cycle)', { ...environment, variables: { '--cycle': 'var(--cycle)' } }));
    assert.throws(() => cssPixels('calc(1px / 0)', environment));
    assert.throws(() => cssPixels('12vw', environment));
    assert.throws(() => cssPixels('calc(1px, 2px)', environment));
});
