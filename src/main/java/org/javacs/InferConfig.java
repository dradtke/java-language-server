package org.javacs;

import com.google.devtools.build.lib.analysis.AnalysisProtos;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class InferConfig {
    private static final Logger LOG = Logger.getLogger("main");

    /** Root of the workspace that is currently open in VSCode */
    private final Path workspaceRoot;
    /** External dependencies specified manually by the user */
    private final Collection<String> externalDependencies;
    /** Location of the maven repository, usually ~/.m2 */
    private final Path mavenHome;
    /** Location of the gradle cache, usually ~/.gradle */
    private final Path gradleHome;

    InferConfig(Path workspaceRoot, Collection<String> externalDependencies, Path mavenHome, Path gradleHome) {
        this.workspaceRoot = workspaceRoot;
        this.externalDependencies = externalDependencies;
        this.mavenHome = mavenHome;
        this.gradleHome = gradleHome;
    }

    InferConfig(Path workspaceRoot, Collection<String> externalDependencies) {
        this(workspaceRoot, externalDependencies, defaultMavenHome(), defaultGradleHome());
    }

    InferConfig(Path workspaceRoot) {
        this(workspaceRoot, Collections.emptySet(), defaultMavenHome(), defaultGradleHome());
    }

    private static Path defaultMavenHome() {
        return Paths.get(System.getProperty("user.home")).resolve(".m2");
    }

    private static Path defaultGradleHome() {
        return Paths.get(System.getProperty("user.home")).resolve(".gradle");
    }

    /** Find .jar files for external dependencies, for examples maven dependencies in ~/.m2 or jars in bazel-genfiles */
    Set<Path> classPath() {
        // externalDependencies
        if (!externalDependencies.isEmpty()) {
            var result = new HashSet<Path>();
            for (var id : externalDependencies) {
                var a = Artifact.parse(id);
                var found = findAnyJar(a, false);
                if (found == NOT_FOUND) {
                    LOG.warning(String.format("Couldn't find jar for %s in %s or %s", a, mavenHome, gradleHome));
                    continue;
                }
                result.add(found);
            }
            return result;
        }

        // Maven
        var pomXml = workspaceRoot.resolve("pom.xml");
        if (Files.exists(pomXml)) {
            return mvnDependencies(pomXml, "dependency:list");
        }

        // Bazel
        if (Files.exists(workspaceRoot.resolve("WORKSPACE"))) {
            return bazelClasspath();
        }

        // Gradle
        // TODO: support gradlew.bat for Windows
        if (Files.exists(workspaceRoot.resolve("gradlew"))) {
            return gradleDeps(false);
        }

        return Collections.emptySet();
    }

    /** Find source .jar files in local maven repository. */
    Set<Path> buildDocPath() {
        // externalDependencies
        if (!externalDependencies.isEmpty()) {
            var result = new HashSet<Path>();
            for (var id : externalDependencies) {
                var a = Artifact.parse(id);
                var found = findAnyJar(a, true);
                if (found == NOT_FOUND) {
                    LOG.warning(String.format("Couldn't find doc jar for %s in %s or %s", a, mavenHome, gradleHome));
                    continue;
                }
                result.add(found);
            }
            return result;
        }

        // Maven
        var pomXml = workspaceRoot.resolve("pom.xml");
        if (Files.exists(pomXml)) {
            return mvnDependencies(pomXml, "dependency:sources");
        }

        // Bazel
        if (Files.exists(workspaceRoot.resolve("WORKSPACE"))) {
            return bazelDeps("srcjar");
            // TODO proto source jars
        }

        // Gradle
        // TODO: support gradlew.bat for Windows
        if (Files.exists(workspaceRoot.resolve("gradlew"))) {
            return gradleDeps(true);
        }

        return Collections.emptySet();
    }

    private Path findAnyJar(Artifact artifact, boolean source) {
        Path maven = findMavenJar(artifact, source);

        if (maven != NOT_FOUND) {
            return maven;
        } else return findGradleJar(artifact, source);
    }

    Path findMavenJar(Artifact artifact, boolean source) {
        var jar =
                mavenHome
                        .resolve("repository")
                        .resolve(artifact.groupId.replace('.', File.separatorChar))
                        .resolve(artifact.artifactId)
                        .resolve(artifact.version)
                        .resolve(fileName(artifact, source));
        if (!Files.exists(jar)) {
            LOG.warning(jar + " does not exist");
            return NOT_FOUND;
        }
        return jar;
    }

    private static final Pattern JAR_PATTERN = Pattern.compile("^(?<groupId>.+):(?<artifactId>.+):(?<version>[0-9\\.]+)$");

    private Set<Path> gradleDeps(boolean sources) {
      final String jarNameModifier = sources ? "-sources" : "";

      LOG.info("Getting Gradle deps");
      // TODO: don't hardcode this, but it's much faster than the other one
      final Path cacheHome = gradleHome.resolve(Paths.get("caches", "modules-2", "files-2.1"));
      LOG.info("Searching for jars in " + cacheHome.toString());

      try {
        var deps = new HashMap<String, Path>();
        for (String project : gradleProjects()) {
          Scanner scanner = new Scanner(runGradleTask(project + ":dependencies"));
          while (scanner.hasNext()) {
            String word = scanner.next();
            if (deps.containsKey(word)) {
              continue;
            }
            Matcher match = JAR_PATTERN.matcher(word);
            if (match.matches()) {
              String groupId = match.group("groupId");
              String artifactId = match.group("artifactId");
              String version = match.group("version");

              File dependencyFolder = cacheHome.resolve(Paths.get(groupId, artifactId, version)).toFile();
              if (!dependencyFolder.exists()) {
                continue;
              }

              for (String hash : dependencyFolder.list()) {
                File hashFolder = new File(dependencyFolder, hash);
                for (String file : hashFolder.list()) {
                  if (file.equals(artifactId + "-" + version + jarNameModifier + ".jar")) {
                    Path jarPath = new File(hashFolder, file).toPath();
                    // LOG.info("Found " + jarPath.toAbsolutePath());
                    deps.put(word, jarPath);
                  }
                }
              }

              // findGradleJar(artifact, false).ifPresentOrElse(deps::add, () -> LOG.severe("Couldn't find Gradle dependency: " + artifact.toString()));
              // findGradleJar(artifact, false).ifPresent(jar -> deps.put(word, jar));
            }
          }
        }
        // LOG.info("Found " + deps.size() + " deps in total");
        return new HashSet<>(deps.values());
      } catch (IOException e) {
        LOG.severe("Failed to load Gradle deps: " + e.getMessage());
        return Collections.emptySet();
      }
    }

    private List<String> gradleProjects() throws IOException {
      var projects = new ArrayList<String>();
      projects.add(""); // Always add the root project.
      Scanner scanner = new Scanner(runGradleTask("projects"));
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        if (line.contains("--- Project")) {
          int endQuote = line.lastIndexOf("'");
          int startQuote = line.lastIndexOf("'", endQuote-1);
          String project = line.substring(startQuote+1, endQuote);
          projects.add(project);
        }
      }
      return projects;
    }

    private InputStream runGradleTask(String... args) throws IOException {
      String[] command = new String[args.length+1];
      command[0] = "./gradlew";
      for (var i = 0; i < args.length; i++) {
        command[i+1] = args[i];
      }
      Process p = new ProcessBuilder(command)
        .directory(workspaceRoot.toFile())
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start();

      return p.getInputStream();
    }

    private Path findGradleJar(Artifact artifact, boolean source) {
        // Search for caches/modules-*/files-*/groupId/artifactId/version/*/artifactId-version[-sources].jar
        var base = gradleHome.resolve("caches");
        var pattern =
                "glob:"
                        + String.join(
                                File.separator,
                                base.toString(),
                                "modules-*",
                                "files-*",
                                artifact.groupId,
                                artifact.artifactId,
                                artifact.version,
                                "*",
                                fileName(artifact, source));
        var match = FileSystems.getDefault().getPathMatcher(pattern);

        try {
            return Files.walk(base, 7).filter(match::matches).findFirst().orElse(NOT_FOUND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String fileName(Artifact artifact, boolean source) {
        return artifact.artifactId + '-' + artifact.version + (source ? "-sources" : "") + ".jar";
    }

    static Set<Path> mvnDependencies(Path pomXml, String goal) {
        Objects.requireNonNull(pomXml, "pom.xml path is null");
        try {
            // TODO consider using mvn valide dependency:copy-dependencies -DoutputDirectory=??? instead
            // Run maven as a subprocess
            String[] command = {
                getMvnCommand(),
                "--batch-mode", // Turns off ANSI control sequences
                "validate",
                goal,
                "-DincludeScope=test",
                "-DoutputAbsoluteArtifactFilename=true",
            };
            var output = Files.createTempFile("java-language-server-maven-output", ".txt");
            LOG.info("Running " + String.join(" ", command) + " ...");
            var workingDirectory = pomXml.toAbsolutePath().getParent().toFile();
            var process =
                    new ProcessBuilder()
                            .command(command)
                            .directory(workingDirectory)
                            .redirectError(ProcessBuilder.Redirect.INHERIT)
                            .redirectOutput(output.toFile())
                            .start();
            // Wait for process to exit
            var result = process.waitFor();
            if (result != 0) {
                LOG.severe("`" + String.join(" ", command) + "` returned " + result);
                return Set.of();
            }
            // Read output
            var dependencies = new HashSet<Path>();
            for (var line : Files.readAllLines(output)) {
                var jar = readDependency(line);
                if (jar != NOT_FOUND) {
                    dependencies.add(jar);
                }
            }
            return dependencies;
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Pattern DEPENDENCY =
            Pattern.compile("^\\[INFO\\]\\s+(.*:.*:.*:.*:.*):(/.*?)( -- module .*)?$");

    static Path readDependency(String line) {
        var match = DEPENDENCY.matcher(line);
        if (!match.matches()) {
            return NOT_FOUND;
        }
        var artifact = match.group(1);
        var path = match.group(2);
        LOG.info(String.format("...%s => %s", artifact, path));
        return Paths.get(path);
    }

    static String getMvnCommand() {
        var mvnCommand = "mvn";
        if (File.separatorChar == '\\') {
            mvnCommand = findExecutableOnPath("mvn.cmd");
            if (mvnCommand == null) {
                mvnCommand = findExecutableOnPath("mvn.bat");
            }
        }
        return mvnCommand;
    }

    private static String findExecutableOnPath(String name) {
        for (var dirname : System.getenv("PATH").split(File.pathSeparator)) {
            var file = new File(dirname, name);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    private Set<Path> bazelClasspath() {
        try {
            // Run bazel as a subprocess
            String[] command = {
                "bazel",
                "aquery",
                "--output=proto",
                "mnemonic(Javac, kind(java_library, ...) union kind(java_test, ...) union kind(java_binary, ...))"
            };
            var output = Files.createTempFile("java-language-server-bazel-output", ".txt");
            LOG.info("Running " + String.join(" ", command) + " ...");
            var process =
                    new ProcessBuilder()
                            .command(command)
                            .directory(workspaceRoot.toFile())
                            .redirectError(ProcessBuilder.Redirect.INHERIT)
                            .redirectOutput(output.toFile())
                            .start();
            // Wait for process to exit
            var result = process.waitFor();
            if (result != 0) {
                LOG.severe("`" + String.join(" ", command) + "` returned " + result);
                return Set.of();
            }
            // Read output
            var container = AnalysisProtos.ActionGraphContainer.parseFrom(Files.newInputStream(output));
            var argumentPaths = new HashSet<String>();
            var outputIds = new HashSet<String>();
            for (var action : container.getActionsList()) {
                var isClasspath = false;
                for (var argument : action.getArgumentsList()) {
                    if (isClasspath && argument.startsWith("-")) {
                        isClasspath = false;
                        continue;
                    }
                    if (!isClasspath) {
                        isClasspath = argument.equals("--classpath");
                        continue;
                    }
                    argumentPaths.add(argument);
                }
                outputIds.addAll(action.getOutputIdsList());
            }
            var classpath = new HashSet<Path>();
            for (var artifact : container.getArtifactsList()) {
                if (!argumentPaths.contains(artifact.getExecPath())) {
                    // artifact is not on the classpath
                    continue;
                }
                if (outputIds.contains(artifact.getId())) {
                    // artifact is the output of another java action
                    continue;
                }
                var relative = artifact.getExecPath();
                var absolute = workspaceRoot.resolve(relative);
                LOG.info("...found bazel dependency " + relative);
                classpath.add(absolute);
            }
            return classpath;
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Set<Path> bazelDeps(String labelsFilter) {
        try {
            // Run bazel as a subprocess
            var query = "labels(" + labelsFilter + ", deps(...))";
            String[] command = {"bazel", "query", query, "--output", "location"};
            var output = Files.createTempFile("java-language-server-bazel-output", ".txt");
            LOG.info("Running " + String.join(" ", command) + " ...");
            var process =
                    new ProcessBuilder()
                            .command(command)
                            .directory(workspaceRoot.toFile())
                            .redirectError(ProcessBuilder.Redirect.INHERIT)
                            .redirectOutput(output.toFile())
                            .start();
            // Wait for process to exit
            var result = process.waitFor();
            if (result != 0) {
                LOG.severe("`" + String.join(" ", command) + "` returned " + result);
                return Set.of();
            }
            // Read output
            var dependencies = new HashSet<Path>();
            for (var line : Files.readAllLines(output)) {
                var jar = findBazelJar(line);
                if (jar != NOT_FOUND) {
                    dependencies.add(jar);
                }
            }
            return dependencies;
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Example:
    // /private/var/tmp/_bazel_georgefraser/33b8fb8944a241143eca3ae505600d73/external/com_fasterxml_jackson_datatype_jackson_datatype_jdk8/jar/BUILD:6:12: source file @com_fasterxml_jackson_datatype_jackson_datatype_jdk8//jar:jackson-datatype-jdk8-2.9.8.jar
    private static final Pattern LOCATION = Pattern.compile("(.*):\\d+:\\d+: source file @(.*)//jar:(.*\\.jar)");
    private static final Path NOT_FOUND = Paths.get("");

    private Path findBazelJar(String line) {
        var matcher = LOCATION.matcher(line);
        if (!matcher.matches()) {
            LOG.warning(line + " does not look like a jar dependency");
            return NOT_FOUND;
        }
        var build = matcher.group(1);
        var jar = matcher.group(3);
        var path = Paths.get(build).getParent().resolve(jar);
        if (!Files.exists(path)) {
            LOG.warning(path + " does not exist");
            return NOT_FOUND;
        }
        return path;
    }
}
