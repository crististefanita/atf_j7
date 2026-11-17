// Keep one delayed or running reply for the exact match state that requested it.
export function createMatchBotTimer(clock, delay, reply) {
    let scheduled;
    return waiting => {
        if (scheduled?.waiting === waiting) return;
        if (scheduled) {
            if (scheduled.timer !== undefined) clock.clearTimeout(scheduled.timer);
            scheduled.controller.abort();
            scheduled = undefined;
        }
        if (!waiting) return;
        const turn = { waiting, controller: new AbortController() };
        turn.timer = clock.setTimeout(async () => {
            if (scheduled !== turn || turn.timer === undefined) return;
            turn.timer = undefined;
            try {
                await reply(waiting, turn.controller.signal);
            } finally {
                if (scheduled === turn) scheduled = undefined;
            }
        }, delay);
        scheduled = turn;
    };
}
