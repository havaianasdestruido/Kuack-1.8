package perfbench;

import java.util.Arrays;
import java.util.Random;

/**
 * Singleplayer packet-latency analysis for the 20 Hz tick-pumped
 * network path vs. the per-frame pump added in this PR
 * (client: Minecraft.runGameLoop -> SingleplayerServerController.pollLocalConnections
 *  + PlayerControllerMP.processNetworkPackets;
 *  server: EaglerIntegratedServerWorker.mainLoop -> processReceivedPacketsOnly).
 *
 * What this models (and what it does NOT):
 *
 *   This is an analytic queueing simulation of the exact pump cadences in the
 *   code, with arrivals at a random phase, not a wall-clock browser measurement.
 *   The browser event loop cannot be timed from the JVM, so what this proves is
 *   the mechanism of the fix: how long a packet that arrived at a random moment
 *   waits before the game sees it, under the old pump (decode only at the next
 *   20 Hz tick) and the new one (decode at the next frame, or next worker-loop
 *   turn on the server).
 *
 *   Model parameters mirror the code exactly:
 *     tick period        50.0 ms   (Timer at 20.0F, Minecraft.runGameLoop)
 *     old client pump    next tick boundary (updateController from runTick)
 *     new client pump    next min(frame boundary, tick boundary); frames 60 FPS
 *     old server pump    next tick boundary (EaglerIntegratedServerWorker.tick())
 *     new server pump    next min(worker-loop turn ~4 ms, tick boundary); the
 *                        4 ms figure is the conservative nested-setTimeout clamp,
 *                        real iterations are usually faster
 *
 * Keepalive round trip = client echo latency after the server's keepalive arrives
 * + server decode latency after the echo arrives (both tick-queued before, both
 * near-instant now).
 *
 * Deterministic: fixed seed, 2,000,000 arrivals. Run with:
 *   ./perfbench/run_bench.sh perfbench.SingleplayerLatencyBench
 */
public final class SingleplayerLatencyBench {

    private static final double TICK_MS = 50.0D;
    private static final double FRAME_MS_60FPS = 1000.0D / 60.0D;
    private static final double FRAME_MS_30FPS = 1000.0D / 30.0D;
    private static final double FRAME_MS_144FPS = 1000.0D / 144.0D;
    private static final double WORKER_TURN_MS = 4.0D; // conservative setTimeout(0) nesting clamp
    private static final int ARRIVALS = 2_000_000;

    private SingleplayerLatencyBench() {
    }

    private static double waitToBoundary(double arrival, double period) {
        double phase = arrival % period;
        return phase == 0.0D ? 0.0D : period - phase;
    }

    private static double[] sample(long seed, double arrivalSpreadMs, int n, java.util.function.DoubleUnaryOperator latencyFn) {
        Random rng = new Random(seed);
        double[] out = new double[n];
        for (int i = 0; i < n; ++i) {
            out[i] = latencyFn.applyAsDouble(rng.nextDouble() * arrivalSpreadMs);
        }
        return out;
    }

    private static double[] percentiles(double[] x) {
        double[] s = x.clone();
        Arrays.sort(s);
        return new double[] { mean(s), s[s.length / 2], s[(int) (s.length * 0.95)],
                s[(int) (s.length * 0.99)], s[s.length - 1] };
    }

    private static double mean(double[] s) {
        double sum = 0.0D;
        for (double v : s) {
            sum += v;
        }
        return sum / (double) s.length;
    }

    private static void report(String label, double[] stats) {
        System.out.printf("  %-58s mean %7.2f ms | p50 %7.2f | p95 %7.2f | p99 %7.2f | max %7.2f%n",
                label, stats[0], stats[1], stats[2], stats[3], stats[4]);
    }

    public static void main(String[] args) {
        System.out.println("=== SingleplayerLatencyBench: tick-pumped vs frame-pumped packet handling ===");
        System.out.printf("arrivals: %,d, seed fixed, spread %,d ms%n%n", ARRIVALS, 60_000);

        // ---- one-way client-side latency (packet arrives at the browser client) ----
        double[] oldClient = sample(0xC11E17L, 60_000.0D, ARRIVALS,
                t -> waitToBoundary(t, TICK_MS));
        double[] newClient60 = sample(0xC11E17L, 60_000.0D, ARRIVALS,
                t -> Math.min(waitToBoundary(t, FRAME_MS_60FPS), waitToBoundary(t, TICK_MS)));
        double[] newClient30 = sample(0xC11E17L, 60_000.0D, ARRIVALS,
                t -> Math.min(waitToBoundary(t, FRAME_MS_30FPS), waitToBoundary(t, TICK_MS)));
        double[] newClient144 = sample(0xC11E17L, 60_000.0D, ARRIVALS,
                t -> Math.min(waitToBoundary(t, FRAME_MS_144FPS), waitToBoundary(t, TICK_MS)));

        System.out.println("one-way, client (packet arrives -> game handles it):");
        report("OLD: next tick boundary (runTick, 20 Hz)", percentiles(oldClient));
        report("NEW: next frame or tick, 30 FPS", percentiles(newClient30));
        report("NEW: next frame or tick, 60 FPS", percentiles(newClient60));
        report("NEW: next frame or tick, 144 FPS", percentiles(newClient144));

        // ---- server-side decode latency (packet arrives at the integrated server) ----
        double[] oldServer = sample(0x5E2E17L, 60_000.0D, ARRIVALS,
                t -> waitToBoundary(t, TICK_MS));
        double[] newServer = sample(0x5E2E17L, 60_000.0D, ARRIVALS,
                t -> Math.min(waitToBoundary(t, WORKER_TURN_MS), waitToBoundary(t, TICK_MS)));

        System.out.println();
        System.out.println("one-way, integrated server (packet arrives -> server handles it):");
        report("OLD: next server tick boundary (20 Hz)", percentiles(oldServer));
        report("NEW: next worker-loop turn (<= 4 ms clamp) or tick", percentiles(newServer));

        // ---- keepalive round trip, as NetHandlerPlayServer measures it ----
        // RTT = time until the client echoes (client pump) + time until the server
        // decodes the echo (server pump). Independent phases, so sum the samples.
        double[] rttOld = new double[ARRIVALS];
        double[] rttNew = new double[ARRIVALS];
        int violations = 0;
        Random rng = new Random(0xBEEF17L);
        for (int i = 0; i < ARRIVALS; ++i) {
            double t0 = rng.nextDouble() * 60_000.0D;
            double t1 = rng.nextDouble() * 60_000.0D;
            rttOld[i] = waitToBoundary(t0, TICK_MS) + waitToBoundary(t1, TICK_MS);
            rttNew[i] = Math.min(waitToBoundary(t0, FRAME_MS_60FPS), waitToBoundary(t0, TICK_MS))
                    + Math.min(waitToBoundary(t1, WORKER_TURN_MS), waitToBoundary(t1, TICK_MS));
            if (rttNew[i] > rttOld[i] + 1e-9D) {
                ++violations;
            }
        }

        System.out.println();
        System.out.println("keepalive round trip at 60 FPS (what the ping display reflects):");
        report("OLD: tick-pumped client + tick-pumped server", percentiles(rttOld));
        report("NEW: frame-pumped client + worker-turn server", percentiles(rttNew));

        double meanOld = mean(rttOld.clone());
        double meanNew = mean(rttNew.clone());
        System.out.println();
        System.out.printf("mean keepalive RTT: %.2f ms -> %.2f ms (%.2fx lower)%n",
                meanOld, meanNew, meanOld / meanNew);
        System.out.printf("samples where NEW was not <= OLD: %d%n", violations);
        if (violations != 0) {
            throw new IllegalStateException("NEW pump was slower than OLD in " + violations + " samples");
        }

        // tick-pump latency is a hard ceiling: never worse than one tick
        double tickMax = 0.0D;
        for (double v : oldClient) {
            if (v > tickMax) {
                tickMax = v;
            }
        }
        System.out.printf("sanity: tick-pump one-way max = %.2f ms (must be <= %.2f)%n", tickMax, TICK_MS);
        if (tickMax > TICK_MS + 1e-9D) {
            throw new IllegalStateException("tick-pump latency exceeded the tick period");
        }
        System.out.println();
        System.out.println("SingleplayerLatencyBench: OK");
    }
}
