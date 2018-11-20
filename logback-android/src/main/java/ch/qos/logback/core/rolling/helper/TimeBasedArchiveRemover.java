/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2015, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core.rolling.helper;

import java.io.File;
import java.io.FilenameFilter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.util.FileSize;

public class TimeBasedArchiveRemover extends ContextAwareBase implements ArchiveRemover {

  protected final FileNamePattern fileNamePattern;
  private final RollingCalendar rc;
  private int maxHistory = CoreConstants.UNBOUND_HISTORY;
  private long totalSizeCap = CoreConstants.UNBOUNDED_TOTAL_SIZE_CAP;
  private final boolean parentClean;
  private final FileProvider fileProvider;
  private final SimpleDateFormat dateFormatter;
  private final Pattern pathPattern;

  public TimeBasedArchiveRemover(FileNamePattern fileNamePattern, RollingCalendar rc, FileProvider fileProvider) {
    this.fileNamePattern = fileNamePattern;
    this.rc = rc;
    this.parentClean = fileNamePattern.convert(new Date()).contains("/");
    this.fileProvider = fileProvider;
    this.dateFormatter = getDateFormatter(fileNamePattern);
    this.pathPattern = Pattern.compile(fileNamePattern.toRegex(true));
  }

  public void clean(final Date now) {
    File resolvedFile = new File(this.fileNamePattern.convert(now));
    File parentDir = resolvedFile.getAbsoluteFile().getParentFile();

    // TODO: Add FileProvider#list() interface to get only the filenames (this would
    // be cheaper than getting all File instances for every file in the parent dir).
    // Then use functions to get all the expired files and all the recent files.
    // Pass the expired files to FileProvider#delete(). Pass the recent files
    // to capTotalSize().

    // FIXME: Recursively search subdirectories (e.g., for "%d{yyyy/MM/dd}/%i.log")

    File[] expiredFiles = this.fileProvider.listFiles(parentDir, this.createFileFilter(now, true));

    for (File f : expiredFiles) {
      this.delete(f);
    }

    if (this.totalSizeCap != CoreConstants.UNBOUNDED_TOTAL_SIZE_CAP && this.totalSizeCap > 0) {
      File[] recentFiles = this.fileProvider.listFiles(parentDir, this.createFileFilter(now, false));
      this.capTotalSize(recentFiles, now);
    }

    if (this.parentClean && (this.fileProvider.listFiles(parentDir, null).length == 0)) {
      this.delete(parentDir);
    }
  }

  private boolean delete(File file) {
    boolean ok = this.fileProvider.deleteFile(file);
    if (!ok) {
      addWarn("cannot delete " + file);
    }
    return ok;
  }

  private void capTotalSize(File[] files, Date date) {
    long totalSize = 0;
    long totalRemoved = 0;

    descendingSort(files, date);
    for (File f : files) {
      long size = this.fileProvider.length(f);
      if (totalSize + size > this.totalSizeCap) {
        addInfo("Deleting [" + f + "]" + " of size " + new FileSize(size));
        if (!delete(f)) {
          size = 0;
        }
        totalRemoved += size;
      }
      totalSize += size;
    }

    addInfo("Removed  "+ new FileSize(totalRemoved) + " of files");
  }

  protected void descendingSort(File[] matchingFileArray, Date date) {
    Arrays.sort(matchingFileArray, new Comparator<File>() {
      @Override
      public int compare(final File f1, final File f2) {

        Date date1 = parseDateFromFilename(f1.getAbsolutePath());
        Date date2 = parseDateFromFilename(f2.getAbsolutePath());

        // newest to oldest
        return date2.compareTo(date1);
      }
    });
  }

  public void setMaxHistory(int maxHistory) {
    this.maxHistory = maxHistory;
  }

  public void setTotalSizeCap(long totalSizeCap) {
    this.totalSizeCap = totalSizeCap;
  }

  public String toString() {
    return "c.q.l.core.rolling.helper.TimeBasedArchiveRemover";
  }

  public Future<?> cleanAsynchronously(Date now) {
    ArchiveRemoverRunnable runnable = new ArchiveRemoverRunnable(now);
    ExecutorService executorService = context.getScheduledExecutorService();
    Future<?> future = executorService.submit(runnable);
    return future;
  }

  private Date parseDate(String dateString) {
    Date date = null;
    try {
      date = this.dateFormatter.parse(dateString);
    } catch (ParseException e) {
      // should not happen
      e.printStackTrace();
    }
    return date;
  }

  private Date parseDateFromFilename(String filename) {
    Date date = null;
    Matcher m = this.pathPattern.matcher(filename);
    if (m.find() && m.groupCount() >= 1) {
      String dateString = m.group(1);
      date = this.parseDate(dateString);
    }
    return date;
  }

  private FilenameFilter createFileFilter(final Date now, boolean isExpiryCheck) {
    return new FilenameFilter() {
      public boolean accept(File dir, String baseName) {
        final TimeBasedArchiveRemover _parent = TimeBasedArchiveRemover.this;
        File file = new File(dir, baseName);
        if (!_parent.fileProvider.isFile(file)) {
          return false;
        }

        boolean isExpiredFile = false;
        Matcher m = _parent.pathPattern.matcher(file.getAbsolutePath());
        if (m.find() && m.groupCount() >= 1) {
          String dateString = m.group(1);
          Date fileDate = _parent.parseDate(dateString);
          Date expiry = _parent.rc.getEndOfNextNthPeriod(now, -_parent.maxHistory);
          isExpiredFile = isExpiryCheck
                        ? fileDate.compareTo(expiry) < 0
                        : fileDate.compareTo(expiry) > 0;
        }
        return isExpiredFile;
      }
    };
  }

  private SimpleDateFormat getDateFormatter(FileNamePattern fileNamePattern) {
    final DateTokenConverter<Object> dateStringConverter = fileNamePattern.getPrimaryDateTokenConverter();
    final String datePattern = dateStringConverter.getDatePattern();
    final SimpleDateFormat dateFormatter = new SimpleDateFormat(datePattern, Locale.US);
    TimeZone timeZone = dateStringConverter.getTimeZone();
    if (timeZone != null) {
      dateFormatter.setTimeZone(timeZone);
    }
    return dateFormatter;
  }

  private class ArchiveRemoverRunnable implements Runnable {
    Date now;
    ArchiveRemoverRunnable(Date now) {
      this.now = now;
    }

    @Override
    public void run() {
      clean(now);
    }
  }
}
