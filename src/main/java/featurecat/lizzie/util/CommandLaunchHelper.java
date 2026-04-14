package featurecat.lizzie.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandLaunchHelper {
  private static final String[] FILE_PATH_OPTIONS = {
    "-model",
    "--model",
    "-config",
    "--config",
    "-override-config",
    "--override-config",
    "-human-model",
    "--human-model",
    "-analysis-model",
    "--analysis-model",
    "-evaluation-model",
    "--evaluation-model",
    "-cacerts",
    "--cacerts"
  };
  private static final Pattern REGISTRY_PATH_LINE_PATTERN =
      Pattern.compile("^\\s*Path\\s+REG_(?:SZ|EXPAND_SZ)\\s+(.*)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern WINDOWS_ENV_REFERENCE_PATTERN = Pattern.compile("%([^%]+)%");

  private CommandLaunchHelper() {}

  public static LaunchSpec prepare(List<String> commandParts) {
    List<String> resolvedCommand = new ArrayList<String>();
    if (commandParts != null) {
      resolvedCommand.addAll(commandParts);
    }
    Path workingDirectory = detectWorkingDirectory(resolvedCommand);
    if (workingDirectory != null) {
      resolveExecutableToken(resolvedCommand, workingDirectory);
      resolveOptionPaths(resolvedCommand, workingDirectory);
      resolveJavaJarPath(resolvedCommand, workingDirectory);
    }
    return new LaunchSpec(
        resolvedCommand, workingDirectory == null ? null : workingDirectory.toFile());
  }

  public static void applyWorkingDirectory(ProcessBuilder processBuilder, LaunchSpec launchSpec) {
    if (processBuilder == null || launchSpec == null || launchSpec.getWorkingDirectory() == null) {
      return;
    }
    if (processBuilder.directory() == null) {
      processBuilder.directory(launchSpec.getWorkingDirectory());
    }
  }

  public static void configureProcessBuilder(ProcessBuilder processBuilder, LaunchSpec launchSpec) {
    applyWorkingDirectory(processBuilder, launchSpec);
    if (!isWindowsPlatform() || processBuilder == null) {
      return;
    }
    configureWindowsSearchPath(processBuilder, launchSpec);
  }

  private static Path detectWorkingDirectory(List<String> commandParts) {
    if (commandParts == null || commandParts.isEmpty()) {
      return null;
    }

    Path bestRoot = null;
    int bestScore = -1;
    for (Path candidateRoot : collectCandidateRoots()) {
      int score = scoreCandidate(commandParts, candidateRoot);
      if (score > bestScore) {
        bestScore = score;
        bestRoot = candidateRoot;
      }
    }
    if (bestScore > 0) {
      return bestRoot;
    }
    return inferWorkingDirectoryFromAbsolutePaths(commandParts);
  }

  private static Path inferWorkingDirectoryFromAbsolutePaths(List<String> commandParts) {
    List<Path> referencedDirectories = new ArrayList<Path>();
    addAbsoluteReferenceDirectory(referencedDirectories, commandParts.get(0));

    for (int i = 0; i < commandParts.size() - 1; i++) {
      if (matchesOption(commandParts.get(i), FILE_PATH_OPTIONS)
          || "-jar".equals(commandParts.get(i))) {
        addAbsoluteReferenceDirectory(referencedDirectories, commandParts.get(i + 1));
      }
    }

    if (referencedDirectories.isEmpty()) {
      return null;
    }

    Path commonDirectory = referencedDirectories.get(0);
    for (int i = 1; i < referencedDirectories.size(); i++) {
      commonDirectory = commonAncestor(commonDirectory, referencedDirectories.get(i));
      if (commonDirectory == null) {
        break;
      }
    }

    if (commonDirectory != null
        && Files.isDirectory(commonDirectory)
        && !isFilesystemRoot(commonDirectory)) {
      return commonDirectory;
    }
    return referencedDirectories.get(0);
  }

  private static void addAbsoluteReferenceDirectory(List<Path> directories, String token) {
    Path directory = toAbsoluteReferenceDirectory(token);
    if (directory != null && Files.isDirectory(directory) && !directories.contains(directory)) {
      directories.add(directory);
    }
  }

  private static Path toAbsoluteReferenceDirectory(String token) {
    if (token == null) {
      return null;
    }
    try {
      Path path = Paths.get(token.trim());
      if (!path.isAbsolute()) {
        return null;
      }
      Path normalized = path.toAbsolutePath().normalize();
      if (Files.isDirectory(normalized)) {
        return normalized;
      }
      Path parent = normalized.getParent();
      if (parent != null && Files.isDirectory(parent)) {
        return parent;
      }
    } catch (Exception e) {
      return null;
    }
    return null;
  }

  private static Path commonAncestor(Path left, Path right) {
    if (left == null || right == null) {
      return null;
    }
    Path current = left.toAbsolutePath().normalize();
    Path other = right.toAbsolutePath().normalize();
    while (current != null) {
      if (other.startsWith(current)) {
        return current;
      }
      current = current.getParent();
    }
    return null;
  }

  private static int scoreCandidate(List<String> commandParts, Path root) {
    if (root == null) {
      return -1;
    }
    int score = 0;
    String executableToken = commandParts.get(0);
    if (looksLikeRelativeFileToken(executableToken)) {
      Path executablePath = resolveCandidate(root, executableToken);
      if (!Files.isRegularFile(executablePath)) {
        return -1;
      }
      score += 6;
    }

    for (int i = 0; i < commandParts.size() - 1; i++) {
      if (matchesOption(commandParts.get(i), FILE_PATH_OPTIONS)) {
        String value = commandParts.get(i + 1);
        if (looksLikeRelativeFileToken(value) && Files.exists(resolveCandidate(root, value))) {
          score += 3;
        }
      }
    }

    if (Utils.isJavaCommand(executableToken)) {
      for (int i = 0; i < commandParts.size() - 1; i++) {
        if ("-jar".equals(commandParts.get(i))) {
          String value = commandParts.get(i + 1);
          if (looksLikeRelativeFileToken(value)
              && Files.isRegularFile(resolveCandidate(root, value))) {
            score += 4;
          }
        }
      }
    }
    return score;
  }

  private static void resolveExecutableToken(List<String> commandParts, Path root) {
    if (commandParts.isEmpty()) {
      return;
    }
    String executableToken = commandParts.get(0);
    if (!looksLikeRelativeFileToken(executableToken)) {
      return;
    }
    Path executablePath = resolveCandidate(root, executableToken);
    if (Files.isRegularFile(executablePath)) {
      commandParts.set(0, executablePath.toAbsolutePath().normalize().toString());
    }
  }

  private static void resolveOptionPaths(List<String> commandParts, Path root) {
    for (int i = 0; i < commandParts.size() - 1; i++) {
      if (!matchesOption(commandParts.get(i), FILE_PATH_OPTIONS)) {
        continue;
      }
      String value = commandParts.get(i + 1);
      if (!looksLikeRelativeFileToken(value)) {
        continue;
      }
      Path candidate = resolveCandidate(root, value);
      if (Files.exists(candidate)) {
        commandParts.set(i + 1, candidate.toAbsolutePath().normalize().toString());
      }
    }
  }

  private static void resolveJavaJarPath(List<String> commandParts, Path root) {
    if (commandParts.isEmpty() || !Utils.isJavaCommand(commandParts.get(0))) {
      return;
    }
    for (int i = 0; i < commandParts.size() - 1; i++) {
      if (!"-jar".equals(commandParts.get(i))) {
        continue;
      }
      String value = commandParts.get(i + 1);
      if (!looksLikeRelativeFileToken(value)) {
        continue;
      }
      Path candidate = resolveCandidate(root, value);
      if (Files.isRegularFile(candidate)) {
        commandParts.set(i + 1, candidate.toAbsolutePath().normalize().toString());
      }
    }
  }

  private static LinkedHashSet<Path> collectCandidateRoots() {
    LinkedHashSet<Path> roots = new LinkedHashSet<Path>();
    addCandidateRoot(roots, System.getProperty("user.dir", "."));

    try {
      Path codePath =
          Paths.get(
                  CommandLaunchHelper.class
                      .getProtectionDomain()
                      .getCodeSource()
                      .getLocation()
                      .toURI())
              .toAbsolutePath()
              .normalize();
      Path current = Files.isDirectory(codePath) ? codePath : codePath.getParent();
      for (int depth = 0; current != null && depth < 8; depth++) {
        roots.add(current);
        current = current.getParent();
      }
    } catch (URISyntaxException | RuntimeException e) {
      e.printStackTrace();
    }
    return roots;
  }

  private static void addCandidateRoot(LinkedHashSet<Path> roots, String rootText) {
    if (rootText == null || rootText.trim().isEmpty()) {
      return;
    }
    try {
      roots.add(Paths.get(rootText).toAbsolutePath().normalize());
    } catch (Exception e) {
    }
  }

  private static boolean matchesOption(String token, String[] options) {
    if (token == null) {
      return false;
    }
    for (String option : options) {
      if (option.equals(token)) {
        return true;
      }
    }
    return false;
  }

  private static boolean looksLikeRelativeFileToken(String token) {
    if (token == null) {
      return false;
    }
    String trimmed = token.trim();
    if (trimmed.isEmpty()) {
      return false;
    }
    try {
      if (Paths.get(trimmed).isAbsolute()) {
        return false;
      }
    } catch (Exception e) {
      return false;
    }
    if (trimmed.startsWith("-")) {
      return false;
    }
    if (trimmed.contains("/") || trimmed.contains("\\")) {
      return true;
    }
    String lower = trimmed.toLowerCase(Locale.ROOT);
    return lower.endsWith(".exe")
        || lower.endsWith(".bat")
        || lower.endsWith(".cmd")
        || lower.endsWith(".jar")
        || lower.endsWith(".cfg")
        || lower.endsWith(".txt")
        || lower.endsWith(".pem")
        || lower.endsWith(".bin")
        || lower.endsWith(".bin.gz")
        || lower.endsWith(".dll");
  }

  private static Path resolveCandidate(Path root, String relativeToken) {
    return root.resolve(relativeToken).toAbsolutePath().normalize();
  }

  private static boolean isWindowsPlatform() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private static boolean isFilesystemRoot(Path path) {
    return path != null && path.toAbsolutePath().normalize().getParent() == null;
  }

  private static void configureWindowsSearchPath(
      ProcessBuilder processBuilder, LaunchSpec launchSpec) {
    Map<String, String> environment = processBuilder.environment();
    if (environment == null) {
      return;
    }

    List<String> orderedEntries = new ArrayList<String>();
    LinkedHashSet<String> seenEntries = new LinkedHashSet<String>();

    addLaunchDirectory(orderedEntries, seenEntries, launchSpec);
    addWorkingDirectory(orderedEntries, seenEntries, launchSpec);
    addPathEntries(orderedEntries, seenEntries, readEnvironmentPath(environment));
    addPathEntries(orderedEntries, seenEntries, System.getenv("PATH"));
    addPathEntries(orderedEntries, seenEntries, System.getenv("Path"));
    addPathEntries(
        orderedEntries,
        seenEntries,
        readRegistryPath("HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment"));
    addPathEntries(orderedEntries, seenEntries, readRegistryPath("HKCU\\Environment"));

    if (orderedEntries.isEmpty()) {
      return;
    }

    String pathKey = findEnvironmentKeyIgnoreCase(environment, "PATH");
    removeEnvironmentKeysIgnoreCase(environment, "PATH");
    environment.put(
        pathKey == null ? "Path" : pathKey, String.join(File.pathSeparator, orderedEntries));
  }

  private static void addLaunchDirectory(
      List<String> orderedEntries, LinkedHashSet<String> seenEntries, LaunchSpec launchSpec) {
    if (launchSpec == null
        || launchSpec.getCommandParts() == null
        || launchSpec.getCommandParts().isEmpty()) {
      return;
    }
    String executableToken = launchSpec.getCommandParts().get(0);
    Path executablePath = Utils.resolveExistingExecutable(executableToken);
    if (executablePath == null) {
      Path executableDirectory = toAbsoluteReferenceDirectory(executableToken);
      if (executableDirectory != null) {
        executablePath = executableDirectory;
      }
    }
    if (executablePath == null) {
      return;
    }
    Path directory =
        Files.isDirectory(executablePath) ? executablePath : executablePath.getParent();
    if (directory != null) {
      addPathEntry(orderedEntries, seenEntries, directory.toAbsolutePath().normalize().toString());
    }
  }

  private static void addWorkingDirectory(
      List<String> orderedEntries, LinkedHashSet<String> seenEntries, LaunchSpec launchSpec) {
    if (launchSpec == null || launchSpec.getWorkingDirectory() == null) {
      return;
    }
    File workingDirectory = launchSpec.getWorkingDirectory();
    if (workingDirectory.isDirectory()) {
      addPathEntry(orderedEntries, seenEntries, workingDirectory.getAbsolutePath());
    }
  }

  private static String readEnvironmentPath(Map<String, String> environment) {
    if (environment == null) {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, String> entry : environment.entrySet()) {
      if (entry.getKey() == null || !entry.getKey().equalsIgnoreCase("PATH")) {
        continue;
      }
      appendPathValue(builder, entry.getValue());
    }
    return builder.toString();
  }

  private static void appendPathValue(StringBuilder builder, String value) {
    if (builder == null || value == null) {
      return;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return;
    }
    if (builder.length() > 0) {
      builder.append(File.pathSeparator);
    }
    builder.append(trimmed);
  }

  private static void addPathEntries(
      List<String> orderedEntries, LinkedHashSet<String> seenEntries, String pathValue) {
    if (pathValue == null || pathValue.trim().isEmpty()) {
      return;
    }
    for (String rawEntry : pathValue.split(Pattern.quote(File.pathSeparator))) {
      addPathEntry(orderedEntries, seenEntries, rawEntry);
    }
  }

  private static void addPathEntry(
      List<String> orderedEntries, LinkedHashSet<String> seenEntries, String rawEntry) {
    if (orderedEntries == null || seenEntries == null || rawEntry == null) {
      return;
    }
    String cleaned = stripWrappingQuotes(rawEntry.trim());
    if (cleaned.isEmpty()) {
      return;
    }
    String normalizedKey = cleaned.replace('/', '\\').toLowerCase(Locale.ROOT);
    if (!seenEntries.add(normalizedKey)) {
      return;
    }
    orderedEntries.add(cleaned);
  }

  private static String stripWrappingQuotes(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      return trimmed.substring(1, trimmed.length() - 1).trim();
    }
    return trimmed;
  }

  private static String findEnvironmentKeyIgnoreCase(Map<String, String> environment, String key) {
    if (environment == null || key == null) {
      return null;
    }
    for (String existingKey : environment.keySet()) {
      if (existingKey != null && existingKey.equalsIgnoreCase(key)) {
        return existingKey;
      }
    }
    return null;
  }

  private static void removeEnvironmentKeysIgnoreCase(Map<String, String> environment, String key) {
    if (environment == null || key == null) {
      return;
    }
    List<String> toRemove = new ArrayList<String>();
    for (String existingKey : environment.keySet()) {
      if (existingKey != null && existingKey.equalsIgnoreCase(key)) {
        toRemove.add(existingKey);
      }
    }
    for (String existingKey : toRemove) {
      environment.remove(existingKey);
    }
  }

  private static String readRegistryPath(String keyPath) {
    String systemRoot = firstNonBlank(System.getenv("SystemRoot"), System.getenv("WINDIR"));
    if (systemRoot == null || keyPath == null || keyPath.trim().isEmpty()) {
      return null;
    }
    Path regExe = Paths.get(systemRoot, "System32", "reg.exe");
    if (!Files.isRegularFile(regExe)) {
      return null;
    }

    ProcessBuilder processBuilder =
        new ProcessBuilder(regExe.toString(), "query", keyPath, "/v", "Path");
    processBuilder.redirectErrorStream(true);
    try {
      Process process = processBuilder.start();
      String pathValue = null;
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          Matcher matcher = REGISTRY_PATH_LINE_PATTERN.matcher(line);
          if (matcher.matches()) {
            pathValue = matcher.group(1).trim();
          }
        }
      }
      int exitCode = process.waitFor();
      if (exitCode != 0 || pathValue == null || pathValue.trim().isEmpty()) {
        return null;
      }
      return expandWindowsEnvironmentReferences(pathValue, System.getenv());
    } catch (IOException e) {
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (value != null && !value.trim().isEmpty()) {
        return value.trim();
      }
    }
    return null;
  }

  private static String expandWindowsEnvironmentReferences(
      String value, Map<String, String> environment) {
    if (value == null || value.indexOf('%') < 0) {
      return value;
    }
    Matcher matcher = WINDOWS_ENV_REFERENCE_PATTERN.matcher(value);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String variableName = matcher.group(1);
      String replacement = getEnvironmentValueIgnoreCase(environment, variableName);
      if (replacement == null) {
        replacement = matcher.group(0);
      }
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private static String getEnvironmentValueIgnoreCase(Map<String, String> environment, String key) {
    if (environment != null) {
      for (Map.Entry<String, String> entry : environment.entrySet()) {
        if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
          return entry.getValue();
        }
      }
    }
    return null;
  }

  public static final class LaunchSpec {
    private final List<String> commandParts;
    private final File workingDirectory;

    private LaunchSpec(List<String> commandParts, File workingDirectory) {
      this.commandParts = commandParts;
      this.workingDirectory = workingDirectory;
    }

    public List<String> getCommandParts() {
      return commandParts;
    }

    public File getWorkingDirectory() {
      return workingDirectory;
    }
  }
}
