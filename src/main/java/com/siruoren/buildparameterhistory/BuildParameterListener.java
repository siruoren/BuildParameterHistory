package com.siruoren.buildparameterhistory;

import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


@Extension
public class BuildParameterListener extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(BuildParameterListener.class.getName());

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
        String jobName;
        String buildId;
        String buildUrl;
        long startTime;
        List<BuildParameterRecord.ParameterEntry> parameters;
        File historyFile;

        try {
            jobName = run.getParent().getFullName();
            buildId = String.valueOf(run.getNumber());
            buildUrl = safeGetBuildUrl(run);
            startTime = run.getStartTimeInMillis();
            parameters = extractParameters(run);
            historyFile = BuildParameterHistoryService.getInstance().resolveHistoryFile(run.getParent());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to extract build info on start", e);
            return;
        }

        final String finalJobName = jobName;
        final String finalBuildId = buildId;
        final String finalBuildUrl = buildUrl;
        final long finalStartTime = startTime;
        final List<BuildParameterRecord.ParameterEntry> finalParameters = parameters;
        final File finalHistoryFile = historyFile;

        BuildParameterHistoryThreadPool.getInstance().submit(() -> {
            try {
                BuildParameterRecord record = new BuildParameterRecord(
                        finalJobName,
                        finalBuildId,
                        finalBuildUrl,
                        finalStartTime,
                        0,
                        null,
                        finalParameters
                );
                BuildParameterHistoryService.getInstance().saveRecord(finalHistoryFile, record);
                LOGGER.log(Level.INFO, "Recorded build start: {0} #{1}",
                        new Object[]{finalJobName, finalBuildId});
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to record build start: {0} #{1}",
                        new Object[]{finalJobName, finalBuildId, e});
            }
        });
    }

    @Override
    public void onCompleted(Run<?, ?> run, TaskListener listener) {
        String jobName;
        String buildId;
        String buildUrl;
        long startTime;
        String result;
        List<BuildParameterRecord.ParameterEntry> parameters;
        File historyFile;

        try {
            jobName = run.getParent().getFullName();
            buildId = String.valueOf(run.getNumber());
            buildUrl = safeGetBuildUrl(run);
            startTime = run.getStartTimeInMillis();
            result = run.getResult() != null ? run.getResult().toString() : "UNKNOWN";
            parameters = extractParameters(run);
            historyFile = BuildParameterHistoryService.getInstance().resolveHistoryFile(run.getParent());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to extract build info on completion", e);
            return;
        }

        final String finalJobName = jobName;
        final String finalBuildId = buildId;
        final String finalBuildUrl = buildUrl;
        final long finalStartTime = startTime;
        final String finalResult = result;
        final List<BuildParameterRecord.ParameterEntry> finalParameters = parameters;
        final File finalHistoryFile = historyFile;

        BuildParameterHistoryThreadPool.getInstance().submit(() -> {
            try {
                BuildParameterHistoryService service = BuildParameterHistoryService.getInstance();
                List<BuildParameterRecord> existingRecords = service.getRecordsForJob(finalHistoryFile, finalJobName);

                List<BuildParameterRecord.ParameterEntry> paramsToUse = finalParameters;
                if (existingRecords != null && !existingRecords.isEmpty()) {
                    for (BuildParameterRecord existing : existingRecords) {
                        if (finalBuildId.equals(existing.getBuildId())
                                && existing.getParameters() != null
                                && !existing.getParameters().isEmpty()) {
                            paramsToUse = existing.getParameters();
                            break;
                        }
                    }
                }

                BuildParameterRecord record = new BuildParameterRecord(
                        finalJobName,
                        finalBuildId,
                        finalBuildUrl,
                        finalStartTime,
                        System.currentTimeMillis(),
                        finalResult,
                        paramsToUse
                );
                service.updateRecord(finalHistoryFile, record);
                LOGGER.log(Level.INFO, "Recorded build completion: {0} #{1} -> {2}",
                        new Object[]{finalJobName, finalBuildId, finalResult});
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to record build completion: {0} #{1}",
                        new Object[]{finalJobName, finalBuildId, e});
            }
        });
    }

    private String safeGetBuildUrl(Run<?, ?> run) {
        try {
            String url = run.getUrl();
            if (url != null && !url.isEmpty()) {
                return url;
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to get build URL", e);
        }
        try {
            return run.getParent().getUrl() + run.getNumber() + "/";
        } catch (Exception e) {
            return "";
        }
    }

    private List<BuildParameterRecord.ParameterEntry> extractParameters(Run<?, ?> run) {
        List<BuildParameterRecord.ParameterEntry> parameters = new ArrayList<>();

        ParametersAction paramsAction = run.getAction(ParametersAction.class);
        if (paramsAction != null) {
            List<ParameterValue> paramValues = paramsAction.getParameters();
            if (paramValues != null) {
                for (ParameterValue paramValue : paramValues) {
                    String name = paramValue.getName();
                    String value = paramValue.getValue() != null ? paramValue.getValue().toString() : "";
                    parameters.add(new BuildParameterRecord.ParameterEntry(name, value));
                }
            }
        }

        return parameters;
    }
}
