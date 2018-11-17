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
import ch.qos.logback.core.pattern.Converter;
import ch.qos.logback.core.pattern.LiteralConverter;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.util.FileSize;

public class TimeBasedArchiveRemover extends ContextAwareBase implements ArchiveRemover {

  private final FileNamePattern fileNamePattern;
  private final RollingCalendar rc;
  private int maxHistory = CoreConstants.UNBOUND_HISTORY;
  private long totalSizeCap = CoreConstants.UNBOUNDED_TOTAL_SIZE_CAP;
  private final boolean parentClean;
  private final FileProvider fileProvider;

  public TimeBasedArchiveRemover(FileNamePattern fileNamePattern, RollingCalendar rc, FileProvider fileProvider) {
    this.fileNamePattern = fileNamePattern;
    this.rc = rc;
    this.parentClean = computeParentCleaningFlag(fileNamePattern);
    this.fileProvider = fileProvider;
  }

  public void clean(final Date now) {
    final DateTokenConverter<Object> dateStringConverter = this.fileNamePattern.getPrimaryDateTokenConverter();
    final String datePattern = dateStringConverter.getDatePattern();
    final SimpleDateFormat dateFormatter = new SimpleDateFormat(datePattern, Locale.US);
    TimeZone timeZone = dateStringConverter.getTimeZone();
    if (timeZone != null) {
      dateFormatter.setTimeZone(timeZone);
    }

    final Pattern pathPattern = Pattern.compile(this.fileNamePattern.toRegex(true));
    File resolvedFile = new File(this.fileNamePattern.convertMultipleArguments(now, 0));
    File parentDir = resolvedFile.getAbsoluteFile().getParentFile();
    File[] filesToDelete = this.fileProvider.listFiles(parentDir, new FilenameFilter() {
      public boolean accept(File dir, String baseName) {
        File file = new File(dir, baseName);
        if (!TimeBasedArchiveRemover.this.fileProvider.isFile(file)) {
          return false;
        }

        boolean isExpiredFile = false;
        Matcher m = pathPattern.matcher(file.getAbsolutePath());
        if (m.find() && m.groupCount() >= 1) {
          String dateString = m.group(1);
          try {
            Date fileDate = dateFormatter.parse(dateString);
            isExpiredFile = fileDate.compareTo(now) < 0;
          } catch (ParseException e) {
            e.printStackTrace();
          }
        }
        return isExpiredFile;
      }
    });

    for (File f : filesToDelete) {
      delete(f);
    }

    if (this.parentClean && (this.fileProvider.listFiles(parentDir, null).length == 0)) {
      delete(parentDir);
    }
  }

  protected File[] getFilesInPeriod(Date dateOfPeriodToClean) {
    File archive0 = new File(fileNamePattern.convertMultipleArguments(dateOfPeriodToClean, 0));
    File parentDir = archive0.getAbsoluteFile().getParentFile();
    String stemRegex = createStemRegex(dateOfPeriodToClean);
    File[] matchingFileArray = FileFilterUtil.filesInFolderMatchingStemRegex(parentDir, stemRegex);
    return matchingFileArray;
  }

  protected String createStemRegex(final Date dateOfPeriodToClean) {
    String regex = fileNamePattern.toRegexForFixedDate(dateOfPeriodToClean);
    return FileFilterUtil.afterLastSlash(regex);
  }

  //@VisibleForTest
  boolean delete(File file) {
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
        long size = f.length();
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

  /**
   * Computes whether the fileNamePattern may create sub-folders.
   * @param fileNamePattern
   * @return
   */
  private boolean computeParentCleaningFlag(FileNamePattern fileNamePattern) {
    DateTokenConverter<Object> dtc = fileNamePattern.getPrimaryDateTokenConverter();
    // if the date pattern has a /, then we need parent cleaning
    if (dtc.getDatePattern().indexOf('/') != -1) {
      return true;
    }
    // if the literal string subsequent to the dtc contains a /, we also
    // need parent cleaning

    Converter<Object> p = fileNamePattern.headTokenConverter;

    // find the date converter
    while (p != null) {
      if (p instanceof DateTokenConverter) {
        break;
      }
      p = p.getNext();
    }

    while (p != null) {
      if (p instanceof LiteralConverter) {
        String s = p.convert(null);
        if (s.indexOf('/') != -1) {
          return true;
        }
      }
      p = p.getNext();
    }

    // no '/', so we don't need parent cleaning
    return false;
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
    ArhiveRemoverRunnable runnable = new ArhiveRemoverRunnable(now);
    ExecutorService executorService = context.getScheduledExecutorService();
    Future<?> future = executorService.submit(runnable);
    return future;
  }

  private class ArhiveRemoverRunnable implements Runnable {
    Date now;
    ArhiveRemoverRunnable(Date now) {
      this.now = now;
    }

    @Override
    public void run() {
      clean(now);
      if (totalSizeCap != CoreConstants.UNBOUNDED_TOTAL_SIZE_CAP && totalSizeCap > 0) {
        capTotalSize(now);
      }
    }
  }
}
