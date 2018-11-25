package ch.qos.logback.core.rolling.helper;

import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Stream;

import ch.qos.logback.classic.LoggerContext;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class TimeBasedArchiveRemoverTest {

  private final String TIMEZONE_NAME = "GMT";
  private final String DATE_FORMAT = "yyyyMMdd";
  private final Date EXPIRY = parseDate(DATE_FORMAT, "20191104");
  private final String FILENAME_PATTERN = "%d{yyyy/MM, aux}/app_%d{" + DATE_FORMAT + ", " + TIMEZONE_NAME + "}.log";

  private File[] expiredFiles;
  private File[] recentFiles;

  private void setupTmpDir(TemporaryFolder tmpDir) throws IOException {
    File[] dirs = new File[] {
      tmpDir.newFolder("2016", "02"),
      tmpDir.newFolder("2017", "12"),
      tmpDir.newFolder("2018", "03"),
      tmpDir.newFolder("2019", "11"),
      tmpDir.newFolder("2019", "10"),
    };
    recentFiles = new File[] {
      tmpDir.newFile("2019/11/app_20191105.log"),
      tmpDir.newFile("2019/11/app_20191104.log"),
    };
    expiredFiles = new File[] {
      tmpDir.newFile("2019/11/app_20191103.log"),
      tmpDir.newFile("2019/11/app_20191102.log"),
      tmpDir.newFile("2019/10/app_20191001.log"),
      tmpDir.newFile("2018/03/app_20180317.log"),
      tmpDir.newFile("2017/12/app_20171225.log"),
      tmpDir.newFile("2016/02/app_20160214.log"),
    };
    Stream.of(dirs).forEach(File::deleteOnExit);
    Stream.of(recentFiles).forEach(File::deleteOnExit);
    Stream.of(expiredFiles).forEach(File::deleteOnExit);
  }

  @EnableRuleMigrationSupport
  abstract class BaseTest {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    TimeBasedArchiveRemover remover;
    FileProvider fileProvider;

    @BeforeEach
    void baseSetup() throws IOException {
      setupTmpDir(tmpDir);
      fileProvider = mockFileProvider();
      remover = mockArchiveRemover(tmpDir.getRoot().getAbsolutePath() + File.separator + FILENAME_PATTERN, fileProvider);
    }
  }

  @Nested
  class ExpiredFileRemovalTest extends BaseTest {

    @Test
    void removesOnlyExpiredFiles() {
      remover.clean(EXPIRY);
      Stream.of(expiredFiles)
        .forEach(f -> verify(fileProvider).deleteFile(f));
      Stream.of(recentFiles)
        .forEach(f -> verify(fileProvider, never()).deleteFile(f));
    }

    @Test
    void removesOnlyExpiredFilesOlderThanMaxHistory() {
      final int MAX_HISTORY = 2;
      remover.setMaxHistory(MAX_HISTORY);
      remover.clean(EXPIRY);

      Stream.of(expiredFiles)
        .skip(MAX_HISTORY)
        .forEach(f -> verify(fileProvider).deleteFile(f));
      Stream.of(expiredFiles)
        .limit(MAX_HISTORY)
        .forEach(f -> verify(fileProvider, never()).deleteFile(f));
    }
  }

  @Nested
  class EmptyParentDirRemovalTest extends BaseTest {

    @Test
    void removesParentDirWhenEmpty() throws IOException {
      File[] emptyDirs = new File[] {
        tmpDir.newFolder("empty_2018", "08"),
        tmpDir.newFolder("empty_2018", "12"),
        tmpDir.newFolder("empty_2019", "01"),
      };
      Stream.of(emptyDirs).forEach(File::deleteOnExit);

      remover = mockArchiveRemover(tmpDir.getRoot().getAbsolutePath() + File.separator + "empty_%d{yyyy/MM}" + File.separator + "%d.log", fileProvider);
      remover.clean(EXPIRY);

      Stream.of(emptyDirs).forEach(d -> verify(fileProvider).deleteFile(d));
    }

    @Test
    void keepsParentDirWhenNonEmpty() {
      // Setting an expiration date of 0 would cause no files to be deleted
      remover.clean(new Date(0));

      verify(fileProvider, never()).deleteFile(any(File.class));
    }
  }

  @Nested
  class TotalSizeCapTest extends BaseTest {
    private final int MAX_HISTORY = 4;
    private final int NUM_FILES_TO_KEEP = 3;

    @BeforeEach
    void setup() {
      final long FILE_SIZE = 1024L;
// XXX: Need to use doReturn().when() here to avoid NPE
//      when(fileProvider.length(any(File.class))).thenReturn(FILE_SIZE);
//      when(fileProvider.deleteFile(any(File.class))).thenReturn(true);
      doReturn(FILE_SIZE).when(fileProvider).length(any(File.class));
      doReturn(true).when(fileProvider).deleteFile(any(File.class));
      remover.setTotalSizeCap(NUM_FILES_TO_KEEP * FILE_SIZE);
      remover.setMaxHistory(MAX_HISTORY);
    }

    @Test
    void removesOlderFilesThatExceedTotalSizeCap() {
      remover.clean(EXPIRY);
      Stream.of(expiredFiles)
        .skip(MAX_HISTORY - NUM_FILES_TO_KEEP)
        .forEach(f -> verify(fileProvider).deleteFile(f));
    }

    @Test
    void keepsRecentFilesAndOlderFilesWithinTotalSizeCap() {
      remover.clean(EXPIRY);
      Stream.concat(Stream.of(recentFiles), Stream.of(expiredFiles).limit(MAX_HISTORY - NUM_FILES_TO_KEEP))
        .forEach(f -> verify(fileProvider, never()).deleteFile(f));
    }
  }

  private Date parseDate(String format, String value) {
    final SimpleDateFormat dateFormat = new SimpleDateFormat(format, Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone(TIMEZONE_NAME));
    Date date;
    try {
      date = dateFormat.parse(value);
    } catch (ParseException e) {
      date = null;
    }
    assertNotNull(date);
    return date;
  }

  private TimeBasedArchiveRemover mockArchiveRemover(String filenamePattern, FileProvider fileProvider) {
    LoggerContext context = new LoggerContext();
    RollingCalendar rollingCalendar = new RollingCalendar(DATE_FORMAT);
    FileNamePattern filePattern = new FileNamePattern(filenamePattern, context);
    TimeBasedArchiveRemover archiveRemover = new TimeBasedArchiveRemover(filePattern, rollingCalendar, fileProvider);
    archiveRemover.setContext(context);
    return spy(archiveRemover);
  }

  private FileProvider mockFileProvider() {
    return spy(new DefaultFileProvider());
  }
}
