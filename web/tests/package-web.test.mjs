import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdir, mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import test from 'node:test';
import { pathToFileURL } from 'node:url';
import { packageWeb, runtimeDirectory } from '../tools/package-web.mjs';

const sourceIndex = [
    '<html><head>',
    '  <link rel="stylesheet" href="game-review.css">',
    '  <link rel="stylesheet" href="study-analysis.css">',
    '  <script type="module" src="game-review.mjs"></script>',
    '</head><body><h1>Chess Lab</h1><p>game-review.mjs</p></body></html>',
    '',
].join('\n');

async function fixture(t) {
    const temporaryBase = resolve(tmpdir());
    const directory = await mkdtemp(join(temporaryBase, 'chess-web-package-'));
    assert.equal(dirname(resolve(directory)), temporaryBase);
    t.after(() => rm(directory, { recursive: true, force: true }));
    const source = join(directory, 'source');
    const engine = join(directory, 'chess-engine.mjs');
    const site = join(directory, 'site');
    await mkdir(source);
    await mkdir(site);
    await Promise.all(Object.entries({
        'index.html': sourceIndex,
        'game-review.css': 'body { color: black; }\n',
        'study-analysis.css': '.track { display: block; }\n',
        'game-review.mjs': 'export const worker = new URL("./engine-worker.mjs", import.meta.url);\nexport { score } from "./inline-hint.mjs";\n',
        'inline-hint.mjs': 'export const score = 12;\n',
        'engine-worker.mjs': 'export { workerReady } from "./chess-engine.mjs";\n',
    }).map(([filename, content]) => writeFile(join(source, filename), content)));
    await writeFile(engine, 'export const workerReady = true;\n');
    return { source, engine, site };
}

test('Published HTML resolves the complete module and stylesheet graph in one immutable directory', async t => {
    const { source, engine, site } = await fixture(t);
    const revision = await packageWeb(source, engine, site);
    assert.match(revision, /^[a-f0-9]{64}$/);
    const index = await readFile(join(site, 'index.html'), 'utf8');
    assert.equal(index, sourceIndex
        .replace('href="game-review.css"', 'href="runtime/' + revision + '/game-review.css"')
        .replace('href="study-analysis.css"', 'href="runtime/' + revision + '/study-analysis.css"')
        .replace('src="game-review.mjs"', 'src="runtime/' + revision + '/game-review.mjs"'));
    assert.equal(await readFile(join(source, 'index.html'), 'utf8'), sourceIndex);
    assert.deepEqual((await readdir(site)).filter(filename => filename.endsWith('.mjs') || filename.endsWith('.css')), []);
    const runtime = await runtimeDirectory(site);
    assert.equal(runtime, join(site, 'runtime', revision));
    assert.deepEqual((await readdir(runtime)).sort(), [
        'chess-engine.mjs', 'engine-worker.mjs', 'game-review.css', 'game-review.mjs', 'inline-hint.mjs', 'study-analysis.css',
    ]);
    const entry = new URL(index.match(/src="([^"]+)"/)[1], pathToFileURL(join(site, 'index.html')));
    const { score, worker } = await import(entry);
    assert.equal(score, 12);
    assert.equal(worker.href, new URL('./engine-worker.mjs', entry).href);
    assert.equal((await import(worker)).workerReady, true);
});

test('Changing only a transitive module allocates a new address and preserves the previous runtime', async t => {
    const { source, engine, site } = await fixture(t);
    const previousRevision = await packageWeb(source, engine, site);
    const previousRuntime = await runtimeDirectory(site);
    const previousIndex = await readFile(join(site, 'index.html'), 'utf8');
    assert.equal(await packageWeb(source, engine, site), previousRevision);
    assert.equal(await readFile(join(site, 'index.html'), 'utf8'), previousIndex);
    const dependency = 'inline-hint.mjs';
    await writeFile(join(source, dependency), 'export const score = 47;\n');
    const currentRevision = await packageWeb(source, engine, site);
    assert.notEqual(currentRevision, previousRevision);
    assert.deepEqual((await readdir(join(site, 'runtime'))).sort(), [previousRevision, currentRevision].sort());
    const currentRuntime = await runtimeDirectory(site);
    assert.equal(currentRuntime, join(site, 'runtime', currentRevision));
    const inspect = async runtime => (await import(pathToFileURL(join(runtime, 'game-review.mjs')))).score;
    assert.equal(await inspect(previousRuntime), 12);
    assert.equal(await inspect(currentRuntime), 47);
    assert.equal(await readFile(join(previousRuntime, dependency), 'utf8'), 'export const score = 12;\n');
});

test('Packaging refuses a missing entry asset and leaves previously published runtime files intact', async t => {
    const { source, engine, site } = await fixture(t);
    await packageWeb(source, engine, site);
    const runtime = await runtimeDirectory(site);
    const originalIndex = await readFile(join(site, 'index.html'), 'utf8');
    const sourcePage = join(source, 'index.html');
    await writeFile(sourcePage, sourceIndex.replace('study-analysis.css', 'unavailable.css'));
    await assert.rejects(packageWeb(source, engine, site), /Missing Web asset: unavailable\.css/);
    assert.equal(await readFile(join(site, 'index.html'), 'utf8'), originalIndex);

    await writeFile(sourcePage, sourceIndex);
    const immutableFile = join(runtime, 'inline-hint.mjs');
    const unexpectedContents = 'export const score = -99;\n';
    await writeFile(immutableFile, unexpectedContents);
    await assert.rejects(packageWeb(source, engine, site), { code: 'EEXIST' });
    assert.equal(await readFile(immutableFile, 'utf8'), unexpectedContents);
    assert.equal(await readFile(join(site, 'index.html'), 'utf8'), originalIndex);
});

test('Git staging with autocrlf preserves qualified engine bytes and the runtime manifest', async t => {
    const { source, engine, site } = await fixture(t);
    await writeFile(engine, 'export const workerReady = true;\r\n');
    const revision = await packageWeb(source, engine, site);
    assert.equal(await readFile(join(site, '.gitattributes'), 'utf8'), '* -text\n');
    const git = (...args) => execFileSync('git', ['-C', site, ...args], { stdio: ['ignore', 'pipe', 'pipe'] });
    git('init');
    git('config', 'core.autocrlf', 'true');
    git('add', '.');
    for (const name of await readdir(join(site, 'runtime', revision))) {
        const route = 'runtime/' + revision + '/' + name;
        assert.deepEqual(git('show', ':' + route), await readFile(join(site, route)), name + ' retains its hashed bytes');
    }
    assert.deepEqual(git('show', ':runtime/' + revision + '/chess-engine.mjs'), await readFile(engine));
});
