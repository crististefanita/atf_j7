// Keep a single reply scheduled for the exact match state that requested it.
export function createMatchBotTimer(clock, delay, reply) {
    let scheduled;
    return waiting => {
        if (scheduled?.waiting === waiting) return;
        if (scheduled) {
            clock.clearTimeout(scheduled.timer);
            scheduled = undefined;
        }
        if (!waiting) return;
        const turn = { waiting };
        turn.timer = clock.setTimeout(() => {
            if (scheduled !== turn) return;
            scheduled = undefined;
            reply(waiting);
        }, delay);
        scheduled = turn;
    };
}
