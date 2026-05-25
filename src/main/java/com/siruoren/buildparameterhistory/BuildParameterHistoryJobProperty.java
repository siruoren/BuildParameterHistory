package com.siruoren.buildparameterhistory;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public class BuildParameterHistoryJobProperty extends JobProperty<Job<?, ?>> {

    private static final int MIN_MAX_RECORDS = 1;
    private static final int MAX_MAX_RECORDS = 10000;

    private Integer maxRecords;

    @DataBoundConstructor
    public BuildParameterHistoryJobProperty(Integer maxRecords) {
        this.maxRecords = maxRecords;
    }

    @CheckForNull
    public Integer getMaxRecords() {
        return maxRecords;
    }

    public void setMaxRecords(Integer maxRecords) {
        this.maxRecords = maxRecords;
    }

    public boolean isMaxRecordsSet() {
        return maxRecords != null && maxRecords > 0;
    }

    public int getEffectiveMaxRecords() {
        if (isMaxRecordsSet()) {
            return Math.max(MIN_MAX_RECORDS, Math.min(maxRecords, MAX_MAX_RECORDS));
        }
        return BuildParameterHistoryGlobalConfiguration.get().getMaxRecords();
    }

    @Extension
    @Symbol("buildParameterHistory")
    public static class DescriptorImpl extends JobPropertyDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.JobProperty_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }

        @Override
        public BuildParameterHistoryJobProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (formData == null || formData.isNullObject()) {
                return null;
            }
            Integer maxRecords = null;
            if (formData.has("maxRecords") && !formData.getString("maxRecords").isEmpty()) {
                try {
                    maxRecords = formData.getInt("maxRecords");
                } catch (Exception e) {
                    maxRecords = null;
                }
            }
            return new BuildParameterHistoryJobProperty(maxRecords);
        }

        public int getMinMaxRecords() {
            return MIN_MAX_RECORDS;
        }

        public int getMaxMaxRecords() {
            return MAX_MAX_RECORDS;
        }

        public int getGlobalMaxRecords() {
            return BuildParameterHistoryGlobalConfiguration.get().getMaxRecords();
        }

        public FormValidation doCheckMaxRecords(@QueryParameter int maxRecords) {
            if (maxRecords <= 0) {
                return FormValidation.ok();
            }
            if (maxRecords < MIN_MAX_RECORDS) {
                return FormValidation.error(Messages.JobProperty_MaxRecords_TooSmall(MIN_MAX_RECORDS));
            }
            if (maxRecords > MAX_MAX_RECORDS) {
                return FormValidation.error(Messages.JobProperty_MaxRecords_TooLarge(MAX_MAX_RECORDS));
            }
            return FormValidation.ok();
        }
    }
}
