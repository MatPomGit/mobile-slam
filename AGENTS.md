# Agent instructions

## Project summary

Mobile SLAM is a didactic Android application that makes the stages and limitations of monocular visual SLAM visible to university students. It prioritizes clarity over accuracy and performance; `.github/memory-bank/` is the authoritative, concise record of product context, architecture, technical context, active work, and progress.

## Plan-and-Act protocol

1. Before editing, read this file and all five Memory Bank files, then inspect the relevant code and existing build/test commands.
2. In `activeContext.md`, record the goal, files in scope, ordered steps, risks, test strategy, and acceptance criteria.
3. In autonomous work, self-review the plan for consistency with `systemPatterns.md`; otherwise obtain approval before acting.
4. Make small, scoped changes. After each implementation step, run the available targeted build/tests and update the plan status.
5. If assumptions fail or a material risk appears, stop implementation, revise the plan, and review it before continuing.
6. Verify acceptance criteria, update the Memory Bank, and summarize changes, open issues, and the proposed next milestone.

## Memory Bank rules

- At the start of every task, read all five files in `.github/memory-bank/`.
- At the end of every task, rewrite `activeContext.md` and update `progress.md`.
- Update `systemPatterns.md` when architecture changes and `techContext.md` when dependencies or versions change.
- Keep Memory Bank files concise, factual, and in English, with no duplicated information.
- Treat the Memory Bank as authoritative. If it disagrees with code, stop and explicitly reconcile the discrepancy.

## Code rules

- Follow Kotlin style enforced by ktlint.
- Keep code under the package layout documented in `systemPatterns.md`.
- Add KDoc to every public class, explaining relevant mathematics for vision classes.
- Do not use shared mutable state without synchronization; prefer immutable snapshots between camera, tracking, mapping, and rendering work.
- Put algorithm thresholds in `Settings`, not scattered implementation constants.
- Keep UI strings in Polish and source code/comments in English.

## Definition of Done

- The app compiles and its unit tests pass where the Android toolchain is available.
- README and relevant KDoc describe behavior and limitations.
- The Memory Bank reflects the final state and any remaining blockers.

## Forbidden

- Skipping the plan or editing outside its approved scope.
- Silent dependency or architecture changes.
- Deleting tests to make the build pass.
