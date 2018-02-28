package net.yrom.screenrecorder.core;

/**
 * Created by lake on 16-3-18.
 */
public class ResAudioBuff {
    public boolean isReadyToFill;
    public int audioFormat = -1;
    public byte[] buff;

    public ResAudioBuff(int audioFormat, int size) {
        isReadyToFill = true;
        this.audioFormat = audioFormat;
        buff = new byte[size];
    }
}
