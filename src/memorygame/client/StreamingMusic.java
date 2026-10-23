package memorygame.client;

import javax.sound.sampled.*;
import java.net.URL;

/** Streams long WAV tracks with a fixed-size buffer instead of loading a whole Clip. */
final class StreamingMusic implements AutoCloseable {
    @FunctionalInterface interface LineFactory { SourceDataLine create(AudioFormat format) throws LineUnavailableException; }
    private final LineFactory lines;
    private final URL source;
    private final Object state = new Object();
    private final Thread worker;
    private volatile boolean requested, closed;
    private volatile long revision;
    private volatile SourceDataLine activeLine;

    StreamingMusic(URL source) {
        this(source, AudioSystem::getSourceDataLine);
    }
    StreamingMusic(URL source, LineFactory lines) {
        this.source = source; this.lines = lines;
        worker = new Thread(this::run, "memory-music-stream"); worker.setDaemon(true); worker.start();
    }
    void start() {
        synchronized (state) { if (!closed && !requested) { requested = true; revision++; state.notifyAll(); } }
    }
    void stop() {
        synchronized (state) { if (requested) { requested = false; revision++; } state.notifyAll(); }
    }
    private boolean playing(long version) { return !closed && requested && revision == version; }
    private void run() {
        while (!closed) {
            long version;
            synchronized (state) {
                while (!closed && !requested) {
                    try { state.wait(); } catch (InterruptedException e) { if (closed) return; }
                }
                if (closed) return;
                version = revision;
            }
            try (AudioInputStream input = AudioSystem.getAudioInputStream(source)) {
                SourceDataLine line = lines.create(input.getFormat());
                activeLine = line;
                try {
                    line.open(input.getFormat(), 16_384);
                    if (!playing(version)) continue;
                    if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                        gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), -12f)));
                    }
                    line.start();
                    byte[] buffer = new byte[4096];
                    while (playing(version)) {
                        int count = input.read(buffer, 0, buffer.length);
                        if (count < 0) { if (playing(version)) line.drain(); break; }
                        int offset = 0;
                        while (offset < count && playing(version)) {
                            int written = line.write(buffer, offset, count - offset);
                            if (written <= 0) break;
                            offset += written;
                        }
                    }
                } finally { line.stop(); line.flush(); line.close(); activeLine = null; }
                // End of file reopens from the beginning while playback is still requested.
            } catch (Exception e) {
                if (!closed) System.err.println("Khong the phat nhac nen: " + e.getMessage());
                synchronized (state) { if (revision == version) requested = false; }
            }
        }
    }
    @Override public void close() {
        synchronized (state) { closed = true; requested = false; revision++; state.notifyAll(); }
        SourceDataLine line = activeLine;
        if (line != null) line.close();
        worker.interrupt();
    }
}
