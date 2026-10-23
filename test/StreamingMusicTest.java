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
        StreamingMusic music = new StreamingMusic(wav.toUri().toURL(), f -> (SourceDataLine) Proxy.newProxyInstance(
                StreamingMusicTest.class.getClassLoader(), new Class<?>[]{SourceDataLine.class}, (proxy, method, values) -> {
                    switch (method.getName()) {
                        case "open" -> opens.incrementAndGet();
                        case "close" -> closes.incrementAndGet();
                        case "write" -> { writes.incrementAndGet(); Thread.sleep(3); return (Integer) values[2]; }
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == float.class) return 0f;
                    return null;
                }));
        try {
            music.start(); await(() -> opens.get() >= 3); // Full EOF loops, without loading the whole recording.
            music.stop(); Thread.sleep(100); int paused = writes.get(); Thread.sleep(50);
            if (writes.get() != paused) throw new AssertionError("Stop continued playback");
            music.start(); await(() -> writes.get() > paused);
        } finally { music.close(); }
        Thread.sleep(100); int stopped = writes.get(); Thread.sleep(50);
        if (writes.get() != stopped || closes.get() < opens.get()) throw new AssertionError("Close leaked playback");
        try (AudioInputStream full = AudioSystem.getAudioInputStream(Path.of("src/memorygame/client/audio/lobby.wav").toFile())) {
            double seconds = full.getFrameLength()/full.getFormat().getFrameRate();
            if (Math.abs(seconds-3638) > 2) throw new AssertionError("Recording truncated");
        }
        System.out.println("STREAMING_MUSIC_OK fullDuration loop stop restart close");
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime()+2_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime()>deadline) throw new AssertionError("Audio worker timed out");
            Thread.sleep(5);
        }
    }
}
