package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HardLeagueAuditTest {
    @Test void anEmptyOrUnauditedCalibrationCannotEarnARating(Checks check) throws Exception {
        var settings=(com.fasterxml.jackson.databind.node.ObjectNode)new ObjectMapper().readTree("{\"pairs\":50,\"games\":100,\"absolutePlyCap\":220,\"bootstrapSamples\":20000,\"hard\":{\"maximumExtraNodes\":200000,\"analysisBeforeEveryPlayedPly\":true,\"shadowOnOpponentTurn\":true}}");
        HardLeagueAudit.validateSettings(settings);
        settings.put("absolutePlyCap",0);
        check.rejects("A zero-ply experiment cannot be presented as capped games",IllegalArgumentException.class,()->HardLeagueAudit.validateSettings(settings));
        settings.put("absolutePlyCap",220);
        ((com.fasterxml.jackson.databind.node.ObjectNode)settings.get("hard")).put("shadowOnOpponentTurn",false);
        check.rejects("Auditing only Hard turns cannot stand for every played ply",IllegalArgumentException.class,()->HardLeagueAudit.validateSettings(settings));
        ((com.fasterxml.jackson.databind.node.ObjectNode)settings.get("hard")).put("shadowOnOpponentTurn",true);
        settings.put("pairs",0);settings.put("games",0);
        check.rejects("An empty paired sample cannot produce PASS",IllegalArgumentException.class,()->HardLeagueAudit.validateSettings(settings));
    }

    @Test void aMoveCannotSkipOrReuseTheAnalysisOfAnotherCheckpoint(Checks check) {
        var start = ChessMatch.start(new MatchSetup(ChessPosition.Side.WHITE, MatchSetup.Level.HARD, true));
        var first = HardMoveAudit.capture(start);
        var move = ChessPosition.Move.fromUci(first.selected());
        var after = HardLeagueAudit.advance(start, first, move);
        var second = HardMoveAudit.capture(after);
        check.same("The exact audited move advances one ply", 1, after.ply());
        check.rejects("The second analysis cannot replace the missing first ply", IllegalArgumentException.class,
                () -> HardLeagueAudit.advance(start, second, ChessPosition.Move.fromUci(second.selected())));
        check.rejects("The prior analysis cannot be repeated at the next ply", IllegalArgumentException.class,
                () -> HardLeagueAudit.advance(after, first, move));
        var foreign = start.play(ChessPosition.Move.fromUci(first.selected().equals("d2d4") ? "e2e4" : "d2d4"));
        check.rejects("A phase from another opening cannot be borrowed", IllegalArgumentException.class,
                () -> HardLeagueAudit.advance(foreign, second, ChessPosition.Move.fromUci(second.selected())));
    }

    @Test void trustedManifestRejectsForeignSourcesAndRewrittenArtifacts(Checks check,@TempDir Path folder) throws Exception {
        var json=new ObjectMapper();var manifest=json.createObjectNode();var identity=json.createObjectNode();identity.put("sourceRevision","1".repeat(40));manifest.set("identity",identity);
        var files=json.createObjectNode();
        for(String name:List.of("moves.jsonl","league.pgn","results.json","runtime.json","freeze.json","starts.tsv")) {
            Files.writeString(folder.resolve(name),"original");var file=json.createObjectNode();file.put("sha256",HardLeagueAudit.digest("original".getBytes(java.nio.charset.StandardCharsets.UTF_8)));files.set(name,file);
        }
        manifest.set("files",files);var path=folder.resolve("manifest.json");Files.writeString(path,json.writeValueAsString(manifest));
        var trusted=HardLeagueAudit.digest(Files.readAllBytes(path));
        check.same("The original trusted file inventory is accepted",manifest,HardLeagueAudit.trustedManifest(folder,trusted,"1".repeat(40)));
        check.rejects("A receipt from another source cannot become current",IllegalArgumentException.class,()->HardLeagueAudit.trustedManifest(folder,trusted,"2".repeat(40)));
        Files.writeString(folder.resolve("moves.jsonl"),"substituted");
        check.rejects("Changed move evidence is rejected before rating",IllegalArgumentException.class,()->HardLeagueAudit.trustedManifest(folder,trusted,"1".repeat(40)));
        Files.writeString(path,"{}");
        check.rejects("Rewriting the received manifest cannot replace the external trust anchor",IllegalArgumentException.class,()->HardLeagueAudit.trustedManifest(folder,trusted,"1".repeat(40)));
    }

    @Test @Tag("benchmark") void publishesEveryNativeMoveAndThePairedHardRating(Checks check) throws Exception {
        String location=System.getProperty("chess.hardLeague.output","");
        check.same("Actual native calibration needs an explicit external bundle",true,!location.isBlank());
        var folder=Path.of(location).toAbsolutePath();
        check.same("Calibration publication stays outside the project checkout",false,folder.normalize().startsWith(ProjectRoot.locate().toAbsolutePath().normalize()));
        var verified=HardLeagueAudit.verify(folder,System.getProperty("chess.hardLeague.manifestDigest"),System.getProperty("chess.hardLeague.revision"),check);
        check.same("The measured source is distinct from the verifier checkout: measured=" + verified.revision()
                + "; verifier=" + System.getProperty("chess.qualification.commit", "UNSET"),
                System.getProperty("chess.hardLeague.revision"), verified.revision());
        var rating=verified.deltaFromAnchor();
        check.same("Hard local Elo relative to the configured SF anchor: "+(verified.anchor()+rating.eloMidpoint())
                +"; conservative95%="+(verified.anchor()+rating.eloLow95())+".."+(verified.anchor()+rating.eloHigh95())
                +"; W/D/L/U="+verified.wins()+"/"+verified.draws()+"/"+verified.losses()+"/"+verified.unfinished(),verified.pairs()*2,verified.games());
        var output=folder.resolve("qualified-rating.json");
        var json=new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(verified);
        if(Files.exists(output)) check.same("Re-publication preserves the same qualified rating",json,Files.readString(output));
        else Files.writeString(output,json,java.nio.file.StandardOpenOption.CREATE_NEW);
    }
}
