package memorygame.client;

import javax.sound.sampled.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class StreamingMusicTest {
    public static void main(String[] args) throws Exception {
        Path wav = Files.createTempFile("music-loop-test", ".wav");
        AudioFormat format = new AudioFormat(22050, 16, 2, true, false);
        try (AudioInputStream input = new AudioInputStream(new ByteArrayInputStream(new byte[8192]), format, 2048)) {
            AudioSystem.write(input, AudioFileFormat.Type.WAVE, wav.toFile());
        }
        AtomicInteger opens = new AtomicInteger(), writes = new AtomicInteger(), closes = new AtomicInteger();
        AtomicInteger bytes = new AtomicInteger(), drains = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> failure = new java.util.concurrent.atomic.AtomicReference<>();
        StreamingMusic music = new StreamingMusic(wav.toUri().toURL(), f -> (SourceDataLine) Proxy.newProxyInstance(
                StreamingMusicTest.class.getClassLoader(), new Class<?>[]{SourceDataLine.class}, (proxy, method, values) -> {
                    switch (method.getName()) {
                        case "open" -> { opens.incrementAndGet(); bytes.set(0); }
                        case "close" -> closes.incrementAndGet();
                        case "write" -> {
                            writes.incrementAndGet(); Thread.sleep(3);
                            int accepted = Math.min(1024, (Integer) values[2]); // Exercise partial writes.
                            bytes.addAndGet(accepted); return accepted;
                        }
                        case "drain" -> {
                            if (bytes.get() != 8192) failure.set("EOF before every PCM byte was written: " + bytes.get());
                            drains.incrementAndGet();
                        }
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == float.class) return 0f;
                    return null;
                }));
        try {
            music.start(); await(() -> drains.get() >= 3); // Complete EOF loops, including partial writes.
            music.stop(); Thread.sleep(100); int paused = writes.get(); Thread.sleep(50);
            if (writes.get() != paused) throw new AssertionError("Stop continued playback");
            music.start(); await(() -> writes.get() > paused);
        } finally { music.close(); }
        Thread.sleep(100); int stopped = writes.get(); Thread.sleep(50);
        if (writes.get() != stopped || closes.get() < opens.get()) throw new AssertionError("Close leaked playback");
        if (failure.get() != null) throw new AssertionError(failure.get());
        // The audio README specifies the first five minutes, PCM 16-bit stereo at 22050 Hz.
        var resource = StreamingMusic.class.getResource("audio/lobby.wav");
        if (resource == null) throw new AssertionError("Packaged lobby.wav missing");
        try (AudioInputStream full = AudioSystem.getAudioInputStream(resource)) {
            AudioFormat actual = full.getFormat();
            if (!AudioFormat.Encoding.PCM_SIGNED.equals(actual.getEncoding()) || actual.getSampleRate() != 22050
                    || actual.getSampleSizeInBits() != 16 || actual.getChannels() != 2 || actual.getFrameSize() != 4)
                throw new AssertionError("Unexpected lobby PCM format: " + actual);
            if (full.getFrameLength() != 300L * 22050) throw new AssertionError("Expected exactly five minutes of audio");
            long total = 0; boolean audible = false; byte[] buffer = new byte[4096];
            for (int n; (n = full.read(buffer)) != -1;) {
                total += n;
                for (int i = 0; i < n; i++) audible |= buffer[i] != 0;
            }
            if (total != full.getFrameLength() * actual.getFrameSize() || !audible)
                throw new AssertionError("Truncated or silent PCM payload: " + total);
        }
        Files.deleteIfExists(wav);
        System.out.println("STREAMING_MUSIC_OK packaged300Seconds fullPCM partialWrites drain loop stop restart close");
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime()+2_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime()>deadline) throw new AssertionError("Audio worker timed out");
            Thread.sleep(5);
        }
    }
}
