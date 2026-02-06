# JSlideshow - Pure Java Slideshow Creator
## Creates video slideshows from JPG images with smooth dissolve transitions using JCodec with parallel H.264 encoding.

## Features

- **Pure Java** - No native dependencies required
- **Parallel encoding** - Batched segment-based encoding across all CPU cores
- **Dissolve transitions** - Smooth alpha-blended dissolves between images
- **Fade in/out** - Automatic fade from/to black at start and end
- **Async muxing** - Dedicated muxer thread writes segments as they complete
- **Lazy image loading** - Images loaded per batch and evicted when no longer needed
- **Java 24 compatible** - Uses modern Java features
- **Customizable** - Configure duration, transition time, frame rate, batch size, and output path via CLI
- **Platform independent** - Runs on any OS with Java 24+
- **Timestamped output** - Default uniquely named output file (YYYYMMDD'T'HHmmss-output.mp4), or specify with `-o`

## Usage

```
java -jar target/jslideshow-1.3.4-jar-with-dependencies.jar [options] <directory>
```

**Options:**
- `-d, --duration <seconds>` - Seconds per image (default: 3.0)
- `-t, --transition <seconds>` - Dissolve transition duration in seconds (default: 0.75)
- `-f, --frame-rate <fps>` - Frames per second (default: 30)
- `-o, --output <path>` - Output MP4 file path (default: timestamped filename)
- `-b, --batchsize <n>` - Parallel encoding batch size (default: number of available processors)
- `-h, --help` - Show help message
- `-V, --version` - Show version

**Positional:**
- `<directory>` - Path to directory containing JPG/jpg files (required)

**Note:** The ```--batchsize``` option can be used to limit the memory usage for large encoding jobs because
lower batch sizes load fewer images simultaneously and have less incomplete frames stacked up in memory
waiting to be mux'ed (written) out.
Using ``--batchsize 1`` is effectively sequential encoding and should have the lowest memory usage.


## Configuration

Default values can be overridden via command line options (`-d`, `-t`, `-f`, `-b`, `-o`):

```java
public static final double DEFAULT_DURATION = 3.0;      // seconds per image
public static final double DEFAULT_TRANSITION = 0.75;    // dissolve transition duration
public static final int DEFAULT_FRAME_RATE = 30;         // frames per second
```

The output filename is automatically generated with a timestamp (`YYYYMMDD'T'HHmmss-output.mp4`) unless overridden with `-o`.

## Example Output

```
Parameters:
  Duration:   5.00 seconds
  Transition: 2.50 seconds
  Frame rate: 30 fps
  Batch size: 20

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
java -jar target/jslideshow-1.3.4-jar-with-dependencies.jar /path/to/images

# Run with custom dissolve transitions
java -jar target/jslideshow-1.3.4-jar-with-dependencies.jar -d 5.0 -t 2.5 /path/to/images

# Run without transitions (hard cuts)
java -jar target/jslideshow-1.3.4-jar-with-dependencies.jar -d 5.0 -t 0 /path/to/images

# Specify output filename
java -jar target/jslideshow-1.3.4-jar-with-dependencies.jar -o vacation.mp4 /path/to/images

# Limit parallel encoding to 4 threads
java -jar target/jslideshow-1.3.4-jar-with-dependencies.jar -b 4 /path/to/images
```

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

### Memory Management

- **Images:** Loaded lazily per batch, evicted when no longer needed by future segments. At most ~batchSize images in memory.
- **Encoded segments:** Inserted into `ConcurrentSkipListMap` by workers, removed by muxer immediately after writing. At steady state: ~batchSize segments in flight.
- **Encoder buffers:** 5MB worst-case buffer per frame, compacted to ~150KB immediately after encoding.

## Performance

**For 5 images at 1620x1080 pixels, 5s duration, 2.5s transitions @ 30fps:**
- Processing time: ~14 seconds
- Total frames: 825 (5×75 hold + 4×75 dissolve + 75 fade-in + 75 fade-out)
- Output size: ~30MB (H.264 in MP4 container)

## Troubleshooting

### Out of Memory Error
Increase heap size for large image sets:
```bash
java -Xmx8g -jar target/jslideshow-1.3.4-jar-with-dependencies.jar /path/to/images
```

Or, reduce the number of images processed in parallel using the ``--batchsize`` command line option.

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
- **picocli 4.7.6** - CLI argument parsing with getopt-style options

### Output Format
- Container: MP4
- Video codec: H.264
- Color space: YUV420
- Frame rate: 30 fps (configurable)

## License

- jslideshow: See the [LICENSE](./LICENSE) file. 
- JCodec is licensed under the FreeBSD License.
- picocli is licensed under the Apache 2 License.

## Author

krystalmonolith

## Example: Sequential/Parallel Test Output
### Parallel Speed Improvement: 8.99x
- Parallel processing time using 28 CPUs: _**83.56 seconds**_
- Sequential processing time using one CPU: **751.42 seconds**
  - Sequential functionality accomplished by using ``--batchsize 1`` command line option.
- The output .mp4 files were identical byte for byte.

### Test Logs

<details>
<summary>Sequential Test 20260206-1: Only 1 CPU Used.</summary>

This demonstrates sequential execution by showing a monotonic segment numbering sequence.
```text
[INFO] Scanning for projects...
[INFO] 
[INFO] -------------------< com.krystalmonolith:jslideshow >-------------------
[INFO] Building JSlideshow 1.3.4
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- exec:3.5.0:java (default-cli) @ jslideshow ---
Parameters:
  Duration:   3.00 seconds
  Transition: 0.75 seconds
  Frame rate: 30 fps
  Batch size: 1

Processing directory: images\images1080
Found 144 images
Duration: 3.00 seconds per image (67 hold frames @ 30 fps)
Transition: 0.75 seconds (22 frames)
Output file: snailtest.mp4

Encoding 144 images into 289 segments (12838 total frames) @ 30 fps
Batch size: 1 (parallel threads)

  Loaded: DSC_7141.JPG (1620x1080) 
  Encoded segment 1/289 (FADE_IN, 22 frames) 
  Muxed segment 1/289 
  Encoded segment 2/289 (HOLD, 67 frames) 
  Muxed segment 2/289 
  Loaded: DSC_7145.JPG (1620x1080) 
  Encoded segment 3/289 (DISSOLVE, 22 frames) 
  Muxed segment 3/289 
  Encoded segment 4/289 (HOLD, 67 frames) 
  Muxed segment 4/289 
  Loaded: DSC_7151.JPG (1620x1080) 
  Encoded segment 5/289 (DISSOLVE, 22 frames) 
  Muxed segment 5/289 
  Encoded segment 6/289 (HOLD, 67 frames) 
  Muxed segment 6/289 
  Loaded: DSC_7155.JPG (1620x1080) 
  Encoded segment 7/289 (DISSOLVE, 22 frames) 
  Muxed segment 7/289 
  Encoded segment 8/289 (HOLD, 67 frames) 
  Muxed segment 8/289 
  Loaded: DSC_7161.JPG (1620x1080) 
  Encoded segment 9/289 (DISSOLVE, 22 frames) 
  Muxed segment 9/289 
  Encoded segment 10/289 (HOLD, 67 frames) 
  Muxed segment 10/289 
  Loaded: DSC_7164.JPG (1620x1080) 
  Encoded segment 11/289 (DISSOLVE, 22 frames) 
  Muxed segment 11/289 
  Encoded segment 12/289 (HOLD, 67 frames) 
  Muxed segment 12/289 
  Loaded: DSC_7181.JPG (1620x1080) 
  Encoded segment 13/289 (DISSOLVE, 22 frames) 
  Muxed segment 13/289 
  Encoded segment 14/289 (HOLD, 67 frames) 
  Muxed segment 14/289 
  Loaded: DSC_7182.JPG (1620x1080) 
  Encoded segment 15/289 (DISSOLVE, 22 frames) 
  Muxed segment 15/289 
  Encoded segment 16/289 (HOLD, 67 frames) 
  Muxed segment 16/289 
  Loaded: DSC_7184.JPG (1620x1080) 
  Encoded segment 17/289 (DISSOLVE, 22 frames) 
  Muxed segment 17/289 
  Encoded segment 18/289 (HOLD, 67 frames) 
  Muxed segment 18/289 
  Loaded: DSC_7186.JPG (1620x1080) 
  Encoded segment 19/289 (DISSOLVE, 22 frames) 
  Muxed segment 19/289 
  Encoded segment 20/289 (HOLD, 67 frames) 
  Muxed segment 20/289 
  Loaded: DSC_7194.JPG (1620x1080) 
  Encoded segment 21/289 (DISSOLVE, 22 frames) 
  Muxed segment 21/289 
  Encoded segment 22/289 (HOLD, 67 frames) 
  Muxed segment 22/289 
  Loaded: DSC_7200.JPG (1620x1080) 
  Encoded segment 23/289 (DISSOLVE, 22 frames) 
  Muxed segment 23/289 
  Encoded segment 24/289 (HOLD, 67 frames) 
  Muxed segment 24/289 
  Loaded: DSC_7201.JPG (1620x1080) 
  Encoded segment 25/289 (DISSOLVE, 22 frames) 
  Muxed segment 25/289 
  Encoded segment 26/289 (HOLD, 67 frames) 
  Muxed segment 26/289 
  Loaded: DSC_7202.JPG (1620x1080) 
  Encoded segment 27/289 (DISSOLVE, 22 frames) 
  Muxed segment 27/289 
  Encoded segment 28/289 (HOLD, 67 frames) 
  Muxed segment 28/289 
  Loaded: DSC_7203.JPG (1620x1080) 
  Encoded segment 29/289 (DISSOLVE, 22 frames) 
  Muxed segment 29/289 
  Encoded segment 30/289 (HOLD, 67 frames) 
  Muxed segment 30/289 
  Loaded: DSC_7204.JPG (1620x1080) 
  Encoded segment 31/289 (DISSOLVE, 22 frames) 
  Muxed segment 31/289 
  Encoded segment 32/289 (HOLD, 67 frames) 
  Muxed segment 32/289 
  Loaded: DSC_7205.JPG (1620x1080) 
  Encoded segment 33/289 (DISSOLVE, 22 frames) 
  Muxed segment 33/289 
  Encoded segment 34/289 (HOLD, 67 frames) 
  Muxed segment 34/289 
  Loaded: DSC_7210.JPG (1620x1080) 
  Encoded segment 35/289 (DISSOLVE, 22 frames) 
  Muxed segment 35/289 
  Encoded segment 36/289 (HOLD, 67 frames) 
  Muxed segment 36/289 
  Loaded: DSC_7211.JPG (1620x1080) 
  Encoded segment 37/289 (DISSOLVE, 22 frames) 
  Muxed segment 37/289 
  Encoded segment 38/289 (HOLD, 67 frames) 
  Muxed segment 38/289 
  Loaded: DSC_7212.JPG (1620x1080) 
  Encoded segment 39/289 (DISSOLVE, 22 frames) 
  Muxed segment 39/289 
  Encoded segment 40/289 (HOLD, 67 frames) 
  Muxed segment 40/289 
  Loaded: DSC_7214.JPG (1620x1080) 
  Encoded segment 41/289 (DISSOLVE, 22 frames) 
  Muxed segment 41/289 
  Encoded segment 42/289 (HOLD, 67 frames) 
  Muxed segment 42/289 
  Loaded: DSC_7216.JPG (1620x1080) 
  Encoded segment 43/289 (DISSOLVE, 22 frames) 
  Muxed segment 43/289 
  Encoded segment 44/289 (HOLD, 67 frames) 
  Muxed segment 44/289 
  Loaded: DSC_7217.JPG (1620x1080) 
  Encoded segment 45/289 (DISSOLVE, 22 frames) 
  Muxed segment 45/289 
  Encoded segment 46/289 (HOLD, 67 frames) 
  Muxed segment 46/289 
  Loaded: DSC_7219.JPG (1620x1080) 
  Encoded segment 47/289 (DISSOLVE, 22 frames) 
  Muxed segment 47/289 
  Encoded segment 48/289 (HOLD, 67 frames) 
  Muxed segment 48/289 
  Loaded: DSC_7220.JPG (1620x1080) 
  Encoded segment 49/289 (DISSOLVE, 22 frames) 
  Muxed segment 49/289 
  Encoded segment 50/289 (HOLD, 67 frames) 
  Muxed segment 50/289 
  Loaded: DSC_7229.JPG (1620x1080) 
  Encoded segment 51/289 (DISSOLVE, 22 frames) 
  Muxed segment 51/289 
  Encoded segment 52/289 (HOLD, 67 frames) 
  Muxed segment 52/289 
  Loaded: DSC_7230.JPG (1620x1080) 
  Encoded segment 53/289 (DISSOLVE, 22 frames) 
  Muxed segment 53/289 
  Encoded segment 54/289 (HOLD, 67 frames) 
  Muxed segment 54/289 
  Loaded: DSC_7258.JPG (1620x1080) 
  Encoded segment 55/289 (DISSOLVE, 22 frames) 
  Muxed segment 55/289 
  Encoded segment 56/289 (HOLD, 67 frames) 
  Muxed segment 56/289 
  Loaded: DSC_7259.JPG (1620x1080) 
  Encoded segment 57/289 (DISSOLVE, 22 frames) 
  Muxed segment 57/289 
  Encoded segment 58/289 (HOLD, 67 frames) 
  Muxed segment 58/289 
  Loaded: DSC_7265.JPG (1620x1080) 
  Encoded segment 59/289 (DISSOLVE, 22 frames) 
  Muxed segment 59/289 
  Encoded segment 60/289 (HOLD, 67 frames) 
  Muxed segment 60/289 
  Loaded: DSC_7270.JPG (1620x1080) 
  Encoded segment 61/289 (DISSOLVE, 22 frames) 
  Muxed segment 61/289 
  Encoded segment 62/289 (HOLD, 67 frames) 
  Muxed segment 62/289 
  Loaded: DSC_7271.JPG (1620x1080) 
  Encoded segment 63/289 (DISSOLVE, 22 frames) 
  Muxed segment 63/289 
  Encoded segment 64/289 (HOLD, 67 frames) 
  Muxed segment 64/289 
  Loaded: DSC_7272.JPG (1620x1080) 
  Encoded segment 65/289 (DISSOLVE, 22 frames) 
  Muxed segment 65/289 
  Encoded segment 66/289 (HOLD, 67 frames) 
  Muxed segment 66/289 
  Loaded: DSC_7276.JPG (1620x1080) 
  Encoded segment 67/289 (DISSOLVE, 22 frames) 
  Muxed segment 67/289 
  Encoded segment 68/289 (HOLD, 67 frames) 
  Muxed segment 68/289 
  Loaded: DSC_7277.JPG (1620x1080) 
  Encoded segment 69/289 (DISSOLVE, 22 frames) 
  Muxed segment 69/289 
  Encoded segment 70/289 (HOLD, 67 frames) 
  Muxed segment 70/289 
  Loaded: DSC_7280.JPG (1620x1080) 
  Encoded segment 71/289 (DISSOLVE, 22 frames) 
  Muxed segment 71/289 
  Encoded segment 72/289 (HOLD, 67 frames) 
  Muxed segment 72/289 
  Loaded: DSC_7285.JPG (1620x1080) 
  Encoded segment 73/289 (DISSOLVE, 22 frames) 
  Muxed segment 73/289 
  Encoded segment 74/289 (HOLD, 67 frames) 
  Muxed segment 74/289 
  Loaded: DSC_7288.JPG (1620x1080) 
  Encoded segment 75/289 (DISSOLVE, 22 frames) 
  Muxed segment 75/289 
  Encoded segment 76/289 (HOLD, 67 frames) 
  Muxed segment 76/289 
  Loaded: DSC_7289.JPG (1620x1080) 
  Encoded segment 77/289 (DISSOLVE, 22 frames) 
  Muxed segment 77/289 
  Encoded segment 78/289 (HOLD, 67 frames) 
  Muxed segment 78/289 
  Loaded: DSC_7291.JPG (1620x1080) 
  Encoded segment 79/289 (DISSOLVE, 22 frames) 
  Muxed segment 79/289 
  Encoded segment 80/289 (HOLD, 67 frames) 
  Muxed segment 80/289 
  Loaded: DSC_7304.JPG (1620x1080) 
  Encoded segment 81/289 (DISSOLVE, 22 frames) 
  Muxed segment 81/289 
  Encoded segment 82/289 (HOLD, 67 frames) 
  Muxed segment 82/289 
  Loaded: DSC_7312.JPG (1620x1080) 
  Encoded segment 83/289 (DISSOLVE, 22 frames) 
  Muxed segment 83/289 
  Encoded segment 84/289 (HOLD, 67 frames) 
  Muxed segment 84/289 
  Loaded: DSC_7319.JPG (1620x1080) 
  Encoded segment 85/289 (DISSOLVE, 22 frames) 
  Muxed segment 85/289 
  Encoded segment 86/289 (HOLD, 67 frames) 
  Muxed segment 86/289 
  Loaded: DSC_7323.JPG (1620x1080) 
  Encoded segment 87/289 (DISSOLVE, 22 frames) 
  Muxed segment 87/289 
  Encoded segment 88/289 (HOLD, 67 frames) 
  Muxed segment 88/289 
  Loaded: DSC_7326.JPG (1620x1080) 
  Encoded segment 89/289 (DISSOLVE, 22 frames) 
  Muxed segment 89/289 
  Encoded segment 90/289 (HOLD, 67 frames) 
  Muxed segment 90/289 
  Loaded: DSC_7327.JPG (1620x1080) 
  Encoded segment 91/289 (DISSOLVE, 22 frames) 
  Muxed segment 91/289 
  Encoded segment 92/289 (HOLD, 67 frames) 
  Muxed segment 92/289 
  Loaded: DSC_7353.JPG (1620x1080) 
  Encoded segment 93/289 (DISSOLVE, 22 frames) 
  Muxed segment 93/289 
  Encoded segment 94/289 (HOLD, 67 frames) 
  Muxed segment 94/289 
  Loaded: DSC_7375.JPG (1620x1080) 
  Encoded segment 95/289 (DISSOLVE, 22 frames) 
  Muxed segment 95/289 
  Encoded segment 96/289 (HOLD, 67 frames) 
  Muxed segment 96/289 
  Loaded: DSC_7378.JPG (1620x1080) 
  Encoded segment 97/289 (DISSOLVE, 22 frames) 
  Muxed segment 97/289 
  Encoded segment 98/289 (HOLD, 67 frames) 
  Muxed segment 98/289 
  Loaded: DSC_7382.JPG (1620x1080) 
  Encoded segment 99/289 (DISSOLVE, 22 frames) 
  Muxed segment 99/289 
  Encoded segment 100/289 (HOLD, 67 frames) 
  Muxed segment 100/289 
  Loaded: DSC_7397.JPG (1620x1080) 
  Encoded segment 101/289 (DISSOLVE, 22 frames) 
  Muxed segment 101/289 
  Encoded segment 102/289 (HOLD, 67 frames) 
  Muxed segment 102/289 
  Loaded: DSC_7415.JPG (1620x1080) 
  Encoded segment 103/289 (DISSOLVE, 22 frames) 
  Muxed segment 103/289 
  Encoded segment 104/289 (HOLD, 67 frames) 
  Muxed segment 104/289 
  Loaded: DSC_7419.JPG (1620x1080) 
  Encoded segment 105/289 (DISSOLVE, 22 frames) 
  Muxed segment 105/289 
  Encoded segment 106/289 (HOLD, 67 frames) 
  Muxed segment 106/289 
  Loaded: DSC_7420.JPG (1620x1080) 
  Encoded segment 107/289 (DISSOLVE, 22 frames) 
  Muxed segment 107/289 
  Encoded segment 108/289 (HOLD, 67 frames) 
  Muxed segment 108/289 
  Loaded: DSC_7421.JPG (1620x1080) 
  Encoded segment 109/289 (DISSOLVE, 22 frames) 
  Muxed segment 109/289 
  Encoded segment 110/289 (HOLD, 67 frames) 
  Muxed segment 110/289 
  Loaded: DSC_7434.JPG (1620x1080) 
  Encoded segment 111/289 (DISSOLVE, 22 frames) 
  Muxed segment 111/289 
  Encoded segment 112/289 (HOLD, 67 frames) 
  Muxed segment 112/289 
  Loaded: DSC_7441.JPG (1620x1080) 
  Encoded segment 113/289 (DISSOLVE, 22 frames) 
  Muxed segment 113/289 
  Encoded segment 114/289 (HOLD, 67 frames) 
  Muxed segment 114/289 
  Loaded: DSC_7443.JPG (1620x1080) 
  Encoded segment 115/289 (DISSOLVE, 22 frames) 
  Muxed segment 115/289 
  Encoded segment 116/289 (HOLD, 67 frames) 
  Muxed segment 116/289 
  Loaded: DSC_7446.JPG (1620x1080) 
  Encoded segment 117/289 (DISSOLVE, 22 frames) 
  Muxed segment 117/289 
  Encoded segment 118/289 (HOLD, 67 frames) 
  Muxed segment 118/289 
  Loaded: DSC_7447.JPG (1620x1080) 
  Encoded segment 119/289 (DISSOLVE, 22 frames) 
  Muxed segment 119/289 
  Encoded segment 120/289 (HOLD, 67 frames) 
  Muxed segment 120/289 
  Loaded: DSC_7465.JPG (1620x1080) 
  Encoded segment 121/289 (DISSOLVE, 22 frames) 
  Muxed segment 121/289 
  Encoded segment 122/289 (HOLD, 67 frames) 
  Muxed segment 122/289 
  Loaded: DSC_7467.JPG (1620x1080) 
  Encoded segment 123/289 (DISSOLVE, 22 frames) 
  Muxed segment 123/289 
  Encoded segment 124/289 (HOLD, 67 frames) 
  Muxed segment 124/289 
  Loaded: DSC_7472.JPG (1620x1080) 
  Encoded segment 125/289 (DISSOLVE, 22 frames) 
  Muxed segment 125/289 
  Encoded segment 126/289 (HOLD, 67 frames) 
  Muxed segment 126/289 
  Loaded: DSC_7473.JPG (1620x1080) 
  Encoded segment 127/289 (DISSOLVE, 22 frames) 
  Muxed segment 127/289 
  Encoded segment 128/289 (HOLD, 67 frames) 
  Muxed segment 128/289 
  Loaded: DSC_7474.JPG (1620x1080) 
  Encoded segment 129/289 (DISSOLVE, 22 frames) 
  Muxed segment 129/289 
  Encoded segment 130/289 (HOLD, 67 frames) 
  Muxed segment 130/289 
  Loaded: DSC_7505.JPG (1620x1080) 
  Encoded segment 131/289 (DISSOLVE, 22 frames) 
  Muxed segment 131/289 
  Encoded segment 132/289 (HOLD, 67 frames) 
  Muxed segment 132/289 
  Loaded: DSC_7512.JPG (1620x1080) 
  Encoded segment 133/289 (DISSOLVE, 22 frames) 
  Muxed segment 133/289 
  Encoded segment 134/289 (HOLD, 67 frames) 
  Muxed segment 134/289 
  Loaded: DSC_7519.JPG (1620x1080) 
  Encoded segment 135/289 (DISSOLVE, 22 frames) 
  Muxed segment 135/289 
  Encoded segment 136/289 (HOLD, 67 frames) 
  Muxed segment 136/289 
  Loaded: DSC_7523.JPG (1620x1080) 
  Encoded segment 137/289 (DISSOLVE, 22 frames) 
  Muxed segment 137/289 
  Encoded segment 138/289 (HOLD, 67 frames) 
  Muxed segment 138/289 
  Loaded: DSC_7524.JPG (1620x1080) 
  Encoded segment 139/289 (DISSOLVE, 22 frames) 
  Muxed segment 139/289 
  Encoded segment 140/289 (HOLD, 67 frames) 
  Muxed segment 140/289 
  Loaded: DSC_7525.JPG (1620x1080) 
  Encoded segment 141/289 (DISSOLVE, 22 frames) 
  Muxed segment 141/289 
  Encoded segment 142/289 (HOLD, 67 frames) 
  Muxed segment 142/289 
  Loaded: DSC_7526.JPG (1620x1080) 
  Encoded segment 143/289 (DISSOLVE, 22 frames) 
  Muxed segment 143/289 
  Encoded segment 144/289 (HOLD, 67 frames) 
  Muxed segment 144/289 
  Loaded: DSC_7527.JPG (1620x1080) 
  Encoded segment 145/289 (DISSOLVE, 22 frames) 
  Muxed segment 145/289 
  Encoded segment 146/289 (HOLD, 67 frames) 
  Muxed segment 146/289 
  Loaded: DSC_7528.JPG (1620x1080) 
  Encoded segment 147/289 (DISSOLVE, 22 frames) 
  Muxed segment 147/289 
  Encoded segment 148/289 (HOLD, 67 frames) 
  Muxed segment 148/289 
  Loaded: DSC_7529.JPG (1620x1080) 
  Encoded segment 149/289 (DISSOLVE, 22 frames) 
  Muxed segment 149/289 
  Encoded segment 150/289 (HOLD, 67 frames) 
  Muxed segment 150/289 
  Loaded: DSC_7535.JPG (1620x1080) 
  Encoded segment 151/289 (DISSOLVE, 22 frames) 
  Muxed segment 151/289 
  Encoded segment 152/289 (HOLD, 67 frames) 
  Muxed segment 152/289 
  Loaded: DSC_7536.JPG (1620x1080) 
  Encoded segment 153/289 (DISSOLVE, 22 frames) 
  Muxed segment 153/289 
  Encoded segment 154/289 (HOLD, 67 frames) 
  Muxed segment 154/289 
  Loaded: DSC_7537.JPG (1620x1080) 
  Encoded segment 155/289 (DISSOLVE, 22 frames) 
  Muxed segment 155/289 
  Encoded segment 156/289 (HOLD, 67 frames) 
  Muxed segment 156/289 
  Loaded: DSC_7538.JPG (1620x1080) 
  Encoded segment 157/289 (DISSOLVE, 22 frames) 
  Muxed segment 157/289 
  Encoded segment 158/289 (HOLD, 67 frames) 
  Muxed segment 158/289 
  Loaded: DSC_7539.JPG (1620x1080) 
  Encoded segment 159/289 (DISSOLVE, 22 frames) 
  Muxed segment 159/289 
  Encoded segment 160/289 (HOLD, 67 frames) 
  Muxed segment 160/289 
  Loaded: DSC_7541.JPG (1620x1080) 
  Encoded segment 161/289 (DISSOLVE, 22 frames) 
  Muxed segment 161/289 
  Encoded segment 162/289 (HOLD, 67 frames) 
  Muxed segment 162/289 
  Loaded: DSC_7543.JPG (1620x1080) 
  Encoded segment 163/289 (DISSOLVE, 22 frames) 
  Muxed segment 163/289 
  Encoded segment 164/289 (HOLD, 67 frames) 
  Muxed segment 164/289 
  Loaded: DSC_7547.JPG (1620x1080) 
  Encoded segment 165/289 (DISSOLVE, 22 frames) 
  Muxed segment 165/289 
  Encoded segment 166/289 (HOLD, 67 frames) 
  Muxed segment 166/289 
  Loaded: DSC_7548.JPG (1620x1080) 
  Encoded segment 167/289 (DISSOLVE, 22 frames) 
  Muxed segment 167/289 
  Encoded segment 168/289 (HOLD, 67 frames) 
  Muxed segment 168/289 
  Loaded: DSC_7549.JPG (1620x1080) 
  Encoded segment 169/289 (DISSOLVE, 22 frames) 
  Muxed segment 169/289 
  Encoded segment 170/289 (HOLD, 67 frames) 
  Muxed segment 170/289 
  Loaded: DSC_7550.JPG (1620x1080) 
  Encoded segment 171/289 (DISSOLVE, 22 frames) 
  Muxed segment 171/289 
  Encoded segment 172/289 (HOLD, 67 frames) 
  Muxed segment 172/289 
  Loaded: DSC_7551.JPG (1620x1080) 
  Encoded segment 173/289 (DISSOLVE, 22 frames) 
  Muxed segment 173/289 
  Encoded segment 174/289 (HOLD, 67 frames) 
  Muxed segment 174/289 
  Loaded: DSC_7552.JPG (1620x1080) 
  Encoded segment 175/289 (DISSOLVE, 22 frames) 
  Muxed segment 175/289 
  Encoded segment 176/289 (HOLD, 67 frames) 
  Muxed segment 176/289 
  Loaded: DSC_7553.JPG (1620x1080) 
  Encoded segment 177/289 (DISSOLVE, 22 frames) 
  Muxed segment 177/289 
  Encoded segment 178/289 (HOLD, 67 frames) 
  Muxed segment 178/289 
  Loaded: DSC_7554.JPG (1620x1080) 
  Encoded segment 179/289 (DISSOLVE, 22 frames) 
  Muxed segment 179/289 
  Encoded segment 180/289 (HOLD, 67 frames) 
  Muxed segment 180/289 
  Loaded: DSC_7555.JPG (1620x1080) 
  Encoded segment 181/289 (DISSOLVE, 22 frames) 
  Muxed segment 181/289 
  Encoded segment 182/289 (HOLD, 67 frames) 
  Muxed segment 182/289 
  Loaded: DSC_7556.JPG (1620x1080) 
  Encoded segment 183/289 (DISSOLVE, 22 frames) 
  Muxed segment 183/289 
  Encoded segment 184/289 (HOLD, 67 frames) 
  Muxed segment 184/289 
  Loaded: DSC_7557.JPG (1620x1080) 
  Encoded segment 185/289 (DISSOLVE, 22 frames) 
  Muxed segment 185/289 
  Encoded segment 186/289 (HOLD, 67 frames) 
  Muxed segment 186/289 
  Loaded: DSC_7558.JPG (1620x1080) 
  Encoded segment 187/289 (DISSOLVE, 22 frames) 
  Muxed segment 187/289 
  Encoded segment 188/289 (HOLD, 67 frames) 
  Muxed segment 188/289 
  Loaded: DSC_7559.JPG (1620x1080) 
  Encoded segment 189/289 (DISSOLVE, 22 frames) 
  Muxed segment 189/289 
  Encoded segment 190/289 (HOLD, 67 frames) 
  Muxed segment 190/289 
  Loaded: DSC_7560.JPG (1620x1080) 
  Encoded segment 191/289 (DISSOLVE, 22 frames) 
  Muxed segment 191/289 
  Encoded segment 192/289 (HOLD, 67 frames) 
  Muxed segment 192/289 
  Loaded: DSC_7561.JPG (1620x1080) 
  Encoded segment 193/289 (DISSOLVE, 22 frames) 
  Muxed segment 193/289 
  Encoded segment 194/289 (HOLD, 67 frames) 
  Muxed segment 194/289 
  Loaded: DSC_7562.JPG (1620x1080) 
  Encoded segment 195/289 (DISSOLVE, 22 frames) 
  Muxed segment 195/289 
  Encoded segment 196/289 (HOLD, 67 frames) 
  Muxed segment 196/289 
  Loaded: DSC_7563.JPG (1620x1080) 
  Encoded segment 197/289 (DISSOLVE, 22 frames) 
  Muxed segment 197/289 
  Encoded segment 198/289 (HOLD, 67 frames) 
  Muxed segment 198/289 
  Loaded: DSC_7564.JPG (1620x1080) 
  Encoded segment 199/289 (DISSOLVE, 22 frames) 
  Muxed segment 199/289 
  Encoded segment 200/289 (HOLD, 67 frames) 
  Muxed segment 200/289 
  Loaded: DSC_7565.JPG (1620x1080) 
  Encoded segment 201/289 (DISSOLVE, 22 frames) 
  Muxed segment 201/289 
  Encoded segment 202/289 (HOLD, 67 frames) 
  Muxed segment 202/289 
  Loaded: DSC_7566.JPG (1620x1080) 
  Encoded segment 203/289 (DISSOLVE, 22 frames) 
  Muxed segment 203/289 
  Encoded segment 204/289 (HOLD, 67 frames) 
  Muxed segment 204/289 
  Loaded: DSC_7567.JPG (1620x1080) 
  Encoded segment 205/289 (DISSOLVE, 22 frames) 
  Muxed segment 205/289 
  Encoded segment 206/289 (HOLD, 67 frames) 
  Muxed segment 206/289 
  Loaded: DSC_7568.JPG (1620x1080) 
  Encoded segment 207/289 (DISSOLVE, 22 frames) 
  Muxed segment 207/289 
  Encoded segment 208/289 (HOLD, 67 frames) 
  Muxed segment 208/289 
  Loaded: DSC_7569.JPG (1620x1080) 
  Encoded segment 209/289 (DISSOLVE, 22 frames) 
  Muxed segment 209/289 
  Encoded segment 210/289 (HOLD, 67 frames) 
  Muxed segment 210/289 
  Loaded: DSC_7570.JPG (1620x1080) 
  Encoded segment 211/289 (DISSOLVE, 22 frames) 
  Muxed segment 211/289 
  Encoded segment 212/289 (HOLD, 67 frames) 
  Muxed segment 212/289 
  Loaded: DSC_7571.JPG (1620x1080) 
  Encoded segment 213/289 (DISSOLVE, 22 frames) 
  Muxed segment 213/289 
  Encoded segment 214/289 (HOLD, 67 frames) 
  Muxed segment 214/289 
  Loaded: DSC_7572.JPG (1620x1080) 
  Encoded segment 215/289 (DISSOLVE, 22 frames) 
  Muxed segment 215/289 
  Encoded segment 216/289 (HOLD, 67 frames) 
  Muxed segment 216/289 
  Loaded: DSC_7573.JPG (1620x1080) 
  Encoded segment 217/289 (DISSOLVE, 22 frames) 
  Muxed segment 217/289 
  Encoded segment 218/289 (HOLD, 67 frames) 
  Muxed segment 218/289 
  Loaded: DSC_7574.JPG (1620x1080) 
  Encoded segment 219/289 (DISSOLVE, 22 frames) 
  Muxed segment 219/289 
  Encoded segment 220/289 (HOLD, 67 frames) 
  Muxed segment 220/289 
  Loaded: DSC_7575.JPG (1620x1080) 
  Encoded segment 221/289 (DISSOLVE, 22 frames) 
  Muxed segment 221/289 
  Encoded segment 222/289 (HOLD, 67 frames) 
  Muxed segment 222/289 
  Loaded: DSC_7576.JPG (1620x1080) 
  Encoded segment 223/289 (DISSOLVE, 22 frames) 
  Muxed segment 223/289 
  Encoded segment 224/289 (HOLD, 67 frames) 
  Muxed segment 224/289 
  Loaded: DSC_7577.JPG (1620x1080) 
  Encoded segment 225/289 (DISSOLVE, 22 frames) 
  Muxed segment 225/289 
  Encoded segment 226/289 (HOLD, 67 frames) 
  Muxed segment 226/289 
  Loaded: DSC_7578.JPG (1620x1080) 
  Encoded segment 227/289 (DISSOLVE, 22 frames) 
  Muxed segment 227/289 
  Encoded segment 228/289 (HOLD, 67 frames) 
  Muxed segment 228/289 
  Loaded: DSC_7579.JPG (1620x1080) 
  Encoded segment 229/289 (DISSOLVE, 22 frames) 
  Muxed segment 229/289 
  Encoded segment 230/289 (HOLD, 67 frames) 
  Muxed segment 230/289 
  Loaded: DSC_7580.JPG (1620x1080) 
  Encoded segment 231/289 (DISSOLVE, 22 frames) 
  Muxed segment 231/289 
  Encoded segment 232/289 (HOLD, 67 frames) 
  Muxed segment 232/289 
  Loaded: DSC_7581.JPG (1620x1080) 
  Encoded segment 233/289 (DISSOLVE, 22 frames) 
  Muxed segment 233/289 
  Encoded segment 234/289 (HOLD, 67 frames) 
  Muxed segment 234/289 
  Loaded: DSC_7582.JPG (1620x1080) 
  Encoded segment 235/289 (DISSOLVE, 22 frames) 
  Muxed segment 235/289 
  Encoded segment 236/289 (HOLD, 67 frames) 
  Muxed segment 236/289 
  Loaded: DSC_7583.JPG (1620x1080) 
  Encoded segment 237/289 (DISSOLVE, 22 frames) 
  Muxed segment 237/289 
  Encoded segment 238/289 (HOLD, 67 frames) 
  Muxed segment 238/289 
  Loaded: DSC_7584.JPG (1620x1080) 
  Encoded segment 239/289 (DISSOLVE, 22 frames) 
  Muxed segment 239/289 
  Encoded segment 240/289 (HOLD, 67 frames) 
  Muxed segment 240/289 
  Loaded: DSC_7585.JPG (1620x1080) 
  Encoded segment 241/289 (DISSOLVE, 22 frames) 
  Muxed segment 241/289 
  Encoded segment 242/289 (HOLD, 67 frames) 
  Muxed segment 242/289 
  Loaded: DSC_7586.JPG (1620x1080) 
  Encoded segment 243/289 (DISSOLVE, 22 frames) 
  Muxed segment 243/289 
  Encoded segment 244/289 (HOLD, 67 frames) 
  Muxed segment 244/289 
  Loaded: DSC_7587.JPG (1620x1080) 
  Encoded segment 245/289 (DISSOLVE, 22 frames) 
  Muxed segment 245/289 
  Encoded segment 246/289 (HOLD, 67 frames) 
  Muxed segment 246/289 
  Loaded: DSC_7588.JPG (1620x1080) 
  Encoded segment 247/289 (DISSOLVE, 22 frames) 
  Muxed segment 247/289 
  Encoded segment 248/289 (HOLD, 67 frames) 
  Muxed segment 248/289 
  Loaded: DSC_7589.JPG (1620x1080) 
  Encoded segment 249/289 (DISSOLVE, 22 frames) 
  Muxed segment 249/289 
  Encoded segment 250/289 (HOLD, 67 frames) 
  Muxed segment 250/289 
  Loaded: DSC_7590.JPG (1620x1080) 
  Encoded segment 251/289 (DISSOLVE, 22 frames) 
  Muxed segment 251/289 
  Encoded segment 252/289 (HOLD, 67 frames) 
  Muxed segment 252/289 
  Loaded: DSC_7591.JPG (1620x1080) 
  Encoded segment 253/289 (DISSOLVE, 22 frames) 
  Muxed segment 253/289 
  Encoded segment 254/289 (HOLD, 67 frames) 
  Muxed segment 254/289 
  Loaded: DSC_7592.JPG (1620x1080) 
  Encoded segment 255/289 (DISSOLVE, 22 frames) 
  Muxed segment 255/289 
  Encoded segment 256/289 (HOLD, 67 frames) 
  Muxed segment 256/289 
  Loaded: DSC_7593.JPG (1620x1080) 
  Encoded segment 257/289 (DISSOLVE, 22 frames) 
  Muxed segment 257/289 
  Encoded segment 258/289 (HOLD, 67 frames) 
  Muxed segment 258/289 
  Loaded: DSC_7594.JPG (1620x1080) 
  Encoded segment 259/289 (DISSOLVE, 22 frames) 
  Muxed segment 259/289 
  Encoded segment 260/289 (HOLD, 67 frames) 
  Muxed segment 260/289 
  Loaded: DSC_7595.JPG (1620x1080) 
  Encoded segment 261/289 (DISSOLVE, 22 frames) 
  Muxed segment 261/289 
  Encoded segment 262/289 (HOLD, 67 frames) 
  Muxed segment 262/289 
  Loaded: DSC_7602.JPG (1620x1080) 
  Encoded segment 263/289 (DISSOLVE, 22 frames) 
  Muxed segment 263/289 
  Encoded segment 264/289 (HOLD, 67 frames) 
  Muxed segment 264/289 
  Loaded: DSC_7603.JPG (1620x1080) 
  Encoded segment 265/289 (DISSOLVE, 22 frames) 
  Muxed segment 265/289 
  Encoded segment 266/289 (HOLD, 67 frames) 
  Muxed segment 266/289 
  Loaded: DSC_7613.JPG (1620x1080) 
  Encoded segment 267/289 (DISSOLVE, 22 frames) 
  Muxed segment 267/289 
  Encoded segment 268/289 (HOLD, 67 frames) 
  Muxed segment 268/289 
  Loaded: DSC_7617.JPG (1620x1080) 
  Encoded segment 269/289 (DISSOLVE, 22 frames) 
  Muxed segment 269/289 
  Encoded segment 270/289 (HOLD, 67 frames) 
  Muxed segment 270/289 
  Loaded: DSC_7634.JPG (1620x1080) 
  Encoded segment 271/289 (DISSOLVE, 22 frames) 
  Muxed segment 271/289 
  Encoded segment 272/289 (HOLD, 67 frames) 
  Muxed segment 272/289 
  Loaded: DSC_7645.JPG (1620x1080) 
  Encoded segment 273/289 (DISSOLVE, 22 frames) 
  Muxed segment 273/289 
  Encoded segment 274/289 (HOLD, 67 frames) 
  Muxed segment 274/289 
  Loaded: DSC_7649.JPG (1620x1080) 
  Encoded segment 275/289 (DISSOLVE, 22 frames) 
  Muxed segment 275/289 
  Encoded segment 276/289 (HOLD, 67 frames) 
  Muxed segment 276/289 
  Loaded: DSC_7655.JPG (1620x1080) 
  Encoded segment 277/289 (DISSOLVE, 22 frames) 
  Muxed segment 277/289 
  Encoded segment 278/289 (HOLD, 67 frames) 
  Muxed segment 278/289 
  Loaded: DSC_7660.JPG (1620x1080) 
  Encoded segment 279/289 (DISSOLVE, 22 frames) 
  Muxed segment 279/289 
  Encoded segment 280/289 (HOLD, 67 frames) 
  Muxed segment 280/289 
  Loaded: DSC_7666.JPG (1620x1080) 
  Encoded segment 281/289 (DISSOLVE, 22 frames) 
  Muxed segment 281/289 
  Encoded segment 282/289 (HOLD, 67 frames) 
  Muxed segment 282/289 
  Loaded: DSC_7706.JPG (1620x1080) 
  Encoded segment 283/289 (DISSOLVE, 22 frames) 
  Muxed segment 283/289 
  Encoded segment 284/289 (HOLD, 67 frames) 
  Muxed segment 284/289 
  Loaded: DSC_7709.JPG (1620x1080) 
  Encoded segment 285/289 (DISSOLVE, 22 frames) 
  Muxed segment 285/289 
  Encoded segment 286/289 (HOLD, 67 frames) 
  Muxed segment 286/289 
  Loaded: DSC_7713.JPG (1620x1080) 
  Encoded segment 287/289 (DISSOLVE, 22 frames) 
  Muxed segment 287/289 
  Encoded segment 288/289 (HOLD, 67 frames) 
  Muxed segment 288/289 
  Encoded segment 289/289 (FADE_OUT, 22 frames) 
  Muxed segment 289/289 
Wrote 12838 total frames

Success! Created snailtest.mp4
Total processing time: 751.42 seconds
```
</details>

<details>
<summary>Parallel Test 20260206-2: 28 CPUs Used</summary>

This demonstrates parallel execution by the batches of random segment encoding followed by monotonic segment mux'ing. 

```text
[INFO] Scanning for projects...
[INFO] 
[INFO] -------------------< com.krystalmonolith:jslideshow >-------------------
[INFO] Building JSlideshow 1.3.4
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- exec:3.5.0:java (default-cli) @ jslideshow ---
Parameters:
  Duration:   3.00 seconds
  Transition: 0.75 seconds
  Frame rate: 30 fps
  Batch size: 28

Processing directory: images\images1080
Found 144 images
Duration: 3.00 seconds per image (67 hold frames @ 30 fps)
Transition: 0.75 seconds (22 frames)
Output file: slowtest.mp4

Encoding 144 images into 289 segments (12838 total frames) @ 30 fps
Batch size: 28 (parallel threads)

  Loaded: DSC_7141.JPG (1620x1080) 
  Loaded: DSC_7145.JPG (1620x1080) 
  Loaded: DSC_7151.JPG (1620x1080) 
  Loaded: DSC_7155.JPG (1620x1080) 
  Loaded: DSC_7161.JPG (1620x1080) 
  Loaded: DSC_7164.JPG (1620x1080) 
  Loaded: DSC_7181.JPG (1620x1080) 
  Loaded: DSC_7182.JPG (1620x1080) 
  Loaded: DSC_7184.JPG (1620x1080) 
  Loaded: DSC_7186.JPG (1620x1080) 
  Loaded: DSC_7194.JPG (1620x1080) 
  Loaded: DSC_7200.JPG (1620x1080) 
  Loaded: DSC_7201.JPG (1620x1080) 
  Loaded: DSC_7202.JPG (1620x1080) 
  Encoded segment 25/289 (DISSOLVE, 22 frames) 
  Encoded segment 23/289 (DISSOLVE, 22 frames) 
  Encoded segment 27/289 (DISSOLVE, 22 frames) 
  Encoded segment 21/289 (DISSOLVE, 22 frames) 
  Encoded segment 11/289 (DISSOLVE, 22 frames) 
  Encoded segment 15/289 (DISSOLVE, 22 frames) 
  Encoded segment 9/289 (DISSOLVE, 22 frames) 
  Encoded segment 17/289 (DISSOLVE, 22 frames) 
  Encoded segment 3/289 (DISSOLVE, 22 frames) 
  Encoded segment 13/289 (DISSOLVE, 22 frames) 
  Encoded segment 19/289 (DISSOLVE, 22 frames) 
  Encoded segment 5/289 (DISSOLVE, 22 frames) 
  Encoded segment 7/289 (DISSOLVE, 22 frames) 
  Encoded segment 1/289 (FADE_IN, 22 frames) 
  Muxed segment 1/289 
  Encoded segment 26/289 (HOLD, 67 frames) 
  Encoded segment 28/289 (HOLD, 67 frames) 
  Encoded segment 22/289 (HOLD, 67 frames) 
  Encoded segment 24/289 (HOLD, 67 frames) 
  Encoded segment 12/289 (HOLD, 67 frames) 
  Encoded segment 20/289 (HOLD, 67 frames) 
  Encoded segment 10/289 (HOLD, 67 frames) 
  Encoded segment 2/289 (HOLD, 67 frames) 
  Muxed segment 2/289 
  Muxed segment 3/289 
  Encoded segment 4/289 (HOLD, 67 frames) 
  Muxed segment 4/289 
  Muxed segment 5/289 
  Encoded segment 8/289 (HOLD, 67 frames) 
  Encoded segment 14/289 (HOLD, 67 frames) 
  Encoded segment 6/289 (HOLD, 67 frames) 
  Muxed segment 6/289 
  Muxed segment 7/289 
  Muxed segment 8/289 
  Muxed segment 9/289 
  Muxed segment 10/289 
  Muxed segment 11/289 
  Muxed segment 12/289 
  Muxed segment 13/289 
  Muxed segment 14/289 
  Muxed segment 15/289 
  Encoded segment 16/289 (HOLD, 67 frames) 
  Muxed segment 16/289 
  Muxed segment 17/289 
  Encoded segment 18/289 (HOLD, 67 frames) 
  Muxed segment 18/289 
  Muxed segment 19/289 
  Muxed segment 20/289 
  Muxed segment 21/289 
  Muxed segment 22/289 
  Muxed segment 23/289 
  Loaded: DSC_7203.JPG (1620x1080) 
  Muxed segment 24/289 
  Muxed segment 25/289 
  Muxed segment 26/289 
  Muxed segment 27/289 
  Muxed segment 28/289 
  Loaded: DSC_7204.JPG (1620x1080) 
  Loaded: DSC_7205.JPG (1620x1080) 
  Loaded: DSC_7210.JPG (1620x1080) 
  Loaded: DSC_7211.JPG (1620x1080) 
  Loaded: DSC_7212.JPG (1620x1080) 
  Loaded: DSC_7214.JPG (1620x1080) 
  Loaded: DSC_7216.JPG (1620x1080) 
  Loaded: DSC_7217.JPG (1620x1080) 
  Loaded: DSC_7219.JPG (1620x1080) 
  Loaded: DSC_7220.JPG (1620x1080) 
  Loaded: DSC_7229.JPG (1620x1080) 
  Loaded: DSC_7230.JPG (1620x1080) 
  Loaded: DSC_7258.JPG (1620x1080) 
  Encoded segment 29/289 (DISSOLVE, 22 frames) 
  Muxed segment 29/289 
  Encoded segment 37/289 (DISSOLVE, 22 frames) 
  Encoded segment 31/289 (DISSOLVE, 22 frames) 
  Encoded segment 35/289 (DISSOLVE, 22 frames) 
  Encoded segment 33/289 (DISSOLVE, 22 frames) 
  Encoded segment 49/289 (DISSOLVE, 22 frames) 
  Encoded segment 45/289 (DISSOLVE, 22 frames) 
  Encoded segment 39/289 (DISSOLVE, 22 frames) 
  Encoded segment 43/289 (DISSOLVE, 22 frames) 
  Encoded segment 53/289 (DISSOLVE, 22 frames) 
  Encoded segment 41/289 (DISSOLVE, 22 frames) 
  Encoded segment 51/289 (DISSOLVE, 22 frames) 
  Encoded segment 55/289 (DISSOLVE, 22 frames) 
  Encoded segment 47/289 (DISSOLVE, 22 frames) 
  Encoded segment 30/289 (HOLD, 67 frames) 
  Muxed segment 30/289 
  Muxed segment 31/289 
  Encoded segment 52/289 (HOLD, 67 frames) 
  Encoded segment 50/289 (HOLD, 67 frames) 
  Encoded segment 32/289 (HOLD, 67 frames) 
  Encoded segment 46/289 (HOLD, 67 frames) 
  Encoded segment 54/289 (HOLD, 67 frames) 
  Muxed segment 32/289 
  Muxed segment 33/289 
  Encoded segment 42/289 (HOLD, 67 frames) 
  Encoded segment 34/289 (HOLD, 67 frames) 
  Muxed segment 34/289 
  Muxed segment 35/289 
  Encoded segment 36/289 (HOLD, 67 frames) 
  Muxed segment 36/289 
  Muxed segment 37/289 
  Encoded segment 56/289 (HOLD, 67 frames) 
  Encoded segment 40/289 (HOLD, 67 frames) 
  Encoded segment 38/289 (HOLD, 67 frames) 
  Muxed segment 38/289 
  Muxed segment 39/289 
  Muxed segment 40/289 
  Muxed segment 41/289 
  Muxed segment 42/289 
  Muxed segment 43/289 
  Encoded segment 48/289 (HOLD, 67 frames) 
  Encoded segment 44/289 (HOLD, 67 frames) 
  Muxed segment 44/289 
  Muxed segment 45/289 
  Muxed segment 46/289 
  Muxed segment 47/289 
  Muxed segment 48/289 
  Muxed segment 49/289 
  Loaded: DSC_7272.JPG (1620x1080) 
  Muxed segment 50/289 
  Muxed segment 51/289 
  Muxed segment 52/289 
  Muxed segment 53/289 
  Muxed segment 54/289 
  Loaded: DSC_7276.JPG (1620x1080) 
  Muxed segment 55/289 
  Muxed segment 56/289 
  Loaded: DSC_7277.JPG (1620x1080) 
  Loaded: DSC_7280.JPG (1620x1080) 
  Loaded: DSC_7285.JPG (1620x1080) 
  Loaded: DSC_7288.JPG (1620x1080) 
  Loaded: DSC_7289.JPG (1620x1080) 
  Loaded: DSC_7291.JPG (1620x1080) 
  Loaded: DSC_7304.JPG (1620x1080) 
  Loaded: DSC_7312.JPG (1620x1080) 
  Loaded: DSC_7259.JPG (1620x1080) 
  Loaded: DSC_7265.JPG (1620x1080) 
  Loaded: DSC_7270.JPG (1620x1080) 
  Loaded: DSC_7271.JPG (1620x1080) 
  Encoded segment 83/289 (DISSOLVE, 22 frames) 
  Encoded segment 79/289 (DISSOLVE, 22 frames) 
  Encoded segment 81/289 (DISSOLVE, 22 frames) 
  Encoded segment 65/289 (DISSOLVE, 22 frames) 
  Encoded segment 67/289 (DISSOLVE, 22 frames) 
  Encoded segment 63/289 (DISSOLVE, 22 frames) 
  Encoded segment 75/289 (DISSOLVE, 22 frames) 
  Encoded segment 57/289 (DISSOLVE, 22 frames) 
  Muxed segment 57/289 
  Encoded segment 71/289 (DISSOLVE, 22 frames) 
  Encoded segment 77/289 (DISSOLVE, 22 frames) 
  Encoded segment 59/289 (DISSOLVE, 22 frames) 
  Encoded segment 61/289 (DISSOLVE, 22 frames) 
  Encoded segment 69/289 (DISSOLVE, 22 frames) 
  Encoded segment 73/289 (DISSOLVE, 22 frames) 
  Encoded segment 82/289 (HOLD, 67 frames) 
  Encoded segment 84/289 (HOLD, 67 frames) 
  Encoded segment 60/289 (HOLD, 67 frames) 
  Encoded segment 64/289 (HOLD, 67 frames) 
  Encoded segment 62/289 (HOLD, 67 frames) 
  Encoded segment 66/289 (HOLD, 67 frames) 
  Encoded segment 80/289 (HOLD, 67 frames) 
  Encoded segment 58/289 (HOLD, 67 frames) 
  Muxed segment 58/289 
  Muxed segment 59/289 
  Muxed segment 60/289 
  Encoded segment 78/289 (HOLD, 67 frames) 
  Muxed segment 61/289/
  Muxed segment 62/289 
  Muxed segment 63/289 
  Muxed segment 64/289 
  Muxed segment 65/289 
  Muxed segment 66/289 
  Muxed segment 67/289 
  Encoded segment 76/289 (HOLD, 67 frames) 
  Encoded segment 68/289 (HOLD, 67 frames) 
  Muxed segment 68/289 
  Muxed segment 69/289 
  Encoded segment 70/289 (HOLD, 67 frames) 
  Muxed segment 70/289 
  Muxed segment 71/289 
  Encoded segment 72/289 (HOLD, 67 frames) 
  Muxed segment 72/289 
  Muxed segment 73/289 
  Encoded segment 74/289 (HOLD, 67 frames) 
  Muxed segment 74/289 
  Muxed segment 75/289 
  Muxed segment 76/289 
  Muxed segment 77/289 
  Muxed segment 78/289 
  Muxed segment 79/289 
  Muxed segment 80/289 
  Loaded: DSC_7319.JPG (1620x1080) 
  Muxed segment 81/289 
  Muxed segment 82/289 
  Muxed segment 83/289 
  Muxed segment 84/289 
  Loaded: DSC_7323.JPG (1620x1080) 
  Loaded: DSC_7326.JPG (1620x1080) 
  Loaded: DSC_7327.JPG (1620x1080) 
  Loaded: DSC_7353.JPG (1620x1080) 
  Loaded: DSC_7375.JPG (1620x1080) 
  Loaded: DSC_7378.JPG (1620x1080) 
  Loaded: DSC_7382.JPG (1620x1080) 
  Loaded: DSC_7397.JPG (1620x1080) 
  Loaded: DSC_7415.JPG (1620x1080) 
  Loaded: DSC_7419.JPG (1620x1080) 
  Loaded: DSC_7420.JPG (1620x1080) 
  Loaded: DSC_7421.JPG (1620x1080) 
  Loaded: DSC_7434.JPG (1620x1080) 
  Encoded segment 87/289 (DISSOLVE, 22 frames) 
  Encoded segment 93/289 (DISSOLVE, 22 frames) 
  Encoded segment 89/289 (DISSOLVE, 22 frames) 
  Encoded segment 95/289 (DISSOLVE, 22 frames) 
  Encoded segment 85/289 (DISSOLVE, 22 frames) 
  Muxed segment 85/289 
  Encoded segment 91/289 (DISSOLVE, 22 frames) 
  Encoded segment 109/289 (DISSOLVE, 22 frames) 
  Encoded segment 107/289 (DISSOLVE, 22 frames) 
  Encoded segment 101/289 (DISSOLVE, 22 frames) 
  Encoded segment 105/289 (DISSOLVE, 22 frames) 
  Encoded segment 103/289 (DISSOLVE, 22 frames) 
  Encoded segment 111/289 (DISSOLVE, 22 frames) 
  Encoded segment 97/289 (DISSOLVE, 22 frames) 
  Encoded segment 99/289 (DISSOLVE, 22 frames) 
  Encoded segment 88/289 (HOLD, 67 frames) 
  Encoded segment 86/289 (HOLD, 67 frames) 
  Muxed segment 86/289 
  Muxed segment 87/289 
  Muxed segment 88/289 
  Muxed segment 89/289 
  Encoded segment 90/289 (HOLD, 67 frames) 
  Muxed segment 90/289 
  Muxed segment 91/289 
  Encoded segment 94/289 (HOLD, 67 frames) 
  Encoded segment 100/289 (HOLD, 67 frames) 
  Encoded segment 92/289 (HOLD, 67 frames) 
  Muxed segment 92/289 
  Muxed segment 93/289 
  Muxed segment 94/289 
  Muxed segment 95/289 
  Encoded segment 104/289 (HOLD, 67 frames) 
  Encoded segment 110/289 (HOLD, 67 frames) 
  Encoded segment 96/289 (HOLD, 67 frames) 
  Muxed segment 96/289 
  Muxed segment 97/289 
  Encoded segment 106/289 (HOLD, 67 frames) 
  Encoded segment 98/289 (HOLD, 67 frames) 
  Muxed segment 98/289 
  Muxed segment 99/289 
  Muxed segment 100/289 
  Muxed segment 101/289 
  Encoded segment 108/289 (HOLD, 67 frames) 
  Encoded segment 102/289 (HOLD, 67 frames) 
  Muxed segment 102/289 
  Muxed segment 103/289 
  Muxed segment 104/289 
  Muxed segment 105/289 
  Muxed segment 106/289 
  Muxed segment 107/289 
  Muxed segment 108/289 
  Muxed segment 109/289 
  Muxed segment 110/289 
  Muxed segment 111/289 
  Encoded segment 112/289 (HOLD, 67 frames) 
  Muxed segment 112/289 
  Loaded: DSC_7474.JPG (1620x1080) 
  Loaded: DSC_7505.JPG (1620x1080) 
  Loaded: DSC_7512.JPG (1620x1080) 
  Loaded: DSC_7519.JPG (1620x1080) 
  Loaded: DSC_7523.JPG (1620x1080) 
  Loaded: DSC_7524.JPG (1620x1080) 
  Loaded: DSC_7441.JPG (1620x1080) 
  Loaded: DSC_7443.JPG (1620x1080) 
  Loaded: DSC_7446.JPG (1620x1080) 
  Loaded: DSC_7447.JPG (1620x1080) 
  Loaded: DSC_7465.JPG (1620x1080) 
  Loaded: DSC_7467.JPG (1620x1080) 
  Loaded: DSC_7472.JPG (1620x1080) 
  Loaded: DSC_7473.JPG (1620x1080) 
  Encoded segment 127/289 (DISSOLVE, 22 frames) 
  Encoded segment 137/289 (DISSOLVE, 22 frames) 
  Encoded segment 113/289 (DISSOLVE, 22 frames) 
  Muxed segment 113/289 
  Encoded segment 133/289 (DISSOLVE, 22 frames) 
  Encoded segment 115/289 (DISSOLVE, 22 frames) 
  Encoded segment 139/289 (DISSOLVE, 22 frames) 
  Encoded segment 125/289 (DISSOLVE, 22 frames) 
  Encoded segment 117/289 (DISSOLVE, 22 frames) 
  Encoded segment 119/289 (DISSOLVE, 22 frames) 
  Encoded segment 123/289 (DISSOLVE, 22 frames) 
  Encoded segment 129/289 (DISSOLVE, 22 frames) 
  Encoded segment 135/289 (DISSOLVE, 22 frames) 
  Encoded segment 131/289 (DISSOLVE, 22 frames) 
  Encoded segment 121/289 (DISSOLVE, 22 frames) 
  Encoded segment 132/289 (HOLD, 67 frames) 
  Encoded segment 114/289 (HOLD, 67 frames) 
  Muxed segment 114/289 
  Muxed segment 115/289 
  Encoded segment 126/289 (HOLD, 67 frames) 
  Encoded segment 116/289 (HOLD, 67 frames) 
  Encoded segment 134/289 (HOLD, 67 frames) 
  Muxed segment 116/289 
  Muxed segment 117/289 
  Encoded segment 120/289 (HOLD, 67 frames) 
  Encoded segment 140/289 (HOLD, 67 frames) 
  Encoded segment 128/289 (HOLD, 67 frames) 
  Encoded segment 122/289 (HOLD, 67 frames) 
  Encoded segment 124/289 (HOLD, 67 frames) 
  Encoded segment 130/289 (HOLD, 67 frames) 
  Encoded segment 118/289 (HOLD, 67 frames) 
  Muxed segment 118/289 
  Muxed segment 119/289 
  Encoded segment 136/289 (HOLD, 67 frames) 
  Muxed segment 120/289 
  Muxed segment 121/289 
  Muxed segment 122/289 
  Muxed segment 123/289 
  Muxed segment 124/289 
  Muxed segment 125/289 
  Muxed segment 126/289 
  Muxed segment 127/289 
  Muxed segment 128/289 
  Muxed segment 129/289 
  Muxed segment 130/289 
  Muxed segment 131/289 
  Muxed segment 132/289 
  Muxed segment 133/289 
  Muxed segment 134/289 
  Muxed segment 135/289 
  Muxed segment 136/289 
  Muxed segment 137/289 
  Encoded segment 138/289 (HOLD, 67 frames) 
  Muxed segment 138/289 
  Muxed segment 139/289 
  Muxed segment 140/289 
  Loaded: DSC_7525.JPG (1620x1080) 
  Loaded: DSC_7526.JPG (1620x1080) 
  Loaded: DSC_7527.JPG (1620x1080) 
  Loaded: DSC_7528.JPG (1620x1080) 
  Loaded: DSC_7529.JPG (1620x1080) 
  Loaded: DSC_7535.JPG (1620x1080) 
  Loaded: DSC_7536.JPG (1620x1080) 
  Loaded: DSC_7537.JPG (1620x1080) 
  Loaded: DSC_7538.JPG (1620x1080) 
  Loaded: DSC_7539.JPG (1620x1080) 
  Loaded: DSC_7541.JPG (1620x1080) 
  Loaded: DSC_7543.JPG (1620x1080) 
  Loaded: DSC_7547.JPG (1620x1080) 
  Loaded: DSC_7548.JPG (1620x1080) 
  Encoded segment 157/289 (DISSOLVE, 22 frames) 
  Encoded segment 159/289 (DISSOLVE, 22 frames) 
  Encoded segment 153/289 (DISSOLVE, 22 frames) 
  Encoded segment 161/289 (DISSOLVE, 22 frames) 
  Encoded segment 155/289 (DISSOLVE, 22 frames) 
  Encoded segment 143/289 (DISSOLVE, 22 frames) 
  Encoded segment 141/289 (DISSOLVE, 22 frames) 
  Muxed segment 141/289 
  Encoded segment 167/289 (DISSOLVE, 22 frames) 
  Encoded segment 149/289 (DISSOLVE, 22 frames) 
  Encoded segment 165/289 (DISSOLVE, 22 frames) 
  Encoded segment 163/289 (DISSOLVE, 22 frames) 
  Encoded segment 151/289 (DISSOLVE, 22 frames) 
  Encoded segment 145/289 (DISSOLVE, 22 frames) 
  Encoded segment 147/289 (DISSOLVE, 22 frames) 
  Encoded segment 152/289 (HOLD, 67 frames) 
  Encoded segment 160/289 (HOLD, 67 frames) 
  Encoded segment 148/289 (HOLD, 67 frames) 
  Encoded segment 142/289 (HOLD, 67 frames) 
  Muxed segment 142/289 
  Muxed segment 143/289 
  Encoded segment 162/289 (HOLD, 67 frames) 
  Encoded segment 154/289 (HOLD, 67 frames) 
  Encoded segment 146/289 (HOLD, 67 frames) 
  Encoded segment 158/289 (HOLD, 67 frames) 
  Encoded segment 144/289 (HOLD, 67 frames) 
  Muxed segment 144/289 
  Muxed segment 145/289 
  Muxed segment 146/289 
  Muxed segment 147/289 
  Muxed segment 148/289 
  Muxed segment 149/289 
  Encoded segment 156/289 (HOLD, 67 frames) 
  Encoded segment 150/289 (HOLD, 67 frames) 
  Muxed segment 150/289 
  Muxed segment 151/289 
  Muxed segment 152/289 
  Muxed segment 153/289 
  Muxed segment 154/289 
  Muxed segment 155/289 
  Encoded segment 164/289 (HOLD, 67 frames) 
  Muxed segment 156/289 
  Muxed segment 157/289 
  Muxed segment 158/289 
  Muxed segment 159/289 
  Muxed segment 160/289 
  Muxed segment 161/289 
  Muxed segment 162/289 
  Muxed segment 163/289 
  Muxed segment 164/289 
  Muxed segment 165/289 
  Encoded segment 168/289 (HOLD, 67 frames) 
  Encoded segment 166/289 (HOLD, 67 frames) 
  Muxed segment 166/289 
  Muxed segment 167/289 
  Muxed segment 168/289 
  Loaded: DSC_7561.JPG (1620x1080) 
  Loaded: DSC_7562.JPG (1620x1080) 
  Loaded: DSC_7549.JPG (1620x1080) 
  Loaded: DSC_7550.JPG (1620x1080) 
  Loaded: DSC_7551.JPG (1620x1080) 
  Loaded: DSC_7552.JPG (1620x1080) 
  Loaded: DSC_7553.JPG (1620x1080) 
  Loaded: DSC_7554.JPG (1620x1080) 
  Loaded: DSC_7555.JPG (1620x1080) 
  Loaded: DSC_7556.JPG (1620x1080) 
  Loaded: DSC_7557.JPG (1620x1080) 
  Loaded: DSC_7558.JPG (1620x1080) 
  Loaded: DSC_7559.JPG (1620x1080) 
  Loaded: DSC_7560.JPG (1620x1080) 
  Encoded segment 193/289 (DISSOLVE, 22 frames) 
  Encoded segment 177/289 (DISSOLVE, 22 frames) 
  Encoded segment 189/289 (DISSOLVE, 22 frames) 
  Encoded segment 185/289 (DISSOLVE, 22 frames) 
  Encoded segment 191/289 (DISSOLVE, 22 frames) 
  Encoded segment 195/289 (DISSOLVE, 22 frames) 
  Encoded segment 179/289 (DISSOLVE, 22 frames) 
  Encoded segment 187/289 (DISSOLVE, 22 frames) 
  Encoded segment 173/289 (DISSOLVE, 22 frames) 
  Encoded segment 181/289 (DISSOLVE, 22 frames) 
  Encoded segment 183/289 (DISSOLVE, 22 frames) 
  Encoded segment 175/289 (DISSOLVE, 22 frames) 
  Encoded segment 171/289 (DISSOLVE, 22 frames) 
  Encoded segment 169/289 (DISSOLVE, 22 frames) 
  Muxed segment 169/289 
  Encoded segment 196/289 (HOLD, 67 frames) 
  Encoded segment 186/289 (HOLD, 67 frames) 
  Encoded segment 194/289 (HOLD, 67 frames) 
  Encoded segment 188/289 (HOLD, 67 frames) 
  Encoded segment 190/289 (HOLD, 67 frames) 
  Encoded segment 184/289 (HOLD, 67 frames) 
  Encoded segment 180/289 (HOLD, 67 frames) 
  Encoded segment 172/289 (HOLD, 67 frames) 
  Encoded segment 192/289 (HOLD, 67 frames) 
  Encoded segment 176/289 (HOLD, 67 frames) 
  Encoded segment 182/289 (HOLD, 67 frames) 
  Encoded segment 174/289 (HOLD, 67 frames) 
  Encoded segment 178/289 (HOLD, 67 frames) 
  Encoded segment 170/289 (HOLD, 67 frames) 
  Muxed segment 170/289 
  Muxed segment 171/289 
  Muxed segment 172/289 
  Muxed segment 173/289 
  Muxed segment 174/289 
  Muxed segment 175/289 
  Loaded: DSC_7563.JPG (1620x1080) 
  Muxed segment 176/289 
  Muxed segment 177/289 
  Muxed segment 178/289 
  Muxed segment 179/289 
  Muxed segment 180/289 
  Muxed segment 181/289 
  Muxed segment 182/289 
  Muxed segment 183/289 
  Loaded: DSC_7564.JPG (1620x1080) 
  Muxed segment 184/289 
  Muxed segment 185/289 
  Muxed segment 186/289 
  Muxed segment 187/289 
  Muxed segment 188/289 
  Muxed segment 189/289 
  Muxed segment 190/289 
  Muxed segment 191/289 
  Loaded: DSC_7565.JPG (1620x1080) 
  Muxed segment 192/289 
  Muxed segment 193/289 
  Muxed segment 194/289 
  Muxed segment 195/289 
  Muxed segment 196/289 
  Loaded: DSC_7566.JPG (1620x1080) 
  Loaded: DSC_7567.JPG (1620x1080) 
  Loaded: DSC_7568.JPG (1620x1080) 
  Loaded: DSC_7569.JPG (1620x1080) 
  Loaded: DSC_7570.JPG (1620x1080) 
  Loaded: DSC_7571.JPG (1620x1080) 
  Loaded: DSC_7572.JPG (1620x1080) 
  Loaded: DSC_7573.JPG (1620x1080) 
  Loaded: DSC_7574.JPG (1620x1080) 
  Loaded: DSC_7575.JPG (1620x1080) 
  Loaded: DSC_7576.JPG (1620x1080) 
  Encoded segment 223/289 (DISSOLVE, 22 frames) 
  Encoded segment 217/289 (DISSOLVE, 22 frames) 
  Encoded segment 201/289 (DISSOLVE, 22 frames) 
  Encoded segment 205/289 (DISSOLVE, 22 frames) 
  Encoded segment 203/289 (DISSOLVE, 22 frames) 
  Encoded segment 219/289 (DISSOLVE, 22 frames) 
  Encoded segment 199/289 (DISSOLVE, 22 frames) 
  Encoded segment 209/289 (DISSOLVE, 22 frames) 
  Encoded segment 213/289 (DISSOLVE, 22 frames) 
  Encoded segment 211/289 (DISSOLVE, 22 frames) 
  Encoded segment 197/289 (DISSOLVE, 22 frames) 
  Muxed segment 197/289 
  Encoded segment 207/289 (DISSOLVE, 22 frames) 
  Encoded segment 215/289 (DISSOLVE, 22 frames) 
  Encoded segment 221/289 (DISSOLVE, 22 frames) 
  Encoded segment 208/289 (HOLD, 67 frames) 
  Encoded segment 202/289 (HOLD, 67 frames) 
  Encoded segment 214/289 (HOLD, 67 frames) 
  Encoded segment 224/289 (HOLD, 67 frames) 
  Encoded segment 218/289 (HOLD, 67 frames) 
  Encoded segment 206/289 (HOLD, 67 frames) 
  Encoded segment 216/289 (HOLD, 67 frames) 
  Encoded segment 222/289 (HOLD, 67 frames) 
  Encoded segment 210/289 (HOLD, 67 frames) 
  Encoded segment 212/289 (HOLD, 67 frames) 
  Encoded segment 200/289 (HOLD, 67 frames) 
  Encoded segment 220/289 (HOLD, 67 frames) 
  Encoded segment 204/289 (HOLD, 67 frames) 
  Encoded segment 198/289 (HOLD, 67 frames) 
  Muxed segment 198/289 
  Muxed segment 199/289 
  Muxed segment 200/289 
  Muxed segment 201/289 
  Muxed segment 202/289 
  Loaded: DSC_7577.JPG (1620x1080) 
  Muxed segment 203/289 
  Muxed segment 204/289 
  Muxed segment 205/289 
  Muxed segment 206/289 
  Muxed segment 207/289 
  Muxed segment 208/289 
  Loaded: DSC_7578.JPG (1620x1080) 
  Muxed segment 209/289 
  Muxed segment 210/289 
  Muxed segment 211/289 
  Muxed segment 212/289 
  Muxed segment 213/289 
  Muxed segment 214/289 
  Muxed segment 215/289 
  Loaded: DSC_7579.JPG (1620x1080) 
  Muxed segment 216/289 
  Muxed segment 217/289 
  Muxed segment 218/289 
  Muxed segment 219/289 
  Muxed segment 220/289 
  Muxed segment 221/289 
  Loaded: DSC_7580.JPG (1620x1080) 
  Muxed segment 222/289 
  Muxed segment 223/289 
  Muxed segment 224/289 
  Loaded: DSC_7581.JPG (1620x1080) 
  Loaded: DSC_7582.JPG (1620x1080) 
  Loaded: DSC_7583.JPG (1620x1080) 
  Loaded: DSC_7584.JPG (1620x1080) 
  Loaded: DSC_7585.JPG (1620x1080) 
  Loaded: DSC_7586.JPG (1620x1080) 
  Loaded: DSC_7587.JPG (1620x1080) 
  Loaded: DSC_7588.JPG (1620x1080) 
  Loaded: DSC_7589.JPG (1620x1080) 
  Loaded: DSC_7590.JPG (1620x1080) 
  Encoded segment 233/289 (DISSOLVE, 22 frames) 
  Encoded segment 235/289 (DISSOLVE, 22 frames) 
  Encoded segment 227/289 (DISSOLVE, 22 frames) 
  Encoded segment 237/289 (DISSOLVE, 22 frames) 
  Encoded segment 243/289 (DISSOLVE, 22 frames) 
  Encoded segment 239/289 (DISSOLVE, 22 frames)
  Encoded segment 247/289 (DISSOLVE, 22 frames)  
  Encoded segment 231/289 (DISSOLVE, 22 frames) 
  Encoded segment 251/289 (DISSOLVE, 22 frames) 
  Encoded segment 245/289 (DISSOLVE, 22 frames) 
  Encoded segment 225/289 (DISSOLVE, 22 frames) 
  Muxed segment 225/289 
  Encoded segment 229/289 (DISSOLVE, 22 frames) 
  Encoded segment 241/289 (DISSOLVE, 22 frames) 
  Encoded segment 249/289 (DISSOLVE, 22 frames) 
  Encoded segment 230/289 (HOLD, 67 frames) 
  Encoded segment 236/289 (HOLD, 67 frames) 
  Encoded segment 234/289 (HOLD, 67 frames) 
  Encoded segment 244/289 (HOLD, 67 frames) 
  Encoded segment 238/289 (HOLD, 67 frames) 
  Encoded segment 248/289 (HOLD, 67 frames) 
  Encoded segment 226/289 (HOLD, 67 frames) 
  Muxed segment 226/289 
  Muxed segment 227/289 
  Encoded segment 232/289 (HOLD, 67 frames) 
  Encoded segment 250/289 (HOLD, 67 frames) 
  Encoded segment 246/289 (HOLD, 67 frames) 
  Encoded segment 252/289 (HOLD, 67 frames) 
  Encoded segment 228/289 (HOLD, 67 frames) 
  Muxed segment 228/289 
  Muxed segment 229/289 
  Muxed segment 230/289 
  Muxed segment 231/289 
  Muxed segment 232/289 
  Muxed segment 233/289 
  Muxed segment 234/289 
  Muxed segment 235/289 
  Muxed segment 236/289 
  Muxed segment 237/289 
  Muxed segment 238/289 
  Muxed segment 239/289 
  Encoded segment 242/289 (HOLD, 67 frames) 
  Encoded segment 240/289 (HOLD, 67 frames) 
  Muxed segment 240/289 
  Muxed segment 241/289 
  Muxed segment 242/289 
  Muxed segment 243/289 
  Loaded: DSC_7593.JPG (1620x1080) 
  Muxed segment 244/289 
  Muxed segment 245/289 
  Muxed segment 246/289 
  Muxed segment 247/289 
  Muxed segment 248/289 
  Muxed segment 249/289 
  Loaded: DSC_7594.JPG (1620x1080) 
  Muxed segment 250/289 
  Muxed segment 251/289 
  Muxed segment 252/289 
  Loaded: DSC_7595.JPG (1620x1080) 
  Loaded: DSC_7602.JPG (1620x1080) 
  Loaded: DSC_7603.JPG (1620x1080) 
  Loaded: DSC_7613.JPG (1620x1080) 
  Loaded: DSC_7617.JPG (1620x1080) 
  Loaded: DSC_7634.JPG (1620x1080) 
  Loaded: DSC_7645.JPG (1620x1080) 
  Loaded: DSC_7649.JPG (1620x1080) 
  Loaded: DSC_7655.JPG (1620x1080) 
  Loaded: DSC_7660.JPG (1620x1080) 
  Loaded: DSC_7591.JPG (1620x1080) 
  Loaded: DSC_7592.JPG (1620x1080) 
  Encoded segment 255/289 (DISSOLVE, 22 frames) 
  Encoded segment 253/289 (DISSOLVE, 22 frames) 
  Encoded segment 265/289 (DISSOLVE, 22 frames) 
  Muxed segment 253/289 
  Encoded segment 261/289 (DISSOLVE, 22 frames) 
  Encoded segment 259/289 (DISSOLVE, 22 frames) 
  Encoded segment 267/289 (DISSOLVE, 22 frames) 
  Encoded segment 273/289 (DISSOLVE, 22 frames) 
  Encoded segment 257/289 (DISSOLVE, 22 frames) 
  Encoded segment 279/289 (DISSOLVE, 22 frames) 
  Encoded segment 263/289 (DISSOLVE, 22 frames) 
  Encoded segment 275/289 (DISSOLVE, 22 frames) 
  Encoded segment 277/289 (DISSOLVE, 22 frames) 
  Encoded segment 269/289 (DISSOLVE, 22 frames) 
  Encoded segment 271/289 (DISSOLVE, 22 frames) 
  Encoded segment 260/289 (HOLD, 67 frames) 
  Encoded segment 280/289 (HOLD, 67 frames) 
  Encoded segment 266/289 (HOLD, 67 frames) 
  Encoded segment 262/289 (HOLD, 67 frames) 
  Encoded segment 264/289 (HOLD, 67 frames) 
  Encoded segment 254/289 (HOLD, 67 frames) 
  Muxed segment 254/289 
  Muxed segment 255/289 
  Encoded segment 256/289 (HOLD, 67 frames) 
  Muxed segment 256/289 
  Muxed segment 257/289 
  Encoded segment 258/289 (HOLD, 67 frames) 
  Muxed segment 258/289 
  Muxed segment 259/289 
  Muxed segment 260/289 
  Muxed segment 261/289 
  Muxed segment 262/289 
  Muxed segment 263/289 
  Muxed segment 264/289 
  Muxed segment 265/289 
  Muxed segment 266/289 
  Muxed segment 267/289 
  Encoded segment 272/289 (HOLD, 67 frames) 
  Encoded segment 268/289 (HOLD, 67 frames) 
  Muxed segment 268/289 
  Muxed segment 269/289 
  Encoded segment 276/289 (HOLD, 67 frames) 
  Encoded segment 274/289 (HOLD, 67 frames) 
  Encoded segment 270/289 (HOLD, 67 frames) 
  Muxed segment 270/289 
  Muxed segment 271/289 
  Muxed segment 272/289 
  Encoded segment 278/289 (HOLD, 67 frames) 
  Muxed segment 273/289 
  Muxed segment 274/289 
  Muxed segment 275/289 
  Muxed segment 276/289 
  Muxed segment 277/289 
  Muxed segment 278/289 
  Loaded: DSC_7666.JPG (1620x1080) 
  Muxed segment 279/289 
  Muxed segment 280/289 
  Loaded: DSC_7706.JPG (1620x1080) 
  Loaded: DSC_7709.JPG (1620x1080) 
  Loaded: DSC_7713.JPG (1620x1080) 
  Encoded segment 287/289 (DISSOLVE, 22 frames) 
  Encoded segment 285/289 (DISSOLVE, 22 frames)|
  Encoded segment 281/289 (DISSOLVE, 22 frames) 
  Encoded segment 283/289 (DISSOLVE, 22 frames) 
  Muxed segment 281/289 
  Encoded segment 289/289 (FADE_OUT, 22 frames) 
  Encoded segment 286/289 (HOLD, 67 frames) 
  Encoded segment 284/289 (HOLD, 67 frames) 
  Encoded segment 288/289 (HOLD, 67 frames) 
  Encoded segment 282/289 (HOLD, 67 frames) 
  Muxed segment 282/289 
  Muxed segment 283/289 
  Muxed segment 284/289 
  Muxed segment 285/289 
  Muxed segment 286/289 
  Muxed segment 287/289 
  Muxed segment 288/289 
  Muxed segment 289/289 
Wrote 12838 total frames

Success! Created slowtest.mp4
Total processing time: 83.56 seconds
```
</details>
