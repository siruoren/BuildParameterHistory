package com.siruoren.buildparameterhistory;

import hudson.Util;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;

public class BuildParameterRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private String jobName;
    private String buildId;
    private String buildUrl;
    private long startTime;
    private long endTime;
    private String result;
    private List<ParameterEntry> parameters;

    @DataBoundConstructor
    public BuildParameterRecord() {
        this.parameters = new ArrayList<>();
    }

    public BuildParameterRecord(String jobName, String buildId, String buildUrl,
                                long startTime, long endTime, String result,
                                List<ParameterEntry> parameters) {
        this.jobName = jobName;
        this.buildId = buildId;
        this.buildUrl = buildUrl;
        this.startTime = startTime;
        this.endTime = endTime;
        this.result = result;
        this.parameters = parameters != null ? parameters : new ArrayList<>();
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getBuildId() {
        return buildId;
    }

    public void setBuildId(String buildId) {
        this.buildId = buildId;
    }

    public String getBuildUrl() {
        return buildUrl;
    }

    public void setBuildUrl(String buildUrl) {
        this.buildUrl = buildUrl;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public List<ParameterEntry> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterEntry> parameters) {
        this.parameters = parameters != null ? parameters : new ArrayList<>();
    }

    public String getStartTimeDisplay() {
        return Util.getTimeSpanString(startTime);
    }

    public String getEndTimeDisplay() {
        return Util.getTimeSpanString(endTime);
    }

    public String getDuration() {
        if (endTime > 0 && startTime > 0) {
            long duration = endTime - startTime;
            return Util.getTimeSpanString(duration);
        }
        return "N/A";
    }

    public String getFormattedStartTime() {
        if (startTime > 0) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(startTime));
        }
        return "N/A";
    }

    public String getFormattedEndTime() {
        if (endTime > 0) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(endTime));
        }
        return "N/A";
    }

    public static class ParameterEntry implements Serializable {

        private static final long serialVersionUID = 1L;

        private String name;
        private String value;

        @DataBoundConstructor
        public ParameterEntry() {
        }

        public ParameterEntry(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return name + "：" + value;
        }
    }
}
