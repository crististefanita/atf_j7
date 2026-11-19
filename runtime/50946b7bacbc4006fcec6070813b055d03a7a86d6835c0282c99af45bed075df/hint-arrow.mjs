// Dimensions match the JVM HintArrow contract; callers supply the measured cell centers.
export function hintArrow(dx, dy, unit = 1) {
    if (![dx, dy, unit].every(Number.isFinite) || unit <= 0) {
        throw new RangeError('Expected finite square centers and a positive square size');
    }
    const start = unit * .31;
    const tip = Math.hypot(dx, dy) - unit * .12;
    const head = tip - unit * .17;
    if (!Number.isFinite(tip) || head <= start) throw new RangeError('Distinct displayed square centers are required');
    return { start, tip, head, halfShaft: unit * .029, halfHead: unit * .106,
        outline: unit * .027, radians: Math.atan2(dy, dx) };
}
