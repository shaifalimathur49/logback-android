/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2013, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 * or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core.rolling;

import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.pattern.SpacePadder;
import ch.qos.logback.core.rolling.helper.RollingCalendar;
import ch.qos.logback.core.util.FileSize;
import ch.qos.logback.core.util.FixedRateInvocationGate;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.LocalDate;

import static ch.qos.logback.core.CoreConstants.DAILY_DATE_PATTERN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TimeBasedRollingWithArchiveRemoval_Test extends ScaffoldingForRollingTests {
  private String MONTHLY_CRONOLOG_DATE_PATTERN = "yyyy/MM";

  private RollingFileAppender<Object> rfa = new RollingFileAppender<Object>();
  private TimeBasedRollingPolicy<Object> tbrp = new TimeBasedRollingPolicy<Object>();

  // by default tbfnatp is an instance of DefaultTimeBasedFileNamingAndTriggeringPolicy
  private TimeBasedFileNamingAndTriggeringPolicy<Object> tbfnatp = new DefaultTimeBasedFileNamingAndTriggeringPolicy<Object>();

  private static long MILLIS_IN_MINUTE = 60 * 1000;
  private static long MILLIS_IN_HOUR = 60 * MILLIS_IN_MINUTE;
  static long MILLIS_IN_DAY = 24 * MILLIS_IN_HOUR;
  private static long MILLIS_IN_MONTH = (long) ((365.242199 / 12) * MILLIS_IN_DAY);
  private static int MONTHS_IN_YEAR = 12;

  // Wed Mar 23 23:07:05 CET 2016
  private static final long WED_2016_03_23_T_230705_CET = 1458770825333L;
  private static final long THU_2016_03_17_T_230330_CET = 1458252210975L;

  private int slashCount = 0;
  private int ticksPerPeriod = 216;
  private ConfigParameters cp; // initialized in setup

  private FixedRateInvocationGate fixedRateInvocationGate = new FixedRateInvocationGate(ticksPerPeriod/2);

  @Before
  public void setUp() {
    super.setUp();
    this.cp = new ConfigParameters(currentTime);
  }

  private int computeSlashCount(String datePattern) {
    if (datePattern == null)
      return 0;
    else {
      int count = 0;
      for (int i = 0; i < datePattern.length(); i++) {
        char c = datePattern.charAt(i);
        if (c == '/')
          count++;
      }
      return count;
    }
  }


  // test that the number of files at the end of the test is same as the expected number taking into account end dates
  // near the beginning of a new year. This test has been run in a loop with start date varying over a two years
  // with success.
  @Test
  public void monthlyRolloverOverManyPeriods() {

    this.slashCount = computeSlashCount(MONTHLY_CRONOLOG_DATE_PATTERN);
    int maxHistory = 2;
    int simulatedNumberOfPeriods = 30;
    String fileNamePattern = randomOutputDir + "/%d{" + MONTHLY_CRONOLOG_DATE_PATTERN + "}/clean.txt.zip";
    cp.maxHistory(maxHistory).fileNamePattern(fileNamePattern).simulatedNumberOfPeriods(simulatedNumberOfPeriods).periodDurationInMillis(MILLIS_IN_MONTH);

    long startTime = currentTime;
    long endTime = logOverMultiplePeriods(cp);
    int differenceInMonths = RollingCalendar.diffInMonths(startTime, endTime);
    Calendar startTimeAsCalendar = Calendar.getInstance();
    startTimeAsCalendar.setTimeInMillis(startTime);
    int indexOfStartPeriod = startTimeAsCalendar.get(Calendar.MONTH);
    boolean withExtraFolder = extraFolder(differenceInMonths, MONTHS_IN_YEAR, indexOfStartPeriod, maxHistory);

    checkFileCount(expectedCountWithFolders(maxHistory, withExtraFolder));
  }

  private long generateDailyRollover(ConfigParameters cp) {
    this.slashCount = computeSlashCount(DAILY_DATE_PATTERN);
    cp.fileNamePattern(randomOutputDir + "clean-%d{" + DAILY_DATE_PATTERN + "}.txt");
    return logOverMultiplePeriods(cp);
  }

  private long generateDailyRolloverAndCheckFileCount(ConfigParameters cp) {
    long millisAtEnd = generateDailyRollover(cp);
    int periodBarriersCrossed = computeCrossedDayBarriers(currentTime, millisAtEnd);
    checkFileCount(expectedCountWithoutFoldersWithInactivity(cp.maxHistory, periodBarriersCrossed, cp.startInactivity + cp.numInactivityPeriods));
    return millisAtEnd;
  }

  @Test
  public void checkCrossedPeriodsWithDSTBarrier() {
    long SAT_2016_03_26_T_230705_CET = WED_2016_03_23_T_230705_CET + 3 * CoreConstants.MILLIS_IN_ONE_DAY;
    System.out.println("SAT_2016_03_26_T_230705_CET " + new Date(SAT_2016_03_26_T_230705_CET));
    long MON_2016_03_28_T_000705_CET = SAT_2016_03_26_T_230705_CET + CoreConstants.MILLIS_IN_ONE_DAY;
    System.out.println("MON_2016_03_28_T_000705_CET " + new Date(MON_2016_03_28_T_000705_CET));

    int result = computeCrossedDayBarriers(SAT_2016_03_26_T_230705_CET, MON_2016_03_28_T_000705_CET, "CET");
    assertEquals(2, result);
  }

  private int computeCrossedDayBarriers(long currentTime, long millisAtEnd) {
    return computeCrossedDayBarriers(currentTime, millisAtEnd, null);
  }

  private int computeCrossedDayBarriers(long currentTime, long millisAtEnd, String timeZoneID) {
    DateTimeZone dateTimeZone = DateTimeZone.getDefault();
    if (timeZoneID != null) {
      dateTimeZone = DateTimeZone.forID(timeZoneID);

    }
    LocalDate startInstant = new LocalDate(currentTime, dateTimeZone);
    LocalDate endInstant = new LocalDate(millisAtEnd, dateTimeZone);
    Days days = Days.daysBetween(startInstant, endInstant);
    return days.getDays();
  }

  @Test
  public void checkCleanupForBasicDailyRollover() {
    cp.maxHistory(6).simulatedNumberOfPeriods(30).startInactivity(10).numInactivityPeriods(1);
    generateDailyRolloverAndCheckFileCount(cp);
  }

  @Test
  public void checkCleanupForBasicDailyRolloverWithSizeCap() {
    long bytesOutputPerPeriod = 15984;
    int sizeInUnitsOfBytesPerPeriod = 2;

    cp.maxHistory(5).simulatedNumberOfPeriods(10).sizeCap(sizeInUnitsOfBytesPerPeriod * bytesOutputPerPeriod+1000);
    generateDailyRollover(cp);
    checkFileCount(sizeInUnitsOfBytesPerPeriod+1);
  }

  @Test
  public void checkThatSmallTotalSizeCapLeavesAtLeastOneArhcive() {
    long WED_2016_03_23_T_131345_CET = WED_2016_03_23_T_230705_CET - 10 * CoreConstants.MILLIS_IN_ONE_HOUR;

    //long bytesOutputPerPeriod = 15984;

    cp = new ConfigParameters(WED_2016_03_23_T_131345_CET);
    final int verySmallCapSize = 1;
    cp.maxHistory(5).simulatedNumberOfPeriods(3).sizeCap(verySmallCapSize);
    generateDailyRollover(cp);
    checkFileCountAtMost(1);
  }

  @Test
  public void checkCleanupForBasicDailyRolloverWithMaxSize() {
    cp.maxHistory(6).simulatedNumberOfPeriods(70).startInactivity(30).numInactivityPeriods(1);
    generateDailyRolloverAndCheckFileCount(cp);
  }


  // Since the duration of a month (in seconds) varies from month to month, tests with inactivity period must
  // be conducted with daily rollover  not monthly
  @Test
  public void checkCleanupForDailyRollover_15Periods() {
    cp.maxHistory(5).simulatedNumberOfPeriods(15).startInactivity(6).numInactivityPeriods(3);
    generateDailyRolloverAndCheckFileCount(cp);
  }

  @Test
  public void checkCleanupForDailyRolloverWithInactivity_30Periods() {
    cp.maxHistory(2).simulatedNumberOfPeriods(30).startInactivity(3).numInactivityPeriods(1);
    generateDailyRolloverAndCheckFileCount(cp);
  }

  @Test
  public void checkCleanupForDailyRolloverWithInactivity_10Periods() {
    this.currentTime = THU_2016_03_17_T_230330_CET;
    cp.maxHistory(6).simulatedNumberOfPeriods(10).startInactivity(2).numInactivityPeriods(2);
    generateDailyRolloverAndCheckFileCount(cp);
  }

  @Test
  public void checkCleanupForDailyRolloverWithSecondPhase() {
    slashCount = computeSlashCount(DAILY_DATE_PATTERN);
    int maxHistory = 5;
    String fileNamePattern = randomOutputDir + "clean-%d{" + DAILY_DATE_PATTERN + "}.txt";

    ConfigParameters cp0 = new ConfigParameters(currentTime).maxHistory(maxHistory).fileNamePattern(fileNamePattern)
            .simulatedNumberOfPeriods(maxHistory * 2);
    long endTime = logOverMultiplePeriods(cp0);

    ConfigParameters cp1 = new ConfigParameters(endTime + MILLIS_IN_DAY * 10).maxHistory(maxHistory).fileNamePattern(fileNamePattern)
            .simulatedNumberOfPeriods(maxHistory);
    logOverMultiplePeriods(cp1);
    checkFileCount(maxHistory);
  }

  @Test
  public void dailySizeBasedRolloverWithoutCap() {
    SizeAndTimeBasedFNATP<Object> sizeAndTimeBasedFNATP = new SizeAndTimeBasedFNATP<Object>();
    sizeAndTimeBasedFNATP.invocationGate = fixedRateInvocationGate;
    sizeAndTimeBasedFNATP.setMaxFileSize(new FileSize(10000));
    tbfnatp = sizeAndTimeBasedFNATP;
    this.slashCount = computeSlashCount(DAILY_DATE_PATTERN);
    String fileNamePattern = randomOutputDir + "/%d{" + DAILY_DATE_PATTERN + "}-clean.%i.zip";
    cp.maxHistory(5).fileNamePattern(fileNamePattern).simulatedNumberOfPeriods(5 * 4);
    logOverMultiplePeriods(cp);
    checkPatternCompliance(cp.maxHistory + slashCount, "\\d{4}-\\d{2}-\\d{2}-clean(\\.\\d)(.zip)?");
  }

  @Test
  public void dailyChronologSizeBasedRollover() {
    SizeAndTimeBasedFNATP<Object> sizeAndTimeBasedFNATP = new SizeAndTimeBasedFNATP<Object>();
    sizeAndTimeBasedFNATP.setMaxFileSize(new FileSize(10000));
    sizeAndTimeBasedFNATP.invocationGate = fixedRateInvocationGate;
    tbfnatp = sizeAndTimeBasedFNATP;
    slashCount = 1;
    String fileNamePattern = randomOutputDir + "/%d{" + DAILY_DATE_PATTERN + "}/clean.%i.zip";
    cp.maxHistory(5).fileNamePattern(fileNamePattern).simulatedNumberOfPeriods(5 * 3);
    logOverMultiplePeriods(cp);
    checkDirPatternCompliance(cp.maxHistory);
  }

  @Test
  public void dailyChronologSizeBasedRolloverWithSecondPhase() {
    SizeAndTimeBasedFNATP<Object> sizeAndTimeBasedFNATP = new SizeAndTimeBasedFNATP<Object>();
    sizeAndTimeBasedFNATP.setMaxFileSize(new FileSize(10000));
    sizeAndTimeBasedFNATP.invocationGate = fixedRateInvocationGate;
    tbfnatp = sizeAndTimeBasedFNATP;
    this.slashCount = 1;
    String fileNamePattern = randomOutputDir + "/%d{" + DAILY_DATE_PATTERN + "}/clean.%i";
    int maxHistory = 5;
    cp.maxHistory(maxHistory).fileNamePattern(fileNamePattern).simulatedNumberOfPeriods(3);
    long endTime = logOverMultiplePeriods(cp);

    int simulatedNumberOfPeriods = maxHistory * 4;
    ConfigParameters cp1 = new ConfigParameters(endTime + MILLIS_IN_DAY * 7).maxHistory(maxHistory).fileNamePattern(fileNamePattern)
            .simulatedNumberOfPeriods(simulatedNumberOfPeriods);
    logOverMultiplePeriods(cp1);
    checkDirPatternCompliance(maxHistory);
  }

  private int expectedCountWithFolders(int maxHistory, boolean withExtraFolder) {
    int numLogFiles = maxHistory;
    int numLogFilesAndFolders = numLogFiles * 2;
    int result = numLogFilesAndFolders + slashCount;
    if (withExtraFolder) result += 1;
    return result;
  }

  private void buildRollingFileAppender(ConfigParameters cp, boolean cleanHistoryOnStart) {
    rfa.setContext(context);
    rfa.setEncoder(encoder);
    tbrp.setContext(context);
    tbrp.setFileNamePattern(cp.fileNamePattern);
    tbrp.setMaxHistory(cp.maxHistory);
    tbrp.setTotalSizeCap(new FileSize(cp.sizeCap));
    tbrp.setParent(rfa);
    tbrp.setCleanHistoryOnStart(cleanHistoryOnStart);
    tbrp.timeBasedFileNamingAndTriggeringPolicy = tbfnatp;
    tbrp.timeBasedFileNamingAndTriggeringPolicy.setCurrentTime(cp.simulatedTime);
    tbrp.start();
    rfa.setRollingPolicy(tbrp);
    rfa.start();
  }

  private boolean DO_NOT_CLEAN_HISTORY_ON_START = false;

  private long logOverMultiplePeriods(ConfigParameters cp) {
    buildRollingFileAppender(cp, DO_NOT_CLEAN_HISTORY_ON_START);
    int runLength = cp.simulatedNumberOfPeriods * ticksPerPeriod;
    int startInactivityIndex = cp.startInactivity * ticksPerPeriod;
    int endInactivityIndex = startInactivityIndex + cp.numInactivityPeriods * ticksPerPeriod;
    long tickDuration = cp.periodDurationInMillis / ticksPerPeriod;

    for (int i = 0; i <= runLength; i++) {
      if (i < startInactivityIndex || i > endInactivityIndex) {
        Date currentDate = new Date(tbrp.timeBasedFileNamingAndTriggeringPolicy.getCurrentTime());
        StringBuilder sb = new StringBuilder("Hello");
        String currentDateStr = currentDate.toString();
        String iAsString = Integer.toString(i);
        sb.append(currentDateStr);
        SpacePadder.spacePad(sb, 66 + (3 - iAsString.length() - currentDateStr.length()));
        sb.append(iAsString);
        rfa.doAppend(sb.toString());
      }
      tbrp.timeBasedFileNamingAndTriggeringPolicy.setCurrentTime(addTime(tbrp.timeBasedFileNamingAndTriggeringPolicy.getCurrentTime(),
              tickDuration));
      add(tbrp.compressionFuture);
      add(tbrp.cleanUpFuture);
      waitForJobsToComplete();
    }

    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    rfa.stop();
    return tbrp.timeBasedFileNamingAndTriggeringPolicy.getCurrentTime();
  }

  private boolean extraFolder(int numPeriods, int periodsPerEra, int beginPeriod, int maxHistory) {
    int valueOfLastMonth = ((beginPeriod) + numPeriods) % periodsPerEra;
    return (valueOfLastMonth < maxHistory);
  }

  private long addTime(long time, long timeToWait) {
    return time + timeToWait;
  }

  private void checkFileCount(int expectedCount) {
    File dir = new File(randomOutputDir);
    List<File> fileList = new ArrayList<File>();
    findAllDirsOrStringContainsFilesRecursively(dir, fileList, "clean");
    System.out.println("[checkFileCount] fileList:");
    fileList.stream().sorted().forEach(System.out::println);
    assertEquals(expectedCount, fileList.size());
  }

  private void checkFileCountAtMost(int expectedCount) {
    File dir = new File(randomOutputDir);
    List<File> fileList = new ArrayList<File>();
    findAllDirsOrStringContainsFilesRecursively(dir, fileList, "clean");
    int fileListSize = fileList.size();
    System.out.println("[checkFileCountAtMost] fileList:");
    fileList.stream().sorted().forEach(System.out::println);
    assertTrue("file list size " + fileListSize + ", expectedCount=" + expectedCount, fileListSize <= expectedCount);
  }

  private int expectedCountWithoutFoldersWithInactivity(int maxHistory, int totalPeriods, int endOfInactivity) {
    int availableHistory = (totalPeriods + 1) - endOfInactivity;
    int actualHistory = Math.min(availableHistory, maxHistory);
    return actualHistory;
  }

  private void genericFindMatching(final FileMatchFunction matchFunc, File dir, List<File> fileList, final String pattern, boolean includeDirs) {
    if (dir.isDirectory()) {
      File[] matchArray = dir.listFiles(new FileFilter() {
        public boolean accept(File f) {
          return f.isDirectory() || matchFunc.match(f, pattern);
        }
      });
      for (File f : matchArray) {
        if (f.isDirectory()) {
          if (includeDirs) fileList.add(f);
          genericFindMatching(matchFunc, f, fileList, pattern, includeDirs);
        } else
          fileList.add(f);
      }
    }
  }

  private void findAllFoldersInFolderRecursively(File dir, List<File> fileList) {
    FileMatchFunction alwaysFalse = new FileMatchFunction() {
      public boolean match(File f, String pattern) {
        return false;
      }
    };
    genericFindMatching(alwaysFalse, dir, fileList, null, true);
  }

  private void findAllDirsOrStringContainsFilesRecursively(File dir, List<File> fileList, String pattern) {
    FileMatchFunction matchFunction = new FileMatchFunction() {
      public boolean match(File f, String pattern) {
        return f.getName().contains(pattern);
      }
    };
    genericFindMatching(matchFunction, dir, fileList, pattern, true);
  }

  private void findFilesInFolderRecursivelyByPatterMatch(File dir, List<File> fileList, String pattern) {
    FileMatchFunction matchByPattern = new FileMatchFunction() {
      public boolean match(File f, String pattern) {
        return f.getName().matches(pattern);
      }
    };
    genericFindMatching(matchByPattern, dir, fileList, pattern, false);
  }

  private Set<String> groupByClass(List<File> fileList, String regex) {
    Pattern p = Pattern.compile(regex);
    Set<String> set = new HashSet<String>();
    for (File f : fileList) {
      String n = f.getName();
      Matcher m = p.matcher(n);
      m.matches();
      int begin = m.start(1);
      String reduced = n.substring(0, begin);
      set.add(reduced);
    }
    return set;
  }


  private void checkPatternCompliance(int expectedClassCount, String regex) {
    Set<String> set = findFilesByPatternClass(regex);
    assertEquals(expectedClassCount, set.size());
  }

  private List<File> findFilesByPattern(String regex) {
    File dir = new File(randomOutputDir);
    List<File> fileList = new ArrayList<File>();
    findFilesInFolderRecursivelyByPatterMatch(dir, fileList, regex);
    System.out.println("[findFilesByPattern] fileList:");
    fileList.stream().sorted().forEach(System.out::println);
    return fileList;
  }

  private Set<String> findFilesByPatternClass(String regex) {
    List<File> fileList = findFilesByPattern(regex);
    Set<String> set = groupByClass(fileList, regex);
    System.out.println("[findFilesByPatternClass] set:");
    set.stream().sorted().forEach(System.out::println);
    return set;
  }

  private void checkDirPatternCompliance(int expectedClassCount) {
    File dir = new File(randomOutputDir);
    List<File> fileList = new ArrayList<File>();
    findAllFoldersInFolderRecursively(dir, fileList);
    System.out.println("[checkDirPatternCompliance] fileList:");
    fileList.stream().sorted().forEach(System.out::println);
    for (File f : fileList) {
      assertTrue(f.list().length >= 1);
    }
    assertEquals(expectedClassCount, fileList.size());
  }
}
