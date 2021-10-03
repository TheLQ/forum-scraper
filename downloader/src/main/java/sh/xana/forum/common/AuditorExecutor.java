package sh.xana.forum.common;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

public class AuditorExecutor {
  private final Logger log;
  private final List<Thread> allThreads = new ArrayList<>();

  public AuditorExecutor(Logger log) {
    this.log = log;
  }

  public <Input, Output> void startConverterForList(
      String prefix,
      int inputThreadsNum,
      List<Input> input,
      ExceptionFunction<Input, Output> converter,
      BlockingQueue<Output> outputQueue) {
    log.info("starting {} {} threads", inputThreadsNum, prefix);

    PerformanceCounter readCounter = new PerformanceCounter(log, 1000);

    List<Thread> inputThreads =
        Utils.threadRunner(
            inputThreadsNum,
            prefix + "-",
            () -> {
              while (true) {
                int idx = readCounter.incrementAndLog(input, outputQueue);
                try {
                  if (idx >= input.size()) {
                    log.info("End");
                    break;
                  }

                  Input entry = input.get(idx);
                  outputQueue.put(converter.run(entry));
                } catch (Exception e) {
                  log.error("FAILED TO PUT", e);
                  // System.exit(1);
                }
              }
            });

    allThreads.addAll(inputThreads);
  }

  public <Input, Output> void startConverterForSupplierToSize(
      String prefix,
      int inputThreadsNum,
      ExceptionSupplier<Input> supplier,
      int supplierTotalSize,
      ExceptionFunction<Input, Output> converter,
      BlockingQueue<Output> outputQueue) {
    log.info("starting {} {} threads", inputThreadsNum, prefix);

    PerformanceCounter readCounter = new PerformanceCounter(log, 1000);

    List<Thread> inputThreads =
        Utils.threadRunner(
            inputThreadsNum,
            prefix + "-",
            () -> {
              while (true) {
                int idx = readCounter.incrementAndLog(supplierTotalSize, outputQueue.size());
                try {
                  if (idx >= supplierTotalSize) {
                    log.info("End");
                    break;
                  }

                  Input entry = supplier.run();
                  outputQueue.put(converter.run(entry));
                } catch (Exception e) {
                  log.error("FAILED TO PUT", e);
                  // System.exit(1);
                }
              }
            });

    allThreads.addAll(inputThreads);
  }

  public <Input, Output> void startConverterForSupplierToNull(
      String prefix,
      int inputThreadsNum,
      ExceptionSupplier<Input> supplier,
      ExceptionFunction<Input, Output> converter,
      BlockingQueue<Output> outputQueue) {
    log.info("starting {} {} threads", inputThreadsNum, prefix);

    PerformanceCounter readCounter = new PerformanceCounter(log, 1000);

    List<Thread> inputThreads =
        Utils.threadRunner(
            inputThreadsNum,
            prefix + "-",
            () -> {
              while (true) {
                readCounter.incrementAndLog();

                try {
                  Input entry = supplier.run();
                  if (entry == null) {
                    log.info("End");
                    break;
                  }

                  Output run = converter.run(entry);
                  if (run != null) {
                    outputQueue.put(run);
                  }
                } catch (Exception e) {
                  log.error("FAILED TO PUT", e);
                  // System.exit(1);
                }
              }
            });

    allThreads.addAll(inputThreads);
  }

  public <Input> void startConsumer(
      BlockingQueue<Input> input, int outputThreadsNum, ExceptionConsumer<Input> consumer) {
    List<Thread> outputThreads =
        Utils.threadRunner(
            outputThreadsNum,
            "processor-",
            () -> {
              while (anyThreadAlive()) {
                try {
                  Input entry = input.poll(10, TimeUnit.SECONDS);
                  if (entry == null) {
                    // check if threads are still running
                    continue;
                  }

                  consumer.run(entry);
                } catch (Exception e) {
                  log.error("PROCESSOR ERROR", e);
                }
              }
            });
    allThreads.addAll(outputThreads);
  }

  public <Input> void startConsumerForSupplierToSize(
      String prefix,
      int threadsNum,
      ExceptionSupplier<Input> input,
      int supplierTotalSize,
      ExceptionConsumer<Input> consumer) {
    log.info("starting {} {} threads", threadsNum, prefix);

    // PerformanceCounter readCounter = new PerformanceCounter(log, 1000);
    AtomicInteger readCounter = new AtomicInteger();
    List<Thread> inputThreads =
        Utils.threadRunner(
            threadsNum,
            prefix + "-",
            () -> {
              while (true) {
                // int idx = readCounter.incrementAndLog(supplierTotalSize);
                int idx = readCounter.getAndIncrement();
                try {
                  if (idx >= supplierTotalSize) {
                    break;
                  }

                  consumer.run(input.run());
                } catch (Exception e) {
                  log.error("FAILED TO PUT", e);
                  // System.exit(1);
                }
              }
            });

    allThreads.addAll(inputThreads);
  }

  public void waitForAllThreads() throws InterruptedException {
    Utils.waitForAllThreads(allThreads);
  }

  public interface ExceptionFunction<Input, Output> {
    Output run(Input in) throws Exception;
  }

  public interface ExceptionConsumer<Input> {
    void run(Input out) throws Exception;
  }

  public interface ExceptionSupplier<Output> {
    Output run() throws Exception;
  }

  public interface ExceptionRunnable {
    void run() throws Exception;
  }

  public boolean anyThreadAlive() {
    return Utils.anyThreadAlive(allThreads);
  }
}
