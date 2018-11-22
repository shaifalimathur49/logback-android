package ch.qos.logback.core.rolling.helper;

import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

class FileFinder {

  private static final String REGEX_MARKER_START = "(?:\uFFFE)?";
  private static final String REGEX_MARKER_END = "(?:\uFFFF)?";

  String[] findFiles(String pathPattern) {
    List<PathPart> pathParts = this.splitPath(pathPattern);
    List<File> files;
    PathPart pathPart = pathParts.get(0);
    if (pathParts.size() > 1) {
      pathParts = pathParts.subList(1, pathParts.size());
    }

    files = pathPart.listFiles();
    List<File> foundFiles = find(files, pathParts);
    List<String> filenames = new ArrayList<>();
    for (File f : foundFiles) {
      filenames.add(f.getAbsolutePath());
    }
    return filenames.toArray(new String[0]);
  }

  List<File> find(List<File> files, List<PathPart> pathParts) {
    List<File> matchedFiles = new ArrayList<>();

    if (pathParts.size() == 1) {
      PathPart pathPart = pathParts.get(0);

      for (File file : files) {
        if (file.isFile() && pathPart.matches(file)) {
          matchedFiles.add(file);
        }
      }
      return matchedFiles;
    }

    PathPart pathPart = pathParts.get(0);
    for (File file : files) {
      if (file.isDirectory() && pathPart.matches(file)) {
        return find(Arrays.asList(file.listFiles()), pathParts.subList(1, pathParts.size()));
      }
    }
    return matchedFiles;
  }

  List<PathPart> splitPath(String pattern) {
    List<PathPart> parts = new ArrayList<>();
    List<String> literals = new ArrayList<>();
    for (String p : pattern.split(File.separator)) {
      final boolean isRegex = p.contains(REGEX_MARKER_START) && p.contains(REGEX_MARKER_END);
      p = p.replace(REGEX_MARKER_START, "").replace(REGEX_MARKER_END, "");
      if (isRegex) {
        if (!literals.isEmpty()) {
          parts.add(new LiteralPathPart(TextUtils.join(File.separator, literals)));
          literals.clear();
        }
        parts.add(new RegexPathPart(p));
      } else {
        literals.add(p);
      }
    }
    if (!literals.isEmpty()) {
      parts.add(new LiteralPathPart(TextUtils.join(File.separator, literals)));
    }
    return parts;
  }

  static String regexEscapePath(String path) {
    if (path.contains(File.separator)) {
      String[] parts = path.split(File.separator);
      for (int i = 0; i < parts.length; i++) {
        parts[i] = FileFinder.REGEX_MARKER_START + parts[i] + FileFinder.REGEX_MARKER_END;
      }
      return TextUtils.join(File.separator, parts);
    } else {
      return FileFinder.REGEX_MARKER_START + path + FileFinder.REGEX_MARKER_END;
    }
  }
}

abstract class PathPart {
  String part;
  boolean isRegex;

  PathPart(String part) {
    this.part = part;
  }

  abstract boolean matches(File file);
  abstract List<File> listFiles();

  List<File> listFiles(String part) {
    File[] files = new File(part).getAbsoluteFile().listFiles();
    if (files == null) {
      files = new File[0];
    }
    return Arrays.asList(files);
  }
}

class LiteralPathPart extends PathPart {
  LiteralPathPart(String part) {
    super(part);
  }

  boolean matches(File file) {
    return file.getName().equals(part);
  }

  List<File> listFiles() {
    return listFiles(part);
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

  List<File> listFiles() {
    return listFiles(".");
  }
}