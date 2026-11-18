import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { createOpeningFlow, createOpeningStudy, openingComparisonJump, openingControls, pickOpeningTap } from '../src/main/web/study-openings.mjs';

const tick = () => new Promise(resolve => setImmediate(resolve));

function fixture(responses, stored = null) {
  const calls = [];
  const checkpoints = [];
  const rendered = [];
  const errors = [];
  const timers = new Map();
  let timer = 0;
  const flow = createOpeningFlow({
    locale: 'ro',
    async call(action, fields) {
      calls.push({ action, ...fields });
      const answer = responses[action];
      if (answer instanceof Error) throw answer;
      if (typeof answer === 'function') return answer(fields);
      return answer;
    },
    read: () => stored,
    save: saved => { checkpoints.push(saved); stored = saved; },
    change: (_, state) => rendered.push(state),
    fail: error => errors.push(error),
    wait: (task, delay) => { timers.set(++timer, { task, delay }); return timer; },
    cancel: id => timers.delete(id),
  });
  return { flow, calls, checkpoints, rendered, errors, timers };
}

test('A learner move schedules exactly one native book reply with its saved position', async () => {
  const pending = { saved: 'lesson:white:move', canAutoReply: true };
  const done = { saved: 'lesson:white:reply', canAutoReply: false };
  const { flow, calls, checkpoints, timers, rendered } = fixture({
    CATALOG: { entries: [{ id: 'italian' }] },
    START: { saved: 'lesson:white:start', canAutoReply: false },
    PLAY: pending, REPLY: done,
  });
  await flow.open();
  await flow.dispatch('START', 'italian', 0);
  await flow.dispatch('PLAY', 'e2e4');
  assert.equal(timers.size, 1);
  const [[id, reply]] = timers;
  assert.equal(reply.delay, 450);
  timers.delete(id);
  reply.task();
  await tick();
  assert.deepEqual(calls.at(-1), { action: 'REPLY', value: '', index: 0, saved: pending.saved, option: 'ro' });
  assert.deepEqual(checkpoints, ['lesson:white:start', pending.saved, done.saved]);
  assert.equal(rendered.at(-1), done);
  assert.equal(timers.size, 0);
});

test('History review and route changes cancel delayed replies without moving the visible board', async () => {
  let establish;
  const { flow, rendered, timers, calls, checkpoints } = fixture({
    CATALOG: { entries: [] },
    RESTORE: { saved: 'black:pending', canAutoReply: true },
    SEEK: { saved: 'black:history', canAutoReply: false },
    FORWARD: () => new Promise(resolve => { establish = resolve; }),
  }, 'black:pending');
  await flow.open();
  flow.visibility(false);
  assert.equal(timers.size, 0);
  flow.visibility(true);
  assert.equal(timers.size, 1);
  await flow.dispatch('SEEK', '', 0);
  assert.equal(timers.size, 0);
  assert.equal(calls.at(-1).saved, 'black:pending');
  const late = flow.dispatch('FORWARD');
  flow.close();
  establish({ saved: 'must-not-overwrite', canAutoReply: true });
  await late;
  assert.equal(rendered.at(-1).saved, 'black:history');
  assert.deepEqual(checkpoints, ['black:history']);
  assert.equal(timers.size, 0);
});

test('An invalid opening checkpoint returns to the catalog without replaying a move', async () => {
  const { flow, checkpoints, rendered, errors, calls } = fixture({
    CATALOG: { entries: [] }, RESTORE: new Error('Invalid checkpoint'),
  }, 'corrupt');
  await flow.open();
  assert.deepEqual(checkpoints, ['']);
  assert.deepEqual(rendered, [null]);
  assert.deepEqual(errors, []);
  assert.deepEqual(calls.map(item => item.action), ['CATALOG', 'RESTORE']);
});

test('Only the taught move can be played by clicking a revealed destination directly', () => {
  const view = { canPlay: true, nextStep: { move: 'e2e4' }, hint: 'PIECE', legal: ['e2e4', 'e2e3', 'g1f3'] };
  assert.deepEqual(pickOpeningTap(view, null, 'f3', 'g1f3'), { selected: null, moves: [] });
  assert.deepEqual(pickOpeningTap(view, null, 'g1'), { selected: 'g1', moves: [] });
  assert.deepEqual(pickOpeningTap(view, 'g1', 'f3'), { selected: 'g1', moves: ['g1f3'] });
  assert.deepEqual(pickOpeningTap(view, null, 'e4', 'e2e4'), { selected: null, moves: ['e2e4'] });
  assert.deepEqual(pickOpeningTap({ ...view, hint: 'SOLUTION' }, null, 'e4'), { selected: null, moves: ['e2e4'] });
  assert.deepEqual(pickOpeningTap({ ...view, canPlay: false }, null, 'e4', 'e2e4'), { selected: null, moves: [] });
});

test('Lesson controls follow native boundaries, pause, review and completion', () => {
  const start = { ply: 0, practicePly: 0, total: 10, phase: 'LEARNER', canPlay: true };
  assert.deepEqual(openingControls(start), { previous: false, next: true, history: false, action: 'THEORY', actionEnabled: true });
  const replying = { ...start, ply: 1, practicePly: 1, phase: 'REPLY', canPlay: false };
  assert.deepEqual(openingControls(replying), { previous: true, next: true, history: true, action: 'THEORY', actionEnabled: false });
  assert.equal(openingControls({ ...replying, replyPaused: true }).action, 'RESUME');
  assert.equal(openingControls({ ...replying, replyPaused: true, reviewing: true }).action, 'REPLAY');
  const complete = { ...start, ply: 10, practicePly: 10, phase: 'COMPLETE', canPlay: false };
  assert.deepEqual(openingControls(complete), { previous: true, next: false, history: true, action: 'RESTART', actionEnabled: true });
});

test('A decisive comparison reaches the exact verified branch and ply', () => {
  const variants = [
    { move: 'e2e4', proof: { addedDefense: false }, frames: [{ lastMove: null }, { lastMove: 'e2e4' }, { lastMove: 'e7e5' }] },
    { move: 'd2d4', proof: { addedDefense: false }, frames: [{ lastMove: null }, { lastMove: 'd2d4' }, { lastMove: 'd7d5' }] },
  ];
  const proof = { branch: 'd2d4', ply: 2, move: 'd7d5' };
  assert.deepEqual(openingComparisonJump(variants, proof), { branch: 1, ply: 2 });
  assert.equal(openingComparisonJump(variants, { ...proof, move: 'e7e5' }), null);
  assert.equal(openingComparisonJump(variants, { ...proof, ply: 0 }), null);
  assert.equal(openingComparisonJump(variants, { ...proof, ply: 3 }), null);
  assert.equal(openingComparisonJump(variants, { ...proof, branch: 'g1f3' }), null);
  assert.equal(openingComparisonJump([variants[0], { ...variants[1], proof: { addedDefense: true } }], proof), null);
});

const longPlan = 'Develop the knight with tempo and retain control of the centre.\n'.repeat(7).trim();

async function mountLesson(locale) {
  const document = { addEventListener() {}, removeEventListener() {} };
  class Element {
    children = [];
    attributes = new Map();
    dataset = {};
    events = new Map();
    classList = { add() {}, toggle() {} };
    style = { setProperty() {} };
    isConnected = true;
    open = false;
    #text = '';
    textWrites = 0;
    constructor(tag) { this.tag = tag; }
    get textContent() { return this.#text; }
    set textContent(value) { this.#text = value; this.textWrites++; }
    append(...children) { this.children.push(...children); }
    replaceChildren(...children) { this.children = children; }
    setAttribute(name, value) { this.attributes.set(name, value); }
    toggleAttribute(name, flag) { if (flag) this.attributes.set(name, ''); else this.attributes.delete(name); }
    addEventListener(name, callback) { this.events.set(name, callback); }
    click() { this.events.get('click')(); }
    focus() { document.activeElement = this; }
    showModal() { this.open = true; this.children.at(-1).focus(); }
    close() { this.open = false; this.events.get('close')(); }
  }
  document.createElement = tag => new Element(tag);
  document.body = new Element('body');
  document.body.focus();
  const root = new Element('section');
  root.ownerDocument = document;
  function find(className, parent = root) {
    if (parent.className === className) return parent;
    for (const child of parent.children) {
      const found = find(className, child);
      if (found) return found;
    }
  }
  const lesson = { id: 'italian', title: 'Italian Game', ply: 0, practicePly: 0, total: 4,
    phase: 'LEARNER', canPlay: true, learner: 'WHITE', canAutoReply: false, sideToMove: 'WHITE',
    fen: '8/8/8/8/8/8/4P3/8', plan: longPlan, hint: 'NONE', legal: ['e2e3', 'e2e4'], nextStep: { move: 'e2e4', idea: 'Control d5.' } };
  let tap;
  let hint = 'NONE';
  const frames = [];
  const study = createOpeningStudy({
    root, locale, home() {},
    board: (_, onTap) => { tap = onTap; return { render: frame => frames.push(frame) }; },
    async call(action) {
      if (action === 'CATALOG') return { entries: [{ ...lesson, eco: 'C50', variation: 'Giuoco Piano', source: '#' }] };
      if (action === 'START') return lesson;
      if (action === 'THEORY') {
        hint = hint === 'NONE' ? 'PIECE' : 'SOLUTION';
        return { ...lesson, hint, hintPiece: 'PAWN' };
      }
      if (action === 'AI_TOGGLE') return { ...lesson, aiEnabled: true };
      if (action === 'ANALYZE') return { evaluation: { whiteCentipawns: 22, whiteShare: .55 }, variants: [] };
      assert.fail('Unexpected worker action ' + action);
    },
  });
  await study.open();
  find('opening-card').click();
  find('opening-dialog-actions').children[0].click();
  await tick();
  return { find, document, tap, frames };
}

for (const [locale, title, hint] of [
  ['en', 'Lesson explanation', 'Try moving the pawn.'],
  ['ro', 'Explicația lecției', 'Încearcă să muți piesa pion.'],
]) {
  test(`${locale.toUpperCase()} long caption opens in full and returns focus without duplicate announcements`, async () => {
    const { find, document, tap } = await mountLesson(locale);
    const caption = find('opening-message');
    const live = find('opening-message-announcement');
    assert.equal(caption.tag, 'button');
    assert.equal(caption.attributes.get('aria-label'), title);
    assert.equal(caption.attributes.get('aria-haspopup'), 'dialog');
    assert.equal(live.attributes.get('role'), 'status');
    assert.equal(live.textContent, longPlan);
    assert.equal(live.textWrites, 1);

    caption.focus();
    caption.click();
    const dialog = find('opening-dialog');
    assert.ok(dialog.open);
    assert.equal(dialog.children[0].attributes.has('data-chess-header'), true);
    assert.equal(dialog.children[0].children[1].textContent, title);
    assert.equal(find('opening-message-full').textContent, longPlan);
    assert.equal(dialog.children[0].children[0].attributes.get('aria-label'), locale === 'ro' ? 'Închide' : 'Close');
    dialog.children[0].children[0].click();
    assert.equal(document.activeElement, caption);
    assert.equal(live.textWrites, 1);

    tap('e2');
    assert.equal(live.textWrites, 1);
    find('opening-actions').children[0].click();
    await tick();
    assert.equal(live.textContent, hint);
    assert.equal(live.textWrites, 2);
  });
}

test('The board and leading evaluation meter share one row before and after enabling analysis', async () => {
  const { find, frames } = await mountLesson('en');
  const row = find('opening-board-row');
  const [meter, board] = row.children;
  const controls = find('opening-actions');
  const [guidance, aiToggle, bulb] = controls.children;
  assert.equal(meter.className, 'opening-evaluation-bar');
  assert.equal(board.className, 'board');
  assert.ok(meter.attributes.has('data-withheld'));
  assert.ok(bulb.attributes.has('data-withheld'));
  assert.equal(guidance.textContent, 'Lesson hint');
  aiToggle.click();
  await tick();
  assert.deepEqual(row.children, [meter, board]);
  assert.equal(meter.attributes.has('data-withheld'), false);
  assert.ok(meter.attributes.has('data-measured'));
  assert.equal(bulb.attributes.has('data-withheld'), false);
  assert.equal(aiToggle.attributes.get('aria-pressed'), 'true');
  guidance.click();
  await tick();
  assert.deepEqual(frames.at(-1).targets, []);
  assert.deepEqual(frames.at(-1).marks, ['e2']);
  guidance.click();
  await tick();
  assert.equal(frames.at(-1).selected, 'e2');
  assert.deepEqual(frames.at(-1).targets, ['e3', 'e4']);
  assert.deepEqual(frames.at(-1).marks, ['e2', 'e4']);
  assert.deepEqual(controls.children, [guidance, aiToggle, bulb]);
  assert.equal(guidance.textContent, 'Hide hint');
  const css = readFileSync(new URL('../src/main/web/study-openings.css', import.meta.url), 'utf8');
  assert.match(css, /\.opening-workspace \.opening-actions > button\s*\{[^}]*min-block-size: 48px[^}]*border-radius: 8px/);
  assert.match(css, /\.opening-message-text\s*\{[^}]*block-size: 3lh[^}]*overflow: hidden/);
  assert.match(css, /\.opening-board-row \.square\[data-study-hint\]::before\s*\{[^}]*border-radius: 13%[^}]*#57e0b4[^}]*pointer-events: none/);
  assert.match(css, /@media \(orientation: landscape\) and \(min-width: 704px\)[\s\S]*repeat\(2, minmax\(0, 1fr\)\)/);
  assert.match(css, /--opening-board-side:\s*max\(0px, min\(var\(--play-board-limit\)/);
});
