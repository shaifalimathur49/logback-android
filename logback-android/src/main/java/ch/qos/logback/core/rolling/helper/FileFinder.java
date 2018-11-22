package ch.qos.logback.core.rolling.helper;

import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

class FileFinder {

  // FIXME: Actual dir names can contain these chars, so we can't
  // assume this was from FilenamePattern. Modify FilenamePattern
  // to escape dir names that contain these chars, and then this
  // class needs to check if the chars are escaped.
  /** Possible regex characters in a filename pattern */
  private static final Pattern REGEX_CHARS = Pattern.compile("[\\[\\](){}+?*]|(?:\\[dwWsSbB]])");

  String[] findFiles(String pathPattern) {
    List<PathPart> pathParts = this.splitPath(pathPattern);
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

  List<File> find(List<File> files, List<PathPart> pathParts) {
    List<File> matchedFiles = new ArrayList<File>();

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
      if (REGEX_CHARS.matcher(p).find()) {
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
}

abstract class PathPart {
  String part;
  boolean isRegex;

  PathPart(String part) {
    this.part = part;
  }

  abstract boolean matches(File file);
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