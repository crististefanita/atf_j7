// A strict arithmetic harness, not a browser layout substitute.
export function cssDeclarations(text) {
    return Object.fromEntries([...text.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)]
        .map(([, name, value]) => [name, value.trim()]));
}

export function cssPixels(expression, { variables, rem, viewportHeight }) {
    function expand(value, seen = new Set()) {
        return value.replace(/var\((--[\w-]+)\)/g, (_, name) => {
            if (seen.has(name) || variables[name] == null) throw new Error('Unresolved CSS variable: ' + name);
            return '(' + expand(String(variables[name]), new Set([...seen, name])) + ')';
        });
    }
    const text = expand(expression);
    const tokens = [];
    const lexer = /\s*(?:(\d+(?:\.\d*)?|\.\d+)(px|rem|dvh)?|([a-z]+)|([()+*,\/-]))/gy;
    let offset = 0;
    while (offset < text.trimEnd().length) {
        lexer.lastIndex = offset;
        const token = lexer.exec(text);
        if (!token) throw new Error('Unsupported CSS arithmetic at ' + offset);
        offset = lexer.lastIndex;
        tokens.push(token[1] ? Number(token[1]) * ({ px: 1, rem, dvh: viewportHeight / 100 }[token[2]] ?? 1) : token[3] ?? token[4]);
    }
    let cursor = 0;
    const take = expected => { if (tokens[cursor++] !== expected) throw new Error('Expected ' + expected); };
    function atom() {
        const token = tokens[cursor++];
        if (typeof token === 'number') return token;
        if (token === '-') return -atom();
        if (token === '(') { const value = sum(); take(')'); return value; }
        if (!['calc', 'min', 'max'].includes(token)) throw new Error('Unsupported CSS function: ' + token);
        take('(');
        const args = [sum()];
        while (tokens[cursor] === ',') { cursor++; args.push(sum()); }
        take(')');
        if (token === 'calc' && args.length !== 1) throw new Error('calc requires one expression');
        return token === 'min' ? Math.min(...args) : token === 'max' ? Math.max(...args) : args[0];
    }
    function product() {
        let value = atom();
        while (['*', '/'].includes(tokens[cursor])) { const op = tokens[cursor++], next = atom(); value = op === '*' ? value * next : value / next; }
        return value;
    }
    function sum() {
        let value = product();
        while (['+', '-'].includes(tokens[cursor])) { const op = tokens[cursor++], next = product(); value += (op === '+' ? 1 : -1) * next; }
        return value;
    }
    const value = sum();
    if (cursor !== tokens.length || !Number.isFinite(value)) throw new Error('Invalid CSS result');
    return value;
}
