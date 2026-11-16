import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { mkdir, mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { basename, dirname, extname, join, relative, resolve, sep } from 'node:path';
import { setTimeout as pause } from 'node:timers/promises';
import { pathToFileURL } from 'node:url';

const args = process.argv.slice(2);
assert.ok(args.length === 4 || args.length === 5, 'Usage: node browser-game-review.mjs <site> <archive.json|--active|--ai|--ledger|--archives|--review-continue> <new-evidence-directory> <edge-exe> [public-ai-url]');
const [site, , evidence, browserPath] = args.map(path => resolve(path));
const activeScenario = args[1] === '--active';
const aiScenario = args[1] === '--ai';
const ledgerScenario = args[1] === '--ledger';
const archivesScenario = args[1] === '--archives';
const reviewContinueScenario = args[1] === '--review-continue';
const fixturePath = activeScenario || aiScenario || ledgerScenario || archivesScenario || reviewContinueScenario ? null : resolve(args[1]);
const hostPath = '/atf_j7/';
const publicUrl = args[4];
const readmeUrl = `https://crististefanita.github.io${hostPath}#ai`;
const relativeEvidence = relative(site, evidence);
assert.ok(relativeEvidence === '..' || relativeEvidence.startsWith('..' + sep), 'Keep evidence outside the built site');
const viewport = { width: 390, height: 844, deviceScaleFactor: 1, locale: publicUrl ? 'en-US' : 'ro-RO' };
const profile = `390x844 CSS px, DPR 1, locale ${viewport.locale}`;
const runId = randomUUID();
const observations = [];
const captures = [];
const fixtureBytes = fixturePath === null ? null : await readFile(fixturePath);
const fixture = fixtureBytes && JSON.parse(fixtureBytes.toString('utf8'));
const anchor = fixture?.expected;
const reviewGraphScenario = fixture?.scope === 'review-graph';
if (publicUrl) assert.ok((aiScenario || ledgerScenario || archivesScenario || reviewContinueScenario || reviewGraphScenario) && publicUrl === readmeUrl,
    `Public smoke requires --ai, --ledger, --archives, --review-continue or the review graph fixture and the README URL ${readmeUrl}`);
if (reviewGraphScenario) {
    assert.equal(anchor.totalPlies, 185);
    assert.deepEqual(anchor.samples.map(sample => sample.ply), [0, 93, 184, 185]);
} else if (fixturePath !== null) {
    assert.equal(anchor.ply, 3);
    assert.equal(anchor.playedUci, 'g2g4');
    assert.equal(anchor.matingUci, 'd8h4');
    assert.equal(anchor.recommendedUci, 'd2d4');
}

const startOfLinesFen = 'rnbqkbnr/pppp1ppp/8/4p3/8/5P2/PPPPP1PP/RNBQKBNR';
const playedFen = 'rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR';
const recommendedFen = 'rnbqkbnr/pppp1ppp/8/4p3/3P4/5P2/PPP1P1PP/RNBQKBNR';
const mateFen = 'rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR';
const legacyLocalCheckpoint = '1|f2f3 e7e5 g2g4';
const legacyAiCheckpoint = 'ai|3|WHITE|ADVANCED|0|d2d4 e7e5';
const archiveACheckpoint = `${legacyLocalCheckpoint} d8h4`;
const archiveBCheckpoint = '3|BLACK|ADVANCED|0|f2f4 e7e6 g2g4 d8h4';

function check(id, expected, actual) {
    const passed = Object.is(expected, actual);
    observations.push({ id, expected, actual, passed });
    assert.ok(passed, `${id} expected ${JSON.stringify(expected)}, observed ${JSON.stringify(actual)}`);
}

const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const files = new Map();
for (const entry of await readdir(site, { recursive: true, withFileTypes: true })) {
    if (entry.isDirectory()) continue;
    assert.ok(entry.isFile(), 'The generated site must contain regular files');
    const path = join(entry.parentPath, entry.name);
    files.set(relative(site, path).split(sep).join('/'), await readFile(path));
}
const manifest = [...files.keys()].sort().map(path => `${sha256(files.get(path))}  ${path}\n`).join('');
const bundleSha256 = sha256(manifest);
let preparedReview;
if (reviewContinueScenario) {
    const { analyzeReview } = await import(pathToFileURL(join(site, 'chess-engine.mjs')).href);
    const digest = createHash('sha256').update(archiveBCheckpoint).digest('base64');
    const review = analyzeReview(archiveBCheckpoint, digest, () => {});
    preparedReview = JSON.stringify({ id: 'edge-archived-continue', checkpoint: archiveBCheckpoint, review });
}
const mime = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8', '.wasm': 'application/wasm', '.json': 'application/json' };
const server = createServer((request, response) => {
    const pathname = new URL(request.url, 'http://127.0.0.1').pathname;
    const path = pathname.startsWith(hostPath) ? pathname.slice(hostPath.length) || 'index.html' : '';
    const bytes = files.get(path);
    if (request.method !== 'GET' || !bytes) {
        response.writeHead(404).end();
        return;
    }
    response.writeHead(200, { 'Content-Type': mime[extname(path)] ?? 'application/octet-stream', 'Cache-Control': 'no-store' }).end(bytes);
});

async function until(probe, label) {
    const deadline = Date.now() + 12000;
    let error;
    while (Date.now() < deadline) {
        try {
            const value = await probe();
            if (value) return value;
        } catch (failure) {
            error = failure;
        }
        await pause(70);
    }
    throw new Error(`Timed out waiting for ${label}`, { cause: error });
}

function client(socket) {
    const pending = new Map();
    let sequence = 0;
    socket.addEventListener('message', event => {
        const data = JSON.parse(event.data);
        const request = pending.get(data.id);
        if (!request) return;
        clearTimeout(request.timeout);
        pending.delete(data.id);
        if (data.error) request.reject(new Error(data.error.message));
        else request.resolve(data.result);
    });
    socket.addEventListener('close', () => {
        for (const request of pending.values()) {
            clearTimeout(request.timeout);
            request.reject(new Error('Edge debugging session closed'));
        }
        pending.clear();
    });
    return (method, params = {}) => {
        const id = ++sequence;
        return new Promise((resolve, reject) => {
            const timeout = setTimeout(() => {
                pending.delete(id);
                reject(new Error(`Edge did not answer ${method}`));
            }, 8000);
            pending.set(id, { resolve, reject, timeout });
            socket.send(JSON.stringify({ id, method, params }));
        });
    };
}

await mkdir(evidence);
if (publicUrl) {
    await Promise.all([...files].filter(([path]) => path !== '.nojekyll').map(async ([path, expected]) => {
        const resource = new URL(path === 'index.html' ? './' : path, publicUrl);
        const response = await fetch(resource, { cache: 'no-store', redirect: 'error', signal: AbortSignal.timeout(15000) });
        assert.equal(response.status, 200, `Public runtime file unavailable: ${path}`);
        const actual = Buffer.from(await response.arrayBuffer());
        assert.equal(sha256(actual), sha256(expected), `Public runtime file differs from the qualified bundle: ${path}`);
    }));
} else {
    await new Promise((resolve, reject) => {
        server.once('error', reject);
        server.listen(0, '127.0.0.1', resolve);
    });
}
const page = publicUrl ?? `http://127.0.0.1:${server.address().port}${hostPath}?lang=ro`;
const profileDirectory = await mkdtemp(join(tmpdir(), 'j7-review-edge-'));
const browser = spawn(browserPath, ['--headless=new', '--disable-gpu', '--disable-background-networking',
    '--disable-extensions', '--no-first-run', '--remote-debugging-port=0', `--user-data-dir=${profileDirectory}`, 'about:blank'],
{ windowsHide: true, stdio: 'ignore' });
let browserError;
browser.on('error', error => { browserError = error; });
let socket;
let browserVersion;

try {
    const port = await until(async () => {
        if (browserError) throw browserError;
        return (await readFile(join(profileDirectory, 'DevToolsActivePort'), 'utf8')).split(/\r?\n/)[0];
    }, 'Edge DevTools port');
    const endpoint = `http://127.0.0.1:${port}`;
    const version = await (await fetch(`${endpoint}/json/version`)).json();
    browserVersion = version.Browser;
    const targets = await (await fetch(`${endpoint}/json/list`)).json();
    socket = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
        socket.addEventListener('open', resolve, { once: true });
        socket.addEventListener('error', reject, { once: true });
    });
    const send = client(socket);
    const evaluate = async expression => {
        const { result, exceptionDetails } = await send('Runtime.evaluate', { expression, returnByValue: true });
        if (exceptionDetails) throw new Error(result.description ?? exceptionDetails.text);
        return result.value;
    };
    const visible = selector => `!document.querySelector(${JSON.stringify(selector)}).hidden`;
    const displayed = selector => evaluate(`Boolean(document.querySelector(${JSON.stringify(selector)})?.getClientRects().length)`);
    const state = () => evaluate(`(() => {
        const review = document.querySelector('#review');
        if (review.hidden || review.getAttribute('aria-busy') !== 'false') return null;
        const board = document.querySelector('#board');
        const rect = board.getBoundingClientRect();
        return { ply: Number(review.dataset.ply), step: Number(review.dataset.step), branch: review.dataset.branch, archiveId: review.dataset.archiveId,
            fen: board.dataset.fen, last: board.dataset.lastMove, turn: board.dataset.turn, orientation: board.dataset.orientation,
            board: [rect.x + scrollX, rect.y + scrollY, rect.width, rect.height], panelWidth: board.parentElement.clientWidth,
            cells: board.children.length, selected: Number(document.querySelector('[aria-current="step"]').dataset.ply),
            why: ${visible('#explanation')}, mateSquare: document.querySelector('#reason').dataset.square,
            canContinue: ${visible('#continue-review')},
            reason: document.querySelector('#reason').textContent, nextDisabled: document.querySelector('#next').disabled,
            queen: board.querySelector('[data-square="h4"]').dataset.piece,
            checked: document.querySelector('[aria-pressed="true"]')?.id,
            status: document.querySelector('#status').textContent, lang: document.documentElement.lang,
        };
    })()`);
    const wait = (label, condition) => until(async () => {
        const result = await state();
        return result && condition(result) ? result : null;
    }, label).catch(async failure => {
        const diagnostic = await evaluate(`({ hash: location.hash, status: document.querySelector('#status').textContent,
            reviewHidden: document.querySelector('#review').hidden, analyzing: !document.querySelector('#analysis-progress').hidden,
            requests: window.chessWebAudit?.requests, replies: window.chessWebAudit?.reviews.length,
            graphTaps: window.chessWebAudit?.graphTaps, ply: document.querySelector('#review').dataset.ply })`).catch(() => null);
        throw new Error(`${failure.message}. Browser state: ${JSON.stringify(diagnostic)}`, { cause: failure });
    });

    async function touch(selector) {
        const point = await evaluate(`(() => {
            const element = document.querySelector(${JSON.stringify(selector)});
            if (!element || element.disabled || !element.getClientRects().length) return null;
            element.scrollIntoView({block:'nearest'});
            const rect = element.getBoundingClientRect();
            return {x: rect.x + rect.width / 2, y: rect.y + rect.height / 2};
        })()`);
        assert.ok(point, `Expected active browser control ${selector}`);
        await send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] });
        await send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    }

    async function capture(file, dimensions = viewport) {
        const { data } = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
        const bytes = Buffer.from(data, 'base64');
        assert.equal(bytes.readUInt32BE(16), dimensions.width);
        assert.equal(bytes.readUInt32BE(20), dimensions.height);
        await writeFile(join(evidence, file), bytes, { flag: 'wx' });
        captures.push({ file, sha256: sha256(bytes) });
    }

    const manualKey = 'chesslab.active-match.v1';
    const aiKey = 'chesslab.ai-match.v1';
    const ledgerKey = 'chesslab.match-ledger.v1';
    const savedMatch = "document.querySelector('#match').dataset.checkpoint";
    const savedReview = "JSON.parse(localStorage.getItem('chesslab.game-review.v1'))";
    const activeState = () => evaluate(`(() => {
        const match = document.querySelector('#match');
        if (match.hidden || match.getAttribute('aria-busy') !== 'false') return null;
        const board = document.querySelector('#match-board');
        const rect = board.getBoundingClientRect();
        return { ply: Number(match.dataset.ply), viewPly: Number(match.dataset.viewPly), turn: board.dataset.turn, last: board.dataset.lastMove,
            result: match.dataset.result, fen: board.dataset.fen, cells: board.children.length, width: rect.width, height: rect.height,
            mode: match.dataset.mode, level: match.dataset.level, humanSide: match.dataset.humanSide, awaitingBot: match.dataset.awaitingBot === 'true',
            readOnly: match.dataset.readOnly === 'true', id: match.dataset.matchId, slot: Number(match.dataset.slot),
            orientation: board.dataset.orientation, upperLeft: board.firstElementChild.dataset.square,
            lowerRight: board.lastElementChild.dataset.square, profile: document.querySelector('#match-profile').textContent,
            announcing: document.querySelector('#match-turn').textContent, urlPath: location.pathname,
            analyzing: !document.querySelector('#analysis-progress').hidden, analyzeDisabled: document.querySelector('#analyze').disabled,
            checkpoint: match.dataset.checkpoint };
    })()`);
    const waitActive = (label, predicate) => until(async () => {
        const state = await activeState();
        return state && predicate(state) ? state : null;
    }, label);
    const square = key => `#match-board button[data-square="${key}"]`;

    async function move(from, to, ply) {
        await touch(square(from));
        const controls = await evaluate(`(() => {
            const origin = document.querySelector(${JSON.stringify(square(from))});
            const target = document.querySelector(${JSON.stringify(square(to))});
            return origin.getAttribute('aria-pressed') === 'true' && target.hasAttribute('data-target');
        })()`);
        assert.ok(controls, `Expected a valid visible move ${from}${to}`);
        await touch(square(to));
        return waitActive(`position after ${from}${to}`, value => value.ply === ply);
    }

    async function reload() {
        const previousPage = await evaluate('performance.timeOrigin');
        await send('Page.reload', { ignoreCache: true });
        await until(async () => (await evaluate('performance.timeOrigin')) !== previousPage, 'new page after a real reload');
    }

    async function chooseLastOption(selector, value) {
        await evaluate(`document.querySelector(${JSON.stringify(selector)}).focus()`);
        const key = { key: 'End', code: 'End', windowsVirtualKeyCode: 35 };
        await send('Input.dispatchKeyEvent', { ...key, type: 'rawKeyDown' });
        await send('Input.dispatchKeyEvent', { ...key, type: 'keyUp' });
        await until(async () => await evaluate(`document.querySelector(${JSON.stringify(selector)}).value`) === value, `chosen option ${value}`);
    }

    async function exerciseAi() {
        if (publicUrl) {
            await until(() => evaluate(`${visible('#ai-setup')} && location.href === ${JSON.stringify(publicUrl)} && document.documentElement.lang === 'en'`),
                'English AI setup from the exact public README link');
            await touch('#open-review');
        }
        await touch('#open-match');
        await waitActive('fresh manual game', value => value.mode === 'LOCAL');
        const manual = await move('d2', 'd4', 1);
        assert.equal(manual.checkpoint, '1|d2d4');
        await touch('#open-review');
        await touch('#open-ai');
        await until(() => evaluate(visible('#ai-setup')), 'native AI setup');
        const choices = await evaluate(`[...document.querySelector('#ai-level').options].map(option => option.value).join(',')`);
        assert.equal(choices, 'BASIC,ADVANCED,HARD');
        await chooseLastOption('#ai-side', 'BLACK');
        await chooseLastOption('#ai-level', 'HARD');
        await touch('#start-ai');
        const pending = await waitActive('black player before first AI reply', value => value.ply === 0 && value.awaitingBot);
        const pendingMatchCheckpoint = '3|BLACK|HARD|0|';
        check('ai-setup', true, pending.mode === 'AI' && pending.humanSide === 'BLACK' && pending.level === 'HARD' &&
            pending.profile.includes('Hard') && pending.urlPath === hostPath);
        check('ai-black-orientation', true, pending.orientation === 'BLACK' && pending.upperLeft === 'h1' && pending.lowerRight === 'a8');
        const waitingLabel = publicUrl ? 'AI to move…' : 'AI este la mutare…';
        check('bot-pending', true, pending.checkpoint === `ai|${pendingMatchCheckpoint}` && pending.turn === 'WHITE' &&
            pending.analyzeDisabled && pending.announcing === waitingLabel);
        await capture('ai-pending.png');
        await reload();

        const resumed = await waitActive('restored pending AI reply', value => value.ply === 0 && value.awaitingBot);
        check('bot-reloaded', true, resumed.checkpoint === pending.checkpoint && resumed.fen === pending.fen &&
            resumed.orientation === pending.orientation && resumed.profile === pending.profile && resumed.urlPath === hostPath);
        await capture('ai-resumed.png');
        const replied = await waitActive('settled AI reply', value => value.ply === 1 && !value.awaitingBot);
        check('bot-reply', 'e2e4', replied.last);
        const completedMatchCheckpoint = pendingMatchCheckpoint + replied.last;
        assert.equal(replied.checkpoint, `ai|${completedMatchCheckpoint}`);
        assert.equal(replied.turn, 'BLACK');
        await capture('ai-replied.png');
        await reload();
        await waitActive('restored settled game', value => value.ply === 1 && !value.awaitingBot);
        await pause(725);
        const settled = await activeState();
        check('bot-applied-once', true, settled?.checkpoint === replied.checkpoint && settled.ply === 1 && settled.last === replied.last &&
            settled.turn === 'BLACK' && !settled.awaitingBot);

        await touch('#open-review');
        await touch('#open-match');
        const manualRestored = await waitActive('original manual game', value => value.mode === 'LOCAL' && value.ply === 1);
        await touch('#open-review');
        await touch('#open-ai');
        const aiRestored = await waitActive('AI game remains independently saved', value => value.mode === 'AI' && value.ply === 1);
        check('manual-independent', true, manualRestored.checkpoint === manual.checkpoint && manualRestored.fen === manual.fen &&
            aiRestored.checkpoint === replied.checkpoint);
        return { scope: 'ai-bot-reload', hostPath, publicUrl, pendingCheckpoint: pending.checkpoint, completedCheckpoint: replied.checkpoint,
            pendingMatchCheckpoint, completedMatchCheckpoint, botMove: replied.last };
    }

    async function exerciseActive() {

        await touch('#open-match');
        const initial = await waitActive('initial game', value => value.ply === 0);
        check('new-match', true, initial.checkpoint === '1|' && initial.result === 'IN_PROGRESS' && initial.turn === 'WHITE' &&
            initial.cells === 64 && initial.width === initial.height && initial.width > 350 && initial.analyzeDisabled);
        const first = await move('f2', 'f3', 1);
        check('first-move', 'f2f3', first.last);
        assert.equal(first.turn, 'BLACK');
        await move('e7', 'e5', 2);
        const recorded = await move('g2', 'g4', 3);
        check('recorded-line', true, recorded.checkpoint === '1|f2f3 e7e5 g2g4' && recorded.fen === playedFen && recorded.turn === 'BLACK');
        await capture('active-after-ply3.png');

        await touch('#analyze');
        await waitActive('analysis started in separate worker', value => value.analyzing);
        await touch('#cancel-analysis');
        const canceled = await waitActive('analysis canceled', value => !value.analyzing && !value.analyzeDisabled);
        check('analysis-canceled', true, canceled.checkpoint === recorded.checkpoint && await evaluate(savedReview) === null);

        await reload();
        const resumed = await waitActive('resumed active match', value => value.ply === 3);
        check('reloaded-active', true, resumed.fen === recorded.fen && resumed.checkpoint === recorded.checkpoint &&
            resumed.result === 'IN_PROGRESS' && resumed.last === 'g2g4' && resumed.turn === 'BLACK');
        await capture('active-reloaded.png');

        await touch('#analyze');
        const partial = await wait('fresh review of unfinished line', value => value.ply === 3 && value.branch === 'HISTORY');
        const partialArchive = await evaluate(savedReview);
        check('unfinished-analysis', true, partial.fen === recorded.fen && typeof partialArchive.review === 'string' && await evaluate(savedMatch) === recorded.checkpoint);
        if (await displayed('#review-close')) await touch('#review-close');
        await touch('#open-match');
        await waitActive('returned from unfinished analysis to same match', value => value.ply === 3);
        const complete = await move('d8', 'h4', 4);
        check('continued-move', 'd8h4', complete.last);
        assert.equal(complete.result, 'BLACK_WINS');
        assert.equal(complete.fen, mateFen);
        assert.equal(complete.checkpoint, '1|f2f3 e7e5 g2g4 d8h4');

        await touch('#analyze');
        const fresh = await wait('fresh review of checkmate', value => value.ply === 4 && value.branch === 'HISTORY');
        const generated = await evaluate(`window.chessWebAudit.reviews.find(record => record.checkpoint === ${JSON.stringify(complete.checkpoint)})`);
        check('analysis-archive', true, generated.checkpoint === complete.checkpoint && generated.review.length > 0 &&
            generated.review !== partialArchive.review && Boolean(fresh.archiveId) && Boolean(await evaluate(`localStorage.getItem('${ledgerKey}')`)));
        check('analysis-opened', true, fresh.fen === mateFen && fresh.last === complete.last && fresh.selected === 4 && fresh.cells === 64);
        await capture('fresh-review.png');
        await touch('button[data-ply="3"]');
        await wait('mistake at exact move', value => value.ply === 3);
        await touch('#why');
        const proof = await wait('fresh explanation', value => value.why);
        check('analysis-why', true, proof.mateSquare === 'h4' && proof.reason === 'Replica dă mat cu dama pe h4.');
        await capture('fresh-review-why.png');
        await touch('#close-why');
        const history = await wait('recorded history after closing Why', value => value.ply === 3 && value.branch === 'HISTORY' && !value.why);
        check('history-restored', true, history.fen === playedFen && history.last === recorded.last && await evaluate(savedMatch) === complete.checkpoint);
        return { scope: 'active-game-review', initialCheckpoint: initial.checkpoint, resumedCheckpoint: resumed.checkpoint,
            recordedCheckpoint: complete.checkpoint, unfinishedReview: partialArchive.review, generatedReview: generated.review };
    }

    async function exerciseLedger() {
        const listRows = () => evaluate(`(() => {
            if (document.querySelector('#match-list').hidden) return null;
            return [...document.querySelectorAll('.match-row')].map(row => ({ ...row.dataset, timestamp: row.querySelector('time')?.dateTime }));
        })()`);
        const waitRows = (label, count) => until(async () => {
            const rows = await listRows();
            return rows?.length === count ? rows : null;
        }, label);

        if (publicUrl) {
            await waitActive('migrated AI match from the public README link', value => value.mode === 'AI' && value.ply === 2);
            assert.equal(await evaluate('location.href'), publicUrl);
            assert.equal(await evaluate('document.documentElement.lang'), 'en');
            await touch('#open-review');
        }
        await touch('#open-saved');
        const migrated = await waitRows('two migrated saved matches', 2);
        const legacyLocalId = migrated.find(row => row.mode === 'LOCAL')?.id;
        const legacyAiId = migrated.find(row => row.mode === 'AI')?.id;
        const hasLedgerAndBackups = await evaluate(`Boolean(localStorage.getItem('${ledgerKey}')) &&
            localStorage.getItem('${manualKey}') === ${JSON.stringify(legacyLocalCheckpoint)} &&
            localStorage.getItem('${aiKey}') === ${JSON.stringify(legacyAiCheckpoint)}`);
        check('legacy-migration', true, hasLedgerAndBackups && Boolean(legacyLocalId) && Boolean(legacyAiId) && legacyLocalId !== legacyAiId);
        check('saved-only-active', true, migrated.every(row => row.result === 'IN_PROGRESS' && Number(row.slot) > 0) &&
            migrated.find(row => row.id === legacyLocalId).ply === '3' && migrated.find(row => row.id === legacyAiId).ply === '2');
        await capture('saved-games.png');
        await touch('#return-menu');
        await touch('#open-finished');
        await waitRows('empty finished list before mate', 0);
        await touch('#return-menu');
        await touch('#open-saved');
        await touch(`.match-row[data-id="${legacyLocalId}"]`);
        const opening = await waitActive('saved manual game', value => value.ply === 3 && value.id === legacyLocalId);
        assert.equal(opening.checkpoint, legacyLocalCheckpoint);
        const completed = await move('d8', 'h4', 4);
        check('mate-archived', true, completed.result === 'BLACK_WINS' && completed.checkpoint === `${legacyLocalCheckpoint} d8h4` && completed.fen === mateFen);

        if (completed.readOnly) {
            await touch('#return-menu');
            await touch('#return-menu');
        } else await touch('#open-review');
        await touch('#open-saved');
        const remaining = await waitRows('finished game removed from saved matches', 1);
        assert.equal(remaining[0].id, legacyAiId);
        await touch('#return-menu');
        await touch('#open-finished');
        const finished = await waitRows('one completed result', 1);
        const archiveId = finished[0].id;
        check('finished-only-complete', true, archiveId === legacyLocalId && finished[0].result === 'BLACK_WINS' &&
            Number(finished[0].slot) === 0 && finished[0].ply === '4' && Boolean(finished[0].timestamp));
        await capture('finished-games.png');

        await touch(`.match-row[data-id="${archiveId}"]`);
        const archived = await waitActive('read-only archived board', value => value.id === archiveId && value.readOnly);
        check('archive-open', true, archived.checkpoint === completed.checkpoint && archived.fen === mateFen && archived.viewPly === 4);
        await touch('#match-previous');
        await waitActive('archived history at ply 3', value => value.viewPly === 3);
        await touch('#match-previous');
        const selected = await waitActive('archived history at ply 2', value => value.viewPly === 2);
        assert.equal(selected.fen, startOfLinesFen);
        assert.equal(selected.ply, 4);
        await capture('selected-history.png');
        await touch('#continue-match');
        await until(() => evaluate('document.querySelector(\'#continue-dialog\').open'), 'native continuation confirmation');
        await touch('#confirm-continue');
        const branch = await waitActive('new active branch', value => value.ply === 2 && !value.readOnly && value.id !== archiveId);
        const forkId = branch.id;
        const forkOriginCheckpoint = '1|f2f3 e7e5';
        check('history-fork', true, Boolean(forkId) && branch.checkpoint === forkOriginCheckpoint && branch.fen === selected.fen && branch.viewPly === 2);
        const playedBranch = await move('d2', 'd4', 3);
        assert.equal(playedBranch.checkpoint, `${forkOriginCheckpoint} d2d4`);
        await reload();
        const restored = await waitActive('same branch after full browser reload', value => value.id === forkId && value.ply === 3);
        check('fork-reloaded', true, restored.checkpoint === playedBranch.checkpoint && restored.fen === recommendedFen && restored.slot === branch.slot);
        await capture('resumed-fork.png');

        await touch('#open-review');
        await touch('#open-saved');
        const savedAfterIds = (await waitRows('branch and original AI match', 2)).map(row => row.id);
        assert.deepEqual(savedAfterIds, [forkId, legacyAiId]);
        await touch(`.match-row[data-id="${legacyAiId}"]`);
        const separateAi = await waitActive('separate AI match preserved', value => value.id === legacyAiId && value.mode === 'AI');
        assert.equal(separateAi.checkpoint, legacyAiCheckpoint);
        await touch('#open-review');
        await touch('#open-finished');
        const finishedAfterIds = (await waitRows('original archive after forking', 1)).map(row => row.id);
        await touch(`.match-row[data-id="${archiveId}"]`);
        const archiveAgain = await waitActive('preserved original archived board', value => value.id === archiveId && value.readOnly);
        check('archive-unchanged', true, archiveAgain.checkpoint === archived.checkpoint && archiveAgain.fen === archived.fen &&
            finishedAfterIds.length === 1 && finishedAfterIds[0] === archiveId);
        return { scope: 'match-ledger', publicUrl, legacyLocalCheckpoint, legacyAiCheckpoint, legacyLocalId, legacyAiId, archiveId,
            archiveCheckpoint: archiveAgain.checkpoint, forkId, forkPly: branch.ply, forkOriginCheckpoint: branch.checkpoint,
            forkCheckpoint: restored.checkpoint, separateAiCheckpoint: separateAi.checkpoint, savedAfterIds, finishedAfterIds };
    }

    async function exerciseArchives() {
        const requests = () => evaluate('window.chessWebAudit.requests');
        const entries = () => evaluate(`(() => {
            if (document.querySelector('#match-list').hidden) return null;
            return [...document.querySelectorAll('.match-row')].map(row => ({ ...row.dataset }));
        })()`);
        const finishList = () => until(async () => {
            const rows = await entries();
            return rows?.length === 2 ? rows : null;
        }, 'two finished archive entries');

        async function openArchived(id, checkpoint) {
            await touch(`.match-row[data-id="${id}"]`);
            const stored = await waitActive('exact archived game', game => game.id === id && game.readOnly);
            assert.equal(stored.checkpoint, checkpoint);
            await touch('#analyze');
            return wait('archive review with stable ID', review => review.archiveId === id);
        }

        async function backToResults(id) {
            if (await displayed('#why-actions')) {
                await touch('#why-actions');
                await touch('#review-on-board');
            } else await touch('#archive-review-back');
            await waitActive('returned to exact archive', game => game.id === id && game.readOnly);
            await touch('#return-menu');
            return finishList();
        }

        if (publicUrl) {
            await until(() => evaluate(`${visible('#ai-setup')} && location.href === ${JSON.stringify(publicUrl)} && document.documentElement.lang === 'en'`),
                'public preview starts in AI setup');
            await touch('#open-review');
        }
        await touch('#open-finished');
        const initial = await finishList();
        const archiveAId = initial.find(row => row.mode === 'LOCAL')?.id;
        const archiveBId = initial.find(row => row.mode === 'AI')?.id;
        check('two-archives', true, Boolean(archiveAId) && Boolean(archiveBId) && archiveAId !== archiveBId &&
            initial.every(row => row.result === 'BLACK_WINS' && row.reviewed === 'false'));

        const firstA = await openArchived(archiveAId, archiveACheckpoint);
        check('analysis-a', true, firstA.ply === 4 && firstA.fen === mateFen && firstA.orientation === 'WHITE' &&
            JSON.stringify(await requests()) === JSON.stringify([archiveACheckpoint]));
        await touch('button[data-ply="3"]');
        await wait('selected archived error', review => review.ply === 3);
        await touch('#why');
        const why = await wait('archived proof of mate', review => review.why);
        assert.equal(why.mateSquare, 'h4');
        await touch('#played');
        await wait('archived played branch', review => review.branch === 'PLAYED' && review.step === 0);
        await touch('#next');
        await wait('first played continuation', review => review.step === 1);
        await touch('#next');
        const bookmarkA = await wait('archive A bookmark', review => review.step === 2);
        check('archive-a-bookmark', true, bookmarkA.archiveId === archiveAId && bookmarkA.ply === 3 && bookmarkA.branch === 'PLAYED' &&
            bookmarkA.why && bookmarkA.fen === mateFen && bookmarkA.last === 'd8h4');
        await capture('archive-a-why.png');

        const withA = await backToResults(archiveAId);
        assert.equal(withA.find(row => row.id === archiveAId).reviewed, 'true');
        assert.equal(withA.find(row => row.id === archiveBId).reviewed, 'false');
        const firstB = await openArchived(archiveBId, 'ai|' + archiveBCheckpoint);
        check('analysis-b', true, firstB.ply === 4 && firstB.last === 'd8h4' && firstB.fen !== firstA.fen && firstB.orientation === 'BLACK' &&
            JSON.stringify(await requests()) === JSON.stringify([archiveACheckpoint, archiveBCheckpoint]));
        await touch('button[data-ply="1"]');
        const bookmarkB = await wait('archive B bookmark', review => review.ply === 1);
        check('archive-b-bookmark', true, bookmarkB.archiveId === archiveBId && bookmarkB.branch === 'HISTORY' && bookmarkB.step === 0 &&
            !bookmarkB.why && bookmarkB.last === 'f2f4');
        await capture('archive-b-history.png');

        await reload();
        const restoredB = await wait('archive B restored after full page reload', review => review.archiveId === archiveBId && review.ply === 1);
        check('archive-b-reloaded', true, restoredB.branch === 'HISTORY' && !restoredB.why && restoredB.fen === bookmarkB.fen && restoredB.orientation === 'BLACK' &&
            (await requests()).length === 0);
        await capture('archive-b-reloaded.png');

        const bothReviewed = await backToResults(archiveBId);
        assert.ok(bothReviewed.every(row => row.reviewed === 'true'));
        const reopenedA = await openArchived(archiveAId, archiveACheckpoint);
        check('archive-a-reopened', true, reopenedA.ply === bookmarkA.ply && reopenedA.branch === bookmarkA.branch && reopenedA.step === bookmarkA.step &&
            reopenedA.fen === bookmarkA.fen && reopenedA.why && (await requests()).length === 0);
        await capture('archive-a-reopened.png');
        await backToResults(archiveAId);
        const reopenedB = await openArchived(archiveBId, 'ai|' + archiveBCheckpoint);
        const ledgerSnapshot = await evaluate(`localStorage.getItem('${ledgerKey}')`);
        check('independent-bookmarks', true, reopenedB.ply === bookmarkB.ply && reopenedB.branch === bookmarkB.branch && reopenedB.step === bookmarkB.step &&
            reopenedB.fen === bookmarkB.fen && !reopenedB.why && (await requests()).length === 0 && Boolean(ledgerSnapshot));
        await evaluate("history.replaceState(null, '', '#review/00000000-0000-0000-0000-000000000000')");
        await reload();
        assert.ok((await finishList()).every(row => row.reviewed === 'true'));
        assert.equal(await evaluate('location.hash'), '#finished');
        assert.notEqual(await evaluate("document.querySelector('#status').textContent"), '');
        assert.equal(await evaluate(`localStorage.getItem('${ledgerKey}')`), ledgerSnapshot);
        return { scope: 'archive-reviews', publicUrl, archiveAId, archiveBId, archiveACheckpoint, archiveBCheckpoint, ledgerSnapshot };
    }

    async function exerciseReviewContinue() {
        const selectedPly = 1;
        const archiveCheckpoint = archiveBCheckpoint;
        const forkStartCheckpoint = '3|BLACK|ADVANCED|0|f2f4';
        const forkCheckpoint = `${forkStartCheckpoint} d7d5`;
        const savedLedger = () => evaluate(`localStorage.getItem('${ledgerKey}')`);
        const modalOpen = () => evaluate("document.querySelector('#continue-dialog').open");

        if (publicUrl) {
            await until(() => evaluate(`${visible('#ai-setup')} && location.href === ${JSON.stringify(publicUrl)} && document.documentElement.lang === 'en'`),
                'public preview starts in AI setup');
            await touch('#open-review');
        }
        if (await displayed('#review-close')) await touch('#review-close');
        await touch('#open-finished');
        const originArchiveId = await until(() => evaluate("document.querySelector('.match-row[data-mode=AI]')?.dataset.id"), 'archived AI game');
        await touch(`.match-row[data-id="${originArchiveId}"]`);
        const archived = await waitActive('exact read-only origin', game => game.id === originArchiveId && game.readOnly);
        assert.equal(archived.checkpoint, 'ai|' + archiveCheckpoint);
        assert.equal(await evaluate("document.querySelector('#analyze').textContent"), publicUrl ? 'Open analysis' : 'Deschide analiza');
        await touch('#analyze');
        const terminal = await wait('completed archive Game Review', review => review.archiveId === originArchiveId && review.ply === 4);
        assert.ok(!terminal.canContinue);
        assert.deepEqual(await evaluate('window.chessWebAudit.requests'), []);
        await touch('button[data-ply="3"]');
        const candidate = await wait('real history before the mistake', review => review.ply === 3);
        assert.ok(candidate.canContinue);
        await touch('#why');
        const explaining = await wait('opened Why explanation', review => review.why);
        await touch('#played');
        const variation = await wait('played comparison branch', review => review.branch === 'PLAYED');
        check('variant-unavailable', true, !explaining.canContinue && !variation.canContinue);
        await touch('#close-why');
        await wait('returned from comparison to recorded history', review => review.branch === 'HISTORY' && !review.why);
        await touch('button[data-ply="1"]');
        const origin = await wait('exact recorded position selected', review => review.ply === selectedPly && review.canContinue);
        check('actual-history', true, origin.archiveId === originArchiveId && origin.branch === 'HISTORY' && origin.step === 0 &&
            origin.last === 'f2f4' && origin.turn === 'BLACK' && origin.orientation === 'BLACK');
        await capture('review-selected-history.png');
        const ledgerBefore = await savedLedger();
        assert.ok(ledgerBefore);

        if (await displayed('#review-actions')) await touch('#review-actions');
        await touch('#continue-review');
        await until(modalOpen, 'shared continuation confirmation');
        const confirming = await evaluate(`({ visible: !document.querySelector('#automatic-option').hidden,
            checked: document.querySelector('#continue-automatic').checked,
            description: document.querySelector('#continue-description').textContent })`);
        check('confirmation', true, confirming.visible && confirming.checked &&
            confirming.description.includes(publicUrl ? 'from move 1' : 'de la mutarea 1'));
        await touch('#cancel-continue');
        await until(async () => !await modalOpen(), 'closed canceled confirmation');
        const afterCancel = await state();
        check('cancel-preserves-ledger', true, await savedLedger() === ledgerBefore && afterCancel.archiveId === originArchiveId &&
            afterCancel.ply === selectedPly && afterCancel.canContinue);

        if (await displayed('#review-actions')) await touch('#review-actions');
        await touch('#continue-review');
        await until(modalOpen, 'reopened shared confirmation');
        await touch('#continue-automatic');
        assert.equal(await evaluate("document.querySelector('#continue-automatic').checked"), false);
        await capture('review-continue-confirmation.png');
        await touch('#confirm-continue');
        const fork = await waitActive('separate active game from the exact review cursor', game => !game.readOnly && game.ply === selectedPly);
        const forkId = fork.id;
        check('forked-exact-position', true, Boolean(forkId) && forkId !== originArchiveId && fork.slot > 0 &&
            fork.mode === 'LOCAL' && fork.checkpoint === forkStartCheckpoint && fork.fen === origin.fen && fork.orientation === origin.orientation && !fork.awaitingBot);
        const diverged = await move('d7', 'd5', 2);
        await pause(725);
        const held = await activeState();
        check('manual-branch', true, diverged.checkpoint === forkCheckpoint && diverged.turn === 'WHITE' &&
            held?.checkpoint === diverged.checkpoint && !held.awaitingBot && held.id === forkId);
        await reload();
        const restored = await waitActive('same branched game after real reload', game => game.id === forkId && game.ply === 2);
        check('fork-reloaded', true, restored.checkpoint === diverged.checkpoint && restored.fen === diverged.fen && restored.slot === fork.slot && !restored.awaitingBot);
        await capture('review-continued-game.png');

        await touch('#open-review');
        if (await displayed('#review-close')) await touch('#review-close');
        await touch('#open-finished');
        await touch(`.match-row[data-id="${originArchiveId}"]`);
        const source = await waitActive('preserved source game', game => game.id === originArchiveId && game.readOnly);
        await touch('#analyze');
        const original = await wait('cached original review', review => review.archiveId === originArchiveId && review.ply === selectedPly);
        const ledgerAfter = await savedLedger();
        check('origin-review-preserved', true, source.checkpoint === archived.checkpoint && source.fen === archived.fen &&
            original.branch === origin.branch && original.step === origin.step && original.why === origin.why && original.fen === origin.fen &&
            (await evaluate('window.chessWebAudit.requests')).length === 0 && Boolean(ledgerAfter) && ledgerAfter !== ledgerBefore);
        await capture('review-origin-restored.png');
        return { scope: 'review-continue', publicUrl, originArchiveId, forkId, archiveCheckpoint, forkStartCheckpoint,
            forkCheckpoint, selectedPly, ledgerBefore, ledgerAfter };
    }

    async function exerciseReviewGraph() {
        if (publicUrl) {
            await until(() => evaluate(`${visible('#ai-setup')} && location.href === ${JSON.stringify(publicUrl)} && document.documentElement.lang === 'en'`),
                'public preview starts in AI setup');
            await touch('#open-review');
        }
        if (await displayed('#review-close')) await touch('#review-close');
        await touch('#open-finished');
        const archiveId = await until(() => evaluate("document.querySelector('.match-row[data-mode=AI]')?.dataset.id"),
            'the migrated long archived game');
        await touch(`.match-row[data-id="${archiveId}"]`);
        const original = await waitActive('the complete game from the fixture', game => game.id === archiveId && game.readOnly);
        assert.equal(original.checkpoint, 'ai|' + fixture.checkpoint);
        await touch('#analyze');
        const opened = await wait('cached Game Review with 185 plies', review => review.archiveId === archiveId && review.ply === 185);
        check('graph-long-fixture', true, opened.cells === 64 && opened.branch === 'HISTORY' &&
            (await evaluate("document.querySelector('#history').children.length")) === 186);

        const tapEvidence = [];
        const sample = ply => anchor.samples.find(position => position.ply === ply);
        const graph = () => evaluate(`(() => {
            const chart = document.querySelector('#review-graph');
            const rect = chart.getBoundingClientRect();
            const marker = document.querySelector('#review-graph-marker').getBoundingClientRect();
            const list = document.querySelector('#history');
            const clip = document.querySelector('#review-details').getBoundingClientRect();
            const rows = list.querySelectorAll('button[aria-current="step"]');
            const row = rows[0]?.getBoundingClientRect();
            const board = document.querySelector('#board').getBoundingClientRect();
            return { viewport: [innerWidth, innerHeight], layout: document.querySelector('#review').dataset.layout,
                width: rect.width, height: rect.height,
                left: rect.left, top: rect.top, inset: Number(chart.dataset.inset), plottedPly: Number(chart.dataset.ply),
                totalPlies: Number(chart.dataset.total), markerX: marker.left + marker.width / 2 - rect.left,
                markerY: marker.top + marker.height / 2 - rect.top,
                rowPly: Number(rows[0]?.dataset.ply), rowCount: rows.length, rowVisible: !!row && row.top >= Math.max(0, clip.top) - .5 &&
                    row.bottom <= Math.min(innerHeight, clip.bottom) + .5 && row.left >= clip.left - .5 && row.right <= clip.right + .5,
                board: [board.x, board.y, board.width, board.height] };
        })()`);
        const alignGraph = () => evaluate(`(() => {
            const report = document.querySelector('#review-details');
            const plot = document.querySelector('#review-graph').getBoundingClientRect();
            const pane = report.getBoundingClientRect();
            report.scrollTop += plot.top - pane.top - (pane.height - plot.height) / 2;
        })()`);
        const within = (actual, expected) => Math.abs(actual - expected) < 1;
        const sameBoard = (first, second) => first.every((value, index) => within(value, second[index]));
        const visibleBoard = ({board: [x, y, width, height], viewport: [screenWidth, screenHeight]}) =>
            x >= -.5 && y >= -.5 && x + width <= screenWidth + .5 && y + height <= screenHeight + .5;
        const readSelection = ply => until(async () => {
            const geometry = await graph();
            const marker = geometry.inset + (geometry.width - 2 * geometry.inset) * ply / 185;
            return geometry.rowVisible && within(geometry.markerX, marker) ? geometry : null;
        }, `visible selected row and marker for ply ${ply}`);
        function observed(id, selection, geometry, touchX = null) {
            const expected = sample(selection.ply);
            const [fen, turn] = expected.fen.split(' ');
            const marker = geometry.inset + (geometry.width - 2 * geometry.inset) * selection.ply / 185;
            check(id, true, selection.fen === fen && selection.last === expected.lastMove &&
                selection.turn === (turn === 'w' ? 'WHITE' : 'BLACK') && selection.archiveId === archiveId &&
                geometry.plottedPly === selection.ply && geometry.rowPly === selection.ply && geometry.rowCount === 1 && geometry.rowVisible &&
                geometry.totalPlies === 185 && geometry.inset === 2 && within(geometry.markerX, marker) && visibleBoard(geometry));
            tapEvidence.push({ id, ply: selection.ply, fen: selection.fen, lastMove: selection.last, turn: selection.turn, touchX, ...geometry });
        }
        async function tapGraph(id, ply, edge = null) {
            await alignGraph();
            const plotted = await graph();
            assert.ok(plotted.width > 4 && plotted.height > 0 && plotted.top >= 0 && plotted.top + plotted.height <= plotted.viewport[1]);
            const localX = edge === 'left' ? 1 : edge === 'right' ? plotted.width - 1
                : plotted.inset + (plotted.width - 2 * plotted.inset) * ply / 185;
            const point = { x: plotted.left + localX, y: plotted.top + plotted.height / 4 };
            assert.equal(await evaluate(`document.elementFromPoint(${point.x}, ${point.y})?.id`), 'review-graph');
            const before = await evaluate('window.chessWebAudit.graphTaps.length');
            await send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] });
            await send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
            const selected = await wait(`graph tap ${id} on ply ${ply}`, review => review.ply === ply && review.selected === ply);
            const geometry = await readSelection(ply);
            const taps = await evaluate('window.chessWebAudit.graphTaps');
            assert.equal(taps.length, before + 1);
            assert.ok(within(plotted.width, geometry.width) && within(taps[before].width, geometry.width)
                && within(taps[before].x, localX));
            observed(id, selected, geometry, taps[before].x);
            return geometry;
        }
        await until(async () => await evaluate("document.querySelector('#review').dataset.layout === 'stacked'"), 'stacked review layout');
        const compactBefore = await graph();
        const left = await tapGraph('left-edge', 0, 'left');
        await alignGraph();
        await capture('graph-left-edge.png');
        const middle = await tapGraph('middle-point', 93);
        const native = await tapGraph('native-target', 184);
        await capture('graph-native-target.png');
        const right = await tapGraph('right-edge', 185, 'right');
        await capture('graph-right-edge.png');

        const wide = { ...viewport, width: 1024, height: 768 };
        async function resize(dimensions) {
            await send('Emulation.setDeviceMetricsOverride', { width: dimensions.width, height: dimensions.height,
                deviceScaleFactor: dimensions.deviceScaleFactor, mobile: true });
            await until(async () => await evaluate(`innerWidth === ${dimensions.width} && innerHeight === ${dimensions.height}`),
                `${dimensions.width}px viewport`);
        }
        await resize(wide);
        await until(async () => await evaluate("document.querySelector('#review').dataset.layout === 'split'"), 'split review layout');
        const wideBefore = await graph();
        const wideTarget = await tapGraph('wide-target', 184);
        await capture('graph-wide-target.png', wide);
        check('board-anchored', true, [left, middle, native, right].every(position => sameBoard(compactBefore.board, position.board)) &&
            sameBoard(wideBefore.board, wideTarget.board) && !within(wideTarget.width, native.width));

        await resize(viewport);
        await reload();
        const reopened = await wait('native target and selected row after a real page reload', review => review.ply === 184 && review.selected === 184);
        assert.equal(await evaluate('window.chessWebAudit.graphTaps.length'), 0);
        observed('reload-cursor', reopened, await readSelection(184));
        await capture('graph-reload.png');
        const ledgerSnapshot = await evaluate(`localStorage.getItem('${ledgerKey}')`);
        return { scope: 'review-graph', publicUrl, fixtureSha256: sha256(fixtureBytes), archiveId, ledgerSnapshot,
            compactBaselineBoard: compactBefore.board, wideBaselineBoard: wideBefore.board, tapEvidence };
    }

    await send('Page.enable');
    await send('Emulation.setDeviceMetricsOverride', { width: viewport.width, height: viewport.height,
        deviceScaleFactor: viewport.deviceScaleFactor, mobile: true });
    await send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 1 });
    await send('Emulation.setLocaleOverride', { locale: viewport.locale.replace('-', '_') });
    if (ledgerScenario || archivesScenario || reviewContinueScenario || activeScenario || reviewGraphScenario) {
        await send('Page.addScriptToEvaluateOnNewDocument', { source: `
            if (location.pathname === ${JSON.stringify(hostPath)}) {
                if (${reviewGraphScenario} && localStorage.getItem('${ledgerKey}') === null) {
                    localStorage.setItem('${aiKey}', ${JSON.stringify(reviewGraphScenario ? 'ai|' + fixture.checkpoint : null)});
                    localStorage.setItem('chesslab.game-review.v1', ${JSON.stringify(reviewGraphScenario ? JSON.stringify(fixture) : null)});
                } else if (${ledgerScenario || archivesScenario || reviewContinueScenario} && localStorage.getItem('${ledgerKey}') === null) {
                    localStorage.setItem('${manualKey}', ${JSON.stringify(archivesScenario || reviewContinueScenario ? archiveACheckpoint : legacyLocalCheckpoint)});
                    localStorage.setItem('${aiKey}', ${JSON.stringify(archivesScenario || reviewContinueScenario ? 'ai|' + archiveBCheckpoint : legacyAiCheckpoint)});
                    if (${reviewContinueScenario}) localStorage.setItem('chesslab.game-review.v1', ${JSON.stringify(preparedReview)});
                }
                window.chessWebAudit = { requests: [], reviews: [], graphTaps: [] };
                const postMessage = Worker.prototype.postMessage;
                Worker.prototype.postMessage = function(message, ...options) {
                    if (message?.type === 'review-analyze') {
                        window.chessWebAudit.requests.push(message.checkpoint);
                        this.addEventListener('message', ({data}) => {
                            if (data.id === message.id && data.archive) window.chessWebAudit.reviews.push(data.archive);
                        });
                    } else if (message?.type === 'review-graph-select') {
                        window.chessWebAudit.graphTaps.push({ x: message.x, width: message.width });
                    }
                    return Reflect.apply(postMessage, this, [message, ...options]);
                };
            }` });
    }
    await send('Page.navigate', { url: page });
    await until(() => evaluate("document.querySelector('#review')?.getAttribute('aria-busy') === 'false'"), 'review page ready');
    let sourceProof;
    if (activeScenario) {
        sourceProof = await exerciseActive();
    } else if (aiScenario) {
        sourceProof = await exerciseAi();
    } else if (ledgerScenario) {
        sourceProof = await exerciseLedger();
    } else if (archivesScenario) {
        sourceProof = await exerciseArchives();
    } else if (reviewContinueScenario) {
        sourceProof = await exerciseReviewContinue();
    } else if (reviewGraphScenario) {
        sourceProof = await exerciseReviewGraph();
    } else {
        sourceProof = await exerciseArchive();
    }
    await writeFile(join(evidence, 'result.json'), JSON.stringify({ runId, browser: browserVersion, profile, viewport,
        bundleSha256, ...sourceProof, observations, captures }, null, 2) + '\n', { flag: 'wx' });
    console.log(`Game Review passed ${observations.length} checks in ${browserVersion}: ${evidence}`);

    async function exerciseArchive() {
        const { root } = await send('DOM.getDocument');
        const { nodeId } = await send('DOM.querySelector', { nodeId: root.nodeId, selector: '#archive' });
        await send('DOM.setFileInputFiles', { nodeId, files: [fixturePath] });
        const opened = await wait('imported archive', value => value.ply === 4);
        check('archive-opened', true, opened.cells === 64 && opened.fen === mateFen && opened.lang === 'ro' && !opened.status);

        await touch('#previous');
        await wait('previous move', value => value.ply === 3);
        await touch('#next');
        await wait('next move', value => value.ply === 4);
        await touch('button[data-ply="3"]');
        const history = await wait('move 3 from the visible history', value => value.ply === anchor.ply);
        assert.equal(history.selected, anchor.ply);
        assert.equal(history.fen, playedFen);
        check('exact-ply', anchor.ply, history.ply);
        check('board-square', true, history.board[2] > 350 && history.board[2] === history.board[3] && history.board[2] === history.panelWidth);
        await capture('history-ply3.png');

        await touch('#why');
        const proof = await wait('explanation', value => value.why);
        check('why-expected-mate', true, proof.mateSquare === 'h4' && proof.reason === 'Replica dă mat cu dama pe h4.');
        await touch('#played');
        const playedRoot = await wait('played branch root', value => value.branch === 'PLAYED' && value.step === 0);
        check('played-root', true, playedRoot.fen === startOfLinesFen && playedRoot.checked === 'played');
        await touch('#next');
        const playedMove = await wait('first played move', value => value.step === 1);
        check('played-move', anchor.playedUci, playedMove.last);
        assert.equal(playedMove.fen, playedFen);
        await touch('#next');
        const mate = await wait('played mate', value => value.step === 2);
        check('played-mate', true, mate.last === anchor.matingUci && mate.fen === mateFen && mate.queen === 'q' && mate.nextDisabled);

        await touch('#recommended');
        const recommendedRoot = await wait('recommended branch root', value => value.branch === 'RECOMMENDED' && value.step === 0);
        check('recommended-root', true, recommendedRoot.fen === playedRoot.fen && recommendedRoot.checked === 'recommended');
        await touch('#next');
        const recommendation = await wait('first recommended move', value => value.step === 1);
        check('recommended-move', anchor.recommendedUci, recommendation.last);
        assert.equal(recommendation.fen, recommendedFen);
        check('board-stable', true, [opened, proof, playedRoot, playedMove, mate, recommendedRoot, recommendation].every(value =>
            value.board.every((coordinate, index) => Math.abs(coordinate - history.board[index]) < .5)));
        await capture('recommended-step1.png');

        await touch('#played');
        await wait('played root again', value => value.branch === 'PLAYED' && value.step === 0);
        await touch('#next');
        await wait('played move again', value => value.step === 1);
        await touch('#next');
        await wait('mate again', value => value.step === 2);
        const previousPage = await evaluate('performance.timeOrigin');
        await send('Page.reload', { ignoreCache: true });
        await until(async () => (await evaluate('performance.timeOrigin')) !== previousPage, 'new page after a real reload');
        const restored = await wait('restored played mate', value => value.branch === 'PLAYED' && value.step === 2);
        check('reload-bookmark', true, restored.why && restored.ply === anchor.ply && restored.selected === anchor.ply && restored.fen === mateFen);
        await capture('restored-played-mate.png');
        await touch('#close-why');
        const returned = await wait('returned to immutable history', value => value.branch === 'HISTORY' && !value.why);
        check('return-history', true, returned.fen === playedFen && returned.ply === anchor.ply && returned.last === anchor.playedUci);
        await capture('returned-history.png');
        const saved = await evaluate("JSON.parse(localStorage.getItem('chesslab.game-review.v1'))");
        check('archive-preserved', true, saved.id === fixture.id && saved.checkpoint === fixture.checkpoint && saved.review === fixture.review &&
            typeof saved.bookmark === 'string' && saved.bookmark.length > 0);
        return { fixtureSha256: sha256(fixtureBytes) };
    }
} finally {
    socket?.close();
    if (server.listening) server.close();
    if (browser.pid && browser.exitCode === null) {
        browser.kill();
        await Promise.race([new Promise(resolve => browser.once('exit', resolve)), pause(2000)]);
    }
    if (dirname(profileDirectory) === resolve(tmpdir()) && basename(profileDirectory).startsWith('j7-review-edge-')) {
        await rm(profileDirectory, { recursive: true, force: true }).catch(() => {});
    }
}
