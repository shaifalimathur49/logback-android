package ch.qos.logback.core.rolling.helper;

import java.io.File;
import java.io.FilenameFilter;

public class DefaultFileProvider implements FileProvider {
  public File[] list(File dir, FilenameFilter filter) {
    return dir.listFiles(filter);
  }
}
