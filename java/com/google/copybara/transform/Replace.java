// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.util.FileUtil;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A source code transformation which replaces a regular expression with some other string.
 *
 * <p>The replacement is defined as two strings with interpolations and a mapping of interpolation
 * names to regular expressions. For instance,
 * <pre>
 * !Replace
 *  before: "foo${x}bar"
 *  after: "bar${x}foo"
 *  regexGroups:
 *   x: "[A-Z]+"
 * </pre>
 *
 * This will replace fooABCDbar with barABCDfoo or vice-versa.
 *
 * <p>This transformation is line-based and only replaces the first instance of the pattern on a
 * line.
 *
 * TODO(matvore): Consider making this configurable to non-line-based and multiple matches.
 */
public final class Replace implements Transformation {

  private static final Logger logger = Logger.getLogger(Replace.class.getName());

  private final TemplateTokens before;
  private final TemplateTokens after;
  private final ImmutableMap<String, Pattern> regexGroups;
  private final PathMatcher fileMatcher;

  private Replace(TemplateTokens before, TemplateTokens after,
      ImmutableMap<String, Pattern> regexGroups, PathMatcher fileMatcher) {
    this.before = Preconditions.checkNotNull(before);
    this.after = Preconditions.checkNotNull(after);
    this.regexGroups = Preconditions.checkNotNull(regexGroups);
    this.fileMatcher = Preconditions.checkNotNull(fileMatcher);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("before", before)
        .add("after", after)
        .add("regexGroup", regexGroups)
        .add("fileMatcher", fileMatcher)
        .toString();
  }

  private final class TransformVisitor extends SimpleFileVisitor<Path> {
    final Pattern beforeRegex = before.toRegex(regexGroups);

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      if (!Files.isRegularFile(file) || !fileMatcher.matches(file)) {
        return FileVisitResult.CONTINUE;
      }
      logger.log(
          Level.INFO, String.format("apply s/%s/%s/ to %s", beforeRegex, after.template(), file));

      List<String> originalLines = ImmutableList.copyOf(
          Splitter.on('\n').split(new String(Files.readAllBytes(file), UTF_8)));
      List<String> newLines = new ArrayList<>(originalLines.size());
      for (String line : originalLines) {
        Matcher matcher = beforeRegex.matcher(line);
        newLines.add(matcher.replaceAll(after.template()));
      }
      if (!originalLines.equals(newLines)) {
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file), UTF_8)) {
          Joiner.on('\n').appendTo(writer, newLines);
        }
      }

      return FileVisitResult.CONTINUE;
    }
  }

  @Override
  public void transform(Path workdir) throws IOException {
    Files.walkFileTree(workdir, new TransformVisitor());
  }

  public final static class Yaml implements Transformation.Yaml {

    private TemplateTokens before;
    private TemplateTokens after;
    private ImmutableMap<String, Pattern> regexGroups = ImmutableMap.of();
    private String path;

    public void setBefore(String before) {
      this.before = TemplateTokens.parse(before);
    }

    public void setAfter(String after) {
      this.after = TemplateTokens.parse(after);
    }

    public void setRegexGroups(Map<String, String> regexGroups) {
      ImmutableMap.Builder<String, Pattern> parsed = new ImmutableMap.Builder<>();
      for (Map.Entry<String, String> group : regexGroups.entrySet()) {
        try {
          parsed.put(group.getKey(), Pattern.compile(group.getValue()));
        } catch (PatternSyntaxException e) {
          throw new ConfigValidationException("'regexGroups' includes invalid regex for key "
              + group.getKey() + ": " + group.getValue(), e);
        }
      }

      this.regexGroups = parsed.build();
    }

    public void setPath(String path) {
      this.path = path;
    }

    @Override
    public Transformation withOptions(Options options) {
      ConfigValidationException.checkNotMissing(before, "before");
      ConfigValidationException.checkNotMissing(after, "after");

      before.validateInterpolations(regexGroups.keySet());
      after.validateInterpolations(regexGroups.keySet());

      PathMatcher pathMatcher = FileUtil.ALL_FILES_MATCHER;
      if (path != null) {
        pathMatcher = FileUtil.relativeGlob(
            options.get(GeneralOptions.class).getWorkdir(), path);
      }

      return new Replace(before, after, regexGroups, pathMatcher);
    }
  }
}