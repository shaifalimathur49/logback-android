package ch.qos.logback.core.rolling.helper;

import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

class FileFinder {
  private FileNamePattern fileNamePattern;

  FileFinder(FileNamePattern fileNamePattern) {
    this.fileNamePattern = fileNamePattern;
  }

  String[] findFiles() {
    List<PathPart> pathParts = this.splitPattern(this.fileNamePattern);
    List<File> files;
    PathPart pathPart = pathParts.get(0);
    if (pathParts.size() > 1) {
      pathParts = pathParts.subList(1, pathParts.size());
    }

    if (pathPart.isRegex) {
      files = Arrays.asList(new File(".").listFiles());
    } else {
      files = Arrays.asList(new File(pathPart.part).listFiles());
    }
    List<File> foundFiles = find(files, pathParts);
    List<String> filenames = new ArrayList<String>();
    for (File f : foundFiles) {
      filenames.add(f.getAbsolutePath());
    }
    return filenames.toArray(new String[0]);
  }

  private List<File> find(List<File> files, List<PathPart> pathParts) {
    List<File> matchedFiles = new ArrayList<File>();

    if (pathParts.size() == 1) {
      PathPart pathPart = pathParts.get(0);
      Pattern p = null;
      if (pathPart.isRegex) {
        p = Pattern.compile(pathPart.part);
      }

      for (File file : files) {
        if (file.isFile()) {
          if (pathPart.isRegex) {
            if (p != null && p.matcher(file.getName()).find()) {
              matchedFiles.add(file);
            }
          } else {
            if (file.getName().equals(pathPart.part)) {
              matchedFiles.add(file);
            }
          }
        }
      }
      return matchedFiles;
    }

    PathPart pathPart = pathParts.get(0);
    for (File file : files) {
      if (file.isDirectory()) {
        if (pathPart.isRegex) {
          Pattern p = Pattern.compile(pathPart.part);
          if (p.matcher(file.getName()).find()) {
            return find(Arrays.asList(file.listFiles()), pathParts.subList(1, pathParts.size()));
          }
        } else {
          if (file.getName().equals(pathPart.part)) {
            return find(Arrays.asList(file.listFiles()), pathParts.subList(1, pathParts.size()));
          }
        }
      }
    }
    return matchedFiles;
  }

  private List<PathPart> splitPattern(FileNamePattern pattern) {
    final Pattern REGEX_CHARS = Pattern.compile("[\\[\\](){}.+?*]|(?:\\[dwWsSbB]])");
    List<PathPart> parts = new ArrayList<PathPart>();
    List<String> literals = new ArrayList<String>();
    for (String p : pattern.toRegex().split(File.separator)) {
      if (REGEX_CHARS.matcher(p).find()) {
        if (literals.size() > 0) {
          parts.add(new LiteralPathPart(TextUtils.join(File.separator, literals)));
          literals.clear();
        }
        parts.add(new RegexPathPart(p));
      } else {
        literals.add(p);
      }
    }
    if (literals.size() > 0) {
      parts.add(new LiteralPathPart(TextUtils.join(File.separator, literals)));
    }
    return parts;
  }
}

abstract class PathPart {
  String part;
  boolean isRegex;

  PathPart(String part) {
    this.part = part;
  }
}

class LiteralPathPart extends PathPart {
  LiteralPathPart(String part) {
    super(part);
  }

  boolean matches(File file) {
    return file.getName().equals(part);
  }
}

class RegexPathPart extends PathPart {
  private Pattern pattern;

  RegexPathPart(String part) {
    super(part);
    isRegex = true;
    pattern = Pattern.compile(part);
  }

  boolean matches(File file) {
    return pattern.matcher(file.getName()).find();
  }
}