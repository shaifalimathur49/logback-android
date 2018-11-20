package ch.qos.logback.core.rolling.helper;

import org.junit.jupiter.api.Test;
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
import java.util.stream.Stream;

import ch.qos.logback.classic.LoggerContext;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimeBasedArchiveRemoverTest {

  private TimeBasedArchiveRemover remover;
  private final String TIMEZONE_NAME = "GMT";
  private final String DATE_FORMAT = "yyyyMMdd";
  private final Date CLEAN_DATE = parseDate(DATE_FORMAT, "20181104");
  private final File[] RECENT_FILES = new File[] {
    new File("app_20181105.log"),
    new File("app_20181104.log"),
  };
  private final File[] EXPIRED_FILES = new File[] {
    new File("app_20181103.log"),
    new File("app_20181102.log"),
    new File("app_20181101.log"),
    new File("app_20180317.log"),
    new File("app_20171225.log"),
    new File("app_20160214.log"),
  };
  private final File[] DUMMY_FILES = Stream.concat(Stream.of(RECENT_FILES), Stream.of(EXPIRED_FILES)).toArray(File[]::new);

  @Test
  void cleanRemovesExpiredFiles() {
    FileProvider fileProvider = this.mockFileProvider(this.DUMMY_FILES, true);
    this.remover = this.createArchiveRemover(fileProvider);
    this.remover.clean(this.CLEAN_DATE);

    Stream.of(this.EXPIRED_FILES)
      .forEach(f -> verify(fileProvider).deleteFile(f));
  }

  @Test
  void cleanKeepsRecentFiles() {
    FileProvider fileProvider = this.mockFileProvider(this.DUMMY_FILES, true);
    this.remover = this.createArchiveRemover(fileProvider);
    this.remover.clean(this.CLEAN_DATE);

    Stream.of(this.RECENT_FILES)
      .forEach(f -> verify(fileProvider, never()).deleteFile(f));
  }

  @Test
  void cleanRemovesExpiredFilesOlderThanMaxHistory() {
    final int MAX_HISTORY = 2;
    FileProvider fileProvider = this.mockFileProvider(this.DUMMY_FILES, true);
    this.remover = this.createArchiveRemover(fileProvider);
    this.remover.setMaxHistory(MAX_HISTORY);
    this.remover.clean(this.CLEAN_DATE);

    Stream.of(this.EXPIRED_FILES)
      .skip(MAX_HISTORY)
      .forEach(f -> verify(fileProvider).deleteFile(f));
  }

  @Test
  void cleanKeepsMaxHistory() {
    final int MAX_HISTORY = 2;
    FileProvider fileProvider = this.mockFileProvider(this.DUMMY_FILES, true);
    this.remover = this.createArchiveRemover(fileProvider);
    this.remover.setMaxHistory(MAX_HISTORY);
    this.remover.clean(this.CLEAN_DATE);

    Stream.of(this.EXPIRED_FILES)
      .limit(MAX_HISTORY)
      .forEach(f -> verify(fileProvider, never()).deleteFile(f));
  }

  @Test
  void doesNotCleanWhenDirEmpty() {
    FileProvider fileProvider = this.mockFileProvider(new File[0], true);
    this.remover = this.createArchiveRemover(fileProvider);
    this.remover.clean(this.CLEAN_DATE);

    verify(fileProvider, never()).deleteFile(any(File.class));
  }

  @Test
  void doesNotCleanWhenNotFile() {
    FileProvider fileProvider = this.mockFileProvider(this.DUMMY_FILES, false);
    this.remover = this.createArchiveRemover(fileProvider);
    this.remover.clean(this.CLEAN_DATE);

    verify(fileProvider, never()).deleteFile(any(File.class));
  }

  @Test
  void removesParentDirWhenCleanRemovesAllFiles() {
    FileProvider fileProvider = this.mockFileProvider(new File[0], true);
    final String FILENAME_PATTERN = "%d{yyyy_MM, " + this.TIMEZONE_NAME + "}/%d{" + this.DATE_FORMAT + ", " + this.TIMEZONE_NAME + "}";
    this.remover = this.createArchiveRemover(fileProvider, FILENAME_PATTERN);
    this.remover.clean(this.parseDate(this.DATE_FORMAT, "20181101"));

    verify(fileProvider).deleteFile(new File("2018_11").getAbsoluteFile());
  }

  @Test
  void keepsParentDirWhenItStillHasFiles() {
    FileProvider fileProvider = this.mockFileProvider(new File[] { new File("2018_11/20181122.log") }, true);
    final String FILENAME_PATTERN = "%d{yyyy_MM, " + this.TIMEZONE_NAME + "}/%d{" + this.DATE_FORMAT + ", " + this.TIMEZONE_NAME + "}";
    this.remover = this.createArchiveRemover(fileProvider, FILENAME_PATTERN);
    this.remover.clean(this.parseDate(this.DATE_FORMAT, "20181101"));

    verify(fileProvider, never()).deleteFile(new File("2018_11").getAbsoluteFile());
  }

  @Test
  void cleanLimitsTotalSizeOfFiles() {
    final int MAX_HISTORY = 4;
    final long FILE_SIZE = 1024L;
    final int NUM_FILES_TO_KEEP = 3;

    FileProvider fileProvider = this.mockFileProvider(this.DUMMY_FILES, true);
    this.remover = this.createArchiveRemover(fileProvider);
    when(fileProvider.length(any(File.class))).thenReturn(FILE_SIZE);
    when(fileProvider.deleteFile(any(File.class))).thenReturn(true);
    this.remover.setTotalSizeCap(NUM_FILES_TO_KEEP * FILE_SIZE);
    this.remover.setMaxHistory(MAX_HISTORY);

    this.remover.clean(this.CLEAN_DATE);

    Stream.of(this.EXPIRED_FILES)
      .skip(MAX_HISTORY - NUM_FILES_TO_KEEP)
      .forEach(f -> verify(fileProvider).deleteFile(f));

    Stream.concat(Stream.of(this.RECENT_FILES), Stream.of(this.EXPIRED_FILES).limit(MAX_HISTORY - NUM_FILES_TO_KEEP))
      .forEach(f -> verify(fileProvider, never()).deleteFile(f));
  }

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
    final String FILENAME_PATTERN = "%d{" + this.DATE_FORMAT + ", " + this.TIMEZONE_NAME + "}.log";
    return this.createArchiveRemover(fileProvider, FILENAME_PATTERN);
  }

  private TimeBasedArchiveRemover createArchiveRemover(FileProvider fileProvider, String fileNamePattern) {
    LoggerContext context = new LoggerContext();
    RollingCalendar rollingCalendar = new RollingCalendar(this.DATE_FORMAT);
    FileNamePattern filePattern = new FileNamePattern(fileNamePattern, context);
    TimeBasedArchiveRemover archiveRemover = new TimeBasedArchiveRemover(filePattern, rollingCalendar, fileProvider);
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
