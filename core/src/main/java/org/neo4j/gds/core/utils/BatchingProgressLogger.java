/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.core.utils;

import org.apache.commons.lang3.mutable.MutableLong;
import org.jetbrains.annotations.TestOnly;
import org.neo4j.gds.core.utils.progress.EmptyProgressEventTracker;
import org.neo4j.gds.core.utils.progress.ProgressEventTracker;
import org.neo4j.logging.Log;

import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public class BatchingProgressLogger implements ProgressLogger {
    public static final long MAXIMUM_LOG_INTERVAL = (long) Math.pow(2, 13);

    public static final ProgressLoggerFactory FACTORY = BatchingProgressLogger::new;

    private final Log log;
    private final int concurrency;
    private long taskVolume;
    private long batchSize;
    private String task;
    private final ProgressEventTracker progressTracker;
    private final LongAdder progressCounter;
    private final ThreadLocal<MutableLong> callCounter;

    private int globalPercentage;

    private static long calculateBatchSize(long taskVolume, int concurrency) {
        // target 100 logs per full run (every 1 percent)
        var batchSize = taskVolume / 100;
        // split batchSize into thread-local chunks
        batchSize /= concurrency;
        // batchSize needs to be a power of two
        return Math.max(1, BitUtil.nextHighestPowerOfTwo(batchSize));
    }

    @TestOnly
    public BatchingProgressLogger(Log log, long taskVolume, String task, int concurrency) {
        this(log, taskVolume, calculateBatchSize(taskVolume, concurrency), task, concurrency, EmptyProgressEventTracker.INSTANCE);
    }

    public BatchingProgressLogger(Log log, long taskVolume, String task, int concurrency, ProgressEventTracker progressTracker) {
        this(log, taskVolume, calculateBatchSize(taskVolume, concurrency), task, concurrency, progressTracker);
    }

    @TestOnly
    public BatchingProgressLogger(Log log, long taskVolume, long batchSize, String task, int concurrency) {
        this(log, taskVolume, batchSize, task, concurrency, EmptyProgressEventTracker.INSTANCE);
    }

    public BatchingProgressLogger(Log log, long taskVolume, long batchSize, String task, int concurrency, ProgressEventTracker progressTracker) {
        this.log = log;
        this.taskVolume = taskVolume;
        this.batchSize = batchSize;
        this.task = task;
        this.progressTracker = progressTracker;

        this.progressCounter = new LongAdder();
        this.callCounter = ThreadLocal.withInitial(MutableLong::new);
        this.concurrency = concurrency;
        this.globalPercentage = -1;
    }

    @Override
    public String getTask() {
        return task;
    }

    @Override
    public void setTask(String task) {
        this.task = task;
    }

    @Override
    public void logProgress(Supplier<String> msgFactory) {
        var localProgress = callCounter.get();
        if (localProgress.incrementAndGet() >= batchSize) {
            doLogPercentage(msgFactory, 1);
            localProgress.setValue(0L);
        } else {
            progressCounter.increment();
        }
    }

    @Override
    public void logProgress(long progress, Supplier<String> msgFactory) {
        if (progress == 0) {
            return;
        }
        var localProgress = callCounter.get();
        if (localProgress.addAndGet(progress) >= batchSize) {
            doLogPercentage(msgFactory, progress);
            localProgress.setValue(localProgress.longValue() & (batchSize - 1));
        } else {
            progressCounter.add(progress);
        }
    }

    @Override
    public void release() {
        progressTracker.release();
    }

    private synchronized void doLogPercentage(Supplier<String> msgFactory, long progress) {
        String message = msgFactory != NO_MESSAGE ? msgFactory.get() : null;
        progressCounter.add(progress);
        int nextPercentage = (int) ((progressCounter.sum() / (double) taskVolume) * 100);
        if (globalPercentage < nextPercentage) {
            globalPercentage = nextPercentage;
            if (message == null || message.isEmpty()) {
                logProgress(nextPercentage);
            } else {
                logProgressWithMessage(nextPercentage, message);
            }
        }
    }

    private void logProgress(int nextPercentage) {
        logAndTrack(task, formatWithLocale("[%s] %s %d%%", Thread.currentThread().getName(), task, nextPercentage));
    }

    private void logProgressWithMessage(int nextPercentage, String msg) {
        logAndTrack(
            task,
            formatWithLocale("[%s] %s %d%% %s", Thread.currentThread().getName(), task, nextPercentage, msg)
        );
    }

    @Override
    public void logMessage(String msg) {
        logAndTrack(task, formatWithLocale("[%s] %s %s", Thread.currentThread().getName(), task, msg));
    }

    @Override
    public void logMessage(Supplier<String> msg) {
        logMessage(Objects.requireNonNull(msg.get()));
    }

    private void logAndTrack(String task, String message) {
        progressTracker.addLogEvent(task, message);
        log.info(message);
    }

    @Override
    public long reset(long newTaskVolume) {
        var remainingVolume = taskVolume - progressCounter.sum();
        this.taskVolume = newTaskVolume;
        this.batchSize = calculateBatchSize(newTaskVolume, concurrency);
        progressCounter.reset();
        globalPercentage = -1;
        return remainingVolume;
    }

    @Override
    public Log getLog() {
        return this.log;
    }

    @Override
    public ProgressEventTracker eventTracker() {
        return progressTracker;
    }

    @Override
    public void logProgress(double percentDone, Supplier<String> msg) {
        throw new UnsupportedOperationException("BatchProgressLogger does not support logging percentages");
    }
}