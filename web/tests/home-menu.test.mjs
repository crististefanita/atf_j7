import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { setImmediate } from 'node:timers/promises';
import { createHomeMenu, hasSavedHomeGames, homeLabels, homeLocale, reloadProfileLanguage, saveHomeProfile } from '../src/main/web/home-menu.mjs';
import { profileKey, readProfile, writeProfile } from '../src/main/web/player-profile.mjs';
import { showReviewPiece } from '../src/main/web/review-workspace.mjs';
import { routeForHash } from '../src/main/web/study-navigation.mjs';
import { createMatchBotTimer } from '../src/main/web/match-bot-timer.mjs';
import { bindInlineMatchAnalysis, createInlineAnalysisSession, guidedMatchMove } from '../src/main/web/match-inline-analysis.mjs';
import { createReviewImport } from '../src/main/web/review-import.mjs';

const base = new URL('../../', import.meta.url);
const read = path => readFile(new URL(path, base), 'utf8');

test('Inline analysis starts unchecked and shares the board frame geometry', async () => {
    const [layout, html, styles] = await Promise.all([
        read('android/app/src/main/res/layout/match_setup.xml'),
        read('web/src/main/web/index.html'),
        read('web/src/main/web/game-review.css'),
    ]);
    const native = layout.match(/<CheckBox android:id="@\+id\/match_analysis_option"[^>]+>/)?.[0];
    assert.ok(native);
    assert.doesNotMatch(native, /android:checked="true"/);
    const checkbox = html.match(/<input\b[^>]*\bid="ai-analysis"[^>]*>/)?.[0] ?? '';
    assert.match(checkbox, /\btype="checkbox"/);
    assert.doesNotMatch(checkbox, /\bchecked\b/);
    assert.match(checkbox, /\baria-describedby="ai-analysis-help"/);
    assert.match(html, /<small id="ai-analysis-help">[^<]+<\/small>/);
    assert.match(html, /<div id="match-inline-analysis"[^>]*hidden>/);
    const boardFrame = html.match(/<div class="match-board-frame">([^]*?)<p id="match-turn"/)?.[1] ?? '';
    assert.match(boardFrame, /id="match-inline-meter"[^>]*role="meter"[^>]*hidden[^]*id="match-board"/);
    const history = html.match(/<nav id="match-history"[^>]*>([^]*?)<\/nav>/)?.[1] ?? '';
    assert.match(history, /id="match-previous"[^]*id="match-next"[^]*id="match-inline-hint"/);
    assert.match(styles, /\.match-board-frame\s*\{[^}]*inline-size:\s*min\(100%,\s*var\(--play-board-limit\)\)/);
});

test('Home ends with native settings and archive tools belong to standalone review', async () => {
    const html = await read('web/src/main/web/index.html');
    const header = html.match(/<main>\s*<header[^>]*>([^]*?)<\/header>/)?.[1];
    const home = html.slice(html.indexOf('<div id="home-menu"'), html.indexOf('<section id="review-tools"'));
    const tools = html.match(/<section id="review-tools"[^>]*hidden>([^]*?)<\/section>/)?.[1];
    assert.ok(header && home && tools);
    assert.doesNotMatch(header, /id="(?:reopen-review|archive)"/);
    assert.doesNotMatch(home, /id="(?:open-match|reopen-review|archive|archive-label|review-import)"/);
    assert.deepEqual([...home.matchAll(/<button id="([^"]+)"/g)].map(entry => entry[1]),
        ['open-ai', 'open-saved', 'open-finished', 'home-puzzles', 'home-openings', 'home-analysis', 'home-name', 'home-settings']);
    assert.match(tools, /id="reopen-review"[^>]*hidden/);
    assert.match(tools, /id="archive"[^>]*type="file"/);
});

function reviewImportFixture() {
    const controls = new Map();
    for (const id of ['review-tools', 'reopen-review', 'archive']) {
        const events = new Map();
        controls.set(id, {
            hidden: true, value: '',
            addEventListener(type, listener) { events.set(type, listener); },
            fire(type) { events.get(type)({ target: this }); },
        });
    }
    const resumed = [];
    const loaded = [];
    const find = controls.get.bind(controls);
    const entry = createReviewImport(find, () => resumed.push('saved-review'), file => loaded.push(file));
    return { entry, find, resumed, loaded };
}

test('Standalone review recovery stays outside home, games, study and native review', () => {
    const { entry, find, resumed } = reviewImportFixture();
    for (const mode of ['home', 'ai', 'match', 'saved', 'finished', 'archive', 'archive-review', 'study-review', 'quests', 'openings', 'analysis']) {
        entry.show(mode, true, false);
        assert.equal(find('review-tools').hidden, true, mode);
        assert.equal(find('reopen-review').hidden, true, mode);
        find('reopen-review').fire('click');
    }
    assert.deepEqual(resumed, []);
    entry.show('review', false, false);
    assert.equal(find('review-tools').hidden, false);
    assert.equal(find('reopen-review').hidden, true);
    entry.show('review', true, false);
    find('reopen-review').fire('click');
    assert.deepEqual(resumed, ['saved-review']);
    entry.show('review', true, true);
    assert.equal(find('review-tools').hidden, true);
    assert.equal(find('reopen-review').hidden, true);
    find('reopen-review').fire('click');
    assert.deepEqual(resumed, ['saved-review']);
});

test('Standalone import clears canceled and repeated selections without opening hidden tools', () => {
    const { entry, find, loaded } = reviewImportFixture();
    const input = find('archive');
    const file = { name: 'review.json' };
    entry.show('review', false, false);
    input.files = [];
    input.value = 'canceled';
    input.fire('change');
    assert.equal(input.value, '');
    assert.deepEqual(loaded, []);
    input.files = [file];
    input.value = file.name;
    input.fire('change');
    assert.equal(input.value, '');
    input.value = file.name;
    input.fire('change');
    assert.deepEqual(loaded, [file, file]);
    entry.show('home', true, false);
    input.value = file.name;
    input.fire('change');
    assert.equal(input.value, '');
    assert.deepEqual(loaded, [file, file]);
});

test('Game controls keep profile and new-game actions on the native home route', async () => {
    const [html, adapter, native] = await Promise.all([
        read('web/src/main/web/index.html'), read('web/src/main/web/game-review.mjs'),
        read('chess-core/src/main/java/ro/atf/chess/MatchControls.java'),
    ]);
    assert.doesNotMatch(html, /id="(?:open-profile|new-match|open-match)"/);
    assert.doesNotMatch(adapter, /find\('(?:open-profile|new-match|open-match)'\)/);
    assert.match(html, /id="open-ai"/);
    assert.match(html, /id="home-name"/);
    assert.match(html, /id="home-settings"/);
    assert.match(native, /enum Action \{ FLIP, HOME, SAVED_GAMES, ANALYSIS_BOARD, GAME_REVIEW, OFFER_DRAW, RESIGN \}/);
    assert.match(adapter, /HOME: 'open-review'/);
    assert.match(adapter, /reviewImport\.show\(mode, Boolean\(archive\), Boolean\(liveReviewOrigin\)\)/);
});

function mockWorker(list) {
    const events = new Map();
    const worker = {
        messages: [],
        addEventListener(type, listener) { events.set(type, listener); },
        postMessage(value) { this.messages.push(value); },
        terminate() { this.stopped = true; },
        answer(message, data) { events.get('message')({ data: { id: message.id, ...data } }); },
    };
    list.push(worker);
    return worker;
}

test('Inline analysis drops older frames and keeps hints on a board flip only', () => {
    const workers = [];
    const statuses = [];
    const session = createInlineAnalysisSession({
        createWorker: () => mockWorker(workers), publish: state => statuses.push(state?.state?.ply ?? null),
    });
    const frame = { reviewCheckpoint: '2|e2e4 e7e5', viewPly: 2, revision: 4, blackBelow: false };
    session.show(frame, 'ai-slot-one');
    assert.equal(workers.length, 0);
    const enabled = { ...frame, analysisEnabled: true };
    session.show(enabled, 'ai-slot-one');
    const first = workers[0];
    assert.equal(first.messages[0].type, 'match-analysis-open');
    session.show({ ...enabled, viewPly: 1, revision: 5 }, 'ai-slot-one');
    const current = workers[1];
    assert.equal(first.stopped, true);
    first.answer(first.messages[0], { analysis: { revision: 4, ply: 2, hint: { move: 'd2d4' } } });
    assert.equal(session.revealedMove(), null);
    current.answer(current.messages[0], { analysis: { revision: 5, ply: 1, hint: { count: 2, move: null } } });
    assert.equal(statuses.at(-1), 1);
    session.command('hint');
    assert.equal(current.messages[1].expectedRevision, 5);
    current.answer(current.messages[1], { analysis: { revision: 5, ply: 1, hint: { count: 2, move: 'e7e5' } } });
    session.show({ ...enabled, viewPly: 1, revision: 6, blackBelow: true }, 'ai-slot-one');
    assert.equal(workers.length, 2);
    assert.equal(session.revealedMove(), 'e7e5');
    session.show(null);
    current.answer(current.messages[1], { analysis: { revision: 5, ply: 1, hint: { move: 'c7c5' } } });
    assert.equal(current.stopped, true);
    assert.equal(statuses.at(-1), null);
    assert.equal(session.revealedMove(), null);
});

test('Inline analysis projects the score, lamp and playable theory for the displayed move', () => {
    function element() {
        const events = new Map();
        const attributes = new Map();
        return {
            children: [], dataset: {}, style: { setProperty(name, value) { this[name] = value; } }, textContent: '', hidden: true, open: false,
            width: 328.8, getBoundingClientRect() { return { width: this.width }; },
            addEventListener: (type, handler) => events.set(type, handler),
            setAttribute: (name, value) => attributes.set(name, String(value)),
            getAttribute: name => attributes.get(name) ?? null,
            removeAttribute: name => attributes.delete(name),
            replaceChildren(...children) { this.children = children; },
            showModal() { this.open = true; },
            close() { this.open = false; },
            focus() {},
            click() { assert.ok(!this.hidden && !this.disabled); events.get('click')(); },
            ownerDocument: { createElement: () => ({ ...element(), hidden: false }) },
        };
    }

    const controls = new Map();
    const find = id => {
        if (!controls.has(id)) controls.set(id, element());
        return controls.get(id);
    };
    const meter = find('match-inline-meter');
    meter.firstElementChild = element();
    const panel = find('match-inline-analysis');
    const lamp = find('match-inline-hint');
    const score = find('match-inline-score');
    const replay = find('match-inline-replay');
    const dialog = find('match-inline-preview');
    const next = find('match-inline-next');
    next.hidden = false;
    find('match-inline-close').hidden = false;
    const workers = [];
    const boards = [];
    const cues = [];
    let selectionClears = 0;
    const session = bindInlineMatchAnalysis({
        find, locale: 'ro', createWorker: () => mockWorker(workers),
        createBoard: container => {
            assert.equal(container, find('match-inline-preview-board'));
            return { render: board => boards.push(board) };
        },
        cue: (move, flipped) => cues.push([move, flipped]),
        clearSelection: () => { selectionClears++; },
    });
    const initial = { reviewCheckpoint: '3|WHITE|BASIC|1|', viewPly: 0, revision: 9, blackBelow: false, sideToMove: 'WHITE' };
    session.show(initial, 'ai-slot');
    assert.deepEqual([meter.hidden, panel.hidden, lamp.hidden, workers.length], [true, true, true, 0]);

    const enabled = { ...initial, analysisEnabled: true };
    session.show(enabled, 'ai-slot');
    assert.deepEqual([meter.hidden, panel.hidden, lamp.hidden, lamp.disabled], [false, false, false, true]);
    assert.equal(score.textContent, 'Analiză AI…');
    assert.equal(meter.getAttribute('aria-valuenow'), null);
    assert.equal(meter.getAttribute('aria-valuetext'), null);
    assert.equal(session.command('hint'), false);
    assert.equal(selectionClears, 0);

    const worker = workers[0];
    const frame = {
        revision: 9, ply: 0, evaluation: { ending: 'HORIZON', centipawns: 32, whiteShare: .58 },
        hint: { number: 0, count: 3, move: null },
    };
    const answer = analysis => worker.answer(worker.messages.at(-1), { outcome: 'APPLIED', analysis });
    answer(frame);
    assert.deepEqual([score.textContent, meter.getAttribute('aria-valuenow'), meter.firstElementChild.style.insetBlockEnd],
        ['+0,32 · Alb', '58', '0']);
    assert.equal(meter.getAttribute('aria-valuetext'), '+0,32 · Alb');
    assert.equal(lamp.getAttribute('aria-label'), 'Arată varianta AI');
    lamp.click();
    assert.equal(worker.messages.at(-1).action, 'hint');
    assert.equal(selectionClears, 1);
    lamp.click();
    assert.equal(worker.messages.length, 2);
    assert.equal(selectionClears, 1);
    const name = { en: 'Italian Game', ro: 'Partida italiană' };
    const shown = { ...frame, hint: { number: 1, count: 3, move: 'e2e4', family: name } };
    answer(shown);
    assert.equal(lamp.getAttribute('aria-pressed'), 'true');
    assert.match(find('match-inline-announcement').textContent, /Varianta 1 din 3\. e2 la e4\. Partida italiană/);
    assert.deepEqual(cues.at(-1), ['e2e4', false]);
    assert.equal(guidedMatchMove(session.revealedMove(), undefined, 'e4', ['d2d4', 'e2e4']), 'e2e4');

    session.show({ ...enabled, blackBelow: true }, 'ai-slot');
    assert.equal(workers.length, 1);
    assert.equal(meter.firstElementChild.style.insetBlockStart, '0');
    assert.deepEqual(cues.at(-1), ['e2e4', true]);
    replay.click();
    assert.equal(worker.messages.at(-1).action, 'preview');
    const line = { kind: 'THEORY', ply: 0, total: 6, branch: 0, branches: ['e2e4', 'd2d4'], name, eco: 'C50' };
    answer({ ...shown, preview: line });
    assert.equal(dialog.open, true);
    assert.equal(dialog.style['--replay-board-side'], '328.8px', 'capture the board, excluding its evaluation rail');
    find('match-board').width = 560;
    assert.match(find('match-inline-evidence').textContent, /Partida italiană · C50/);
    assert.equal(find('match-inline-previous').disabled, true);
    assert.equal(boards.at(-1).locked, true);
    assert.equal(boards.at(-1).blackBelow, true);
    next.click();
    assert.equal(worker.messages.at(-1).action, 'preview-seek');
    assert.equal(worker.messages.at(-1).index, 1);
    answer({ ...shown, preview: { ...line, ply: 1, lastMove: 'e2e4' } });
    assert.equal(dialog.style['--replay-board-side'], '328.8px', 'stepping does not enlarge the preview');
    assert.match(find('match-inline-position').textContent, /Pasul 1 din 6 · e2–e4/);
    find('match-inline-close').click();
    assert.equal(worker.messages.at(-1).action, 'preview-close');
    answer(shown);
    assert.equal(dialog.open, false);
    assert.equal(session.revealedMove(), 'e2e4');

    replay.click();
    answer({ ...shown, preview: line });
    assert.equal(dialog.style['--replay-board-side'], '560px', 'a new preview captures the current viewport');
    find('match-inline-close').click();
    answer(shown);

    session.show({ ...enabled, reviewCheckpoint: '3|WHITE|BASIC|1|d2d4', viewPly: 1, revision: 10 }, 'ai-slot');
    assert.equal(worker.stopped, true);
    assert.equal(lamp.disabled, true);
    assert.equal(session.revealedMove(), null);
    const nextWorker = workers[1];
    nextWorker.answer(nextWorker.messages[0], { outcome: 'APPLIED', analysis: { ...shown, revision: 10, ply: 0 } });
    assert.equal(score.textContent, 'Analiza AI este indisponibilă.');
    assert.equal(session.revealedMove(), null);
    session.show(null);
    assert.deepEqual([meter.hidden, panel.hidden, lamp.hidden, dialog.open], [true, true, true, false]);
});

test('A hinted destination applies only the exact legal variation and respects selection', () => {
    assert.equal(guidedMatchMove('a7a8n', undefined, 'a8', ['a7a8q', 'a7a8n']), 'a7a8n');
    assert.equal(guidedMatchMove('a7a8n', undefined, 'a8', ['a7a8q']), null);
    assert.equal(guidedMatchMove('a7a8n', 'a7', 'a8', ['a7a8n']), null);
    assert.equal(guidedMatchMove('a7a8n', undefined, 'a7', ['a7a8n']), null);
});

const translations = {
    'home-games-heading': 'menu_play', 'home-study-heading': 'menu_study', 'home-profile-heading': 'profile_title',
    'open-ai': 'new_match', 'open-saved': 'match_saved_games', 'open-finished': 'match_archive',
    'home-puzzles': 'menu_puzzles', 'home-openings': 'openings_title', 'home-analysis': 'manual_analysis',
    'home-settings': 'settings_title', 'profile-name-label': 'profile_name_hint', 'profile-language-label': 'settings_language_label',
    'save-profile': 'profile_save', 'profile-reset': 'profile_reset', defaultName: 'profile_default',
};

const groups = [
    ['home_games', 'home-games', ['new_match', 'resume_match', 'match_archive'], ['open-ai', 'open-saved', 'open-finished']],
    ['home_study', 'home-study', ['open_puzzles', 'home_totals', 'open_openings', 'open_analysis'],
        ['home-puzzles', 'home-study-totals', 'home-openings', 'home-analysis']],
    ['home_profile', 'home-profile', ['edit_player_name', 'open_settings'], ['home-name', 'home-settings']],
];

test('Web home preserves Android groups, action order and RO and EN labels', async () => {
    const html = await read('web/src/main/web/index.html');
    const layout = await read('android/app/src/main/res/layout/main_menu.xml');
    const nativeHeading = layout.match(/<TextView\b[^>]*style="@style\/MenuHeading"[^>]*>/)?.[0];
    const strings = await read('android/app/src/main/res/values/strings.xml');
    assert.equal(nativeHeading?.match(/android:text="@string\/([^"]+)"/)?.[1], 'app_name');
    const name = strings.match(/<string name="app_name" translatable="false">([^<]+)<\/string>/)?.[1];
    assert.ok(name, 'The Android home heading resolves to the app name');
    assert.equal(html.match(/<title>([^<]+)<\/title>/)?.[1], name);
    assert.equal(html.match(/<h1\b[^>]*\sid="title"[^>]*>([^<]+)<\/h1>/)?.[1], name);

    for (const [nativeId, webId, nativeActions, webActions] of groups) {
        const native = layout.split('android:id="@+id/' + nativeId + '"')[1].split('</LinearLayout>')[0];
        const web = html.split('id="' + webId + '"')[1].split('</div>')[0];
        assert.deepEqual([...native.matchAll(/android:id="@\+id\/([^"]+)"/g)].map(match => match[1]), nativeActions);
        assert.deepEqual([...web.matchAll(/id="([^"]+)"/g)].map(match => match[1]), webActions);
    }
    for (const [locale, folder] of [['ro', 'values-ro'], ['en', 'values']]) {
        const prefix = 'android/app/src/main/res/' + folder;
        const resources = await read(prefix + '/strings.xml') + await read(prefix + '/settings.xml');
        for (const [webId, resource] of Object.entries(translations)) {
            const value = resources.match(new RegExp('<string name="' + resource + '">([^<]+)'));
            assert.equal(homeLabels[locale][webId], value?.[1], webId + ' in ' + locale);
        }
    }
});

test('Only active saved games show the home action', () => {
    assert.equal(hasSavedHomeGames([]), false);
    assert.equal(hasSavedHomeGames([{ result: 'BLACK_WINS' }]), false);
    assert.equal(hasSavedHomeGames([{ result: 'IN_PROGRESS' }]), true);
});

test('AI color choices preserve Android order and RO and EN labels', async () => {
    const [html, adapter, arrays, english, romanian] = await Promise.all([
        read('web/src/main/web/index.html'),
        read('web/src/main/web/game-review.mjs'),
        read('android/app/src/main/res/values/match_arrays.xml'),
        read('android/app/src/main/res/values/strings.xml'),
        read('android/app/src/main/res/values-ro/strings.xml'),
    ]);
    const colorArray = arrays.match(/<string-array name="match_colors">([^]*?)<\/string-array>/)?.[1] ?? '';
    const nativeIds = [...colorArray.matchAll(/<item>@string\/(\w+)<\/item>/g)].map(match => match[1]);
    const colorSelect = html.match(/<select id="ai-side">([^]*?)<\/select>/)?.[1] ?? '';
    const labels = [...colorSelect.matchAll(/<option id="([^"]+)" value="([^"]+)">([^<]+)<\/option>/g)];
    const resourceFor = { WHITE: 'white', BLACK: 'black', RANDOM: 'match_random_color' };
    assert.deepEqual(labels.map(([, , value]) => resourceFor[value]), nativeIds);
    assert.deepEqual(nativeIds, ['white', 'black', 'match_random_color']);
    const nativeLabel = (resources, id) => resources.match(new RegExp('<string name="' + id + '">([^<]+)'))?.[1];
    for (const [index, [, id, , label]] of labels.entries()) {
        assert.equal(label, nativeLabel(english, nativeIds[index]), id);
        const localized = [...adapter.matchAll(new RegExp("'" + id + "': '([^']+)'", 'g'))].map(match => match[1]);
        assert.deepEqual(localized, [label, nativeLabel(romanian, nativeIds[index])], id);
    }
});

test('The match options expose native board actions in RO and EN', async () => {
    const [html, adapter, toolbar, english, romanian] = await Promise.all([
        read('web/src/main/web/index.html'), read('web/src/main/web/game-review.mjs'),
        read('android/app/src/main/res/layout/match_toolbar.xml'),
        read('android/app/src/main/res/values/strings.xml'), read('android/app/src/main/res/values-ro/strings.xml'),
    ]);
    assert.match(toolbar, /android:contentDescription="@string\/match_actions"/);
    const header = html.match(/<main>\s*<header[^>]*>([^]*?)<\/header>/)?.[1];
    assert.match(header, /id="return-menu"[^]*?id="title"[^]*?id="match-options-anchor"/);
    const anchor = header.slice(header.indexOf('<div id="match-options-anchor"'), header.indexOf('<div class="header-actions"'));
    assert.match(anchor, /id="match-options-button"[^>]+aria-controls="match-options" aria-expanded="false"/);
    const panel = anchor.match(/<div id="match-options"[^>]* role="group"[^>]* hidden>([^]*?)<\/div>/)?.[1];
    assert.deepEqual([...panel.matchAll(/<button id="([^"]+)"/g)].map(entry => entry[1]),
        ['flip-match', 'open-review', 'menu-saved', 'menu-analysis', 'menu-review', 'menu-draw', 'menu-resign']);
    for (const [id, key] of [['match-options-title', 'match_actions'], ['flip-match', 'flip'], ['open-review', 'main_menu'],
        ['menu-saved', 'match_saved_games'], ['menu-analysis', 'manual_analysis'], ['menu-review', 'match_game_review'],
        ['menu-draw', 'match_offer_draw'], ['menu-resign', 'match_resign'], ['ai-analysis-label', 'match_analysis_option'],
        ['resign-confirm', 'match_confirm_resign'], ['resign-message', 'match_resign_confirm']]) {
        const localized = [...adapter.matchAll(new RegExp("'" + id + "': '([^']+)'", 'g'))].map(match => match[1]);
        const nativeLabels = [english, romanian].map(resources => resources.match(new RegExp('<string name="' + key + '">([^<]+)'))?.[1]);
        assert.deepEqual(localized, nativeLabels, id);
    }
});

test('The automatic AI control has the native RO and EN labels', async () => {
    const [html, adapter, toolbar, en, ro, css] = await Promise.all([
        read('web/src/main/web/index.html'), read('web/src/main/web/game-review.mjs'),
        read('android/app/src/main/res/layout/match_toolbar.xml'),
        read('android/app/src/main/res/values/strings.xml'), read('android/app/src/main/res/values-ro/strings.xml'),
        read('web/src/main/web/game-review.css'),
    ]);
    assert.match(toolbar, /<CheckBox[^]*android:minHeight="48dp"[^]*android:text="@string\/match_automatic_ai"/);
    assert.match(html, /<label id="match-automatic-option"[^>]*hidden>\s*<input id="match-automatic" type="checkbox">/);
    assert.match(css, /\.match-automatic\s*\{[^}]*min-block-size:\s*var\(--play-action-size\);/);
    for (const [key, id] of [["'match-automatic-label'", 'match_automatic_ai'], ['manualAiTurn', 'match_manual_ai_turn']]) {
        const actual = [...adapter.matchAll(new RegExp(key + ": '([^']+)'", 'g'))].map(match => match[1]);
        const native = [en, ro].map(resources => resources.match(new RegExp('<string name="' + id + '">([^<]+)'))?.[1]);
        assert.deepEqual(actual, native, id);
    }
});

test('A new board revision replaces the scheduled AI reply exactly once', () => {
    const pending = new Map();
    const applied = [];
    let next = 0;
    const clock = {
        setTimeout(action, delay) { assert.equal(delay, 650); pending.set(++next, action); return next; },
        clearTimeout(id) { pending.delete(id); },
    };
    const schedule = createMatchBotTimer(clock, 650, turn => applied.push(turn.revision));
    const before = { revision: 11 };
    schedule(before);
    schedule(before);
    assert.equal(pending.size, 1);
    const staleCallback = pending.get(1);
    schedule(undefined);
    assert.equal(pending.size, 0);
    schedule({ revision: 12 });
    assert.deepEqual([...pending.keys()], [2]);
    staleCallback();
    assert.deepEqual(applied, []);
    const replacement = pending.get(2);
    pending.delete(2);
    replacement();
    replacement();
    assert.deepEqual(applied, [12]);
});

test('Disabling automation aborts a running calculation and discards its result', async () => {
    const jobs = new Map();
    const sent = [];
    const computations = [];
    let next = 0;
    const clock = {
        setTimeout(action) { jobs.set(++next, action); return next; },
        clearTimeout(id) { jobs.delete(id); },
    };
    const schedule = createMatchBotTimer(clock, 650, async (turn, signal) => {
        const result = await new Promise(resolve => computations.push({ resolve, signal }));
        if (!signal.aborted) sent.push([turn.revision, result]);
    });
    const current = { revision: 7 };
    schedule(current);
    const oldReply = jobs.get(1)();
    schedule(current);
    assert.equal(computations.length, 1);
    schedule(undefined);
    assert.equal(computations[0].signal.aborted, true);
    schedule({ revision: 9 });
    const nextReply = jobs.get(2)();
    computations[0].resolve('e7e5');
    computations[1].resolve('c7c5');
    await Promise.all([oldReply, nextReply]);
    assert.deepEqual(sent, [[9, 'c7c5']]);
});

test('Profile persistence respects the native validator and write failures', async () => {
    const entries = new Map();
    const storage = { getItem: key => entries.get(key), setItem: (key, value) => entries.set(key, value) };
    const normalize = async name => {
        if (name === '?') throw new TypeError('Native validator rejected the name');
        return name.trim().normalize();
    };
    writeProfile(storage, { name: 'Ion', language: 'ro' });
    assert.deepEqual(await saveHomeProfile(storage, { name: '  Ana  ' }, 'name', false, normalize), { name: 'Ana', language: 'ro' });
    await assert.rejects(saveHomeProfile(storage, { name: '?' }, 'name', false, normalize), /Native validator/);
    assert.deepEqual(readProfile(() => storage), { name: 'Ana', language: 'ro' });
    await assert.rejects(saveHomeProfile({ setItem() { throw Error('Full'); } }, { name: 'Ion' }, 'name', false, normalize), /Full/);
    assert.deepEqual(await saveHomeProfile(storage, { language: 'en' }, 'language', false, normalize), { name: 'Ana', language: 'en' });
    assert.deepEqual(await saveHomeProfile(storage, { name: '', language: 'ro' }, 'both', false, normalize), { name: '', language: 'ro' });
    assert.deepEqual(await saveHomeProfile(storage, { name: '?' }, 'name', true, normalize), { name: '', language: 'ro' });
    assert.deepEqual([...entries.keys()], [profileKey]);
    assert.equal(homeLocale('', storage), 'ro');
    assert.equal(homeLocale('?lang=en', storage), 'en');
    await assert.rejects(saveHomeProfile(storage, { language: 'fr' }, 'language', false, normalize), TypeError);
});

test('Board piece renderer reuses the same Staunton SVG on a move and clears it on capture', () => {
    class Element {
        children = [];
        attributes = new Map();
        dataset = {};
        constructor(kind, ownerDocument) { this.kind = kind; this.ownerDocument = ownerDocument; }
        setAttribute(attribute, value) { this.attributes.set(attribute, value); }
        removeAttribute(attribute) { if (attribute === 'data-shape') delete this.dataset.shape; }
        replaceChildren(...children) { this.children = children; }
        append(child) { this.children.push(child); }
    }
    const document = { createElementNS: (_, kind) => new Element(kind, document) };
    const art = { b: { body: 'M 0 0 L 10 10 Z', details: 'M 8 3 L 2 9' } };
    const piece = new Element('span', document);
    assert.equal(showReviewPiece(piece, 'b', art), true);
    const [svg] = piece.children;
    assert.equal(svg.attributes.get('viewBox'), '0 0 48 48');
    assert.deepEqual(svg.children.map(path => path.attributes.get('d')), [art.b.body, art.b.details]);
    assert.deepEqual(svg.children.map(path => path.attributes.get('stroke-width')), [1.35, 1.12]);
    assert.equal(showReviewPiece(piece, 'b', art), true);
    assert.equal(piece.children[0], svg);
    assert.equal(showReviewPiece(piece, ' ', art), false);
    assert.equal(piece.dataset.shape, undefined);
    assert.equal(piece.children.length, 0);
});

test('Home name and settings share the native dialog and stored profile', async () => {
    const html = await read('web/src/main/web/index.html');
    const controls = new Map();
    let focused;
    for (const [, id] of html.matchAll(/\bid="([^"]+)"/g)) {
        assert.equal(controls.has(id), false, 'duplicate HTML id: ' + id);
        const events = new Map();
        controls.set(id, {
            addEventListener: (type, handler) => events.set(type, handler),
            fire: type => events.get(type)({ preventDefault() {} }),
            focus: () => { focused = id; },
            select() {}, showModal() { this.open = true; }, close() { this.open = false; },
        });
    }
    const entries = new Map();
    const storage = { getItem: key => entries.get(key), setItem: (key, value) => entries.set(key, value) };
    writeProfile(storage, { name: 'Ion', language: 'ro' });
    const changes = [];
    const find = controls.get.bind(controls);
    const home = createHomeMenu(find, 'ro', storage, async name => name.trim(), (profile, fields) => changes.push({ profile, fields }));
    assert.equal(home.name, 'Ion');
    find('home-name').fire('click');
    assert.equal(focused, 'profile-name');
    assert.equal(find('profile-language-field').hidden, true);
    find('profile-name').value = ' Ana ';
    find('profile-form').fire('submit');
    await setImmediate();
    assert.equal(find('home-name').textContent, 'Nume · Ana');
    find('home-settings').fire('click');
    assert.equal(focused, 'profile-language');
    assert.equal(find('profile-name-field').hidden, true);
    find('profile-language').value = 'en';
    find('profile-form').fire('submit');
    await setImmediate();
    find('home-name').fire('click');
    assert.equal(find('profile-name-field').hidden, false);
    assert.equal(find('profile-language-field').hidden, true);
    assert.equal(find('profile-name').value, 'Ana');
    assert.deepEqual(changes, [
        { profile: { name: 'Ana', language: 'ro' }, fields: 'name' },
        { profile: { name: 'Ana', language: 'en' }, fields: 'language' },
    ]);
    assert.deepEqual([...entries.keys()], [profileKey]);
});

test('Language reload retains Home with a saved review and preserves the AI profile route', async () => {
    const entries = new Map([
        ['chesslab.game-review.v1', 'saved review'], ['chesslab.match-ledger.v1', 'saved games'],
    ]);
    const storage = { getItem: key => entries.get(key), setItem: (key, value) => entries.set(key, value) };
    const browser = (href) => {
        const location = { href, reloads: 0, reload() { this.reloads++; } };
        const history = {
            state: { other: 'kept' },
            replaceState(state, _, url) {
                this.state = structuredClone(state);
                if (url) location.href = url;
            },
        };
        return { location, history };
    };

    const fromHome = browser('https://example.test/chess/?lang=en&example=1');
    const settings = await saveHomeProfile(storage, { language: 'ro' }, 'language', false);
    assert.equal(reloadProfileLanguage(fromHome, 'en', settings.language, 'language'), true);
    assert.equal(fromHome.location.reloads, 1);
    assert.equal(fromHome.location.href, 'https://example.test/chess/?example=1');
    assert.equal(homeLocale(new URL(fromHome.location.href).search, storage), 'ro');
    assert.deepEqual(routeForHash(new URL(fromHome.location.href).hash), { mode: 'home' });
    assert.equal(entries.get('chesslab.game-review.v1'), 'saved review');
    assert.equal(entries.get('chesslab.match-ledger.v1'), 'saved games');
    assert.deepEqual(fromHome.history.state, { other: 'kept' });
    assert.equal(reloadProfileLanguage(fromHome, 'ro', 'ro', 'language'), false);
    assert.equal(fromHome.location.reloads, 1);

    const inAi = browser('https://example.test/chess/?lang=ro&example=1#ai');
    assert.equal(reloadProfileLanguage(inAi, 'ro', 'en', 'both'), true);
    assert.equal(inAi.location.reloads, 1);
    assert.equal(inAi.location.href, 'https://example.test/chess/?example=1#ai');
    assert.deepEqual(routeForHash(new URL(inAi.location.href).hash), { mode: 'ai' });
    assert.deepEqual(inAi.history.state, { other: 'kept' });
});
