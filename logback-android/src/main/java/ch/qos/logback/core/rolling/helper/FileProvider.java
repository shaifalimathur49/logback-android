package ch.qos.logback.core.rolling.helper;

import java.io.File;
import java.io.FilenameFilter;

interface FileProvider {
  File[] list(File dir, FilenameFilter filter);
}
