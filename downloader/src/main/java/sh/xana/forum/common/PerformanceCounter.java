package sh.xana.forum.common;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

public class PerformanceCounter {
  private final AtomicInteger counter = new AtomicInteger();
  private long startTime;
  private final Logger log;
  private final int splitBy;

  public PerformanceCounter(Logger log, int splitBy) {
    this.log = log;
    this.splitBy = splitBy;
    start();
  }

  public void start() {
    startTime = System.currentTimeMillis();
  }

  public int current() {
    return counter.get();
  }

  public int incrementAndLog() {
    int idx = counter.getAndIncrement();
    if (idx % splitBy == 0) {
      // avoid divide by zero error
      long processIndex = Math.max(idx, 1);
      long durationSec = Math.max(System.currentTimeMillis() - startTime, 1);
      durationSec = Math.max(durationSec / 1000, 1);

      log.info("read {} /sec {}", processIndex, processIndex / durationSec);
    }
    return idx;
  }

  public int incrementAndLog(Collection<?> list) {
    int idx = counter.getAndIncrement();
    if (idx % splitBy == 0) {
      // avoid divide by zero error
      long processIndex = Math.max(idx, 1);
      long durationSec = Math.max(System.currentTimeMillis() - startTime, 1);
      durationSec = Math.max(durationSec / 1000, 1);

      log.info(
          "read {} of {} /sec {} %{}",
          processIndex,
          list.size(),
          processIndex / durationSec,
          "%.2f".formatted(idx / (double) list.size() * 100));
    }
    return idx;
  }

  public int incrementAndLog(Collection<?> inputList, Collection<?> outputList) {
    return incrementAndLog(inputList.size(), outputList);
  }

  public int incrementAndLog(long inputSize, Collection<?> outputList) {
    int idx = counter.getAndIncrement();
    if (idx % splitBy == 0) {
      // avoid divide by zero error
      long processIndex = Math.max(idx, 1);
      long durationSec = Math.max(System.currentTimeMillis() - startTime, 1);
      durationSec = Math.max(durationSec / 1000, 1);

      log.info(
          "read {} of {} /sec {} %{} - queued {}",
          processIndex,
          inputSize,
          processIndex / durationSec,
          "%.2f".formatted(idx / (double) inputSize * 100),
          outputList.size());
    }
    return idx;
  }

  public int incrementAndLog(long inputSize) {
    // log.info("COUNTING");
    int idx = counter.getAndIncrement();
    if (idx % splitBy == 0) {
      // avoid divide by zero error
      long processIndex = Math.max(idx, 1);
      long durationSec = Math.max(System.currentTimeMillis() - startTime, 1);
      durationSec = Math.max(durationSec / 1000, 1);

      log.info(
          "read {} of {} /sec {} %{}",
          processIndex,
          inputSize,
          processIndex / durationSec,
          "%.2f".formatted(idx / (double) inputSize * 100));
    }
    return idx;
  }
}
