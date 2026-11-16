import assert from 'node:assert/strict';
import test from 'node:test';
import { createOpeningFlow, openingComparisonJump, openingControls, pickOpeningTap } from '../src/main/web/study-openings.mjs';

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
