# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JSlideshow is a pure Java command-line application that creates MP4 video slideshows from JPG images with smooth dissolve transitions. It uses JCodec (pure Java video encoding library) with no native dependencies.

## Git Branches

The default branch is `master`. There is no `main` branch.

## Build Commands

```bash
# Build the project (creates fat JAR with dependencies)
# Uses MAVEN_HOME and JAVA_HOME environment variables
java -cp "$MAVEN_HOME/boot/plexus-classworlds-2.9.0.jar" -Dclassworlds.conf="$MAVEN_HOME/bin/m2.conf" -Dmaven.home="$MAVEN_HOME" -Dmaven.multiModuleProjectDirectory="$PWD" org.codehaus.plexus.classworlds.launcher.Launcher clean package -DskipTests

# Run the application
java -jar target/jslideshow-1.3.4-jar-with-dependencies.jar [options] <directory>
#   -d, --duration <seconds>     Seconds per image (default: 3.0)
#   -t, --transition <seconds>   Transition duration (default: 0.75)
#   -f, --frame-rate <fps>       Frame rate (default: 30)
#   -o, --output <path>          Output MP4 file path (default: timestamped filename)
#   -b, --batchsize <n>          Parallel encoding batch size (default: available processors)
#   -h, --help                   Show help
#   -V, --version                Show version
```

## Architecture

**Three-class design:**

- **`Main.java`** - CLI entry point using picocli for getopt-style option parsing
- **`SlideshowCreator2.java`** - Finds images, calculates frame counts, orchestrates encoding
- **`JCodecParallelEncoder.java`** - Batched parallel H.264 encoding with async MP4 muxing
  - `buildSegmentSpecs()` - Generates segment layout (fade-in, holds, dissolves, fade-out)
  - `encodeHoldSegment()` - Encodes static image as IDR + P-frames
  - `encodeDissolveSegment()` - Encodes alpha-blended transition between two images
  - `encodeFadeSegment()` - Encodes fade in/out by dissolving with a black image
  - `muxerLoop()` - Async thread that drains consecutive segments to MP4
  - `spin()` / `clearSpinner()` - Animated text spinner (`|/-\`) showing per-frame encoding progress

**Default configuration constants** at top of `SlideshowCreator2.java`:
```java
public static final double DEFAULT_DURATION = 3.0;      // seconds per image
public static final double DEFAULT_TRANSITION = 0.75;    // transition duration
public static final int DEFAULT_FRAME_RATE = 30;         // frames per second
```

These can be overridden via command line options: `-d`, `-t`, `-f`

**Frame validation:** If duration and transition both produce 0 frames at the given frame rate, the program errors out. Warnings are shown when only one rounds to zero.

## Dependencies

- **JCodec 0.2.5** - Pure Java H.264/MP4 video encoding
- **JCodec JavaSE 0.2.5** - AWT integration for BufferedImage conversion
- **picocli 4.7.6** - CLI argument parsing with getopt-style options

## Requirements

- Java 24+
- Maven 3.9+

## Output Format

- Container: MP4
- Codec: H.264
- Default filename pattern: `YYYYMMDD'T'HHmmss-output.mp4` (override with `-o`)

## Docker

Build and run without compiling from source. The Dockerfile downloads the fat JAR from the latest GitHub release.

```bash
# Build the image
docker build -t jslideshow .

# Linux — separate input and output mounts
docker run --rm -v ./my-photos:/images -v ./output:/output jslideshow -o /output/slideshow.mp4 /images

# Windows (PowerShell) — separate input and output mounts
docker run --rm -v ${PWD}\my-photos:/images -v C:\Users\me\Desktop:/output jslideshow -o /output/slideshow.mp4 /images

# Windows (Git Bash) — must disable MSYS path conversion
MSYS_NO_PATHCONV=1 docker run --rm -v C:\Users\me\photos:/images -v C:\Users\me\Desktop:/output jslideshow -o /output/slideshow.mp4 /images
```

Base image: `eclipse-temurin:25-jre`. Workdir: `/data`. All jslideshow CLI options are passed directly after the image name.

## Test Profiles

```bash
mvn package exec:java -Pfasttest     # images/                → fasttest.mp4
mvn package exec:java -Pslowtest    # images/images1080/      → slowtest.mp4
mvn package exec:java -Psnailtest   # images/images1080/      → snailtest.mp4  (batchsize=1)
mvn package exec:java -Phugetest    # images/images-original/ → hugetest.mp4
```

## Bump Version

When asked to "bump the version" or "bump":
1. Read the current `<version>` tag from pom.xml
2. Increment the patch number (e.g., 1.2.1 → 1.2.2)
3. Update the `<version>` tag in pom.xml
4. Report the change (e.g., "Version bumped: 1.2.1 → 1.2.2")
