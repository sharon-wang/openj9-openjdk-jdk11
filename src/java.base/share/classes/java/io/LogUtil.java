package java.io;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Utility class which logs to a temp file, bound to a thread (so each thread will log to its own file).
 * Get/create by {@link #get()}, then use {@link #log(String)} to log. Finally make sure to always
 * call {@link #clear()} when done so it closes the file and gets removed from the thread local.
 */
public final class LogUtil {

  private static final ThreadLocal<LogUtil> THREAD_LOCAL = new ThreadLocal<>();

  private final OutputStream streamOut;
  private final DateTimeFormatter dateTimeFormatter;

  private LogUtil() {
    this(getTempPath() , "openj9_ois_", ".log");
  }

  private LogUtil(final Path pathTmpDir, final String prefix, final String postFix) {
    System.out.println("Temp directory determined to '" + pathTmpDir.toString() + "'");

    try {
      final Path fileOut = Files.createTempFile(pathTmpDir, prefix, postFix);

      System.out.println("Thread '" + Thread.currentThread().getName() + "' will write log to '" + fileOut.toString() + "'");

      streamOut = new BufferedOutputStream(new FileOutputStream(fileOut.toFile()));
      dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS VV");
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to create FileUtil", e);
    }
  }

  public static void clear() {
    final LogUtil logUtil = THREAD_LOCAL.get();
    if (logUtil != null) {
      try {
        logUtil.close();
      }
      catch (IOException e) {
        throw new RuntimeException("Failed to close FileUtil", e);
      }
      finally {
        THREAD_LOCAL.remove();
      }
    }
  }

  public static void log(final String message) {
    // We could force it to GMT maybe, now it takes whatever is the JVM's default
    final LogUtil logUtil = get();
    logUtil.logMessage(message);
  }

  private void logMessage(final String message) {
    final String timeStamp = dateTimeFormatter.format(ZonedDateTime.now());
    try {
      streamOut.write(timeStamp.getBytes(StandardCharsets.UTF_8));
      streamOut.write(" [".getBytes(StandardCharsets.UTF_8));
      streamOut.write(Thread.currentThread().getName().getBytes(StandardCharsets.UTF_8));
      streamOut.write("] ".getBytes(StandardCharsets.UTF_8));
      streamOut.write(Objects.requireNonNullElse(message, "<null message>").getBytes(StandardCharsets.UTF_8));
      streamOut.write('\n');
      streamOut.flush();
    }
    catch (IOException e) {
      // Just to make the logging easier in this case normally we just should throw the IOException
      throw new RuntimeException("Logging a line failed: " + message, e);
    }
  }

  private void close() throws IOException {
    streamOut.close();
  }

  private static LogUtil get()  {
    LogUtil logUtil = THREAD_LOCAL.get();
    if (logUtil != null) {
      return logUtil;
    }
    logUtil = new LogUtil();
    THREAD_LOCAL.set(logUtil);
    return logUtil;
  }


  private static Path getTempPath() {
    final String strTempDir = System.getProperty("java.io.tmpdir");
    if (strTempDir == null || strTempDir.isEmpty()) {
      throw new RuntimeException("Failed to find tmp directory, use FileUtil.create(path) instead");
    }
    return Path.of(strTempDir);
  }
}
