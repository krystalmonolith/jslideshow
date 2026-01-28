# JSlideshow - Pure Java Slideshow Creator

Creates video slideshows from JPG images using JCodec with parallel H.264 encoding.

## Features

- **Pure Java** - No native dependencies required
- **Parallel encoding** - Images encoded as independent GOPs across all CPU cores
- **Java 24 compatible** - Uses modern Java features
- **Customizable** - Configure duration, transition time, and frame rate via CLI
- **Platform independent** - Runs on any OS with Java 24+
- **Command line interface** - Specify image directory and encoding parameters
- **Timestamped output** - Each run creates uniquely named output file (YYYYMMDD'T'HHmmss-output.mp4)
- **Flexible input** - Processes all .JPG and .jpg files in specified directory

## How Parallel Encoding Works

JSlideshow uses a two-phase **segment-based parallel encoding** strategy:

```
Phase 1: Parallel Encoding (one encoder per image)
  [Image 1] --> [Encoder 1] --> [Segment 1: IDR + P-frames]
  [Image 2] --> [Encoder 2] --> [Segment 2: IDR + P-frames]
  [Image 3] --> [Encoder 3] --> [Segment 3: IDR + P-frames]
       ^
    PARALLEL via IntStream.parallel()

Phase 2: Sequential Muxing
  [Segment 1] --> [Segment 2] --> [Segment 3] --> MP4 file
```

Each image is encoded as an independent **GOP (Group of Pictures)**, starting with an IDR keyframe followed by P-frames. Since GOPs have no cross-image dependencies, encoding is fully parallelizable. Encoded segments are then muxed into the MP4 container in order.

The `CodecMP4MuxerTrack` handles Annex B to MP4 (ISO BMF) NAL unit conversion and SPS/PPS extraction internally during the muxing phase.

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

# Run with default settings (3s per image, 30 fps)
java -jar target/jslideshow-1.3.1-jar-with-dependencies.jar /path/to/images

# Run with custom settings
java -jar target/jslideshow-1.3.1-jar-with-dependencies.jar /path/to/images 5.0 0 30
```

### Usage

```
java -jar target/jslideshow-1.3.1-jar-with-dependencies.jar <directory> [duration] [transition] [frameRate]
```

**Parameters:**
- `<directory>` - Path to directory containing JPG/jpg files (required)
- `[duration]` - Seconds per image (default: 3.0)
- `[transition]` - Dissolve transition duration in seconds (default: 0.0, not yet supported in parallel encoder)
- `[frameRate]` - Frames per second (default: 30)

## Architecture

**Three-class design:**

- **`Main.java`** - CLI entry point, validates args, parses optional parameters
- **`SlideshowCreator2.java`** - Loads images, calculates frame counts, orchestrates encoding
- **`JCodecParallelEncoder.java`** - Segment-based parallel H.264 encoding and MP4 muxing
  - `encodeSegment()` - Encodes one image as an independent GOP (IDR + P-frames)
  - `muxSegments()` - Writes segments sequentially to MP4 with global frame numbering

### Memory Optimization

Encoded frame data is compacted into right-sized buffers immediately after encoding. The H.264 encoder requires a worst-case ~5MB buffer per frame, but actual encoded data is typically ~150KB. Without compaction, encoding 144 images at 150 frames each would require ~105GB; with compaction, it requires ~3.3GB.

## Configuration

Default values can be overridden via command line arguments:

```java
public static final double DEFAULT_DURATION = 3.0;      // seconds per image
public static final double DEFAULT_TRANSITION = 0.0;     // transition duration (disabled)
public static final int DEFAULT_FRAME_RATE = 30;         // frames per second
```

The output filename is automatically generated with a timestamp: `YYYYMMDD'T'HHmmss-output.mp4`

## Example Output

```
Parameters:
  Duration:   5.00 seconds
  Transition: 0.00 seconds
  Frame rate: 30 fps

Processing directory: /home/user/photos/vacation
Found 5 images
Duration: 5.00 seconds per image (150 frames @ 30 fps)
Output file: 20260128T124627-output.mp4

  Loaded: DSC_7141_1920x1080.JPG (1620x1080)
  Loaded: DSC_7145_1920x1080.JPG (1620x1080)
  Loaded: DSC_7151_1920x1080.JPG (1620x1080)
  Loaded: DSC_7155_1920x1080.JPG (1620x1080)
  Loaded: DSC_7161_1920x1080.JPG (1620x1080)
Encoding 5 images, 150 frames each @ 30 fps
  Encoded segment 1/5
  Encoded segment 5/5
  Encoded segment 4/5
  Encoded segment 2/5
  Encoded segment 3/5
Muxing segments...
Wrote 750 total frames

Success! Created 20260128T124627-output.mp4
Total processing time: 10.13 seconds
```

Note: Segments complete out-of-order (1, 5, 4, 2, 3), confirming true parallel execution.

## Performance

**For 5 images at 1620x1080 pixels, 5s duration @ 30fps:**
- Processing time: ~10 seconds
- Output size: ~30MB (H.264 in MP4 container)
- Total frames: 750

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
java -Xmx8g -jar target/jslideshow-1.3.1-jar-with-dependencies.jar /path/to/images
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
