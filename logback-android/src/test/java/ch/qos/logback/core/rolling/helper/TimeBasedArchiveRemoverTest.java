package ch.qos.logback.core.rolling.helper;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
  private final File RECENT_FILE = new File("20181105.log");
  private final File[] EXPIRED_FILES = new File[]{
    new File("20181102.log"),
    new File("20181103.log"),
    new File("20181104.log")
  };
  private final File[] DUMMY_FILES = new File[] {
    EXPIRED_FILES[0],
    EXPIRED_FILES[1],
    EXPIRED_FILES[2],
    RECENT_FILE
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
      public File[] list(File dir, FilenameFilter filter) {
        ArrayList<File> foundFiles = new ArrayList<File>();
        for (File f : FILES) {
          if (filter.accept(f.getParentFile(), f.getName())) {
            foundFiles.add(f);
          }
        }
        return foundFiles.toArray(new File[foundFiles.size()]);
      }
    }));

    doReturn(true).when(this.remover).delete(any(File.class));
//    doReturn(this.DUMMY_FILES).when(this.remover).getFilesInPeriod(any(Date.class));

    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone(this.TIMEZONE_NAME));
    this.remover.clean(dateFormat.parse(this.CLEAN_DATE));
  }

//  @Test
//  public void cleanCalculatesExpiredFiles() {
//    verify(this.remover, atLeastOnce()).getFilesInPeriod(any(Date.class));
//  }

  @Test
  public void cleanRemovesExpiredFiles() {
    for (File f : this.EXPIRED_FILES) {
      verify(this.remover, atLeastOnce()).delete(f);
    }
  }

  @Test
  public void cleanKeepsRecentFiles() {
    verify(this.remover, never()).delete(this.RECENT_FILE);
  }
}
