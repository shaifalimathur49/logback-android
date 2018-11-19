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

  private final FileNamePattern fileNamePattern;
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

  public void clean(final Date now) {
    File resolvedFile = new File(this.fileNamePattern.convert(now));
    File parentDir = resolvedFile.getAbsoluteFile().getParentFile();
    File[] filesToDelete = this.fileProvider.listFiles(parentDir, this.createFileFilter(now));

    for (File f : filesToDelete) {
      this.delete(f);
    }

    if (this.totalSizeCap != CoreConstants.UNBOUNDED_TOTAL_SIZE_CAP && this.totalSizeCap > 0) {
      this.capTotalSize(now);
    }

    if (this.parentClean && (this.fileProvider.listFiles(parentDir, null).length == 0)) {
      this.delete(parentDir);
    }
  }

  protected File[] getFilesInPeriod(Date now) {
    File resolvedFile = new File(this.fileNamePattern.convert(now));
    File parentDir = resolvedFile.getAbsoluteFile().getParentFile();
    return this.fileProvider.listFiles(parentDir, this.createFileFilter(now));
  }

  protected String createStemRegex(final Date dateOfPeriodToClean) {
    String regex = fileNamePattern.toRegexForFixedDate(dateOfPeriodToClean);
    return FileFilterUtil.afterLastSlash(regex);
  }

  private boolean delete(File file) {
    boolean ok = this.fileProvider.deleteFile(file);
    if (!ok) {
      addWarn("cannot delete " + file);
    }
    return ok;
  }

  private void capTotalSize(Date now) {
    long totalSize = 0;
    long totalRemoved = 0;
    for (int offset = 0; offset < maxHistory; offset++) {
      Date date = rc.getEndOfNextNthPeriod(now, -offset);
      File[] matchingFileArray = getFilesInPeriod(date);
      descendingSort(matchingFileArray, date);
      for (File f : matchingFileArray) {
        long size = this.fileProvider.length(f);
        if (totalSize + size > totalSizeCap) {
          addInfo("Deleting [" + f + "]" + " of size " + new FileSize(size));
          totalRemoved += size;
          if (!delete(f)) {
            size = 0;
          }
        }
        totalSize += size;
      }
    }
    addInfo("Removed  "+ new FileSize(totalRemoved) + " of files");
  }

  protected void descendingSort(File[] matchingFileArray, Date date) {
    // nothing to do in super class
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

  private FilenameFilter createFileFilter(final Date now) {
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
          isExpiredFile = fileDate.compareTo(expiry) < 0;
        }
        return isExpiredFile;
      }
    };
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
