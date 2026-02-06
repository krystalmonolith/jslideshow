package com.krystalmonolith.jslideshow;

import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.common.Codec;
import org.jcodec.common.VideoCodecMeta;
import org.jcodec.common.VideoEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.*;
import org.jcodec.containers.mp4.Brand;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.muxer.CodecMP4MuxerTrack;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.scale.AWTUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Parallel H.264 encoder using batched segment-based GOP encoding with
 * dissolve transitions, fade in/out, and an async muxer thread.
 */
public class JCodecParallelEncoder {

    private static final char[] SPINNER = {'|', '/', '-', '\\'};
    private static final AtomicInteger spinnerIndex = new AtomicInteger(0);

    private static void spin() {
        System.out.print("\b" + SPINNER[spinnerIndex.getAndIncrement() & 3]);
    }

    private static void clearSpinner() {
        System.out.print("\b \b");
    }

    enum SegmentType { FADE_IN, HOLD, DISSOLVE, FADE_OUT }

    record SegmentSpec(int segmentIndex, SegmentType type,
                       int imageIndexA, int imageIndexB, int frameCount) {}

    record EncodedSegment(int segmentIndex, List<MP4Packet> packets) {}

    public JCodecParallelEncoder() {
    }

    /**
     * Build the list of segment specifications for N images.
     * Layout: FADE_IN, HOLD[0], DISSOLVE[0->1], HOLD[1], ..., HOLD[N-1], FADE_OUT
     */
    static List<SegmentSpec> buildSegmentSpecs(int imageCount, int holdFrames, int transitionFrames) {
        List<SegmentSpec> specs = new ArrayList<>(2 * imageCount);
        int segIdx = 0;

        // Fade in from black to first image
        specs.add(new SegmentSpec(segIdx++, SegmentType.FADE_IN, 0, -1, transitionFrames));

        for (int i = 0; i < imageCount; i++) {
            // Hold segment for image i
            specs.add(new SegmentSpec(segIdx++, SegmentType.HOLD, i, -1, holdFrames));

            if (i < imageCount - 1) {
                // Dissolve from image i to image i+1
                specs.add(new SegmentSpec(segIdx++, SegmentType.DISSOLVE, i, i + 1, transitionFrames));
            }
        }

        // Fade out from last image to black
        specs.add(new SegmentSpec(segIdx++, SegmentType.FADE_OUT, imageCount - 1, -1, transitionFrames));

        return specs;
    }

    /**
     * Blend two images together with specified alpha using AlphaComposite.SRC_OVER.
     */
    private static BufferedImage blendImages(BufferedImage img1, BufferedImage img2, float alpha) {
        int width = img1.getWidth();
        int height = img1.getHeight();

        var blended = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = blended.createGraphics();
        try {
            g.drawImage(img1, 0, 0, null);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.drawImage(img2, 0, 0, null);
        } finally {
            g.dispose();
        }
        return blended;
    }

    /**
     * Encode a hold segment: static image repeated for frameCount frames.
     */
    private static EncodedSegment encodeHoldSegment(SegmentSpec spec, BufferedImage image, int frameRate) {
        return encodeFrames(spec.segmentIndex(), spec.frameCount(), frameRate,
                localFrame -> image);
    }

    /**
     * Encode a dissolve segment: alpha-blended transition from imgA to imgB.
     */
    private static EncodedSegment encodeDissolveSegment(SegmentSpec spec, BufferedImage imgA,
                                                        BufferedImage imgB, int frameRate) {
        int frameCount = spec.frameCount();
        return encodeFrames(spec.segmentIndex(), frameCount, frameRate,
                localFrame -> {
                    float alpha = (float) (localFrame + 1) / frameCount;
                    return blendImages(imgA, imgB, alpha);
                });
    }

    /**
     * Encode a fade segment (fade-in or fade-out) by dissolving between a black image and the real image.
     */
    private static EncodedSegment encodeFadeSegment(SegmentSpec spec, BufferedImage realImage, int frameRate) {
        BufferedImage black = new BufferedImage(realImage.getWidth(), realImage.getHeight(), BufferedImage.TYPE_INT_RGB);
        if (spec.type() == SegmentType.FADE_IN) {
            return encodeDissolveSegment(spec, black, realImage, frameRate);
        } else {
            return encodeDissolveSegment(spec, realImage, black, frameRate);
        }
    }

    /**
     * Generic frame encoder: encodes frameCount frames using a frame supplier.
     */
    private static EncodedSegment encodeFrames(int segmentIndex, int frameCount, int frameRate,
                                               java.util.function.IntFunction<BufferedImage> frameSupplier) {
        if (frameCount <= 0) {
            return new EncodedSegment(segmentIndex, List.of());
        }

        H264Encoder encoder = H264Encoder.createH264Encoder();
        List<MP4Packet> packets = new ArrayList<>(frameCount);

        for (int localFrame = 0; localFrame < frameCount; localFrame++) {
            BufferedImage frameImage = frameSupplier.apply(localFrame);
            Picture yuv = AWTUtil.fromBufferedImage(frameImage, ColorSpace.YUV420J);

            int bufferSize = frameImage.getWidth() * frameImage.getHeight() * 3;
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

            VideoEncoder.EncodedFrame encoded = encoder.encodeFrame(yuv, buffer);
            boolean isKeyFrame = encoded.isKeyFrame();

            ByteBuffer srcData = encoded.getData();
            ByteBuffer data = ByteBuffer.allocate(srcData.remaining());
            data.put(srcData);
            data.flip();

            MP4Packet packet = new MP4Packet(
                    data,
                    localFrame,
                    frameRate,
                    1L,
                    localFrame,
                    isKeyFrame ? Packet.FrameType.KEY : Packet.FrameType.INTER,
                    null,
                    localFrame,
                    localFrame,
                    0,
                    0L,
                    data.remaining(),
                    isKeyFrame
            );

            packets.add(packet);
            spin();
        }

        return new EncodedSegment(segmentIndex, packets);
    }

    /**
     * Dispatch encoding of one segment based on its type.
     */
    private static EncodedSegment encodeOneSegment(SegmentSpec spec, Map<Integer, BufferedImage> imageCache,
                                                   int frameRate) {
        return switch (spec.type()) {
            case HOLD -> encodeHoldSegment(spec, imageCache.get(spec.imageIndexA()), frameRate);
            case DISSOLVE -> encodeDissolveSegment(spec, imageCache.get(spec.imageIndexA()),
                    imageCache.get(spec.imageIndexB()), frameRate);
            case FADE_IN, FADE_OUT -> encodeFadeSegment(spec, imageCache.get(spec.imageIndexA()), frameRate);
        };
    }

    /**
     * Load images needed for a batch into the cache (skipping already-loaded ones).
     */
    private static void loadForBatch(List<SegmentSpec> batch, File[] imageFiles,
                                     Map<Integer, BufferedImage> imageCache) throws IOException {
        Set<Integer> needed = new HashSet<>();
        for (SegmentSpec spec : batch) {
            if (spec.imageIndexA() >= 0) needed.add(spec.imageIndexA());
            if (spec.imageIndexB() >= 0) needed.add(spec.imageIndexB());
        }

        for (int idx : needed) {
            if (!imageCache.containsKey(idx)) {
                BufferedImage img = ImageIO.read(imageFiles[idx]);
                if (img == null) {
                    throw new IOException("Could not read image: " + imageFiles[idx].getName());
                }
                imageCache.put(idx, img);
                clearSpinner();
                System.out.printf("%n  Loaded: %s (%dx%d)  ", imageFiles[idx].getName(), img.getWidth(), img.getHeight());
            }
        }
    }

    /**
     * Evict images from cache that are not needed by any future segment.
     */
    private static void evictUnneeded(Map<Integer, BufferedImage> imageCache, List<SegmentSpec> futureSpecs) {
        Set<Integer> futureNeeded = new HashSet<>();
        for (SegmentSpec spec : futureSpecs) {
            if (spec.imageIndexA() >= 0) futureNeeded.add(spec.imageIndexA());
            if (spec.imageIndexB() >= 0) futureNeeded.add(spec.imageIndexB());
        }
        imageCache.keySet().removeIf(idx -> !futureNeeded.contains(idx));
    }

    /**
     * Encode images into an MP4 video file with dissolve transitions and fade in/out.
     *
     * @param imageFiles       array of image files to encode
     * @param holdFrames       number of frames to hold each image
     * @param transitionFrames number of frames for each transition
     * @param frameRate        frame rate for the output video
     * @param output           output MP4 file
     */
    public void encode(File[] imageFiles, int holdFrames, int transitionFrames,
                       int frameRate, File output) throws Exception {
        if (imageFiles.length == 0) {
            throw new IllegalArgumentException("Image file list cannot be empty");
        }

        List<SegmentSpec> allSpecs = buildSegmentSpecs(imageFiles.length, holdFrames, transitionFrames);
        int totalSegments = allSpecs.size();
        int batchSize = Runtime.getRuntime().availableProcessors();

        long totalFrames = allSpecs.stream().mapToLong(SegmentSpec::frameCount).sum();
        System.out.printf("Encoding %d images into %d segments (%d total frames) @ %d fps%n",
                imageFiles.length, totalSegments, totalFrames, frameRate);
        System.out.printf("Batch size: %d (parallel threads)%n", batchSize);

        // Shared state for muxer coordination
        ConcurrentSkipListMap<Integer, EncodedSegment> completedSegments = new ConcurrentSkipListMap<>();
        Object muxerLock = new Object();
        AtomicReference<Exception> muxerError = new AtomicReference<>();
        final boolean[] encodingComplete = {false};

        // Start muxer thread
        Thread muxerThread = new Thread(() -> {
            try {
                muxerLoop(completedSegments, totalSegments, frameRate, output, muxerLock, encodingComplete);
            } catch (Exception e) {
                muxerError.set(e);
            }
        }, "muxer-thread");
        muxerThread.start();

        // Batched encoding
        Map<Integer, BufferedImage> imageCache = new HashMap<>();

        try {
            for (int batchStart = 0; batchStart < totalSegments; batchStart += batchSize) {
                // Check for muxer errors early
                Exception err = muxerError.get();
                if (err != null) throw err;

                int batchEnd = Math.min(batchStart + batchSize, totalSegments);
                List<SegmentSpec> batch = allSpecs.subList(batchStart, batchEnd);

                // Load images needed for this batch
                loadForBatch(batch, imageFiles, imageCache);

                // Encode batch in parallel
                batch.parallelStream().forEach(spec -> {
                    EncodedSegment segment = encodeOneSegment(spec, imageCache, frameRate);
                    completedSegments.put(segment.segmentIndex(), segment);
                    clearSpinner();
                    System.out.printf("%n  Encoded segment %d/%d (%s, %d frames)  ",
                            spec.segmentIndex() + 1, totalSegments, spec.type(), spec.frameCount());
                    synchronized (muxerLock) {
                        muxerLock.notifyAll();
                    }
                });

                // Evict images not needed by future segments
                if (batchEnd < totalSegments) {
                    evictUnneeded(imageCache, allSpecs.subList(batchEnd, totalSegments));
                } else {
                    imageCache.clear();
                }
            }
        } finally {
            // Signal encoding complete and wake muxer
            synchronized (muxerLock) {
                encodingComplete[0] = true;
                muxerLock.notifyAll();
            }
        }

        // Wait for muxer to finish
        muxerThread.join();

        // Check for muxer errors
        Exception err = muxerError.get();
        if (err != null) throw err;
    }

    /**
     * Muxer thread body: drains consecutive completed segments and writes them to the MP4 file.
     */
    private void muxerLoop(ConcurrentSkipListMap<Integer, EncodedSegment> completedSegments,
                           int totalSegments, int frameRate, File output,
                           Object muxerLock, boolean[] encodingComplete) throws Exception {
        try (SeekableByteChannel out = NIOUtils.writableFileChannel(output.getPath())) {
            MP4Muxer muxer = MP4Muxer.createMP4Muxer(out, Brand.MP4);

            Size size = new Size(1920, 1080);
            CodecMP4MuxerTrack track = (CodecMP4MuxerTrack) muxer.addVideoTrack(
                    Codec.H264,
                    VideoCodecMeta.createVideoCodecMeta("avc1", null, size, Rational.ONE)
            );

            int nextExpected = 0;
            long globalFrame = 0;

            while (nextExpected < totalSegments) {
                EncodedSegment segment;

                // Try to drain consecutive segments
                while ((segment = completedSegments.remove(nextExpected)) != null) {
                    for (MP4Packet packet : segment.packets()) {
                        ByteBuffer rawData = packet.getData().duplicate();

                        MP4Packet globalPacket = new MP4Packet(
                                rawData,
                                globalFrame,
                                frameRate,
                                1L,
                                globalFrame,
                                packet.getFrameType(),
                                null,
                                (int) globalFrame,
                                globalFrame,
                                0,
                                0L,
                                rawData.remaining(),
                                packet.getFrameType() == Packet.FrameType.KEY
                        );

                        track.addFrame(globalPacket);
                        globalFrame++;
                    }
                    clearSpinner();
                    System.out.printf("%n  Muxed segment %d/%d  ", nextExpected + 1, totalSegments);
                    nextExpected++;
                }

                if (nextExpected < totalSegments) {
                    synchronized (muxerLock) {
                        // Re-check after acquiring lock
                        if (!completedSegments.containsKey(nextExpected) && !encodingComplete[0]) {
                            muxerLock.wait(500);
                        }
                    }
                }
            }

            muxer.finish();
            clearSpinner();
            System.out.printf("%nWrote %d total frames%n", globalFrame);

        } catch (Exception e) {
            System.err.println("Error writing to \"" + output.getAbsolutePath() + "\"");
            throw e;
        }
    }
}
