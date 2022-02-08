package at.porscheinformatik.sonarqube.licensecheck.gradle;

import at.porscheinformatik.sonarqube.licensecheck.Dependency;
import at.porscheinformatik.sonarqube.licensecheck.LicenseCheckLanguage;
import at.porscheinformatik.sonarqube.licensecheck.interfaces.Scanner;
import at.porscheinformatik.sonarqube.licensecheck.mavenlicense.MavenLicenseService;

import org.codehaus.plexus.util.StringUtils;
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
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GradleDependencyScanner implements Scanner
{
    private static final Logger LOGGER = Loggers.get(GradleDependencyScanner.class);
    private final MavenLicenseService mavenLicenseService;

    public GradleDependencyScanner(MavenLicenseService mavenLicenseService)
    {
        this.mavenLicenseService = mavenLicenseService;
    }

    @Override
    public Set<Dependency> scan(File moduleDir)
    {

        LOGGER.info("Licensecheck scan on {}", moduleDir.getPath());

        Map<Pattern, String> defaultLicenseMap = mavenLicenseService.getLicenseMap();

        File licenseDetailsJsonFile = new File(moduleDir, "build" + File.separator + "reports" + File.separator
            + "dependency-license" + File.separator + "license-details.json");

        if (!licenseDetailsJsonFile.exists())
        {
            LOGGER.info("No license-details.json file found in {} - skipping Gradle dependency scan",
                licenseDetailsJsonFile.getPath());
            return Collections.emptySet();
        }

        LOGGER.info("Licensecheck - Proceeding with analysis with {}",licenseDetailsJsonFile.getPath());

        return readLicenseDetailsJson(licenseDetailsJsonFile)
            .stream()
            .map(d -> mapMavenDependencyToLicense(defaultLicenseMap, d))
            .collect(Collectors.toSet());
    }

    private Set<Dependency> readLicenseDetailsJson(File licenseDetailsJsonFile)
    {
        Set<Dependency> dependencySet = new HashSet<>();
        try (InputStream fis = new FileInputStream(licenseDetailsJsonFile);
            JsonReader jsonReader = Json.createReader(fis))
        {

            JsonObject readObject = jsonReader.readObject();

            JsonArray arr = readObject.getJsonArray("dependencies");

            //read additional data from imported modules
            JsonArray importedModuleDependencies = null;
            if (readObject.containsKey("importedModules")) {
                importedModuleDependencies = readObject.getJsonArray("importedModules").get(0).asJsonObject().getJsonArray("dependencies");
            }

            prepareDependencySet(dependencySet, arr, importedModuleDependencies);
            return dependencySet;
        }
        catch (IOException e)
        {
            LOGGER.error("Problems reading Gradle license file {}: {}",
                licenseDetailsJsonFile.getPath(), e.getMessage());
        }
        return dependencySet;
    }

    private void prepareDependencySet(Set<Dependency> dependencySet, JsonArray arr, JsonArray importedModules)
    {
        for (javax.json.JsonValue entry : arr)
        {
            JsonObject jsonDepObj = entry.asJsonObject();
            JsonArray arrModuleUrls = jsonDepObj.getJsonArray("moduleUrls");
            String moduleLicense = getModuleLicenseFromJsonObject(jsonDepObj, importedModules);
            String moduleLicenseUrl = null;
            if (arrModuleUrls != null)
            {
                moduleLicenseUrl = arrModuleUrls.getString(0, null);
            }
            Dependency dep = new Dependency(jsonDepObj.getString("moduleName", null),
                jsonDepObj.getString("moduleVersion", null), moduleLicense);
            dep.setPomPath(moduleLicenseUrl);
            dependencySet.add(dep);
        }
    }

    private String getModuleLicenseFromJsonObject(JsonObject jsonDepObj, JsonArray optImportedModules)
    {
        String moduleLicense = null;
        JsonArray arrModuleLicenses = jsonDepObj.getJsonArray("moduleLicenses");
        if (arrModuleLicenses != null)
        {
            moduleLicense = getModuleLicense(arrModuleLicenses);
        } else if (optImportedModules != null) {
            LOGGER.debug("No license found for {}... checking additional informations...", jsonDepObj.getString("moduleName"));
            List<javax.json.JsonValue> importedDep = optImportedModules.stream().filter(d -> (d.asJsonObject()).getString("moduleName").equals(jsonDepObj.getString("moduleName"))).collect(Collectors.toList());
            if (importedDep != null && !importedDep.isEmpty()) {
                LOGGER.debug("License declared imported module !");
                moduleLicense = (importedDep.get(0).asJsonObject()).getString("moduleLicense", null);
            } else {
                LOGGER.debug("License not found in imported module.");
            }
        }
        return moduleLicense;
    }

    private String getModuleLicense(JsonArray arrModuleLicenses)
    {
        String moduleLicense = null;
        JsonObject firstJsonObj = arrModuleLicenses.getJsonObject(0);
        if (firstJsonObj != null)
        {
            moduleLicense = firstJsonObj.getString("moduleLicense", null);
            if (moduleLicense == null)
            {
                moduleLicense = firstJsonObj.getString("moduleLicenseUrl", null);
            }
        }
        return moduleLicense;
    }

    private Dependency mapMavenDependencyToLicense(Map<Pattern, String> defaultLicenseMap, Dependency dependency)
    {
        if (StringUtils.isBlank(dependency.getLicense()))
        {
            LOGGER.error("License not found for Dependency {}", dependency);
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

    public String getLanguage() {
        return LicenseCheckLanguage.JAVA.getLanguage();
    }
}
