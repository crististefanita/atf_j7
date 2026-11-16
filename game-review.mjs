const storageKey = 'chesslab.game-review.v1';
const matchKeys = { match: 'chesslab.active-match.v1', ai: 'chesslab.ai-match.v1' };
const routeHashes = { match: '#game', ai: '#ai' };
const botDelayMillis = 650;
const locale = new URLSearchParams(location.search).get('lang') === 'ro' ? 'ro' : 'en';
const words = {
    en: {
        title: 'Game Review', 'archive-label': 'Open a review',
        'open-match': 'Local game', 'open-review': 'Game Review', 'new-match': 'New game',
        'open-ai': 'Play against AI', 'side-label': 'Your color', 'level-label': 'AI level',
        'white-choice': 'White', 'black-choice': 'Black', 'start-ai': 'Start game', 'cancel-setup': 'Cancel',
        'retry-bot': 'Retry AI move', botTurn: 'AI to move…', botFailure: 'The AI move could not be completed. You can retry from this position.',
        aiProfile: (level, side) => `AI · ${level} · You play ${side.toLowerCase()}`,
        analyze: 'Analyze game', 'cancel-analysis': 'Cancel analysis', 'progress-label': 'Analyzing game',
        'promote-label': 'Promote to', promotion: 'Choose a piece',
        matchFailure: 'Could not open or change the active game.', analysisFailure: 'Could not analyze the recorded moves.',
        matchSaveFailed: 'The browser could not save this move. The last saved position is still available.',
        WHITE_WINS: 'White wins', BLACK_WINS: 'Black wins', DRAW: 'Draw',
        empty: 'Open a Chess Lab review archive to replay its moves and compare alternatives.',
        why: 'Why?', 'why-title': 'Why?', played: 'Played line', recommended: 'Recommended line',
        'history-label': 'Move history', 'seek-label': 'Select a move by position',
        movement: 'Review navigation', previous: 'Previous move', next: 'Next move',
        'close-why': 'Close explanation', board: 'Chess board', WHITE: 'White to move', BLACK: 'Black to move',
        badArchive: 'Could not open this review archive.', failedAction: 'Could not update the review.',
        saveFailed: 'The browser could not save this change. The last saved review is still available.',
        changed: 'Another tab changed this review. Reload to continue.',
        engineFailed: 'The review engine is unavailable. Reload to try again.',
        evaluation: (played, best, depth) => `Played ${played} · Recommended ${best} · Depth ${depth}`,
        ALLOWS_MATE: (piece, square) => `The reply mates with the ${piece} on ${square}.`,
        MISSES_MATE: (piece, square) => `The recommended line mates with the ${piece} on ${square}.`,
        LOSES_MATERIAL: (piece, square, captured, value) => `The ${piece} on ${square} captures the ${captured}. Material loss: ${value}.`,
        MISSES_MATERIAL: (piece, square, captured, value) => `The ${piece} on ${square} can capture the ${captured}. Material gain: ${value}.`,
        pieces: { PAWN: 'pawn', KNIGHT: 'knight', BISHOP: 'bishop', ROOK: 'rook', QUEEN: 'queen', KING: 'king' },
        start: 'Start', step: 'Line', pieceAt: (piece, white, square) => `${white ? 'white' : 'black'} ${piece} ${square}`,
    },
    ro: {
        title: 'Analiza partidei', 'archive-label': 'Deschide o analiză',
        'open-match': 'Partidă locală', 'open-review': 'Analiza partidei', 'new-match': 'Partidă nouă',
        'open-ai': 'Joc cu AI', 'side-label': 'Culoarea ta', 'level-label': 'Nivelul AI',
        'white-choice': 'Alb', 'black-choice': 'Negru', 'start-ai': 'Începe partida', 'cancel-setup': 'Anulează',
        'retry-bot': 'Reîncearcă mutarea AI', botTurn: 'AI este la mutare…', botFailure: 'AI nu a terminat mutarea. Poți reîncerca din această poziție.',
        aiProfile: (level, side) => `AI · ${level} · Joci cu ${side.toLowerCase()}`,
        analyze: 'Analizează partida', 'cancel-analysis': 'Oprește analiza', 'progress-label': 'Analizăm partida',
        'promote-label': 'Promovează în', promotion: 'Alege piesa',
        matchFailure: 'Partida curentă nu a putut fi deschisă sau modificată.', analysisFailure: 'Mutările înregistrate nu au putut fi analizate.',
        matchSaveFailed: 'Browserul nu a salvat mutarea. Ultima poziție salvată este disponibilă.',
        WHITE_WINS: 'Albul câștigă', BLACK_WINS: 'Negrul câștigă', DRAW: 'Remiză',
        empty: 'Deschide o arhivă Chess Lab pentru a revedea mutările și a compara variantele.',
        why: 'De ce?', 'why-title': 'De ce?', played: 'Linia jucată', recommended: 'Linia recomandată',
        'history-label': 'Istoricul mutărilor', 'seek-label': 'Alege poziția din istoric',
        movement: 'Navigare în analiză', previous: 'Mutarea anterioară', next: 'Mutarea următoare',
        'close-why': 'Închide explicația', board: 'Tabla de șah', WHITE: 'Albul la mutare', BLACK: 'Negrul la mutare',
        badArchive: 'Arhiva de analiză nu a putut fi deschisă.', failedAction: 'Analiza nu a putut fi actualizată.',
        saveFailed: 'Browserul nu a salvat schimbarea. Ultima analiză salvată este disponibilă.',
        changed: 'Altă filă a schimbat analiza. Reîncarcă pagina pentru a continua.',
        engineFailed: 'Motorul de analiză nu este disponibil. Reîncarcă pagina pentru a reîncerca.',
        evaluation: (played, best, depth) => `Jucată ${played} · Recomandată ${best} · Adâncime ${depth}`,
        ALLOWS_MATE: (piece, square) => `Replica dă mat cu ${piece} pe ${square}.`,
        MISSES_MATE: (piece, square) => `Linia recomandată dă mat cu ${piece} pe ${square}.`,
        LOSES_MATERIAL: (piece, square, captured, value) => `${piece} pe ${square} capturează ${captured}. Pierdere de material: ${value}.`,
        MISSES_MATERIAL: (piece, square, captured, value) => `${piece} pe ${square} poate captura ${captured}. Câștig de material: ${value}.`,
        pieces: { PAWN: 'pionul', KNIGHT: 'calul', BISHOP: 'nebunul', ROOK: 'turnul', QUEEN: 'dama', KING: 'regele' },
        start: 'Start', step: 'Linie', pieceAt: (piece, white, square) => `${piece} ${white ? 'la alb' : 'la negru'} pe ${square}`,
    },
}[locale];

const find = id => document.getElementById(id);
const board = find('board');
const matchBoard = find('match-board');
const status = find('status');
const history = find('history');
const glyphs = { p: '♟', n: '♞', b: '♝', r: '♜', q: '♛', k: '♚' };
const kinds = { p: 'PAWN', n: 'KNIGHT', b: 'BISHOP', r: 'ROOK', q: 'QUEEN', k: 'KING' };
const score = new Intl.NumberFormat(locale, { maximumFractionDigits: 1 });
const levels = { BASIC: 'Basic', ADVANCED: 'Advanced', HARD: 'Hard' };
const worker = new Worker(new URL('./engine-worker.mjs', import.meta.url), { type: 'module' });
const pending = new Map();
let serial = 0;
let engineFailed = false;
let busy = false;
let otherTabChanged = false;
let stored = null;
const storedMatches = { match: null, ai: null };
let archive;
let view;
let match;
let matchRoute;
let mode = 'review';
let setupVisible = false;
let botTimer;
let failedBotRevision;
let selectedSquare;
let promotionMoves = [];
let analysis;

document.documentElement.lang = locale;
document.title = `Chess Lab · ${words.title}`;
for (const id of ['title', 'archive-label', 'empty', 'why', 'why-title', 'played', 'recommended', 'history-label', 'seek-label',
    'open-match', 'open-ai', 'open-review', 'new-match', 'analyze', 'cancel-analysis', 'progress-label', 'promote-label',
    'side-label', 'level-label', 'white-choice', 'black-choice', 'start-ai', 'cancel-setup', 'retry-bot']) {
    find(id).textContent = words[id];
}
for (const id of ['movement', 'previous', 'next', 'close-why']) find(id).setAttribute('aria-label', words[id]);

const squares = buildBoard(board, false);
const matchSquares = buildBoard(matchBoard, true);

worker.addEventListener('message', ({ data }) => {
    const request = pending.get(data.id);
    if (!request) return;
    pending.delete(data.id);
    if (data.error) request.reject(new Error(data.error));
    else request.resolve(data);
});

function stopWorker() {
    cancelAnalysis();
    engineFailed = true;
    scheduleBot();
    worker.terminate();
    for (const request of pending.values()) request.reject(new Error(words.engineFailed));
    pending.clear();
    setStatus(words.engineFailed);
}

worker.addEventListener('error', stopWorker);
worker.addEventListener('messageerror', stopWorker);

function call(type, fields) {
    if (engineFailed) return Promise.reject(new Error(words.engineFailed));
    const id = String(++serial);
    return new Promise((resolve, reject) => {
        pending.set(id, { resolve, reject });
        worker.postMessage({ id, type, ...fields });
    });
}

function coordinate(kind, text) {
    const label = document.createElement('span');
    label.className = `coordinate ${kind}`;
    label.textContent = text;
    return label;
}

function buildBoard(target, playable) {
    const cells = new Map();
    for (let rank = 8; rank > 0; rank--) {
        for (let column = 0; column < 8; column++) {
            const file = 'abcdefgh'[column];
            const name = `${file}${rank}`;
            const cell = document.createElement(playable ? 'button' : 'div');
            cell.dataset.square = name;
            cell.className = `square${(rank + column) % 2 ? ' dark' : ''}`;
            if (playable) {
                cell.type = 'button';
                cell.tabIndex = name === 'e2' ? 0 : -1;
            } else cell.setAttribute('aria-hidden', 'true');
            const piece = document.createElement('span');
            piece.className = 'piece';
            piece.setAttribute('aria-hidden', 'true');
            cell.append(piece);
            cells.set(name, { cell, piece });
        }
    }
    orientBoard(target, cells, false);
    return cells;
}

function orientBoard(target, cells, blackBelow) {
    const orientation = blackBelow ? 'BLACK' : 'WHITE';
    if (target.dataset.orientation === orientation) return;
    target.dataset.orientation = orientation;
    const order = [...cells.values()].map(({ cell }) => cell);
    if (blackBelow) order.reverse();
    order.forEach((cell, index) => {
        cell.querySelectorAll('.coordinate').forEach(label => label.remove());
        if (index % 8 === 0) cell.append(coordinate('rank', cell.dataset.square[1]));
        if (index >= 56) cell.append(coordinate('file', cell.dataset.square[0]));
        if (target === matchBoard) cell.tabIndex = cell.dataset.square === (blackBelow ? 'e7' : 'e2') ? 0 : -1;
    });
    target.replaceChildren(...order);
}

function drawBoard(target, cells, state) {
    const placement = state.fen.split(' ')[0].split('/').join('').replace(/[1-8]/g, count => ' '.repeat(Number(count)));
    if (placement.length !== 64) throw new Error('Invalid board placement');
    target.dataset.fen = state.fen;
    target.dataset.lastMove = state.lastMove ?? '';
    target.dataset.turn = state.sideToMove;
    const descriptions = [];
    let index = 0;
    for (const [name, { cell, piece }] of cells) {
        const token = placement[index++];
        const exists = token !== ' ';
        cell.dataset.piece = exists ? token : '';
        cell.toggleAttribute('data-last', state.lastMove?.slice(0, 2) === name || state.lastMove?.slice(2, 4) === name);
        cell.toggleAttribute('data-cue', state.cueFrom === name || state.cueTo === name);
        const white = exists && token === token.toUpperCase();
        const type = token.toLowerCase();
        piece.classList.toggle('white', white);
        piece.textContent = glyphs[type] ?? '';
        const description = exists ? words.pieceAt(words.pieces[kinds[type]], white, name) : name;
        if (exists) descriptions.push(description);
        if (target === matchBoard) cell.setAttribute('aria-label', description);
    }
    const turn = words[state.sideToMove];
    target.setAttribute('aria-label', `${words.board} · ${turn}`);
    if (target === board) {
        find('board-position').textContent = descriptions.join(', ');
        find('turn').textContent = turn;
    }
}

function drawExplanation(state) {
    find('why').disabled = !state.why && !state.explanationOpen;
    find('why').setAttribute('aria-expanded', state.explanationOpen);
    find('explanation').hidden = !state.explanationOpen;
    if (!state.explanationOpen) return;
    const reason = find('reason');
    const explain = words[state.reason];
    reason.hidden = !explain;
    reason.dataset.kind = state.reason ?? '';
    reason.dataset.square = state.decisiveSquare ?? '';
    reason.textContent = explain ? explain(words.pieces[state.decisivePiece], state.decisiveSquare,
        words.pieces[state.capturedPiece], score.format(state.materialCp / 100)) : '';
    find('evaluation').textContent = words.evaluation(score.format(state.playedPoints / 10),
        score.format(state.bestPoints / 10), state.depth);
    for (const branch of ['played', 'recommended']) {
        find(branch).disabled = !state[branch];
        find(branch).setAttribute('aria-pressed', state.branch === branch.toUpperCase());
    }
}

function drawHistory(state) {
    const moves = [words.start, ...state.moves];
    if (history.childElementCount !== moves.length || moves.some((move, index) => history.children[index]?.dataset.move !== move)) {
        const rows = moves.map((move, ply) => {
            const row = document.createElement('li');
            const item = document.createElement('button');
            item.type = 'button';
            item.dataset.ply = ply;
            row.dataset.move = move;
            item.textContent = ply === 0 ? move : `${ply} · ${move}`;
            row.append(item);
            return row;
        });
        history.replaceChildren(...rows);
    }
    for (const row of history.children) {
        const item = row.firstElementChild;
        if (Number(item.dataset.ply) === state.ply) item.setAttribute('aria-current', 'step');
        else item.removeAttribute('aria-current');
    }
    find('seek').max = state.totalPlies;
    find('seek').value = state.ply;
}

function render() {
    const container = find('review');
    container.hidden = mode !== 'review' || !view;
    find('empty').hidden = mode !== 'review' || Boolean(view);
    if (!view) return;
    container.dataset.ply = view.ply;
    container.dataset.branch = view.branch;
    container.dataset.step = view.step;
    const onLine = view.branch !== 'HISTORY';
    drawBoard(board, squares, view);
    find('previous').disabled = !(onLine ? view.linePrevious : view.previous);
    find('next').disabled = !(onLine ? view.lineNext : view.next);
    find('position').textContent = onLine ? `${words.step} ${view.step}/${view.lineLength}` : `${view.ply}/${view.totalPlies}`;
    drawExplanation(view);
    drawHistory(view);
}

function setStatus(message) {
    status.textContent = message;
}

function normalizeArchive(value) {
    if (!value || ['id', 'checkpoint', 'review'].some(key => typeof value[key] !== 'string' || !value[key])) {
        throw new Error('Invalid review archive');
    }
    const { id, checkpoint, review, bookmark } = value;
    return typeof bookmark === 'string' ? { id, checkpoint, review, bookmark } : { id, checkpoint, review };
}

async function exclusive(work, failure) {
    if (busy || otherTabChanged || engineFailed) return;
    busy = true;
    find('review').setAttribute('aria-busy', 'true');
    find('match').setAttribute('aria-busy', 'true');
    setStatus('');
    try {
        await work();
    } catch {
        setStatus(otherTabChanged ? words.changed : engineFailed ? words.engineFailed : failure);
    } finally {
        busy = false;
        find('review').setAttribute('aria-busy', 'false');
        find('match').setAttribute('aria-busy', 'false');
        scheduleBot();
    }
}

async function restoreDurable(message) {
    if (archive && !otherTabChanged) {
        try {
            view = (await call('review-open', { archive })).view;
        } catch {
            stopWorker();
            return;
        }
    }
    render();
    setStatus(message);
}

async function commit(state, source) {
    const nextArchive = { ...source, bookmark: state.bookmark };
    try {
        if (otherTabChanged || localStorage.getItem(storageKey) !== stored) {
            otherTabChanged = true;
            setStatus(words.changed);
            return false;
        }
        const value = JSON.stringify(nextArchive);
        localStorage.setItem(storageKey, value);
        stored = value;
    } catch {
        await restoreDurable(words.saveFailed);
        return false;
    }
    archive = nextArchive;
    view = state;
    render();
    return true;
}

function action(action, ply) {
    if (!view) return;
    void exclusive(async () => {
        const response = await call('review-action', { action, expectedRevision: view.revision, ply });
        if (response.outcome === 'STALE') {
            await restoreDurable(words.failedAction);
        } else if (response.outcome === 'APPLIED' && await commit(response.view, archive)) {
            if (action === 'OPEN_EXPLANATION') find('why-title').focus({ preventScroll: true });
            if (action === 'CLOSE_EXPLANATION') find('why').focus({ preventScroll: true });
        } else if (response.outcome === 'UNAVAILABLE') render();
    }, words.failedAction);
}

function renderMatch() {
    const container = find('match');
    container.hidden = mode !== matchRoute || !match || setupVisible;
    if (container.hidden) return;
    const isAi = matchRoute === 'ai';
    container.dataset.ply = match.ply;
    container.dataset.result = match.result;
    container.dataset.mode = match.mode;
    container.dataset.humanSide = match.humanSide;
    container.dataset.level = match.level;
    container.dataset.awaitingBot = match.awaitingBot;
    orientBoard(matchBoard, matchSquares, isAi && match.humanSide === 'BLACK');
    drawBoard(matchBoard, matchSquares, match);
    const result = match.result === 'IN_PROGRESS' ? words[match.sideToMove] : words[match.result] ?? words.DRAW;
    find('match-turn').textContent = match.awaitingBot && failedBotRevision !== match.revision ? words.botTurn : result;
    find('match-profile').hidden = !isAi;
    if (isAi) find('match-profile').textContent = words.aiProfile(levels[match.level], words[`${match.humanSide.toLowerCase()}-choice`]);
    find('new-match').hidden = !isAi && match.result === 'IN_PROGRESS';
    find('retry-bot').hidden = !match.awaitingBot || failedBotRevision !== match.revision;
    const targets = new Set(match.legalMoves.filter(move => move.slice(0, 2) === selectedSquare).map(move => move.slice(2, 4)));
    for (const [square, { cell }] of matchSquares) {
        cell.toggleAttribute('data-selected', square === selectedSquare);
        cell.toggleAttribute('data-target', targets.has(square));
        cell.setAttribute('aria-pressed', square === selectedSquare);
    }
    find('promotion').hidden = promotionMoves.length === 0;
    renderAnalysis();
}

function renderAnalysis() {
    const running = Boolean(analysis);
    find('analysis-progress').hidden = !running;
    find('cancel-analysis').hidden = !running;
    find('analyze').disabled = running || !match || match.awaitingBot || match.ply === 0;
    find('new-match').disabled = running || Boolean(match?.awaitingBot);
}

function showMode(nextMode) {
    if (nextMode !== mode) cancelAnalysis();
    mode = nextMode;
    window.history.replaceState(null, '', routeHashes[mode] ?? (location.pathname + location.search));
    const game = mode !== 'review';
    find('title').textContent = words[mode === 'ai' ? 'open-ai' : game ? 'open-match' : 'title'];
    document.title = `Chess Lab · ${find('title').textContent}`;
    find('open-match').hidden = game;
    find('open-ai').hidden = game;
    find('open-review').hidden = !game;
    find('review-import').hidden = game;
    find('ai-setup').hidden = mode !== 'ai' || !setupVisible;
    find('cancel-setup').hidden = storedMatches.ai === null;
    renderMatch();
    render();
    scheduleBot();
}

async function restoreMatchDurable(message) {
    const checkpoint = storedMatches[matchRoute];
    if (checkpoint && !otherTabChanged) {
        try {
            match = (await call('match-open', { checkpoint })).match;
        } catch {
            stopWorker();
            return;
        }
    }
    selectedSquare = undefined;
    promotionMoves = [];
    renderMatch();
    setStatus(message);
}

async function commitMatch(state, route) {
    if (state.mode !== (route === 'ai' ? 'AI' : 'LOCAL')) throw new Error('Match mode does not match storage slot');
    try {
        if (otherTabChanged || localStorage.getItem(matchKeys[route]) !== storedMatches[route]) {
            otherTabChanged = true;
            setStatus(words.changed);
            return false;
        }
        localStorage.setItem(matchKeys[route], state.checkpoint);
        storedMatches[route] = state.checkpoint;
    } catch {
        await restoreMatchDurable(words.matchSaveFailed);
        return false;
    }
    match = state;
    matchRoute = route;
    failedBotRevision = undefined;
    selectedSquare = undefined;
    promotionMoves = [];
    renderMatch();
    return true;
}

async function enterMatch(route, setup) {
    if (route === 'ai' && storedMatches.ai === null && !setup) {
        openAiSetup();
        return;
    }
    if (matchRoute === route && match && !setup) {
        setupVisible = false;
        showMode(route);
        return;
    }
    await exclusive(async () => {
        const checkpoint = storedMatches[route];
        const options = setup ? { setup } : checkpoint === null ? {} : { checkpoint };
        const response = await call('match-open', options);
        if (await commitMatch(response.match, route)) {
            setupVisible = false;
            showMode(route);
        }
    }, words.matchFailure);
}

function play(move) {
    if (analysis || !match || match.awaitingBot) return;
    void exclusive(async () => {
        const response = await call('match-play', { move, expectedRevision: match.revision });
        if (response.outcome === 'APPLIED') await commitMatch(response.match, matchRoute);
        else await restoreMatchDurable(words.matchFailure);
    }, words.matchFailure);
}

function scheduleBot() {
    const ready = mode === 'ai' && matchRoute === 'ai' && !setupVisible && match?.awaitingBot &&
        failedBotRevision !== match.revision && !busy && !analysis && !engineFailed && !otherTabChanged;
    if (!ready) {
        clearTimeout(botTimer);
        botTimer = undefined;
        return;
    }
    if (botTimer !== undefined) return;
    const waiting = match;
    botTimer = setTimeout(() => {
        botTimer = undefined;
        if (mode !== 'ai' || match !== waiting) return;
        void exclusive(async () => {
            try {
                const response = await call('match-bot', { expectedRevision: waiting.revision });
                if (response.outcome !== 'APPLIED') throw new Error('Bot move unavailable at this revision');
                if (await commitMatch(response.match, 'ai')) return;
            } catch {
                await restoreMatchDurable(words.botFailure);
            }
            failedBotRevision = match.revision;
            renderMatch();
        }, words.botFailure);
    }, botDelayMillis);
}

function chooseSquare(square) {
    if (!match || busy || analysis || otherTabChanged || engineFailed || match.awaitingBot || match.result !== 'IN_PROGRESS') return;
    const target = `${selectedSquare}${square}`;
    const moves = match.legalMoves.filter(move => move.slice(0, 4) === target);
    if (moves.length === 1) {
        play(moves[0]);
        return;
    }
    if (moves.length > 1) {
        promotionMoves = moves;
        const promote = find('promote');
        const prompt = new Option(words.promotion, '', true, true);
        prompt.disabled = true;
        promote.replaceChildren(prompt, ...moves.map(move => new Option(words.pieces[kinds[move[4]]], move)));
        renderMatch();
        promote.focus({ preventScroll: true });
        return;
    }
    selectedSquare = square !== selectedSquare && match.legalMoves.some(move => move.startsWith(square)) ? square : undefined;
    promotionMoves = [];
    renderMatch();
}

function cancelAnalysis() {
    analysis?.terminate();
    analysis = undefined;
    renderAnalysis();
}

function analyzeMatch() {
    if (!match?.ply || match.awaitingBot || busy || analysis || otherTabChanged || engineFailed) return;
    const snapshot = match;
    const route = matchRoute;
    const job = new Worker(new URL('./engine-worker.mjs', import.meta.url), { type: 'module' });
    analysis = job;
    const id = String(++serial);
    const progress = find('progress');
    progress.removeAttribute('value');
    renderAnalysis();
    setStatus('');
    const fail = () => {
        if (analysis !== job) return;
        cancelAnalysis();
        setStatus(words.analysisFailure);
    };
    job.addEventListener('error', fail);
    job.addEventListener('messageerror', fail);
    job.addEventListener('message', ({ data }) => {
        if (analysis !== job || data.id !== id) return;
        if (data.error) return fail();
        if (Number.isInteger(data.progress) && data.total === snapshot.ply) {
            progress.max = data.total;
            progress.value = data.progress;
            return;
        }
        cancelAnalysis();
        if (match !== snapshot || mode !== route) return;
        void exclusive(async () => {
            const source = normalizeArchive(data.archive);
            if (source.checkpoint !== snapshot.reviewCheckpoint) throw new Error('Analysis checkpoint differs from the active game');
            const response = await call('review-open', { archive: source });
            if (match === snapshot && mode === route && await commit(response.view, source)) showMode('review');
        }, words.analysisFailure);
    });
    job.postMessage({ id, type: 'review-analyze', checkpoint: snapshot.reviewCheckpoint });
}

function openAiSetup() {
    if (matchRoute === 'ai' && match) {
        find('ai-side').value = match.humanSide;
        find('ai-level').value = match.level;
    }
    setupVisible = true;
    showMode('ai');
}

find('open-match').addEventListener('click', () => {
    if (!busy) void enterMatch('match');
});
find('open-ai').addEventListener('click', () => {
    if (!busy) void enterMatch('ai');
});
find('open-review').addEventListener('click', () => {
    setupVisible = false;
    showMode('review');
});
find('new-match').addEventListener('click', () => {
    if (busy || analysis || !match || match.awaitingBot) return;
    if (matchRoute === 'ai') openAiSetup();
    else if (match.result !== 'IN_PROGRESS') void enterMatch('match', { mode: 'LOCAL', humanSide: 'WHITE', level: 'BASIC' });
});
find('ai-setup').addEventListener('submit', event => {
    event.preventDefault();
    if (busy || mode !== 'ai') return;
    void enterMatch('ai', { mode: 'AI', humanSide: find('ai-side').value, level: find('ai-level').value });
});
find('cancel-setup').addEventListener('click', () => {
    if (!busy) void enterMatch('ai');
});
find('retry-bot').addEventListener('click', () => {
    if (failedBotRevision !== match?.revision) return;
    failedBotRevision = undefined;
    setStatus('');
    renderMatch();
    scheduleBot();
});
find('analyze').addEventListener('click', analyzeMatch);
find('cancel-analysis').addEventListener('click', cancelAnalysis);
find('promote').addEventListener('change', event => {
    if (promotionMoves.includes(event.target.value)) play(event.target.value);
});
matchBoard.addEventListener('click', event => {
    const cell = event.target.closest('button[data-square]');
    if (!cell) return;
    if (cell.tabIndex === -1) {
        matchBoard.querySelector('[tabindex="0"]').tabIndex = -1;
        cell.tabIndex = 0;
    }
    chooseSquare(cell.dataset.square);
});
matchBoard.addEventListener('keydown', event => {
    const delta = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -8, ArrowDown: 8 }[event.key];
    if (!delta) return;
    const cell = event.target.closest('button[data-square]');
    if (!cell) return;
    const index = Array.from(matchBoard.children).indexOf(cell);
    const next = index + delta;
    if (Math.abs(delta) === 1 && Math.floor(index / 8) !== Math.floor(next / 8)) return;
    const destination = matchBoard.children[next];
    if (!destination) return;
    event.preventDefault();
    cell.tabIndex = -1;
    destination.tabIndex = 0;
    destination.focus();
});

find('previous').addEventListener('click', () => action('PREVIOUS'));
find('next').addEventListener('click', () => action('NEXT'));
find('why').addEventListener('click', () => action(view.explanationOpen ? 'CLOSE_EXPLANATION' : 'OPEN_EXPLANATION'));
find('close-why').addEventListener('click', () => action('CLOSE_EXPLANATION'));
find('played').addEventListener('click', () => action('SELECT_PLAYED'));
find('recommended').addEventListener('click', () => action('SELECT_RECOMMENDED'));
find('seek').addEventListener('change', event => action('SEEK', Number(event.target.value)));
history.addEventListener('click', event => {
    const row = event.target.closest('button[data-ply]');
    if (row) action('SEEK', Number(row.dataset.ply));
});
find('archive').addEventListener('change', event => {
    const file = event.target.files[0];
    event.target.value = '';
    if (!file) return;
    void exclusive(async () => {
        const imported = normalizeArchive(JSON.parse(await file.text()));
        const response = await call('review-open', { archive: imported });
        await commit(response.view, imported);
    }, words.badArchive);
});
addEventListener('storage', event => {
    if (event.storageArea !== localStorage) return;
    const matchChanged = Object.entries(matchKeys).some(([route, key]) => event.key === key && event.newValue !== storedMatches[route]);
    if ((event.key === storageKey && event.newValue !== stored) || matchChanged
        || (event.key === null && (stored !== null || Object.values(storedMatches).some(value => value !== null)))) {
        otherTabChanged = true;
        cancelAnalysis();
        scheduleBot();
        setStatus(words.changed);
    }
});

await exclusive(async () => {
    stored = localStorage.getItem(storageKey);
    for (const [route, key] of Object.entries(matchKeys)) storedMatches[route] = localStorage.getItem(key);
    if (stored === null) return;
    const restored = normalizeArchive(JSON.parse(stored));
    const response = await call('review-open', { archive: restored });
    archive = restored;
    view = response.view;
    render();
}, words.badArchive);
for (const [route, hash] of Object.entries(routeHashes)) {
    if (location.hash === hash) await enterMatch(route);
}
