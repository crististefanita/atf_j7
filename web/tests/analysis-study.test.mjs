import assert from 'node:assert/strict';
import { test } from 'node:test';
import { analysisHintDescription, analysisScore, analysisStudySession } from '../src/main/web/study-analysis.mjs';

const flush = () => new Promise(resolve => setImmediate(resolve));
const frame = (revision, ply, extra = {}) => ({
    revision, ply, total: ply, result: 'IN_PROGRESS', analysis: true, evaluation: null, saved: 'line:' + ply, ...extra,
});

test('Mounted analysis restores its bookmark and requests one evaluation for each position', async () => {
    const observed = [];
    const renders = [];
    const local = new Map([['chesslab.analysis-study.v1', 'earlier-study']]);
    const storage = () => ({ getItem: key => local.get(key), setItem: (key, value) => local.set(key, value) });
    const session = analysisStudySession({
        storage,
        failed: () => assert.fail('The analysis request should succeed'),
        render: value => renders.push(value),
        call: async (action, input) => {
            observed.push([action, input]);
            if (action === 'resume' || action === 'flip') return frame(2, 1);
            if (action === 'tap') return frame(3, 2);
            return frame(input.index, input.index - 1, { evaluation: { centipawns: 20 } });
        },
    });
    await session.open();
    await flush();
    await session.request('flip');
    await session.request('tap', { value: 'e5' });
    await flush();
    assert.deepEqual(observed, [['resume', { saved: 'earlier-study' }], ['evaluate', { index: 2 }],
        ['flip', {}], ['tap', { value: 'e5' }], ['evaluate', { index: 3 }]]);
    assert.deepEqual(renders.map(state => state.ply), [1, 1, 1, 2, 2]);
    assert.equal(local.get('chesslab.analysis-study.v1'), 'line:2');
});

test('Late evaluation after a seek or route exit cannot replace the chosen board', async () => {
    const renders = [];
    const pending = [];
    const session = analysisStudySession({
        storage: () => undefined,
        failed: () => assert.fail('A reply without an active route should not surface an error'),
        render: value => renders.push(value.ply),
        call: async (action, input) => {
            if (action === 'open') return frame(1, input.index);
            if (action === 'seek') return frame(2, input.index, { analysis: false });
            return new Promise(resolve => pending.push(resolve));
        },
    });
    await session.open({ checkpoint: 'fixture', ply: 1, blackBelow: true });
    await session.request('seek', { index: 0 });
    pending[0](frame(1, 1, { evaluation: { centipawns: 15 } }));
    await flush();
    session.close();
    await session.open({ checkpoint: 'other', ply: 2 });
    session.close();
    pending[1](frame(1, 2, { evaluation: { centipawns: 15 } }));
    await flush();
    assert.deepEqual(renders, [1, 0, 2]);
});

test('Analysis retains a playable session when storage is blocked', async () => {
    const session = analysisStudySession({
        storage: () => { throw new Error('Storage disabled'); },
        failed: () => assert.fail('Unavailable storage must not disable the board'),
        render: () => {},
        call: async (action, input) => {
            assert.deepEqual([action, input], ['resume', { saved: '' }]);
            return frame(1, 0, { analysis: false });
        },
    });
    assert.equal((await session.open()).ply, 0);
});

test('Reported analysis score uses the same side, mate and decimal policy as Android', () => {
    assert.equal(analysisScore({ ending: 'HORIZON', centipawns: 42 }, 'ro'), '+0,42 · Alb');
    assert.equal(analysisScore({ ending: 'HORIZON', centipawns: -42 }, 'en'), '−0.42 · Black');
    assert.equal(analysisScore({ ending: 'HORIZON', centipawns: 0 }, 'en'), '0.00 · Balanced');
    assert.equal(analysisScore({ ending: 'CHECKMATE', mate: -2 }, 'ro'), 'Negru · Mat în 2');
    assert.equal(analysisScore({ ending: 'FINISHED' }, 'en'), 'AI analysis · Completed game');
});

test('A revealed analysis hint names its moves and opening for screen readers', () => {
    const hint = { move: 'e2e4', number: 1, count: 3, family: { ro: 'Jocul deschis', en: 'Open game' } };
    assert.equal(analysisHintDescription(hint, 'ro'), 'Varianta 1 din 3. e2 la e4. Jocul deschis.');
    assert.equal(analysisHintDescription({ move: 'a7a8n', number: 1, count: 1 }, 'en'), 'Variation 1 of 1. a7 to a8 Knight.');
    assert.equal(analysisHintDescription({ number: 0 }, 'en'), '');
});
