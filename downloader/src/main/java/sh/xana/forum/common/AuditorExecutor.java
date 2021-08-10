package sh.xana.forum.common;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

public class AuditorExecutor<Input, Output> {
  private final Logger log;
  private final PerformanceCounter readCounter;

  public AuditorExecutor(Logger log) {
    this.log = log;
    readCounter = new PerformanceCounter(log, 1000);
  }

  public void run(
      List<Input> input,
      int inputThreadsNum,
      InputFunction<Input, Output> inputFunction,
      int outputThreadsNum,
      OutputFunction<Output> outputFunction)
      throws InterruptedException {
    BlockingQueue<Output> outputQueue = new ArrayBlockingQueue<>(5000);

    List<Thread> inputThreads =
        Utils.threadRunner(
            inputThreadsNum,
            "input-",
            () -> {
              while (true) {
                int idx = readCounter.incrementAndLog(input, outputQueue);
                try {
                  if (idx >= input.size()) {
                    log.info("End");
                    break;
                  }

                  Input entry = input.get(idx);
                  outputQueue.put(inputFunction.run(entry));
                } catch (Exception e) {
                  log.error("FAILED TO PUT", e);
                  // System.exit(1);
                }
              }
            });

    List<Thread> outputThreads =
        Utils.threadRunner(
            outputThreadsNum,
            "processor-",
            () -> {
              while (anyThreadAlive(inputThreads)) {
                try {
                  Output entry = outputQueue.poll(10, TimeUnit.SECONDS);
                  if (entry == null) {
                    // check if threads are still running
                    continue;
                  }

                  outputFunction.run(entry);
                } catch (Exception e) {
                  log.error("PROCESSOR ERROR", e);
                }
              }
              log.info("Ending processor");
            });

    List<Thread> allThreads = new ArrayList<>();
    allThreads.addAll(inputThreads);
    allThreads.addAll(outputThreads);
    while (anyThreadAlive(allThreads)) {
      TimeUnit.SECONDS.sleep(10);
    }
  }

  public static interface InputFunction<Input, Output> {
    Output run(Input in) throws Exception;
  }

  public static interface OutputFunction<Output> {
    void run(Output out) throws Exception;
  }

  private static boolean anyThreadAlive(List<Thread> threads) {
    for (Thread thread : threads) {
      if (thread.isAlive()) {
        return true;
      }
    }
    return false;
  }
}
