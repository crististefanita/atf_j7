package ro.atf.chess;

import static ro.atf.chess.WebBoardState.move;
import static ro.atf.chess.WebBoardState.placement;
import static ro.atf.chess.WebBoardState.quote;

import java.util.List;
import java.util.stream.Collectors;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.QuestCatalog.Objective;
import ro.atf.chess.QuestCatalog.Quest;

/** Worker adapter for the same puzzle catalog, save format and decisions used by Android. */
final class WebQuestStudy {
    private record Chapter(String key, List<Quest> quests, List<String> ids) {
        Chapter(String key, List<Quest> quests) { this(key, quests, quests.stream().map(Quest::id).collect(Collectors.toList())); }
    }

    private static final class Catalog {
        static final List<Chapter> CHAPTERS = List.of(
                new Chapter("mate-one", QuestCatalog.load()), new Chapter("mate-two", QuestCatalog.loadMateInTwo()),
                new Chapter("mate-three", QuestCatalog.loadMateInThree()), new Chapter("tactics", QuestCatalog.loadTactics()),
                new Chapter("training", QuestCatalog.loadTraining()), new Chapter("intermezzo", QuestCatalog.loadIntermezzo()));
        static final List<String> ORDER = CHAPTERS.stream().flatMap(chapter -> chapter.ids().stream()).collect(Collectors.toList());
    }

    String command(String action, String value, int index, String saved, String option) {
        var preferences = new WebQuestPreferences(saved);
        var store = new QuestStore(preferences);
        var progress = store.read(Catalog.ORDER);
        var rewards = store.readRewards();
        var chapter = findChapter(progress.currentId());
        if ("menu".equals(action) || "summary".equals(action)) {
            return menu(progress, rewards, store.resumeChapter() ? chapter.key() : null);
        }
        if ("open".equals(action)) {
            chapter = Catalog.CHAPTERS.stream().filter(item -> item.key().equals(value)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown puzzle chapter"));
            progress = QuestProgress.restore(store.remembered(chapter.ids().get(0)), progress.solvedIds(), chapter.ids());
            requireSaved(store.save(progress, rewards));
            return result(preferences, position(chapter, store, progress, rewards, QuestHint.NONE, 0, null, null));
        }
        if ("leave".equals(action)) {
            requireSaved(store.leaveChapter());
            return result(preferences, menu(progress, rewards, null));
        }
        var hint = option == null || option.isEmpty() ? QuestHint.NONE : QuestHint.valueOf(option);
        var quest = chapter.quests().get(chapter.ids().indexOf(progress.currentId()));
        var step = QuestStep.restore(progress.currentSolved() ? 0 : store.ply(quest.id()), quest);
        boolean pending = step.defensePending();
        QuestCatalog.Attempt attempt = null;
        Move rejected = null;
        int award = 0;
        switch (action) {
            case "view" -> { }
            case "previous", "next" -> {
                if (!pending) {
                    int current = chapter.ids().indexOf(progress.currentId());
                    int next = "next".equals(action) ? progress.nextIndex(current, chapter.ids()) : current - 1;
                    if (next >= 0) {
                        progress = progress.select(chapter.ids().get(next));
                        requireSaved(store.save(progress, rewards));
                        hint = QuestHint.NONE;
                    }
                }
            }
            case "hint" -> {
                var direction = hint.nextAction(progress.currentSolved(), pending);
                if (direction == QuestHint.Action.EXPLAIN) return result(preferences, explanation(quest, step, store, progress, value));
                if (direction != QuestHint.Action.WAIT) {
                    rewards = rewards.interrupt(quest.id());
                    requireSaved(store.save(progress, rewards, step.ply()));
                    hint = hint.advance();
                }
            }
            case "explain", "alternative" -> {
                if (!pending) return result(preferences, "alternative".equals(action) ? alternative(quest, step)
                        : explanation(quest, step, store, progress, value));
            }
            case "defense" -> {
                if (pending && quest.id().equals(value) && step.ply() == index) {
                    requireSaved(store.save(progress, rewards, step.advance().ply()));
                }
            }
            case "play" -> {
                if (!pending && !progress.currentSolved()) {
                    Move requested = Move.fromUci(value);
                    if (!step.position(quest).legalMoves().contains(requested)) throw new IllegalArgumentException("Illegal puzzle move");
                    attempt = quest.assess(requested, step.stage());
                    if (attempt.solved()) {
                        var earned = rewards.complete(progress);
                        progress = progress.solveCurrent();
                        rewards = earned.rewards();
                        award = earned.amount();
                        requireSaved(store.saveFinish(progress, rewards, step.finishCheckpoint(requested)));
                        hint = QuestHint.NONE;
                    } else if (attempt.continues()) {
                        requireSaved(store.save(progress, rewards, step.advance().ply()));
                        hint = QuestHint.NONE;
                    } else {
                        rejected = requested;
                        rewards = rewards.interrupt(quest.id());
                        requireSaved(store.save(progress, rewards, step.ply()));
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unknown puzzle action");
        }
        return result(preferences, position(chapter, store, progress, rewards, hint, award, attempt, rejected));
    }

    private static Chapter findChapter(String puzzle) {
        return Catalog.CHAPTERS.stream().filter(entry -> entry.ids().contains(puzzle)).findFirst().orElse(Catalog.CHAPTERS.get(0));
    }

    private static String result(WebQuestPreferences preferences, String data) {
        return "{\"saved\":" + quote(preferences.snapshot()) + ",\"data\":" + data + '}';
    }

    private static String menu(QuestProgress progress, QuestRewards rewards, String resume) {
        var chapters = new StringBuilder("[");
        for (var chapter : Catalog.CHAPTERS) {
            add(chapters, "{\"key\":" + quote(chapter.key()) + ",\"totals\":" + totals(rewards.summarize(chapter.ids(), progress)) + '}');
        }
        return "{\"type\":\"menu\",\"chapters\":" + chapters.append(']') + ",\"resume\":" + quote(resume)
                + ",\"overall\":" + totals(rewards.summarize(Catalog.ORDER, progress)) + ",\"streak\":" + rewards.streak() + '}';
    }

    private static String totals(QuestRewards.Summary summary) {
        return "{\"solved\":" + summary.solved() + ",\"total\":" + summary.total() + ",\"points\":" + summary.points() + '}';
    }

    private static String position(Chapter chapter, QuestStore store, QuestProgress progress, QuestRewards rewards,
                                   QuestHint hint, int award, QuestCatalog.Attempt attempt, Move rejected) {
        int current = chapter.ids().indexOf(progress.currentId());
        var authored = chapter.quests().get(current);
        boolean solved = progress.currentSolved();
        var quest = solved ? authored.restoreFinish(store.finish(authored.id())) : authored;
        var step = QuestStep.restore(solved ? 0 : store.ply(authored.id()), authored);
        var board = solved ? quest.solvedPosition() : step.position(authored);
        var last = solved ? quest.finishingMove() : step.lastMove(authored);
        boolean pending = step.defensePending();
        int next = progress.nextIndex(current, chapter.ids());
        var milestone = progress.milestone(chapter.ids());
        var finish = QuestFinishStudy.forStage(authored, step.stage());
        boolean alternative = finish != null && finish.alternative().equals(rejected);
        return "{\"type\":\"puzzle\",\"chapter\":" + quote(chapter.key()) + ",\"id\":" + quote(authored.id())
                + ",\"rank\":" + (current + 1) + ",\"count\":" + chapter.quests().size() + ",\"ply\":" + step.ply()
                + ",\"stage\":" + step.stage() + ",\"playerMoves\":" + authored.playerMoves()
                + ",\"objective\":" + quote(authored.objective().name()) + ",\"solved\":" + solved + ",\"pending\":" + pending
                + ",\"startSide\":" + quote(authored.position().turn().name()) + ",\"board\":" + quote(squares(board))
                + ",\"sideToMove\":" + quote(board.turn().name()) + ",\"lastMove\":" + move(last)
                + ",\"legalMoves\":" + moves(solved || pending ? List.of() : board.legalMoves())
                + ",\"previous\":" + (!pending && current > 0) + ",\"next\":" + (!pending && next >= 0)
                + ",\"firstUnsolved\":" + (next >= 0 && next < current)
                + ",\"hint\":" + quote(hint.name()) + ",\"hintAction\":" + quote(hint.nextAction(solved, pending).name())
                + ",\"suggestion\":" + (hint == QuestHint.NONE || solved || pending ? "null" : move(authored.playerMove(step.stage())))
                + ",\"earned\":" + award + ",\"streak\":" + rewards.streak()
                + ",\"totals\":" + totals(rewards.summarize(chapter.ids(), progress))
                + ",\"milestone\":{\"next\":" + milestone.next() + ",\"reached\":" + milestone.reached() + '}'
                + ",\"material\":" + (solved && authored.objective() == Objective.MATERIAL ? quest.materialChange() : 0)
                + ",\"specialFinish\":" + (finish != null) + ",\"alternative\":" + (alternative ? quote(finish.notation()) : "null")
                + ",\"attempt\":" + wrongAttempt(attempt, rejected) + '}';
    }

    private static String wrongAttempt(QuestCatalog.Attempt attempt, Move rejected) {
        if (attempt == null || rejected == null) return "null";
        var analysis = attempt.replyAnalysis();
        if (analysis == null) {
            return "{\"move\":" + move(rejected) + ",\"outcome\":" + quote(attempt.outcome().name()) + '}';
        }
        var best = analysis.best();
        var reply = best.get(0);
        return "{\"move\":" + move(rejected) + ",\"outcome\":" + quote(attempt.outcome().name())
                + ",\"line\":" + moves(reply.line()) + ",\"ending\":" + quote(reply.ending().name())
                + ",\"gain\":" + reply.materialChange() + ",\"mate\":" + reply.signedMatePlies()
                + ",\"equal\":" + best.size() + ",\"other\":" + move(best.size() > 1 ? best.get(1).move() : null) + '}';
    }

    private static String explanation(Quest quest, QuestStep step, QuestStore store, QuestProgress progress, String value) {
        var completed = progress.currentSolved() ? quest.restoreFinish(store.finish(quest.id())) : null;
        var explained = completed == null ? quest : completed;
        if (quest.objective() == Objective.MATE) {
            var replay = QuestReplay.mate(quest, step, completed);
            var mate = explained.explanation();
            return replay(replay.payoff(), replay.ply(), explained.matingPattern().name(), mate, null, false);
        }
        var finish = QuestFinishStudy.forStage(explained, explained.playerMoves() - 1);
        boolean triedAlternative = finish != null && finish.alternative().uci().equals(value);
        if (finish != null && (completed != null || step.stage() == finish.stage()) && (value == null || !"replay".equals(value))) {
            String ro = (triedAlternative ? finish.alternativeRo() + "\n\n" : "") + finish.explanationRo();
            String en = (triedAlternative ? finish.alternativeEn() + "\n\n" : "") + finish.explanationEn();
            return "{\"type\":\"finish\",\"ro\":" + quote(ro) + ",\"en\":" + quote(en) + ",\"alternative\":" + quote(finish.notation())
                    + ",\"hasReplay\":" + !triedAlternative + '}';
        }
        var payoff = QuestPayoff.forExplanation(explained);
        return replay(payoff, payoff.defenseStudy() == null ? payoff.plies() : 1, null, null, null, false);
    }

    private static String alternative(Quest quest, QuestStep step) {
        var study = QuestFinishStudy.forStage(quest, step.stage());
        if (study == null) study = QuestFinishStudy.forStage(quest, quest.playerMoves() - 1);
        if (study == null) throw new IllegalArgumentException("Puzzle has no alternate finish");
        return replay(study.replay(quest), 1, null, null, study.notation(), true);
    }

    private static String replay(QuestPayoff payoff, int initial, String pattern, ChessPosition.MateExplanation mate,
                                 String title, boolean alternative) {
        var frames = new StringBuilder("[");
        for (int ply = 0; ply <= payoff.plies(); ply++) {
            var frame = payoff.at(ply);
            var danger = QuestReplay.at(payoff, ply).toggleDanger().shownDanger();
            String marked = danger == null ? "null" : "{\"kind\":" + quote(danger.kind().name())
                    + ",\"square\":" + quote(BoardSquare.name(danger.square())) + ",\"gain\":" + danger.retainedCp() + '}';
            add(frames, "{\"board\":" + quote(squares(frame.position())) + ",\"sideToMove\":" + quote(frame.position().turn().name())
                    + ",\"lastMove\":" + move(frame.move()) + ",\"material\":" + frame.materialChange()
                    + ",\"check\":" + frame.position().isCheck()
                    + ",\"captured\":" + quote(frame.capture() == null ? null : frame.capture().piece().kind().name())
                    + ",\"captureSquare\":" + quote(frame.capture() == null ? null : BoardSquare.name(frame.capture().square()))
                    + ",\"danger\":" + marked + '}');
        }
        var reviewed = payoff.defenseStudy();
        return "{\"type\":\"replay\",\"initial\":" + initial + ",\"pattern\":" + quote(pattern) + ",\"frames\":" + frames.append(']')
                + ",\"lesson\":" + quote(payoff.lesson() == null ? null : payoff.lesson().name()) + ",\"title\":" + quote(title)
                + ",\"alternative\":" + alternative + ",\"mate\":" + mate(mate)
                + ",\"lessonRo\":" + quote(reviewed == null ? null : reviewed.lessonRo())
                + ",\"lessonEn\":" + quote(reviewed == null ? null : reviewed.lessonEn())
                + ",\"depth\":" + (reviewed == null ? "null" : reviewed.depth())
                + ",\"evaluation\":" + (reviewed == null ? "null" : reviewed.evaluationCp())
                + ",\"mateIn\":" + (reviewed == null ? "null" : reviewed.mateIn())
                + ",\"floor\":" + payoff.immediateMaterialFloor() + '}';
    }

    private static String mate(ChessPosition.MateExplanation mate) {
        if (mate == null) return "null";
        var exits = new StringBuilder("[");
        for (var exit : mate.exits()) {
            add(exits, "{\"square\":" + quote(BoardSquare.name(exit.square())) + ",\"blocker\":"
                    + quote(exit.ownBlocker() == null ? null : exit.ownBlocker().kind().name())
                    + ",\"attackers\":" + threats(exit.attackers()) + '}');
        }
        return "{\"king\":" + quote(BoardSquare.name(mate.king())) + ",\"checkers\":" + threats(mate.checkers()) + ",\"exits\":" + exits.append(']') + '}';
    }

    private static String threats(List<ChessPosition.Threat> threats) {
        var encoded = new StringBuilder("[");
        for (var threat : threats) {
            add(encoded, "{\"piece\":" + quote(threat.piece().kind().name()) + ",\"square\":" + quote(BoardSquare.name(threat.square())) + '}');
        }
        return encoded.append(']').toString();
    }

    private static String moves(List<Move> moves) {
        var joined = new StringBuilder("[");
        for (var item : moves) add(joined, move(item));
        return joined.append(']').toString();
    }

    private static void add(StringBuilder array, String item) {
        if (array.length() > 1) array.append(',');
        array.append(item);
    }

    private static String squares(ChessPosition position) {
        var result = new StringBuilder(64);
        for (char symbol : placement(position).toCharArray()) {
            if (symbol == '/') continue;
            if (symbol >= '1' && symbol <= '8') {
                for (int empty = 0; empty < symbol - '0'; empty++) result.append(' ');
            } else result.append(symbol);
        }
        return result.toString();
    }

    private static void requireSaved(boolean saved) {
        if (!saved) throw new IllegalStateException("Unable to save puzzle state");
    }
}
