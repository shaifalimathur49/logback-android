package ch.qos.logback.core.rolling.helper;

import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@EnableRuleMigrationSupport
class TimeBasedArchiveRemoverTest {

  private final String TIMEZONE_NAME = "GMT";
  private final String DATE_FORMAT = "yyyyMMdd";
  private final Date CLEAN_DATE = parseDate(DATE_FORMAT, "20181104");
  private final String FILENAME_PATTERN = "%d{yyyy/MM}/app_%d{" + this.DATE_FORMAT + ", " + this.TIMEZONE_NAME + "}.log";

//  private final File[] RECENT_FILES = new File[] {
//    new File("app_20181105.log"),
//    new File("app_20181104.log"),
//  };
//  private final File[] EXPIRED_FILES = new File[] {
//    new File("app_20181103.log"),
//    new File("app_20181102.log"),
//    new File("app_20181101.log"),
//    new File("app_20180317.log"),
//    new File("app_20171225.log"),
//    new File("app_20160214.log"),
//  };
//  private final File[] DUMMY_FILES = Stream.concat(Stream.of(RECENT_FILES), Stream.of(EXPIRED_FILES)).toArray(File[]::new);

  private File parentDir;
  private File[] allFiles;
  private File[] expiredFiles;
  private File[] recentFiles;

  private void setupTmpDir(TemporaryFolder tmpDir) throws IOException {
    this.parentDir = tmpDir.newFolder("2018", "11");
    this.recentFiles = new File[] {
      tmpDir.newFile("2018/11/app_20181105.log"),
      tmpDir.newFile("2018/11/app_20181104.log"),
    };
    this.expiredFiles = new File[] {
      tmpDir.newFile("2018/11/app_20181103.log"),
      tmpDir.newFile("2018/11/app_20181102.log"),
      tmpDir.newFile("2018/11/app_20181101.log"),
      tmpDir.newFile("2018/11/app_20180317.log"),
      tmpDir.newFile("2018/11/app_20171225.log"),
      tmpDir.newFile("2018/11/app_20160214.log"),
    };
    this.allFiles = Stream.concat(Stream.of(this.recentFiles), Stream.of(this.expiredFiles)).toArray(File[]::new);
  }

  @EnableRuleMigrationSupport
  @Nested
  class ExpiredFileRemovalTest {

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();
    private TimeBasedArchiveRemover remover;
    private FileProvider fileProvider;

    @BeforeEach
    void setup() throws IOException {
      setupTmpDir(tmpDir);
      fileProvider = mockFileProvider(allFiles, true);
      remover = createArchiveRemover(tmpDir.getRoot().getAbsolutePath(), fileProvider);
    }

    @Test
    void removesOnlyExpiredFiles() {
      remover.clean(CLEAN_DATE);
      Stream.of(expiredFiles)
        .forEach(f -> verify(fileProvider).deleteFile(f));
      Stream.of(recentFiles)
        .forEach(f -> verify(fileProvider, never()).deleteFile(f));
    }
  }

//  @Nested
//  class MiscTest {
//
//    @Test
//    void doesNotCleanWhenDirEmpty() {
//      FileProvider fileProvider = mockFileProvider(new File[0], true);
//      TimeBasedArchiveRemover remover = createArchiveRemover(fileProvider);
//      remover.clean(CLEAN_DATE);
//
//      verify(fileProvider, never()).deleteFile(any(File.class));
//    }
//
//    @Test
//    void doesNotCleanWhenNotFile() {
//      FileProvider fileProvider = mockFileProvider(DUMMY_FILES, false);
//      TimeBasedArchiveRemover remover = createArchiveRemover(fileProvider);
//      remover.clean(CLEAN_DATE);
//
//      verify(fileProvider, never()).deleteFile(any(File.class));
//    }
//  }
//
//  @Nested
//  class MaxHistoryTest {
//    final int MAX_HISTORY = 2;
//    private TimeBasedArchiveRemover remover;
//    private FileProvider fileProvider;
//
//    @BeforeEach
//    void setup() {
//      fileProvider = mockFileProvider(DUMMY_FILES, true);
//      remover = createArchiveRemover(fileProvider);
//      remover.setMaxHistory(MAX_HISTORY);
//    }
//
//    @Test
//    void removesExpiredFilesOlderThanMaxHistory() {
//      remover.clean(CLEAN_DATE);
//      Stream.of(EXPIRED_FILES)
//        .skip(MAX_HISTORY)
//        .forEach(f -> verify(fileProvider).deleteFile(f));
//    }
//
//    @Test
//    void keepsMaxHistory() {
//      remover.clean(CLEAN_DATE);
//      Stream.of(EXPIRED_FILES)
//        .limit(MAX_HISTORY)
//        .forEach(f -> verify(fileProvider, never()).deleteFile(f));
//    }
//  }
//
//  @Nested
//  class ParentCleanRemovalTest {
//
//    @Test
//    void removesParentDirWhenCleanRemovesAllFiles() {
//      FileProvider fileProvider = mockFileProvider(new File[0], true);
//      final String FILENAME_PATTERN = "%d{yyyy_MM, " + TIMEZONE_NAME + "}/%d{" + DATE_FORMAT + ", " + TIMEZONE_NAME + "}";
//      TimeBasedArchiveRemover remover = createArchiveRemover(fileProvider, FILENAME_PATTERN);
//      remover.clean(parseDate(DATE_FORMAT, "20181101"));
//
//      verify(fileProvider).deleteFile(new File("2018_11").getAbsoluteFile());
//    }
//
//    @Test
//    void keepsParentDirWhenItStillHasFiles() {
//      FileProvider fileProvider = mockFileProvider(new File[]{new File("2018_11/20181122.log")}, true);
//      final String FILENAME_PATTERN = "%d{yyyy_MM, " + TIMEZONE_NAME + "}/%d{" + DATE_FORMAT + ", " + TIMEZONE_NAME + "}";
//      TimeBasedArchiveRemover remover = createArchiveRemover(fileProvider, FILENAME_PATTERN);
//      remover.clean(parseDate(DATE_FORMAT, "20181101"));
//
//      verify(fileProvider, never()).deleteFile(new File("2018_11").getAbsoluteFile());
//    }
//  }
//
//  @Nested
//  class TotalSizeCapTest {
//    private TimeBasedArchiveRemover remover;
//    private FileProvider fileProvider;
//    private final int MAX_HISTORY = 4;
//    private final int NUM_FILES_TO_KEEP = 3;
//
//    @BeforeEach
//    void setup() {
//      final long FILE_SIZE = 1024L;
//      this.fileProvider = mockFileProvider(DUMMY_FILES, true);
//      this.remover = createArchiveRemover(fileProvider);
//      when(fileProvider.length(any(File.class))).thenReturn(FILE_SIZE);
//      when(fileProvider.deleteFile(any(File.class))).thenReturn(true);
//      this.remover.setTotalSizeCap(NUM_FILES_TO_KEEP * FILE_SIZE);
//      this.remover.setMaxHistory(MAX_HISTORY);
//    }
//
//    @Test
//    void removesOlderFilesThatExceedTotalSizeCap() {
//      this.remover.clean(CLEAN_DATE);
//      Stream.of(EXPIRED_FILES)
//        .skip(MAX_HISTORY - NUM_FILES_TO_KEEP)
//        .forEach(f -> verify(fileProvider).deleteFile(f));
//    }
//
//    @Test
//    void keepsRecentFilesAndOlderFilesWithinTotalSizeCap() {
//      this.remover.clean(CLEAN_DATE);
//      Stream.concat(Stream.of(RECENT_FILES), Stream.of(EXPIRED_FILES).limit(MAX_HISTORY - NUM_FILES_TO_KEEP))
//        .forEach(f -> verify(this.fileProvider, never()).deleteFile(f));
//    }
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

  private TimeBasedArchiveRemover createArchiveRemover(String rootDir, FileProvider fileProvider) {
    return this.createArchiveRemover(fileProvider, rootDir + File.separator + FILENAME_PATTERN);
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
//    FileProvider fileProvider = mock(FileProvider.class);
//
//    when(fileProvider.listFiles(any(File.class), any(FilenameFilter.class))).then(
//      new Answer<File[]>() {
//        public File[] answer(InvocationOnMock invocation) {
//          FilenameFilter filter = invocation.getArgument(1);
//
//          ArrayList<File> foundFiles = new ArrayList<File>();
//          for (File f : files) {
//            if (filter.accept(f.getParentFile(), f.getName())) {
//              foundFiles.add(f);
//            }
//          }
//          return foundFiles.toArray(new File[0]);
//        }
//      }
//    );
//
//    when(fileProvider.listFiles(any(File.class), isNull(FilenameFilter.class))).thenReturn(files);
//    when(fileProvider.list(any(File.class), isNull(FilenameFilter.class))).thenReturn(Stream.of(files).map(File::getName).toArray(String[]::new));
//    when(fileProvider.isFile(any(File.class))).thenReturn(isFileRval);
//    when(fileProvider.exists(any(File.class))).thenReturn(true);
//    when(fileProvider.isDirectory(argThat((File f) -> f.getName().isEmpty()))).thenReturn(true);
//    return fileProvider;
    return spy(new DefaultFileProvider());
  }
}
