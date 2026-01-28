# JSlideshow - Pure Java Slideshow Creator

Creates video slideshows from JPG images with smooth dissolve transitions using JCodec with parallel H.264 encoding.

## Features

- **Pure Java** - No native dependencies required
- **Parallel encoding** - Batched segment-based encoding across all CPU cores
- **Dissolve transitions** - Smooth alpha-blended dissolves between images
- **Fade in/out** - Automatic fade from/to black at start and end
- **Async muxing** - Dedicated muxer thread writes segments as they complete
- **Lazy image loading** - Images loaded per batch and evicted when no longer needed
- **Java 24 compatible** - Uses modern Java features
- **Customizable** - Configure duration, transition time, and frame rate via CLI
- **Platform independent** - Runs on any OS with Java 24+
- **Timestamped output** - Each run creates uniquely named output file (YYYYMMDD'T'HHmmss-output.mp4)

## How Parallel Encoding Works

JSlideshow uses a **batched segment-based parallel encoding** strategy with an async muxer thread:

```
Segment Layout (for N images):

  Seg 0: FADE_IN    (black -> image[0])
  Seg 1: HOLD       (image[0])
  Seg 2: DISSOLVE   (image[0] -> image[1])
  Seg 3: HOLD       (image[1])
  ...
  Seg 2N-2: HOLD    (image[N-1])
  Seg 2N-1: FADE_OUT (image[N-1] -> black)

Encoding (batches of CPU-count segments):
  [Batch 1: Seg 0..K] --> parallelStream --> ConcurrentSkipListMap
  [Batch 2: Seg K+1..2K] --> parallelStream --> ConcurrentSkipListMap
       ^                                           |
    Load images per batch,                    Muxer thread drains
    evict when done                           consecutive segments
                                              to MP4 file
```

Each segment is encoded as an independent **GOP (Group of Pictures)**, starting with an IDR keyframe followed by P-frames. The async muxer thread writes completed segments to the MP4 file in order, freeing memory immediately.

## Requirements

- Java 24 or later
- Maven 3.9+
- Directory containing JPG or jpg image files

## Quick Start

### Build and Run

```bash
# Clone or download the project
cd jslideshow

# Build the project
mvn clean package

# Run with default settings (3s per image, 0.75s dissolve, 30 fps)
java -jar target/jslideshow-1.3.2-jar-with-dependencies.jar /path/to/images

# Run with custom dissolve transitions
java -jar target/jslideshow-1.3.2-jar-with-dependencies.jar /path/to/images 5.0 2.5 30

# Run without transitions (hard cuts)
java -jar target/jslideshow-1.3.2-jar-with-dependencies.jar /path/to/images 5.0 0 30
```

### Usage

```
java -jar target/jslideshow-1.3.2-jar-with-dependencies.jar <directory> [duration] [transition] [frameRate]
```

**Parameters:**
- `<directory>` - Path to directory containing JPG/jpg files (required)
- `[duration]` - Seconds per image (default: 3.0)
- `[transition]` - Dissolve transition duration in seconds (default: 0.75)
- `[frameRate]` - Frames per second (default: 30)

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

### Memory Management

- **Images:** Loaded lazily per batch, evicted when no longer needed by future segments. At most ~batchSize images in memory.
- **Encoded segments:** Inserted into `ConcurrentSkipListMap` by workers, removed by muxer immediately after writing. At steady state: ~batchSize segments in flight.
- **Encoder buffers:** 5MB worst-case buffer per frame, compacted to ~150KB immediately after encoding.

## Configuration

Default values can be overridden via command line arguments:

```java
public static final double DEFAULT_DURATION = 3.0;      // seconds per image
public static final double DEFAULT_TRANSITION = 0.75;    // dissolve transition duration
public static final int DEFAULT_FRAME_RATE = 30;         // frames per second
```

The output filename is automatically generated with a timestamp: `YYYYMMDD'T'HHmmss-output.mp4`

## Example Output

```
Parameters:
  Duration:   5.00 seconds
  Transition: 2.50 seconds
  Frame rate: 30 fps

Processing directory: /home/user/photos/vacation
Found 5 images
Duration: 5.00 seconds per image (75 hold frames @ 30 fps)
Transition: 2.50 seconds (75 frames)
Output file: 20260128T175406-output.mp4

Encoding 5 images into 11 segments (825 total frames) @ 30 fps
Batch size: 20 (parallel threads)
  Loaded: DSC_7141_1920x1080.JPG (1620x1080)
  Loaded: DSC_7145_1920x1080.JPG (1620x1080)
  Loaded: DSC_7151_1920x1080.JPG (1620x1080)
  Loaded: DSC_7155_1920x1080.JPG (1620x1080)
  Loaded: DSC_7161_1920x1080.JPG (1620x1080)
  Encoded segment 2/11 (HOLD, 75 frames)
  Encoded segment 10/11 (HOLD, 75 frames)
  Encoded segment 6/11 (HOLD, 75 frames)
  Encoded segment 4/11 (HOLD, 75 frames)
  Encoded segment 8/11 (HOLD, 75 frames)
  Encoded segment 9/11 (DISSOLVE, 75 frames)
  Encoded segment 3/11 (DISSOLVE, 75 frames)
  Encoded segment 7/11 (DISSOLVE, 75 frames)
  Encoded segment 5/11 (DISSOLVE, 75 frames)
  Encoded segment 11/11 (FADE_OUT, 75 frames)
  Encoded segment 1/11 (FADE_IN, 75 frames)
  Muxed segment 1/11
  Muxed segment 2/11
  ...
  Muxed segment 11/11
Wrote 825 total frames

Success! Created 20260128T175406-output.mp4
Total processing time: 13.78 seconds
```

Note: Segments encode out-of-order (hold segments finish before dissolves), confirming true parallel execution. The muxer writes them in correct order.

## Performance

**For 5 images at 1620x1080 pixels, 5s duration, 2.5s transitions @ 30fps:**
- Processing time: ~14 seconds
- Total frames: 825 (5×75 hold + 4×75 dissolve + 75 fade-in + 75 fade-out)
- Output size: ~30MB (H.264 in MP4 container)

## Building from Source

```bash
# Compile only
mvn compile

# Compile and package (fat JAR with dependencies)
mvn clean package

# Skip tests
mvn clean package -DskipTests
```

## Troubleshooting

### Out of Memory Error
Increase heap size for large image sets:
```bash
java -Xmx8g -jar target/jslideshow-1.3.2-jar-with-dependencies.jar /path/to/images
```

### No Images Found
- Ensure directory contains files with .JPG or .jpg extensions
- Check file permissions
- Verify you specified the correct directory path

### Unsupported class file major version
You're not using Java 24. Download and install Java 24 or later.

## Technical Details

### Dependencies
- **JCodec 0.2.5** - Pure Java H.264/MP4 video encoding
- **JCodec JavaSE 0.2.5** - AWT integration for BufferedImage conversion

### Output Format
- Container: MP4
- Video codec: H.264
- Color space: YUV420
- Frame rate: 30 fps (configurable)

## License

This is example code for educational purposes. JCodec is licensed under FreeBSD License.

## Author

krystalmonolith
