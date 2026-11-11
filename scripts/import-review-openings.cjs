const fs = require('node:fs');
const path = require('node:path');
const { Chess } = require(path.resolve(process.argv[2]));
const directory = process.argv[3];
const commit = process.argv[4];
if (!/^[0-9a-f]{40}$/.test(commit)) throw new Error('Pass the pinned upstream commit');
const rows = [];
for (const letter of 'abcde') {
    const [header, ...lines] = fs.readFileSync(path.join(directory, letter + '.tsv'), 'utf8').trim().split(/\r?\n/);
    if (header !== 'eco\tname\tpgn') throw new Error('Unexpected upstream TSV header');
    for (const line of lines) {
        const [eco, name, pgn] = line.split('\t');
        const chess = new Chess();
        chess.loadPgn(pgn);
        const moves = chess.history({ verbose: true }).map(move => move.from + move.to + (move.promotion || ''));
        rows.push([eco, name, chess.fen().split(' ').slice(0, 4).join(' '), moves.join(' ')]);
    }
}
rows.sort((a, b) => a[3].split(' ').length - b[3].split(' ').length);
const contents = ['# lichess-org/chess-openings ' + commit + ' CC0-1.0', 'eco\tname\tepd\tmoves',
    ...rows.map(row => row.join('\t'))].join('\n') + '\n';
fs.writeFileSync(process.argv[5], contents);
console.log(rows.length + ' opening lines');
