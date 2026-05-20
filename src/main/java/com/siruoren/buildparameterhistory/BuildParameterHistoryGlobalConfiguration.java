package com.siruoren.buildparameterhistory;

import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class BuildParameterHistoryGlobalConfiguration extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(BuildParameterHistoryGlobalConfiguration.class.getName());
    private static final int DEFAULT_MAX_RECORDS = 200;
    private static final int MIN_MAX_RECORDS = 1;
    private static final int MAX_MAX_RECORDS = 10000;

    private int maxRecords = DEFAULT_MAX_RECORDS;

    public BuildParameterHistoryGlobalConfiguration() {
        load();
        LOGGER.log(Level.INFO, "BuildParameterHistoryGlobalConfiguration loaded, maxRecords={0}", maxRecords);
    }

    public static BuildParameterHistoryGlobalConfiguration get() {
        return GlobalConfiguration.all().getInstance(BuildParameterHistoryGlobalConfiguration.class);
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        maxRecords = formData.getInt("maxRecords");
        if (maxRecords < MIN_MAX_RECORDS) {
            maxRecords = MIN_MAX_RECORDS;
        }
        if (maxRecords > MAX_MAX_RECORDS) {
            maxRecords = MAX_MAX_RECORDS;
        }
        LOGGER.log(Level.INFO, "BuildParameterHistoryGlobalConfiguration configured, maxRecords={0}", maxRecords);
        save();
        return true;
    }

    public int getMaxRecords() {
        LOGGER.log(Level.FINE, "BuildParameterHistoryGlobalConfiguration getMaxRecords={0}", maxRecords);
        return maxRecords;
    }

    public void setMaxRecords(int maxRecords) {
        if (maxRecords < MIN_MAX_RECORDS) {
            maxRecords = MIN_MAX_RECORDS;
        }
        if (maxRecords > MAX_MAX_RECORDS) {
            maxRecords = MAX_MAX_RECORDS;
        }
        this.maxRecords = maxRecords;
        LOGGER.log(Level.INFO, "BuildParameterHistoryGlobalConfiguration setMaxRecords={0}", maxRecords);
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
