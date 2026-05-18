package com.siruoren.buildparameterhistory;

import hudson.Extension;
import hudson.model.Cause;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

@Extension
public class BuildParameterListener extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(BuildParameterListener.class.getName());

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
        try {
            recordBuildStart(run);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to record build start for " + run.getFullDisplayName(), e);
        }
    }

    @Override
    public void onCompleted(Run<?, ?> run, @Nonnull TaskListener listener) {
        try {
            recordBuildCompletion(run);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to record build completion for " + run.getFullDisplayName(), e);
        }
    }

    private void recordBuildStart(Run<?, ?> run) {
        BuildParameterHistoryService service = BuildParameterHistoryService.getInstance();

        String jobName = run.getParent().getFullName();
        String buildId = String.valueOf(run.getNumber());
        String buildUrl = run.getUrl();

        List<BuildParameterRecord.ParameterEntry> parameters = extractParameters(run);

        BuildParameterRecord record = new BuildParameterRecord(
                jobName,
                buildId,
                buildUrl,
                run.getStartTimeInMillis(),
                0,
                null,
                parameters
        );

        service.saveRecord(record);
        LOGGER.log(Level.FINE, "Recorded build start: {0} #{1}", new Object[]{jobName, buildId});
    }

    private void recordBuildCompletion(Run<?, ?> run) {
        BuildParameterHistoryService service = BuildParameterHistoryService.getInstance();

        String jobName = run.getParent().getFullName();
        String buildId = String.valueOf(run.getNumber());

        BuildParameterRecord existingRecord = findExistingRecord(service, jobName, buildId);

        List<BuildParameterRecord.ParameterEntry> parameters;
        if (existingRecord != null && existingRecord.getParameters() != null && !existingRecord.getParameters().isEmpty()) {
            parameters = existingRecord.getParameters();
        } else {
            parameters = extractParameters(run);
        }

        String result = run.getResult() != null ? run.getResult().toString() : "UNKNOWN";

        BuildParameterRecord record = new BuildParameterRecord(
                jobName,
                buildId,
                run.getUrl(),
                run.getStartTimeInMillis(),
                System.currentTimeMillis(),
                result,
                parameters
        );

        service.updateRecord(record);
        LOGGER.log(Level.FINE, "Recorded build completion: {0} #{1} -> {2}",
                new Object[]{jobName, buildId, result});
    }

    private BuildParameterRecord findExistingRecord(BuildParameterHistoryService service,
                                                     String jobName, String buildId) {
        List<BuildParameterRecord> records = service.getRecordsForJob(jobName);
        for (BuildParameterRecord record : records) {
            if (buildId.equals(record.getBuildId())) {
                return record;
            }
        }
        return null;
    }

    private List<BuildParameterRecord.ParameterEntry> extractParameters(Run<?, ?> run) {
        List<BuildParameterRecord.ParameterEntry> parameters = new ArrayList<>();

        ParametersAction paramsAction = run.getAction(ParametersAction.class);
        if (paramsAction != null) {
            List<ParameterValue> paramValues = paramsAction.getParameters();
            for (ParameterValue paramValue : paramValues) {
                String name = paramValue.getName();
                String value = paramValue.getValue() != null ? paramValue.getValue().toString() : "";
                parameters.add(new BuildParameterRecord.ParameterEntry(name, value));
            }
        }

        return parameters;
    }
}
