package probe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v1_8.buildtools.task.teavm.TeaVMBridgeImpl;

/**
 * Drives the project's real TeaVM bridge (buildtools/TeaVMBridge.jar, TeaVMTargetType.JAVASCRIPT,
 * optimizationLevel ADVANCED - the same settings CompileLatestClientHeadless uses) over a small
 * self-contained classpath. Development-only, not part of the game.
 *
 * args: <compiled-classes-dir> <colon-separated-jars> <output-dir> <main-class> [file-name]
 */
public final class TeaVMProbe {

    private TeaVMProbe() {
    }

    public static void main(String[] args) {
        List<String> cp = new ArrayList<String>();
        cp.add(args[0]);
        for (String j : args[1].split(":")) {
            if (!j.isEmpty()) {
                cp.add(j);
            }
        }
        Map<String, Object> opts = new HashMap<String, Object>();
        opts.put("classPathEntries", cp);
        opts.put("entryPointName", "main");
        opts.put("mainClass", args[3]);
        opts.put("minifying", Boolean.FALSE); // readable JS for inspection
        opts.put("optimizationLevel", "ADVANCED"); // production setting
        opts.put("generateSourceMaps", Boolean.FALSE);
        opts.put("targetDirectory", args[2]);
        opts.put("targetFileName", args.length > 4 ? args[4] : "probe.js");
        boolean ok = TeaVMBridgeImpl.compileTeaVM(opts);
        System.out.println("TeaVM compile succeeded: " + ok);
        if (!ok) {
            throw new RuntimeException("TeaVM reported problems");
        }
    }
}
