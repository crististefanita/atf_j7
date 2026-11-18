import { attachOptionsMenu } from './options-menu.mjs';
import { rememberBoardSize } from './study-board-size.mjs';
import { renderLinePosition } from './line-navigation.mjs';

const copy = {
    en: {
        title: 'Analysis board', back: 'Back to the previous screen', reset: 'Reset line', options: 'Analysis board options',
        analysis: 'AI analysis', review: 'Review all moves', continue: 'Continue as a saved game', flip: 'Flip board',
        save: 'Continue', cancel: 'Cancel', auto: 'Let AI play its moves', confirm: ply => 'Start a new saved game after ply ' + ply + '?',
        noSlot: 'A free game slot is needed to save this line.', failed: 'This analysis could not be updated. Try again.',
        white: 'White', black: 'Black', toMove: side => side + ' to move', position: (at, end) => 'Position ' + at + ' · ' + end,
        previous: 'Previous move', next: 'Next move', latest: 'Jump to latest move', history: 'Move history', start: 'Start',
        pending: 'AI analysis…', finished: 'AI analysis · Completed game', balanced: 'Balanced', mate: count => 'Mate in ' + count,
        hint: 'Show AI variation', nextHint: 'Show next AI variation', shownHint: 'AI variation shown', preview: 'Open line replay',
        hintVariant: (number, count) => 'Variation ' + number + ' of ' + count,
        theory: 'Opening theory', variation: 'Calculated line', return: 'Return to the position', track: 'Evaluation for White',
        step: (at, end) => 'Step ' + at + ' of ' + end, choice: n => 'Line ' + n, genericTheory: 'This continuation is recorded in the opening index.',
        forecast: side => 'Estimate for ' + side + ' at start.', promote: 'Promote pawn', depth: n => 'Depth ' + n + ' plies',
        mateProof: (piece, move) => 'In this line, ' + piece + ' gives mate with ' + move + '.',
        replyMate: (piece, move) => 'The reply delivers mate with ' + piece + ' on ' + move + '.',
        capture: (move, piece, gain) => move + ' captures a piece (' + piece + '). Net material: +' + gain + '.',
        proof: (line, ply, move, piece, mate) => 'Line ' + line + ', ply ' + ply + ': ' + move + (mate ? ' gives mate with ' : ' captures ') + piece + '.',
        jump: 'Show this move on the board.',
        pieces: { q: 'Queen', r: 'Rook', b: 'Bishop', n: 'Knight', p: 'Pawn', k: 'King' },
        results: { WHITE_WINS: 'Checkmate', BLACK_WINS: 'Checkmate', STALEMATE: 'Stalemate', MATERIAL_DRAW: 'Draw by insufficient material',
            REPETITION_DRAW: 'Draw by repetition', MOVE_COUNT_DRAW: 'Draw by move count', AGREED_DRAW: 'Draw by agreement' },
    },
    ro: {
        title: 'Tablă de analiză', back: 'Înapoi la ecranul anterior', reset: 'Resetează linia', options: 'Opțiunile tablei de analiză',
        analysis: 'Analiză AI', review: 'Analizează toate mutările', continue: 'Continuă ca partidă salvată', flip: 'Întoarce tabla',
        save: 'Continuă', cancel: 'Anulează', auto: 'Lasă AI-ul să mute',
        confirm: ply => 'Începi o partidă salvată după semimutarea ' + ply + '?',
        noSlot: 'Pentru a salva linia ai nevoie de un loc liber.', failed: 'Analiza nu a putut fi actualizată. Încearcă din nou.',
        white: 'Alb', black: 'Negru', toMove: side => side + ' la mutare', position: (at, end) => 'Poziția ' + at + ' · ' + end,
        previous: 'Mutarea anterioară', next: 'Mutarea următoare', latest: 'Sari la ultima mutare', history: 'Istoricul mutărilor', start: 'Start',
        pending: 'Analiză AI…', finished: 'Analiză AI · Partidă încheiată', balanced: 'Echilibru', mate: count => 'Mat în ' + count,
        hint: 'Arată varianta AI', nextHint: 'Arată următoarea variantă AI', shownHint: 'Varianta AI afișată', preview: 'Deschide redarea liniei',
        hintVariant: (number, count) => 'Varianta ' + number + ' din ' + count,
        theory: 'Teoria deschiderilor', variation: 'Linie calculată', return: 'Revino la poziție', track: 'Evaluarea albului',
        step: (at, end) => 'Pasul ' + at + ' din ' + end, choice: n => 'Linia ' + n, genericTheory: 'Continuarea apare în indexul deschiderilor.',
        forecast: side => 'Estimare pentru ' + side + ' la început.', promote: 'Promovează pionul', depth: n => 'Adâncime ' + n + ' semimutări',
        mateProof: (piece, move) => 'În această variantă, ' + piece + ' dă mat cu ' + move + '.',
        replyMate: (piece, move) => 'Replica dă mat cu piesa ' + piece + ' la mutarea ' + move + '.',
        capture: (move, piece, gain) => move + ' capturează piesa ' + piece + '. Bilanț material: +' + gain + '.',
        proof: (line, ply, move, piece, mate) => 'Linia ' + line + ', pasul ' + ply + ': ' + move
            + (mate ? ' dă mat cu piesa ' : ' capturează piesa ') + piece + '.',
        jump: 'Arată această mutare pe tablă.',
        pieces: { q: 'Damă', r: 'Turn', b: 'Nebun', n: 'Cal', p: 'Pion', k: 'Rege' },
        results: { WHITE_WINS: 'Șah mat', BLACK_WINS: 'Șah mat', STALEMATE: 'Pat', MATERIAL_DRAW: 'Remiză prin material insuficient',
            REPETITION_DRAW: 'Remiză prin repetiție', MOVE_COUNT_DRAW: 'Remiză prin numărul de mutări', AGREED_DRAW: 'Remiză prin acord' },
    },
};

const storageKey = 'chesslab.analysis-study.v1';
const language = locale => locale === 'ro' ? 'ro' : 'en';
const sideName = (words, side) => side === 'WHITE' ? words.white : words.black;
const other = side => side === 'WHITE' ? 'BLACK' : 'WHITE';
const notation = move => move ? move.slice(0, 2) + ' → ' + move.slice(2, 4) + (move.length === 5 ? '=' + move[4].toUpperCase() : '') : '';
const pieceName = (words, kind) => words.pieces[{ PAWN: 'p', KING: 'k', QUEEN: 'q', ROOK: 'r', BISHOP: 'b', KNIGHT: 'n' }[kind]];

function pawns(centipawns, locale) {
    const number = new Intl.NumberFormat(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return (centipawns > 0 ? '+' : centipawns < 0 ? '−' : '') + number.format(Math.abs(centipawns) / 100);
}

export function analysisScore(evaluation, locale) {
    const words = copy[language(locale)];
    if (!evaluation) return words.pending;
    if (evaluation.ending === 'FINISHED') return words.finished;
    if (evaluation.ending === 'STALEMATE') return words.results.STALEMATE;
    if (evaluation.ending === 'CHECKMATE') {
        return sideName(words, evaluation.mate > 0 ? 'WHITE' : 'BLACK') + ' · ' + words.mate(Math.abs(evaluation.mate));
    }
    const value = evaluation.centipawns;
    return pawns(value, locale) + ' · ' + (value === 0 ? words.balanced : sideName(words, value > 0 ? 'WHITE' : 'BLACK'));
}

export function analysisHintDescription(hint, locale) {
    if (!hint?.move) return '';
    const words = copy[language(locale)];
    const family = hint.family?.[language(locale)];
    const move = hint.move.slice(0, 2) + (locale === 'ro' ? ' la ' : ' to ') + hint.move.slice(2, 4);
    const promotion = hint.move.length === 5 ? ' ' + words.pieces[hint.move[4]] : '';
    return words.hintVariant(hint.number, hint.count) + '. ' + move + promotion + (family ? '. ' + family : '') + '.';
}

export function renderAnalysisMeter(meter, fill, evaluation, blackBelow, locale) {
    meter.setAttribute('aria-hidden', evaluation == null);
    if (!evaluation) {
        meter.removeAttribute('aria-valuenow');
        meter.removeAttribute('aria-valuetext');
        fill.style.height = '0%';
        return;
    }
    const share = Math.round(evaluation.whiteShare * 100);
    meter.setAttribute('aria-valuenow', share);
    meter.setAttribute('aria-valuetext', analysisScore(evaluation, locale));
    fill.style.height = share + '%';
    fill.style.insetBlockStart = blackBelow ? '0' : 'auto';
    fill.style.insetBlockEnd = blackBelow ? 'auto' : '0';
}

export function renderAnalysisEvidence(target, preview, rootSide, locale, proofButton) {
    const words = copy[language(locale)];
    if (preview.kind === 'THEORY') {
        target.textContent = preview.name[language(locale)] + ' · ' + preview.eco + ' · '
            + (preview.idea?.[language(locale)] ?? words.genericTheory);
        return;
    }
    const evidence = [];
    if (preview.depth > 0) evidence.push(words.depth(preview.depth));
    if (preview.initialCp !== null) evidence.push(pawns(preview.initialCp, locale) + ' → ' + pawns(preview.finalCp, locale)
        + ' · Δ ' + pawns(preview.finalCp - preview.initialCp, locale));
    if (preview.decisive) {
        const proof = preview.decisive;
        const label = words.proof(proof.branch + 1, proof.ply, notation(proof.move), pieceName(words, proof.piece), proof.reason === 'MATE');
        const jump = proofButton(label);
        jump.setAttribute('aria-label', label + ' ' + words.jump);
        target.replaceChildren(evidence.join(' · '), jump);
        return;
    }
    const piece = pieceName(words, preview.piece);
    const move = notation(preview.proofMove);
    evidence.push(preview.proof === 'CHECKMATE' ? words.mateProof(piece, move)
        : preview.proof === 'CHECKMATE_AGAINST' ? words.replyMate(piece, move)
        : preview.proof === 'CAPTURE' ? words.capture(move, pieceName(words, preview.captured), preview.gain)
        : preview.proof === 'STALEMATE' ? words.results.STALEMATE : words.forecast(sideName(words, rootSide)));
    target.textContent = evidence.join(' · ');
}

function keepMoveVisible(history, move) {
    if (!move || !history.clientWidth) return;
    const viewport = history.getBoundingClientRect();
    const bounds = move.getBoundingClientRect();
    if (bounds.left < viewport.left) history.scrollLeft += bounds.left - viewport.left;
    else if (bounds.right > viewport.right) history.scrollLeft += bounds.right - viewport.right;
}

function loadBookmark(storage) {
    try {
        return storage()?.getItem(storageKey) ?? '';
    } catch {
        return '';
    }
}

function saveBookmark(storage, saved) {
    if (typeof saved !== 'string') return;
    try {
        storage()?.setItem(storageKey, saved);
    } catch {
        // The in-memory line remains usable when browser storage is unavailable.
    }
}

/** Owns request ordering and session persistence independently of the DOM. */
export function analysisStudySession({ call, render, failed, storage }) {
    let active = false;
    let generation = 0;
    let latest = 0;
    let requestedEvaluation;
    let bookmark;
    let contextual = false;
    async function request(action, input = {}) {
        if (!active) return null;
        const route = generation;
        const ticket = ++latest;
        try {
            const reply = await call(action, input);
            if (!active || route !== generation || ticket !== latest) return null;
            const state = reply.state ?? reply;
            if (typeof state.saved === 'string') {
                bookmark = state.saved;
                if (!contextual) saveBookmark(storage, bookmark);
            }
            render(state);
            if (state.analysis && state.result === 'IN_PROGRESS' && !state.evaluation
                    && action !== 'evaluate' && requestedEvaluation !== state.revision) {
                requestedEvaluation = state.revision;
                void request('evaluate', { index: state.revision });
            }
            return state;
        } catch {
            if (active && route === generation && ticket === latest) {
                if (action === 'evaluate') requestedEvaluation = undefined;
                failed();
            }
            return null;
        }
    }
    function activate(action, input) {
        active = true;
        generation++;
        requestedEvaluation = undefined;
        return request(action, input);
    }
    return {
        open(entry) {
            contextual = Boolean(entry);
            bookmark = contextual ? '' : loadBookmark(storage);
            return entry ? activate('open', { value: entry.checkpoint ?? '', index: entry.ply ?? 0,
                option: entry.blackBelow ? 'black' : '' }) : activate('resume', { saved: bookmark });
        },
        restore() { return activate('resume', { saved: bookmark }); },
        request,
        close() { active = false; generation++; },
    };
}

export function createAnalysisStudy({ root, locale, call, home, board, openReview, continueGame,
    storage = () => globalThis.sessionStorage }) {
    const words = copy[language(locale)];
    const document = root.ownerDocument;
    let state;
    let controls;
    let session;
    let saving = false;
    const text = (tag, content, className = '') => {
        const element = document.createElement(tag);
        if (className) element.className = className;
        element.textContent = content;
        return element;
    };
    const button = (label, action, icon = label) => {
        const element = text('button', icon);
        element.type = 'button';
        element.setAttribute('aria-label', label);
        if (icon !== label) {
            element.className = 'analysis-study-icon';
            element.title = label;
        }
        element.addEventListener('click', action);
        return element;
    };
    const request = (action, body) => {
        if (!state) return null;
        if (controls) controls.error.hidden = true;
        return session.request(action, body);
    };
    const seek = index => state?.preview ? request('preview-seek', { index }) : request('seek', { index });
    const closePreview = async () => {
        if (await request('preview-close')) controls.replay.focus();
    };
    const back = () => state?.preview ? closePreview() : home();

    function mount() {
        const header = text('div', '', 'analysis-study-header');
        header.setAttribute('data-chess-header', '');
        const backButton = button(words.back, back, '‹');
        backButton.setAttribute('data-chess-back', '');
        const title = text('h2', words.title);
        const reset = button(words.reset, () => void request('reset'), '↺');
        const menu = text('div', '', 'analysis-study-menu options-anchor');
        const summary = text('button', '☰', 'analysis-study-icon');
        summary.type = 'button';
        summary.setAttribute('aria-label', words.options);
        summary.setAttribute('aria-controls', 'analysis-study-options');
        summary.setAttribute('aria-expanded', 'false');
        const actions = text('div', '', 'analysis-study-menu-items options-panel');
        actions.id = 'analysis-study-options';
        actions.hidden = true;
        actions.setAttribute('role', 'group');
        actions.setAttribute('aria-label', words.options);
        menu.append(summary, actions);
        const options = attachOptionsMenu(summary, actions);
        const toggler = text('label', '');
        const toggle = document.createElement('input');
        toggle.type = 'checkbox';
        toggle.addEventListener('change', () => void request('analysis', { option: toggle.checked ? 'on' : 'off' }));
        toggler.append(toggle, words.analysis);
        const review = button(words.review, () => {
            options.close();
            if (state?.analysis && state.total > 0) openReview?.(state.checkpoint, state.blackBelow, state.ply);
        });
        const promptContinuation = () => {
            options.close();
            if (!continueGame || state?.result !== 'IN_PROGRESS' || saveDialog.open) return;
            saveMessage.textContent = words.confirm(state.ply);
            automatic.checked = true;
            saveDialog.showModal();
        };
        const saveGame = button(words.continue, promptContinuation);
        const flip = button(words.flip, () => { options.close(true); void request('flip'); });
        actions.append(toggler, review, saveGame, flip);
        header.append(backButton, title, reset, menu);
        const top = text('p', '', 'analysis-study-side');
        const bottom = text('p', '', 'analysis-study-side');
        const stage = text('div', '', 'analysis-study-stage');
        const stack = text('div', '', 'analysis-study-stack');
        const panel = text('div', '', 'analysis-study-panel');
        const host = text('div', '', 'analysis-study-board');
        const widget = board(host, square => void request('tap', { value: square }));
        const meter = text('div', '', 'analysis-study-meter chess-evaluation-meter');
        meter.setAttribute('role', 'meter');
        meter.setAttribute('aria-label', words.track);
        meter.setAttribute('aria-valuemin', '0');
        meter.setAttribute('aria-valuemax', '100');
        const fill = text('div', '', 'analysis-study-meter-fill');
        meter.append(fill);
        panel.append(meter, host);
        stack.append(top, panel, bottom);
        stage.append(stack);
        const status = text('p', '', 'analysis-study-status');
        status.setAttribute('aria-live', 'polite');
        status.tabIndex = -1;
        const score = text('p', '', 'analysis-study-score');
        const announcement = text('p', '', 'visually-hidden');
        announcement.setAttribute('aria-live', 'polite');
        announcement.setAttribute('aria-atomic', 'true');
        const error = text('p', '', 'analysis-study-error');
        error.setAttribute('role', 'alert');
        error.hidden = true;
        const history = text('nav', '', 'analysis-study-history');
        history.setAttribute('aria-label', words.history);
        const navigation = text('div', '', 'analysis-study-navigation analysis-study-toolbar');
        navigation.setAttribute('data-chess-navigation', '');
        const previous = button(words.previous, () => void seek((state.preview ?? state).ply - 1), '‹');
        const playback = text('p', '', 'analysis-study-playback');
        playback.setAttribute('aria-live', 'polite');
        playback.tabIndex = -1;
        playback.hidden = true;
        const next = button(words.next, () => void seek((state.preview ?? state).ply + 1), '›');
        const latest = button(words.latest, () => void seek((state.preview ?? state).total), '»');
        const hint = button(words.hint, () => void request('hint'), '💡');
        const replay = button(words.preview, async () => {
            if ((await request('preview'))?.preview) backButton.focus();
        });
        const feedback = text('div', '', 'analysis-study-feedback');
        feedback.append(score, replay);
        const variants = text('div', '', 'analysis-study-variants');
        variants.setAttribute('data-chess-lines', '');
        const evidence = text('p', '', 'analysis-study-evidence');
        const replayDetails = text('div', '', 'analysis-study-replay-details');
        replayDetails.hidden = true;
        replayDetails.append(evidence);
        variants.hidden = true;
        navigation.append(previous, playback, history, next, latest, hint);
        for (const arrow of [previous, next, latest]) arrow.setAttribute('data-chess-arrow', '');
        const promotion = document.createElement('dialog');
        promotion.setAttribute('aria-label', words.promote);
        const promotionChoices = text('div', '', 'analysis-study-navigation');
        const cancelPromotion = button(words.cancel, () => void request('cancel-promotion'));
        promotion.addEventListener('cancel', event => { event.preventDefault(); void request('cancel-promotion'); });
        promotion.append(text('h3', words.promote), promotionChoices, cancelPromotion);
        const saveDialog = document.createElement('dialog');
        saveDialog.setAttribute('aria-label', words.continue);
        const saveMessage = text('p', '');
        const automatic = document.createElement('input');
        automatic.type = 'checkbox';
        const autoLabel = text('label', '');
        autoLabel.append(automatic, words.auto);
        const cancelSave = button(words.cancel, () => saveDialog.close());
        const confirmSave = button(words.save, async () => {
            if (saving) return;
            saving = true;
            confirmSave.disabled = true;
            try {
                const nextState = await request('continue');
                if (nextState?.continuation && continueGame) {
                    const accepted = await continueGame(nextState.continuation, automatic.checked, nextState.blackBelow);
                    if (accepted === false) {
                        error.textContent = words.noSlot;
                        error.hidden = false;
                    }
                }
            } catch {
                failed();
            } finally {
                saving = false;
                confirmSave.disabled = false;
                saveDialog.close();
            }
        });
        const saveActions = text('div', '', 'analysis-study-navigation');
        saveActions.append(cancelSave, confirmSave);
        saveDialog.append(text('h3', words.continue), saveMessage, autoLabel, saveActions);
        root.classList.add('analysis-study');
        root.setAttribute('aria-label', words.title);
        root.replaceChildren(header, stage, status, feedback, announcement, error, variants, navigation, replayDetails, promotion, saveDialog);
        return { title, back: backButton, reset, menu, options, toggle, review, saveGame, top, bottom, host, widget, meter, fill, status, score, feedback,
            announcement, error, replay, replayDetails, variants, evidence, history, previous, next, latest, hint, playback,
            promotion, promotionChoices, saveDialog,
            promptContinuation };
    }

    function render(next) {
        if (next.preview && !state?.preview) rememberBoardSize(controls.host, root);
        state = next;
        const preview = next.preview;
        if (preview) controls.options.close();
        const frame = preview ?? next;
        controls.widget.render({ ...frame, blackBelow: next.blackBelow, locked: !!preview || next.result !== 'IN_PROGRESS' });
        const lower = next.blackBelow ? 'BLACK' : 'WHITE';
        for (const [label, side] of [[controls.top, other(lower)], [controls.bottom, lower]]) {
            const active = frame.sideToMove === side && (preview || next.result === 'IN_PROGRESS');
            label.textContent = active ? words.toMove(sideName(words, side)) : sideName(words, side);
            label.classList.toggle('active', !!active);
        }
        controls.title.textContent = preview ? preview.kind === 'THEORY' ? words.theory : words.variation : words.title;
        controls.back.setAttribute('aria-label', preview ? words.return : words.back);
        controls.reset.hidden = controls.menu.hidden = !!preview;
        controls.toggle.checked = next.analysis;
        controls.review.disabled = !openReview || !next.analysis || next.total === 0;
        controls.saveGame.disabled = !continueGame || next.result !== 'IN_PROGRESS';
        controls.status.hidden = Boolean(preview);
        controls.status.textContent = words.position(next.ply, next.total) + (words.results[next.result] ? '\n' + words.results[next.result] : '');
        controls.playback.hidden = !preview;
        controls.feedback.hidden = !next.analysis || !!preview;
        controls.score.textContent = analysisScore(next.evaluation, locale);
        controls.meter.hidden = !next.analysis || !!preview;
        renderAnalysisMeter(controls.meter, controls.fill, controls.meter.hidden ? null : next.evaluation, next.blackBelow, locale);
        controls.previous.disabled = frame.ply === 0;
        controls.next.disabled = controls.latest.disabled = frame.ply === frame.total;
        controls.latest.hidden = !!preview;
        controls.hint.hidden = !next.analysis || !!preview;
        controls.hint.disabled = !next.hint?.count;
        controls.hint.setAttribute('aria-pressed', !!next.hint?.move);
        controls.hint.setAttribute('aria-label', next.hint?.move ? next.hint.count > 1 ? words.nextHint : words.shownHint : words.hint);
        controls.replay.hidden = !next.hint?.move || !!preview;
        const announcement = analysisHintDescription(next.hint, locale);
        if (controls.announcement.textContent !== announcement) controls.announcement.textContent = announcement;
        if (next.hint?.move) {
            const family = next.hint.family?.[language(locale)];
            controls.replay.textContent = words.preview + ' · ' + notation(next.hint.move) + (family ? ' · ' + family : '');
            controls.replay.setAttribute('aria-label', words.preview + '. ' + announcement);
        }
        root.classList.toggle('analysis-study-replay', Boolean(preview));
        controls.variants.hidden = controls.replayDetails.hidden = !preview;
        if (preview) {
            const move = frame.lastMove || preview.branches[preview.branch];
            renderLinePosition(controls.playback, { ply: frame.ply, total: frame.total,
                text: words.step(frame.ply, frame.total) + (move ? ' · ' + notation(move) : '') });
            const key = preview.branches.join(' ');
            if (key !== controls.variants.dataset.branches) {
                controls.variants.replaceChildren(...preview.branches.map((move, index) => {
                    const name = words.choice(index + 1);
                    const choice = button(name + ' · ' + notation(move), () => void request('preview-branch', { index }));
                    choice.textContent = name;
                    return choice;
                }));
                controls.variants.dataset.branches = key;
            }
            for (const [index, choice] of Array.from(controls.variants.children).entries()) {
                choice.setAttribute('aria-pressed', index === preview.branch);
            }
            renderAnalysisEvidence(controls.evidence, preview, next.sideToMove, locale,
                label => button(label, async () => { if (await request('preview-proof')) controls.playback.focus(); }));
        }
        renderHistory(next, !!preview);
        renderPromotion(next.promotions);
    }

    function renderHistory(next, preview) {
        controls.history.hidden = preview;
        if (preview) return;
        const moves = [null, ...next.history];
        if (moves.length !== controls.history.children.length || moves.some((move, index) =>
            controls.history.children[index]?.dataset.move !== (move ?? ''))) {
            controls.history.replaceChildren(...moves.map((move, index) => {
                const label = move ? Math.ceil(index / 2) + (index % 2 ? '. ' : '… ') + notation(move) : words.start;
                const item = button(label, () => void seek(index));
                item.dataset.move = move ?? '';
                return item;
            }));
        }
        for (const [index, item] of Array.from(controls.history.children).entries()) {
            if (index === next.ply) item.setAttribute('aria-current', 'step');
            else item.removeAttribute('aria-current');
        }
        keepMoveVisible(controls.history, controls.history.children[next.ply]);
    }

    function renderPromotion(promotions) {
        if (!promotions.length) {
            if (controls.promotion.open) controls.promotion.close();
            return;
        }
        controls.promotionChoices.replaceChildren(...promotions.map(move => button(words.pieces[move.at(-1)],
            () => void request('promote', { value: move }))));
        if (!controls.promotion.open) controls.promotion.showModal();
    }

    function failed() {
        controls.error.textContent = words.failed;
        controls.error.hidden = false;
    }

    return {
        open(entry) {
            if (!controls) {
                controls = mount();
                session = analysisStudySession({ call, render, failed, storage });
            }
            controls.error.hidden = true;
            state = undefined;
            if (entry) controls.back.focus({ preventScroll: true });
            return session.open(entry);
        },
        restore() {
            state = undefined;
            return session.restore();
        },
        back,
        seek(index) { return request('seek', { index }); },
        async continueAt(index) {
            if (await request('seek', { index })) controls.promptContinuation();
        },
        close() {
            if (!controls) return;
            controls.options.close();
            if (controls.promotion.open) controls.promotion.close();
            if (controls.saveDialog.open) controls.saveDialog.close();
            session.close();
        },
    };
}
