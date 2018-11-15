package ch.qos.logback.core.rolling.helper;

import org.junit.Test;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import ch.qos.logback.classic.LoggerContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class TimeBasedArchiveRemoverTest {

  @Test
  public void rolloverDailyFileRemovesExpiredFiles() throws ParseException {
    final String DATE_PATTERN = "yyyyMMdd";
    final String FILENAME_PATTERN = "%d{" + DATE_PATTERN + "}.log";
    final LoggerContext context = new LoggerContext();
    final RollingCalendar rollingCalendar = new RollingCalendar(DATE_PATTERN);
    final TimeBasedArchiveRemover remover = spy(new TimeBasedArchiveRemover(new FileNamePattern(FILENAME_PATTERN, context), rollingCalendar));
    final File[] dummyFiles = new File[] {
      new File("20181101.log"),
      new File("20181102.log"),
      new File("20181103.log"),
      new File("20181104.log")
    };

    doReturn(true).when(remover).delete(any(File.class));
    doReturn(dummyFiles).when(remover).getFilesInPeriod(any(Date.class));

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    Date date = dateFormat.parse("2018/11/04");
    remover.clean(date);

    verify(remover, atLeastOnce()).getFilesInPeriod(any(Date.class));

    for (File f : dummyFiles) {
      verify(remover, atLeastOnce()).delete(f);
    }
  }

  @Test
  public void rolloverDailyFileRemovesExpiredFilesButKeepsMaxHistory() {

  }

  @Test
  public void rolloverDailyDirectoryRemovesExpiredFiles() {
    final String FILENAME_PATTERN = "%d{yyyyMMdd}/app.log";
  }

  @Test
  public void rolloverDailyDirectoryRemovesEmptyDirectory() {
    final String FILENAME_PATTERN = "%d{yyyyMMdd}/app.log";
  }

  @Test
  public void rolloverDailyDirectoryDoesNotRemoveNonEmptyDirectory() {
    final String FILENAME_PATTERN = "%d{yyyyMMdd}/app.log";
  }

}
