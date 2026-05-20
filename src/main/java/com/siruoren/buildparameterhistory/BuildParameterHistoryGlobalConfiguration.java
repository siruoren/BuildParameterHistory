package com.siruoren.buildparameterhistory;

import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

@Extension
public class BuildParameterHistoryGlobalConfiguration extends GlobalConfiguration {

    private static final int DEFAULT_MAX_RECORDS = 200;
    private static final int MIN_MAX_RECORDS = 1;
    private static final int MAX_MAX_RECORDS = 10000;

    private int maxRecords = DEFAULT_MAX_RECORDS;

    public BuildParameterHistoryGlobalConfiguration() {
        load();
    }

    public static BuildParameterHistoryGlobalConfiguration get() {
        return GlobalConfiguration.all().getInstance(BuildParameterHistoryGlobalConfiguration.class);
    }

    public int getMaxRecords() {
        return maxRecords;
    }

    @DataBoundSetter
    public void setMaxRecords(int maxRecords) {
        if (maxRecords < MIN_MAX_RECORDS) {
            maxRecords = MIN_MAX_RECORDS;
        }
        if (maxRecords > MAX_MAX_RECORDS) {
            maxRecords = MAX_MAX_RECORDS;
        }
        this.maxRecords = maxRecords;
        save();
    }

    public int getDefaultMaxRecords() {
        return DEFAULT_MAX_RECORDS;
    }

    public int getMinMaxRecords() {
        return MIN_MAX_RECORDS;
    }

    public int getMaxMaxRecords() {
        return MAX_MAX_RECORDS;
    }

    public FormValidation doCheckMaxRecords(@QueryParameter int maxRecords) {
        if (maxRecords < MIN_MAX_RECORDS) {
            return FormValidation.error(Messages.GlobalConfig_MaxRecords_TooSmall(MIN_MAX_RECORDS));
        }
        if (maxRecords > MAX_MAX_RECORDS) {
            return FormValidation.error(Messages.GlobalConfig_MaxRecords_TooLarge(MAX_MAX_RECORDS));
        }
        return FormValidation.ok();
    }

    @Override
    public String getDisplayName() {
        return Messages.GlobalConfig_DisplayName();
    }
}
