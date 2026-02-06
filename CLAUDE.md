# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JSlideshow is a pure Java command-line application that creates MP4 video slideshows from JPG images with smooth dissolve transitions. It uses JCodec (pure Java video encoding library) with no native dependencies.

## Build Commands

```bash
# Build the project (creates fat JAR with dependencies)
# Uses MAVEN_HOME and JAVA_HOME environment variables
java -cp "$MAVEN_HOME/boot/plexus-classworlds-2.9.0.jar" -Dclassworlds.conf="$MAVEN_HOME/bin/m2.conf" -Dmaven.home="$MAVEN_HOME" -Dmaven.multiModuleProjectDirectory="$PWD" org.codehaus.plexus.classworlds.launcher.Launcher clean package -DskipTests

# Run the application
java -jar target/jslideshow-1.3.4-jar-with-dependencies.jar <directory> [duration] [transition] [frameRate]
```

## Architecture

**Three-class design:**

- **`Main.java`** - CLI entry point, validates args, parses optional parameters
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

These can be overridden via optional command line arguments: `[duration] [transition] [frameRate]`

## Dependencies

- **JCodec 0.2.5** - Pure Java H.264/MP4 video encoding
- **JCodec JavaSE 0.2.5** - AWT integration for BufferedImage conversion

## Requirements

- Java 24+
- Maven 3.9+

## Output Format

- Container: MP4
- Codec: H.264
- Filename pattern: `YYYYMMDD'T'HHmmss-output.mp4`

## Fast Test

```bash
java -jar target/jslideshow-1.3.4-jar-with-dependencies.jar images 5.0 0 30
```

## Bump Version

When asked to "bump the version" or "bump":
1. Read the current `<version>` tag from pom.xml
2. Increment the patch number (e.g., 1.2.1 → 1.2.2)
3. Update the `<version>` tag in pom.xml
4. Report the change (e.g., "Version bumped: 1.2.1 → 1.2.2")
