package ch.qos.logback.core.rolling.helper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TimeBasedArchiveRemoverTest {

  private TimeBasedArchiveRemover remover;
  private final String TIMEZONE_NAME = "GMT";
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

  @Before
  public void clean() throws ParseException {
    this.remover = this.createArchiveRemover();
    this.remover.clean(this.parseDate("yyyy/MM/dd", this.CLEAN_DATE));
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

  private Date parseDate(String format, String value) throws ParseException {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone(this.TIMEZONE_NAME));
    return dateFormat.parse(value);
  }

  private TimeBasedArchiveRemover createArchiveRemover() {
    final String DATE_PATTERN = "yyyyMMdd";
    final String FILENAME_PATTERN = "%d{" + DATE_PATTERN + ", " + this.TIMEZONE_NAME + "}.log";
    LoggerContext context = new LoggerContext();
    RollingCalendar rollingCalendar = new RollingCalendar(DATE_PATTERN);
    FileProvider fileProvider = this.mockFileProvider(this.DUMMY_FILES, true, true);
    FileNamePattern fileNamePattern = new FileNamePattern(FILENAME_PATTERN, context);
    return spy(new TimeBasedArchiveRemover(fileNamePattern, rollingCalendar, fileProvider));
  }

  private FileProvider mockFileProvider(final File[] files, boolean isFileRval, boolean deleteFileRval) {
    FileProvider fileProvider = mock(FileProvider.class);

    when(fileProvider.listFiles(any(File.class), any(FilenameFilter.class))).then(
      new Answer<File[]>() {
        public File[] answer(InvocationOnMock invocation) {
          FilenameFilter filter = invocation.getArgument(1);

          ArrayList<File> foundFiles = new ArrayList<File>();
          for (File f : files) {
            if (filter.accept(f.getParentFile(), f.getName())) {
              foundFiles.add(f);
            }
          }
          return foundFiles.toArray(new File[0]);
        }
      }
    );
    when(fileProvider.listFiles(any(File.class), isNull(FilenameFilter.class))).thenReturn(files);
    when(fileProvider.deleteFile(any(File.class))).thenReturn(deleteFileRval);
    when(fileProvider.isFile(any(File.class))).thenReturn(isFileRval);
    return fileProvider;
  }
}
