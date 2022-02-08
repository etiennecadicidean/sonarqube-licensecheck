package at.porscheinformatik.sonarqube.licensecheck;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.bootstrap.ProjectDefinition;
import org.sonar.api.batch.fs.internal.DefaultInputModule;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.batch.sensor.issue.internal.DefaultIssue;

import at.porscheinformatik.sonarqube.licensecheck.license.License;
import at.porscheinformatik.sonarqube.licensecheck.license.LicenseService;

public class ValidateLicensesTest
{
    private static final License APACHE_LICENSE = new License("Apache-2.0", "Apache-2.0", "true");
    private ValidateLicenses validateLicenses;
    private ProjectDefinition projectDefinition;

    @Before
    public void setup()
    {
        projectDefinition = mock(ProjectDefinition.class);
        when(projectDefinition.getBaseDir()).thenReturn(new File("."));
        when(projectDefinition.getWorkDir()).thenReturn(new File("."));
        when(projectDefinition.getParent()).thenReturn(projectDefinition);
        final LicenseService licenseService = mock(LicenseService.class);
        when(licenseService.getLicenses(projectDefinition)).thenReturn(Arrays.asList(
            new License("MIT", "MIT", "false"),
            APACHE_LICENSE));
        validateLicenses = new ValidateLicenses(licenseService);
    }

    private SensorContext createContext()
    {
        SensorContext context = mock(SensorContext.class);
        DefaultInputModule inputModule = mock(DefaultInputModule.class);
        when(inputModule.definition()).thenReturn(projectDefinition);
        when(context.module()).thenReturn(inputModule);
        return context;
    }

    @Test
    public void licenseNotAllowed()
    {
        SensorContext context = createContext();
        DefaultIssue issue = new DefaultIssue(mock(SensorStorage.class));
        when(context.newIssue()).thenReturn(issue);

        validateLicenses.validateLicenses(deps(new Dependency("thing", "1.0", "MIT")), context, LicenseCheckLanguage.JAVA.getLanguage());

        verify(context).newIssue();
        assertThat(issue.toString(), containsString(LicenseCheckMetrics.LICENSE_CHECK_NOT_ALLOWED_LICENSE_KEY));
    }

    @Test
    public void licenseAllowed()
    {
        SensorContext context = createContext();

        validateLicenses.validateLicenses(deps(new Dependency("thing", "1.0", "Apache-2.0"),
            new Dependency("another", "2.0", "Apache-2.0")), context, LicenseCheckLanguage.JAVA.getLanguage());

        verify(context, never()).newIssue();
    }

    @Test
    public void licenseNotFound()
    {
        SensorContext context = createContext();
        DefaultIssue issue = new DefaultIssue(mock(SensorStorage.class));
        when(context.newIssue()).thenReturn(issue);

        validateLicenses.validateLicenses(deps(new Dependency("thing", "1.0", null)), context, LicenseCheckLanguage.JAVA.getLanguage());

        verify(context).newIssue();
        assertThat(issue.toString(), containsString(LicenseCheckMetrics.LICENSE_CHECK_UNLISTED_KEY));
    }

    @Test
    public void getUsedLicenses()
    {
        assertThat(validateLicenses.getUsedLicenses(deps(), projectDefinition).size(), is(0));

        Set<License> usedLicensesApache = validateLicenses.getUsedLicenses(
            deps(new Dependency("thing", "1.0", "Apache-2.0"),
                new Dependency("another", "2.0", "Apache-2.0")), projectDefinition);

        assertThat(usedLicensesApache.size(), is(1));
        assertThat(usedLicensesApache, CoreMatchers.hasItem(APACHE_LICENSE));
    }

    private static Set<Dependency> deps(Dependency... dependencies)
    {
        final Set<Dependency> dependencySet = new HashSet<>();
        Collections.addAll(dependencySet, dependencies);
        return dependencySet;
    }
}
