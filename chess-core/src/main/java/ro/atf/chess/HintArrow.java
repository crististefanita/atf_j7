package ro.atf.chess;

/** Arrow dimensions measured from the actual centers of two displayed squares. */
public record HintArrow(double start, double tip, double head, double halfShaft,
                        double halfHead, double outline, double radians) {
    public static HintArrow between(double dx, double dy, double unit) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(unit) || unit <= 0) {
            throw new IllegalArgumentException("Expected finite square centers and a positive square size");
        }
        double start = unit * .31;
        double tip = Math.hypot(dx, dy) - unit * .12;
        double head = tip - unit * .17;
        if (!Double.isFinite(tip) || head <= start) {
            throw new IllegalArgumentException("Distinct displayed square centers are required");
        }
        return new HintArrow(start, tip, head, unit * .029, unit * .106, unit * .027, Math.atan2(dy, dx));
    }
}
