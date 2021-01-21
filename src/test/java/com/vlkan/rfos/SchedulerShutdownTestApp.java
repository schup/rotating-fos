package com.vlkan.rfos;

import com.vlkan.rfos.policy.TimeBasedRotationPolicy;
import lombok.extern.java.Log;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Log
public enum SchedulerShutdownTestApp {;

    @Log
    private static final class DelayedRotationPolicy extends TimeBasedRotationPolicy {

        private final Queue<Duration> delays;

        private DelayedRotationPolicy(Long... delays) {
            this.delays = Arrays
                    .stream(delays)
                    .map(Duration::ofMillis)
                    .collect(Collectors.toCollection(LinkedList::new));
        }

        @Override
        public Instant getTriggerInstant(Clock clock) {
            Duration delay = delays.poll();
            Objects.requireNonNull(delay, "delay");
            log.info(String.format("setting trigger with delay %s", delay));
            return clock.now().plus(delay);
        }

        @Override
        protected Logger getLogger() {
            return log;
        }

    }

    public static void main(String[] args) throws IOException {

        // Determine file names.
        String filePrefix =
                RotatingFileOutputStream.class.getSimpleName()
                        + "-"
                        + SchedulerShutdownTestApp.class.getSimpleName();
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        File file = new File(tmpDir, filePrefix + ".log");
        String fileName = file.getAbsolutePath();
        String fileNamePattern = new File(tmpDir, filePrefix + "-%d{yyyy}.log").getAbsolutePath();

        // Create the stream config.
        long rotationDelay1Millis = 500L;
        long rotationDelay2Millis = 5L * 60L * 1_000L;  // 5 minutes
        DelayedRotationPolicy policy = new DelayedRotationPolicy(rotationDelay1Millis, rotationDelay2Millis);
        RotationCallback callback = Mockito.spy(LoggingRotationCallback.getInstance());
        RotationConfig config = RotationConfig
                .builder()
                .file(fileName)
                .filePattern(fileNamePattern)
                .policy(policy)
                .callback(callback)
                .build();

        // Create the stream.
        log.info("creating the stream");
        RotatingFileOutputStream stream = new RotatingFileOutputStream(config);

        // Write something to stream to avoid rotation being skipped.
        stream.write(filePrefix.getBytes(StandardCharsets.US_ASCII));

        // Verify the 1st rotation.
        log.info("verifying the 1st rotation");
        long expectedRotationDelay1Millis1 = rotationDelay1Millis + /* extra threshold */ 100;
        Mockito
                .verify(callback, Mockito.timeout(expectedRotationDelay1Millis1))
                .onTrigger(Mockito.eq(policy), Mockito.any(Instant.class));

        // Close the stream.
        log.info("closing stream");
        stream.close();
        log.info("closed stream");

    }

}
