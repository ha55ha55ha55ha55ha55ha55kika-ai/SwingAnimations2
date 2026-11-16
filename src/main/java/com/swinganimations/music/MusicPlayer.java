package com.swinganimations.music;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.EXTThreadLocalContext;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Плеер MP3-потоков. Декодирует JLayer'ом и выводит звук через OpenAL (то, чем играет сама игра; Java Sound на Android/Pojav недоступен)
 * в отдельном потоке с собственным контекстом, поэтому не мешает игре и её звукам. Очередь, пауза, громкость, автопереход к следующему треку.
 * Весь публичный API потокобезопасен; состояния читаются из потока рендера без блокировок.
 */
public final class MusicPlayer {
    public enum State { IDLE, LOADING, PLAYING, PAUSED, ERROR }

    private static final AtomicInteger GEN = new AtomicInteger();
    private static volatile State state = State.IDLE;
    private static volatile List<Track> queue = List.of();
    private static volatile int index = -1;
    private static volatile long positionMs;
    private static volatile String error = "";
    private static volatile float volume = 0.6f;
    private static volatile boolean paused;
    private static volatile InputStream currentIn;

    private MusicPlayer() {}

    // ---------------- состояние ----------------

    public static State state() { return state; }

    public static Track current() {
        List<Track> q = queue;
        int i = index;
        return i >= 0 && i < q.size() ? q.get(i) : null;
    }

    public static int index() { return index; }

    public static long positionMs() { return positionMs; }

    public static String error() { return error; }

    public static float volume() { return volume; }

    public static boolean isActive() {
        State s = state;
        return s == State.LOADING || s == State.PLAYING || s == State.PAUSED;
    }

    public static void setVolume(float v) { volume = Math.max(0f, Math.min(1f, v)); }

    /** Время в формате m:ss. */
    public static String fmt(long ms) {
        long s = Math.max(0L, ms / 1000L);
        return (s / 60L) + ":" + (s % 60L < 10L ? "0" : "") + (s % 60L);
    }

    // ---------------- управление ----------------

    /** Запускает очередь с позиции start. По окончании трека автоматически играет следующий. */
    public static synchronized void play(List<Track> list, int start) {
        queue = List.copyOf(list);
        startIndex(start);
    }

    public static synchronized void next() {
        if (queue.isEmpty()) return;
        startIndex(index + 1 < queue.size() ? index + 1 : 0);
    }

    public static synchronized void prev() {
        if (queue.isEmpty()) return;
        if (positionMs > 3000L && index >= 0) startIndex(index);
        else startIndex(Math.max(0, index - 1));
    }

    public static synchronized void togglePause() {
        State s = state;
        if (s == State.PLAYING) {
            paused = true;
            state = State.PAUSED;
        } else if (s == State.PAUSED) {
            paused = false;
            state = State.PLAYING;
        } else if (!queue.isEmpty() && index >= 0) {
            startIndex(index); // после остановки или ошибки: запускаем текущий трек заново
        }
    }

    public static synchronized void stop() {
        GEN.incrementAndGet();
        closeCurrent();
        paused = false;
        positionMs = 0L;
        state = State.IDLE;
    }

    private static void startIndex(int i) {
        List<Track> q = queue;
        if (i < 0 || i >= q.size()) {
            stop();
            return;
        }
        int gen = GEN.incrementAndGet();
        closeCurrent();
        index = i;
        positionMs = 0L;
        paused = false;
        error = "";
        state = State.LOADING;
        Track t = q.get(i);
        Thread th = new Thread(() -> run(t, gen), "SwingMusic-Player");
        th.setDaemon(true);
        th.start();
    }

    private static void closeCurrent() {
        InputStream in = currentIn;
        currentIn = null;
        if (in != null) {
            try { in.close(); } catch (IOException ignored) { }
        }
    }

    // ---------------- вывод звука через OpenAL ----------------

    /**
     * Потоковый вывод PCM в OpenAL. По возможности создаёт свой контекст, привязанный только к этому потоку
     * (ALC_EXT_thread_local_context), чтобы не трогать звуки игры. Если расширения нет, используется общий контекст игры.
     */
    private static final class AlOut {
        private static final int BUFFERS = 6;
        private long dev, ctx;
        private boolean ownDevice, threadCtx, ownGlobal;
        private int source;
        private final int[] bufs = new int[BUFFERS];
        private final ArrayDeque<Integer> free = new ArrayDeque<>();
        private int format, rate;
        private final ShortBuffer scratch = BufferUtils.createShortBuffer(65536);

        void open(int rate, int channels) {
            this.rate = rate;
            this.format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            long cur = ALC10.alcGetCurrentContext();
            dev = cur != 0L ? ALC10.alcGetContextsDevice(cur) : 0L;
            if (dev == 0L) {
                dev = ALC10.alcOpenDevice((ByteBuffer) null);
                ownDevice = true;
            }
            if (dev == 0L) throw new IllegalStateException("OpenAL: нет аудиоустройства");
            ALCCapabilities alcCaps = ALC.createCapabilities(dev);
            if (alcCaps.ALC_EXT_thread_local_context) {
                ctx = ALC10.alcCreateContext(dev, (IntBuffer) null);
                if (ctx != 0L && EXTThreadLocalContext.alcSetThreadContext(ctx)) {
                    AL.createCapabilities(alcCaps);
                    threadCtx = true;
                }
            }
            if (!threadCtx && cur == 0L) {
                if (ctx == 0L) ctx = ALC10.alcCreateContext(dev, (IntBuffer) null);
                ALC10.alcMakeContextCurrent(ctx);
                AL.createCapabilities(alcCaps);
                ownGlobal = true;
            }
            source = AL10.alGenSources();
            for (int i = 0; i < BUFFERS; i++) {
                bufs[i] = AL10.alGenBuffers();
                free.add(bufs[i]);
            }
        }

        void gain(float g) { AL10.alSourcef(source, AL10.AL_GAIN, g); }

        private void reclaim() {
            int done = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
            while (done-- > 0) free.add(AL10.alSourceUnqueueBuffers(source));
        }

        /** Кладёт n сэмплов в очередь; ждёт свободный буфер, пока трек не сменили. */
        boolean write(short[] pcm, int n, int gen) throws InterruptedException {
            int off = 0;
            while (off < n) {
                if (gen != GEN.get()) return false;
                reclaim();
                Integer buf = free.poll();
                if (buf == null) {
                    Thread.sleep(8L);
                    gain(volume * volume);
                    continue;
                }
                int len = Math.min(n - off, scratch.capacity());
                scratch.clear();
                scratch.put(pcm, off, len).flip();
                AL10.alBufferData(buf, format, scratch, rate);
                AL10.alSourceQueueBuffers(source, buf);
                off += len;
                if (!paused && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) AL10.alSourcePlay(source);
            }
            return true;
        }

        void pause() { AL10.alSourcePause(source); }

        void resume() { AL10.alSourcePlay(source); }

        /** Ждёт, пока доиграет хвост очереди. */
        void drain(int gen) throws InterruptedException {
            while (gen == GEN.get() && !paused && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {
                gain(volume * volume);
                Thread.sleep(30L);
            }
        }

        void close() {
            try {
                if (source != 0) {
                    AL10.alSourceStop(source);
                    AL10.alSourcei(source, AL10.AL_BUFFER, 0);
                    AL10.alDeleteSources(source);
                }
                for (int b : bufs) if (b != 0) AL10.alDeleteBuffers(b);
            } catch (Throwable ignored) { }
            try {
                if (threadCtx) {
                    EXTThreadLocalContext.alcSetThreadContext(0L);
                    AL.setCurrentThread(null);
                    ALC10.alcDestroyContext(ctx);
                } else if (ownGlobal) {
                    ALC10.alcMakeContextCurrent(0L);
                    ALC10.alcDestroyContext(ctx);
                }
                if (ownDevice && dev != 0L) ALC10.alcCloseDevice(dev);
            } catch (Throwable ignored) { }
        }
    }

    // ---------------- поток воспроизведения ----------------

    private static void run(Track t, int gen) {
        AlOut out = null;
        InputStream in = null;
        try {
            in = new BufferedInputStream(Http.open(t.streamUrl()), 64 * 1024);
            currentIn = in;
            if (gen != GEN.get()) return;
            Bitstream bs = new Bitstream(in);
            Decoder dec = new Decoder();
            short[] acc = new short[32768];
            int accN = 0;
            Header h;
            while (gen == GEN.get() && (h = bs.readFrame()) != null) {
                SampleBuffer sb = (SampleBuffer) dec.decodeFrame(h, bs);
                if (out == null) {
                    out = new AlOut();
                    out.open(dec.getOutputFrequency(), dec.getOutputChannels());
                    out.gain(volume * volume);
                }
                short[] pcm = sb.getBuffer();
                int n = sb.getBufferLength();
                if (accN + n > acc.length) {
                    if (!out.write(acc, accN, gen)) break;
                    accN = 0;
                }
                System.arraycopy(pcm, 0, acc, accN, n);
                accN += n;
                // копим около 0,1 с звука перед отправкой, чтобы не плодить мелкие буферы
                if (accN >= 8192) {
                    if (paused) {
                        out.pause();
                        while (paused && gen == GEN.get()) Thread.sleep(40L);
                        if (gen != GEN.get()) break;
                        out.resume();
                    }
                    if (!out.write(acc, accN, gen)) break;
                    accN = 0;
                }
                positionMs += (long) h.ms_per_frame();
                if (state == State.LOADING && gen == GEN.get()) state = State.PLAYING;
                bs.closeFrame();
            }
            if (out != null && accN > 0 && gen == GEN.get()) out.write(acc, accN, gen);
            if (out != null && gen == GEN.get()) out.drain(gen);
            if (gen == GEN.get()) finished(gen);
        } catch (Throwable e) {
            if (gen == GEN.get()) {
                String m = e.getMessage();
                error = e.getClass().getSimpleName() + (m == null || m.isEmpty() ? "" : ": " + m);
                state = State.ERROR;
            }
        } finally {
            if (out != null) out.close();
            if (in != null) {
                try { in.close(); } catch (IOException ignored) { }
            }
            if (currentIn == in) currentIn = null;
        }
    }

    /** Трек доиграл до конца: идём дальше по очереди, на последнем останавливаемся. */
    private static synchronized void finished(int gen) {
        if (gen != GEN.get()) return;
        if (index + 1 < queue.size()) startIndex(index + 1);
        else {
            state = State.IDLE;
            positionMs = 0L;
        }
    }
}
