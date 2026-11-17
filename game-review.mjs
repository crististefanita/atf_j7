import { createReviewWorkspace, showReviewPiece } from './review-workspace.mjs';
import { createHomeMenu, homeLabels, homeLocale, reloadProfileLanguage } from './home-menu.mjs';
import { bindBoardSelection, boardPlacement, mountStudyArrow, studyMarkers, studySquareDescription } from './study-board.mjs';
import { createQuestStudy } from './study-quests.mjs';
import { createOpeningStudy } from './study-openings.mjs';
import { createAnalysisStudy } from './study-analysis.mjs';
import { matchAnalysisNavigation, routeForHash, routeHashes, watchRouteHistory, writeModeHash } from './study-navigation.mjs';
import { historyPlyForKey, reviewFocusCandidates } from './review-focus.mjs';
import { reviewAnnouncementChannel, reviewWhyText, sliderPlyForKey } from './review-announcement.mjs';
import { createMatchBotTimer } from './match-bot-timer.mjs';
import { attachOptionsMenu } from './options-menu.mjs';

const storageKey = 'chesslab.game-review.v1';
const ledgerKey = 'chesslab.match-ledger.v1';
const legacyMatchKeys = { local: 'chesslab.active-match.v1', ai: 'chesslab.ai-match.v1' };
const studyRoutes = {
    quests: ['home-puzzles', createQuestStudy],
    openings: ['home-openings', createOpeningStudy],
    analysis: ['home-analysis', createAnalysisStudy],
};
const botDelayMillis = 650;
const locale = homeLocale(location.search, localStorage);
const words = {
    en: {
        title: 'Game review', 'archive-label': 'Open a review',
        'open-match': 'Local game', 'open-review': 'Main menu', 'new-match': 'New game',
        'match-options-title': 'Game options', 'flip-match': 'Flip board',
        'menu-saved': 'Saved games', 'menu-analysis': 'Analysis board', 'menu-review': 'Game review',
        'menu-draw': 'Offer draw', 'menu-resign': 'Resign game', 'ai-analysis-label': 'AI analysis for a new game',
        'resign-message': 'End this game and record a loss?', 'resign-confirm': 'Confirm resignation', 'resign-cancel': 'Cancel',
        drawDeclined: 'The AI declined the draw.', resigned: 'You resigned', agreedDraw: 'Draw by agreement', reviewEmpty: 'There are no moves to analyze.',
        'match-automatic-label': 'Let AI play its moves', manualAiTurn: 'Your move for the AI side',
        automationSaveFailed: 'The browser could not save the AI setting.',
        'open-ai': 'Play against AI', 'side-label': 'Your color', 'level-label': 'AI level',
        'open-saved': 'Saved games', 'open-finished': 'Finished games', 'return-menu': 'Back',
        'archive-review-back': 'Close', 'review-close': 'Close', 'reopen-review': 'Open analysis', openCached: 'Open analysis', reviewed: 'Analysis saved',
        'review-actions': 'Review actions', 'review-on-board': 'Show on board', 'next-key': 'Next key moment',
        bestLine: 'Show recommended line', hideLine: 'Hide recommended line', linePrevious: 'Previous line step', lineNext: 'Next line step',
        'new-local': 'New local game', 'new-ai': 'New AI game', 'match-history': 'Game history',
        'continue-match': 'Continue from here', 'continue-title': 'Continue from here', 'confirm-continue': 'Continue',
        'continue-review': 'Continue from here',
        'cancel-continue': 'Cancel', 'automatic-label': 'Keep AI replies enabled',
        emptySaved: 'No saved games.', emptyFinished: 'No finished games.',
        noFreeSlot: 'All saved game slots are in use. Finish a saved game before continuing this result.',
        continueActive: ply => `Continue this game from move ${ply}? Later moves will be replaced.`,
        continueArchive: ply => `Start a saved game from move ${ply}? The finished game stays in the archive.`,
        moves: count => `${count} ${count === 1 ? 'move' : 'moves'}`, active: 'In progress',
        'white-choice': 'White', 'black-choice': 'Black', 'random-choice': 'Random',
        'start-ai': 'Start game', 'cancel-setup': 'Cancel',
        'retry-bot': 'Retry AI move', botTurn: 'AI to move…', botFailure: 'The AI move could not be completed. You can retry from this position.',
        aiProfile: (level, side) => `AI · ${level} · You play ${side.toLowerCase()}`,
        analyze: 'Analyze game', 'cancel-analysis': 'Cancel analysis', 'progress-label': 'Analyzing game',
        'promote-label': 'Promote to', promotion: 'Choose a piece',
        matchFailure: 'Could not open or change the active game.', analysisFailure: 'Could not analyze the recorded moves.',
        matchSaveFailed: 'The browser could not save this move. The last saved position is still available.',
        WHITE_WINS: 'White wins', BLACK_WINS: 'Black wins', WON: 'Win', LOST: 'Loss', DRAW: 'Draw',
        why: 'Why?', 'why-title': 'Why?', played: 'After move', recommended: 'Recommended',
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
        'open-match': 'Partidă locală', 'open-review': 'Meniu principal', 'new-match': 'Partidă nouă',
        'match-options-title': 'Opțiunile partidei', 'flip-match': 'Întoarce tabla',
        'menu-saved': 'Partide salvate', 'menu-analysis': 'Tablă de analiză', 'menu-review': 'Analiza partidei',
        'menu-draw': 'Oferă remiză', 'menu-resign': 'Renunță la partidă', 'ai-analysis-label': 'Analiză AI pentru o partidă nouă',
        'resign-message': 'Închei partida și înregistrezi o înfrângere?', 'resign-confirm': 'Confirmă renunțarea', 'resign-cancel': 'Anulează',
        drawDeclined: 'AI-ul a refuzat remiza.', resigned: 'Ai renunțat la partidă', agreedDraw: 'Remiză prin acord', reviewEmpty: 'Partida nu are mutări de analizat.',
        'match-automatic-label': 'Lasă AI-ul să mute', manualAiTurn: 'Mută pentru culoarea AI-ului',
        automationSaveFailed: 'Browserul nu a putut salva setarea AI.',
        'open-ai': 'Joc cu AI', 'side-label': 'Culoarea ta', 'level-label': 'Nivelul AI',
        'open-saved': 'Partide salvate', 'open-finished': 'Partide încheiate', 'return-menu': 'Înapoi',
        'archive-review-back': 'Închide', 'review-close': 'Închide', 'reopen-review': 'Deschide analiza', openCached: 'Deschide analiza', reviewed: 'Analiză salvată',
        'review-actions': 'Acțiuni de analiză', 'review-on-board': 'Arată pe tablă', 'next-key': 'Moment-cheie următor',
        bestLine: 'Arată linia recomandată', hideLine: 'Ascunde linia recomandată',
        linePrevious: 'Pasul anterior al variantei', lineNext: 'Pasul următor al variantei',
        'new-local': 'Partidă locală nouă', 'new-ai': 'Partidă nouă cu AI', 'match-history': 'Istoricul partidei',
        'continue-match': 'Continuă de aici', 'continue-title': 'Continuă de aici', 'confirm-continue': 'Continuă',
        'continue-review': 'Continuă de aici',
        'cancel-continue': 'Anulează', 'automatic-label': 'Păstrează replicile AI',
        emptySaved: 'Nu ai partide salvate.', emptyFinished: 'Nu ai partide încheiate.',
        noFreeSlot: 'Toate locurile sunt ocupate. Încheie o partidă salvată pentru a continua acest rezultat.',
        continueActive: ply => `Continui această partidă de la mutarea ${ply}? Mutările ulterioare vor fi înlocuite.`,
        continueArchive: ply => `Începi o partidă salvată de la mutarea ${ply}? Rezultatul inițial rămâne în arhivă.`,
        moves: count => `${count} ${count === 1 ? 'mutare' : 'mutări'}`, active: 'În desfășurare',
        'white-choice': 'Alb', 'black-choice': 'Negru', 'random-choice': 'Aleatoriu',
        'start-ai': 'Începe partida', 'cancel-setup': 'Anulează',
        'retry-bot': 'Reîncearcă mutarea AI', botTurn: 'AI este la mutare…', botFailure: 'AI nu a terminat mutarea. Poți reîncerca din această poziție.',
        aiProfile: (level, side) => `AI · ${level} · Joci cu ${side.toLowerCase()}`,
        analyze: 'Analizează partida', 'cancel-analysis': 'Oprește analiza', 'progress-label': 'Analizăm partida',
        'promote-label': 'Promovează în', promotion: 'Alege piesa',
        matchFailure: 'Partida curentă nu a putut fi deschisă sau modificată.', analysisFailure: 'Mutările înregistrate nu au putut fi analizate.',
        matchSaveFailed: 'Browserul nu a salvat mutarea. Ultima poziție salvată este disponibilă.',
        WHITE_WINS: 'Albul câștigă', BLACK_WINS: 'Negrul câștigă', WON: 'Victorie', LOST: 'Înfrângere', DRAW: 'Remiză',
        why: 'De ce?', 'why-title': 'De ce?', played: 'După mutare', recommended: 'Recomandată',
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
const homeMenu = createHomeMenu(find, locale, localStorage, async name => (await call('profile-name', { name })).name, (profile, fields) => {
    if (!reloadProfileLanguage(window, locale, profile.language, fields)) renderMatch();
});
const board = find('board');
const matchBoard = find('match-board');
const matchOptions = find('match-options');
const matchOptionsButton = find('match-options-button');
const resignation = find('match-resign');
const menuActions = {
    FLIP: 'flip-match', HOME: 'open-review', SAVED_GAMES: 'menu-saved', ANALYSIS_BOARD: 'menu-analysis',
    GAME_REVIEW: 'menu-review', OFFER_DRAW: 'menu-draw', RESIGN: 'menu-resign',
};
let resignationRevision;
let liveReviewOrigin;
const matchMenu = attachOptionsMenu(matchOptionsButton, matchOptions, {
    canOpen: () => !busy,
    onToggle: scheduleBot,
});
const matchAutomatic = find('match-automatic');
const status = find('status');
const history = find('history');
const reviewWorkspace = createReviewWorkspace(locale, (x, width) => reviewCommand('review-graph-select', { x, width }, 'history'));
const glyphs = { p: '♟', n: '♞', b: '♝', r: '♜', q: '♛', k: '♚' };
const kinds = { p: 'PAWN', n: 'KNIGHT', b: 'BISHOP', r: 'ROOK', q: 'QUEEN', k: 'KING' };
const score = new Intl.NumberFormat(locale, { maximumFractionDigits: 1 });
const gameDate = new Intl.DateTimeFormat(locale, { dateStyle: 'short', timeStyle: 'short' });
const levels = { BASIC: 'Basic', ADVANCED: 'Advanced', HARD: 'Hard' };
const listRoutes = new Set(['saved', 'finished']);
const localSetup = { mode: 'LOCAL', humanSide: 'WHITE', level: 'BASIC' };
const worker = new Worker(new URL('./engine-worker.mjs', import.meta.url), { type: 'module' });
const pending = new Map();
let serial = 0;
let engineFailed = false;
let busy = false;
let otherTabChanged = false;
let stored = null;
let storedLedger = null;
let pieceArtwork;
let ledgerReady = false;
let savedMatches = [];
let finishedMatches = [];
let archive;
let view;
let archiveReviewId;
let reportScrollBeforeExplanation = 0;
let focusChange = 0;
let match;
let matchRoute;
let mode;
let setupVisible = false;
let setupBackMode;
const scheduleBotReply = createMatchBotTimer(window, botDelayMillis, runBotTurn);
let failedBotRevision;
let selectedSquare;
let promotionMoves = [];
let analysis;
let studyAnalysis;
let studyReviewOpening = false;
let studyReturn;
let studyGeneration = 0;
let studyTotalsDirty = true;
const contextualAnalysis = matchAnalysisNavigation({
    browser: window,
    currentMatch: () => match,
    openStudy: entry => openStudy('analysis', { addHistory: true, entry }),
    returnToMatch: showMode,
});
const studyPanels = Object.fromEntries(Object.entries(studyRoutes).map(([route, [, create]]) => {
    const root = find('study-' + route);
    return [route, create({
        root, locale, home: leaveStudy, board: createStudyBoard,
        openReview: openStudyReview, continueGame: continueStudyGame,
        call: async (action, { value = '', index = 0, saved = '', option = '' } = {}) =>
            (await call('study', { feature: route, action, value, index, saved, option, locale })).study,
    })];
}));

document.documentElement.lang = locale;
for (const id of ['archive-label', 'why', 'why-title', 'played', 'recommended', 'history-label', 'seek-label',
    'open-match', 'open-review', 'flip-match', 'match-automatic-label',
    'menu-saved', 'menu-analysis', 'menu-review', 'menu-draw', 'menu-resign', 'ai-analysis-label',
    'resign-message', 'resign-confirm', 'resign-cancel',
    'new-match', 'analyze', 'cancel-analysis', 'progress-label', 'promote-label',
    'side-label', 'level-label', 'white-choice', 'black-choice', 'random-choice', 'start-ai', 'cancel-setup',
    'retry-bot', 'open-saved',
    'open-finished', 'return-menu', 'new-local', 'new-ai', 'continue-match', 'continue-title', 'confirm-continue',
    'cancel-continue', 'automatic-label', 'archive-review-back', 'continue-review', 'review-close', 'reopen-review', 'review-on-board']) {
    find(id).textContent = words[id];
}
for (const id of ['movement', 'previous', 'next', 'close-why', 'review-actions', 'next-key']) find(id).setAttribute('aria-label', words[id]);
find('open-match-analysis').textContent = homeLabels[locale]['home-analysis'];
matchOptionsButton.setAttribute('aria-label', words['match-options-title']);
matchOptions.setAttribute('aria-label', words['match-options-title']);
find('resign-title').textContent = words['menu-resign'];
find('why-actions').setAttribute('aria-label', words['review-actions']);
find('match-history').setAttribute('aria-label', words['match-history']);
find('study-progress-label').textContent = words['progress-label'];
find('cancel-study-analysis').textContent = words['cancel-analysis'];
find('cancel-study-analysis').addEventListener('click', () => studyAnalysis?.cancel());
for (const id of ['previous', 'next']) find(`match-${id}`).setAttribute('aria-label', words[id]);
document.addEventListener('focusin', event => {
    focusChange++;
    if (find('review').contains(event.target)) reviewWorkspace.revealFocus(event.target);
});

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
    renderMatchAutomation();
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
        if (cell.tagName === 'BUTTON') cell.tabIndex = cell.dataset.square === (blackBelow ? 'e7' : 'e2') ? 0 : -1;
    });
    target.replaceChildren(...order);
}

function drawBoard(target, cells, state) {
    const placement = boardPlacement(state.fen);
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
        if (target === board) cell.toggleAttribute('data-cue-origin', state.cueFrom === name);
        const white = exists && token === token.toUpperCase();
        const type = token.toLowerCase();
        piece.classList.toggle('white', white);
        if (!showReviewPiece(piece, type, state.pieceArt ?? pieceArtwork)) piece.textContent = glyphs[type] ?? '';
        const description = exists ? words.pieceAt(words.pieces[kinds[type]], white, name) : name;
        if (exists) descriptions.push(description);
        if (cell.tagName === 'BUTTON') cell.setAttribute('aria-label', description);
    }
    const turn = words[state.sideToMove];
    target.setAttribute('aria-label', `${words.board} · ${turn}`);
    if (target === board) {
        find('board-position').textContent = descriptions.join(', ');
        find('turn').textContent = turn;
    }
}

function createStudyBoard(host, onSquare) {
    host.classList.add('board', 'study-board');
    host.setAttribute('role', 'group');
    const cells = buildBoard(host, true);
    const drawArrow = mountStudyArrow(host);
    bindBoardSelection(host, onSquare);
    return { render(state) {
        const targets = new Set(state.targets ?? []);
        const { origin, destination, danger } = studyMarkers(state.marks);
        const blackBelow = state.blackBelow === true;
        orientBoard(host, cells, blackBelow);
        drawBoard(host, cells, { ...state, fen: state.board ?? state.fen });
        for (const [square, { cell }] of cells) {
            const selected = square === state.selected;
            const markers = { target: targets.has(square), origin: square === origin, destination: square === destination, danger: square === danger };
            cell.toggleAttribute('data-selected', selected);
            cell.toggleAttribute('data-target', markers.target);
            cell.toggleAttribute('data-study-hint', markers.origin);
            cell.toggleAttribute('data-study-danger', markers.danger);
            cell.setAttribute('aria-label', studySquareDescription(cell.getAttribute('aria-label'), locale, markers));
            cell.setAttribute('aria-pressed', String(selected));
            cell.setAttribute('aria-disabled', String(state.locked === true));
        }
        drawArrow(origin, destination, blackBelow);
    } };
}

function drawExplanation(state) {
    find('why').disabled = !state.why && !state.explanationOpen;
    find('why').hidden = find('why').disabled;
    find('why').setAttribute('aria-expanded', state.explanationOpen);
    find('explanation').hidden = !state.explanationOpen;
    find('review-branches').hidden = !state.explanationOpen;
    const position = reviewWorkspace.positionText(state);
    setReviewText('review-selection-context', position);
    if (!state.explanationOpen) return;
    const reason = find('reason');
    const explain = words[state.reason];
    reason.hidden = !explain;
    reason.dataset.kind = state.reason ?? '';
    reason.dataset.square = state.decisiveSquare ?? '';
    reason.textContent = explain ? explain(words.pieces[state.decisivePiece], state.decisiveSquare,
        words.pieces[state.capturedPiece], score.format(state.materialCp / 100)) : '';
    find('evaluation').hidden = Boolean(explain);
    find('evaluation').textContent = words.evaluation(score.format(state.playedPoints / 10),
        score.format(state.bestPoints / 10), state.depth);
    setReviewText('review-why-context', reviewWhyText(locale, position,
        reason.hidden ? find('evaluation').textContent.replaceAll(' · ', '. ') + '.' : reason.textContent));
    find('why-title').textContent = reviewWorkspace.moveLabel(state, state.ply);
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
            item.textContent = reviewWorkspace.moveLabel(state, ply);
            row.append(item);
            return row;
        });
        history.replaceChildren(...rows);
    }
    for (const row of history.children) {
        const item = row.firstElementChild;
        const ply = Number(item.dataset.ply);
        const label = reviewWorkspace.moveLabel(state, ply);
        if (item.textContent !== label) item.textContent = label;
        const position = reviewWorkspace.positionText(state, ply);
        if (item.getAttribute('aria-label') !== position) item.setAttribute('aria-label', position);
        item.tabIndex = ply === state.ply ? 0 : -1;
        if (ply === state.ply && !item.hasAttribute('aria-current')) item.setAttribute('aria-current', 'step');
        else if (ply !== state.ply && item.hasAttribute('aria-current')) item.removeAttribute('aria-current');
    }
}

function setReviewText(id, text) {
    const element = find(id);
    if (element.textContent !== text) element.textContent = text;
}

function render() {
    const container = find('review');
    const visibleReview = Boolean(view && (mode === 'study-review' || mode === 'archive-review' || mode === 'review'));
    const analysisReview = mode === 'study-review' && studyReturn?.route === 'analysis';
    const wasExplaining = document.body.classList.contains('review-explaining');
    const explaining = visibleReview && view.explanationOpen;
    if (explaining && !wasExplaining) reportScrollBeforeExplanation = find('review-details').scrollTop;
    container.hidden = !visibleReview;
    document.body.classList.toggle('review-active', visibleReview);
    document.body.classList.toggle('review-explaining', explaining);
    const homeVisible = mode === 'home';
    homeMenu.show(homeVisible, savedMatches);
    if (homeVisible) refreshStudyTotals();
    document.body.classList.toggle('home-active', homeVisible);
    find('review-close').hidden = !visibleReview || Boolean(archiveReviewId) || explaining;
    find('archive-review-back').hidden = !visibleReview || !archiveReviewId || explaining;
    find('close-why').hidden = !explaining;
    find('why-actions').hidden = !explaining || (!archiveReviewId && !analysisReview);
    find('reopen-review').hidden = !homeVisible || !archive;
    if (!visibleReview) return;
    find('title').textContent = explaining ? words['why-title'] : words.title;
    document.title = `Chess Lab · ${find('title').textContent}`;
    container.dataset.archiveId = archiveReviewId ?? '';
    container.dataset.ply = view.ply;
    container.dataset.branch = view.branch;
    container.dataset.step = view.step;
    const onLine = view.branch !== 'HISTORY';
    orientBoard(board, squares, view.blackBelow === true);
    drawBoard(board, squares, view);
    const stepping = onLine || explaining;
    find('previous').disabled = !(stepping ? view.linePrevious : view.previous);
    find('next').disabled = !(stepping ? view.lineNext : view.next);
    find('previous').setAttribute('aria-label', stepping ? words.linePrevious : words.previous);
    find('next').setAttribute('aria-label', stepping ? words.lineNext : words.next);
    const recommended = view.branch === 'RECOMMENDED';
    find('best-line').disabled = !view.recommended;
    find('best-line').setAttribute('aria-pressed', recommended);
    find('best-line').setAttribute('aria-label', recommended ? words.hideLine : words.bestLine);
    find('next-key').disabled = !view.nextKey;
    find('review-actions').hidden = !archiveReviewId && !analysisReview;
    find('continue-review').hidden = analysisReview ? !view.canContinueStudy : !archiveReviewId || !view.canContinue;
    drawExplanation(view);
    drawHistory(view);
    reviewWorkspace.render(view);
    if (wasExplaining && !explaining) find('review-details').scrollTop = reportScrollBeforeExplanation;
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
    renderMatchAutomation();
    scheduleBot();
    find('review').setAttribute('aria-busy', 'true');
    find('match').setAttribute('aria-busy', 'true');
    setStatus('');
    try {
        await work();
    } catch {
        setStatus(otherTabChanged ? words.changed : engineFailed ? words.engineFailed : failure);
    } finally {
        busy = false;
        renderMatchAutomation();
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
    archiveReviewId = undefined;
    view = state;
    render();
    return true;
}

function action(action, ply, focusIntent = 'toolbar') {
    reviewCommand('review-action', { action, ply }, focusIntent);
}

function focusReview(intent, initiator) {
    for (const candidate of reviewFocusCandidates(intent, initiator, view?.explanationOpen)) {
        const element = candidate === 'selected-history' ? history.querySelector('[aria-current="step"]') : find(candidate);
        if (!element || element.disabled || !element.getClientRects().length) continue;
        reviewWorkspace.revealFocus(element);
        if (document.activeElement !== element) element.focus({ preventScroll: true });
        if (document.activeElement === element) return candidate;
    }
    return null;
}

function announceReview(intent, focus) {
    if (reviewAnnouncementChannel(intent, focus) !== 'status') return;
    const text = intent === 'open-why' ? find('review-why-context').textContent
        : intent === 'graph' ? reviewWorkspace.graphAnnouncementText(view) : reviewWorkspace.announcementText(view);
    setReviewText('review-announcement', text);
}

function reviewCommand(type, fields, focusIntent) {
    if (!view) return;
    const intent = fields.action === 'OPEN_EXPLANATION' ? 'open-why' : fields.action === 'CLOSE_EXPLANATION' ? 'close-why' : focusIntent;
    const source = document.activeElement?.id;
    const startFocus = focusChange;
    void exclusive(async () => {
        const archiveId = archiveReviewId;
        const studyReview = mode === 'study-review';
        const response = await call(type, { ...fields, expectedRevision: view.revision, archiveId, studyReview });
        if (response.outcome === 'STALE') {
            if (studyReview) setStatus(words.failedAction);
            else if (archiveId) await restoreMatchDurable(words.failedAction);
            else await restoreDurable(words.failedAction);
            return;
        }
        if (response.outcome === 'APPLIED') {
            if (!await commitReviewAction(response, archiveId)) return;
            if (intent === 'open-why') reviewWorkspace.revealExplanation();
        } else if (response.outcome === 'UNAVAILABLE') {
            render();
        } else return;
        if (startFocus === focusChange) {
            if (type === 'review-graph-select') reviewWorkspace.revealSelected();
            if (document.hasFocus()) {
                const focus = focusReview(intent, source);
                if (response.outcome === 'APPLIED') announceReview(intent, focus);
            }
        }
    }, words.failedAction);
}

function commitReviewAction(response, archiveId) {
    if (mode === 'study-review') {
        view = response.view;
        render();
        return true;
    }
    return archiveId ? commitArchiveReview(response, archiveId) : commit(response.view, archive);
}

function renderMatch() {
    const container = find('match');
    container.hidden = mode !== matchRoute || !match || setupVisible;
    find('match-options-anchor').hidden = container.hidden;
    renderMatchAutomation();
    if (container.hidden) {
        matchMenu.close();
        if (resignation.open) resignation.close('cancel');
        return;
    }
    const isAi = match.mode === 'AI';
    find('open-profile').hidden = !isAi || match.readOnly;
    const browsing = match.viewPly < match.ply;
    container.dataset.ply = match.ply;
    container.dataset.viewPly = match.viewPly;
    container.dataset.result = match.result;
    container.dataset.mode = match.mode;
    container.dataset.slot = match.slot;
    container.dataset.matchId = match.archiveId ?? savedMatches.find(entry => entry.slot === match.slot)?.id ?? '';
    container.dataset.readOnly = match.readOnly;
    container.dataset.checkpoint = match.checkpoint;
    container.dataset.humanSide = match.humanSide;
    container.dataset.level = match.level;
    container.dataset.awaitingBot = match.awaitingBot;
    orientBoard(matchBoard, matchSquares, match.blackBelow);
    drawBoard(matchBoard, matchSquares, match);
    const result = browsing || match.result === 'IN_PROGRESS' ? words[match.sideToMove]
        : match.resigned ? words.resigned : match.result === 'AGREED_DRAW' ? words.agreedDraw : words[match.result] ?? words.DRAW;
    find('match-turn').textContent = match.manualAiTurn ? words.manualAiTurn
        : match.awaitingBot && failedBotRevision !== match.revision ? words.botTurn : result;
    find('match-profile').hidden = !isAi;
    if (isAi) {
        const summary = words.aiProfile(levels[match.level], words[`${match.humanSide.toLowerCase()}-choice`]);
        find('match-profile').textContent = homeMenu.name ? `${homeMenu.name} · ${summary}` : summary;
    }
    find('new-match').hidden = match.readOnly || !isAi && match.result === 'IN_PROGRESS';
    find('match-history').hidden = match.ply === 0;
    find('match-ply').textContent = `${match.viewPly}/${match.ply}`;
    find('match-previous').disabled = match.viewPly === 0;
    find('match-next').disabled = !browsing;
    find('continue-match').hidden = !match.canContinue;
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

function renderMatchAutomation() {
    const option = match?.automation;
    find('match-automatic-option').hidden = find('match').hidden || !option?.visible;
    matchAutomatic.checked = option?.checked === true;
    matchAutomatic.disabled = !option?.enabled || Boolean(analysis) || otherTabChanged || engineFailed;
    matchAutomatic.setAttribute('aria-disabled', String(matchAutomatic.disabled || busy));
}

function renderAnalysis() {
    const running = Boolean(analysis);
    find('analysis-progress').hidden = !running;
    find('cancel-analysis').hidden = !running;
    find('open-match-analysis').disabled = running || !match?.reviewCheckpoint;
    find('analyze').disabled = running || !match || match.awaitingBot || match.ply === 0;
    const reviewed = match?.archiveId && finishedMatches.some(entry => entry.id === match.archiveId && entry.reviewed);
    find('analyze').textContent = reviewed ? words.openCached : words.analyze;
    find('open-match-analysis').hidden = match?.mode === 'AI';
    find('analyze').hidden = match?.mode === 'AI';
    const available = match?.menu ?? {};
    for (const [action, id] of Object.entries(menuActions)) {
        const button = find(id);
        button.hidden = !Object.hasOwn(available, action);
        button.disabled = !available[action] || (action !== 'HOME' && action !== 'SAVED_GAMES' && (running || engineFailed || otherTabChanged));
    }
    find('new-match').disabled = running || Boolean(match?.awaitingBot);
    renderMatchAutomation();
}

function renderMatchList() {
    const list = find('match-list');
    list.hidden = !listRoutes.has(mode);
    if (list.hidden) return;
    const archived = mode === 'finished';
    const entries = archived ? finishedMatches : savedMatches;
    find('empty-list').hidden = entries.length > 0;
    find('empty-list').textContent = archived ? words.emptyFinished : words.emptySaved;
    find('add-matches').hidden = archived;
    const rows = entries.map(entry => {
        const item = document.createElement('li');
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'match-row';
        button.dataset.slot = entry.slot;
        button.dataset.id = entry.id;
        button.dataset.mode = entry.mode;
        button.dataset.result = entry.result;
        button.dataset.ply = entry.ply;
        const title = document.createElement('strong');
        const opponent = entry.mode === 'AI' ? `AI · ${levels[entry.level]}` : words['open-match'];
        title.textContent = archived ? opponent : `${entry.slot} · ${opponent}`;
        const details = document.createElement('span');
        details.className = 'match-subtitle';
        const outcome = entry.mode === 'AI' ? entry.status ?? entry.result : entry.result;
        const result = archived ? words[outcome] ?? words.DRAW : words.active;
        details.textContent = [words.moves(entry.ply), result, archived && entry.reviewed ? words.reviewed : ''].filter(Boolean).join(' · ');
        button.dataset.reviewed = Boolean(entry.reviewed);
        button.append(title, details);
        if (entry.timestamp > 0) {
            const date = new Date(entry.timestamp);
            const time = document.createElement('time');
            time.dateTime = date.toISOString();
            time.textContent = gameDate.format(date);
            button.append(time);
        }
        item.append(button);
        return item;
    });
    find('match-rows').replaceChildren(...rows);
}

function shownRouteHash() {
    if (mode === 'archive-review') return `#review/${encodeURIComponent(archiveReviewId)}`;
    if (mode === 'archive') return `#archive/${encodeURIComponent(match.archiveId)}`;
    return routeHashes[mode === 'study-review' ? studyReturn.route : mode];
}

function showMode(nextMode, addHistory = false) {
    if (nextMode !== mode) {
        if (mode === 'review') liveReviewOrigin = undefined;
        cancelAnalysis();
        studyAnalysis?.cancel();
        studyPanels[mode]?.close?.();
        studyGeneration++;
    }
    setReviewMenu(false);
    mode = nextMode;
    writeModeHash(window, shownRouteHash(), addHistory);
    const listing = listRoutes.has(mode);
    const title = { match: 'open-match', ai: 'open-ai', archive: 'open-finished', saved: 'open-saved', finished: 'open-finished' };
    const studying = Object.hasOwn(studyPanels, mode);
    find('title').textContent = mode === 'home' ? 'Chess Lab' : studying ? homeLabels[locale][studyRoutes[mode][0]] : words[title[mode] ?? 'title'];
    document.title = mode === 'home' ? 'Chess Lab' : `Chess Lab · ${find('title').textContent}`;
    find('return-menu').hidden = !listing && mode !== 'archive';
    find('archive-review-back').hidden = mode !== 'archive-review';
    find('ai-setup').hidden = mode !== 'ai' || !setupVisible;
    find('cancel-setup').hidden = !setupBackMode;
    document.body.classList.toggle('study-active', studying);
    for (const route of Object.keys(studyPanels)) find('study-' + route).hidden = route !== mode;
    renderMatch();
    renderMatchList();
    render();
    scheduleBot();
}

async function restoreMatchDurable(message) {
    if (otherTabChanged) return;
    const previous = match;
    try {
        let response = await call('ledger-open', { ledger: storedLedger, legacy: {} });
        if (previous?.archiveId || previous?.slot > 0) {
            const source = previous.readOnly || previous.slot === 0 ? { archiveId: previous.archiveId } : { slot: previous.slot };
            const opened = await call('match-open', source);
            if (opened.match) response = opened;
            if (opened.match && previous.viewPly < opened.match.ply) {
                response = await call('match-history', { expectedRevision: opened.match.revision, ply: previous.viewPly });
            }
        }
        adoptMatchState(response);
        if (mode === 'archive-review' && archiveReviewId) {
            const restored = await call('archive-review-open', { archiveId: archiveReviewId });
            if (restored.outcome !== 'OPENED' || !restored.view) throw new Error('Saved archive review unavailable');
            view = restored.view;
            render();
        } else if (mode === 'review' && archive && !archiveReviewId) {
            view = (await call('review-open', { archive })).view;
            render();
        }
        setStatus(message);
    } catch {
        stopWorker();
    }
}

function adoptMatchState(response) {
    savedMatches = response.saved;
    finishedMatches = response.finished;
    if (Object.hasOwn(response, 'match')) {
        match = response.match;
        matchRoute = match ? match.readOnly && match.slot === 0 ? 'archive' : match.mode === 'AI' ? 'ai' : 'match' : undefined;
        failedBotRevision = undefined;
        selectedSquare = undefined;
        promotionMoves = [];
    }
    renderMatch();
    renderMatchList();
    homeMenu.show(mode === 'home', savedMatches);
}

async function commitMatch(response, failure = words.matchSaveFailed) {
    if (typeof response.ledger !== 'string' || !Array.isArray(response.saved) || !Array.isArray(response.finished)) {
        throw new Error('Invalid game ledger response');
    }
    const writeLedger = () => {
        if (otherTabChanged || localStorage.getItem(ledgerKey) !== storedLedger) {
            otherTabChanged = true;
            setStatus(words.changed);
            return false;
        }
        if (response.ledger !== storedLedger) localStorage.setItem(ledgerKey, response.ledger);
        storedLedger = response.ledger;
        return true;
    };
    try {
        const committed = navigator.locks ? await navigator.locks.request(ledgerKey, writeLedger) : writeLedger();
        if (!committed) return false;
    } catch {
        await restoreMatchDurable(failure);
        return false;
    }
    adoptMatchState(response);
    return true;
}

async function enterMatch(options) {
    if (!ledgerReady) {
        setStatus(words.matchFailure);
        return;
    }
    await exclusive(async () => {
        let response = await call('match-open', options);
        if (!response.match && options.route === 'AI') {
            openAiSetup();
            return;
        }
        if (!response.match && options.route === 'LOCAL') response = await call('match-open', { setup: localSetup });
        if (!response.match) throw new Error('Requested match unavailable');
        if (await commitMatch(response)) {
            setupVisible = false;
            showMode(matchRoute);
        }
    }, words.matchFailure);
}

async function commitPlayedMatch(response, sourceMode) {
    const committed = await commitMatch(response);
    if (committed && mode === sourceMode && matchRoute !== sourceMode) showMode(matchRoute);
    return committed;
}

function play(move) {
    if (analysis || !canPlayMatch()) return;
    void exclusive(async () => {
        const sourceMode = mode;
        const response = await call('match-play', { move, expectedRevision: match.revision });
        if (response.outcome === 'APPLIED') await commitPlayedMatch(response, sourceMode);
        else await restoreMatchDurable(words.matchFailure);
    }, words.matchFailure);
}

function scheduleBot() {
    const ready = mode === 'ai' && matchRoute === 'ai' && !setupVisible && !matchMenu.isOpen() && !resignation.open &&
        match?.awaitingBot && match.viewPly === match.ply &&
        failedBotRevision !== match.revision && !busy && !analysis && !engineFailed && !otherTabChanged;
    scheduleBotReply(ready ? match : undefined);
}

async function runBotTurn(waiting, signal) {
    let move;
    try {
        move = await computeBotTurn(waiting.reviewCheckpoint, signal);
    } catch {
        if (signal.aborted || match !== waiting) return;
        failedBotRevision = waiting.revision;
        setStatus(words.botFailure);
        renderMatch();
        return;
    }
    if (signal.aborted || mode !== 'ai' || match !== waiting) return;
    await exclusive(async () => {
        try {
            const response = await call('match-bot-apply', { expectedRevision: waiting.revision, move });
            if (response.outcome !== 'APPLIED') throw new Error('Bot move unavailable at this revision');
            if (await commitPlayedMatch(response, 'ai')) return;
        } catch {
            await restoreMatchDurable(words.botFailure);
        }
        if (otherTabChanged || engineFailed) return;
        failedBotRevision = match?.revision;
        renderMatch();
    }, words.botFailure);
}

async function computeBotTurn(checkpoint, signal) {
    const job = new Worker(new URL('./engine-worker.mjs', import.meta.url), { type: 'module' });
    let cancel;
    try {
        return await new Promise((resolve, reject) => {
            const fail = () => reject(new Error(words.botFailure));
            cancel = () => resolve(undefined);
            signal.addEventListener('abort', cancel, { once: true });
            job.addEventListener('error', fail);
            job.addEventListener('messageerror', fail);
            job.addEventListener('message', ({ data }) => {
                if (data?.id !== 'bot-turn') return;
                if (data.error || typeof data.move !== 'string') fail();
                else resolve(data.move);
            });
            job.postMessage({ id: 'bot-turn', type: 'match-bot-compute', checkpoint });
        });
    } finally {
        if (cancel) signal.removeEventListener('abort', cancel);
        job.terminate();
    }
}

function canPlayMatch() {
    return match && !match.readOnly && !match.awaitingBot && match.viewPly === match.ply && match.result === 'IN_PROGRESS';
}

function chooseSquare(square) {
    if (!canPlayMatch() || busy || analysis || otherTabChanged || engineFailed) return;
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

function analyzeMatch(fromMenu = false) {
    if (!match || busy || analysis || otherTabChanged || engineFailed || !fromMenu && match.awaitingBot) return;
    if (match.ply === 0) {
        if (match.readOnly) setStatus(words.reviewEmpty);
        return;
    }
    if (match.readOnly && match.archiveId) {
        void openArchiveReview(match.archiveId, match);
    } else {
        runReviewAnalysis(match, async source => {
            const response = await call('review-open', { archive: source });
            if (await commit(response.view, source)) {
                if (fromMenu) liveReviewOrigin = match;
                showMode('review', true);
            }
        });
    }
}

async function commitArchiveReview(response, archiveId) {
    if (!response.view) throw new Error('Archive review has no view');
    if (!await commitMatch(response, words.saveFailed)) return false;
    archiveReviewId = archiveId;
    view = response.view;
    render();
    return true;
}

async function openArchiveReview(archiveId, snapshot) {
    let needsAnalysis = false;
    let opened = false;
    let rated = false;
    await exclusive(async () => {
        const response = await call('archive-review-open', { archiveId });
        if (response.outcome === 'OPENED') {
            opened = await commitArchiveReview(response, archiveId);
            if (opened) {
                showMode('archive-review');
                revealRestoredReview(view);
            }
        } else if (response.outcome === 'NEEDS_ANALYSIS') {
            if (snapshot && response.checkpoint !== snapshot.reviewCheckpoint) throw new Error('Archive checkpoint changed');
            needsAnalysis = true;
            rated = response.rated === true;
        } else throw new Error('Archived game not available');
    }, words.badArchive);
    if (!needsAnalysis) {
        if (!snapshot && !opened && !otherTabChanged) showMode('finished');
        return;
    }
    if (!snapshot) {
        await enterMatch({ archiveId });
    } else if (mode === 'archive' && match === snapshot) {
        runReviewAnalysis(snapshot, async source => {
            const response = await call('archive-review-save', { archiveId, checkpoint: source.checkpoint, review: source.review });
            if (response.outcome !== 'OPENED') throw new Error('Archive no longer accepts analysis');
            if (await commitArchiveReview(response, archiveId)) showMode('archive-review');
        }, rated);
    }
}

function revealRestoredReview(snapshot) {
    requestAnimationFrame(() => {
        if (view !== snapshot || find('review').hidden) return;
        if (snapshot.explanationOpen) reviewWorkspace.revealExplanation();
        else if (snapshot.atEntry === false && snapshot.branch === 'HISTORY') reviewWorkspace.revealSelected();
    });
}

function runReviewAnalysis(snapshot, openComputedReview, rated = false) {
    const route = matchRoute;
    const job = new Worker(new URL('./engine-worker.mjs', import.meta.url), { type: 'module' });
    analysis = job;
    scheduleBot();
    const id = String(++serial);
    const progress = find('progress');
    progress.removeAttribute('value');
    renderAnalysis();
    setStatus('');
    const fail = () => {
        if (analysis !== job) return;
        cancelAnalysis();
        setStatus(words.analysisFailure);
        scheduleBot();
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
            await openComputedReview(source);
        }, words.analysisFailure);
    });
    job.postMessage({ id, type: 'review-analyze', checkpoint: snapshot.reviewCheckpoint, rated });
}

function showMainMenu(addHistory = false) {
    setupVisible = false;
    liveReviewOrigin = undefined;
    if (archiveReviewId) {
        archiveReviewId = undefined;
        view = undefined;
    }
    showMode('home', addHistory);
}

async function openStandaloneReview(addHistory = false) {
    if (!archive) {
        showMainMenu();
        return;
    }
    await exclusive(async () => {
        if (!view || archiveReviewId) view = (await call('review-open', { archive })).view;
        archiveReviewId = undefined;
        showMode('review', addHistory);
        revealRestoredReview(view);
    }, words.badArchive);
}

function openAiSetup() {
    const previousMode = mode ?? 'home';
    setupBackMode = previousMode === 'home' || listRoutes.has(previousMode) || previousMode === 'ai' ? previousMode : undefined;
    find('ai-setup').reset();
    setupVisible = true;
    showMode('ai', true);
}

function refreshStudyTotals() {
    if (!studyTotalsDirty || !studyPanels.quests.summary) return;
    studyTotalsDirty = false;
    const totals = find('home-study-totals');
    const generation = studyGeneration;
    Promise.resolve().then(() => studyPanels.quests.summary()).then(text => {
        if (generation !== studyGeneration) return;
        totals.textContent = text ?? '';
        totals.hidden = !text;
    }).catch(() => { if (generation === studyGeneration) totals.hidden = true; });
}

async function openStudy(route, { resume = false, addHistory = false, entry, restore = false } = {}) {
    showMode(route, addHistory);
    setStatus('');
    const generation = studyGeneration;
    try {
        if (restore) await studyPanels[route].restore();
        else await studyPanels[route].open(route === 'quests' ? { resume } : entry);
    } catch {
        if (generation === studyGeneration && mode === route) setStatus(words.failedAction);
    }
}

function leaveStudy() {
    const route = mode;
    if (route === 'analysis' && contextualAnalysis.back()) return;
    studyTotalsDirty = true;
    showMainMenu(true);
    if (studyRoutes[route]) find(studyRoutes[route][0]).focus();
}

function computeStudyReview(checkpoint) {
    const job = new Worker(new URL('./engine-worker.mjs', import.meta.url), { type: 'module' });
    const id = String(++serial);
    const progress = find('study-progress');
    progress.removeAttribute('value');
    find('study-review-progress').hidden = false;
    return new Promise((resolve, reject) => {
        const finish = (error, archive) => {
            if (studyAnalysis?.job !== job) return;
            studyAnalysis = undefined;
            find('study-review-progress').hidden = true;
            job.terminate();
            if (error) reject(error);
            else resolve(archive);
        };
        studyAnalysis = { job, cancel: () => finish() };
        job.addEventListener('error', () => finish(new Error(words.analysisFailure)));
        job.addEventListener('messageerror', () => finish(new Error(words.analysisFailure)));
        job.addEventListener('message', ({ data }) => {
            if (data.id !== id || studyAnalysis?.job !== job) return;
            if (data.error) finish(new Error(words.analysisFailure));
            else if (Number.isInteger(data.progress) && Number.isInteger(data.total) && data.total > 0) {
                progress.max = data.total;
                progress.value = data.progress;
            } else finish(null, data.archive);
        });
        job.postMessage({ id, type: 'review-analyze', checkpoint, rated: false });
    });
}

async function openStudyReview(checkpoint, blackBelow, selectPly = -1) {
    if (!Object.hasOwn(studyPanels, mode) || studyReviewOpening || busy || engineFailed) return false;
    studyReviewOpening = true;
    const route = mode;
    const generation = studyGeneration;
    const opener = document.activeElement;
    try {
        const result = await computeStudyReview(checkpoint);
        if (!result || generation !== studyGeneration) return false;
        const source = normalizeArchive(result);
        if (source.checkpoint !== checkpoint) throw new Error('Analysis checkpoint differs from the study');
        const response = await call('study-review-open', { archive: source, blackBelow, ply: selectPly });
        if (generation !== studyGeneration) return false;
        studyReturn = { route, opener, view, archiveReviewId };
        view = response.view;
        archiveReviewId = undefined;
        showMode('study-review');
        revealRestoredReview(view);
        find('review-close').focus({ preventScroll: true });
        return true;
    } catch {
        if (generation === studyGeneration) setStatus(words.analysisFailure);
        return false;
    } finally {
        studyReviewOpening = false;
    }
}

function restoreStudyReviewOrigin() {
    const previous = studyReturn;
    if (!previous) return;
    studyReturn = undefined;
    view = previous.view;
    archiveReviewId = previous.archiveReviewId;
    return previous;
}

async function closeStudyReview() {
    const previous = restoreStudyReviewOrigin();
    if (!previous) return;
    await openStudy(previous.route, { restore: previous.route === 'analysis' });
    if (mode === previous.route && previous.opener?.isConnected) previous.opener.focus({ preventScroll: true });
}

async function continueStudyGame(checkpoint, automatic, blackBelow) {
    if (!Object.hasOwn(studyPanels, mode) || !ledgerReady) return false;
    let continued = false;
    await exclusive(async () => {
        const response = await call('study-match-continue', { checkpoint, automatic, blackBelow });
        if (response.outcome === 'NO_FREE_SLOT') setStatus(words.noFreeSlot);
        else if (response.outcome === 'SAVE_FAILED') setStatus(words.matchSaveFailed);
        else if (response.outcome === 'APPLIED' && await commitMatch(response)) {
            contextualAnalysis.discard();
            setupVisible = false;
            showMode(matchRoute);
            continued = true;
        }
    }, words.matchFailure);
    return continued;
}

for (const [route, [id]] of Object.entries(studyRoutes)) {
    find(id).addEventListener('click', () => { if (!busy) void openStudy(route, { addHistory: true }); });
}

find('open-match').addEventListener('click', () => {
    void enterMatch({ route: 'LOCAL' });
});
find('open-ai').addEventListener('click', () => {
    openAiSetup();
});

matchOptions.addEventListener('click', event => {
    if (event.target.closest('button[type="button"]')) matchMenu.close(true);
}, true);
find('menu-saved').addEventListener('click', () => { if (!busy) showMode('saved', true); });
find('menu-analysis').addEventListener('click', () => {
    if (mode === matchRoute && !busy && !analysis && !engineFailed) contextualAnalysis.open(mode, matchOptionsButton);
});
find('menu-review').addEventListener('click', () => analyzeMatch(true));
find('menu-draw').addEventListener('click', () => void decideMatch('OFFER_DRAW', match?.revision));
find('menu-resign').addEventListener('click', () => {
    if (busy || !match?.menu.RESIGN) return;
    resignationRevision = match.revision;
    resignation.returnValue = '';
    resignation.showModal();
    scheduleBot();
});
resignation.addEventListener('close', () => {
    if (resignation.returnValue === 'confirm') void decideMatch('RESIGN', resignationRevision);
    if (!find('match-options-anchor').hidden) matchOptionsButton.focus({ preventScroll: true });
    scheduleBot();
});

async function decideMatch(action, revision) {
    if (busy || match?.revision !== revision || !match?.menu[action] || mode !== matchRoute) return;
    await exclusive(async () => {
        const sourceMode = mode;
        const response = await call('match-decision', { expectedRevision: revision, action });
        if (response.outcome === 'APPLIED') await commitPlayedMatch(response, sourceMode);
        else if (response.outcome === 'DECLINED') setStatus(words.drawDeclined);
        else await restoreMatchDurable(words.matchSaveFailed);
    }, words.matchSaveFailed);
}
find('flip-match').addEventListener('click', () => {
    if (!match || mode !== matchRoute || setupVisible || analysis) return;
    void exclusive(async () => {
        const waitingForRetry = failedBotRevision === match.revision;
        const response = await call('match-flip', { expectedRevision: match.revision });
        if (response.outcome === 'APPLIED') {
            if (await commitMatch(response) && waitingForRetry) {
                failedBotRevision = match.revision;
                renderMatch();
            }
        } else if (response.outcome === 'STALE') await restoreMatchDurable(words.matchFailure);
        else setStatus(response.outcome === 'SAVE_FAILED' ? words.matchSaveFailed : words.matchFailure);
    }, words.matchFailure);
});

matchAutomatic.addEventListener('change', () => {
    if (busy || analysis || otherTabChanged || engineFailed || !match?.automation.enabled || mode !== matchRoute) {
        renderMatchAutomation();
        return;
    }
    const automatic = matchAutomatic.checked;
    void exclusive(async () => {
        const response = await call('match-automation', { expectedRevision: match.revision, automatic });
        if (response.outcome === 'APPLIED') await commitMatch(response, words.automationSaveFailed);
        else if (response.outcome === 'STALE') await restoreMatchDurable(words.automationSaveFailed);
        else if (response.outcome === 'SAVE_FAILED') setStatus(words.automationSaveFailed);
    }, words.automationSaveFailed);
});

find('open-review').addEventListener('click', () => { if (!busy) showMainMenu(); });
find('open-saved').addEventListener('click', () => showMode('saved', true));
find('open-finished').addEventListener('click', () => showMode('finished', true));
find('return-menu').addEventListener('click', () => {
    if (busy) return;
    if (mode === 'archive') showMode('finished');
    else showMainMenu();
});
find('archive-review-back').addEventListener('click', () => {
    if (archiveReviewId) void enterMatch({ archiveId: archiveReviewId });
});
find('review-close').addEventListener('click', () => {
    if (busy) return;
    if (mode === 'study-review') {
        void closeStudyReview();
        return;
    }
    if (liveReviewOrigin && liveReviewOrigin === match) {
        liveReviewOrigin = undefined;
        showMode(matchRoute);
        matchOptionsButton.focus({ preventScroll: true });
    } else showMainMenu();
});
find('reopen-review').addEventListener('click', () => { void openStandaloneReview(true); });
find('match-rows').addEventListener('click', event => {
    const row = event.target.closest('button.match-row');
    if (!row) return;
    void enterMatch(mode === 'finished' ? { archiveId: row.dataset.id } : { slot: Number(row.dataset.slot) });
});
find('new-local').addEventListener('click', () => void enterMatch({ setup: localSetup }));
find('new-ai').addEventListener('click', () => {
    if (!busy) openAiSetup();
});
find('new-match').addEventListener('click', () => {
    if (busy || analysis || !match || match.awaitingBot) return;
    if (matchRoute === 'ai') openAiSetup();
    else if (match.result !== 'IN_PROGRESS') void enterMatch({ setup: localSetup });
});
find('ai-setup').addEventListener('submit', event => {
    event.preventDefault();
    if (busy || mode !== 'ai') return;
    void enterMatch({ setup: { mode: 'AI', humanSide: find('ai-side').value, level: find('ai-level').value, analysis: find('ai-analysis').checked } });
});
find('cancel-setup').addEventListener('click', () => {
    if (busy || !setupBackMode) return;
    setupVisible = false;
    showMode(setupBackMode);
});
find('retry-bot').addEventListener('click', () => {
    if (failedBotRevision !== match?.revision) return;
    failedBotRevision = undefined;
    setStatus('');
    renderMatch();
    scheduleBot();
});
find('analyze').addEventListener('click', () => analyzeMatch());
find('open-match-analysis').addEventListener('click', event => {
    if (mode === matchRoute && !busy && !analysis && !engineFailed && !setupVisible) contextualAnalysis.open(mode, event.currentTarget);
});
find('cancel-analysis').addEventListener('click', () => {
    cancelAnalysis();
    scheduleBot();
});
find('promote').addEventListener('change', event => {
    if (promotionMoves.includes(event.target.value)) play(event.target.value);
});

function browseMatch(delta) {
    if (!match || analysis) return;
    void exclusive(async () => {
        const response = await call('match-history', { expectedRevision: match.revision, ply: match.viewPly + delta });
        if (response.outcome === 'APPLIED') await commitMatch(response);
        else if (response.outcome === 'STALE') await restoreMatchDurable(words.matchFailure);
    }, words.matchFailure);
}

find('match-previous').addEventListener('click', () => browseMatch(-1));
find('match-next').addEventListener('click', () => browseMatch(1));
const continueDialog = find('continue-dialog');
let continuation;

function confirmContinuation(context) {
    continuation = context;
    find('continue-description').textContent = (context.readOnly ? words.continueArchive : words.continueActive)(context.ply);
    find('automatic-option').hidden = !context.usesAi;
    find('continue-automatic').checked = context.automatic;
    continueDialog.returnValue = '';
    continueDialog.showModal();
}

find('continue-match').addEventListener('click', () => {
    if (!match?.canContinue || busy || analysis) return;
    confirmContinuation({ revision: match.revision, ply: match.viewPly, readOnly: match.readOnly,
        usesAi: match.mode === 'AI', automatic: match.automatic !== false });
});
find('continue-review').addEventListener('click', () => {
    if (mode === 'study-review' && studyReturn?.route === 'analysis' && view?.canContinueStudy && !busy) {
        const ply = view.ply;
        setReviewMenu(false);
        void closeStudyReview().then(() => mode === 'analysis' ? studyPanels.analysis.continueAt(ply) : null);
        return;
    }
    if (mode !== 'archive-review' || !archiveReviewId || !view?.canContinue || busy) return;
    setReviewMenu(false);
    const usesAi = view.automaticContinuation === true;
    confirmContinuation({ archiveId: archiveReviewId, revision: view.revision, ply: view.ply, readOnly: true,
        usesAi, automatic: usesAi });
});
continueDialog.addEventListener('close', () => {
    const context = continuation;
    continuation = undefined;
    if (continueDialog.returnValue !== 'continue' || !context) return;
    const current = context.archiveId ? mode === 'archive-review' && archiveReviewId === context.archiveId && view?.canContinue &&
        view.revision === context.revision : mode === matchRoute && match?.canContinue && match.revision === context.revision;
    if (!current) return;
    void exclusive(async () => {
        const type = context.archiveId ? 'archive-review-continue' : 'match-continue';
        let response;
        try {
            response = await call(type, { archiveId: context.archiveId, expectedRevision: context.revision,
                automatic: context.usesAi && find('continue-automatic').checked });
        } catch (failure) {
            if (!context.archiveId) throw failure;
            await restoreMatchDurable(words.matchFailure);
            return;
        }
        if (response.outcome === 'NO_FREE_SLOT') setStatus(words.noFreeSlot);
        else if (response.outcome === 'APPLIED' && await commitMatch(response)) showMode(matchRoute);
        else if (response.outcome === 'STALE') await restoreMatchDurable(words.matchFailure);
        else if (response.outcome === 'SAVE_FAILED') setStatus(words.matchSaveFailed);
        else if (response.outcome === 'UNAVAILABLE') setStatus(words.matchFailure);
    }, words.matchFailure);
});

bindBoardSelection(matchBoard, chooseSquare);

function setReviewMenu(open) {
    find('review-menu').hidden = !open;
    find('review-actions').setAttribute('aria-expanded', open);
    find('why-actions').setAttribute('aria-expanded', open);
}

find('review-actions').addEventListener('click', () => {
    if (!busy) setReviewMenu(find('review-menu').hidden);
});
find('why-actions').addEventListener('click', () => {
    if (!busy) setReviewMenu(find('review-menu').hidden);
});
find('review-on-board').addEventListener('click', () => {
    if (mode === 'study-review' && studyReturn?.route === 'analysis' && view && !busy) {
        const ply = view.ply;
        setReviewMenu(false);
        void closeStudyReview().then(() => mode === 'analysis' ? studyPanels.analysis.seek(ply) : null);
        return;
    }
    if (!archiveReviewId || !view) return;
    setReviewMenu(false);
    void exclusive(async () => {
        let response = await call('match-open', { archiveId: archiveReviewId });
        if (!response.match) throw new Error('Requested match unavailable');
        if (response.match.viewPly !== view.ply) {
            response = await call('match-history', { expectedRevision: response.match.revision, ply: view.ply });
            if (response.outcome !== 'APPLIED') throw new Error('Requested match position unavailable');
        }
        if (await commitMatch(response)) showMode(matchRoute);
    }, words.matchFailure);
});
document.addEventListener('pointerdown', event => {
    if (!find('review-menu').hidden && !event.target.closest('#review-actions, #why-actions, #review-menu')) setReviewMenu(false);
});
document.addEventListener('keydown', event => {
    if (event.key !== 'Escape' || event.defaultPrevented || document.querySelector('dialog[open]')) return;
    if (!find('review-menu').hidden) {
        event.preventDefault();
        setReviewMenu(false);
        find(view?.explanationOpen ? 'why-actions' : 'review-actions').focus();
    } else if (!find('review').hidden && view?.explanationOpen) {
        event.preventDefault();
        action('CLOSE_EXPLANATION');
    } else if (mode === 'study-review' && !busy) {
        event.preventDefault();
        void closeStudyReview();
    } else if (studyPanels[mode]?.back) {
        event.preventDefault();
        void studyPanels[mode].back();
    }
});
find('previous').addEventListener('click', () => action('PREVIOUS'));
find('next').addEventListener('click', () => action('NEXT'));
find('best-line').addEventListener('click', () => action('TOGGLE_BEST'));
find('next-key').addEventListener('click', () => action('NEXT_KEY'));
find('why').addEventListener('click', () => action(view.explanationOpen ? 'CLOSE_EXPLANATION' : 'OPEN_EXPLANATION'));
find('close-why').addEventListener('click', () => action('CLOSE_EXPLANATION'));
find('played').addEventListener('click', () => action('SELECT_PLAYED'));
find('recommended').addEventListener('click', () => action('SELECT_RECOMMENDED'));
find('seek').addEventListener('keydown', event => {
    if (!view || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    const target = sliderPlyForKey(event.key, view.ply, view.totalPlies);
    if (target === null) return;
    event.preventDefault();
    if (target !== view.ply) action('SEEK', target, 'graph');
});
find('seek').addEventListener('change', event => {
    if (busy || otherTabChanged || engineFailed) {
        if (view) event.target.value = view.ply;
    } else action('SEEK', Number(event.target.value), 'graph');
});
history.addEventListener('click', event => {
    const row = event.target.closest('button[data-ply]');
    if (row) action('SEEK', Number(row.dataset.ply), 'history');
});
history.addEventListener('keydown', event => {
    const row = event.target.closest('button[data-ply]');
    if (!row || !view || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    const from = Number(row.dataset.ply);
    const target = historyPlyForKey(event.key, from, view.totalPlies);
    if (target === null) return;
    event.preventDefault();
    if (target !== from) action('SEEK', target, 'history');
});
find('archive').addEventListener('change', event => {
    const file = event.target.files[0];
    event.target.value = '';
    if (!file) return;
    void exclusive(async () => {
        const imported = normalizeArchive(JSON.parse(await file.text()));
        const response = await call('review-open', { archive: imported });
        if (await commit(response.view, imported)) showMode('review', true);
    }, words.badArchive);
});
addEventListener('storage', event => {
    if (event.storageArea !== localStorage) return;
    if ((event.key === storageKey && event.newValue !== stored) || (event.key === ledgerKey && event.newValue !== storedLedger)
        || (event.key === null && (stored !== null || storedLedger !== null))) {
        otherTabChanged = true;
        cancelAnalysis();
        scheduleBot();
        setStatus(words.changed);
    }
});

await exclusive(async () => {
    stored = localStorage.getItem(storageKey);
    storedLedger = localStorage.getItem(ledgerKey);
    const legacy = storedLedger === null
        ? Object.fromEntries(Object.entries(legacyMatchKeys).map(([mode, key]) => [mode, localStorage.getItem(key)])) : {};
    try {
        const response = await call('ledger-open', { ledger: storedLedger, legacy });
        pieceArtwork = response.pieceArt;
        ledgerReady = await commitMatch(response);
    } catch {
        setStatus(words.matchFailure);
    }
    if (stored === null) return;
    const restored = normalizeArchive(JSON.parse(stored));
    archive = restored;
    if (ledgerReady) {
        const migrated = await call('archive-review-migrate', { archive: restored });
        await commitMatch(migrated, words.saveFailed);
    }
}, words.badArchive);
const activeStudyRoute = () => studyReturn?.route ?? (Object.hasOwn(studyRoutes, mode) ? mode : undefined);

async function openLocatedMode(hash) {
    const previousStudy = activeStudyRoute();
    restoreStudyReviewOrigin();
    if (contextualAnalysis.restore(hash)) return;
    const { mode: destination, archiveId } = routeForHash(hash);
    if (Object.hasOwn(studyRoutes, destination)) await openStudy(destination, { resume: destination === 'quests' });
    else if (destination === 'match') await enterMatch({ route: 'LOCAL' });
    else if (destination === 'ai') await enterMatch({ route: 'AI' });
    else if (listRoutes.has(destination)) showMode(destination);
    else if (destination === 'review') await openStandaloneReview();
    else if (destination === 'archive-review') await openArchiveReview(archiveId);
    else if (destination === 'archive') await enterMatch({ archiveId });
    else {
        if (previousStudy) studyTotalsDirty = true;
        showMainMenu();
        if (previousStudy) find(studyRoutes[previousStudy][0]).focus();
    }
}

watchRouteHistory(window, shownRouteHash, openLocatedMode);
await openLocatedMode(location.hash);
if (!mode) showMainMenu();
