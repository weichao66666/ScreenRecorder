package net.yrom.screenrecorder.task;

import android.media.MediaCodec;
import android.util.Log;

import net.yrom.screenrecorder.core.Packager;
import net.yrom.screenrecorder.rtmp.ResFlvData;
import net.yrom.screenrecorder.rtmp.ResFlvDataCollecter;

import java.nio.ByteBuffer;

import static android.content.ContentValues.TAG;
import static net.yrom.screenrecorder.rtmp.ResFlvData.FLV_TAGTYPE_AUDIO;

/**
 * Created by lakeinchina on 26/05/16.
 */
public class AudioSenderThread extends Thread {
    private static final long WAIT_TIME = 5000;//1ms;
    private MediaCodec.BufferInfo eInfo;
    private long startTime = 0;
    private MediaCodec dstAudioEncoder;
    private ResFlvDataCollecter dataCollecter;

    public AudioSenderThread(String name, MediaCodec encoder, ResFlvDataCollecter flvDataCollecter) {
        super(name);
        eInfo = new MediaCodec.BufferInfo();
        startTime = 0;
        dstAudioEncoder = encoder;
        dataCollecter = flvDataCollecter;
    }

    private boolean shouldQuit = false;

    public void quit() {
        shouldQuit = true;
        this.interrupt();
    }

    @Override
    public void run() {
        while (!shouldQuit) {
            int eobIndex = dstAudioEncoder.dequeueOutputBuffer(eInfo, WAIT_TIME);
            switch (eobIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "AudioSenderThread,MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
//                        LogTools.d("AudioSenderThread,MediaCodec.INFO_TRY_AGAIN_LATER");
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG, "AudioSenderThread,MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:" +
                            dstAudioEncoder.getOutputFormat().toString());
                    ByteBuffer csd0 = dstAudioEncoder.getOutputFormat().getByteBuffer("csd-0");
                    sendAudioSpecificConfig(0, csd0);
                    break;
                default:
                    Log.d(TAG, "AudioSenderThread,MediaCode,eobIndex=" + eobIndex);
                    if (startTime == 0) {
                        startTime = eInfo.presentationTimeUs / 1000;
                    }
                    /**
                     * we send audio SpecificConfig already in INFO_OUTPUT_FORMAT_CHANGED
                     * so we ignore MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                     */
                    if (eInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && eInfo.size != 0) {
                        ByteBuffer realData = dstAudioEncoder.getOutputBuffers()[eobIndex];
                        realData.position(eInfo.offset);
                        realData.limit(eInfo.offset + eInfo.size);
                        sendRealData((eInfo.presentationTimeUs / 1000) - startTime, realData);
                    }
                    dstAudioEncoder.releaseOutputBuffer(eobIndex, false);
                    break;
            }
        }
        eInfo = null;
    }

    private void sendAudioSpecificConfig(long tms, ByteBuffer realData) {
        int packetLen = Packager.FlvPackager.FLV_AUDIO_TAG_HEADER_LENGTH +
                realData.remaining();
        byte[] finalBuff = new byte[packetLen];
        realData.get(finalBuff, Packager.FlvPackager.FLV_AUDIO_TAG_HEADER_LENGTH,
                realData.remaining());
        Packager.FlvPackager.setAacTag(finalBuff,
                0,
                true);
        ResFlvData resFlvData = new ResFlvData();
        resFlvData.droppable = false;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) tms;
        resFlvData.flvTagType = ResFlvData.FLV_TAGTYPE_AUDIO;
        dataCollecter.collect(resFlvData, FLV_TAGTYPE_AUDIO);
    }

    private void sendRealData(long tms, ByteBuffer realData) {
        int packetLen = Packager.FlvPackager.FLV_AUDIO_TAG_HEADER_LENGTH +
                realData.remaining();
        byte[] finalBuff = new byte[packetLen];
        realData.get(finalBuff, Packager.FlvPackager.FLV_AUDIO_TAG_HEADER_LENGTH,
                realData.remaining());
        Packager.FlvPackager.setAacTag(finalBuff,
                0,
                false);
        ResFlvData resFlvData = new ResFlvData();
        resFlvData.droppable = true;
        resFlvData.byteBuffer = finalBuff;
        resFlvData.size = finalBuff.length;
        resFlvData.dts = (int) tms;
        resFlvData.flvTagType = ResFlvData.FLV_TAGTYPE_AUDIO;
        dataCollecter.collect(resFlvData, FLV_TAGTYPE_AUDIO);
    }
}
