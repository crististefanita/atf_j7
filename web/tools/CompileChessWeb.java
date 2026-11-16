import java.io.File;
import org.teavm.backend.javascript.JSModuleType;
import org.teavm.tooling.ConsoleTeaVMToolLog;
import org.teavm.tooling.TeaVMProblemRenderer;
import org.teavm.tooling.TeaVMTool;

final class CompileChessWeb {
    public static void main(String[] args) throws Exception {
        var compiler = new TeaVMTool();
        compiler.setMainClass("ro.atf.chess.ChessWebEngine");
        compiler.setTargetDirectory(new File(args[0]));
        compiler.setTargetFileName("chess-engine.mjs");
        compiler.setJsModuleType(JSModuleType.ES2015);
        var log = new ConsoleTeaVMToolLog(false);
        compiler.setLog(log);
        compiler.generate();
        var problems = compiler.getProblemProvider();
        if (!problems.getProblems().isEmpty()) {
            TeaVMProblemRenderer.describeProblems(compiler.getDependencyInfo().getCallGraph(), problems, log);
        }
        if (compiler.wasCancelled() || !problems.getSevereProblems().isEmpty()) {
            throw new IllegalStateException("The chess engine did not compile for browsers");
        }
    }
}
