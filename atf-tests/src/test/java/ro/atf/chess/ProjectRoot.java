package ro.atf.chess;

import java.nio.file.Files;
import java.nio.file.Path;

final class ProjectRoot {
    private ProjectRoot() { }

    static Path locate() {
        for (Path folder = Path.of("").toAbsolutePath(); folder != null; folder = folder.getParent()) {
            if (Files.isRegularFile(folder.resolve("android/app/build.gradle"))
                    && Files.isRegularFile(folder.resolve("atf-tests/pom.xml"))) return folder;
        }
        throw new IllegalStateException("Run the tests from the J7 checkout or its test module");
    }
}
