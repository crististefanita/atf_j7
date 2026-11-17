import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import test from 'node:test';

const site = process.env.J7_STUDY_WEB_SITE;

test('Material progress from the published Web package retains solved boards and the pending exercise', { skip: !site }, async () => {
    const { studyCommand } = await import(pathToFileURL(resolve(site, 'chess-engine.mjs')));
    const old = JSON.parse(await readFile(new URL('./material-progress-previous.json', import.meta.url), 'utf8'));
    let saved = old.saved;
    const run = (action, value = '', hint = '') => {
        const response = JSON.parse(studyCommand('quests', action, value, 0, saved, hint));
        saved = response.saved ?? saved;
        return response.data ?? response;
    };
    assert.deepEqual(run('summary').overall, old.totals);
    assert.equal(run('menu').resume, 'training');
    let position = run('open', 'training');
    assert.deepEqual([position.id, position.ply, position.board, position.solved], [old.training.id, old.training.ply, old.training.board, false]);
    for (const [chapter, previous] of [['tactics', old.tactic], ['intermezzo', old.inter]]) {
        position = run('open', chapter);
        assert.deepEqual([position.id, position.board, position.solved, position.earned], [previous.id, previous.board, true, 0]);
        const before = saved;
        const replay = run('explain');
        assert.equal(replay.type, 'replay');
        assert.equal(replay.initial, replay.frames.length - 1);
        assert.equal(replay.frames.at(-1).board, previous.board);
        assert.equal(saved, before, 'reading an explanation leaves the persisted position and points alone');
    }
    assert.equal(run('open', 'training').board, old.training.board);
    const piece = run('hint');
    const hint = run('hint', '', piece.hint);
    position = run('play', hint.suggestion, hint.hint);
    assert.equal(position.solved, true);
    assert.equal(run('play', hint.suggestion).earned, 0);
    assert.deepEqual(run('summary').overall, { ...old.totals, solved: old.totals.solved + 1, points: old.totals.points + 10 });
});

test('Built Web engine accepts APK puzzle stages, restores a pending defense and awards a solution once', { skip: !site }, async () => {
    const { studyCommand } = await import(pathToFileURL(resolve(site, 'chess-engine.mjs')));
    let saved = '';
    let option = '';
    const run = (action, value = '', index = 0) => {
        const response = JSON.parse(studyCommand('quests', action, value, index, saved, option));
        if (response.saved) saved = response.saved;
        const data = response.data ?? response;
        if (data.type === 'puzzle') option = data.hint;
        return data;
    };
    const menu = run('menu');
    assert.deepEqual(menu.chapters.map(chapter => chapter.key), ['mate-one', 'mate-two', 'mate-three', 'tactics', 'training', 'intermezzo']);
    assert.ok(menu.chapters.every(chapter => chapter.totals.total > 0));
    assert.equal(menu.overall.points, 0);
    let puzzle = run('open', 'mate-two');
    const id = puzzle.id;
    assert.equal(puzzle.count, 300);
    assert.equal(puzzle.board.length, 64);
    assert.equal(puzzle.next, false);
    puzzle = run('hint');
    assert.equal(puzzle.hint, 'PIECE');
    puzzle = run('hint');
    assert.equal(puzzle.hint, 'SOLUTION');
    assert.ok(puzzle.legalMoves.includes(puzzle.suggestion));
    puzzle = run('play', puzzle.suggestion);
    assert.equal(puzzle.pending, true);
    assert.deepEqual(puzzle.legalMoves, []);
    assert.equal(JSON.parse(studyCommand('quests', 'menu', '', 0, saved, '')).resume, 'mate-two');
    puzzle = run('view');
    assert.equal(puzzle.id, id);
    assert.equal(puzzle.ply, 1);
    assert.equal(run('defense', 'other-puzzle', 1).pending, true);
    puzzle = run('defense', id, 1);
    assert.equal(puzzle.ply, 2);
    assert.equal(puzzle.pending, false);
    run('hint');
    const last = run('hint').suggestion;
    puzzle = run('play', last);
    assert.equal(puzzle.solved, true);
    assert.equal(puzzle.earned, 10);
    assert.equal(puzzle.totals.solved, 1);
    assert.equal(puzzle.streak, 0);
    assert.equal(run('play', last).earned, 0);
    const replay = run('explain');
    assert.equal(replay.type, 'replay');
    assert.equal(replay.frames.length, 4);
    assert.equal(replay.frames.at(-1).danger.kind, 'MATE');
    puzzle = run('next');
    assert.notEqual(puzzle.id, id);
    assert.equal(run('previous').id, id);
    assert.equal(run('menu').overall.points, 10);
});

test('Built Web engine opens all material chapters with the authored replay and no solved credit', { skip: !site }, async () => {
    const { studyCommand } = await import(pathToFileURL(resolve(site, 'chess-engine.mjs')));
    let saved = '';
    const run = (action, value = '') => {
        const response = JSON.parse(studyCommand('quests', action, value, 0, saved, ''));
        if (response.saved) saved = response.saved;
        return response.data ?? response;
    };
    for (const chapter of ['tactics', 'training', 'intermezzo']) {
        const puzzle = run('open', chapter);
        assert.equal(puzzle.objective, 'MATERIAL');
        assert.ok(puzzle.legalMoves.length);
        const replay = run('explain', 'replay');
        assert.equal(replay.type, 'replay');
        assert.ok(replay.frames.length >= 2);
        assert.ok(puzzle.legalMoves.includes(replay.frames[1].lastMove));
        assert.ok(replay.frames.every(frame => frame.board.length === 64));
    }
    assert.equal(run('menu').overall.solved, 0);
    assert.equal(run('leave').resume, null);
});

test('Built Web engine solves one real puzzle in each of the six native chapters', { skip: !site }, async () => {
    const { studyCommand } = await import(pathToFileURL(resolve(site, 'chess-engine.mjs')));
    let saved = '';
    let hint = '';
    const run = (action, value = '', index = 0) => {
        const response = JSON.parse(studyCommand('quests', action, value, index, saved, hint));
        if (response.saved) saved = response.saved;
        const data = response.data ?? response;
        if (data.type === 'puzzle') hint = data.hint;
        return data;
    };
    const list = run('menu');
    for (const chapter of list.chapters) {
        let puzzle = run('open', chapter.key);
        const startingId = puzzle.id;
        const moves = puzzle.playerMoves;
        for (let stage = 0; stage < moves && !puzzle.solved; stage++) {
            assert.equal(run('hint').hint, 'PIECE');
            const solution = run('hint');
            assert.equal(solution.stage, stage);
            assert.ok(solution.legalMoves.includes(solution.suggestion), chapter.key);
            puzzle = run('play', solution.suggestion);
            if (puzzle.pending) puzzle = run('defense', puzzle.id, puzzle.ply);
        }
        assert.equal(puzzle.id, startingId);
        assert.equal(puzzle.solved, true, chapter.key);
        assert.equal(puzzle.earned, 10, chapter.key);
        assert.equal(puzzle.next, true, chapter.key);
    }
    const result = run('summary');
    assert.equal(result.overall.solved, 6);
    assert.equal(result.overall.points, 60);
    assert.deepEqual(result.chapters.map(chapter => [chapter.totals.solved, chapter.totals.points]), Array.from({ length: 6 }, () => [1, 10]));
});

test('Mate chapters restore their own cursor and solved credit from one saved snapshot', { skip: !site }, async () => {
    const { studyCommand } = await import(pathToFileURL(resolve(site, 'chess-engine.mjs')));
    let saved = '';
    const call = (action, value = '', index = 0, hint = '') => {
        const response = JSON.parse(studyCommand('quests', action, value, index, saved, hint));
        saved = response.saved ?? saved;
        return response.data ?? response;
    };
    const chapters = ['mate-one', 'mate-two', 'mate-three'];
    const first = chapters.map(chapter => call('open', chapter));
    assert.deepEqual(first.map(puzzle => puzzle.playerMoves), [1, 2, 3]);
    assert.ok(first.every(puzzle => puzzle.objective === 'MATE' && puzzle.next === false));
    for (const initial of first) {
        let puzzle = call('open', initial.chapter);
        for (let stage = 0; stage < initial.playerMoves; stage++) {
            assert.equal(puzzle.stage, stage);
            const piece = call('hint');
            const full = call('hint', '', 0, piece.hint);
            assert.equal(full.hint, 'SOLUTION');
            puzzle = call('play', full.suggestion, 0, full.hint);
            if (puzzle.pending) {
                const pending = puzzle;
                call('open', first.find(entry => entry.chapter !== initial.chapter).chapter);
                puzzle = call('open', initial.chapter);
                assert.deepEqual([puzzle.id, puzzle.ply, puzzle.board, puzzle.pending], [pending.id, pending.ply, pending.board, true]);
                puzzle = call('defense', pending.id, pending.ply);
                assert.equal(call('defense', pending.id, pending.ply).ply, puzzle.ply);
            }
        }
        const reloaded = call('view');
        assert.deepEqual([reloaded.id, reloaded.board, reloaded.solved, reloaded.earned], [initial.id, puzzle.board, true, 0]);
        assert.notEqual(call('next').id, initial.id);
    }
    assert.deepEqual(call('menu').chapters.slice(0, 3).map(({ totals }) => [totals.solved, totals.points]), [[1, 10], [1, 10], [1, 10]]);
    for (const initial of first) assert.notEqual(call('open', initial.chapter).id, initial.id);
});
