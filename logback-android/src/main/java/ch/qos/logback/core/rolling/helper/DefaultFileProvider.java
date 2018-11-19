package ch.qos.logback.core.rolling.helper;

import java.io.File;
import java.io.FilenameFilter;

public class DefaultFileProvider implements FileProvider {
  public File[] listFiles(File dir, FilenameFilter filter) {
    return dir.listFiles(filter);
  }

  public boolean deleteFile(File file) {
    return file.delete();
  }

  public boolean isFile(File file) {
    return file.isFile();
  }

  public long length(File file) {
    return file.length();
  }
}
