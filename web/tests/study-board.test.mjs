import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { bindBoardSelection, boardKeyIndex, boardPlacement, studyMarkers } from '../src/main/web/study-board.mjs';

test('Each study route has an enabled home entry and a distinct panel', async () => {
    const html = await readFile(new URL('../src/main/web/index.html', import.meta.url), 'utf8');
    assert.match(html, /<button id="open-review"[^>]*>Main menu<\/button>/);
    for (const [entry, route] of [['puzzles', 'quests'], ['openings', 'openings'], ['analysis', 'analysis']]) {
        const button = html.match(new RegExp('<button id="home-' + entry + '"[^>]*>'));
        assert.ok(button);
        assert.doesNotMatch(button[0], /\bdisabled\b/);
        assert.match(html, new RegExp('<section id="study-' + route + '" class="study-surface" hidden>'));
    }
});

test('Study accepts native square order and placement without moving any piece', () => {
    const placement = boardPlacement('r3k2r/8/8/8/8/8/4P3/4K3 w kq - 0 1');
    assert.equal(placement.length, 64);
    assert.equal(placement[0], 'r');
    assert.equal(placement[4], 'k');
    assert.equal(placement[7], 'r');
    assert.equal(placement[52], 'P');
    assert.equal(placement[60], 'K');
    assert.equal(boardPlacement(placement), placement);
    assert.throws(() => boardPlacement('16/8/8/8/8/8/8/8'), /Invalid board placement/);
    assert.equal(studyMarkers(['e2', { square: 'e7', kind: 'danger' }]).get('e2'), 'hint');
    assert.equal(studyMarkers(['e2', { square: 'e7', kind: 'danger' }]).get('e7'), 'danger');
});

test('Keyboard follows displayed squares without wrapping rows or activating a locked position', () => {
    const listeners = new Map();
    const chosen = [];
    let focused = -1;
    const children = Array.from({ length: 64 }, (_, index) => ({
        dataset: { square: String(index) }, tabIndex: index === 0 ? 0 : -1, locked: false,
        closest() { return this; }, focus() { focused = index; },
        getAttribute(name) { return name === 'aria-disabled' ? String(this.locked) : null; },
    }));
    bindBoardSelection({ children, contains: cell => children.includes(cell),
        querySelector: () => children.find(cell => cell.tabIndex === 0),
        addEventListener: (name, callback) => listeners.set(name, callback) }, square => chosen.push(square));
    assert.equal(boardKeyIndex('ArrowRight', 7), -1);
    assert.equal(boardKeyIndex('ArrowDown', 56), -1);
    let prevented = false;
    listeners.get('keydown')({ key: 'ArrowDown', target: children[0], preventDefault() { prevented = true; } });
    assert.equal(prevented, true);
    assert.equal(focused, 8);
    assert.equal(children[8].tabIndex, 0);
    children[8].locked = true;
    listeners.get('click')({ target: children[8] });
    assert.deepEqual(chosen, []);
    listeners.get('click')({ target: children[9] });
    assert.deepEqual(chosen, ['9']);
    assert.equal(children[8].tabIndex, -1);
    assert.equal(children[9].tabIndex, 0);
});
