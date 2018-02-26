package net.yrom.screenrecorder.task;

import android.text.TextUtils;

import net.yrom.screenrecorder.core.RESCoreParameters;
import net.yrom.screenrecorder.rtmp.FLvMetaData;
import net.yrom.screenrecorder.rtmp.RESFlvData;
import net.yrom.screenrecorder.rtmp.RtmpClient;
import net.yrom.screenrecorder.tools.LogTools;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by raomengyang on 12/03/2017.
 */
public class RtmpStreamingSender implements Runnable {
    private static final String TAG = "RtmpStreamingSender";

    private static class STATE {
        private static final int START = 0;
        private static final int RUNNING = 1;
        private static final int STOPPED = 2;
    }

    private volatile int state;

    private String mRtmpAddr = null;

    private final Object lock = new Object();
    private static final int MAX_QUEUE_CAPACITY = 50;
    private LinkedBlockingDeque<RESFlvData> frameQueue = new LinkedBlockingDeque<>(MAX_QUEUE_CAPACITY);
    private AtomicBoolean quit = new AtomicBoolean(false);
    private FLvMetaData fLvMetaData;
    private RESCoreParameters coreParameters;

    private long jniRtmpPointer = 0;
    private int maxQueueLength = 50;
    private int writeMsgNum = 0;

    public RtmpStreamingSender() {
        coreParameters = new RESCoreParameters();
        coreParameters.mediacodecAACBitRate = RESFlvData.AAC_BITRATE;
        coreParameters.mediacodecAACSampleRate = RESFlvData.AAC_SAMPLE_RATE;
        coreParameters.mediacodecAVCFrameRate = RESFlvData.FPS;
        coreParameters.videoWidth = RESFlvData.VIDEO_WIDTH;
        coreParameters.videoHeight = RESFlvData.VIDEO_HEIGHT;

        fLvMetaData = new FLvMetaData(coreParameters);
    }

    @Override
    public void run() {
        while (!quit.get()) {
            if (frameQueue.size() > 0) {
                switch (state) {
                    case STATE.START:
                        LogTools.d("RESRtmpSender,WorkHandler,tid=" + Thread.currentThread().getId());
                        if (TextUtils.isEmpty(mRtmpAddr)) {
                            LogTools.e("rtmp address is null!");
                            break;
                        }
                        jniRtmpPointer = RtmpClient.open(mRtmpAddr, true);
                        if (jniRtmpPointer != 0) {
                            String serverIpAddr = RtmpClient.getIpAddr(jniRtmpPointer);
                            LogTools.d("server ip address = " + serverIpAddr);

                            byte[] MetaData = fLvMetaData.getMetaData();
                            RtmpClient.write(jniRtmpPointer, MetaData, MetaData.length, RESFlvData.FLV_RTMP_PACKET_TYPE_INFO, 0);

                            state = STATE.RUNNING;
                        }
                        break;
                    case STATE.RUNNING:
                        synchronized (lock) {
                            --writeMsgNum;
                        }
                        RESFlvData flvData = frameQueue.pop();
                        if (writeMsgNum >= (maxQueueLength * 2 / 3) && flvData.flvTagType == RESFlvData.FLV_RTMP_PACKET_TYPE_VIDEO && flvData.droppable) {
                            LogTools.d("senderQueue is crowded, abort a frame");
                            break;
                        }

                        int result = RtmpClient.write(jniRtmpPointer, flvData.byteBuffer, flvData.byteBuffer.length, flvData.flvTagType, flvData.dts);
                        if (result == 0) {
                            if (flvData.flvTagType == RESFlvData.FLV_RTMP_PACKET_TYPE_VIDEO) {
                                LogTools.d("video frame sent = " + flvData.size);
                            } else {
                                LogTools.d("audio frame sent = " + flvData.size);
                            }
                        } else {
                            LogTools.e("writeError = " + result);
                        }

                        break;
                    case STATE.STOPPED:
                        result = RtmpClient.close(jniRtmpPointer);
                        LogTools.e("close result = " + result);
                        break;
                }
            }
        }
        int result = RtmpClient.close(jniRtmpPointer);
        LogTools.e("close result = " + result);
    }

    public void sendStart(String rtmpAddr) {
        synchronized (lock) {
            writeMsgNum = 0;
        }
        mRtmpAddr = rtmpAddr;
        state = STATE.START;
    }

    public void sendStop() {
        synchronized (lock) {
            writeMsgNum = 0;
        }
        state = STATE.STOPPED;
    }

    public void sendFood(RESFlvData flvData, int type) {
        synchronized (lock) {
            if (writeMsgNum <= maxQueueLength) {
                frameQueue.add(flvData);
                ++writeMsgNum;
            } else {
                LogTools.d("senderQueue is full, abort a frame");
            }
        }
    }

    public final void quit() {
        quit.set(true);
    }
}