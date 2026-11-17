import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { bindBoardSelection, boardKeyIndex, boardPlacement, mountStudyArrow, studyArrow, studyMarkers, studySquareDescription } from '../src/main/web/study-board.mjs';
import { studyHashes, studyRouteForHash, watchStudyHistory, writeModeHash } from '../src/main/web/study-navigation.mjs';

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

test('Study links, Back and Forward reopen the route represented by the address', () => {
    const origin = 'http://127.0.0.1/atf_j7/?lang=ro';
    const entries = [new URL(origin)];
    let index = 0;
    let onHashChange;
    const browser = {
        get location() { return entries[index]; },
        history: {
            pushState(_state, _title, url) {
                entries.splice(index + 1, entries.length, new URL(url, browser.location));
                index++;
            },
            replaceState(_state, _title, url) {
                entries[index] = new URL(url, browser.location);
            },
        },
        addEventListener(type, listener) {
            assert.equal(type, 'hashchange');
            onHashChange = listener;
        },
    };
    function traverse(offset) {
        const previousHash = browser.location.hash;
        index += offset;
        if (browser.location.hash !== previousHash) onHashChange();
    }
    const visible = [];
    let current;
    watchStudyHistory(browser, () => current, hash => {
        current = studyRouteForHash(hash);
        visible.push(current ?? 'menu');
        writeModeHash(browser, hash || undefined, false);
    });
    writeModeHash(browser, studyHashes.quests, true);
    current = 'quests';
    writeModeHash(browser, studyHashes.quests, true);
    assert.equal(entries.length, 2, 'an unchanged hash does not create a duplicate entry');
    browser.history.pushState(null, '', studyHashes.openings);
    onHashChange();
    assert.equal(current, 'openings');
    traverse(-1);
    assert.equal(current, 'quests');
    traverse(-1);
    assert.equal(browser.location.href, origin);
    traverse(1);
    traverse(1);
    assert.deepEqual(visible, ['openings', 'quests', 'menu', 'quests', 'openings']);
    assert.equal(browser.location.search, '?lang=ro');
});

test('Study hash routing leaves unrelated route changes alone', () => {
    let current;
    let onHashChange;
    const navigated = [];
    const browser = {
        location: { hash: '#saved' },
        addEventListener(_type, listener) { onHashChange = listener; },
    };
    watchStudyHistory(browser, () => current, hash => navigated.push(hash));
    onHashChange();
    assert.deepEqual(navigated, []);
    browser.location.hash = '#analysis';
    onHashChange();
    current = 'analysis';
    onHashChange();
    assert.deepEqual(navigated, ['#analysis']);
    browser.location.hash = '#saved';
    onHashChange();
    assert.deepEqual(navigated, ['#analysis', '#saved']);
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
});

test('Study draws the native origin and destination cue in both board orientations', () => {
    const { origin, destination, danger } = studyMarkers(['e2', { square: 'e7', kind: 'danger' }, 'e4']);
    assert.deepEqual({ origin, destination, danger }, { origin: 'e2', destination: 'e4', danger: 'e7' });
    const white = studyArrow(origin, destination, false);
    const black = studyArrow(origin, destination, true);
    assert.equal(white.transform, 'translate(4.5 6.5) rotate(-90)');
    assert.equal(black.transform, 'translate(3.5 1.5) rotate(90)');
    assert.equal(white.path, black.path);
    assert.ok(white.path.includes('L 1.72 0'));
    const knight = studyArrow('b1', 'c3', false);
    assert.match(knight.transform, /^translate\(1\.5 7\.5\) rotate\(-63\./);
    assert.equal(studyArrow('e2', undefined, false), null);
    assert.equal(studyArrow('e2', 'e2', false), null);
    assert.equal(studyArrow('e2', 'e9', false), null);
});

test('Study descriptions distinguish the hint, destination and legal move with native RO and EN labels', async () => {
    for (const locale of ['en', 'ro']) {
        const folder = locale === 'ro' ? 'values-ro' : 'values';
        const resources = new URL(`../../android/app/src/main/res/${folder}/strings.xml`, import.meta.url);
        const xml = await readFile(resources, 'utf8');
        for (const [flag, id] of Object.entries({ target: 'legal_destination', origin: 'hint_origin', destination: 'hint_destination' })) {
            const native = xml.match(new RegExp(`<string name="${id}">([^<]+)</string>`))[1].replace('%1$s', 'e4');
            assert.equal(studySquareDescription('e4', locale, { [flag]: true }), native);
        }
    }
    assert.equal(studySquareDescription('e4', 'en', {}), 'e4');
    assert.equal(studySquareDescription('e4', 'ro', { destination: true, danger: true }), 'e4, destinația soluției, pericol');
});

test('Hint overlay survives board rotation and is removed when the solution is hidden', () => {
    function element() {
        return {
            children: [], attributes: {}, parentNode: null,
            setAttribute(name, value) { this.attributes[name] = value; },
            append(child) { child.parentNode = this; this.children.push(child); },
            remove() {
                if (!this.parentNode) return;
                this.parentNode.children = this.parentNode.children.filter(child => child !== this);
                this.parentNode = null;
            },
        };
    }
    const host = element();
    host.ownerDocument = { createElementNS: element };
    const draw = mountStudyArrow(host);
    draw('e2', 'e4', false);
    const [overlay] = host.children;
    assert.equal(overlay.attributes['aria-hidden'], 'true');
    assert.equal(overlay.children[0].attributes.transform, 'translate(4.5 6.5) rotate(-90)');
    draw('e2', 'e4', false);
    assert.deepEqual(host.children, [overlay]);
    overlay.remove();
    draw('e2', 'e4', true);
    assert.deepEqual(host.children, [overlay]);
    assert.equal(overlay.children[0].attributes.transform, 'translate(3.5 1.5) rotate(90)');
    draw('e2', undefined, true);
    assert.deepEqual(host.children, []);
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
