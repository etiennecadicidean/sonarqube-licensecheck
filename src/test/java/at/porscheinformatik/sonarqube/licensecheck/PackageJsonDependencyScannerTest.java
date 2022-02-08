package at.porscheinformatik.sonarqube.licensecheck;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import at.porscheinformatik.sonarqube.licensecheck.interfaces.Scanner;
import at.porscheinformatik.sonarqube.licensecheck.npm.PackageJsonDependencyScanner;

public class PackageJsonDependencyScannerTest
{
    final File folder = new File("src/test/resources");

    @Test
    public void testHappyPath()
    {
        Scanner scanner = new PackageJsonDependencyScanner();

        Set<Dependency> dependencies = scanner.scan(folder);

        Assert.assertThat(dependencies, CoreMatchers.hasItem(new Dependency("angular", "1.5.0", "MIT")));
    }
}
