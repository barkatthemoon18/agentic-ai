package com.fuad.media.context;

import com.fuad.audio.output.AudioOutputProvider;
import com.fuad.audio.output.AudioOutputSnapshot;
import com.fuad.media.MediaPlaybackState;
import com.fuad.media.MediaSessionProvider;
import com.fuad.media.MediaSessionSnapshot;
import com.fuad.media.control.*;
import com.fuad.media.enrichment.MediaEnrichmentProvider;
import com.fuad.media.enrichment.MediaEnrichmentSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class MediaContextService implements MediaContextProvider, MediaActionHandler, MediaActionExecutor, AutoCloseable {
    private static final long CORE_REFRESH_MILLIS = 500L;
    private static final long ENRICHMENT_REFRESH_MILLIS = 1500L;
    private static final long PROCESS_REFRESH_NANOS = TimeUnit.SECONDS.toNanos(3);
    private static final long CLOSE_TIMEOUT_SECONDS = 5L;
    private final MediaSessionProvider sessions;
    private final AudioOutputProvider audio;
    private final MediaEnrichmentProvider enrichment;
    private final Supplier<ProcessPresence> processProbe;
    private final Clock clock;
    private final AtomicReference<MediaContextSnapshot> current = new AtomicReference<>(MediaContextSnapshot.unavailable());
    private final Object lifecycleLock = new Object();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ConcurrentMap<CompletableFuture<MediaActionResult>, MediaAction> pending =  new ConcurrentHashMap<>();
    private final AtomicReference<Thread> coreWorker = new AtomicReference<>();
    private final AtomicReference<Thread> enrichmentWorker = new AtomicReference<>();
    private final ScheduledExecutorService coreExecutor = Executors.newSingleThreadScheduledExecutor(task ->
            daemon(task, "ares-media-control", coreWorker));
    private final ScheduledExecutorService enrichmentExecutor = Executors.newSingleThreadScheduledExecutor(task ->
            daemon(task, "ares-media-enrichment", enrichmentWorker));
    private ScheduledFuture<?> coreTask;
    private ScheduledFuture<?> enrichmentTask;
    private long nextProcessRefreshNanos;

    public MediaContextService(MediaSessionProvider sessions, AudioOutputProvider audio, MediaEnrichmentProvider enrichment) {
        this(sessions, audio, enrichment, Clock.systemUTC(), MediaContextService::detectTidalProcess);
    }

    public MediaContextService(MediaSessionProvider sessions, AudioOutputProvider audio, MediaEnrichmentProvider enrichment,
                               Clock clock,  Supplier<ProcessPresence> processProbe) {
        this.sessions = Objects.requireNonNull(sessions);
        this.audio = Objects.requireNonNull(audio);
        this.enrichment = Objects.requireNonNull(enrichment);
        this.clock = Objects.requireNonNull(clock);
        this.processProbe = Objects.requireNonNull(processProbe);
    }

    @Override
    public MediaContextSnapshot current() {
        return current.get();
    }

    @Override
    public boolean submit(MediaAction action) {
        return enqueue(action, new CompletableFuture<>());
    }

    @Override
    public CompletionStage<MediaActionResult> executeAsync(MediaAction action) {
        var result = new CompletableFuture<MediaActionResult>();

        enqueue(action, result);
        return result;
    }

    @Override
    public void close() throws Exception {
        long until;
        boolean coreStopped;
        boolean enrichmentStopped;

        synchronized (lifecycleLock) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            current.updateAndGet(latest -> new MediaContextSnapshot(latest.activeSession().asFailed(),
                    latest.tidalPlayback().asFailed(), latest.tidalEnrichment().asFailed(), latest.audioOutput().asFailed(),
                    latest.tidalProcess().asFailed()));
            if (coreTask != null) {
                coreTask.cancel(false);
            }
            if (enrichmentTask != null) {
                enrichmentTask.cancel(false);
            }
            try {
                coreExecutor.execute(() -> {
                    closeSafely(sessions, "media sessions");
                    closeSafely(audio, "audio output");
                });
                enrichmentExecutor.execute(() -> closeSafely(enrichment, "TIDAL enrichment"));
            }
            finally {
                coreExecutor.shutdown();
                enrichmentExecutor.shutdown();
            }
        }
        pending.forEach((future, action) ->
                future.complete(new MediaActionResult(action, MediaActionStatus.CLOSED)));
        if (Thread.currentThread() == coreWorker.get() || Thread.currentThread() == enrichmentWorker.get()) {
            return;
        }
        try {
            until = System.nanoTime() + TimeUnit.SECONDS.toNanos(CLOSE_TIMEOUT_SECONDS);
            coreStopped = awaitRemaining(coreExecutor, until);
            enrichmentStopped = awaitRemaining(enrichmentExecutor, until);
            if (!coreStopped || !enrichmentStopped) {
                System.err.println("Media context close timed out; cleanup remains queued");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void start() {
        ScheduledFuture<?> first;
        ScheduledFuture<?> second;

        synchronized (lifecycleLock) {
            if (closed.get()) {
                throw new IllegalStateException("Media context service is closed");
            }
            if (started.get()) {
                return;
            }
            first = coreExecutor.scheduleWithFixedDelay(this::refreshCoreSafely, 0, CORE_REFRESH_MILLIS,
                    TimeUnit.MILLISECONDS);
            try {
                second = enrichmentExecutor.scheduleWithFixedDelay(this::refreshEnrichmentSafely, 0,
                        ENRICHMENT_REFRESH_MILLIS, TimeUnit.MILLISECONDS);
                coreTask = first;
                enrichmentTask = second;
                started.set(true);
            }
            catch (RuntimeException e) {
                first.cancel(false);
                throw e;
            }
        }
    }

    private boolean enqueue(MediaAction action, CompletableFuture<MediaActionResult> result) {
        boolean accepted;

        Objects.requireNonNull(action);
        Objects.requireNonNull(result);
        accepted = false;
        synchronized (lifecycleLock) {
            if (!closed.get()) {
                pending.put(result, action);
                result.whenComplete((ignored, error) -> pending.remove(result));
                try {
                    coreExecutor.execute(() -> runAction(action, result));
                    accepted = true;
                }
                catch (RejectedExecutionException e) {
                    pending.remove(result);
                }
            }
        }
        if (!accepted) {
            result.complete(new MediaActionResult(action, MediaActionStatus.CLOSED));
        }
        return accepted;
    }

    private void runAction(MediaAction action, CompletableFuture<MediaActionResult> result) {
        MediaActionResult outcome;
        boolean acknowledged;

        if (closed.get()) {
            result.complete(new MediaActionResult(action, MediaActionStatus.CLOSED));
            return;
        }
        outcome = new MediaActionResult(action, MediaActionStatus.FAILED);
        try {
            acknowledged = switch (action) {
                case PREVIOUS -> sessions.previous();
                case NEXT -> sessions.next();
                case PAUSE -> sessions.pause();
                case RESUME -> sessions.resume();
                case PLAY_PAUSE -> sessions.playPause();
            };
            outcome = new MediaActionResult(action, acknowledged ? MediaActionStatus.ACKNOWLEDGED : MediaActionStatus.REJECTED);
        }
        catch (RuntimeException | LinkageError e) {
            System.err.println("Media action failed[" + action + "]: " + e);
        }
        finally {
            result.complete(outcome);
            if (!closed.get()) {
                refreshCoreSafely();
            }
        }
    }

    private void refreshCoreSafely() {
        MediaContextSnapshot previous;
        MediaSample<MediaSessionSnapshot> active;
        MediaSample<Optional<MediaPlaybackState>> tidal;
        MediaSample<AudioOutputSnapshot> output;
        MediaSample<ProcessPresence> process;

        if (closed.get()) {
            return;
        }
        try {
            previous = current.get();
            active = read(sessions::current, previous.activeSession(), "active session");
            tidal = read(() -> sessions.playbackState("TIDAL"), previous.tidalPlayback(), "TIDAL playback");
            output = read(audio::current, previous.audioOutput(), "audio output");
            process = readProcess(previous.tidalProcess());
            synchronized (lifecycleLock) {
                if (!closed.get()) {
                    current.updateAndGet(latest ->
                            new MediaContextSnapshot(active, tidal, latest.tidalEnrichment(), output, process));
                }
            }
        }
        catch (RuntimeException | LinkageError e) {
            System.err.println("Media core refresh failed: " + e);
        }
    }

    private void refreshEnrichmentSafely() {
        MediaSample<MediaEnrichmentSnapshot> sample;

        if (closed.get()) {
            return;
        }
        try {
            sample = read(enrichment::current, current.get().tidalEnrichment(), "TIDAL enrichment");
            synchronized (lifecycleLock) {
                if (!closed.get()) {
                    current.updateAndGet(latest -> new MediaContextSnapshot(latest.activeSession(),
                            latest.tidalPlayback(), sample, latest.audioOutput(), latest.tidalProcess()));
                }
            }
        }
        catch (RuntimeException | LinkageError e) {
            System.err.println("Media enrichment refresh failed: " + e);
        }
    }

    private <T> MediaSample<T> read(Supplier<T> supplier, MediaSample<T> prior, String label) {
        try {
            return MediaSample.observed(Objects.requireNonNull(supplier.get(), label), clock);
        }
        catch (RuntimeException | LinkageError e) {
            System.err.println("Media provider read failed [" +  label + "]: " + e);
            return prior.asFailed();
        }
    }

    private MediaSample<ProcessPresence> readProcess(MediaSample<ProcessPresence> prior) {
        long now = System.nanoTime();

        if (now < nextProcessRefreshNanos)
            return prior;
        nextProcessRefreshNanos = now + PROCESS_REFRESH_NANOS;
        return read(processProbe, prior, "TIDAL process");
    }

    private static boolean awaitRemaining(ExecutorService executor, long until) throws InterruptedException {
        return executor.awaitTermination(Math.max(0L, until - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    private static Thread daemon(Runnable task, String name, AtomicReference<Thread> holder) {
        Thread worker = new Thread(task, name);

        worker.setDaemon(true);
        holder.set(worker);
        return worker;
    }

    private static void closeSafely(AutoCloseable provider, String label) {
        try {
            provider.close();
        }
        catch (Exception | LinkageError e) {
            System.err.println("Unable to close " + label + ": " + e);
        }
    }

    static ProcessPresence detectTidalProcess() {
        ProcessHandle process;
        Path executable;
        boolean incomplete = false;

        try (var processes = ProcessHandle.allProcesses()) {
            var iterator = processes.iterator();
            while (iterator.hasNext()) {
                process = iterator.next();
                try {
                    var cmd = process.info().command();
                    if (cmd.isEmpty()) {
                        incomplete = true;
                        continue;
                    }
                    executable = Path.of(cmd.get()).getFileName();
                    if (executable != null && "TIDAL.exe".equalsIgnoreCase(executable.toString())) {
                        return ProcessPresence.RUNNING;
                    }
                }
                catch (RuntimeException | LinkageError e) {
                    incomplete = true;
                }
            }
        }
        catch (RuntimeException | LinkageError e) {
            return ProcessPresence.UNKNOWN;
        }
        return incomplete ? ProcessPresence.UNKNOWN : ProcessPresence.NOT_RUNNING;
    }
}
