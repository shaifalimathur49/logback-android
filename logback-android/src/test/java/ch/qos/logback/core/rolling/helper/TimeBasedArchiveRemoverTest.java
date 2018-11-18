package ch.qos.logback.core.rolling.helper;

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

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TimeBasedArchiveRemoverTest {

  private TimeBasedArchiveRemover remover;
  private final String TIMEZONE_NAME = "GMT";
  private final Date CLEAN_DATE = parseDate("yyyy/MM/dd", "2018/11/04");
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

  @Test
  public void cleanRemovesExpiredFiles() {
    FileProvider fileProvider = this.mockFileProvider(this.DUMMY_FILES, true);
    this.remover = this.createArchiveRemover(fileProvider);
    this.remover.clean(this.CLEAN_DATE);

    for (File f : this.EXPIRED_FILES) {
      verify(fileProvider).deleteFile(f);
    }
  }

  @Test
  public void cleanKeepsRecentFiles() {
    FileProvider fileProvider = this.mockFileProvider(this.DUMMY_FILES, true);
    this.remover = this.createArchiveRemover(fileProvider);
    this.remover.clean(this.CLEAN_DATE);

    for (File f : this.RECENT_FILES) {
      verify(fileProvider, never()).deleteFile(f);
    }
  }

  @Test
  public void cleanRemovesExpiredFilesOlderThanMaxHistory() {
    final int MAX_HISTORY = 2;
    FileProvider fileProvider = this.mockFileProvider(this.DUMMY_FILES, true);
    this.remover = this.createArchiveRemover(fileProvider);
    this.remover.setMaxHistory(MAX_HISTORY);
    this.remover.clean(this.CLEAN_DATE);

    for (int i = 0; i < this.EXPIRED_FILES.length - MAX_HISTORY; i++) {
      File f = this.EXPIRED_FILES[i];
      verify(fileProvider).deleteFile(f);
    }
  }

  @Test
  public void cleanKeepsMaxHistory() {
    final int MAX_HISTORY = 2;
    FileProvider fileProvider = this.mockFileProvider(this.DUMMY_FILES, true);
    this.remover = this.createArchiveRemover(fileProvider);
    this.remover.setMaxHistory(MAX_HISTORY);
    this.remover.clean(this.CLEAN_DATE);

    for (int i = this.EXPIRED_FILES.length - MAX_HISTORY; i < this.EXPIRED_FILES.length; i++) {
      File f = this.EXPIRED_FILES[i];
      verify(fileProvider, never()).deleteFile(f);
    }
  }

  @Test
  public void doesNotCleanWhenDirEmpty() {
    FileProvider fileProvider = this.mockFileProvider(new File[0], true);
    this.remover = this.createArchiveRemover(fileProvider);
    this.remover.clean(this.CLEAN_DATE);

    verify(fileProvider, never()).deleteFile(any(File.class));
  }

  @Test
  public void doesNotCleanWhenNotFile() {
    FileProvider fileProvider = this.mockFileProvider(this.DUMMY_FILES, false);
    this.remover = this.createArchiveRemover(fileProvider);
    this.remover.clean(this.CLEAN_DATE);

    verify(fileProvider, never()).deleteFile(any(File.class));
  }

//  @Test
//  public void deletesParentDirWhenCleanRemoveAllFiles() {
//    this.remover = this.createArchiveRemover(this.EXPIRED_FILES, true, true);
//    this.remover.clean(this.CLEAN_DATE);
//
//    verify(this.remover).delete(this.PARENT_DIR);
//  }

  private Date parseDate(String format, String value) {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone(this.TIMEZONE_NAME));
    Date date;
    try {
      date = dateFormat.parse(value);
    } catch (ParseException e) {
      date = null;
    }
    assertNotNull(date);
    return date;
  }

  private TimeBasedArchiveRemover createArchiveRemover(FileProvider fileProvider) {
    final String DATE_FORMAT = "yyyyMMdd";
    final String FILENAME_PATTERN = "%d{" + DATE_FORMAT + ", " + this.TIMEZONE_NAME + "}.log";
    LoggerContext context = new LoggerContext();
    RollingCalendar rollingCalendar = new RollingCalendar(DATE_FORMAT);
    FileNamePattern fileNamePattern = new FileNamePattern(FILENAME_PATTERN, context);
    TimeBasedArchiveRemover archiveRemover = new TimeBasedArchiveRemover(fileNamePattern, rollingCalendar, fileProvider);
    archiveRemover.setContext(context);
    return spy(archiveRemover);
  }

  private FileProvider mockFileProvider(final File[] files, boolean isFileRval) {
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
    when(fileProvider.isFile(any(File.class))).thenReturn(isFileRval);
    return fileProvider;
  }
}
