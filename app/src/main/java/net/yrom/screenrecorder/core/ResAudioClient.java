package net.yrom.screenrecorder.core;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import net.yrom.screenrecorder.rtmp.ResFlvDataCollecter;
import net.yrom.screenrecorder.tools.LogTools;


/**
 * Created by lake on 16-5-24.
 */
public class ResAudioClient {
    ResCoreParameters resCoreParameters;
    private final Object syncOp = new Object();
    private AudioRecordThread audioRecordThread;
    private AudioRecord audioRecord;
    private byte[] audioBuffer;
    private ResSoftAudioCore softAudioCore;

    public ResAudioClient(ResCoreParameters parameters) {
        resCoreParameters = parameters;
    }

    public boolean prepare() {
        synchronized (syncOp) {
            resCoreParameters.audioBufferQueueNum = 5;
            softAudioCore = new ResSoftAudioCore(resCoreParameters);
            if (!softAudioCore.prepare()) {
                LogTools.e("ResAudioClient,prepare");
                return false;
            }
            resCoreParameters.audioRecoderFormat = AudioFormat.ENCODING_PCM_16BIT;
            resCoreParameters.audioRecoderChannelConfig = AudioFormat.CHANNEL_IN_MONO;
            resCoreParameters.audioRecoderSliceSize = resCoreParameters.mediacodecAACSampleRate / 10;
            resCoreParameters.audioRecoderBufferSize = resCoreParameters.audioRecoderSliceSize * 2;
            resCoreParameters.audioRecoderSource = MediaRecorder.AudioSource.DEFAULT;
            resCoreParameters.audioRecoderSampleRate = resCoreParameters.mediacodecAACSampleRate;
            prepareAudio();
            return true;
        }
    }

    public boolean start(ResFlvDataCollecter flvDataCollecter) {
        synchronized (syncOp) {
            softAudioCore.start(flvDataCollecter);
            audioRecord.startRecording();
            audioRecordThread = new AudioRecordThread();
            audioRecordThread.start();
            LogTools.d("ResAudioClient,start()");
            return true;
        }
    }

    public boolean stop() {
        synchronized (syncOp) {
            audioRecordThread.quit();
            try {
                audioRecordThread.join();
            } catch (InterruptedException ignored) {
            }
            softAudioCore.stop();
            audioRecordThread = null;
            audioRecord.stop();
            return true;
        }
    }

    public boolean destroy() {
        synchronized (syncOp) {
            audioRecord.release();
            return true;
        }
    }
    public void setSoftAudioFilter(BaseSoftAudioFilter baseSoftAudioFilter) {
        softAudioCore.setAudioFilter(baseSoftAudioFilter);
    }
    public BaseSoftAudioFilter acquireSoftAudioFilter() {
        return softAudioCore.acquireAudioFilter();
    }

    public void releaseSoftAudioFilter() {
        softAudioCore.releaseAudioFilter();
    }

    private boolean prepareAudio() {
        int minBufferSize = AudioRecord.getMinBufferSize(resCoreParameters.audioRecoderSampleRate,
                resCoreParameters.audioRecoderChannelConfig,
                resCoreParameters.audioRecoderFormat);
        audioRecord = new AudioRecord(resCoreParameters.audioRecoderSource,
                resCoreParameters.audioRecoderSampleRate,
                resCoreParameters.audioRecoderChannelConfig,
                resCoreParameters.audioRecoderFormat,
                minBufferSize * 5);
        audioBuffer = new byte[resCoreParameters.audioRecoderBufferSize];
        if (AudioRecord.STATE_INITIALIZED != audioRecord.getState()) {
            LogTools.e("audioRecord.getState()!=AudioRecord.STATE_INITIALIZED!");
            return false;
        }
        if (AudioRecord.SUCCESS != audioRecord.setPositionNotificationPeriod(resCoreParameters.audioRecoderSliceSize)) {
            LogTools.e("AudioRecord.SUCCESS != audioRecord.setPositionNotificationPeriod(" + resCoreParameters.audioRecoderSliceSize + ")");
            return false;
        }
        return true;
    }

    class AudioRecordThread extends Thread {
        private boolean isRunning = true;

        AudioRecordThread() {
            isRunning = true;
        }

        public void quit() {
            isRunning = false;
        }

        @Override
        public void run() {
            LogTools.d("AudioRecordThread,tid=" + Thread.currentThread().getId());
            while (isRunning) {
                int size = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                if (isRunning && softAudioCore != null && size > 0) {
                    softAudioCore.queueAudio(audioBuffer);
                }
            }
        }
    }
}
