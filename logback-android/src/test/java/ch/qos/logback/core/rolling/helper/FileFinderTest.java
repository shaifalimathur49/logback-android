package ch.qos.logback.core.rolling.helper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class FileFinderTest {

  @Test
  void files() {

  }

  @Nested
  class SplitPath {
    FileFinder finder;

    @BeforeEach
    void setup() {
      finder = new FileFinder();
    }

    @Test
    void doesNotSplitBaseFilename() {
      assertThat(splitPath("foo.log"), contains("foo.log"));
    }

    @Test
    void doesNotSplitBaseFilenameWithRegexChars() {
      String[] regexPatterns = new String[]{
        "\\d{2}.log",
        "\\w.log",
        "\\W.log",
        "\\s.log",
        "\\S.log",
        "\\b.log",
        ".{3}.log",
        ".*.log",
        ".?.log",
        ".+.log",
        "[AP]M.log",
      };
      Stream.of(regexPatterns).forEach(x -> assertThat(splitPath(x), contains(x)));
    }

    @Test
    void doesNotSplitNestedFilenameWithoutRegexChars() {
      assertThat(splitPath("/a/b/c.log"), contains("/a/b/c.log"));
    }

    @Test
    void splitsNestedFilenameWithRegexChars() {
      assertThat(splitPath("/\\d{4}/\\d{2}/c.log"), contains("", "\\d{4}", "\\d{2}", "c.log"));
    }

    private List<String> splitPath(String pattern) {
      List<PathPart> parts = finder.splitPath(pattern);
      return parts.stream().map(p -> p.part).collect(Collectors.toList());
    }
  }
}
