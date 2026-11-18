package ro.atf.chess;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

/** Offline JSON-lines adapter. A forced journal write precedes every actual move. */
final class LearningGameCollector {
    public static void main(String[] args) throws Exception {
        var json=LearningGameAudit.mapper();
        if(args.length==2&&args[0].equals("validate")) {
            var rows=new ArrayList<LearningGameAudit.Envelope>();
            for(var line:Files.readAllLines(Path.of(args[1])))rows.add(json.readValue(line,LearningGameAudit.Envelope.class));
            if(rows.isEmpty())throw new IllegalArgumentException("Empty game");
            var origin=rows.getFirst().body().before();
            var game=LearningGameAudit.validate(rows,origin.identity(),origin.caseId(),origin.initialFen());
            System.out.println(json.writeValueAsString(java.util.Map.of("case",origin.caseId(),"plies",game.ply(),"result",game.result(),"checkpoint",game.checkpoint())));
            return;
        }
        try(var input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
            var init=json.readTree(input.readLine());
            var identity=json.treeToValue(init.get("identity"),LearningGameAudit.Identity.class);
            var fen=init.path("initialFen").asText();
            var setup=new MatchSetup(ChessPosition.Side.valueOf(init.path("humanSide").asText()),MatchSetup.Level.HARD,true);
            var game=ChessMatch.fromPosition(ChessPosition.fromFen(fen),setup);
            try(var file=FileChannel.open(Path.of(init.path("journal").asText()),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                var observer=new LearningGameAudit(identity,init.path("caseId").asText(),fen,game,receipt->{
                    try {
                        var bytes=(json.writeValueAsString(receipt)+"\n").getBytes(StandardCharsets.UTF_8);
                        var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())file.write(buffer);file.force(true);
                    }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
                });
                System.out.println("{\"ready\":true}");System.out.flush();
                for(String line;(line=input.readLine())!=null;) {
                    var command=json.readTree(line);
                    switch(command.path("command").asText()) {
                        case "observe" -> System.out.println(json.writeValueAsString(observer.observe()));
                        case "play" -> {
                            observer.play(ChessPosition.Move.fromUci(command.path("move").asText()),LearningGameAudit.Actor.valueOf(command.path("actor").asText()),command.path("evidence").isTextual()?command.path("evidence").asText():null);
                            System.out.println(json.writeValueAsString(java.util.Map.of("ply",observer.match().ply(),"result",observer.match().result())));
                        }
                        case "stop" -> {return;}
                        default -> throw new IllegalArgumentException("Unknown collector command");
                    }
                    System.out.flush();
                }
            }
        }
    }
}
