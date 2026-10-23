package memorygame.client;

import javax.sound.sampled.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;

final class SoundManager implements AutoCloseable {
    private static final float RATE = 22_050f;
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "memory-audio"); t.setDaemon(true); return t;
    });
    private volatile boolean musicEnabled = true;
    private volatile boolean effectsEnabled = true;
    private volatile boolean lobbyRequested;
    private volatile Clip music, click, flip, correct, wrong;
    private volatile StreamingMusic streamedMusic;

    SoundManager() {
        loader.execute(() -> {
            URL source = musicSource();
            if (source != null) streamedMusic = new StreamingMusic(source);
            else music = music();
            click = notes(new double[]{720}, new int[]{55}, .20);
            flip = sweep(320, 680, 120, .22);
            correct = notes(new double[]{523, 659, 784}, new int[]{90, 90, 160}, .24);
            wrong = notes(new double[]{260, 195, 146}, new int[]{100, 110, 190}, .22);
            if (lobbyRequested) startLobbyMusic();
        });
    }

    boolean isMusicEnabled() { return musicEnabled; }
    boolean isEffectsEnabled() { return effectsEnabled; }
    void setEffectsEnabled(boolean enabled) { effectsEnabled = enabled; }
    void setMusicEnabled(boolean enabled) {
        musicEnabled = enabled;
        if (!enabled) stopLobbyMusic();
    }
    void startLobbyMusic() {
        lobbyRequested = true;
        if (musicEnabled && streamedMusic != null) streamedMusic.start();
        Clip c = music;
        if (musicEnabled && c != null && !c.isRunning()) { c.setFramePosition(0); c.loop(Clip.LOOP_CONTINUOUSLY); }
    }
    void stopLobbyMusic() {
        lobbyRequested = false;
        if (streamedMusic != null) streamedMusic.stop();
        if (music != null) music.stop();
    }
    void playClick() { play(click); }
    void playFlip() { play(flip); }
    void playCorrect() { play(correct); }
    void playWrong() { play(wrong); }

    private void play(Clip c) {
        if (!effectsEnabled || c == null) return;
        synchronized (c) { c.stop(); c.setFramePosition(0); c.start(); }
    }

    private static Clip music() {
        int samples = (int) (RATE * 12), chord;
        byte[] data = new byte[samples * 2];
        double[][] chords = {{220,262,330},{175,220,262},{196,247,294},{165,220,262}};
        for (int i = 0; i < samples; i++) {
            double t = i / RATE, local = t % 3;
            chord = Math.min(3, (int) (t / 3));
            double envelope = Math.min(1, local / .35) * Math.min(1, (3 - local) / .5), value = 0;
            for (double f : chords[chord]) value += Math.sin(2 * Math.PI * f * t) + .2 * Math.sin(4 * Math.PI * f * t);
            write(data, i, value / 3.6 * envelope * .12);
        }
        Clip c = open(data);
        if (c != null && c.isControlSupported(FloatControl.Type.MASTER_GAIN))
            ((FloatControl)c.getControl(FloatControl.Type.MASTER_GAIN)).setValue(-8f);
        return c;
    }

    private static URL musicSource() {
        URL source = SoundManager.class.getResource("audio/lobby.wav");
        if (source != null) return source;
        Path local = Path.of("src", "memorygame", "client", "audio", "lobby.wav");
        try { return Files.isRegularFile(local) ? local.toUri().toURL() : null; }
        catch (Exception e) { return null; }
    }

    private static Clip notes(double[] frequencies, int[] durations, double volume) {
        int total = 0; for (int d : durations) total += d;
        byte[] data = new byte[(int)(RATE * total / 1000) * 2]; int cursor = 0;
        for (int n = 0; n < frequencies.length; n++) {
            int count = (int)(RATE * durations[n] / 1000);
            for (int i = 0; i < count; i++, cursor++) {
                double env = Math.sin(Math.PI * i / Math.max(1, count - 1));
                write(data, cursor, Math.sin(2 * Math.PI * frequencies[n] * cursor / RATE) * env * volume);
            }
        }
        return open(data);
    }

    private static Clip sweep(double from, double to, int ms, double volume) {
        int count = (int)(RATE * ms / 1000); byte[] data = new byte[count * 2]; double phase = 0;
        for (int i = 0; i < count; i++) {
            double p = i / (double)Math.max(1, count - 1);
            phase += 2 * Math.PI * (from + (to - from) * p) / RATE;
            write(data, i, Math.sin(phase) * Math.sin(Math.PI * p) * volume);
        }
        return open(data);
    }

    private static Clip open(byte[] data) {
        try { Clip c = AudioSystem.getClip(); c.open(new AudioFormat(RATE,16,1,true,false), data,0,data.length); return c; }
        catch (Exception ignored) { return null; }
    }
    private static void write(byte[] data, int index, double value) {
        short s = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(value * Short.MAX_VALUE)));
        data[index*2] = (byte)(s & 255); data[index*2+1] = (byte)((s >>> 8) & 255);
    }
    @Override public void close() {
        stopLobbyMusic();
        if (streamedMusic != null) streamedMusic.close();
        for (Clip c : new Clip[]{music,click,flip,correct,wrong}) if (c != null) c.close();
        loader.shutdownNow();
    }
}
