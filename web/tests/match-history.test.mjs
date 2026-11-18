import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { renderMatchHistory, selectedMatchMove } from '../src/main/web/match-history.mjs';

const examples = JSON.parse(await readFile(new URL('../../atf-tests/src/test/resources/match-history-cases.json', import.meta.url), 'utf8'));
const frame = example => {
    const start = example.fen?.split(' ')[1] === 'b' ? 'BLACK' : 'WHITE';
    const other = start === 'WHITE' ? 'BLACK' : 'WHITE';
    return { viewPly: example.ply, ply: example.moves.length, history: example.moves,
        sideToMove: example.ply % 2 ? other : start, blackBelow: false };
};

test('Shared game history cases keep the selected move and boundary in both wire formats', () => {
    for (const example of examples) {
        const old = frame(example);
        const projected = selectedMatchMove(old);
        assert.equal(projected.compact, example.compact, example.id);
        assert.equal(projected.boundary, example.boundary, example.id);
        for (const blackBelow of [false, true]) assert.deepEqual(selectedMatchMove({ ...old, blackBelow, navigation: projected }), projected);
    }
});

test('Only the current move is visible and boundary updates survive growth, stepping and branching', () => {
    const attributes = new Map();
    let writes = 0;
    let visible = '';
    const output = { dataset: {}, setAttribute: (key, value) => attributes.set(key, value),
        get textContent() { return visible; }, set textContent(value) { writes++; visible = value; } };
    for (const locale of ['en', 'ro']) {
        for (const id of ['single-move', 'first', 'black-middle', 'white-last', 'single-start', 'branched-first', 'promotion']) {
            const example = examples.find(item => item.id === id);
            renderMatchHistory(output, frame(example), locale);
            assert.equal(visible, example.compact || 'Start');
            assert.equal(output.dataset.boundary, example.boundary);
            assert.ok(attributes.get('aria-label').includes(visible));
            assert.equal(output.title, attributes.get('aria-label'));
        }
        assert.ok(attributes.get('aria-label').includes(locale === 'ro' ? 'Prima și ultima mutare' : 'First and latest move'));
        const before = writes;
        renderMatchHistory(output, frame(examples.find(item => item.id === 'promotion')), locale);
        assert.equal(writes, before, 'unchanged frames do not repeat the live text announcement');
    }
});

test('Invalid and stale cursors cannot render an unrelated move', () => {
    const valid = frame(examples.find(item => item.id === 'black-middle'));
    for (const change of [{ viewPly: -1 }, { viewPly: 6 }, { viewPly: 1.5 }, { history: [] }, { sideToMove: 'NONE' },
        { navigation: { ply: 3, total: 5, move: 'g1f3' } }, { history: ['e2e4', 'broken', 'g1f3', 'b8c6', 'f1b5'] },
        { navigation: { ...selectedMatchMove(valid), compact: '' } },
        { navigation: { ...selectedMatchMove(valid), boundary: 'LAST' } }]) assert.throws(() => selectedMatchMove({ ...valid, ...change }), RangeError);
});

test('Game navigation reserves space for one move and keeps continuation outside the arrow row', async () => {
    const [markup, css] = await Promise.all(['index.html', 'game-review.css']
        .map(file => readFile(new URL('../src/main/web/' + file, import.meta.url), 'utf8')));
    const row = markup.match(/<nav id="match-history"[^]*?<\/nav>/)[0];
    assert.match(row, /id="match-previous"[^]*?id="match-ply"[^]*?id="match-next"/);
    assert.doesNotMatch(row, /id="continue-match"/);
    assert.match(row, /id="match-ply"[^>]*aria-atomic="true"/);
    assert.match(css, /#match-ply\s*\{[^}]*min-block-size: var\(--play-action-size\)/);
    for (const boundary of ['START', 'FIRST', 'LAST', 'ONLY']) assert.ok(css.includes('[data-boundary="' + boundary + '"]'));
});
