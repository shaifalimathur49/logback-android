package ch.qos.logback.core.rolling.helper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

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
    void doesNotSplitPathOfLiterals() {
      assertThat(splitPath("/a/b/c.log"), contains("/a/b/c.log"));
    }

    @Test
    void doesNotSplitPathOfRawRegex() {
      String[] inputs = new String[] {
        "/\\d{4}/\\d{2}/c.log",
        "/logs (.)[x]{1}.+?/\\d{4}/\\d{2}/c.log",
      };
      for (String input : inputs) {
        assertThat(splitPath(input), contains(input));
      }
    }

    @Test
    void splitsPathOfEscapedRegex() {
      assertThat(splitPath(FileFinder.regexEscapePath("/\\d{4}/\\d{2}/c.log")), contains("", "\\d{4}", "\\d{2}", "c.log"));
      HashMap<String, String[]> inputs = new HashMap<>();
      inputs.put("/\\d{4}/\\d{2}/c.log", new String[] { "", "\\d{4}", "\\d{2}", "c.log" });
      inputs.put("/logs (.)[x]{1}.+?/\\d{4}/\\d{2}/c.log", new String[] { "", "logs (.)[x]{1}.+?", "\\d{4}", "\\d{2}", "c.log" });

      for (String key : inputs.keySet()) {
        assertThat(splitPath(FileFinder.regexEscapePath(key)), contains(inputs.get(key)));
      }
    }

    private List<String> splitPath(String pattern) {
      return finder.splitPath(pattern)
              .stream()
              .map(p -> p.part)
              .collect(Collectors.toList());
    }
  }
}
