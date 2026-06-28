package org.maunu.jakartaee.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Unit tests for PackageScanner.
 *
 * @author Mikko Maunu
 */
public class PackageScannerTest {

    @Test
    public void testGetPackagesWithJakartaServlet() {
        PackageScanner scanner = new PackageScanner();
        List<String> packages = scanner.getPackages("jakarta.servlet");

        // Should return at least some child packages
        assertTrue(packages.size() > 0, "Should find child packages under jakarta.servlet");

        // All returned packages should start with jakarta.servlet.
        for (String pkg : packages) {
            assertTrue(pkg.startsWith("jakarta.servlet."), 
                "Package " + pkg + " should start with jakarta.servlet.");
        }
    }

    @Test
    public void testGetPackagesWithJakarta() {
        PackageScanner scanner = new PackageScanner();
        List<String> packages = scanner.getPackages("jakarta");

        // Should return child packages of jakarta
        assertTrue(packages.size() > 0, "Should find child packages under jakarta");

        for (String pkg : packages) {
            assertTrue(pkg.startsWith("jakarta."), 
                "Package " + pkg + " should start with jakarta.");
        }
    }

    @Test
    public void testGetPackagesWithNonExistentPackage() {
        PackageScanner scanner = new PackageScanner();
        List<String> packages = scanner.getPackages("nonexistent.package");

        // Should return empty list for non-existent package
        assertTrue(packages.isEmpty(), "Should return empty list for non-existent package");
    }

    @Test
    public void testGetPackageInfoWithExistingPackage() {
        PackageScanner scanner = new PackageScanner();
        String packageInfo = scanner.getPackageInfo("jakarta.jws");

        // Should return package-info.java content for existing package
        assertTrue(packageInfo != null && !packageInfo.isEmpty(), 
            "Should find package-info.java for jakarta.jws");
        assertTrue(packageInfo.contains("package") || packageInfo.contains("jakarta"),
            "package-info.java should contain package-related content");
    }

    @Test
    public void testGetPackageInfoWithNonExistentPackage() {
        PackageScanner scanner = new PackageScanner();
        String packageInfo = scanner.getPackageInfo("nonexistent.package");

        // Should return null for non-existent package
        assertTrue(packageInfo == null, "Should return null for non-existent package");
    }

    @Test
    public void testGetPackageDocWithPackageInfo() {
        PackageScanner scanner = new PackageScanner();
        Map.Entry<String, String> packageDoc = scanner.getPackageDoc("jakarta.jws");

        // Should return package-info.java content for existing package
        assertTrue(packageDoc != null && !packageDoc.getKey().isEmpty(), 
            "Should find package-info.java for jakarta.jws");
        assertEquals("package-info.java", packageDoc.getValue(), 
            "Should identify package-info.java as the source");
        assertTrue(packageDoc.getKey().contains("package") || packageDoc.getKey().contains("jakarta"),
            "package-info.java should contain package-related content");
    }

    @Test
    public void testGetPackageDocWithPackageHtml() {
        PackageScanner scanner = new PackageScanner();
        Map.Entry<String, String> packageDoc = scanner.getPackageDoc("jakarta.servlet");

        // Should return package.html content for packages that have it
        assertTrue(packageDoc != null && !packageDoc.getKey().isEmpty(), 
            "Should find package documentation for jakarta.servlet");
        assertEquals("package.html", packageDoc.getValue(), 
            "Should identify package.html as the source when package-info.java not found");
    }

    @Test
    public void testGetPackageDocWithNonExistentPackage() {
        PackageScanner scanner = new PackageScanner();
        Map.Entry<String, String> packageDoc = scanner.getPackageDoc("nonexistent.package");

        // Should return null for non-existent package
        assertTrue(packageDoc == null, "Should return null for non-existent package");
    }

    @Test
    public void testGetClassesWithExistingPackage() {
        PackageScanner scanner = new PackageScanner();
        List<String> classes = scanner.getClasses("jakarta.servlet");

        // Should return classes for existing package
        assertTrue(classes.size() > 0, "Should find classes in jakarta.servlet package");
        
        // All returned items should be class names (no .java extension)
        for (String className : classes) {
            assertTrue(!className.endsWith(".java"), 
                "Class name should not have .java extension: " + className);
        }
    }

    @Test
    public void testGetClassesWithNonExistentPackage() {
        PackageScanner scanner = new PackageScanner();
        List<String> classes = scanner.getClasses("nonexistent.package");

        // Should return empty list for non-existent package
        assertTrue(classes.isEmpty(), "Should return empty list for non-existent package");
    }

    @Test
    public void testGetSourceCodeWithExistingClass() {
        PackageScanner scanner = new PackageScanner();
        String sourceCode = scanner.getSourceCode("jakarta.servlet.Servlet");

        // Should return source code for existing class
        assertTrue(sourceCode != null && !sourceCode.isEmpty(), 
            "Should find source code for jakarta.servlet.Servlet");
        // Just check it contains some expected content
        assertTrue(sourceCode.contains("Servlet"),
            "Source code should contain Servlet. Actual: " + (sourceCode != null ? sourceCode.substring(0, Math.min(100, sourceCode.length())) : "null"));
    }

    @Test
    public void testGetSourceCodeWithNonExistentClass() {
        PackageScanner scanner = new PackageScanner();
        String sourceCode = scanner.getSourceCode("nonexistent.package.NonExistentClass");

        // Should return null for non-existent class
        assertTrue(sourceCode == null, "Should return null for non-existent class");
    }
}
