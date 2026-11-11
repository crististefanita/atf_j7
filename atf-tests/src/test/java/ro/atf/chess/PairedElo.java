package ro.atf.chess;

import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/** Uses the opening as the independent unit. An unfinished game contributes an interval. */
final class PairedElo {
    record Pair(int wins, int draws, int losses, int unfinished) {
        Pair {
            if (wins < 0 || draws < 0 || losses < 0 || unfinished < 0 || wins + draws + losses + unfinished != 2) {
                throw new IllegalArgumentException("Each opening needs two game outcomes");
            }
        }

        double low() { return (wins + draws * .5) / 2; }
        double high() { return low() + unfinished * .5; }
    }

    record Estimate(double scoreLow, double scoreHigh, double eloMidpoint, double eloLow95, double eloHigh95) { }

    private PairedElo() { }

    static Estimate estimate(List<Pair> pairs, int samples, long seed) {
        if (pairs.size() < 2 || samples < 1_000) throw new IllegalArgumentException("Insufficient paired sample");
        var random = new SplittableRandom(seed);
        double[] lower = new double[samples];
        double[] upper = new double[samples];
        for (int draw = 0; draw < samples; draw++) {
            for (int index = 0; index < pairs.size(); index++) {
                Pair pair = pairs.get(random.nextInt(pairs.size()));
                lower[draw] += pair.low();
                upper[draw] += pair.high();
            }
            lower[draw] /= pairs.size();
            upper[draw] /= pairs.size();
        }
        Arrays.sort(lower);
        Arrays.sort(upper);
        double low = pairs.stream().mapToDouble(Pair::low).average().orElseThrow();
        double high = pairs.stream().mapToDouble(Pair::high).average().orElseThrow();
        return new Estimate(low, high, elo((low + high) / 2), elo(lower[(int) (samples * .025)]),
                elo(upper[(int) Math.ceil(samples * .975) - 1]));
    }

    static double elo(double score) {
        if (score <= 0) return Double.NEGATIVE_INFINITY;
        if (score >= 1) return Double.POSITIVE_INFINITY;
        return 400 * Math.log10(score / (1 - score));
    }
}
