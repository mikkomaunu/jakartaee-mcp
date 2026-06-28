package org.maunu.jakartaee.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Utility class for discovering child packages and classes from Jakarta EE sources.
 * Sources are stored in a JAR file inside the application JAR at /resources/
 * and are extracted on-demand to a temporary directory when needed.
 *
 * @author Mikko Maunu
 */
public class PackageScanner {

    private static final String SOURCES_JAR_PATH = "resources/jakarta.jakartaee-api-10.0.0-sources.jar";
    
    private final ClassLoader classLoader;
    private final Map<String, Path> extractedFilesCache = new HashMap<>();
    private JarFile sourcesJarFile;
    private Path tempDirectory;
    private Path sourcesJarPath;

    /**
     * Creates a new PackageScanner with the default class loader.
     */
    public PackageScanner() {
        this(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates a new PackageScanner with the specified class loader.
     *
     * @param classLoader the class loader to use for scanning
     */
    public PackageScanner(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Gets the temporary directory for extracted files.
     * Creates it if it doesn't exist.
     */
    private synchronized Path getTempDirectory() throws IOException {
        if (tempDirectory == null) {
            tempDirectory = Files.createTempDirectory("jakartaee-mcp-extract-");
            // Add shutdown hook to clean up temp directory
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    deleteRecursively(tempDirectory);
                    // Close the sources JAR file
                    synchronized (this) {
                        if (sourcesJarFile != null) {
                            try {
                                sourcesJarFile.close();
                            } catch (IOException e) {
                                // Ignore
                            }
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Failed to clean up temp directory: " + e.getMessage());
                }
            }));
        }
        return tempDirectory;
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                .sorted((a, b) -> -a.compareTo(b)) // Reverse order: files before directories
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + path + ": " + e.getMessage());
                    }
                });
        }
    }

    /**
     * Gets the sources JAR file, loading it from resources if needed.
     * The sources JAR is embedded inside the application JAR at /resources/
     */
    private synchronized JarFile getSourcesJarFile() throws IOException {
        if (sourcesJarFile == null) {
            // Check if we already extracted it
            if (sourcesJarPath == null || !Files.exists(sourcesJarPath)) {
                // Extract the sources JAR from resources to temp directory
                URL resourceUrl = classLoader.getResource(SOURCES_JAR_PATH);
                if (resourceUrl == null) {
                    throw new IOException("Sources JAR not found at: " + SOURCES_JAR_PATH);
                }
                
                sourcesJarPath = getTempDirectory().resolve("jakarta.jakartaee-api-10.0.0-sources.jar");
                
                try (InputStream is = classLoader.getResourceAsStream(SOURCES_JAR_PATH)) {
                    if (is == null) {
                        throw new IOException("Cannot open stream for: " + SOURCES_JAR_PATH);
                    }
                    Files.copy(is, sourcesJarPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            
            sourcesJarFile = new JarFile(sourcesJarPath.toFile());
        }
        return sourcesJarFile;
    }

    /**
     * Returns all child packages under the specified root package.
     * Scans the Jakarta EE source files from the embedded sources JAR.
     *
     * @param root the root package name (e.g., "jakarta.servlet")
     * @return list of child package names that start with the root followed by a dot
     */
    public List<String> getPackages(String root) {
        List<String> packages = new ArrayList<>();
        String packagePath = root.replace('.', '/') + "/";

        try {
            // Get the sources JAR file
            JarFile jarFile = getSourcesJarFile();
            scanJarForPackages(jarFile, packagePath, root, packages);
        } catch (IOException e) {
            // Return empty list on error
        }

        return packages;
    }

    /**
     * Scans a JAR file for packages under a specific path.
     */
    private void scanJarForPackages(JarFile jarFile, String packagePath, String rootPrefix, List<String> packages) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();
            
            // Check if this entry is a directory under our package path
            if (entryName.startsWith(packagePath) && entry.isDirectory()) {
                String relativePath = entryName.substring(packagePath.length());
                if (!relativePath.isEmpty()) {
                    // Get the first level of subdirectory
                    int firstSlash = relativePath.indexOf('/');
                    String packageName;
                    if (firstSlash == -1) {
                        packageName = rootPrefix + "." + relativePath;
                    } else {
                        packageName = rootPrefix + "." + relativePath.substring(0, firstSlash);
                    }
                    if (!packages.contains(packageName)) {
                        packages.add(packageName);
                        // Recursively scan subdirectories
                        scanJarForPackages(jarFile, packagePath + relativePath.substring(0, firstSlash == -1 ? relativePath.length() : firstSlash + 1) + "/", packageName, packages);
                    }
                }
            }
        }
    }

    /**
     * Returns the content of package-info.java for the specified package.
     * Extracts the file from the embedded sources JAR.
     *
     * @param packageName the package name (e.g., "jakarta.servlet")
     * @return the content of package-info.java as a String, or null if not found
     */
    public String getPackageInfo(String packageName) {
        String filePath = packageName.replace('.', '/') + "/package-info.java";
        return getContentFromJar(filePath);
    }

    /**
     * Returns the content of package documentation for the specified package.
     * Tries package-info.java first, then falls back to package.html.
     * Extracts the file from the embedded sources JAR.
     *
     * @param packageName the package name (e.g., "jakarta.servlet")
     * @return a Pair containing the content and the file type found ("package-info.java" or "package.html"), or null if neither found
     */
    public Map.Entry<String, String> getPackageDoc(String packageName) {
        String packagePath = packageName.replace('.', '/') + "/";
        
        // Try package-info.java first
        String infoContent = getContentFromJar(packagePath + "package-info.java");
        if (infoContent != null) {
            return new java.util.AbstractMap.SimpleEntry<>(infoContent, "package-info.java");
        }
        
        // Fall back to package.html
        String htmlContent = getContentFromJar(packagePath + "package.html");
        if (htmlContent != null) {
            return new java.util.AbstractMap.SimpleEntry<>(htmlContent, "package.html");
        }
        
        return null;
    }

    /**
     * Returns all class names in the specified package.
     * Scans the Jakarta EE source files from the embedded sources JAR.
     *
     * @param packageName the package name (e.g., "jakarta.servlet")
     * @return list of class names (without .java extension) in the package
     */
    public List<String> getClasses(String packageName) {
        List<String> classes = new ArrayList<>();
        String packagePath = packageName.replace('.', '/') + "/";

        try {
            // Get the sources JAR file
            JarFile jarFile = getSourcesJarFile();
            scanJarForClasses(jarFile, packagePath, classes);
        } catch (IOException e) {
            // Return empty list on error
        }

        return classes;
    }

    /**
     * Scans a JAR file for class files in a specific package.
     */
    private void scanJarForClasses(JarFile jarFile, String packagePath, List<String> classes) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String entryName = entry.getName();
            
            if (entryName.startsWith(packagePath) && !entry.isDirectory()) {
                // Get the filename
                String fileName = entryName.substring(packagePath.length());
                if (fileName.endsWith(".java") && !fileName.equals("package-info.java")) {
                    String className = fileName.replace(".java", "");
                    if (!classes.contains(className)) {
                        classes.add(className);
                    }
                }
            }
        }
    }

    /**
     * Returns the source code for the specified fully qualified class name.
     * Extracts the file from the embedded sources JAR.
     *
     * @param className the fully qualified class name (e.g., "jakarta.servlet.Servlet")
     * @return the source code as a String, or null if not found
     */
    public String getSourceCode(String className) {
        String filePath = className.replace('.', '/') + ".java";
        return getContentFromJar(filePath);
    }

    /**
     * Gets content from a file stored in the embedded sources JAR.
     * Extracts and caches the file for subsequent requests.
     */
    private String getContentFromJar(String filePath) {
        synchronized (extractedFilesCache) {
            // Check cache first
            Path cachedFile = extractedFilesCache.get(filePath);
            if (cachedFile != null && Files.exists(cachedFile)) {
                try {
                    return new String(Files.readAllBytes(cachedFile));
                } catch (IOException e) {
                    // Cache miss, continue to extract
                }
            }

            try {
                // Get the sources JAR file
                JarFile jarFile = getSourcesJarFile();
                JarEntry entry = jarFile.getJarEntry(filePath);
                if (entry != null && !entry.isDirectory()) {
                    // Extract to temp directory
                    String safeFileName = filePath.replace('/', '_').replace('.', '_');
                    Path extractedFile = getTempDirectory().resolve(safeFileName);
                    Files.createDirectories(extractedFile.getParent());
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        Files.copy(is, extractedFile, StandardCopyOption.REPLACE_EXISTING);
                        extractedFilesCache.put(filePath, extractedFile);
                        return new String(Files.readAllBytes(extractedFile));
                    }
                }
            } catch (IOException e) {
                // Return null on error
            }
        }
        return null;
    }
}
