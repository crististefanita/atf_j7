import { backLabel } from './screen-chrome.mjs';
import { attachOptionsMenu } from './options-menu.mjs';
import { rememberBoardSize } from './study-board-size.mjs';
import { renderLinePosition } from './line-navigation.mjs';

const progressKey = 'chesslab.puzzle-progress.v1';
const defenseDelay = 650;
const pieceKinds = { p: 'PAWN', n: 'KNIGHT', b: 'BISHOP', r: 'ROOK', q: 'QUEEN', k: 'KING' };

export const questChapterLabels = {
    en: {
        'mate-one': 'Mate in 1', 'mate-two': 'Mate in 2', 'mate-three': 'Mate in 3',
        tactics: 'Tactics', training: 'Tactical training', intermezzo: 'In-between move',
    },
    ro: {
        'mate-one': 'Mat în 1', 'mate-two': 'Mat în 2', 'mate-three': 'Mat în 3',
        tactics: 'Tactică', training: 'Antrenament tactic', intermezzo: 'Mutarea intermediară',
    },
};

export const questLessons = {
    en: {
        FORK: 'Attack two targets at once. Watch which one the defender can save.',
        DEFLECTION: 'Draw a defender away from the square or piece it was protecting.',
        PIN: 'Exploit the piece that cannot move without exposing its king or a more valuable piece.',
        INTERMEZZO: 'Play a forcing move before the expected reply, then return to the original exchange.',
        CLEARANCE: 'Move a piece out of the way to open a square or line for another piece.',
        DISCOVERY: 'Move one piece to uncover an attack from the piece behind it.',
        PROMOTION: 'Follow the pawn to its new piece and see what the promotion changes.',
        REMOVE_DEFENDER: 'Capture the piece guarding your real target before taking that target.',
        DEFENSE: 'Answer the immediate threat while keeping your own tactical opportunity.',
        QUIET_THREAT: 'Prepare a forcing threat with a move that gives no check and takes no piece.',
        XRAY: 'Look beyond the first piece on the line. Its move can expose a second target.',
        SKEWER: 'Force the more valuable piece to move, then take the piece behind it.',
        SACRIFICE: 'Give up material temporarily. Follow the replies to see what you gain in return.',
        TRAPPED_PIECE: 'Take away escape squares before capturing the trapped piece.',
    },
    ro: {
        FORK: 'Atacă două ținte simultan. Urmărește pe care o poate salva adversarul.',
        DEFLECTION: 'Abate apărătorul de la câmpul sau piesa pe care o proteja.',
        PIN: 'Profită de piesa care nu poate muta fără să expună regele sau o piesă mai valoroasă.',
        INTERMEZZO: 'Joacă o mutare forțantă înaintea răspunsului așteptat, apoi revino la schimbul inițial.',
        CLEARANCE: 'Eliberează un câmp sau o linie pentru o altă piesă.',
        DISCOVERY: 'Mută o piesă pentru a descoperi atacul celei din spatele ei.',
        PROMOTION: 'Urmărește pionul până la promovare și vezi ce schimbă noua piesă.',
        REMOVE_DEFENDER: 'Capturează mai întâi piesa care apără ținta reală.',
        DEFENSE: 'Răspunde la amenințarea imediată și păstrează propria șansă tactică.',
        QUIET_THREAT: 'Pregătește o amenințare forțantă printr-o mutare fără șah și fără captură.',
        XRAY: 'Privește dincolo de prima piesă de pe linie. Mutarea ei poate expune o a doua țintă.',
        SKEWER: 'Forțează piesa mai valoroasă să se retragă, apoi capturează piesa din spatele ei.',
        SACRIFICE: 'Cedează material temporar. Urmărește replicile ca să vezi ce câștigi în schimb.',
        TRAPPED_PIECE: 'Taie căile de scăpare înainte să capturezi piesa prinsă.',
    },
};

const labels = {
    en: {
        title: 'Puzzles', home: 'Main menu', menu: 'Puzzle options', flip: 'Flip board', close: 'Close', promote: 'Promote to',
        hint: 'Hint', rank: (index, count) => 'Puzzle ' + index + ' of ' + count,
        points: (total, earned) => (earned ? '+' + earned + ' · ' : '') + total + ' points', series: value => value + ' in a row',
        solvedCount: (solved, count) => 'Solved · ' + solved + '/' + count,
        previous: 'Previous', next: 'Next', first: 'First unsolved', whyMate: 'Why mate?', whyTactic: 'Tactic explained',
        showPiece: 'Show piece', showMove: 'Show move', explain: 'Explain', wait: 'Waiting', pending: 'Opponent to reply…',
        mate: 'Find the mate', tactic: 'Find the tactic that wins material.', solved: 'Checkmate · Solved',
        tacticSolved: value => 'Solved · ' + value, streak: value => 'Streak ' + value,
        reply: (move, stage, count) => 'Defense ' + move + ' · move ' + stage + '/' + count,
        replyMate: (move, remaining) => 'Defense ' + move + (remaining === 1 ? ' · find mate' : ' · two moves to mate'),
        tryMate: ['That move does not give checkmate. Try again.', 'That move does not force mate in two. Try again.',
            'That move does not force mate in three. Try again.'],
        tryTactic: 'There is a better move. Try again.', stalemate: 'Stalemate. The opposing king is safe.',
        check: line => 'Check. Opponent line: ' + line, gain: n => '+' + n + ' in pawn units',
        opponentMate: 'opponent mates', ownMate: 'you can mate', draw: 'stalemate',
        equal: (n, move) => n + ' equal replies, including ' + move, final: 'Final move: win material now.',
        alternative: move => 'Why is ' + move + ' weaker?', alternateReplay: move => 'Line after ' + move,
        attemptDetails: 'Move details',
        replay: 'Puzzle replay', threat: 'Danger', white: 'White', black: 'Black', turn: side => side + ' to move',
        tally: ({ solved, total, points }) => solved + '/' + total + ' solved · ' + points + ' pts', overall: 'Overall',
        nextMilestone: value => 'Next goal · ' + value, complete: 'Collection complete',
        milestoneReached: (solved, next) => solved + ' reached · next ' + next,
        saveFailed: 'Progress could not be saved. Try again.', loadFailed: 'This puzzle could not be loaded. Try again.',
        hintDescription: (piece, square) => piece + ' on ' + square, suggested: move => 'Suggested move · ' + move,
        hintPiece: (square, side, piece) => 'Move the ' + side + ' ' + piece + ' on ' + square + '.',
        fullHint: (turn, piece, square) => turn + '. ' + piece + ' Destination ' + square + '.',
        hintPromotion: (move, piece) => move + ', promote to ' + piece,
        intermezzo: 'Find the forcing move before the recapture.',
        materialGain: value => 'Material gain: +' + value + ' pawn units',
        materialLoss: value => 'Material spent: −' + value + ' pawn units', materialEqual: 'No material change yet',
        materialFloor: value => 'Minimum after one opponent reply: ' + value + ' pawn units from the start',
        capture: (value, square) => 'Captured ' + value + ' on ' + square,
        matingLine: 'Mating line', mateEnd: 'Checkmate', replayStart: 'Starting position', keyOnly: 'Only the key move is shown here.',
        inCheck: side => side + ' to move · in check', defenseStudy: 'Defense study',
        evaluated: (depth, side, estimate) => 'Stockfish 19 · Depth ' + depth + ' after the key move · ' + side + ': ' + estimate,
        estimate: value => 'position estimate ' + value + ' in pawn units',
        studyMate: value => 'mate in ' + Math.abs(value) + ' for the ' + (value > 0 ? 'solver' : 'defender'),
        materialStep: value => 'Material on this board: ' + value + ' pawn units from the start',
        materialOne: value => 'Material after this move: ' + value + ' pawn units',
        provenMate: square => 'Proven checkmate · ' + square,
        provenCapture: (square, gain) => 'Capture on ' + square + ' · ' + gain + ' in pawn units after any legal reply',
        mateKing: (square, pieces) => 'King on ' + square + ' in check by ' + pieces + '.',
        mateAttack: (square, pieces) => square + ' is attacked by ' + pieces + '.',
        mateBlock: (square, piece) => square + ' is occupied by the defending ' + piece + '.',
        patterns: { DIRECT: 'The moved piece checks the king, which has no legal defense.',
            DISCOVERED: 'The move uncovers another piece’s attack on the king, leaving no legal defense.',
            CASTLING: 'Castling moves the rook to the square from which it delivers mate.',
            EN_PASSANT: 'The en passant capture removes the opposing pawn and leaves the king without a legal defense.',
            PROMOTION: 'The promoted piece is the required choice for mate.' },
        pieces: { PAWN: 'Pawn', KNIGHT: 'Knight', BISHOP: 'Bishop', ROOK: 'Rook', QUEEN: 'Queen', KING: 'King' },
    },
    ro: {
        title: 'Probleme', home: 'Meniu principal', menu: 'Opțiuni puzzle', flip: 'Întoarce tabla', close: 'Închide', promote: 'Promovează în',
        hint: 'Indiciu', rank: (index, count) => 'Problema ' + index + ' din ' + count,
        points: (total, earned) => (earned ? '+' + earned + ' · ' : '') + total + ' puncte', series: value => value + ' la rând',
        solvedCount: (solved, count) => 'Rezolvate · ' + solved + '/' + count,
        previous: 'Precedentă', next: 'Următoarea', first: 'Prima nerezolvată', whyMate: 'De ce e mat?', whyTactic: 'Explicația tacticii',
        showPiece: 'Arată piesa', showMove: 'Arată mutarea', explain: 'Explică', wait: 'Așteaptă', pending: 'Răspunde adversarul…',
        mate: 'Găsește matul', tactic: 'Găsește mutarea care câștigă cel mai mult material.', solved: 'Șah mat · Rezolvat',
        tacticSolved: value => 'Rezolvat · ' + value, streak: value => 'Serie ' + value,
        reply: (move, stage, count) => 'Apărare ' + move + ' · mutarea ' + stage + '/' + count,
        replyMate: (move, remaining) => 'Apărare ' + move + (remaining === 1 ? ' · găsește matul' : ' · două mutări până la mat'),
        tryMate: ['Mutarea nu dă mat. Încearcă din nou.', 'Mutarea nu forțează matul în două. Încearcă din nou.',
            'Mutarea nu forțează matul în trei. Încearcă din nou.'],
        tryTactic: 'Există o mutare mai bună. Încearcă din nou.', stalemate: 'Pat. Regele advers este în siguranță.',
        check: line => 'Șah. Linia adversarului: ' + line, gain: n => '+' + n + ' în unități de pion',
        opponentMate: 'adversarul dă mat', ownMate: 'poți da mat', draw: 'pat',
        equal: (n, move) => n + ' replici egale, inclusiv ' + move, final: 'Ultima mutare: câștigă material imediat.',
        alternative: move => 'De ce e mai slabă ' + move + '?', alternateReplay: move => 'Linia după ' + move,
        attemptDetails: 'Detaliile mutării',
        replay: 'Reia exercițiul', threat: 'Pericol', white: 'Alb', black: 'Negru', turn: side => side + ' la mutare',
        tally: ({ solved, total, points }) => solved + '/' + total + ' rezolvate · ' + points + ' pct.', overall: 'Total',
        nextMilestone: value => 'Următorul prag · ' + value, complete: 'Colecție completă',
        milestoneReached: (solved, next) => solved + ' atinse · urmează ' + next,
        saveFailed: 'Progresul nu a putut fi salvat. Încearcă din nou.', loadFailed: 'Problema nu a putut fi încărcată. Încearcă din nou.',
        hintDescription: (piece, square) => piece + ' pe ' + square, suggested: move => 'Mutare sugerată · ' + move,
        hintPiece: (square, side, piece) => 'Mută piesa de pe ' + square + ' (' + piece.toLowerCase() + ', ' + side + ').',
        fullHint: (turn, piece, square) => turn + '. ' + piece + ' Destinația ' + square + '.',
        hintPromotion: (move, piece) => move + ', promovează la ' + piece.toLowerCase(),
        intermezzo: 'Găsește mutarea forțată înaintea recapturii.',
        materialGain: value => 'Câștig material: +' + value + ' unități de pion',
        materialLoss: value => 'Material cedat: −' + value + ' unități de pion', materialEqual: 'Materialul este încă neschimbat',
        materialFloor: value => 'Minim după o replică a adversarului: ' + value + ' unități de pion față de început',
        capture: (value, square) => 'Captură pe ' + square + ' · ' + value,
        matingLine: 'Linia de mat', mateEnd: 'Șah mat', replayStart: 'Poziția inițială', keyOnly: 'Aici este afișată doar mutarea-cheie.',
        inCheck: side => side + ' la mutare · în șah', defenseStudy: 'Studiul apărării',
        evaluated: (depth, side, estimate) => 'Stockfish 19 · Adâncime ' + depth + ' după mutarea-cheie · ' + side + ': ' + estimate,
        estimate: value => 'evaluarea poziției ' + value + ' în unități de pion',
        studyMate: value => 'mat în ' + Math.abs(value) + ' pentru ' + (value > 0 ? 'cel care rezolvă' : 'apărător'),
        materialStep: value => 'Material pe tablă: ' + value + ' unități de pion față de început',
        materialOne: value => 'Material după mutare: ' + value + ' unități de pion',
        provenMate: square => 'Șah mat demonstrat · ' + square,
        provenCapture: (square, gain) => 'Captură pe ' + square + ' · ' + gain + ' în unități de pion după orice replică legală',
        mateKing: (square, pieces) => 'Regele de pe ' + square + ' este în șah de la ' + pieces + '.',
        mateAttack: (square, pieces) => square + ' este atacat de ' + pieces + '.',
        mateBlock: (square, piece) => square + ' este ocupat de o piesă proprie (' + piece + ').',
        patterns: { DIRECT: 'Piesa mutată dă șah, iar adversarul nu are nicio apărare legală.',
            DISCOVERED: 'Mutarea descoperă atacul altei piese asupra regelui, fără apărare legală.',
            CASTLING: 'Rocada aduce turnul pe pătratul de unde dă mat.',
            EN_PASSANT: 'Captura en passant elimină pionul advers și lasă regele fără apărare legală.',
            PROMOTION: 'Piesa aleasă la promovare este cea care permite matul.' },
        pieces: { PAWN: 'Pion', KNIGHT: 'Cal', BISHOP: 'Nebun', ROOK: 'Turn', QUEEN: 'Damă', KING: 'Rege' },
    },
};

export function selectQuestSquare(puzzle, selected, square) {
    const moves = puzzle.legalMoves ?? [];
    const chosen = selected ? moves.filter(move => move.slice(0, 4) === selected + square) : [];
    if (chosen.length && selected !== square) return { selected, chosen, targets: [] };
    const targets = selected === square ? [] : [...new Set(moves.filter(move => move.startsWith(square)).map(move => move.slice(2, 4)))];
    return { selected: targets.length ? square : null, targets, chosen: [] };
}

export function createQuestController({ call, storage, onChange, onError, schedule = setTimeout, cancel = clearTimeout }) {
    let generation = 0;
    let active = false;
    let busy = false;
    let timer = null;
    let state = null;
    let observed = null;

    function clearTimer() {
        if (timer !== null) cancel(timer);
        timer = null;
    }

    function emit(data = null) {
        onChange({ state, busy, data });
    }

    async function run(action, args = {}) {
        if (!active || busy) return false;
        const request = generation;
        clearTimer();
        busy = true;
        emit();
        let failed = false;
        try {
            const saved = storage.getItem(progressKey) ?? '';
            if (observed !== null && observed !== saved) throw new Error('Puzzle progress changed');
            const option = state?.type === 'puzzle' ? state.hint : '';
            const response = await call(action, { saved, option, ...args });
            if (!active || generation !== request) return false;
            if (response.saved && response.saved !== saved) {
                if ((storage.getItem(progressKey) ?? '') !== saved) throw new Error('Puzzle progress changed');
                storage.setItem(progressKey, response.saved);
            }
            observed = response.saved ?? saved;
            const data = response.data ?? response;
            if (data.type === 'menu' || data.type === 'puzzle') state = data;
            busy = false;
            emit(data);
            return data;
        } catch (error) {
            failed = true;
            if (active && generation === request) onError(error, action);
            return false;
        } finally {
            if (active && generation === request) {
                if (busy) {
                    busy = false;
                    emit();
                }
                if (state?.pending && !failed) {
                    const value = state.id;
                    const index = state.ply;
                    timer = schedule(() => {
                        if (!active || generation !== request) return;
                        timer = null;
                        void run('defense', { value, index });
                    }, defenseDelay);
                }
            }
        }
    }

    async function open({ resume = false } = {}) {
        close();
        active = true;
        const view = generation;
        const menu = await run('menu');
        if (resume && active && view === generation && menu?.resume) {
            await run('open', { value: menu.resume });
        }
    }

    function close() {
        active = false;
        generation++;
        clearTimer();
        busy = false;
        state = null;
        observed = null;
    }

    async function summary() {
        const saved = storage.getItem(progressKey) ?? '';
        const response = await call('summary', { saved });
        return (response.data ?? response).overall;
    }

    return { open, close, run, summary };
}

function notation(move) {
    if (!move) return '';
    const promotion = move[4] ? '=' + move[4].toUpperCase() : '';
    return move.slice(0, 2) + '–' + move.slice(2, 4) + promotion;
}

export function createQuestStudy({ root, locale, call, home, board, storage = globalThis.localStorage }) {
    const words = labels[locale] ?? labels.en;
    const chapters = questChapterLabels[locale] ?? questChapterLabels.en;
    const document = root.ownerDocument;
    const decimal = new Intl.NumberFormat(locale, { maximumFractionDigits: 2 });
    let puzzle;
    let blackBelow = false;
    let busy = false;
    let selected = null;
    let targets = [];
    let replay = null;
    let replayPly = 0;
    let danger = false;
    let failedAction = null;
    let milestoneAnnouncement = '';

    root.classList.add('quest-study');
    root.innerHTML = `
        <p id="quest-study-error" role="status" hidden></p>
        <button id="quest-study-retry" type="button" hidden></button>
        <button id="quest-study-error-home" type="button" hidden></button>
        <section id="quest-study-menu" hidden>
          <header data-chess-header><button id="quest-study-home" data-chess-back type="button">‹</button><h2 id="quest-study-title"></h2></header>
          <p id="quest-study-total"></p><p id="quest-study-series"></p>
          <div id="quest-study-chapters"></div>
        </section>
        <section id="quest-study-game" hidden>
          <header class="quest-study-head" data-chess-header>
            <button id="quest-study-back" data-chess-back type="button">‹</button>
            <h2 id="quest-study-chapter"></h2><span id="quest-study-rank"></span>
            <div id="quest-study-options-anchor" class="options-anchor">
              <button id="quest-study-option-title" type="button" aria-controls="quest-study-options" aria-expanded="false">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4,6h16 M4,12h16 M4,18h16"/></svg>
              </button>
              <div id="quest-study-options" class="options-panel" role="group" hidden>
                <button id="quest-study-flip" type="button"></button>
                <button id="quest-study-game-home" type="button"></button>
              </div>
            </div>
          </header>
          <div class="quest-study-score"><strong id="quest-study-score"></strong>
            <span id="quest-study-series-badge" class="quest-study-series"><svg viewBox="0 0 24 24" aria-hidden="true">
            <path fill-rule="evenodd" d="M13,2C13.6,6.5 17.5,7.4 17.5,11C18.4,10.4 18.8,9.7 19,9C20.2,11 21,12.5 21,15C21,19.4 17.3,22 12.5,22C7.6,22 4,18.9 4,14.9C4,11.2 6.2,9.3 8.2,7.5C8,10 8.8,11.1 9.7,11.7C9.7,7.3 12.2,5.5 13,2ZM12.7,13C12.2,15.2 9.6,16.1 9.6,18C9.6,19.5 10.7,20.5 12.5,20.5C14.3,20.5 15.4,19.4 15.4,17.8C15.4,16.3 14.3,15.3 12.7,13Z"/></svg>
            <span id="quest-study-current-series"></span></span>
          </div>
          <p id="quest-study-turn"></p>
          <div class="quest-study-board-and-details">
            <div id="quest-study-board" class="quest-study-board"></div>
            <div id="quest-study-description" class="quest-study-details" tabindex="0" role="region">
              <p id="quest-study-status"></p>
              <button id="quest-study-details" type="button" hidden></button>
              <div class="quest-study-progress"><span id="quest-study-solved-count"></span><span id="quest-study-milestone"></span></div>
              <progress id="quest-study-meter"></progress>
            </div>
          </div>
          <p id="quest-study-announcement" class="visually-hidden" role="status" aria-atomic="true"></p>
          <div class="quest-study-footer">
            <nav class="quest-study-actions" data-chess-navigation>
              <button id="quest-study-previous" data-chess-arrow type="button">‹</button>
              <button id="quest-study-hint" type="button">
                <svg id="quest-study-lamp-piece" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M9,18L9,16C9,14.9 8.1,14.2 7.5,13.4C6.6,12.3 6,10.9 6,9.5C6,6.2 8.7,3.5 12,3.5C15.3,3.5 18,6.2 18,9.5C18,10.9 17.4,12.3 16.5,13.4C15.9,14.2 15,14.9 15,16L15,18ZM10,21L14,21"/>
                </svg>
                <svg id="quest-study-lamp-move" viewBox="0 0 24 24" aria-hidden="true" hidden><path d="M6,18L18,6M11,6L18,6L18,13"/></svg>
                <svg id="quest-study-lamp-explain" viewBox="0 0 24 24" aria-hidden="true" hidden><path d="M3,5C6,4 9,4 12,6C15,4 18,4 21,5L21,18C18,17 15,17 12,19C9,17 6,17 3,18ZM12,6L12,19"/></svg>
              </button>
              <button id="quest-study-next" data-chess-arrow type="button">›</button>
            </nav>
            <p id="quest-study-hint-label" aria-hidden="true"></p>
          </div>
        </section>
        <dialog id="quest-study-promotion" aria-labelledby="quest-study-promote-title"><div class="quest-study-dialog">
          <h3 id="quest-study-promote-title"></h3><div id="quest-study-promote-choices"></div>
          <button id="quest-study-promote-close" type="button"></button>
        </div></dialog>
        <dialog id="quest-study-explanation" aria-labelledby="quest-study-explain-title"><div class="quest-study-dialog">
          <div class="quest-study-dialog-head" data-chess-header>
            <button id="quest-study-explain-close" data-chess-back type="button">‹</button><h3 id="quest-study-explain-title"></h3></div>
          <div class="quest-study-replay-body">
            <div id="quest-study-replay" hidden><div id="quest-study-replay-board" class="quest-study-board"></div></div>
            <div id="quest-study-replay-details" class="quest-study-replay-details" tabindex="0" role="region">
              <p id="quest-study-danger-reason" role="status" hidden></p>
              <p id="quest-study-replay-status" aria-live="polite" hidden></p>
              <p id="quest-study-explain-text"></p>
            </div>
          </div>
          <nav id="quest-study-replay-actions" class="quest-study-actions" data-chess-navigation hidden>
            <button id="quest-study-replay-previous" data-chess-arrow type="button">‹</button>
            <button id="quest-study-threat" type="button" aria-pressed="false"></button>
            <button id="quest-study-replay-next" data-chess-arrow type="button">›</button>
          </nav>
          <div id="quest-study-lesson-actions" hidden>
            <button id="quest-study-explain-replay" type="button"></button>
            <button id="quest-study-explain-alternative" type="button"></button>
          </div>
        </div></dialog>`;

    const at = name => root.querySelector('#quest-study-' + name);
    const setText = (name, text) => { if (at(name).textContent !== text) at(name).textContent = text; };
    const boardView = board(at('board'), chooseSquare);
    const replayView = board(at('replay-board'), () => {});
    const controller = createQuestController({ call, storage, onChange, onError });
    const options = attachOptionsMenu(at('option-title'), at('options'));
    const mark = square => ({ square, kind: 'danger' });
    const displayMaterial = cp => cp === 0 ? words.materialEqual
        : (cp > 0 ? words.materialGain : words.materialLoss)(decimal.format(Math.abs(cp) / 100));
    const signedMaterial = cp => (cp > 0 ? '+' : cp < 0 ? '−' : '') + decimal.format(Math.abs(cp) / 100);
    const overall = totals => words.overall + ' · ' + words.tally(totals);
    const guided = () => puzzle?.suggestion ? [puzzle.suggestion.slice(0, 2),
        ...(puzzle.hint === 'SOLUTION' ? [puzzle.suggestion.slice(2, 4)] : [])] : [];
    const handler = (name, fn) => at(name).addEventListener('click', fn);
    const present = dialog => { if (!dialog.open) dialog.showModal(); };
    let explanationOpener;

    for (const [id, label] of Object.entries({
        title: words.title, home: words.home, 'game-home': words.home, 'error-home': words.home,
        flip: words.flip, 'promote-title': words.promote, 'promote-close': words.close,
        'explain-close': words.close, 'explain-replay': words.replay, retry: locale === 'ro' ? 'Reîncearcă' : 'Retry',
    })) at(id).textContent = label;
    for (const [id, name] of Object.entries({
        previous: words.previous, next: words.next, 'replay-previous': words.previous,
        'replay-next': words.next, meter: words.title, threat: words.threat, 'option-title': words.menu, options: words.menu,
        description: words.attemptDetails, 'replay-details': words.replay,
    })) at(id).setAttribute('aria-label', name);
    at('threat').textContent = words.threat;

    async function command(action, args = {}) {
        if (busy) return false;
        failedAction = null;
        at('error').hidden = at('retry').hidden = at('error-home').hidden = true;
        return controller.run(action, args);
    }

    async function showMenu() {
        if (await command('leave')) at('title').focus();
    }

    async function goHome() {
        options.close();
        if (puzzle && !await command('leave')) return;
        close();
        home();
    }

    function onError(error, action) {
        failedAction = action;
        const saving = !['menu', 'summary', 'view', 'explain', 'alternative'].includes(action);
        at('error').textContent = saving ? words.saveFailed : words.loadFailed;
        at('error').hidden = false;
        at('retry').hidden = action !== 'defense' && action !== 'menu';
        at('error-home').hidden = Boolean(puzzle);
    }

    function onChange({ state, busy: loading, data }) {
        busy = loading;
        if (state?.type === 'menu') {
            puzzle = null;
            milestoneAnnouncement = '';
            if (data?.type === 'menu') renderMenu(state);
        }
        if (state?.type === 'puzzle') {
            if (state !== puzzle) {
                if (!puzzle || state.chapter !== puzzle.chapter) blackBelow = state.startSide === 'BLACK';
                milestoneAnnouncement = state.solved && puzzle?.id === state.id && !puzzle.solved && state.milestone.reached
                    ? milestoneText(state) : '';
                selected = null;
                targets = [];
                puzzle = state;
            }
            renderPuzzle();
        }
        if (data?.type === 'replay') openReplay(data);
        if (data?.type === 'finish') openLesson(data);
        at('game').setAttribute('aria-busy', String(busy));
        for (const button of at('chapters').querySelectorAll('button')) button.disabled = busy;
    }

    function renderMenu(data) {
        at('menu').hidden = false;
        at('game').hidden = true;
        at('total').textContent = overall(data.overall);
        at('series').textContent = data.streak >= 2 ? words.streak(data.streak) : '';
        at('chapters').replaceChildren(...data.chapters.map(chapter => {
            const row = document.createElement('div');
            const entry = document.createElement('button');
            entry.type = 'button';
            entry.textContent = chapters[chapter.key];
            entry.addEventListener('click', async () => {
                if (await command('open', { value: chapter.key })) at('chapter').focus();
            });
            const total = document.createElement('p');
            total.textContent = words.tally(chapter.totals);
            row.append(entry);
            if (chapter.key === 'intermezzo') {
                const help = document.createElement('p');
                help.textContent = words.intermezzo;
                row.append(help);
            }
            row.append(total);
            return row;
        }));
    }

    function attemptStatus(state) {
        const attempt = state.attempt;
        if (state.alternative) return words.final;
        if (state.objective === 'MATERIAL') return words.tryTactic;
        if (attempt?.outcome === 'CHECK') {
            const messages = [words.check(attempt.line.map(notation).join(' · '))];
            if (attempt.ending === 'CHECKMATE') messages.push(attempt.mate > 0 ? words.opponentMate : words.ownMate);
            if (attempt.ending === 'STALEMATE') messages.push(words.draw);
            if (attempt.ending === 'HORIZON' && attempt.gain > 0) messages.push(words.gain(decimal.format(attempt.gain / 100)));
            if (attempt.equal > 1) messages.push(words.equal(attempt.equal, notation(attempt.other)));
            return messages.join(' · ');
        }
        if (attempt?.outcome === 'STALEMATE') return words.stalemate;
        return words.tryMate[state.playerMoves - state.stage - 1];
    }

    function puzzleStatus(state) {
        if (state.solved) {
            return state.objective === 'MATE' ? words.solved : words.tacticSolved(displayMaterial(state.material));
        }
        if (state.pending) return words.pending;
        if (state.attempt) return attemptStatus(state);
        if (state.specialFinish) return words.final;
        if (state.stage > 0) {
            return state.objective === 'MATE' ? words.replyMate(notation(state.lastMove), state.playerMoves - state.stage)
                : words.reply(notation(state.lastMove), state.stage + 1, state.playerMoves);
        }
        return state.objective === 'MATE' ? words.mate : words.tactic;
    }

    function hintStatus(state, side, turn) {
        const from = state.suggestion.slice(0, 2);
        const to = state.suggestion.slice(2, 4);
        const token = state.board[(8 - Number(from[1])) * 8 + from.charCodeAt(0) - 97];
        const piece = words.hintPiece(from, side, words.pieces[pieceKinds[token.toLowerCase()]]);
        const promoted = words.pieces[pieceKinds[state.suggestion[4]]];
        const arrow = from + ' → ' + to;
        const move = promoted ? words.hintPromotion(arrow, promoted) : arrow;
        const goal = state.specialFinish ? '\n' + words.final : '';
        if (state.hint === 'PIECE') return { visible: piece + goal, spoken: turn + '. ' + piece + goal };
        const spoken = words.fullHint(turn, piece, to) + (promoted ? ' ' + move : '');
        return { visible: words.suggested(move) + goal, spoken: spoken + goal };
    }

    function milestoneText(state) {
        const { solved } = state.totals;
        const { next, reached } = state.milestone;
        if (solved === state.count) return words.complete;
        return reached ? words.milestoneReached(solved, next) : words.nextMilestone(next);
    }

    function renderGuidance(state) {
        const action = state.hintAction;
        at('lamp-piece').toggleAttribute('hidden', action !== 'SHOW_PIECE' && action !== 'WAIT');
        at('lamp-move').toggleAttribute('hidden', action !== 'SHOW_MOVE');
        at('lamp-explain').toggleAttribute('hidden', action !== 'EXPLAIN');
        at('hint-label').textContent = { SHOW_PIECE: words.showPiece, SHOW_MOVE: words.showMove, EXPLAIN: words.explain, WAIT: words.wait }[action];
        const explanation = state.objective === 'MATE' ? words.whyMate : words.whyTactic;
        at('hint').setAttribute('aria-label', { SHOW_PIECE: words.hint, SHOW_MOVE: words.showMove, EXPLAIN: explanation, WAIT: words.wait }[action]);
        at('hint').disabled = busy || action === 'WAIT';
    }

    function renderPuzzle() {
        at('menu').hidden = true;
        at('game').hidden = false;
        at('chapter').textContent = chapters[puzzle.chapter];
        at('rank').textContent = puzzle.rank + ' / ' + puzzle.count;
        at('rank').setAttribute('aria-label', words.rank(puzzle.rank, puzzle.count));
        at('score').textContent = words.points(puzzle.totals.points, puzzle.earned);
        at('series-badge').setAttribute('aria-hidden', String(!(puzzle.streak >= 2)));
        at('series-badge').setAttribute('aria-label', words.streak(puzzle.streak ?? 0));
        at('current-series').textContent = words.series(puzzle.streak ?? 0);
        at('solved-count').textContent = words.solvedCount(puzzle.totals.solved, puzzle.count);
        at('milestone').textContent = milestoneText(puzzle);
        at('meter').max = puzzle.count;
        at('meter').value = puzzle.totals.solved;
        const side = puzzle.sideToMove === 'WHITE' ? words.white : words.black;
        const turn = puzzle.solved ? '' : words.turn(side);
        const hint = puzzle.suggestion && !puzzle.attempt ? hintStatus(puzzle, side, turn) : null;
        const status = hint?.visible ?? puzzleStatus(puzzle);
        setText('turn', turn);
        setText('status', status);
        setText('announcement', hint?.spoken ?? [turn, status, milestoneAnnouncement].filter(Boolean).join('. '));
        at('details').hidden = !puzzle.attempt;
        at('details').textContent = puzzle.alternative ? words.alternative(puzzle.alternative) : words.attemptDetails;
        at('details').disabled = busy;
        at('previous').disabled = busy || !puzzle.previous;
        at('next').disabled = busy || !puzzle.next;
        at('next').setAttribute('aria-label', puzzle.firstUnsolved ? words.first : words.next);
        renderGuidance(puzzle);
        boardView.render({ board: puzzle.board, sideToMove: puzzle.sideToMove, blackBelow, selected, targets,
            marks: guided(), lastMove: puzzle.lastMove, locked: busy || puzzle.solved || puzzle.pending });
    }

    function chooseSquare(square) {
        if (busy || !puzzle || puzzle.solved || puzzle.pending) return;
        const choice = selectQuestSquare(puzzle, selected, square);
        if (choice.chosen.length === 1) {
            void command('play', { value: choice.chosen[0] });
        } else if (choice.chosen.length > 1) {
            at('promote-choices').replaceChildren(...choice.chosen.map(move => {
                const button = document.createElement('button');
                button.type = 'button';
                button.textContent = words.pieces[pieceKinds[move[4]]];
                button.addEventListener('click', () => {
                    at('promotion').close();
                    void command('play', { value: move });
                });
                return button;
            }));
            present(at('promotion'));
        } else {
            selected = choice.selected;
            targets = choice.targets;
            renderPuzzle();
        }
    }

    function openLesson(data) {
        replay = null;
        at('explain-title').textContent = words.whyTactic;
        at('explain-text').textContent = (locale === 'ro' ? data.ro : data.en);
        showReplayFrame(false);
        at('lesson-actions').hidden = false;
        at('explain-replay').hidden = !data.hasReplay;
        at('explain-alternative').textContent = words.alternateReplay(data.alternative);
        present(at('explanation'));
    }

    function openAttempt() {
        replay = null;
        at('explain-title').textContent = words.attemptDetails;
        at('explain-text').textContent = attemptStatus(puzzle);
        showReplayFrame(false);
        at('lesson-actions').hidden = true;
        present(at('explanation'));
    }

    function openReplay(data) {
        if (!replay || !at('explanation').open) rememberBoardSize(at('board'), at('explanation'));
        replay = data;
        replayPly = data.initial;
        danger = false;
        at('explain-title').textContent = data.alternative ? words.alternateReplay(data.title)
            : data.mate ? words.matingLine : data.depth !== null ? words.defenseStudy : words.whyTactic;
        at('lesson-actions').hidden = true;
        showReplayFrame(true);
        renderReplay();
        present(at('explanation'));
    }

    function showReplayFrame(visible) {
        for (const id of ['replay', 'replay-status', 'replay-actions']) at(id).hidden = !visible;
        at('danger-reason').hidden = true;
        at('explanation').toggleAttribute('data-replay', visible);
    }

    function replayExplanation(data, current, final) {
        const describeThreats = items => items.map(item => words.hintDescription(words.pieces[item.piece], item.square)).join(', ');
        if (data.mate) return final ? [
            words.patterns[data.pattern],
            words.mateKing(data.mate.king, describeThreats(data.mate.checkers)),
            ...data.mate.exits.map(exit => exit.blocker ? words.mateBlock(exit.square, words.pieces[exit.blocker])
                : words.mateAttack(exit.square, describeThreats(exit.attackers))),
        ].join('\n') : '';
        if (data.depth === null) return [data.frames.length === 2 ? words.keyOnly : '', (questLessons[locale] ?? questLessons.en)[data.lesson]]
            .filter(Boolean).join('\n');
        const side = data.frames[0].sideToMove === 'WHITE' ? words.white : words.black;
        const estimate = data.mateIn === null ? words.estimate(signedMaterial(data.evaluation)) : words.studyMate(data.mateIn);
        const floor = final && data.floor !== null && data.floor !== current.material ? words.materialFloor(signedMaterial(data.floor)) : '';
        return [locale === 'ro' ? data.lessonRo : data.lessonEn, words.evaluated(data.depth, side, estimate), floor].filter(Boolean).join('\n');
    }

    function renderReplay() {
        if (!replay) return;
        const current = replay.frames[replayPly];
        at('replay-previous').disabled = replayPly === 0;
        at('replay-next').disabled = replayPly === replay.frames.length - 1;
        at('threat').hidden = !current.danger;
        at('threat').setAttribute('aria-pressed', String(danger));
        const final = replayPly === replay.frames.length - 1;
        const side = current.sideToMove === 'WHITE' ? words.white : words.black;
        const movedBy = current.sideToMove === 'WHITE' ? words.black : words.white;
        const replayStatus = current.lastMove ? [replayPly + '/' + (replay.frames.length - 1), movedBy, notation(current.lastMove)] : [words.replayStart];
        if (replay.mate) replayStatus.push(final ? words.mateEnd : current.check ? words.inCheck(side) : words.turn(side));
        else if (replay.depth !== null) replayStatus.push(words.materialStep(signedMaterial(current.material)));
        else if (replay.frames.length === 2) replayStatus.push(words.materialOne(signedMaterial(current.material)));
        else replayStatus.push(displayMaterial(current.material));
        if (current.captured) replayStatus.push(words.capture(words.pieces[current.captured], current.captureSquare));
        const threat = danger ? current.danger : null;
        at('danger-reason').hidden = !threat;
        setText('danger-reason', !threat ? '' : threat.kind === 'MATE' ? words.provenMate(threat.square)
            : words.provenCapture(threat.square, signedMaterial(threat.gain)));
        at('explain-text').textContent = replayExplanation(replay, current, final);
        renderLinePosition(at('replay-status'), { ply: replayPly, total: replay.frames.length - 1,
            text: replayStatus.filter(Boolean).join(' · ') });
        at('replay-details').scrollTop = 0;
        replayView.render({ board: current.board, sideToMove: current.sideToMove, blackBelow,
            marks: danger && current.danger ? [mark(current.danger.square)] : [], lastMove: current.lastMove, locked: true });
    }

    function stepReplay(step) {
        if (!replay || replayPly + step < 0 || replayPly + step >= replay.frames.length) return;
        replayPly += step;
        danger = false;
        renderReplay();
    }

    async function open(options) {
        close();
        at('error').hidden = at('retry').hidden = at('error-home').hidden = true;
        at('title').tabIndex = -1;
        at('chapter').tabIndex = -1;
        await controller.open(options);
    }

    function close() {
        controller.close();
        puzzle = null;
        selected = null;
        targets = [];
        replay = null;
        milestoneAnnouncement = '';
        at('menu').hidden = at('game').hidden = true;
        options.close();
        for (const id of ['promotion', 'explanation']) if (at(id).open) at(id).close();
    }

    handler('previous', () => void command('previous'));
    handler('next', () => void command('next'));
    function showGuidance() {
        if (at('hint').disabled) return;
        explanationOpener = at('hint');
        void command('hint', { value: puzzle?.alternative ? puzzle.attempt.move : '' });
    }
    handler('hint', showGuidance);
    handler('hint-label', showGuidance);
    handler('details', () => {
        explanationOpener = at('details');
        if (puzzle?.alternative) void command('explain', { value: puzzle.attempt.move });
        else if (puzzle?.attempt) openAttempt();
    });
    handler('home', () => void goHome());
    at('home').textContent = '‹';
    at('home').setAttribute('aria-label', words.home);
    at('explain-close').textContent = '‹';
    at('explain-close').setAttribute('aria-label', words.close);
    at('back').setAttribute('aria-label', backLabel('PUZZLE', locale));
    at('home').setAttribute('aria-label', backLabel('PUZZLES', locale));
    handler('back', () => void showMenu());

    handler('game-home', () => void goHome());
    handler('error-home', () => void goHome());
    root.addEventListener('keydown', event => {
        if (event.key !== 'Escape' || !puzzle || options.isOpen() || at('promotion').open || at('explanation').open) return;
        event.preventDefault();
        void showMenu();
    });
    handler('flip', () => {
        blackBelow = !blackBelow;
        options.close(true);
        renderPuzzle();
    });
    handler('retry', () => {
        if (failedAction === 'menu') void open();
        else if (failedAction === 'defense' && puzzle) void command('defense', { value: puzzle.id, index: puzzle.ply });
    });
    handler('promote-close', () => at('promotion').close());
    handler('explain-close', () => at('explanation').close());
    at('explanation').addEventListener('close', () => {
        if (!at('game').hidden && explanationOpener && !explanationOpener.disabled && !explanationOpener.hidden) {
            explanationOpener.focus();
        }
        explanationOpener = undefined;
    });
    handler('explain-replay', () => void command('explain', { value: 'replay' }));
    handler('explain-alternative', () => void command('alternative'));
    handler('replay-previous', () => stepReplay(-1));
    handler('replay-next', () => stepReplay(1));
    handler('threat', () => {
        danger = !danger;
        renderReplay();
    });
    return { open, close, async summary() {
        try { return overall(await controller.summary()); } catch { return ''; }
    } };
}
