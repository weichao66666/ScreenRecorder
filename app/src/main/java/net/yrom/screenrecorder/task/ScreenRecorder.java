/*
 * Copyright (c) 2014 Yrom Wang <http://www.yrom.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.yrom.screenrecorder.task;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.util.Log;
import android.view.Surface;

import net.yrom.screenrecorder.core.Packager;
import net.yrom.screenrecorder.rtmp.ResFlvData;
import net.yrom.screenrecorder.rtmp.ResFlvDataCollecter;
import net.yrom.screenrecorder.tools.LogTools;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.yrom.screenrecorder.rtmp.ResFlvData.FLV_TAGTYPE_VIDEO;

/**
 * @author Yrom
 *         Modified by raomengyang 2017-03-12
 */
public class ScreenRecorder extends Thread {
    private static final String TAG = "ScreenRecorder";

    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video Coding
    private static final int FRAME_RATE = 30; // 30fps
    private static final int IFRAME_INTERVAL = 2; // 2s between I-frames
    private static final int TIMEOUT_US = 10000;

    private ResFlvDataCollecter mDataCollecter;
    private int mWidth;
    private int mHeight;
    private int mBitRate;
    private int mDpi;
    private MediaProjection mMediaProjection;

    private long startTime = 0;
    private MediaCodec mediaCodec;
    private Surface surface;
    private AtomicBoolean quit = new AtomicBoolean(false);
    private MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    private VirtualDisplay virtualDisplay;

    public ScreenRecorder(ResFlvDataCollecter dataCollecter, int width, int height, int bitRate, int dpi, MediaProjection mediaProjection) {
        super(TAG);
        mDataCollecter = dataCollecter;
        mWidth = width;
        mHeight = height;
        mBitRate = bitRate;
        mDpi = dpi;
        mMediaProjection = mediaProjection;
        startTime = 0;
    }

    private void release() {
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }
    }

    @Override
    public void run() {
        try {
            prepareEncoder();
            /*将画面投影到 VirtualDisplay 中*/
            virtualDisplay = mMediaProjection.createVirtualDisplay(TAG, mWidth, mHeight, mDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC/*设置可以将其他显示器的内容反射到该 VirtualDisplay 上（TODO 默认只能是自身应用？）*/,
                    surface/*VirtualDisplay 将图像渲染到 Surface 中*/,
                    null, null);
            Log.d(TAG, "created virtual display: " + virtualDisplay);
            recordVirtualDisplay();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            release();
        }
    }

    private void prepareEncoder() {
        try {
            // 创建 video/avc 类型的编码器，在这个场景下，MediaCodec 只允许使用 video/avc 编码类型，使用其他的编码会 crash
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
            // 设置视频格式：video/avc
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
            // 设置颜色格式：Surface
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            // 设置比特率
            format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
            // 设置帧率：30fps
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            // 设置关键帧间隔时间：2s
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
            // 编码器应用格式
            mediaCodec.configure(format, null, null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE/*设置该 MediaCodec 作为编码器使用*/);
            // 创建 Surface
            surface = mediaCodec.createInputSurface();
            mediaCodec.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void recordVirtualDisplay() {
        while (!quit.get()) {
            // 最多阻塞 10s，用于获取已成功解码的输出缓冲区的索引（缓冲的元数据会放到 bufferInfo 中）或者一个状态值
            int eobIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
            switch (eobIndex) {
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    LogTools.d("MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:" + mediaCodec.getOutputFormat().toString());
                    sendAvcDecoderConfigurationRecord(0, mediaCodec.getOutputFormat());
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    LogTools.d("MediaCodec.INFO_TRY_AGAIN_LATER");
                    break;
                default:
                    LogTools.d("mediaCodec eobIndex=" + eobIndex);
                    /**
                     * we send sps pps already in INFO_OUTPUT_FORMAT_CHANGED
                     * so we ignore MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                     */
                    if (bufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && bufferInfo.size != 0) {
                        if (startTime == 0) {
                            startTime = bufferInfo.presentationTimeUs / 1000;
                        }
                        //
                        ByteBuffer realData = mediaCodec.getOutputBuffer(eobIndex);
                        if (realData != null) {
                            realData.position(bufferInfo.offset + 4);// 跳过用于表示上个 TAG 大小的 4 个字节
                            realData.limit(bufferInfo.offset + bufferInfo.size);
                            sendAvcData((bufferInfo.presentationTimeUs / 1000) - startTime, realData);
                        }
                    }
                    mediaCodec.releaseOutputBuffer(eobIndex, false);
                    break;
            }
        }
    }

    private void sendAvcDecoderConfigurationRecord(long timeMs, MediaFormat format) {
        byte[] avcDecoderConfigurationRecord = Packager.AvcPackager.generateAvcDecoderConfigurationRecord(format);
        int packetLen = Packager.FlvPackager.FLV_VIDEO_TAG_HEADER_LENGTH + avcDecoderConfigurationRecord.length;
        byte[] finalBuff = new byte[packetLen];
        // 在 finalBuff 数组最前面插入 5 个字节的 FLV video TAG header
        Packager.FlvPackager.setAvcTag(finalBuff, 0, true, true, avcDecoderConfigurationRecord.length);
        // 将 avcDecoderConfigurationRecord 数组的数据拼接到 finalBuff 数组后面
        System.arraycopy(avcDecoderConfigurationRecord, 0, finalBuff, Packager.FlvPackager.FLV_VIDEO_TAG_HEADER_LENGTH, avcDecoderConfigurationRecord.length);
        ResFlvData resFlvData = new ResFlvData();
        resFlvData.droppable = false;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) timeMs;
        resFlvData.flvTagType = FLV_TAGTYPE_VIDEO;
        resFlvData.videoFrameType = ResFlvData.AVC_NALU_TYPE_IDR;
        mDataCollecter.collect(resFlvData, FLV_TAGTYPE_VIDEO);
    }

    private void sendAvcData(long timeMs, ByteBuffer data) {
        int realDataLength = data.remaining();
        int packetLen = Packager.FlvPackager.FLV_VIDEO_TAG_HEADER_LENGTH + Packager.FlvPackager.NALU_HEADER_LENGTH + realDataLength;
        byte[] finalBuff = new byte[packetLen];
        // 将 data 数组的数据放到 finalBuff 数组的第 Packager.FlvPackager.FLV_VIDEO_TAG_HEADER_LENGTH+Packager.FlvPackager.NALU_HEADER_LENGTH+1 个位置到结束
        data.get(finalBuff, Packager.FlvPackager.FLV_VIDEO_TAG_HEADER_LENGTH + Packager.FlvPackager.NALU_HEADER_LENGTH, realDataLength);
        // 获取 NALU 类型，计算后得 5 表示 NALU 类型是 AVC_NALU_TYPE_IDR
        int frameType = finalBuff[Packager.FlvPackager.FLV_VIDEO_TAG_HEADER_LENGTH + Packager.FlvPackager.NALU_HEADER_LENGTH] & 0x1F;
        // 在 finalBuff 数组最前面插入 9 个字节的 FLV video TAG（含 5 个字节的 header 和 4 个字节的 length）
        Packager.FlvPackager.setAvcTag(finalBuff, 0, false, frameType == 5, realDataLength);
        ResFlvData resFlvData = new ResFlvData();
        resFlvData.droppable = true;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) timeMs;
        resFlvData.flvTagType = FLV_TAGTYPE_VIDEO;
        resFlvData.videoFrameType = frameType;
        mDataCollecter.collect(resFlvData, FLV_TAGTYPE_VIDEO);
    }

    public boolean getStatus() {
        return !quit.get();
    }

    public void quit() {
        quit.set(true);
    }
}