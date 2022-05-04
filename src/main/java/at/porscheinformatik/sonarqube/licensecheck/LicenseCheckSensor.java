package at.porscheinformatik.sonarqube.licensecheck;

import java.util.Set;
import java.util.TreeSet;

import at.porscheinformatik.sonarqube.licensecheck.gradle.GradleDependencyScanner;
import at.porscheinformatik.sonarqube.licensecheck.swift.SwiftDependencyScanner;
import at.porscheinformatik.sonarqube.licensecheck.flutter.PubDependencyScanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.bootstrap.ProjectDefinition;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputModule;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.config.Settings;

import at.porscheinformatik.sonarqube.licensecheck.interfaces.Scanner;
import at.porscheinformatik.sonarqube.licensecheck.license.License;
import at.porscheinformatik.sonarqube.licensecheck.maven.MavenDependencyScanner;
import at.porscheinformatik.sonarqube.licensecheck.mavendependency.MavenDependencyService;
import at.porscheinformatik.sonarqube.licensecheck.mavenlicense.MavenLicenseService;
import at.porscheinformatik.sonarqube.licensecheck.npm.PackageJsonDependencyScanner;

public class LicenseCheckSensor implements Sensor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LicenseCheckSensor.class);

    private final FileSystem fs;
    private final Settings settings;
    private final ValidateLicenses validateLicenses;
    private final Scanner[] scanners;

    public LicenseCheckSensor(FileSystem fs, Settings settings, ValidateLicenses validateLicenses,
        MavenLicenseService mavenLicenseService, MavenDependencyService mavenDependencyService)
    {
        this.fs = fs;
        this.settings = settings;
        this.validateLicenses = validateLicenses;
        this.scanners = new Scanner[] {
            new PackageJsonDependencyScanner(),
            new GradleDependencyScanner(mavenLicenseService),
            new SwiftDependencyScanner(mavenLicenseService),
            new MavenDependencyScanner(mavenLicenseService, mavenDependencyService),
            new PubDependencyScanner(mavenLicenseService)
        };
    }

    private static void saveDependencies(SensorContext sensorContext, Set<Dependency> dependencies)
    {
        if (!dependencies.isEmpty())
        {
            sensorContext
                .newMeasure()
                .forMetric(LicenseCheckMetrics.INPUTDEPENDENCY)
                .withValue(Dependency.createString(dependencies))
                .on(sensorContext.module())
                .save();
        }
    }

    private static void saveLicenses(SensorContext sensorContext, Set<License> licenses)
    {
        if (!licenses.isEmpty())
        {
            sensorContext
                .newMeasure()
                .forMetric(LicenseCheckMetrics.INPUTLICENSE)
                .withValue(License.createString(licenses))
                .on(sensorContext.module())
                .save();
        }
    }

    @Override
    public void describe(SensorDescriptor descriptor)
    {
        descriptor.name("License Check")
            .createIssuesForRuleRepositories(LicenseCheckRulesDefinition.getRepositories());
    }

    @Override
    public void execute(SensorContext context)
    {
        if (settings.getBoolean(LicenseCheckPropertyKeys.ACTIVATION_KEY))
        {
            Set<Dependency> dependencies = new TreeSet<>();

            for (Scanner scanner : scanners)
            {
                Set<Dependency> scannedLicenses = scanner.scan(fs.baseDir());
                validateLicenses.validateLicenses(scannedLicenses, context, scanner.getLanguage());
                dependencies.addAll(scannedLicenses);
            }
            ProjectDefinition project = LicenseCheckPlugin.getRootProject(((DefaultInputModule) context.module()).definition());
        
            Set<License> usedLicenses = validateLicenses.getUsedLicenses(dependencies, project);

            saveDependencies(context, dependencies);
            saveLicenses(context, usedLicenses);
        }
        else
        {
            LOGGER.info("Scanner is set to inactive. No scan possible.");
        }
    }
}