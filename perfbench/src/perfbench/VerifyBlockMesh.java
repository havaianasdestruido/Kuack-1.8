package perfbench;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

/**
 * Differential verification for the BlockModelRenderer scratch-reuse change.
 *
 * Renders thousands of random (model, block, position, checkSides) cases through BOTH the
 * pre-change implementation (perfbench.BlockModelRendererVanilla, which allocates fresh scratch
 * per call, exactly like production did before) and the current production implementation
 * (whose scratch objects are reused across every call) under every combination of
 * ambient-occlusion / anaglyph / deferred / dynamic-lights modes, and compares the complete
 * recorded WorldRenderer call sequences.
 *
 * Because the production instance is REUSED across all cases while the vanilla reference
 * allocates fresh scratch per call, any read of stale scratch state (an unwritten bounds
 * entry, an AO vertex slot not covered by the VertexTranslations permutation, a bounds flag
 * left set from a previous quad) would produce a different vertex stream and fail this test.
 */
public class VerifyBlockMesh {
    public static void main(String[] args) {
        BenchBootstrap.register();
        World world = MeshHarness.newWorld();
        Random rng = new Random(0xB10C4A17L);

        int cases = 0;
        int mismatches = 0;

        boolean[] aoModes = { true, false };
        boolean[] anaglyphModes = { false, true };
        boolean[] deferredModes = { false, true };
        boolean[] dynLightsModes = { false, true };
        boolean[] checkSidesModes = { true, false };

        for (boolean ao : aoModes) {
            for (boolean anaglyph : anaglyphModes) {
                for (boolean deferred : deferredModes) {
                    for (boolean dynLights : dynLightsModes) {
                        MeshHarness.setAnaglyph(anaglyph);
                        MeshHarness.setDeferred(deferred);
                        MeshHarness.setDynamicLights(dynLights);
                        for (int i = 0; i < 100; ++i) {
                            boolean inset = rng.nextBoolean();
                            boolean tinted = rng.nextBoolean();
                            boolean general = rng.nextBoolean();
                            boolean skipFaces = rng.nextBoolean();
                            MeshHarness.SyntheticModel model = MeshHarness.SyntheticModel
                                    .build(rng, inset, tinted, general, skipFaces);
                            Block block = MeshHarness.PALETTE[rng.nextInt(MeshHarness.PALETTE.length)];
                            BlockPos pos = MeshHarness.randomPos(rng);
                            boolean checkSides = checkSidesModes[rng.nextInt(checkSidesModes.length)];

                            MeshHarness.RecordingWorldRenderer rv = new MeshHarness.RecordingWorldRenderer();
                            MeshHarness.RecordingWorldRenderer rp = new MeshHarness.RecordingWorldRenderer();
                            boolean retV = MeshHarness.renderWith(true, MeshHarness.newCache(world), model, block,
                                    pos, rv, checkSides, ao);
                            boolean retP = MeshHarness.renderWith(false, MeshHarness.newCache(world), model, block,
                                    pos, rp, checkSides, ao);

                            ++cases;
                            if (retV != retP || !rv.record.equals(rp.record)) {
                                ++mismatches;
                                if (mismatches <= 5) {
                                    System.out.println("MISMATCH case " + cases + " ao=" + ao + " anaglyph="
                                            + anaglyph + " deferred=" + deferred + " dynLights=" + dynLights
                                            + " checkSides=" + checkSides + " block=" + block.getClass().getSimpleName()
                                            + " pos=" + pos + " retV=" + retV + " retP=" + retP);
                                    System.out.println("  vanilla calls: " + rv.record.size()
                                            + " production calls: " + rp.record.size());
                                    int n = Math.min(rv.record.size(), rp.record.size());
                                    for (int k = 0; k < n; ++k) {
                                        if (!rv.record.get(k).equals(rp.record.get(k))) {
                                            System.out.println("  first differing call #" + k + ":");
                                            System.out.println("    vanilla:    " + rv.record.get(k));
                                            System.out.println("    production: " + rp.record.get(k));
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        MeshHarness.setDeferred(false);
        MeshHarness.setDynamicLights(false);
        MeshHarness.setAnaglyph(false);

        System.out.println("VerifyBlockMesh: " + cases + " cases, " + mismatches + " mismatches");
        if (mismatches > 0) {
            System.exit(1);
        }
    }
}
