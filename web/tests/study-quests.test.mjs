import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { setImmediate } from 'node:timers/promises';
import { createQuestController, createQuestStudy, questChapterLabels, questLessons, selectQuestSquare } from '../src/main/web/study-quests.mjs';

const initial = { type: 'puzzle', id: 'mate2-001', ply: 0, hint: 'NONE', legalMoves: ['h2h4'] };
const pending = { ...initial, ply: 1, pending: true, legalMoves: [] };
const ready = { ...initial, ply: 2 };
const menu = { type: 'menu', resume: 'mate-two', overall: { solved: 2, total: 4, points: 20 } };

function fixture(call) {
    const data = new Map();
    const changes = [];
    const errors = [];
    const requests = [];
    const callbacks = [];
    const storage = { getItem: key => data.get(key), setItem: (key, value) => data.set(key, value) };
    const controller = createQuestController({
        storage,
        onChange: value => changes.push(value),
        onError: (error, action) => errors.push({ error, action }),
        schedule: (callback, delay) => {
            const timer = { callback, delay, cancelled: false };
            callbacks.push(timer);
            return timer;
        },
        cancel: timer => { timer.cancelled = true; },
        call: async (action, args) => {
            requests.push({ action, args });
            return call(action, args);
        },
    });
    return { controller, data, storage, changes, errors, requests, callbacks };
}

test('Web puzzle menu has the six APK chapters in native order in RO and EN', async () => {
    const base = new URL('../../android/app/src/main/res/', import.meta.url);
    const layout = await readFile(new URL('layout/puzzle_menu.xml', base), 'utf8');
    const entries = [...layout.matchAll(/android:text="@string\/(open_[^"]+)"/g)].map(match => match[1]);
    assert.deepEqual(entries, ['open_mate_one', 'open_mate_two', 'open_mate_three', 'open_tactics', 'open_training', 'open_intermezzo']);
    for (const [locale, path] of [['en', 'values'], ['ro', 'values-ro']]) {
        const strings = await readFile(new URL(path + '/strings.xml', base), 'utf8');
        const native = entries.map(id => strings.match(new RegExp('<string name="' + id + '">([^<]+)'))?.[1]);
        assert.deepEqual(Object.values(questChapterLabels[locale]), native);
        for (const [key, text] of Object.entries(questLessons[locale])) {
            assert.equal(text, strings.match(new RegExp('<string name="tactics_lesson_' + key.toLowerCase() + '">([^<]+)'))?.[1]);
        }
    }
});

test('Only legal destinations select a move and promotion choices are preserved', () => {
    const puzzle = { legalMoves: ['h7h8q', 'h7h8r', 'h7h8n', 'h7h8b', 'a2a3', 'a2a4'] };
    assert.deepEqual(selectQuestSquare(puzzle, null, 'h7'), { selected: 'h7', targets: ['h8'], chosen: [] });
    assert.deepEqual(selectQuestSquare(puzzle, 'h7', 'h8').chosen, puzzle.legalMoves.slice(0, 4));
    assert.deepEqual(selectQuestSquare(puzzle, 'h7', 'e5'), { selected: null, targets: [], chosen: [] });
    assert.deepEqual(selectQuestSquare(puzzle, 'a2', 'a2'), { selected: null, targets: [], chosen: [] });
});

test('Paired scripted reply commits once from the persisted quest and ply', async () => {
    const { controller, data, changes, requests, callbacks } = fixture(action => {
        if (action === 'menu') return menu;
        if (action === 'open') return { saved: 'initial', data: initial };
        if (action === 'play') return { saved: 'pending', data: pending };
        return { saved: 'ready', data: ready };
    });
    await controller.open({ resume: true });
    assert.deepEqual(requests.map(request => request.action), ['menu', 'open']);
    assert.equal(requests[1].args.value, 'mate-two');
    await controller.run('play', { value: 'h2h4' });
    assert.deepEqual([...data.values()], ['pending']);
    assert.equal(callbacks.length, 1);
    assert.equal(callbacks[0].delay, 650);
    callbacks[0].callback();
    await setImmediate();
    assert.deepEqual(requests.at(-1), { action: 'defense', args: { saved: 'pending', option: 'NONE', value: 'mate2-001', index: 1 } });
    assert.deepEqual([...data.values()], ['ready']);
    assert.equal(changes.at(-1).state, ready);
});

test('Leaving a pending defense cancels stale callbacks and reopening resumes the saved reply', async () => {
    const { controller, requests, data, callbacks } = fixture((action, args) => {
        if (action === 'menu') return menu;
        if (action === 'open' && args.saved === 'pending') return { saved: 'pending', data: pending };
        if (action === 'open') return { saved: 'initial', data: initial };
        if (action === 'play') return { saved: 'pending', data: pending };
        return { saved: 'ready', data: ready };
    });
    await controller.open({ resume: true });
    await controller.run('play', { value: 'h2h4' });
    const old = callbacks[0];
    controller.close();
    assert.equal(old.cancelled, true);
    await controller.open({ resume: true });
    old.callback();
    await setImmediate();
    assert.equal(requests.filter(item => item.action === 'defense').length, 0);
    const fresh = callbacks[1];
    fresh.callback();
    await setImmediate();
    assert.equal(requests.filter(item => item.action === 'defense').length, 1);
    assert.deepEqual([...data.values()], ['ready']);
});

test('A failed write keeps the prior board and an externally changed puzzle blocks stale commands', async () => {
    const { controller, storage, data, changes, errors, requests } = fixture(action => {
        if (action === 'menu') return menu;
        if (action === 'open') return { saved: 'initial', data: initial };
        return { saved: 'pending', data: pending };
    });
    await controller.open({ resume: true });
    storage.setItem = () => { throw new Error('Quota'); };
    assert.equal(await controller.run('play', { value: 'h2h4' }), false);
    assert.equal(changes.at(-1).state, initial);
    assert.equal(errors.at(-1).action, 'play');
    const previousRequests = requests.length;
    data.set([...data.keys()][0], 'other-tab');
    assert.equal(await controller.run('play', { value: 'h2h4' }), false);
    assert.equal(requests.length, previousRequests);
    assert.match(errors.at(-1).error.message, /changed/);
});

test('Home summary reads saved totals without mounting the quest board', async () => {
    const { controller, requests, changes } = fixture(() => menu);
    assert.deepEqual(await controller.summary(), menu.overall);
    assert.deepEqual(requests, [{ action: 'summary', args: { saved: '' } }]);
    assert.deepEqual(changes, []);
});

async function mountPuzzle(state, call, locale = 'ro') {
    let focused = '';
    let exits = 0;
    let markup;
    class Element {
        id = '';
        children = [];
        events = new Map();
        attributes = new Map();
        #text = '';
        textWrites = 0;
        hidden = false;
        open = false;
        classList = { add() {} };
        get textContent() { return this.#text; }
        set textContent(value) {
            this.#text = value;
            this.textWrites++;
        }
        append(...items) { this.children.push(...items); }
        replaceChildren(...items) { this.children = items; }
        querySelectorAll() { return this.children.flatMap(row => row.children); }
        setAttribute(name, value) { this.attributes.set(name, value); }
        toggleAttribute(name, enabled) {
            if (enabled) this.attributes.set(name, '');
            else this.attributes.delete(name);
        }
        addEventListener(name, fn) { this.events.set(name, fn); }
        showModal() { this.open = true; }
        close() { this.open = false; this.events.get('close')?.(); }
        focus() { focused = this.id; }
    }
    const elements = new Map();
    const root = new Element();
    root.ownerDocument = { createElement: () => new Element() };
    Object.defineProperty(root, 'innerHTML', {
        set: html => {
            markup = html;
            for (const [tag, id] of html.matchAll(/<[^>]+\bid="([^"]+)"[^>]*>/g)) {
                const item = new Element();
                item.id = id;
                for (const [, key, value] of tag.matchAll(/\s([a-z-]+)="([^"]+)"/g)) item.setAttribute(key, value);
                elements.set('#' + id, item);
            }
        },
    });
    root.querySelector = selector => elements.get(selector);
    const at = id => root.querySelector('#quest-study-' + id);
    const boards = new Map();
    const storage = new Map();
    const study = createQuestStudy({ root, locale, home() { exits++; },
        storage: { getItem: key => storage.get(key), setItem: (key, value) => storage.set(key, value) },
        call: async (action, args) => {
            if (action === 'menu') return { ...menu, chapters: [{ key: state.chapter, totals: state.totals }] };
            if (action === 'open') return { saved: 'start', data: state };
            return call(action, args);
        },
        board: (host, tap) => {
            const reports = [];
            const render = board => reports.push(board);
            boards.set(host.id, { tap, reports });
            return { render };
        },
    });
    await study.open();
    const chapter = at('chapters').children[0].children[0];
    await chapter.events.get('click')();
    return { at, root, markup, chapter, focused: () => focused, exits: () => exits,
        ...boards.get('quest-study-board'), replayReports: boards.get('quest-study-replay-board').reports };
}

const tacticsState = { type: 'puzzle', chapter: 'tactics', id: 'tactic-006', rank: 6, count: 100, startSide: 'WHITE',
    sideToMove: 'WHITE', objective: 'MATERIAL', board: ' '.repeat(64), legalMoves: ['d1e2'], hint: 'NONE',
    hintAction: 'SHOW_PIECE', previous: false, next: false, pending: false, solved: false, ply: 0, stage: 0, playerMoves: 1,
    totals: { solved: 5, total: 100, points: 50 }, milestone: { next: 15, reached: true } };

test('Wrong material check stays in the current puzzle and keeps the Web controls usable', async () => {
    const { at, tap, reports } = await mountPuzzle(tacticsState, (action, args) => {
        assert.deepEqual([action, args.value], ['play', 'd1e2']);
        return { saved: 'attempted', data: { ...tacticsState, attempt: { outcome: 'CHECK', move: 'd1e2' } } };
    });
    assert.equal(at('milestone').textContent, '5 atinse · urmează 15');
    assert.equal(at('details').hidden, true);
    tap('d1');
    assert.deepEqual(reports.at(-1).targets, ['e2']);
    tap('e2');
    await setImmediate();
    assert.equal(at('status').textContent, 'Există o mutare mai bună. Încearcă din nou.');
    assert.equal(at('details').hidden, false);
    assert.equal(at('details').textContent, 'Detaliile mutării');
    at('details').events.get('click')();
    assert.equal(at('explanation').open, true);
    assert.equal(at('explain-text').textContent, at('status').textContent);
    assert.equal(at('replay').hidden, true);
    assert.equal(at('lesson-actions').hidden, true);
    assert.equal(reports.at(-1).locked, false);
    assert.equal(at('hint').disabled, false);
    assert.equal(at('next').disabled, true);
});

test('Puzzle hints announce the APK turn, piece, destination and promotion once in RO and EN', async () => {
    const resources = new URL('../../android/app/src/main/res/', import.meta.url);
    for (const [locale, folder, side, from, to, token] of [
        ['en', 'values', 'white', 'd7', 'd8', 'P'],
        ['ro', 'values-ro', 'black', 'd2', 'd1', 'p'],
    ]) {
        const strings = await readFile(new URL(folder + '/strings.xml', resources), 'utf8');
        const native = (key, ...values) => strings.match(new RegExp('<string name="' + key + '">([^<]+)'))[1]
            .replace(/%(\d+)\$s/g, (_, index) => values[Number(index) - 1]);
        const sideToMove = side.toUpperCase();
        const square = (8 - Number(from[1])) * 8 + 3;
        const board = ' '.repeat(square) + token + ' '.repeat(63 - square);
        const suggestion = from + to + 'q';
        const state = { ...tacticsState, startSide: sideToMove, sideToMove, board, legalMoves: [suggestion] };
        const hints = ['PIECE', 'SOLUTION'];
        const { at, tap } = await mountPuzzle(state, action => {
            assert.equal(action, 'hint');
            const hint = hints.shift();
            return { saved: 'start', data: { ...state, hint, suggestion, hintAction: 'SHOW_MOVE' } };
        }, locale);
        const turn = native('to_move', native(side));
        const piece = native('hint_description', from, native(side), native('pawn'));
        const announcement = at('announcement');
        assert.equal(announcement.attributes.get('role'), 'status');
        assert.equal(announcement.attributes.get('aria-atomic'), 'true');
        assert.equal(at('status').attributes.has('aria-live'), false);
        assert.equal(at('turn').attributes.has('aria-live'), false);
        at('hint').events.get('click')();
        await setImmediate();
        assert.equal(at('status').textContent, piece);
        assert.equal(announcement.textContent, turn + '. ' + piece);
        const pieceWrites = announcement.textWrites;
        tap(from);
        assert.equal(announcement.textWrites, pieceWrites);
        at('hint').events.get('click')();
        await setImmediate();
        const move = native('solution_promotion', from, to, native('queen'));
        assert.equal(at('status').textContent, native('suggested_move', move));
        assert.equal(announcement.textContent, native('full_hint_announcement', turn, piece, to) + ' ' + move);
        const moveWrites = announcement.textWrites;
        tap(from);
        assert.equal(announcement.textWrites, moveWrites);
    }
});

test('Puzzle options match the APK and Escape returns focus to the chapters', async () => {
    let leaves = 0;
    const { at, root, markup, focused, reports, chapter, exits } = await mountPuzzle(tacticsState, action => {
        assert.equal(action, 'leave');
        leaves++;
        return { ...menu, chapters: [{ key: tacticsState.chapter, totals: tacticsState.totals }] };
    });
    const options = markup.match(/<dialog id="quest-study-options">([^]*?)<\/dialog>/)[1];
    assert.deepEqual([...options.matchAll(/<button id="quest-study-([^"]+)"/g)].map(match => match[1]), ['flip', 'game-home']);
    assert.equal(focused(), 'quest-study-chapter');
    assert.equal(at('option-title').attributes.get('aria-label'), 'Opțiuni puzzle');
    at('option-title').events.get('click')();
    assert.equal(at('options').open, true);
    assert.equal(focused(), 'quest-study-flip');
    assert.equal(at('option-title').attributes.get('aria-expanded'), 'true');
    at('flip').events.get('click')();
    assert.equal(reports.at(-1).blackBelow, true);
    assert.equal(at('option-title').attributes.get('aria-expanded'), 'false');
    assert.equal(focused(), 'quest-study-option-title');
    let escapeHandled = false;
    root.events.get('keydown')({ key: 'Escape', preventDefault() { escapeHandled = true; } });
    await setImmediate();
    assert.equal(escapeHandled, true);
    assert.equal(focused(), 'quest-study-title');
    assert.equal(at('menu').hidden, false);
    assert.equal(leaves, 1);
    await chapter.events.get('click')();
    at('option-title').events.get('click')();
    at('game-home').events.get('click')();
    await setImmediate();
    assert.equal(leaves, 2);
    assert.equal(exits(), 1);
    assert.equal(at('options').open, false);
});

test('Puzzle points, streak and guidance match the native states and announce a newly reached goal once', async () => {
    const state = { ...tacticsState, objective: 'MATE', rank: 5, streak: 1,
        totals: { solved: 4, total: 100, points: 40 }, milestone: { next: 5, reached: false } };
    const { at, tap } = await mountPuzzle(state, action => {
        assert.equal(action, 'play');
        return { saved: 'solved', data: { ...state, solved: true, earned: 14, streak: 2, hintAction: 'EXPLAIN',
            totals: { solved: 5, total: 100, points: 54 }, milestone: { next: 15, reached: true } } };
    });
    assert.equal(at('chapter').textContent, 'Tactică');
    assert.equal(at('rank').textContent, '5 / 100');
    assert.equal(at('rank').attributes.get('aria-label'), 'Problema 5 din 100');
    assert.equal(at('score').textContent, '40 puncte');
    assert.equal(at('series-badge').attributes.get('aria-hidden'), 'true');
    assert.equal(at('hint-label').textContent, 'Arată piesa');
    assert.equal(at('hint').attributes.get('aria-label'), 'Indiciu');
    assert.equal(at('lamp-piece').attributes.has('hidden'), false);
    tap('d1');
    tap('e2');
    await setImmediate();
    assert.equal(at('score').textContent, '+14 · 54 puncte');
    assert.equal(at('series-badge').attributes.get('aria-hidden'), 'false');
    assert.equal(at('current-series').textContent, '2 la rând');
    assert.equal(at('solved-count').textContent, 'Rezolvate · 5/100');
    assert.equal(at('hint-label').textContent, 'Explică');
    assert.equal(at('hint').attributes.get('aria-label'), 'De ce e mat?');
    assert.equal(at('lamp-piece').attributes.has('hidden'), true);
    assert.equal(at('lamp-explain').attributes.has('hidden'), false);
    assert.equal(at('announcement').textContent, 'Șah mat · Rezolvat. 5 atinse · urmează 15');
    const writes = at('announcement').textWrites;
    at('option-title').events.get('click')();
    at('flip').events.get('click')();
    assert.equal(at('announcement').textWrites, writes);
});

test('Puzzle replay keeps its actions outside the scrollable text and retains eight square rows on compact screens', async () => {
    const board = ' '.repeat(64);
    const { at, markup, replayReports } = await mountPuzzle({ ...tacticsState, hintAction: 'EXPLAIN' }, action => {
        assert.equal(action, 'hint');
        return { type: 'replay', initial: 1, depth: null, lesson: 'FORK', frames: [
            { board, sideToMove: 'WHITE', material: 0 },
            { board, sideToMove: 'BLACK', material: 100, lastMove: 'd1e2' },
        ] };
    });
    at('hint').events.get('click')();
    await setImmediate();
    assert.equal(at('explanation').open, true);
    assert.equal(at('explanation').attributes.has('data-replay'), true);
    assert.equal(at('replay-actions').hidden, false);
    assert.equal(at('replay-previous').disabled, false);
    assert.equal(at('replay-next').disabled, true);
    assert.equal(replayReports.at(-1).lastMove, 'd1e2');
    at('replay-details').scrollTop = 100;
    at('replay-previous').events.get('click')();
    assert.equal(at('replay-details').scrollTop, 0);
    assert.equal(at('replay-previous').disabled, true);
    assert.equal(at('replay-next').disabled, false);
    assert.equal(replayReports.at(-1).sideToMove, 'WHITE');
    assert.match(markup, /id="quest-study-replay-details"[^]*?<\/div>\s*<\/div>\s*<nav id="quest-study-replay-actions"/);
    const css = await readFile(new URL('../src/main/web/study-quests.css', import.meta.url), 'utf8');
    const shared = await readFile(new URL('../src/main/web/game-review.css', import.meta.url), 'utf8');
    assert.match(shared, /grid-template-rows:\s*repeat\(8, minmax\(0, 1fr\)\)/);
    assert.match(shared, /button\.square\s*\{\s*min-block-size:\s*0/);
    assert.doesNotMatch(css, /\.quest-study button\s*\{/);
    assert.match(css, /\.quest-study button:not\(\.square\)\s*\{\s*min-block-size:/);
    assert.match(css, /\[data-replay\]\[open\]\s*\{[^}]*height:\s*100dvh/);
});
