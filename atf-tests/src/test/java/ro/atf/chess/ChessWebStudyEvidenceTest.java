package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.ChessWebStudyPublication.Profile;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessWebStudyEvidenceTest {
    @TempDir Path temporary;

    @Test void verifiesOriginalBrowserFrameBeforeConvertingItForExtent(Checks check) throws IOException {
        Path image = temporary.resolve("compact-puzzles.jpg");
        ImageIO.write(new BufferedImage(360, 640, BufferedImage.TYPE_INT_RGB), "JPEG", image.toFile());
        String hash = WebEvidencePublication.sha256(Files.readAllBytes(image));
        var capture = ChessWebStudyPublication.capture(temporary, "compact-puzzles", hash, 360, 640);
        check.same("Screenshot retains the original evidence identity", hash, capture.originalHash());
        check.same("Extent receives a verified PNG in the measured dimensions", true,
                WebEvidencePublication.validPng(capture.png(), new WebEvidencePublication.Frame("compact-puzzles.png", "Fixture", 360, 640)));
        check.same("A substituted original is rejected", true, rejects("0".repeat(64), 360, 640));
        check.same("An original captured on another viewport is rejected", true, rejects(hash, 1280, 900));
    }

    @Test void rejectsUnscaledOrClippedWitnessesAndSelfConflictingAssertions(Checks check) throws IOException {
        var profile = new Profile("compact", 360, 640, 1.3);
        check.same("130 percent is measured, not inferred from the page URL", true,
                ChessWebStudyPublication.expectedFont(json("{\"baseRootFontPx\":16,\"rootFontPx\":20.8}"), profile));
        check.same("A 100 percent capture cannot qualify the compact enlarged profile", false,
                ChessWebStudyPublication.expectedFont(json("{\"baseRootFontPx\":16,\"rootFontPx\":16}"), profile));
        check.same("A square clipped by the far edge does not pass", false,
                ChessWebStudyPublication.boardFits(json("{\"x\":30,\"y\":80,\"width\":340,\"height\":340}"), profile));
        var duplicate = json("{\"checks\":[{\"label\":\"focus\",\"expected\":\"hint\",\"actual\":\"hint\"},"
                + "{\"label\":\"focus\",\"expected\":\"hint\",\"actual\":\"dialog\"}]}");
        check.same("Duplicate interaction labels cannot both pass", false,
                ChessWebStudyPublication.behavior(duplicate.path("checks")).get(1).passed());
    }

    private boolean rejects(String hash, int width, int height) {
        try {
            ChessWebStudyPublication.capture(temporary, "compact-puzzles", hash, width, height);
            return false;
        } catch (IOException invalid) {
            return true;
        }
    }

    private static JsonNode json(String value) throws IOException {
        return WebEvidencePublication.readObject(value.getBytes(StandardCharsets.UTF_8), "Test fixture");
    }
}
