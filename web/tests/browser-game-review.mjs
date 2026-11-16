import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { mkdir, mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { basename, dirname, extname, join, relative, resolve, sep } from 'node:path';
import { setTimeout as pause } from 'node:timers/promises';

const args = process.argv.slice(2);
assert.ok(args.length === 4 || args.length === 5, 'Usage: node browser-game-review.mjs <site> <archive.json|--active|--ai> <new-evidence-directory> <edge-exe> [public-ai-url]');
const [site, , evidence, browserPath] = args.map(path => resolve(path));
const activeScenario = args[1] === '--active';
const aiScenario = args[1] === '--ai';
const fixturePath = activeScenario || aiScenario ? null : resolve(args[1]);
const hostPath = '/atf_j7/';
const publicUrl = args[4];
const readmeUrl = `https://crististefanita.github.io${hostPath}#ai`;
if (publicUrl) assert.ok(aiScenario && publicUrl === readmeUrl, `Public smoke requires --ai and the README URL ${readmeUrl}`);
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
if (fixturePath !== null) {
    assert.equal(anchor.ply, 3);
    assert.equal(anchor.playedUci, 'g2g4');
    assert.equal(anchor.matingUci, 'd8h4');
    assert.equal(anchor.recommendedUci, 'd2d4');
}

const startOfLinesFen = 'rnbqkbnr/pppp1ppp/8/4p3/8/5P2/PPPPP1PP/RNBQKBNR';
const playedFen = 'rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR';
const recommendedFen = 'rnbqkbnr/pppp1ppp/8/4p3/3P4/5P2/PPP1P1PP/RNBQKBNR';
const mateFen = 'rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR';

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
    const state = () => evaluate(`(() => {
        const review = document.querySelector('#review');
        if (review.hidden || review.getAttribute('aria-busy') !== 'false') return null;
        const board = document.querySelector('#board');
        const rect = board.getBoundingClientRect();
        return { ply: Number(review.dataset.ply), step: Number(review.dataset.step), branch: review.dataset.branch,
            fen: board.dataset.fen, last: board.dataset.lastMove, turn: board.dataset.turn,
            board: [rect.x + scrollX, rect.y + scrollY, rect.width, rect.height], panelWidth: board.parentElement.clientWidth,
            cells: board.children.length, selected: Number(document.querySelector('[aria-current="step"]').dataset.ply),
            why: ${visible('#explanation')}, mateSquare: document.querySelector('#reason').dataset.square,
            reason: document.querySelector('#reason').textContent, nextDisabled: document.querySelector('#next').disabled,
            queen: board.querySelector('[data-square="h4"]').dataset.piece,
            checked: document.querySelector('[aria-pressed="true"]')?.id,
            status: document.querySelector('#status').textContent, lang: document.documentElement.lang,
        };
    })()`);
    const wait = (label, condition) => until(async () => {
        const result = await state();
        return result && condition(result) ? result : null;
    }, label);

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

    async function capture(file) {
        const { data } = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
        const bytes = Buffer.from(data, 'base64');
        assert.equal(bytes.readUInt32BE(16), viewport.width);
        assert.equal(bytes.readUInt32BE(20), viewport.height);
        await writeFile(join(evidence, file), bytes, { flag: 'wx' });
        captures.push({ file, sha256: sha256(bytes) });
    }

    const manualKey = 'chesslab.active-match.v1';
    const aiKey = 'chesslab.ai-match.v1';
    const savedMatch = `localStorage.getItem('${manualKey}')`;
    const savedReview = "JSON.parse(localStorage.getItem('chesslab.game-review.v1'))";
    const activeState = () => evaluate(`(() => {
        const match = document.querySelector('#match');
        if (match.hidden || match.getAttribute('aria-busy') !== 'false') return null;
        const board = document.querySelector('#match-board');
        const rect = board.getBoundingClientRect();
        return { ply: Number(match.dataset.ply), turn: board.dataset.turn, last: board.dataset.lastMove,
            result: match.dataset.result, fen: board.dataset.fen, cells: board.children.length, width: rect.width, height: rect.height,
            mode: match.dataset.mode, level: match.dataset.level, humanSide: match.dataset.humanSide, awaitingBot: match.dataset.awaitingBot === 'true',
            orientation: board.dataset.orientation, upperLeft: board.firstElementChild.dataset.square,
            lowerRight: board.lastElementChild.dataset.square, profile: document.querySelector('#match-profile').textContent,
            announcing: document.querySelector('#match-turn').textContent, urlPath: location.pathname,
            analyzing: !document.querySelector('#analysis-progress').hidden, analyzeDisabled: document.querySelector('#analyze').disabled,
            checkpoint: localStorage.getItem(match.dataset.mode === 'AI' ? '${aiKey}' : '${manualKey}') };
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
        check('manual-independent', true, manualRestored.checkpoint === manual.checkpoint && manualRestored.fen === manual.fen &&
            await evaluate(`localStorage.getItem('${aiKey}')`) === replied.checkpoint);
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
        await touch('#open-match');
        await waitActive('returned from unfinished analysis to same match', value => value.ply === 3);
        const complete = await move('d8', 'h4', 4);
        check('continued-move', 'd8h4', complete.last);
        assert.equal(complete.result, 'BLACK_WINS');
        assert.equal(complete.fen, mateFen);
        assert.equal(complete.checkpoint, '1|f2f3 e7e5 g2g4 d8h4');

        await touch('#analyze');
        const fresh = await wait('fresh review of checkmate', value => value.ply === 4 && value.branch === 'HISTORY');
        const generated = await evaluate(savedReview);
        check('analysis-archive', true, generated.checkpoint === complete.checkpoint && generated.review.length > 0 && generated.review !== partialArchive.review &&
            typeof generated.bookmark === 'string' && generated.bookmark.length > 0);
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

    await send('Page.enable');
    await send('Emulation.setDeviceMetricsOverride', { width: viewport.width, height: viewport.height,
        deviceScaleFactor: viewport.deviceScaleFactor, mobile: true });
    await send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 1 });
    await send('Emulation.setLocaleOverride', { locale: viewport.locale.replace('-', '_') });
    await send('Page.navigate', { url: page });
    await until(() => evaluate("document.querySelector('#review')?.getAttribute('aria-busy') === 'false'"), 'review page ready');
    let sourceProof;
    if (activeScenario) {
        sourceProof = await exerciseActive();
    } else if (aiScenario) {
        sourceProof = await exerciseAi();
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
