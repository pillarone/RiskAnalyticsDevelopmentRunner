Initial setup of RiskAnalytics

fetch components

git clone git@github.com:pillarone/RiskAnalyticsDevelopmentRunner.git
git clone git@github.com:pillarone/risk-analytics-pc-cashflow.git
git clone git@github.com:pillarone/risk-analytics-core.git
git clone git@github.com:pillarone/risk-analytics-application.git
git clone git@github.com:pillarone/risk-analytics-commons.git
git clone git@github.com:pillarone/risk-analytics-property-casualty.git
git clone git@github.com:pillarone/risk-analytics-graph-components.git
git clone git@github.com:pillarone/risk-analytics-reporting.git
git clone git@github.com:pillarone/risk-analytics-graph-form-editor.git
git clone git@github.com:pillarone/pillarone-ulc-extensions.git
git clone git@github.com:pillarone/risk-analytics-doc.git
git clone git@github.com:pillarone/risk-analytics-graph-core.git

make first build

make sure you have set GRAILS_HOME environment variable to your grails 1.3.7 installation path.

goto RiskAnalyticsDevelopmentRunner and call
ant compile