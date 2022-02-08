/*
 * SonarQube Licencecheck Plugin
 * Copyright (C) 2016 Porsche Informatik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package at.porscheinformatik.sonarqube.licensecheck;

import org.sonar.api.rule.Severity;
import org.sonar.api.server.rule.RulesDefinition;

/**
 * Repository for the rules used in the plugin
 */
public final class LicenseCheckRulesDefinition implements RulesDefinition {
    
    @Override
    public void define(final Context context) {
        for (int i = 0; i < LicenseCheckLanguage.values().length; i++) {
            String language = LicenseCheckLanguage.values()[i].toString();
            NewRepository repository = context.createRepository(getRepositoryKey(language), language);
            repository.setName(getRepositoryName(language));
            repository
                    .createRule(LicenseCheckMetrics.LICENSE_CHECK_UNLISTED_KEY)
                    .setName("Dependency has unknown license [license-check]")
                    .setHtmlDescription("The dependencies license could not be determined!")
                    .setSeverity(Severity.BLOCKER);

            repository
                    .createRule(LicenseCheckMetrics.LICENSE_CHECK_NOT_ALLOWED_LICENSE_KEY)
                    .setName("License is not allowed [license-check]")
                    .setHtmlDescription("Violation because the license of the dependency is not allowed.")
                    .setSeverity(Severity.BLOCKER);

            repository.done();
        }
    }

    public static String getRepositoryName(String language) {
        return "License Check" + "(" + language + ")";
    }
    
    public static String getRepositoryKey(String language) {
        return LicenseCheckMetrics.LICENSE_CHECK_KEY.concat(".").concat(language);
    }

    public static String[] getRepositories() {
        String[] repoStrings = new String[LicenseCheckLanguage.values().length];
        for (int i = 0; i < repoStrings.length; i++) {
            repoStrings[i] = getRepositoryKey(LicenseCheckLanguage.values()[i].getLanguage());
        }
        return repoStrings;
    }
}
