package sh.xana.forum.server.threads;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sh.xana.forum.common.AbstractTaskThread;

public class RuntimeDebugThread extends AbstractTaskThread {
  private static final Logger log = LoggerFactory.getLogger(RuntimeDebugThread.class);
  private static final Path PATH_DEATH_TRIGGER = Path.of("death.tmp");
  private final AutoCloseable main;

  public RuntimeDebugThread(AutoCloseable main) {
    // we handle the cycle sleep internally
    super("RuntimeDebugThread", 0);
    this.main = main;
  }

  @Override
  protected boolean runCycle() throws Exception {
    StringBuilder sb = new StringBuilder("State thread").append(System.lineSeparator());
    sb.append(getSystemInformation()).append(System.lineSeparator());
    for (var thread : ThreadUtils.getAllThreads()) {
      sb.append("Thread ").append(thread.getName()).append(System.lineSeparator());
    }
    System.err.println(sb);

    // wait 5 minutes
    for (int i = 0; i < 5 * 2; i++) {
      TimeUnit.SECONDS.sleep(30);
      if (Files.exists(PATH_DEATH_TRIGGER)) {
        Files.delete(PATH_DEATH_TRIGGER);
        main.close();
        return false;
      }
    }
    return true;
  }

  public static long getMaxMemory() {
    return Runtime.getRuntime().maxMemory();
  }

  public static long getUsedMemory() {
    return getMaxMemory() - getFreeMemory();
  }

  public static long getTotalMemory() {
    return Runtime.getRuntime().totalMemory();
  }

  public static long getFreeMemory() {
    return Runtime.getRuntime().freeMemory();
  }

  private static final long MEGABYTE_FACTOR = 1024L * 1024L;
  private static final DecimalFormat ROUNDED_DOUBLE_DECIMALFORMAT;
  private static final String MIB = "MiB";

  static {
    DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols(Locale.ENGLISH);
    otherSymbols.setDecimalSeparator('.');
    otherSymbols.setGroupingSeparator(',');
    ROUNDED_DOUBLE_DECIMALFORMAT = new DecimalFormat("####0.00", otherSymbols);
    ROUNDED_DOUBLE_DECIMALFORMAT.setGroupingUsed(false);
  }

  public static String getTotalMemoryInMiB() {
    double totalMiB = bytesToMiB(getTotalMemory());
    return String.format("%s %s", ROUNDED_DOUBLE_DECIMALFORMAT.format(totalMiB), MIB);
  }

  public static String getFreeMemoryInMiB() {
    double freeMiB = bytesToMiB(getFreeMemory());
    return String.format("%s %s", ROUNDED_DOUBLE_DECIMALFORMAT.format(freeMiB), MIB);
  }

  public static String getUsedMemoryInMiB() {
    double usedMiB = bytesToMiB(getUsedMemory());
    return String.format("%s %s", ROUNDED_DOUBLE_DECIMALFORMAT.format(usedMiB), MIB);
  }

  public static String getMaxMemoryInMiB() {
    double maxMiB = bytesToMiB(getMaxMemory());
    return String.format("%s %s", ROUNDED_DOUBLE_DECIMALFORMAT.format(maxMiB), MIB);
  }

  public static double getPercentageUsed() {
    return ((double) getUsedMemory() / getMaxMemory()) * 100;
  }

  public static String getPercentageUsedFormatted() {
    double usedPercentage = getPercentageUsed();
    return ROUNDED_DOUBLE_DECIMALFORMAT.format(usedPercentage) + "%";
  }

  private static double bytesToMiB(long bytes) {
    return ((double) bytes / MEGABYTE_FACTOR);
  }

  public static String getSystemInformation() {
    return String.format(
        "SystemInfo=Current heap:%s; Used:%s; Free:%s; Maximum Heap:%s; Percentage Used:%s",
        getTotalMemoryInMiB(),
        getUsedMemoryInMiB(),
        getFreeMemoryInMiB(),
        getMaxMemoryInMiB(),
        getPercentageUsedFormatted());
  }
}
