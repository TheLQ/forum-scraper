package sh.xana.forum.common;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

public class PerformanceCounter {
  private final AtomicInteger counter = new AtomicInteger();
  private final long startTime = System.currentTimeMillis();
  private final Logger log;
  private final int splitBy;

  public PerformanceCounter(Logger log, int splitBy) {
    this.log = log;
    this.splitBy = splitBy;
  }

  public int incrementAndLog(Collection<?> inputList, Collection<?> outputList) {
    int idx = counter.incrementAndGet();
    if (idx % splitBy == 0) {
      // avoid divide by zero error
      long processIndex = Math.max(idx, 1);
      long durationSec = Math.max(System.currentTimeMillis() - startTime, 1);
      durationSec = Math.max(durationSec / 1000, 1);

      log.info(
          "read {} of {} /sec {} %{} - queued {}",
          processIndex,
          inputList.size(),
          processIndex / durationSec,
          "%.2f".formatted(idx / (double) inputList.size() * 100),
          outputList.size());
    }
    return idx;
  }
}
