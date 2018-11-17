package ch.qos.logback.core.rolling.helper;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

import ch.qos.logback.classic.LoggerContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class TimeBasedArchiveRemoverTest {

  private TimeBasedArchiveRemover remover;
  private final String CLEAN_DATE = "2018/11/04";
  private final File[] RECENT_FILES = new File[] {
    new File("app_20181104.log"),
    new File("app_20181105.log")
  };
  private final File[] EXPIRED_FILES = new File[]{
    new File("app_20181102.log"),
    new File("app_20181103.log")
  };
  private final File[] DUMMY_FILES = new File[] {
    EXPIRED_FILES[0],
    EXPIRED_FILES[1],
    RECENT_FILES[0],
    RECENT_FILES[1]
  };
  private final String TIMEZONE_NAME = "GMT";

  @Before
  public void clean() throws ParseException {
    final String DATE_PATTERN = "yyyyMMdd";
    final String FILENAME_PATTERN = "%d{" + DATE_PATTERN + ", " + this.TIMEZONE_NAME + "}.log";
    final LoggerContext context = new LoggerContext();
    final RollingCalendar rollingCalendar = new RollingCalendar(DATE_PATTERN);
    final File[] FILES = this.DUMMY_FILES;

    this.remover = spy(new TimeBasedArchiveRemover(new FileNamePattern(FILENAME_PATTERN, context), rollingCalendar, new FileProvider() {
      public File[] listFiles(File dir, FilenameFilter filter) {
        if (filter == null) {
          return FILES;
        }

        ArrayList<File> foundFiles = new ArrayList<File>();
        for (File f : FILES) {
          if (filter.accept(f.getParentFile(), f.getName())) {
            foundFiles.add(f);
          }
        }
        return foundFiles.toArray(new File[foundFiles.size()]);
      }

      public boolean deleteFile(File file) {
        return true;
      }

      public boolean isFile(File file) {
        return true;
      }
    }));

    doReturn(true).when(this.remover).delete(any(File.class));

    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone(this.TIMEZONE_NAME));
    this.remover.clean(dateFormat.parse(this.CLEAN_DATE));
  }

  @Test
  public void cleanRemovesExpiredFiles() {
    for (File f : this.EXPIRED_FILES) {
      verify(this.remover, atLeastOnce()).delete(f);
    }
  }

  @Test
  public void cleanKeepsRecentFiles() {
    for (File f : this.RECENT_FILES) {
      verify(this.remover, never()).delete(f);
    }
  }
}
