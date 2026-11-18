import { analysisHintDescription, analysisScore, renderAnalysisEvidence, renderAnalysisMeter } from './study-analysis.mjs';
import { rememberBoardSize } from './study-board-size.mjs';
import { renderLinePosition } from './line-navigation.mjs';

export function createInlineAnalysisSession({ createWorker, publish }) {
    let current;
    let serial = 0;

    function discard() {
        const previous = current;
        current = undefined;
        previous?.worker?.terminate();
    }

    function notify() { publish(current); }

    function request(owner, type, fields) {
        owner.pending = String(++serial);
        notify();
        try { owner.worker.postMessage({ id: owner.pending, type, ...fields }); }
        catch { fail(owner); }
    }

    function fail(owner) {
        if (current !== owner) return;
        owner.worker?.terminate();
        owner.worker = undefined;
        owner.pending = undefined;
        owner.state = undefined;
        owner.failed = true;
        notify();
    }

    return {
        show(match, id) {
            if (!match?.analysisEnabled) {
                if (current) {
                    discard();
                    notify();
                }
                return;
            }
            const { reviewCheckpoint: checkpoint, viewPly: ply, revision } = match;
            if (current?.id === id && current.checkpoint === checkpoint && current.ply === ply) {
                if (current.match.blackBelow !== match.blackBelow) {
                    current.match = match;
                    notify();
                }
                return;
            }
            discard();
            const owner = current = { id, checkpoint, ply, revision, match };
            try {
                owner.worker = createWorker();
                owner.worker.addEventListener('error', () => fail(owner));
                owner.worker.addEventListener('messageerror', () => fail(owner));
                owner.worker.addEventListener('message', ({ data }) => {
                    if (current !== owner || !owner.pending || data?.id !== owner.pending) return;
                    if (data.error || data.analysis?.revision !== revision || data.analysis?.ply !== ply) return fail(owner);
                    owner.pending = undefined;
                    owner.state = data.analysis;
                    notify();
                });
                request(owner, 'match-analysis-open', { checkpoint, ply, expectedRevision: revision });
            } catch { fail(owner); }
        },
        command(action, index = 0) {
            if (!current?.state || current.pending) return false;
            request(current, 'match-analysis-action', { expectedRevision: current.revision, action, index });
            return true;
        },
        revealedMove() { return current?.state?.hint?.move ?? null; },
    };
}

export function guidedMatchMove(revealed, selected, square, legalMoves) {
    return !selected && revealed?.slice(2, 4) === square && legalMoves.includes(revealed) ? revealed : null;
}

const labels = {
    en: {
        show: 'Show AI line', nextHint: 'Show next AI line', shown: 'AI line shown', meter: 'Evaluation for White',
        replay: 'Open the calculated line on a board', line: 'Calculated line', close: 'Return to the position',
        previous: 'Previous move', next: 'Next move', choice: 'Line', step: 'Step', of: 'of', failed: 'AI analysis is unavailable.',
    },
    ro: {
        show: 'Arată varianta AI', nextHint: 'Arată următoarea variantă AI', shown: 'Varianta AI afișată', meter: 'Evaluarea albului',
        replay: 'Deschide varianta recomandată pe tablă', line: 'Linie calculată', close: 'Revino la poziție',
        previous: 'Mutarea anterioară', next: 'Mutarea următoare', choice: 'Linia', step: 'Pasul', of: 'din',
        failed: 'Analiza AI este indisponibilă.',
    },
};

export function bindInlineMatchAnalysis({ find, locale, createWorker, createBoard, cue, clearSelection }) {
    const words = labels[locale] ?? labels.en;
    const panel = find('match-inline-analysis');
    const meter = find('match-inline-meter');
    const score = find('match-inline-score');
    const hint = find('match-inline-hint');
    const replay = find('match-inline-replay');
    const announcement = find('match-inline-announcement');
    const dialog = find('match-inline-preview');
    const position = find('match-inline-position');
    const branches = find('match-inline-branches');
    const previous = find('match-inline-previous');
    const next = find('match-inline-next');
    let widget;
    const session = createInlineAnalysisSession({ createWorker, publish: render });

    const setText = (element, value) => { if (element.textContent !== value) element.textContent = value; };
    const label = (element, value) => element.setAttribute('aria-label', value);
    const notation = move => move.slice(0, 2) + '–' + move.slice(2, 4) + (move.length === 5 ? '=' + move[4].toUpperCase() : '');
    const makeButton = (title, action) => {
        const button = dialog.ownerDocument.createElement('button');
        button.type = 'button';
        button.textContent = title;
        button.addEventListener('click', action);
        return button;
    };

    function render(owner) {
        const frame = owner?.state;
        const shown = frame?.hint;
        const move = shown?.move;
        panel.hidden = meter.hidden = hint.hidden = !owner;
        cue(move, owner?.match.blackBelow);
        if (!owner) {
            if (dialog.open) dialog.close();
            setText(announcement, '');
            return;
        }
        setText(score, owner.failed ? words.failed : analysisScore(frame?.evaluation, locale));
        renderAnalysisMeter(meter, meter.firstElementChild, frame?.evaluation, owner.match.blackBelow, locale);
        hint.disabled = !shown?.count;
        hint.setAttribute('aria-pressed', Boolean(move));
        hint.title = move ? shown.count > 1 ? words.nextHint : words.shown : words.show;
        label(hint, hint.title);
        setText(announcement, analysisHintDescription(shown, locale));
        replay.hidden = !move;
        if (move) {
            setText(replay, notation(move));
            label(replay, words.replay + '. ' + analysisHintDescription(shown, locale));
        }
        const preview = frame?.preview;
        if (!preview) {
            if (dialog.open) dialog.close();
            return;
        }
        widget ??= createBoard(find('match-inline-preview-board'), () => {});
        widget.render({ ...preview, blackBelow: owner.match.blackBelow, locked: true });
        previous.disabled = preview.ply === 0;
        next.disabled = preview.ply >= preview.total;
        position.dataset.step = preview.ply;
        renderLinePosition(position, { ply: preview.ply, total: preview.total,
            text: words.step + ' ' + preview.ply + ' ' + words.of + ' ' + preview.total
                + (preview.lastMove ? ' · ' + notation(preview.lastMove) : '') });
        const key = preview.branches.join(' ');
        if (branches.dataset.moves !== key) {
            branches.dataset.moves = key;
            branches.replaceChildren(...preview.branches.map((_, index) =>
                makeButton(words.choice + ' ' + (index + 1), () => session.command('preview-branch', index))));
        }
        for (const [index, button] of Array.from(branches.children).entries()) button.setAttribute('aria-pressed', index === preview.branch);
        renderAnalysisEvidence(find('match-inline-evidence'), preview, owner.match.sideToMove, locale,
            title => makeButton(title, () => session.command('preview-proof')));
        if (!dialog.open) {
            rememberBoardSize(find('match-board'), dialog);
            dialog.showModal();
        }
    }

    label(meter, words.meter);
    label(hint, words.show);
    label(find('match-inline-close'), words.close);
    label(previous, words.previous);
    label(next, words.next);
    find('match-inline-preview-title').textContent = words.line;
    hint.addEventListener('click', () => {
        if (session.command('hint')) clearSelection();
    });
    replay.addEventListener('click', () => session.command('preview'));
    find('match-inline-close').addEventListener('click', () => session.command('preview-close'));
    dialog.addEventListener('cancel', event => { event.preventDefault(); session.command('preview-close'); });
    previous.addEventListener('click', () => session.command('preview-seek', Number(position.dataset.step) - 1));
    next.addEventListener('click', () => session.command('preview-seek', Number(position.dataset.step) + 1));
    return session;
}
