# Contributing

This is a solo-maintained fork of the OpenTelemetry Java Agent. Development
happens on a self-hosted GitLab instance (`gitlab.forza0310.cn`) and is
**push-mirrored** to GitHub. CI runs **only on GitHub** — there is no
`.gitlab-ci.yml`.

## How changes land

1. Commit and push to `main` on GitLab.
2. The push mirror replicates the ref to GitHub.
3. GitHub Actions pick up the `push` event on `main`.

> GitLab merge requests do **not** become GitHub pull requests — push mirroring
> replicates git refs only. If you want the PR gate (multi-JDK + muzzle + lint +
> smoke), open the PR directly on GitHub.

## CI overview

| Workflow | Trigger | Purpose |
| --- | --- | --- |
| `build.yml` | push to `main` / manual | Single-JDK (21) unit test gate. Minimal. |
| `build-pull-request.yml` | pull request | Multi-JDK (8/11/17/21) unit tests + muzzle + lint + 5 smoke suites. |
| `release.yml` | tag `v*` | Builds the agent jar and attaches it to a GitHub Release. |
| `codeql.yml` | schedule + PR | CodeQL security analysis. |
| `gradle-wrapper-validation.yml` | push / PR | Validates the Gradle wrapper jar. |
| `metadata-update.yml` | weekly (Mon 02:07 UTC) / manual | Regenerates `docs/instrumentation-list.yaml`; opens an **issue** if it drifted. Does not auto-commit. |
| `overhead-benchmark-weekly.yml` | weekly (Mon 05:07 UTC) / manual | Runs the overhead benchmark, commits results to `gh-pages`. |
| `publish-petclinic-benchmark-image.yml` | push to `Dockerfile.petclinic` / manual | Builds & pushes the PetClinic benchmark image to ghcr.io. |
| `publish-smoke-test-*-images.yml` (×4) | push to `smoke-tests/images/<scenario>/**` / manual | Builds & pushes smoke-test app images to ghcr.io. |
| `pr-smoke-test-*-images.yml` (×3) | PR touching `smoke-tests/images/<scenario>/**` | Builds smoke images locally (no push) to validate the build. |
| `reusable-*.yml` | called by the above | Shared jobs: muzzle, lint, servlet-image build, smoke-image build, workflow notification. |

### Dropped from upstream

`build-common.yml` / `reusable-pr-build.yml` (112-job test matrix + dependencies
on deleted scripts), `reusable-test-latest-deps.yml`, `prepare-release-branch.yml`,
`prepare-patch-release.yml`, `draft-release-notes.yml`, `ossf-scorecard.yml`, and
the scripts they depended on (`use-cla-approved-bot.sh`, `get-version.sh`,
`update-version.sh`, `update-changelog-for-release.sh`,
`generate-release-contributors.sh`, `check-*.sh`, `deadlock-detector.sh`). The
test matrix was collapsed to one JDK on push and four on PR.

## Releasing

1. Ensure `main` is green (`build.yml` passed).
2. Tag on the GitLab side: `git tag vX.Y.Z && git push origin vX.Y.Z`. The mirror
   pushes the tag to GitHub, triggering `release.yml`.
3. `release.yml` runs `./gradlew assemble` and attaches
   `opentelemetry-javaagent.jar` to a GitHub Release titled `vX.Y.Z`.

No release branches, no automated version bump. The working-copy version is a
SNAPSHOT, so the jar filename inside the build is `*-SNAPSHOT.jar` even though
the release is named after the tag; the workflow renames the asset to
`opentelemetry-javaagent.jar` on attach. Bump `version.gradle.kts` yourself if
you want the jar filename to match the tag.

## Smoke tests

`build-pull-request.yml` runs `:smoke-tests:test` over four suites:

- `other` — spring-boot, fake-backend, and early-jdk8 (`CrashEarlyJdk8Test`)
- `tomcat` / `tomee` / `wildfly` — the servlet scenario

The test sources hardcode image names under the **upstream** `open-telemetry`
ghcr.io org (pinned in `smoke-tests/.../TestImageVersions.java`). Smoke tests
therefore pull upstream-published app images and validate this fork's **agent**
against them. The fork's own `publish-smoke-test-*-images` workflows build
images under the fork's ghcr.io owner (the jib config reads `$GITHUB_REPOSITORY`)
but those are not yet consumed by the tests — keep them to exercise the image
build and as the path to self-hosted images if the test names are ever
parameterized.

The `security-manager` and `grpc` smoke scenarios were removed (test classes and
the `security-manager` image directory deleted).

## Prerequisites for image / benchmark jobs

- **ghcr.io pushes** require the GitHub repository name to be **lowercase**.
  The jib config derives the image path from `$GITHUB_REPOSITORY`, and ghcr.io
  rejects mixed-case names. Rename the repo if needed.
- **`overhead-benchmark-weekly.yml`** commits results to a `gh-pages` branch.
  `gh-pages` is a **GitHub-only** branch: it must be excluded from the
  GitLab→GitHub push mirror (mirror only `refs/heads/main` + tags), otherwise
  each mirror sync overwrites the benchmark result commits. Bootstrap it once
  as an empty orphan branch containing `benchmark-overhead/results/` (a README
  placeholder is enough) and push to the `github` remote, or the
  `actions/checkout ref: gh-pages` step will fail.

## Metadata update

`metadata-update.yml` runs weekly, regenerates
`docs/instrumentation-list.yaml`, and — if it changed — opens (or comments on)
an issue titled *"chore: update instrumentation list [automated reminder]"*. It
deliberately does **not** commit or open a PR: automated commits on the GitHub
side would diverge from the GitLab source of truth. When you see the issue, run
`./gradlew :instrumentation-docs:runAnalysis` locally, commit
`docs/instrumentation-list.yaml` on GitLab, and push — the mirror brings it to
GitHub.

## Local checks before pushing

```bash
./gradlew spotlessCheck                                        # formatting
./gradlew test -PtestJavaVersion=21 -PtestJavaVM=hotspot -PtestIndy=false
./gradlew :instrumentation:muzzle1                             # muzzle sanity (full: muzzle1-4)
./gradlew :smoke-tests:test -PsmokeTestSuite=other             # needs Docker + the app images
```
