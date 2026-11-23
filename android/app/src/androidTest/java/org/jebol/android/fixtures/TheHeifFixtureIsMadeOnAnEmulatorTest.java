package org.jebol.android.fixtures;

import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class TheHeifFixtureIsMadeOnAnEmulatorTest {

    static final String WRITTEN = "android-made-64x64.heic";

    private static final int WIDE = 64;

    private static final int HIGH = 64;

    @Test
    @DisplayName("Android's own HEVC encoder and HEIF muxer write a 64 by 64 picture of one colour, for the test resources")
    void writesTheHeifFixture() throws Exception {
        File written = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),
                WRITTEN);

        writeHeifFilledWith(written, 200, 60, 40);

        assertThat(written).isFile().isNotEmpty();
    }

    private static void writeHeifFilledWith(File written, int red, int green, int blue) throws Exception {
        MediaFormat asked = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, WIDE, HIGH);
        asked.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        asked.setInteger(MediaFormat.KEY_BIT_RATE, 1_000_000);
        asked.setInteger(MediaFormat.KEY_FRAME_RATE, 1);
        asked.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0);
        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
        try {
            encoder.configure(asked, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            int input = encoder.dequeueInputBuffer(5_000_000);
            Image frame = encoder.getInputImage(input);
            fillPlane(frame.getPlanes()[0], WIDE, HIGH, (int) (0.257 * red + 0.504 * green + 0.098 * blue + 16));
            fillPlane(frame.getPlanes()[1], WIDE / 2, HIGH / 2, (int) (-0.148 * red - 0.291 * green + 0.439 * blue + 128));
            fillPlane(frame.getPlanes()[2], WIDE / 2, HIGH / 2, (int) (0.439 * red - 0.368 * green - 0.071 * blue + 128));
            encoder.queueInputBuffer(input, 0, WIDE * HIGH * 3 / 2, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            MediaMuxer muxer = new MediaMuxer(written.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_HEIF);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int track = -1;
            boolean finished = false;
            while (!finished) {
                int output = encoder.dequeueOutputBuffer(info, 5_000_000);
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat produced = encoder.getOutputFormat();
                    produced.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC);
                    track = muxer.addTrack(produced);
                    muxer.start();
                } else if (output >= 0) {
                    ByteBuffer data = encoder.getOutputBuffer(output);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
                        muxer.writeSampleData(track, data, info);
                    }
                    finished = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    encoder.releaseOutputBuffer(output, false);
                } else if (output == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    throw new IllegalStateException("the HEVC encoder gave nothing for five seconds");
                }
            }
            muxer.stop();
            muxer.release();
        } finally {
            encoder.release();
        }
    }

    private static void fillPlane(Image.Plane plane, int wide, int high, int value) {
        ByteBuffer buffer = plane.getBuffer();
        for (int down = 0; down < high; down++) {
            for (int across = 0; across < wide; across++) {
                buffer.put(down * plane.getRowStride() + across * plane.getPixelStride(), (byte) value);
            }
        }
    }
}
