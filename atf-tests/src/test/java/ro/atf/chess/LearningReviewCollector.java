package ro.atf.chess;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

/** One source-bound sidecar per saved game; existing journals stay byte-identical. */
final class LearningReviewCollector {
    public static void main(String[] args)throws Exception {
        var json=LearningGameAudit.mapper();var init=json.readTree(Path.of(args[0]).toFile());
        var reviewer=json.treeToValue(init.path("reviewer"),LearningGameAudit.Identity.class);
        for(var item:init.path("games")) {
            var path=Path.of(item.path("journal").asText());var rows=new ArrayList<LearningGameAudit.Envelope>();
            for(var line:Files.readAllLines(path))rows.add(json.readValue(line,LearningGameAudit.Envelope.class));
            var sha=HardLeagueAudit.digest(Files.readAllBytes(path));
            if(!sha.equals(item.path("journalSha256").asText()))throw new IllegalArgumentException("Changed original journal");
            var result=LearningReviewAudit.capture(rows,sha,reviewer,init.path("reviewPolicySha256").asText());
            Files.writeString(Path.of(item.path("output").asText()),json.writerWithDefaultPrettyPrinter().writeValueAsString(result)+"\n",StandardOpenOption.CREATE_NEW);
            System.out.println("REVIEW "+result.body().caseId()+" plies="+result.body().snapshot().turns().size()+" mode="+result.body().mode());
        }
    }
}
