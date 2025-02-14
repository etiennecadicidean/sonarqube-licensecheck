package at.porscheinformatik.sonarqube.licensecheck.flutter;

import at.porscheinformatik.sonarqube.licensecheck.Dependency;
import at.porscheinformatik.sonarqube.licensecheck.LicenseCheckRulesDefinition;
import at.porscheinformatik.sonarqube.licensecheck.Scanner;
import at.porscheinformatik.sonarqube.licensecheck.licensemapping.LicenseMappingService;
import org.codehaus.plexus.util.StringUtils;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PubDependencyScanner implements Scanner
{
    private static final Logger LOGGER = Loggers.get(PubDependencyScanner.class);
    private final LicenseMappingService licenseMappingService;

    public PubDependencyScanner(LicenseMappingService licenseMappingService)
    {
        this.licenseMappingService = licenseMappingService;
    }

    @Override
    public Set<Dependency> scan(SensorContext context)
    {
        File moduleDir = context.fileSystem().baseDir();
        Map<Pattern, String> defaultLicenseMap = licenseMappingService.getLicenseMap();

        File licenseDetailsJsonFile = new File(moduleDir, "build" + File.separator + "reports" + File.separator + "license_finder" + File.separator + "flutter-license-details.json");

        if (!licenseDetailsJsonFile.exists())
        {
            LOGGER.info("No flutter-license-details.json file found in {} - skipping Flutter/Pub dependency scan",
                    licenseDetailsJsonFile.getPath());
            return Collections.emptySet();
        }

        return readLicenseDetailsJson(licenseDetailsJsonFile)
                .stream()
                .map(d -> mapMavenDependencyToLicense(defaultLicenseMap, d))
                .peek(d -> d.setInputComponent(context.module()))
                .collect(Collectors.toSet());
    }

    private Set<Dependency> readLicenseDetailsJson(File licenseDetailsJsonFile)
    {
        final Set<Dependency> dependencySet = new HashSet<>();

        try (InputStream fis = new FileInputStream(licenseDetailsJsonFile);
            JsonReader jsonReader = Json.createReader(fis))
        {
            JsonObject jo = jsonReader.readObject();
            JsonArray arr = jo.getJsonArray("dependencies");
            prepareDependencySet(dependencySet, arr);
            return dependencySet;
        }
        catch (Exception e)
        {
            LOGGER.error("Problems reading Flutter/Pub license file {}: {}",
                licenseDetailsJsonFile.getPath(), e.getMessage());
        }
        return dependencySet;
    }

    private void prepareDependencySet(Set<Dependency> dependencySet, JsonArray arr)
    {
        for (javax.json.JsonValue entry : arr)
        {
            JsonObject jsonDepObj = entry.asJsonObject();
            String moduleLicense = getModuleLicenseFromJsonObject(jsonDepObj);
            String moduleLicenseUrl = null;
            Dependency dep = new Dependency(jsonDepObj.getString("name", null),
                jsonDepObj.getString("version", null), moduleLicense, LicenseCheckRulesDefinition.LANG_DART);
            dep.setPomPath(moduleLicenseUrl);
            dependencySet.add(dep);
        }
    }

    private String getModuleLicenseFromJsonObject(JsonObject jsonDepObj)
    {
        String moduleLicense = null;
        JsonArray arrModuleLicenses = jsonDepObj.getJsonArray("licenses");
        if (arrModuleLicenses != null)
        {
            moduleLicense = getModuleLicense(arrModuleLicenses);
        }
        return moduleLicense;
    }

    private String getModuleLicense(JsonArray arrModuleLicenses) {
        if (arrModuleLicenses != null && !arrModuleLicenses.isEmpty()) {
            if (arrModuleLicenses.getString(0).equals("unknown"))
                return null;
            return arrModuleLicenses.getString(0);
        } else {
            return null;
        }
    }

    private Dependency mapMavenDependencyToLicense(Map<Pattern, String> defaultLicenseMap, Dependency dependency)
    {
        if (StringUtils.isBlank(dependency.getLicense()))
        {
            LOGGER.error(" License not found for Dependency {}", dependency);
            return dependency;
        }

        for (Map.Entry<Pattern, String> allowedDependency : defaultLicenseMap.entrySet())
        {
            if (allowedDependency.getKey().matcher(dependency.getLicense()).matches())
            {
                dependency.setLicense(allowedDependency.getValue());
                break;
            }
        }
        return dependency;
    }
}
