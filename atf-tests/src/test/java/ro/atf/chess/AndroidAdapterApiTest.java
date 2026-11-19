package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Guards the observed API 28 stream failure in adapter and instrumentation source. */
@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class AndroidAdapterApiTest {
    @Test void nativeSourcesUseTheApi28CompatibleCollector(Checks check) throws Exception {
        var failures = new ArrayList<String>();
        for (String folder : List.of("android/app/src/main/java", "android/app/src/androidTest/java")) {
            try (var files = Files.walk(ProjectRoot.locate().resolve(folder))) {
                for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    if (!unsupported(Files.readString(file)).isEmpty()) failures.add(file.getFileName().toString());
                }
            }
        }
        check.same("Adapters and native probes avoid the observed Stream.toList runtime failure", List.of(), failures);
    }

    @Test void rejectsTheUnsupportedCallWithoutFlaggingACollectorOrQuotedExample(Checks check) throws Exception {
        check.same("A stream terminal call is rejected", List.of("stream.toList"),
                unsupported("class Probe { Object f() { return stream.toList(); } }"));
        check.same("Collectors and documentation examples are admitted", List.of(), unsupported(
                "class Probe { String note = \"stream.toList()\"; Object f() { return stream.collect(java.util.stream.Collectors.toList()); } }"));
    }

    private static List<String> unsupported(String source) throws Exception {
        var file = new SimpleJavaFileObject(URI.create("string:///Probe.java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignored) { return source; }
        };
        var task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, null, diagnostic -> { },
                List.of("-proc:none"), null, List.of(file));
        var found = new ArrayList<String>();
        var scan = new TreeScanner<Void, Void>() {
            @Override public Void visitMethodInvocation(MethodInvocationTree call, Void ignored) {
                if (call.getMethodSelect() instanceof MemberSelectTree select && select.getIdentifier().contentEquals("toList")) {
                    String owner = select.getExpression().toString();
                    if (!owner.equals("Collectors") && !owner.equals("java.util.stream.Collectors")) found.add(select.toString());
                }
                return super.visitMethodInvocation(call, ignored);
            }
        };
        for (var unit : task.parse()) scan.scan(unit, null);
        return List.copyOf(found);
    }
}
