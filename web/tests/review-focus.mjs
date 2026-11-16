import assert from 'node:assert/strict';

function readFocus() {
    const active = document.activeElement;
    const rows = document.querySelectorAll('#history button[aria-current=step]');
    const row = rows[0];
    const report = document.querySelector('#review-details');
    const showing = element => Boolean(element?.getClientRects().length);
    function visible(element, bounds = null) {
        if (!showing(element)) return false;
        const rect = element.getBoundingClientRect();
        return rect.left >= -.5 && rect.right <= innerWidth + .5 && rect.top >= Math.max(0, bounds?.top ?? 0) - .5
            && rect.bottom <= Math.min(innerHeight, bounds?.bottom ?? innerHeight) + .5;
    }
    const clip = report.getBoundingClientRect();
    const seek = document.querySelector('#seek');
    const focusTarget = active === seek ? document.querySelector('#review-graph-plot') : active;
    const board = document.querySelector('#board');
    const { x, y, width, height } = board.getBoundingClientRect();
    return {
        focus: active.id || (active.dataset.ply !== undefined ? 'row-' + active.dataset.ply : active.tagName),
        focusVisible: visible(focusTarget, report.contains(focusTarget) ? clip : null),
        keyboardFocus: active.matches(':focus-visible'),
        rowPly: Number(row?.dataset.ply),
        rowCount: rows.length,
        rowVisible: visible(row, clip),
        rowTabStops: [...document.querySelectorAll('#history button')]
            .filter(button => button.tabIndex === 0 && showing(button)).map(button => Number(button.dataset.ply)),
        plotPly: Number(document.querySelector('#review-graph').dataset.ply),
        seekVisible: showing(seek),
        seekValue: Number(seek.value),
        seekLabel: seek.getAttribute('aria-valuetext') ?? '',
        whyExpanded: document.querySelector('#why').getAttribute('aria-expanded') === 'true',
        board: [x, y, width, height],
        boardVisible: visible(board),
        viewport: [innerWidth, innerHeight]
    };
}

function fontSizes() {
    return [document.documentElement, document.querySelector('#why'), document.querySelector('#history button[aria-current]')]
        .map(element => Number.parseFloat(getComputedStyle(element).fontSize));
}

export async function exerciseReviewFocus({ evaluate, until, wait, touch, pressKey, capture, check, openMigratedReview, playedFen, accessibility }) {
    const { archiveId } = await openMigratedReview(4);
    await accessibility?.observe('before-toolbar');
    accessibility?.begin('toolbar');
    await touch('#previous');
    await wait('the historical Fool’s Mate blunder at ply 3', review => review.ply === 3 && review.selected === 3);
    await accessibility?.observe('after-toolbar');
    const read = () => evaluate('(' + readFocus.toString() + ')()');
    const measureFonts = () => evaluate('(' + fontSizes.toString() + ')()');
    const baseFonts = await measureFonts();
    await evaluate("document.documentElement.style.fontSize = '130%'");
    const scaledFonts = await measureFonts();
    check('font-enlarged', true, baseFonts.every((value, index) => value >= 12 &&
        Math.abs(scaledFonts[index] - value * 1.3) < .1));

    const focusEvidence = [];
    async function focused(id) {
        let last;
        return until(async () => {
            last = await read();
            return last.focus === id && last.focusVisible ? last : null;
        }, 'visible keyboard focus on ' + id).catch(error => {
            throw new Error(error.message + '. Last focus witness: ' + JSON.stringify(last), { cause: error });
        });
    }
    async function witness(id, focus, ply, why = false) {
        const review = await wait(id, value => value.archiveId === archiveId && value.ply === ply && value.why === why);
        const target = await focused(focus);
        const result = { id, ply, fen: review.fen, lastMove: review.last, turn: review.turn, branch: review.branch, why, ...target };
        focusEvidence.push(result);
        await accessibility?.observe(id);
        return result;
    }
    async function tabChain(initial, expected, shift = false) {
        const trace = [initial];
        for (const id of expected) {
            await pressKey('Tab', shift);
            const current = await focused(id);
            assert.ok(current.keyboardFocus, 'Tab should give keyboard-indicated focus to ' + id);
            trace.push(current.focus);
        }
        return trace;
    }

    await evaluate("document.querySelector('#archive-review-back').focus()");
    const hasNextKey = await evaluate("!document.querySelector('#next-key').disabled");
    const afterBoard = ['board', 'previous', 'best-line', 'next', ...(hasNextKey ? ['next-key'] : []), 'why', 'review-actions', 'seek'];
    const normalOrder = await tabChain((await read()).focus, afterBoard);
    check('review-tab-order', true, normalOrder[0] === 'archive-review-back');
    await accessibility?.observe('graph-before');

    accessibility?.begin('graph-left');
    await pressKey('ArrowLeft');
    const left = await witness('graph-left', 'seek', 2);
    if (accessibility) await until(() => accessibility.sawSliderChange('graph-left', 2), 'Edge AX graph update for ply 2');
    accessibility?.begin('graph-right');
    await pressKey('ArrowRight');
    const right = await witness('graph-right', 'seek', 3);
    if (accessibility) await until(() => accessibility.sawSliderChange('graph-right', 3), 'Edge AX graph update for ply 3');
    check('graph-retains-focus', true, [left, right].every(entry => entry.keyboardFocus && entry.plotPly === entry.ply &&
        entry.seekValue === entry.ply && entry.rowPly === entry.ply && entry.seekLabel.includes(String(entry.ply))));
    await capture('focus-graph.png');

    accessibility?.begin('selected-row');
    await pressKey('Tab');
    const selected = await witness('selected-row', 'row-3', 3);
    check('selected-row-only-tab-stop', true, selected.keyboardFocus && selected.rowVisible && selected.rowCount === 1 &&
        selected.rowTabStops.length === 1 && selected.rowTabStops[0] === 3);
    await capture('focus-history.png');
    const rowVisits = [['row-up', 'ArrowUp', 2], ['row-end', 'End', 4], ['row-home', 'Home', 0], ['row-down', 'ArrowDown', 1],
        ['row-end-again', 'End', 4], ['row-return', 'ArrowUp', 3]];
    for (const [id, key, ply] of rowVisits) {
        await pressKey(key);
        const row = await witness(id, 'row-' + ply, ply);
        assert.ok(row.rowVisible && row.keyboardFocus && row.rowCount === 1 &&
            row.rowPly === ply && row.rowTabStops.length === 1 && row.rowTabStops[0] === ply);
    }
    check('history-navigation-keys', true, true);

    const reverseOrder = await tabChain((await read()).focus, ['seek', 'review-actions', 'why'], true);
    accessibility?.begin('why-entry');
    await pressKey('Enter');
    const entered = await witness('why-entry', 'close-why', 3, true);
    const whyOrder = await tabChain(entered.focus, ['why-actions', 'board', 'played', 'recommended']);
    check('why-entry-order', true, entered.keyboardFocus && !entered.seekVisible && !entered.rowVisible && entered.whyExpanded);
    accessibility?.begin('why-recommended');
    await pressKey('Enter');
    const recommended = await witness('why-recommended', 'recommended', 3, true);
    assert.equal(recommended.branch, 'RECOMMENDED');
    await capture('focus-why.png');

    accessibility?.begin('why-escape');
    await pressKey('Escape');
    const escaped = await witness('why-escape', 'why', 3);
    check('escape-restores-launcher', true, escaped.keyboardFocus && !escaped.whyExpanded && escaped.branch === 'HISTORY' &&
        escaped.rowPly === 3 && escaped.fen === playedFen);
    accessibility?.begin('why-reopen');
    await pressKey('Enter');
    await witness('why-reopen', 'close-why', 3, true);
    accessibility?.begin('why-back');
    await pressKey('Enter');
    const closed = await witness('why-back', 'why', 3);
    check('back-restores-launcher', true, closed.keyboardFocus && !closed.whyExpanded && closed.branch === 'HISTORY' &&
        closed.rowPly === 3 && closed.fen === playedFen);
    await capture('focus-return.png');
    const anchored = focusEvidence.filter(entry => !entry.why);
    check('square-board-stable', true, anchored.every(entry => entry.boardVisible && entry.board[2] === entry.board[3] &&
        entry.board.every((value, index) => Math.abs(value - right.board[index]) < 1)));
    const analysisRequests = await evaluate('window.chessWebAudit.requests.length');
    check('cached-review-used', 0, analysisRequests);
    return { archiveId, baseFonts, scaledFonts, normalOrder, reverseOrder, whyOrder, focusEvidence, analysisRequests,
        accessibility: accessibility?.evidence() };
}
