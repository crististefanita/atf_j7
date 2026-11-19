import { backLabel } from './screen-chrome.mjs';
import { boardPlacement } from './study-board.mjs';
import { renderLinePosition } from './line-navigation.mjs';

const checkpointKey = 'chesslab.opening-session.v1';
const vocabulary = {
  en: {
    title: 'Openings', back: 'Back', source: 'Source', white: 'White', black: 'Black',
    playWhite: 'Play White', playBlack: 'Play Black', previous: 'Previous move', next: 'Next move',
    your: 'Your move', reply: 'Book reply', paused: 'Book reply paused', complete: 'Line complete',
    review: 'Review', history: 'Review played moves', start: 'Starting position',
    hint: 'Lesson hint', move: 'Show move', hide: 'Hide hint', replay: 'Play from here',
    resume: 'Resume lesson', retry: 'Practise again', ai: 'AI analysis', hideAi: 'Hide AI',
    alternative: 'AI alternative · use Lesson hint for the taught line.',
    offLine: 'That move is legal. Try the continuation for this lesson.',
    error: 'Could not load the opening. Try again.', pending: 'AI analysis…', ended: 'AI analysis · Game over',
    showAi: 'Show best move', nextAi: 'Show next best move', preview: 'Replay recommendation',
    noComment: 'Evaluation', close: 'Close', promotion: 'Choose a promotion', explanation: 'Lesson explanation',
    turn: side => side + ' to move', tryPiece: piece => 'Try moving the ' + piece + '.',
    limit: (move, idea) => 'Lesson move ' + move + ' · ' + idea,
    mate: (side, moves) => side + ' · Mate in ' + moves,
    balanced: 'Balanced', stalemate: 'Draw · Stalemate', line: 'View line',
    depth: count => 'Compared at depth ' + count,
    estimate: side => 'Positional estimate for ' + side,
    difference: (start, end, change) => start + ' → ' + end + ' · Change ' + change,
    netMaterial: (notation, kind, gain) => notation + ' captures the ' + kind + ' · Material gain ' + gain,
    mateBy: (notation, kind, against) => (against ? 'The other side mates' : 'Checkmate') + ' · ' + kind + ' · ' + notation,
    checkedReply: move => 'Defense checked · ' + move,
    compare: (line, ply, move, piece, mate) => 'Line ' + line + ', ply ' + ply + ': ' + move + (mate ? ' gives mate with ' : ' captures ') + piece + '.',
    jump: 'Show this move on the board.',
    pieces: { PAWN: 'pawn', KNIGHT: 'knight', BISHOP: 'bishop', ROOK: 'rook', QUEEN: 'queen', KING: 'king' },
    promote: { q: 'Queen', r: 'Rook', b: 'Bishop', n: 'Knight' },
  },
  ro: {
    title: 'Deschideri', back: 'Înapoi', source: 'Sursă', white: 'Alb', black: 'Negru',
    playWhite: 'Cu albele', playBlack: 'Cu negrele', previous: 'Mutarea anterioară', next: 'Mutarea următoare',
    your: 'Rândul tău', reply: 'Replica din lecție', paused: 'Replica în așteptare', complete: 'Linie parcursă',
    review: 'Revizuire', history: 'Vezi mutările jucate', start: 'Poziția inițială',
    hint: 'Indiciu lecție', move: 'Arată mutarea', hide: 'Ascunde indiciul', replay: 'Rejoacă de aici',
    resume: 'Continuă lecția', retry: 'Exersează din nou', ai: 'Analiză AI', hideAi: 'Ascunde AI',
    alternative: 'Variantă AI · folosește Indiciu lecție pentru linia predată.',
    offLine: 'Mutarea este legală. Încearcă să urmezi continuarea din lecție.',
    error: 'Deschiderea nu s-a putut încărca. Încearcă din nou.', pending: 'Analiză AI…', ended: 'Analiză AI · Partidă încheiată',
    showAi: 'Arată cea mai bună mutare', nextAi: 'Arată următoarea variantă', preview: 'Redă variantă recomandată',
    noComment: 'Evaluare', close: 'Închide', promotion: 'Alege promovarea', explanation: 'Explicația lecției',
    turn: side => side + ' la mutare', tryPiece: piece => 'Încearcă să muți piesa ' + piece + '.',
    limit: (move, idea) => 'Mutarea din lecție ' + move + ' · ' + idea,
    mate: (side, moves) => side + ' · Mat în ' + moves,
    balanced: 'Echilibru', stalemate: 'Remiză · Pat', line: 'Vezi linia',
    depth: count => 'Comparație la adâncimea ' + count,
    estimate: side => 'Estimare pozițională pentru ' + side.toLowerCase(),
    difference: (start, end, change) => start + ' → ' + end + ' · Diferență ' + change,
    netMaterial: (notation, kind, gain) => notation + ' capturează ' + kind + ' · Câștig de material ' + gain,
    mateBy: (notation, kind, against) => (against ? 'Adversarul dă mat' : 'Șah mat') + ' · ' + kind + ' · ' + notation,
    checkedReply: move => 'Apărare verificată · ' + move,
    compare: (line, ply, move, piece, mate) => 'Linia ' + line + ', pasul ' + ply + ': ' + move + (mate ? ' dă mat cu piesa ' : ' capturează piesa ') + piece + '.',
    jump: 'Arată această mutare pe tablă.',
    pieces: { PAWN: 'pion', KNIGHT: 'cal', BISHOP: 'nebun', ROOK: 'turn', QUEEN: 'damă', KING: 'rege' },
    promote: { q: 'Damă', r: 'Turn', b: 'Nebun', n: 'Cal' },
  },
};

/** Keeps local adapter effects separate from the Java lesson state. */
export function createOpeningFlow({
  call, locale, change, fail, read, save, wait = setTimeout, cancel = clearTimeout,
}) {
  let current;
  let active = false;
  let visible = true;
  let busy = false;
  let epoch = 0;
  let timer = null;
  let entries = [];

  function stop() {
    if (timer !== null) cancel(timer);
    timer = null;
  }

  function schedule() {
    stop();
    if (!active || !visible || busy || !current?.canAutoReply) return;
    const saved = current.saved;
    timer = wait(() => {
      timer = null;
      if (current?.saved === saved && visible) void dispatch('REPLY');
    }, 450);
  }

  async function request(action, value = '', index = 0) {
    return call(action, { value, index, saved: current?.saved ?? '', option: locale });
  }

  async function dispatch(action, value, index) {
    if (!active || busy) return;
    stop();
    busy = true;
    const pending = epoch;
    let applied = false;
    try {
      const state = await request(action, value, index);
      if (pending !== epoch) return;
      current = state;
      save(state.saved);
      applied = true;
      change(entries, current);
    } catch (failure) {
      if (pending === epoch) fail(failure);
    } finally {
      if (pending === epoch) {
        busy = false;
        if (applied) schedule();
      }
    }
  }

  async function open() {
    const pending = ++epoch;
    active = true;
    busy = true;
    current = null;
    stop();
    try {
      const reply = await request('CATALOG');
      if (pending !== epoch) return;
      entries = reply.entries;
      const saved = read();
      if (saved) {
        try {
          const restored = await call('RESTORE', { value: '', index: 0, saved, option: locale });
          if (pending !== epoch) return;
          current = restored;
        } catch {
          if (pending !== epoch) return;
          save('');
        }
      }
      change(entries, current);
    } catch (failure) {
      if (pending === epoch) fail(failure);
    } finally {
      if (pending === epoch) {
        busy = false;
        schedule();
      }
    }
  }

  return {
    open, dispatch,
    get current() { return current; },
    analyze: () => request('ANALYZE'),
    catalog() {
      epoch++;
      stop();
      busy = false;
      current = null;
      save('');
      change(entries, null);
    },
    visibility(shown) { visible = shown; schedule(); },
    close() { epoch++; active = false; busy = false; stop(); },
  };
}

export function pickOpeningTap(view, selected, square, revealed = null) {
  if (!view.canPlay) return { selected, moves: [] };
  const taught = view.nextStep?.move;
  const guided = view.hint === 'SOLUTION' || (revealed && taught === revealed) ? taught : null;
  if (guided && (!selected || selected === guided.slice(0, 2)) && square === guided.slice(2, 4)) {
    return { selected, moves: [guided] };
  }
  const choices = selected && selected !== square ? view.legal.filter(move => move.startsWith(selected + square)) : [];
  if (choices.length) return { selected, moves: choices };
  return { selected: selected !== square && view.legal.some(move => move.startsWith(square)) ? square : null, moves: [] };
}

export function openingControls(view) {
  const action = view.reviewing ? 'REPLAY' : view.replyPaused ? 'RESUME' : view.phase === 'COMPLETE' ? 'RESTART' : 'THEORY';
  return {
    previous: view.ply > 0,
    next: view.ply < view.total,
    history: view.practicePly > 0,
    action,
    actionEnabled: action !== 'THEORY' || view.canPlay,
  };
}

export function openingComparisonJump(alternatives, decisive) {
  if (!decisive || !Number.isInteger(decisive.ply) || decisive.ply < 1) return null;
  const branch = alternatives.findIndex(line => line.move === decisive.branch);
  const target = alternatives[branch];
  if (!target || target.proof.addedDefense || target.frames[decisive.ply]?.lastMove !== decisive.move) return null;
  return { branch, ply: decisive.ply };
}

export function createOpeningStudy({ root, locale, call, home, board }) {
  const text = vocabulary[locale] ?? vocabulary.en;
  const document = root.ownerDocument;
  let lesson;
  let selected = null;
  let analysis;
  let analysisKey;
  let variant = -1;
  let releaseFocus = null;
  let active = false;

  function element(tag, label, className) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (label != null) node.textContent = label;
    return node;
  }

  function button(label, handle, className) {
    const node = element('button', label, className);
    node.type = 'button';
    node.addEventListener('click', handle);
    return node;
  }

  const heading = element('h2', text.title);
  heading.tabIndex = -1;
  const backButton = button('‹', back);
  backButton.setAttribute('aria-label', backLabel('OPENINGS', locale));
  const header = element('header', null, 'opening-header');
  header.setAttribute('data-chess-header', '');
  backButton.setAttribute('data-chess-back', '');
  header.append(backButton, heading);
  const catalog = element('div', null, 'opening-catalog');
  const workspace = element('div', null, 'opening-workspace');
  workspace.hidden = true;
  const navigation = element('div', null, 'opening-navigation');
  navigation.setAttribute('data-chess-navigation', '');
  const previous = button('‹', () => seek(lesson.ply - 1));
  const next = button('›', () => flow.dispatch('FORWARD'));
  previous.setAttribute('aria-label', text.previous);
  next.setAttribute('aria-label', text.next);
  const progress = button(null, reviewHistory);
  navigation.append(previous, progress, next);
  previous.setAttribute('data-chess-arrow', '');
  next.setAttribute('data-chess-arrow', '');

  const panel = element('div', null, 'opening-panel');
  const top = element('p', null, 'opening-seat');
  const bottom = element('p', null, 'opening-seat');
  const boardHost = element('div', null, 'board');
  const drawing = board(boardHost, chooseSquare);
  const meter = element('div', null, 'opening-evaluation-bar');
  meter.setAttribute('aria-hidden', 'true');
  const boardRow = element('div', null, 'opening-board-row');
  boardRow.append(meter, boardHost);
  panel.append(top, boardRow, bottom);

  const controls = element('div', null, 'opening-controls');
  const explanation = button(null, () => openDialog(text.explanation, [element('p', message.textContent, 'opening-message-full')]),
    'opening-message');
  explanation.setAttribute('aria-label', text.explanation);
  explanation.setAttribute('aria-haspopup', 'dialog');
  const message = element('span', null, 'opening-message-text');
  message.setAttribute('aria-hidden', 'true');
  explanation.append(message);
  const announcement = element('p', null, 'opening-message-announcement');
  announcement.setAttribute('role', 'status');
  const assessment = button(null, showPreview, 'opening-assessment');
  const actions = element('div', null, 'opening-actions');
  const guidance = button(text.hint, useGuidance);
  const aiToggle = button(text.ai, () => flow.dispatch('AI_TOGGLE'));
  const bulb = button('◉', revealAnalysis);
  bulb.setAttribute('aria-label', text.showAi);
  actions.append(guidance, aiToggle, bulb);
  controls.append(explanation, announcement, assessment, actions);
  const columns = element('div', null, 'opening-columns');
  columns.append(panel, controls);
  workspace.append(navigation, columns);
  const error = element('p', null, 'opening-error');
  error.hidden = true;
  error.setAttribute('role', 'alert');

  const dialog = element('dialog', null, 'opening-dialog');
  const dialogTitle = element('h3');
  dialogTitle.id = 'study-opening-dialog-title';
  dialog.setAttribute('aria-labelledby', dialogTitle.id);
  dialog.addEventListener('close', () => {
    if (active && releaseFocus?.isConnected) releaseFocus.focus();
    releaseFocus = null;
  });
  root.classList.add('opening-study');
  root.replaceChildren(header, error, catalog, workspace, dialog);

  function read() {
    try { return globalThis.sessionStorage.getItem(checkpointKey); } catch { return null; }
  }

  function save(value) {
    try {
      if (value) globalThis.sessionStorage.setItem(checkpointKey, value);
      else globalThis.sessionStorage.removeItem(checkpointKey);
    } catch { /* An unavailable session store still allows this lesson. */ }
  }

  function fail() {
    error.textContent = text.error;
    error.hidden = false;
  }

  const flow = createOpeningFlow({ call, locale, change, fail, read, save });

  function visibility() { flow.visibility(!document.hidden); }
  function close() {
    active = false;
    flow.close();
    document.removeEventListener('visibilitychange', visibility);
    if (dialog.open) dialog.close();
    analysisKey = null;
    analysis = null;
    variant = -1;
  }

  function open() {
    active = true;
    error.hidden = true;
    document.addEventListener('visibilitychange', visibility);
    visibility();
    return flow.open();
  }

  function openDialog(title, contents) {
    releaseFocus = document.activeElement;
    dialogTitle.textContent = title;
    const dialogHeader = element('div');
    dialogHeader.setAttribute('data-chess-header', '');
    const back = button('‹', () => dialog.close());
    back.setAttribute('data-chess-back', '');
    back.setAttribute('aria-label', text.close);
    dialogHeader.append(back, dialogTitle);
    dialog.replaceChildren(dialogHeader, ...contents);
    dialog.showModal();
  }

  function menu(entries) {
    catalog.replaceChildren(...entries.map(entry => {
      const card = button(null, () => overview(entry), 'opening-card');
      card.setAttribute('aria-label', [entry.title, entry.variation, entry.eco].join('. '));
      card.append(element('span', [entry.eco, entry.variation].join(' · '), 'opening-meta'), element('strong', entry.title));
      return card;
    }));
  }

  function overview(entry) {
    const choice = (label, side) => button(label, () => {
      dialog.close();
      void flow.dispatch('START', entry.id, side);
    });
    const source = element('a', text.source);
    source.href = entry.source;
    source.target = '_blank';
    source.rel = 'noopener noreferrer';
    const choices = element('div', null, 'opening-dialog-actions');
    choices.append(choice(text.playWhite, 0), choice(text.playBlack, 1), source);
    openDialog(entry.title, [element('p', entry.variation), element('p', entry.plan), choices]);
  }

  function seek(ply) { return flow.dispatch('SEEK', '', ply); }
  function back() {
    if (dialog.open) { dialog.close(); return; }
    if (lesson?.reviewing) { void flow.dispatch('BACK'); return; }
    if (lesson) flow.catalog();
    else home();
  }

  function change(entries, state) {
    const pageChanged = Boolean(lesson) !== Boolean(state);
    const before = lesson;
    lesson = state;
    selected = state?.canPlay && state.hint === 'SOLUTION' ? state.nextStep.move.slice(0, 2) : null;
    error.hidden = true;
    workspace.hidden = !lesson;
    catalog.hidden = Boolean(lesson);
    heading.textContent = lesson?.title ?? text.title;
    backButton.setAttribute('aria-label', backLabel(lesson ? 'OPENING' : 'OPENINGS', locale, Boolean(lesson?.reviewing)));
    if (lesson) {
      if (frameKey(lesson) !== frameKey(before) || lesson.hint !== before?.hint || !lesson.aiEnabled) variant = -1;
      drawLesson();
      void requestAnalysis();
    } else {
      analysis = null;
      analysisKey = null;
      variant = -1;
      menu(entries);
    }
    if (pageChanged || document.activeElement === document.body) heading.focus();
  }

  function status() {
    if (lesson.reviewing) return text.review;
    if (lesson.replyPaused) return text.paused;
    return { LEARNER: text.your, REPLY: text.reply, COMPLETE: text.complete }[lesson.phase];
  }

  function notation(move, ply) {
    const lead = Number.isInteger(ply) ? Math.floor((ply + 1) / 2) + (ply % 2 === 0 ? '… ' : '. ') : '';
    return lead + move.slice(0, 2) + ' → ' + move.slice(2, 4)
      + (move.length === 5 ? ' = ' + text.promote[move[4]] : '');
  }

  function caption(step, ply) {
    return step ? notation(step.move, ply) + ' · ' + step.idea : lesson.plan;
  }

  function currentMessage() {
    if (lesson.offLine) return text.offLine;
    if (lesson.reviewing) return caption(lesson.shownStep, lesson.ply);
    if (lesson.phase === 'COMPLETE') return lesson.plan;
    if (lesson.hint === 'SOLUTION') return text.limit(notation(lesson.nextStep.move, lesson.ply + 1), lesson.nextStep.idea);
    if (lesson.hint === 'PIECE') return text.tryPiece(text.pieces[lesson.hintPiece]);
    const current = caption(lesson.shownStep, lesson.ply);
    const suggestion = revealed()?.move;
    return suggestion && lesson.canPlay && suggestion !== lesson.nextStep.move ? text.alternative + '\n' + current : current;
  }

  function renderSeat(seat, side) {
    const moving = (lesson.reviewing || lesson.phase !== 'COMPLETE') && lesson.sideToMove === side;
    const label = side === 'WHITE' ? text.white : text.black;
    seat.textContent = moving ? text.turn(label) : label;
    seat.classList.toggle('moving', moving);
  }

  function drawLesson() {
    const blackBelow = lesson.learner === 'BLACK';
    const availability = openingControls(lesson);
    renderSeat(top, blackBelow ? 'WHITE' : 'BLACK');
    renderSeat(bottom, lesson.learner);
    renderLinePosition(progress, { ply: lesson.ply, total: lesson.total,
      text: [lesson.ply + ' / ' + lesson.total, status()].join(' · ') });
    progress.disabled = !availability.history;
    progress.setAttribute('aria-label', progress.textContent + (progress.disabled ? '' : '. ' + text.history));
    previous.disabled = !availability.previous;
    next.disabled = !availability.next;
    const caption = currentMessage();
    if (announcement.textContent !== caption) {
      message.textContent = caption;
      announcement.textContent = caption;
    }
    explanation.classList.toggle('off-line', lesson.offLine);
    guidance.textContent = {
      REPLAY: text.replay, RESUME: text.resume, RESTART: text.retry,
      THEORY: { NONE: text.hint, PIECE: text.move, SOLUTION: text.hide }[lesson.hint],
    }[availability.action];
    guidance.disabled = !availability.actionEnabled;
    aiToggle.textContent = lesson.aiEnabled ? text.hideAi : text.ai;
    aiToggle.setAttribute('aria-pressed', String(lesson.aiEnabled));
    renderAnalysis();

    const marks = [];
    const guided = lesson.nextStep?.move;
    const aiMove = revealed()?.move;
    if (guided && lesson.hint !== 'NONE') {
      marks.push(guided.slice(0, 2));
      if (lesson.hint === 'SOLUTION') marks.push(guided.slice(2, 4));
    } else if (aiMove) marks.push(aiMove.slice(0, 2), aiMove.slice(2, 4));
    drawing.render({ board: boardPlacement(lesson.fen), sideToMove: lesson.sideToMove, blackBelow, selected,
      targets: selected ? [...new Set(lesson.legal.filter(move => move.startsWith(selected)).map(move => move.slice(2, 4)))] : [],
      marks, lastMove: lesson.lastMove, locked: !lesson.canPlay });
  }

  function useGuidance() {
    const { action, actionEnabled } = openingControls(lesson);
    if (actionEnabled) void flow.dispatch(action);
  }

  function apply(move) { void flow.dispatch('PLAY', move); }
  function chooseSquare(square) {
    const chosen = pickOpeningTap(lesson, selected, square, revealed()?.move);
    if (chosen.moves.length === 1) { apply(chosen.moves[0]); return; }
    if (chosen.moves.length > 1) {
      openDialog(text.promotion, chosen.moves.map(move => button(text.promote[move[4]], () => {
        dialog.close();
        apply(move);
      })));
      return;
    }
    selected = chosen.selected;
    drawLesson();
  }

  function reviewHistory() {
    if (!lesson.practicePly) return;
    const choose = (label, ply) => button(label, () => {
      dialog.close();
      void seek(ply);
    });
    const moves = element('div', null, 'opening-history');
    moves.append(choose(text.start, 0), ...lesson.history.map((step, index) =>
      choose(caption(step, index + 1), index + 1)));
    openDialog(text.history, [moves]);
  }

  function frameKey(state) { return state ? state.id + '|' + state.ply : null; }

  function revealed() { return variant >= 0 && lesson?.aiEnabled && analysisKey === frameKey(lesson) ? analysis?.variants[variant] : null; }

  async function requestAnalysis() {
    if (!lesson?.aiEnabled) {
      analysis = null;
      analysisKey = null;
      return;
    }
    if (frameKey(lesson) === analysisKey) return;
    analysis = null;
    const key = frameKey(lesson);
    analysisKey = key;
    renderAnalysis();
    try {
      const received = await flow.analyze();
      if (!active || !lesson?.aiEnabled || frameKey(lesson) !== key || analysisKey !== key) return;
      analysis = received;
    } catch {
      if (!active || !lesson?.aiEnabled || frameKey(lesson) !== key || analysisKey !== key) return;
      analysis = { failed: true, evaluation: null, variants: [] };
    }
    renderAnalysis();
  }

  function evaluationText(evaluation) {
    if (!evaluation) return text.ended;
    if (evaluation.ending === 'CHECKMATE') {
      return text.mate(evaluation.whiteMateMoves > 0 ? text.white : text.black, Math.abs(evaluation.whiteMateMoves));
    }
    if (evaluation.ending === 'STALEMATE') return text.stalemate;
    const centipawns = evaluation.whiteCentipawns;
    return pawnLabel(centipawns) + ' · ' + (centipawns === 0 ? text.balanced : centipawns > 0 ? text.white : text.black);
  }

  function pawnLabel(centipawns) {
    const number = new Intl.NumberFormat(locale, { signDisplay: 'exceptZero', minimumFractionDigits: 2, maximumFractionDigits: 2 });
    return number.format(centipawns / 100).replace('-', '−');
  }

  function renderAnalysis() {
    const ready = analysisKey === frameKey(lesson) ? analysis : null;
    for (const control of [assessment, meter, bulb]) control.toggleAttribute('data-withheld', !lesson.aiEnabled);
    bulb.disabled = !ready?.variants.length;
    const suggestion = revealed();
    assessment.disabled = !suggestion;
    assessment.setAttribute('aria-label', suggestion ? text.preview + ' · ' + notation(suggestion.move) : text.noComment);
    const evaluation = ready?.failed ? text.error : ready ? evaluationText(ready.evaluation) : text.pending;
    const label = suggestion && ready.variants.length > 1 ? (variant + 1) + '/' + ready.variants.length + ' · ' : '';
    assessment.textContent = suggestion ? evaluation + '\n' + text.line + ' · ' + label + notation(suggestion.move) : evaluation;
    const share = ready?.evaluation?.whiteShare;
    meter.toggleAttribute('data-measured', share != null);
    meter.toggleAttribute('data-black-below', lesson.learner === 'BLACK');
    if (share != null) meter.style.setProperty('--opening-white-share', (share * 100) + '%');
    bulb.setAttribute('aria-label', suggestion && ready.variants.length > 1 ? text.nextAi : text.showAi);
    bulb.setAttribute('aria-pressed', String(Boolean(suggestion)));
  }

  async function revealAnalysis() {
    if (!analysis?.variants.length || analysisKey !== frameKey(lesson)) return;
    if (lesson.hint !== 'NONE' || lesson.offLine) await flow.dispatch('CLEAR_HINT');
    if (!active || !lesson?.aiEnabled || !analysis?.variants.length || analysisKey !== frameKey(lesson) || lesson.hint !== 'NONE') return;
    variant = (variant + 1) % analysis.variants.length;
    const suggestion = revealed();
    selected = lesson.canPlay && suggestion.move === lesson.nextStep.move ? suggestion.move.slice(0, 2) : null;
    drawLesson();
  }

  function showPreview() {
    const suggestion = revealed();
    if (!suggestion) return;
    const alternatives = suggestion.previewMoves.map(move => analysis.variants.find(item => item.move === move)).filter(Boolean);
    const cursors = Array(alternatives.length).fill(0);
    let branch = alternatives.indexOf(suggestion);
    if (branch < 0) return;
    const host = element('div', null, 'board');
    const preview = board(host, () => {});
    const status = element('span');
    status.setAttribute('aria-live', 'polite');
    status.tabIndex = -1;
    const row = element('div', null, 'opening-navigation');
    row.setAttribute('data-chess-navigation', '');
    const choices = element('div', null, 'opening-dialog-actions');
    choices.setAttribute('data-chess-lines', '');
    const depth = element('p');
    const evidence = element('p');
    const estimates = element('p');
    const jump = button(null, () => {
      const location = openingComparisonJump(alternatives, alternatives[branch].decisive);
      if (!location) return;
      branch = location.branch;
      cursors[branch] = location.ply;
      advance(0);
      status.focus();
    });
    const earlier = button('‹', () => advance(-1));
    const later = button('›', () => advance(1));
    earlier.setAttribute('aria-label', text.previous);
    later.setAttribute('aria-label', text.next);
    row.append(earlier, status, later);
    earlier.setAttribute('data-chess-arrow', '');
    later.setAttribute('data-chess-arrow', '');
    alternatives.forEach((candidate, index) => choices.append(button((index + 1) + ' · ' + notation(candidate.move), () => {
      branch = index;
      advance(0);
    })));
    choices.hidden = alternatives.length === 1;
    function advance(delta) {
      const current = alternatives[branch];
      const ply = cursors[branch] += delta;
      const frame = current.frames[ply];
      const proof = current.proof;
      preview.render({ board: boardPlacement(frame.fen), sideToMove: frame.sideToMove, blackBelow: lesson.learner === 'BLACK',
        locked: true, targets: [], marks: ply === 0 ? [current.move.slice(0, 2), current.move.slice(2, 4)] : [], lastMove: frame.lastMove });
      const last = ply === current.frames.length - 1;
      const played = frame.lastMove ? notation(frame.lastMove) : text.start;
      renderLinePosition(status, { ply, total: current.frames.length - 1,
        text: ply + ' / ' + (current.frames.length - 1) + ' · ' + (last && proof.addedDefense ? text.checkedReply(played) : played) });
      earlier.disabled = ply === 0;
      later.disabled = last;
      depth.hidden = current.depth === 0;
      depth.textContent = text.depth(current.depth);
      estimates.hidden = !proof.estimate;
      if (proof.estimate) {
        estimates.textContent = text.difference(pawnLabel(proof.estimate.initial), pawnLabel(proof.estimate.end), pawnLabel(proof.estimate.change));
      }
      evidence.textContent = proof.reason === 'CHECKMATE' || proof.reason === 'CHECKMATE_AGAINST'
        ? text.mateBy(notation(proof.move), text.pieces[proof.piece], proof.reason === 'CHECKMATE_AGAINST')
        : proof.reason === 'CAPTURE' ? text.netMaterial(notation(proof.move), text.pieces[proof.captured], proof.netMaterial)
          : proof.reason === 'STALEMATE' ? text.stalemate : text.estimate(lesson.sideToMove === 'WHITE' ? text.white : text.black);
      const decisive = current.decisive;
      const location = openingComparisonJump(alternatives, decisive);
      evidence.hidden = Boolean(location);
      jump.hidden = !location;
      if (location) {
        const label = text.compare(location.branch + 1, location.ply, notation(decisive.move), text.pieces[decisive.piece], decisive.reason === 'MATE');
        jump.textContent = label;
        jump.setAttribute('aria-label', label + ' ' + text.jump);
      }
      [...choices.children].forEach((button, index) => button.setAttribute('aria-pressed', String(index === branch)));
    }
    openDialog(text.preview, [depth, choices, row, host, estimates, evidence, jump]);
    advance(0);
  }

  return { open, close, back };
}
