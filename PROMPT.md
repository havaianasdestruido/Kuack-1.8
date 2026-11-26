# Project instructions — Kuack-1.8

Guidance for anyone (human or AI agent) sending changes to this repository.

## Scope

Kuack-1.8 is a fork of EaglercraftX 1.8 (Minecraft 1.8 compiled to JavaScript with TeaVM)
plus the patches and tooling needed to build and extend it. See `TODO.md` for the project's
priorities and `README.md` for how the client is built.

## General rules

- Keep changes focused: one topic per pull request.
- Do not change gameplay, rendering output, collision accuracy or networking behaviour unless
  that is the explicit point of the PR, and say so in the description when it is.
- Code that is compiled by TeaVM must stay TeaVM-compatible: no threads, no JVM-only APIs,
  no unbounded caches, nothing hidden rather than removed.
- Builds must keep working: the `Build client` workflow
  (`.github/workflows/build.yml`) runs on every push and pull request.

## Performance pull requests

**Every performance PR should contain a reliable benchmark of the performance gain that the PR
claims.** A performance claim without a benchmark is not reviewable, and is not merged.

A benchmark counts as reliable when it:

- measures the **before and after** states of the same workload, with the same code path and
  environment (same JVM/run flags, same input sizes), so the comparison is apples to apples;
- reports concrete numbers (median or mean per operation, plus the speedup factor), not just
  a description of the change;
- uses a realistic or explicitly stated input size, and includes more than one size where the
  claim is about asymptotic behaviour;
- can be reproduced by a reviewer — include the command and the harness/class used, e.g.
  `./perfbench/run_bench.sh perfbench.SomeBench`, and commit the harness under `perfbench/`
  when it is new;
- is backed by an equivalence check for any optimisation: show that the output is unchanged
  (randomised or exhaustive comparison against the original implementation), as the existing
  `perfbench.Verify*` classes do.

Put the numbers in the PR description (a short before/after table is ideal) and extend
`perfbench/FINDINGS.md` with the same data when the change is merged. If a candidate change is
implemented but measured to be a non-win, record the measurement and back the change out
instead of shipping it — see FINDINGS #2 for the existing example.

## Documentation

Update `README.md` and `TODO.md` when behaviour, build steps or priorities change. Keep
evidence (benchmarks, verification results) next to the code it justifies under `perfbench/`.
