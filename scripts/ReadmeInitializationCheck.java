import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;

public class ReadmeInitializationCheck {
    public static void main(String[] args) throws Exception {
        String classpath = args[0];
        Matcher fence = Pattern.compile("## Initialization\\n.*?```java\\n(.*?)\\n```", Pattern.DOTALL)
            .matcher(Files.readString(Path.of("README.md")));
        if (!fence.find()) throw new AssertionError("README initialization fence not found");
        Path temporary = Files.createTempDirectory("featurevisor-readme-");
        try {
            Path source = temporary.resolve("Main.java");
            Files.writeString(source, fence.group(1));
            int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "11", "-encoding", "UTF-8", "-cp", classpath,
                "-d", temporary.toString(), source.toString());
            if (result != 0) throw new AssertionError("README compilation failed");
            Path datafile = temporary.resolve("featurevisor-all.json");
            Files.writeString(datafile, "{\"schemaVersion\":\"2\",\"revision\":\"readme\","
                + "\"segments\":{},\"variables\":{},\"features\":{\"checkout\":{"
                + "\"hash\":\"checkout\",\"bucketBy\":\"userId\",\"traffic\":[{"
                + "\"key\":\"all\",\"segments\":\"*\",\"percentage\":100000}]}}}");
            execute(temporary, classpath, false, "true");
            Files.writeString(datafile, "{");
            execute(temporary, classpath, true, "Exception");
            Files.delete(datafile);
            execute(temporary, classpath, true, "NoSuchFileException");
            System.out.println("README initialization passed: Java 11 compilation, evaluation, invalid JSON, missing file (runtime "
                + System.getProperty("java.specification.version") + ")");
        } finally {
            try (Stream<Path> paths = Files.walk(temporary)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void execute(Path directory, String classpath, boolean failure, String expected)
            throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path output = directory.resolve("output.txt");
        Process process = new ProcessBuilder(java, "-cp",
            directory + File.pathSeparator + classpath, "Main")
            .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor();
            throw new AssertionError("README execution timed out");
        }
        String text = Files.readString(output, StandardCharsets.UTF_8);
        if ((process.exitValue() != 0) != failure || !text.contains(expected)) {
            throw new AssertionError("Unexpected README result: " + text);
        }
    }
}
