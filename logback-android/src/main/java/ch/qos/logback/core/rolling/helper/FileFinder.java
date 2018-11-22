package ch.qos.logback.core.rolling.helper;

import android.text.TextUtils;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

class FileFinder {

  private static final String REGEX_MARKER_START = "(?:\uFFFE)?";
  private static final String REGEX_MARKER_END = "(?:\uFFFF)?";

  String[] findFiles(String pathPattern) {
    Deque<PathPart> pathParts = this.splitPath(pathPattern);
    PathPart pathPart = pathParts.remove();
    List<File> foundFiles = find(pathPart.listFiles(), pathParts);
    List<String> filenames = new ArrayList<>();
    for (File f : foundFiles) {
      filenames.add(f.getAbsolutePath());
    }
    return filenames.toArray(new String[0]);
  }

  List<File> find(List<File> files, Deque<PathPart> pathParts) {
    List<File> matchedFiles = new ArrayList<>();

    PathPart pathPart = pathParts.remove();
    if (pathParts.isEmpty()) {
      for (File file : files) {
        if (file.isFile() && pathPart.matches(file)) {
          matchedFiles.add(file);
        }
      }
      return matchedFiles;
    }

    for (File file : files) {
      if (file.isDirectory() && pathPart.matches(file)) {
        return find(Arrays.asList(file.listFiles()), pathParts);
      }
    }
    return matchedFiles;
  }

  Deque<PathPart> splitPath(String pattern) {
    Deque<PathPart> parts = new ArrayDeque<>();
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
        if (parts[i].length() > 0) {
          parts[i] = REGEX_MARKER_START + parts[i] + REGEX_MARKER_END;
        }
      }
      return TextUtils.join(File.separator, parts);
    } else {
      return REGEX_MARKER_START + path + REGEX_MARKER_END;
    }
  }
}

abstract class PathPart {
  String part;

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
    pattern = Pattern.compile(part);
  }

  boolean matches(File file) {
    return pattern.matcher(file.getName()).find();
  }

  List<File> listFiles() {
    return listFiles(".");
  }
}