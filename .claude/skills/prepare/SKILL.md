---
name: prepare
description: Detect and install the local prerequisites to build, run, and test Mini WSA on macOS — Docker (to run the cluster) plus JDK 21 + Maven (to run the event-generator on the host and run the test suite). Use before the first run, or when the deploy skill reports a missing prerequisite.
---

# /prepare — install local prerequisites (macOS)

Makes a fresh macOS machine able to run Mini WSA. The cluster runs entirely in Docker (images build in a multi-stage Maven container), so **Docker is the only hard requirement to run it**. JDK 21 + Maven are needed to run the event-generator on the host (`/generate`) and to run `mvn verify`.

## Detect first (never reinstall what's present)
1. **OS:** `uname -s` must be `Darwin`. If not macOS, print the manual install list — Docker, JDK 21, Maven — and stop.
2. **Homebrew:** `command -v brew`. If missing, tell the user to install it from https://brew.sh and stop (Homebrew's installer needs an interactive sudo shell — the user must run it themselves).
3. **Report what's already there:**
   - Docker CLI + daemon: `docker version` then `docker info`
   - Java 21: `/usr/libexec/java_home -v 21` (or `java -version`)
   - Maven: `mvn -v`

## Install only what's missing
- **JDK 21:** `brew install openjdk@21`. Then ensure `JAVA_HOME` is exported — if absent from `~/.zshrc`, append:
  ```
  export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
  ```
- **Maven:** `brew install maven`.
- **Docker:** `brew install --cask docker`, then the user must **launch Docker Desktop once** (GUI grant + starts the daemon). Wait until `docker info` succeeds. Alternative without Desktop: `brew install colima docker && colima start`.

## Verify (report a green/red checklist)
- `docker info` succeeds (daemon up)
- `java -version` shows 21 and `echo $JAVA_HOME` is set
- `mvn -v` shows Maven running on Java 21

## Notes
- Only **Docker** is required to run the cluster (`/deploy`). JDK 21 + Maven are for the host-run generator and the tests.
- Never run `sudo` non-interactively. If a step needs it (Homebrew install, Docker Desktop first launch), ask the user to run it in their terminal with `! <command>`.
