import assert from 'node:assert/strict';
import { test } from 'node:test';
import { historyPlyForKey, revealDistance, reviewFocusCandidates } from '../src/main/web/review-focus.mjs';

test('History navigation stays within recorded moves', () => {
    assert.equal(historyPlyForKey('ArrowUp', 93, 185), 92);
    assert.equal(historyPlyForKey('ArrowDown', 184, 185), 185);
    assert.equal(historyPlyForKey('ArrowUp', 0, 185), 0);
    assert.equal(historyPlyForKey('ArrowDown', 185, 185), 185);
    assert.equal(historyPlyForKey('Home', 93, 185), 0);
    assert.equal(historyPlyForKey('End', 93, 185), 185);
    assert.equal(historyPlyForKey('ArrowRight', 93, 185), null);
});

test('Graph and history preserve their own entry targets', () => {
    assert.equal(reviewFocusCandidates('graph')[0], 'seek');
    assert.equal(reviewFocusCandidates('history')[0], 'selected-history');
});

test('Explanation enters from Back and returns to its opener', () => {
    assert.equal(reviewFocusCandidates('open-why')[0], 'close-why');
    assert.equal(reviewFocusCandidates('close-why')[0], 'why');
});

test('A toolbar command keeps priority before neighboring controls', () => {
    const candidates = reviewFocusCandidates('toolbar', 'next');
    assert.equal(candidates[0], 'next');
    assert.ok(candidates.includes('next-key'));
    assert.ok(candidates.indexOf('next-key') < candidates.indexOf('previous'));
    const line = reviewFocusCandidates('toolbar', 'recommended', true);
    assert.equal(line[0], 'recommended');
    assert.ok(line.includes('played'));
});

test('Revealing focus scrolls only the necessary distance', () => {
    assert.equal(revealDistance(100, 220, 130, 177), 0);
    assert.equal(revealDistance(100, 220, 212, 265), 45);
    assert.equal(revealDistance(100, 220, 80, 140), -20);
    assert.equal(revealDistance(100, 220, 119, 260), 19);
});
