import { createHash } from 'node:crypto';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const entry = 'game-review.mjs';
const engine = 'chess-engine.mjs';
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const lf = text => text.replace(/\r\n/g, '\n');

export async function runtimeDirectory(site) {
    const page = await readFile(join(site, 'index.html'), 'utf8');
    const entries = [...page.matchAll(/\bsrc="(runtime\/[0-9a-f]{64})\/game-review\.mjs"/g)];
    if (entries.length !== 1) throw new Error('The Web entry must identify one immutable runtime directory');
    return join(site, entries[0][1]);
}

async function writeImmutable(path, bytes) {
    try {
        await writeFile(path, bytes, { flag: 'wx' });
    } catch (error) {
        if (error.code !== 'EEXIST' || !(await readFile(path)).equals(bytes)) throw error;
    }
}

export async function packageWeb(source, engineFile, site) {
    const files = new Map([[engine, await readFile(engineFile)]]);
    for (const file of await readdir(source, { withFileTypes: true })) {
        if (file.name === 'index.html') continue;
        if (!file.isFile() || files.has(file.name)) throw new Error('Invalid Web asset: ' + file.name);
        const bytes = await readFile(join(source, file.name));
        files.set(file.name, /\.(mjs|css)$/.test(file.name) ? Buffer.from(lf(bytes.toString('utf8'))) : bytes);
    }

    const names = [...files.keys()].sort();
    const manifest = names.map(name => sha256(files.get(name)) + '  ' + name + '\n').join('');
    const revision = sha256(manifest);
    const address = 'runtime/' + revision;
    const sourcePage = lf(await readFile(join(source, 'index.html'), 'utf8'));
    let entryCount = 0;
    const page = sourcePage.replace(/(<(?:script|link)\b[^>]*?\b(?:src|href)\s*=\s*)(["'])(.*?)\2/gi, (_, prefix, quote, name) => {
        if (!files.has(name)) throw new Error('Missing Web asset: ' + name);
        if (name === entry && prefix.toLowerCase().startsWith('<script')) entryCount++;
        return prefix + '"' + address + '/' + name + '"';
    });
    if (entryCount !== 1) throw new Error('Expected one Web module entry: ' + entry);

    const runtime = join(site, address);
    await mkdir(runtime, { recursive: true });
    for (const name of names) await writeImmutable(join(runtime, name), files.get(name));
    await writeFile(join(site, '.nojekyll'), '');
    await writeFile(join(site, 'index.html'), page);
    return revision;
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
    const [source, engineFile, site] = process.argv.slice(2);
    if (![source, engineFile, site].every(Boolean)) throw new Error('Supply Web sources, the TeaVM engine and the site directory');
    console.log(await packageWeb(source, engineFile, site));
}
