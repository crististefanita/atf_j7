import { revealDistance } from './review-focus.mjs';
import { hintArrow } from './hint-arrow.mjs';
import { reviewGraphText, reviewPositionText, updateReviewRange } from './review-announcement.mjs';

const axisInset = 2;
const colors = { axis: '#56564c', curve: '#b8d88a', arrow: '#57e0b4', arrowOutline: '#173b31' };
const find = id => document.getElementById(id);
const levels = { BASIC: 'Basic', ADVANCED: 'Advanced', HARD: 'Hard' };

const labels = {
    en: {
        graph: 'Evaluation by move · White above the center',
        start: 'Starting position', initial: 'Choose a move, graph point, or key moment to inspect.',
        played: 'Played', recommended: 'Recommended', next: 'Next', chooseLine: 'Choose a line to replay',
        linePosition: (branch, step, length) => `${branch} line. Step ${step} of ${length}.`,
        selectedScore: (best, played, depth) => `Expected score · Best ${best}% · Played ${played}% · depth ${depth} plies`,
        sacrifice: pawns => `The strongest reply accepts a sacrifice worth ${pawns} pawns.`,
        white: 'White', black: 'Black', human: 'Player', ai: 'AI', category: 'Move type',
        accuracy: value => `Accuracy ${value}`, rated: 'Game Elo', anchor: 'AI reference Elo',
        insufficient: count => `Too few scored moves (${count})`,
        modeNote: {
            REVIEW_MATCH: 'Your estimate uses this result against the AI reference. The interval shows its uncertainty.',
            REVIEW_MANUAL: 'Elo estimates are unavailable when you played moves for the AI side.',
            REVIEW_LIVE: 'Elo is available after the game ends.',
            REVIEW_STUDY: 'The report shows the accuracy of each color for this study line.',
        },
        grade: {
            BOOK: 'Book', FORCED: 'Forced', BRILLIANT: 'Brilliant', GREAT: 'Great', BEST: 'Best', EXCELLENT: 'Excellent', GOOD: 'Good',
            INACCURACY: 'Inaccuracy', MISTAKE: 'Mistake', MISS: 'Miss', BLUNDER: 'Blunder',
        },
        promotion: { q: 'queen', r: 'rook', b: 'bishop', n: 'knight' },
    },
    ro: {
        graph: 'Evaluare pe mutări · avantajul albului deasupra centrului',
        start: 'Poziția de start', initial: 'Alege o mutare, un punct de pe grafic sau un moment-cheie.',
        played: 'Jucată', recommended: 'Recomandată', next: 'Urmează', chooseLine: 'Alege linia de redat',
        linePosition: (branch, step, length) => `Linia ${branch.toLowerCase()}. Pasul ${step} din ${length}.`,
        selectedScore: (best, played, depth) => `Scor estimat · Recomandată ${best}% · Jucată ${played}% · adâncime ${depth}`,
        sacrifice: pawns => `Cea mai puternică replică acceptă un sacrificiu de ${pawns} pioni.`,
        white: 'Alb', black: 'Negru', human: 'Jucător', ai: 'AI', category: 'Tipul mutării',
        accuracy: value => `Precizie ${value}`, rated: 'Elo în partidă', anchor: 'Elo de referință AI',
        insufficient: count => `Prea puține mutări evaluate (${count})`,
        modeNote: {
            REVIEW_MATCH: 'Estimarea ta folosește rezultatul partidei față de reperul AI. Intervalul arată incertitudinea.',
            REVIEW_MANUAL: 'Estimările Elo sunt indisponibile dacă ai jucat mutări pentru AI.',
            REVIEW_LIVE: 'Estimarea Elo devine disponibilă după încheierea partidei.',
            REVIEW_STUDY: 'Raportul arată precizia fiecărei culori în linia de studiu.',
        },
        grade: {
            BOOK: 'De carte', FORCED: 'Forțată', BRILLIANT: 'Strălucită', GREAT: 'Excelentă tactic', BEST: 'Cea mai bună', EXCELLENT: 'Excelentă',
            GOOD: 'Bună', INACCURACY: 'Imprecizie', MISTAKE: 'Greșeală', MISS: 'Ocazie ratată', BLUNDER: 'Greșeală gravă',
        },
        promotion: { q: 'damă', r: 'turn', b: 'nebun', n: 'cal' },
    },
};

function canvasContext(canvas, width, height) {
    const ratio = window.devicePixelRatio || 1;
    const pixelWidth = Math.max(1, Math.round(width * ratio));
    const pixelHeight = Math.max(1, Math.round(height * ratio));
    if (canvas.width !== pixelWidth) canvas.width = pixelWidth;
    if (canvas.height !== pixelHeight) canvas.height = pixelHeight;
    const context = canvas.getContext('2d');
    context.setTransform(pixelWidth / width, 0, 0, pixelHeight / height, 0, 0);
    context.clearRect(0, 0, width, height);
    return context;
}

function drawCue(state) {
    const cue = find('review-cue');
    const source = find('board').querySelector(`[data-square="${state.cueFrom ?? ''}"]`);
    const destination = find('board').querySelector(`[data-square="${state.cueTo ?? ''}"]`);
    cue.hidden = !source || !destination || source === destination;
    if (cue.hidden) return;
    const bounds = cue.getBoundingClientRect();
    const a = source.getBoundingClientRect();
    const b = destination.getBoundingClientRect();
    const unit = a.width;
    if (unit <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0 || bounds.width <= 0 || bounds.height <= 0) {
        cue.hidden = true;
        return;
    }
    const dx = b.x + b.width / 2 - a.x - unit / 2;
    const dy = b.y + b.height / 2 - a.y - a.height / 2;
    const shape = hintArrow(dx, dy, unit);
    const context = canvasContext(cue, bounds.width, bounds.height);
    context.translate(a.x + unit / 2 - bounds.x, a.y + a.height / 2 - bounds.y);
    context.rotate(shape.radians);
    const { start, tip, head, halfShaft: shaft, halfHead: arrowHead } = shape;
    context.beginPath();
    context.moveTo(start, -shaft);
    for (const [x, y] of [[head, -shaft], [head, -arrowHead], [tip, 0], [head, arrowHead], [head, shaft], [start, shaft]]) context.lineTo(x, y);
    context.closePath();
    context.lineJoin = 'round';
    context.lineWidth = shape.outline;
    context.strokeStyle = colors.arrowOutline;
    context.stroke();
    context.fillStyle = colors.arrow;
    context.fill();
}

function drawGraph(canvas, marker, state) {
    const { width, height } = canvas.getBoundingClientRect();
    if (!width || !height) return;
    const context = canvasContext(canvas, width, height);
    const points = state.whitePoints;
    const last = points.length - 1;
    const span = width - 2 * axisInset;
    const xFor = ply => last && span > 0 ? axisInset + span * ply / last : width / 2;
    const yFor = points => axisInset + (height - 2 * axisInset) * (1 - points / 1000);
    const axisStart = Math.min(axisInset, width / 2);
    context.beginPath();
    context.moveTo(axisStart, height / 2);
    context.lineTo(width - axisStart, height / 2);
    context.strokeStyle = colors.axis;
    context.lineWidth = 1;
    context.stroke();
    context.beginPath();
    context.moveTo(xFor(0), yFor(points[0]));
    for (let ply = 1; ply <= last; ply++) context.lineTo(xFor(ply), yFor(points[ply]));
    context.strokeStyle = colors.curve;
    context.lineWidth = 2;
    context.stroke();
    marker.style.left = `${xFor(state.ply)}px`;
    marker.style.top = `${yFor(points[state.ply])}px`;
}

function fitWorkspace(workspace) {
    const { width, height } = workspace.getBoundingClientRect();
    const boardHeight = Math.max(0, height - workspace.querySelector('.review-controls').getBoundingClientRect().height);
    const stacked = Math.min(width, boardHeight - Math.min(132, Math.floor(boardHeight / 3)));
    const panelLimit = width - 248 - 8;
    const split = panelLimit >= 240 ? Math.min(panelLimit, boardHeight) : 0;
    const sideBySide = width > height && split > stacked;
    workspace.dataset.layout = sideBySide ? 'split' : 'stacked';
    workspace.style.setProperty('--review-board-side', `${sideBySide ? split : stacked}px`);
    workspace.style.setProperty('--review-panel-width', `${sideBySide ? Math.max(240, split) : width}px`);
}

function appendCell(row, tag, value) {
    const cell = document.createElement(tag);
    cell.textContent = value;
    row.append(cell);
}

function showPlayers(state, words, countFormat) {
    const panel = find('review-players');
    panel.hidden = !state.players || !state.mode;
    if (panel.hidden) return;
    const study = state.mode === 'REVIEW_STUDY';
    const rated = state.mode === 'REVIEW_MATCH';
    const first = study ? 'WHITE' : state.humanSide ?? 'WHITE';
    const second = first === 'WHITE' ? 'BLACK' : 'WHITE';
    const nameFor = side => side === 'WHITE' ? words.white : words.black;
    const card = (side, actor, ratingLabel) => {
        const player = state.players[side];
        const info = document.createElement('p');
        if (!player) return info;
        const title = study ? nameFor(side) : `${player.name ?? actor} · ${nameFor(side)}`;
        const rating = player.rating;
        const range = rating ? `≈ ${rating.elo} (${rating.low == null ? '≤ ' + rating.high : rating.low + '–' + rating.high})`
            : words.insufficient(player.ratedMoves ?? 0);
        const accuracy = words.accuracy(player.accuracy == null ? '—' : `${player.accuracy}%`);
        info.append(title, document.createElement('br'), rated ? `${accuracy} · ${ratingLabel} ${range}` : accuracy);
        return info;
    };
    const opponent = levels[state.level] ? `${words.ai} ${levels[state.level]}` : words.ai;
    find('review-ratings').replaceChildren(card(first, words.human, words.rated), card(second, opponent, words.anchor));
    find('review-rating-note').textContent = words.modeNote[state.mode] ?? '';
    const table = find('review-comparison');
    table.replaceChildren();
    if (!state.gradeCounts?.length) return;
    const heading = table.createTHead().insertRow();
    for (const value of [words.category, study ? words.white : words.human, study ? words.black : words.ai]) appendCell(heading, 'th', value);
    const rows = table.createTBody();
    for (const grade of state.gradeCounts) {
        const row = rows.insertRow();
        appendCell(row, 'th', words.grade[grade.grade] ?? grade.grade);
        for (const count of [grade.human, grade.ai]) appendCell(row, 'td', countFormat.format(count));
    }
}

export function createReviewWorkspace(locale, selectGraph) {
    const words = labels[locale];
    const fractional = new Intl.NumberFormat(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
    const decimal = new Intl.NumberFormat(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const count = new Intl.NumberFormat(locale);
    const workspace = find('review');
    const graph = find('review-graph');
    const marker = find('review-graph-marker');
    const details = find('review-details');
    let state;
    let frame;
    let comparisonKey;
    let lastMoves;
    graph.dataset.inset = axisInset;
    find('review-graph-label').textContent = words.graph;

    function schedule() {
        if (frame !== undefined) return;
        frame = requestAnimationFrame(() => {
            frame = undefined;
            if (!state || workspace.hidden) return;
            fitWorkspace(workspace);
            if (!graph.hidden) drawGraph(graph, marker, state);
            drawCue(state);
        });
    }

    const observer = new ResizeObserver(schedule);
    observer.observe(workspace);
    observer.observe(workspace.querySelector('.review-controls'));
    graph.addEventListener('click', event => {
        const box = graph.getBoundingClientRect();
        if (state?.totalPlies && box.width > 0) selectGraph(event.clientX - box.left, box.width);
    });

    function moveName(move) {
        const promotion = words.promotion[move?.[4]];
        return move ? `${move.slice(0, 2)} → ${move.slice(2, 4)}${promotion ? ` = ${promotion}` : ''}` : '';
    }

    function moveLabel(view, ply) {
        if (ply === 0) return words.start;
        const prefix = `${Math.ceil(ply / 2)}${ply % 2 ? '.' : '…'}`;
        const grade = words.grade[view.gradeKeys?.[ply - 1]];
        return [prefix + ' ' + moveName(view.moves[ply - 1]), grade].filter(Boolean).join(' · ');
    }

    function positionText(view, ply = view.ply) {
        return reviewPositionText(locale, moveLabel(view, ply), ply, view.totalPlies);
    }

    function announcementText(view) {
        if (view.branch === 'HISTORY') return positionText(view);
        const branch = view.branch === 'PLAYED' ? words.played : words.recommended;
        return `${positionText(view)} ${words.linePosition(branch, view.step, view.lineLength)}`;
    }

    function graphReady(view) {
        return Array.isArray(view.whitePoints) && view.whitePoints.length === view.totalPlies + 1;
    }

    function graphAnnouncementText(view) {
        const points = graphReady(view) ? view.whitePoints[view.ply] : null;
        return reviewGraphText(locale, positionText(view), Number.isFinite(points) ? fractional.format(points / 10) : null);
    }

    function render(next) {
        const moves = next.moves.join(' ');
        if (moves !== lastMoves) details.scrollTop = 0;
        lastMoves = moves;
        state = next;
        const ready = graphReady(next);
        graph.hidden = marker.hidden = !ready;
        find('review-graph-label').hidden = !ready;
        find('review-graph-plot').toggleAttribute('data-ready', ready);
        graph.dataset.ply = marker.dataset.ply = next.ply;
        graph.dataset.total = next.totalPlies;
        if (ready) graph.dataset.whitePoints = next.whitePoints[next.ply];
        updateReviewRange(find('seek'), next.ply, next.totalPlies, graphAnnouncementText(next));
        const opening = find('review-opening');
        opening.hidden = !next.atEntry || !next.opening;
        opening.textContent = next.opening ? `${next.opening.name} · ${next.opening.eco}` : '';
        const inLine = next.branch !== 'HISTORY';
        const branch = next.branch === 'PLAYED' ? words.played : words.recommended;
        const step = `${next.step} / ${next.lineLength}`;
        find('position').textContent = next.explanationOpen ? (inLine ? `${branch} · ${step}` : words.chooseLine)
            : `${moveLabel(next, next.ply)}${inLine ? ` · ${next.step}/${next.lineLength}` : ''}`;
        const score = next.ply ? words.selectedScore(fractional.format(next.bestPoints / 10), fractional.format(next.playedPoints / 10), next.depth) : words.initial;
        find('review-score').textContent = next.grade === 'BRILLIANT' && next.sacrificeCp > 0
            ? `${score} · ${words.sacrifice(decimal.format(next.sacrificeCp / 100))}` : score;
        const line = find('review-line');
        line.hidden = !inLine;
        line.textContent = `${branch} · ${step}` +
            (next.nextLineMove ? ` · ${words.next}: ${moveName(next.nextLineMove)}` : '');
        const comparison = JSON.stringify([next.players, next.mode, next.humanSide, next.level, next.gradeCounts]);
        if (comparison !== comparisonKey) {
            comparisonKey = comparison;
            showPlayers(next, words, count);
        }
        schedule();
    }

    function reveal(element) {
        if (!element) return;
        const viewport = details.getBoundingClientRect();
        const selected = element.getBoundingClientRect();
        details.scrollTop += revealDistance(viewport.top, viewport.bottom, selected.top, selected.bottom);
    }

    function revealFocus(element) {
        if (details.contains(element)) reveal(element);
        const toolbar = element.closest('#movement');
        if (!toolbar) return;
        const viewport = toolbar.getBoundingClientRect();
        const selected = element.getBoundingClientRect();
        toolbar.scrollLeft += revealDistance(viewport.left, viewport.right, selected.left, selected.right);
    }

    return { render, moveLabel, positionText, announcementText, graphAnnouncementText, revealFocus,
        revealSelected: () => reveal(find('history').querySelector('[aria-current="step"]')),
        revealExplanation: () => reveal(find('why-title')) };
}

export function showReviewPiece(element, type, art) {
    const shape = art?.[type];
    if (!shape) {
        if (element.dataset.shape) {
            element.removeAttribute('data-shape');
            element.replaceChildren();
        }
        return false;
    }
    if (element.dataset.shape !== type) {
        const namespace = 'http://www.w3.org/2000/svg';
        const svg = element.ownerDocument.createElementNS(namespace, 'svg');
        svg.setAttribute('viewBox', '0 0 48 48');
        svg.setAttribute('preserveAspectRatio', 'none');
        for (const [name, data, width] of [['body', shape.body, 1.35], ['details', shape.details, 1.12]]) {
            const path = element.ownerDocument.createElementNS(namespace, 'path');
            path.setAttribute('d', data);
            path.setAttribute('class', `piece-${name}`);
            path.setAttribute('stroke-width', width);
            path.setAttribute('stroke-linejoin', 'round');
            path.setAttribute('stroke-linecap', 'round');
            svg.append(path);
        }
        element.replaceChildren(svg);
        element.dataset.shape = type;
    }
    return true;
}
