import assert from 'node:assert/strict';

const properties = new Set(['focused', 'focusable', 'valuetext', 'describedby', 'current', 'expanded', 'pressed',
    'live', 'atomic', 'relevant', 'hidden', 'disabled']);
const targets = {
    board: '#board',
    slider: '#seek',
    row: '#history button[aria-current=step]',
    why: '#why',
    back: '#close-why',
    heading: '#why-title',
    position: '#position',
    recommended: '#recommended',
    announcement: '#review-announcement',
};
const milestones = {
    'before-toolbar': ['position', 'announcement'],
    'after-toolbar': ['position', 'announcement'],
    'graph-before': ['board', 'slider', 'row', 'announcement'],
    'graph-left': ['slider', 'row', 'announcement'],
    'graph-right': ['slider', 'row', 'announcement'],
    'selected-row': ['slider', 'row', 'announcement'],
    'why-entry': ['why', 'back', 'heading', 'slider', 'row', 'announcement'],
    'why-recommended': ['back', 'heading', 'recommended', 'announcement'],
    'why-escape': ['why', 'back', 'slider', 'row', 'announcement'],
    'why-back': ['why', 'back', 'slider', 'row', 'announcement'],
};

const normalized = node => ({
    node: node.nodeId,
    backend: node.backendDOMNodeId ?? null,
    ignored: node.ignored,
    role: node.role?.value ?? '',
    name: node.name?.value ?? '',
    value: node.value?.value ?? null,
    description: node.description?.value ?? '',
    properties: Object.fromEntries((node.properties ?? []).filter(({name}) => properties.has(name)).map(({name, value}) => [name, value.value])),
});

export async function createReviewAccessibilityProbe(send, socket) {
    const watched = new Map();
    const witnesses = [];
    const updates = [];
    let phase = '';
    socket.addEventListener('message', event => {
        const data = JSON.parse(event.data);
        if (data.method !== 'Accessibility.nodesUpdated') return;
        for (const node of data.params.nodes) {
            const target = watched.get(node.backendDOMNodeId);
            if (target) updates.push({ phase, target, ...normalized(node) });
        }
    });
    await send('Accessibility.enable');

    async function read(target) {
        const selector = targets[target];
        const { root } = await send('DOM.getDocument', { depth: 0 });
        const { nodeId } = await send('DOM.querySelector', { nodeId: root.nodeId, selector });
        if (!nodeId) return null;
        const { node: { backendNodeId } } = await send('DOM.describeNode', { nodeId });
        watched.set(backendNodeId, target);
        const { nodes } = await send('Accessibility.getAXNodeAndAncestors', { backendNodeId });
        const node = nodes.find(candidate => candidate.backendDOMNodeId === backendNodeId);
        assert.ok(node, 'Chromium did not expose AX data for ' + selector);
        const result = normalized(node);
        if (selector === targets.announcement) {
            result.children = await textChildren(node);
        }
        return result;
    }

    async function textChildren(parent) {
        if (!parent.childIds?.length) return [];
        const { nodes } = await send('Accessibility.getChildAXNodes', { id: parent.nodeId });
        for (const node of nodes) {
            if (node.backendDOMNodeId != null) watched.set(node.backendDOMNodeId, 'announcement-text');
        }
        return nodes.filter(node => !node.ignored && node.role?.value === 'StaticText').map(normalized);
    }

    return {
        begin: id => { phase = id; },
        async observe(id) {
            if (!Object.hasOwn(milestones, id)) return;
            const nodes = {};
            for (const target of milestones[id]) nodes[target] = await read(target);
            witnesses.push({ id, nodes });
        },
        sawSliderChange: (expectedPhase, ply) => updates.some(entry => entry.phase === expectedPhase && entry.target === 'slider' &&
            entry.value === ply && entry.role === 'slider' && entry.properties.focused === true),
        evidence: () => ({ protocol: 'Chromium CDP Accessibility', spokenAudioRecorded: false, witnesses, updates }),
    };
}
