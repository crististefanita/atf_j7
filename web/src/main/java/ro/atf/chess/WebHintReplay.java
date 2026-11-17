package ro.atf.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import ro.atf.chess.ChessPosition.Move;
import static ro.atf.chess.WebBoardState.frame;
import static ro.atf.chess.WebBoardState.json;
import static ro.atf.chess.WebBoardState.marks;
import static ro.atf.chess.WebBoardState.move;
import static ro.atf.chess.WebBoardState.quote;
import static ro.atf.chess.WebBoardState.text;

/** Adapts a revealed hint and its playable lines to a read-only board cursor. */
final class WebHintReplay {
    private List<Replay> previews = List.of();
    private int branch;

    boolean isOpen() { return !previews.isEmpty(); }

    boolean open(ChessPosition root, MatchHint hint) {
        if (hint == null || isOpen()) return false;
        var theory = hint.theoryPlayback(root);
        if (theory.isPresent()) {
            var paths = theory.get();
            previews = paths.paths().stream().<Replay>map(path -> new TheoryReplay(path, new OpeningReplay(root, path)))
                    .collect(Collectors.toList());
            branch = paths.selected();
            return true;
        }
        hint.playback(root).ifPresent(playback -> openTactical(root, playback));
        return isOpen();
    }

    private void openTactical(ChessPosition root, MatchHint.Playback playback) {
        var replays = new ArrayList<HintPreview>();
        var lines = playback.lines();
        branch = 0;
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            replays.add(HintPreview.openDefended(root, line).orElseThrow());
            if (line.move().equals(playback.selected())) branch = index;
        }
        var available = new ArrayList<Replay>();
        for (int index = 0; index < lines.size(); index++) {
            var proof = playback.comparison(root, index).flatMap(HintComparison::decisive).orElse(null);
            int decisiveBranch = proof == null || proof.inStronger() ? 0 : Math.max(1, index);
            var decisive = proof != null && !replays.get(decisiveBranch).addedDefense() ? new Proof(decisiveBranch, proof) : null;
            available.add(new TacticalReplay(replays.get(index), playback.depth(), decisive));
        }
        previews = List.copyOf(available);
    }

    boolean close() {
        if (!isOpen()) return false;
        previews = List.of();
        return true;
    }

    boolean select(int index) {
        if (index < 0 || index >= previews.size()) return false;
        branch = index;
        return true;
    }

    boolean seek(int index) {
        if (!isOpen() || index < 0 || index > previews.get(branch).length()) return false;
        previews.get(branch).seek(index);
        return true;
    }

    boolean prove() {
        if (!isOpen() || !(previews.get(branch) instanceof TacticalReplay line) || line.proof() == null) return false;
        branch = line.proof().branch();
        previews.get(branch).seek(line.proof().move().ply());
        return true;
    }

    String state() {
        if (!isOpen()) return "null";
        var preview = previews.get(branch);
        return "{" + frame(preview.position(), preview.last()) + ",\"ply\":" + preview.ply() + ",\"total\":" + preview.length()
                + ",\"branch\":" + branch + ",\"branches\":" + json(previews, item -> move(item.first()))
                + ",\"marks\":" + marks(preview.ply() == 0 ? preview.first() : null) + preview.evidence() + "}";
    }

    private interface Replay {
        ChessPosition position();
        int ply();
        int length();
        Move first();
        Move last();
        void seek(int ply);
        String evidence();
    }

    private record TheoryReplay(MatchHint.Theory path, OpeningReplay replay) implements Replay {
        @Override public ChessPosition position() { return replay.position(); }
        @Override public int ply() { return replay.ply(); }
        @Override public int length() { return replay.length(); }
        @Override public Move first() { return path.move(); }
        @Override public Move last() { return replay.lastMove(); }
        @Override public void seek(int ply) {
            while (replay.ply() < ply) replay.next();
            while (replay.ply() > ply) replay.previous();
        }
        @Override public String evidence() {
            return ",\"kind\":\"THEORY\",\"eco\":" + quote(path.eco()) + ",\"name\":" + text(path.name())
                    + ",\"idea\":" + text(replay.explainedStep().idea());
        }
    }

    private record Proof(int branch, HintComparison.DecisiveMove move) {
        String json() {
            return "{\"branch\":" + branch + ",\"ply\":" + move.ply() + ",\"move\":" + WebBoardState.move(move.move())
                    + ",\"piece\":" + quote(move.piece().name()) + ",\"reason\":" + quote(move.reason().name()) + "}";
        }
    }

    private record TacticalReplay(HintPreview replay, int depth, Proof proof) implements Replay {
        @Override public ChessPosition position() { return replay.position(); }
        @Override public int ply() { return replay.step(); }
        @Override public int length() { return replay.length(); }
        @Override public Move first() { return replay.firstMove(); }
        @Override public Move last() { return replay.lastMove(); }
        @Override public void seek(int ply) { replay.seek(ply); }
        @Override public String evidence() {
            var evidence = replay.evidence();
            var estimate = replay.estimate().orElse(null);
            return ",\"kind\":\"TACTICAL\",\"proof\":" + quote(evidence.reason().name()) + ",\"proofMove\":" + move(evidence.move())
                    + ",\"piece\":" + quote(evidence.piece() == null ? null : evidence.piece().name())
                    + ",\"captured\":" + quote(evidence.captured() == null ? null : evidence.captured().name())
                    + ",\"gain\":" + evidence.netMaterial() + ",\"initialCp\":" + (estimate == null ? "null" : estimate.initialCp())
                    + ",\"finalCp\":" + (estimate == null ? "null" : estimate.finalCp())
                    + ",\"depth\":" + depth + ",\"decisive\":" + (proof == null ? "null" : proof.json());
        }
    }
}
