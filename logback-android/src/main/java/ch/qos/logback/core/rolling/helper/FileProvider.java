package ch.qos.logback.core.rolling.helper;

import java.io.File;
import java.io.FilenameFilter;

interface FileProvider {
  File[] listFiles(File dir, FilenameFilter filter);
  boolean deleteFile(File file);
  boolean isFile(File file);
  long length(File file);
}
