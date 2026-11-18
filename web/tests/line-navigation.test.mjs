import assert from 'node:assert/strict';
import test from 'node:test';
import { lineBoundary, renderLinePosition } from '../src/main/web/line-navigation.mjs';

test('Line cursors distinguish start, first, intermediate, last and a single move', () => {
    for (const [ply, total, expected] of [[0, 0, 'START'], [0, 4, 'START'], [1, 1, 'ONLY'], [1, 4, 'FIRST'], [2, 4, 'MIDDLE'], [4, 4, 'LAST']]) {
        assert.equal(lineBoundary(ply, total), expected);
    }
    for (const [ply, total] of [[-1, 4], [5, 4], [0, -1], [NaN, 4], [1, Infinity], [1.5, 4]]) {
        assert.throws(() => lineBoundary(ply, total), RangeError);
    }
});

test('Shared position rendering preserves contextual labels, branch state and unchanged announcements', () => {
    let text = '';
    let writes = 0;
    const attributes = new Map([['aria-label', 'Open move history']]);
    const element = { dataset: { branch: '1' }, setAttribute: (name, value) => attributes.set(name, value),
        get textContent() { return text; }, set textContent(value) { writes++; text = value; } };
    const firstBranch = Object.freeze({ ply: 1, total: 5, text: '1 / 5 · e2 → e4' });
    renderLinePosition(element, firstBranch);
    assert.equal(element.dataset.boundary, 'FIRST');
    assert.equal(attributes.get('aria-label'), 'Open move history');
    renderLinePosition(element, firstBranch);
    assert.equal(writes, 1);
    renderLinePosition(element, { ply: 0, total: 3, text: 'Start', description: 'Return to this line' });
    assert.equal(element.dataset.boundary, 'START');
    assert.equal(element.dataset.branch, '1');
    assert.equal(attributes.get('aria-label'), 'Return to this line');
    renderLinePosition(element, firstBranch);
    assert.equal(text, firstBranch.text, 'returning to a line retains the owner-supplied ply');
});
