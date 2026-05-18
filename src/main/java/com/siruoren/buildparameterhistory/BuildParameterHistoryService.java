package com.siruoren.buildparameterhistory;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Job;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;

public class BuildParameterHistoryService {

    private static final Logger LOGGER = Logger.getLogger(BuildParameterHistoryService.class.getName());
    private static final String HISTORY_FILE = "param_history";

    private static BuildParameterHistoryService instance;

    private BuildParameterHistoryService() {
    }

    public static synchronized BuildParameterHistoryService getInstance() {
        if (instance == null) {
            instance = new BuildParameterHistoryService();
        }
        return instance;
    }

    private File getHistoryFile(String jobName) {
        Job<?, ?> job = Jenkins.get().getItemByFullName(jobName, Job.class);
        if (job == null) {
            LOGGER.log(Level.WARNING, "Job not found: " + jobName);
            return null;
        }
        File jobDir = job.getRootDir();
        if (!jobDir.exists()) {
            jobDir.mkdirs();
        }
        return new File(jobDir, HISTORY_FILE);
    }

    public void saveRecord(@NonNull BuildParameterRecord record) {
        try {
            File historyFile = getHistoryFile(record.getJobName());
            
            String line = formatRecord(record);
            
            if (historyFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(historyFile));
                StringWriter writer = new StringWriter();
                writer.write(line + "\n");
                
                String existingLine;
                while ((existingLine = reader.readLine()) != null) {
                    writer.write(existingLine + "\n");
                }
                reader.close();
                
                BufferedWriter bw = new BufferedWriter(new FileWriter(historyFile));
                bw.write(writer.toString());
                bw.close();
            } else {
                BufferedWriter bw = new BufferedWriter(new FileWriter(historyFile));
                bw.write(line + "\n");
                bw.close();
            }
            
            LOGGER.log(Level.FINE, "Saved build parameter record for {0} #{1}",
                    new Object[]{record.getJobName(), record.getBuildId()});
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save build parameter record for " + record.getJobName(), e);
        }
    }

    private String formatRecord(BuildParameterRecord record) {
        StringBuilder sb = new StringBuilder();
        sb.append(record.getJobName()).append("|");
        sb.append(record.getBuildId()).append("|");
        sb.append(record.getBuildUrl()).append("|");
        sb.append(record.getStartTime()).append("|");
        sb.append(record.getEndTime()).append("|");
        sb.append(record.getResult() != null ? record.getResult() : "UNKNOWN").append("|");
        
        if (record.getParameters() != null && !record.getParameters().isEmpty()) {
            StringBuilder paramsBuilder = new StringBuilder();
            int index = 1;
            for (BuildParameterRecord.ParameterEntry param : record.getParameters()) {
                if (index > 1) {
                    paramsBuilder.append(" ");
                }
                paramsBuilder.append("参数").append(index).append(":").append(param.getName()).append("：").append(param.getValue());
                index++;
            }
            sb.append(paramsBuilder.toString());
        }
        
        return sb.toString();
    }

    public void updateRecord(@NonNull BuildParameterRecord record) {
        saveRecord(record);
    }

    public List<BuildParameterRecord> getRecordsForJob(String jobName) {
        List<BuildParameterRecord> records = new ArrayList<>();
        
        File historyFile = getHistoryFile(jobName);
        if (!historyFile.exists()) {
            return records;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(historyFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                BuildParameterRecord record = parseRecord(line, jobName);
                if (record != null) {
                    records.add(record);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read build parameter history for " + jobName, e);
        }

        return records;
    }

    private BuildParameterRecord parseRecord(String line, String jobName) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        String[] parts = line.split("\\|", 7);
        if (parts.length < 6) {
            return null;
        }

        try {
            String buildId = parts[1];
            String buildUrl = parts[2];
            long startTime = Long.parseLong(parts[3]);
            long endTime = Long.parseLong(parts[4]);
            String result = parts[5];

            List<BuildParameterRecord.ParameterEntry> parameters = new ArrayList<>();
            
            if (parts.length > 6 && parts[6] != null && !parts[6].trim().isEmpty()) {
                String paramsStr = parts[6];
                String[] paramEntries = paramsStr.split(" ");
                for (String entry : paramEntries) {
                    int colonIndex = entry.indexOf(":");
                    if (colonIndex > 0 && colonIndex < entry.length() - 1) {
                        String name = entry.substring(colonIndex + 1, entry.indexOf("："));
                        String value = entry.substring(entry.indexOf("：") + 1);
                        parameters.add(new BuildParameterRecord.ParameterEntry(name, value));
                    }
                }
            }

            return new BuildParameterRecord(jobName, buildId, buildUrl, startTime, endTime, result, parameters);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse record: " + line, e);
            return null;
        }
    }

    public List<BuildParameterRecord> getAllRecords() {
        List<BuildParameterRecord> allRecords = new ArrayList<>();

        for (Job<?, ?> job : Jenkins.get().getAllItems(Job.class)) {
            File historyFile = new File(job.getRootDir(), HISTORY_FILE);
            if (!historyFile.exists()) {
                continue;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(historyFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    BuildParameterRecord record = parseRecord(line, job.getFullName());
                    if (record != null) {
                        allRecords.add(record);
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to read history for " + job.getFullName(), e);
            }
        }

        allRecords.sort(Comparator.comparingLong(BuildParameterRecord::getStartTime).reversed());
        return allRecords;
    }

    public List<BuildParameterRecord> filterRecords(String jobName, String resultFilter,
                                                     String searchKeyword, String parameterName,
                                                     String parameterValue) {
        List<BuildParameterRecord> records;

        if (jobName != null && !jobName.trim().isEmpty()) {
            records = getRecordsForJob(jobName);
        } else {
            records = getAllRecords();
        }

        return records.stream()
                .filter(r -> filterByResult(r, resultFilter))
                .filter(r -> filterBySearchKeyword(r, searchKeyword))
                .filter(r -> filterByParameterName(r, parameterName))
                .filter(r -> filterByParameterValue(r, parameterValue))
                .collect(Collectors.toList());
    }

    private boolean filterByResult(BuildParameterRecord record, String resultFilter) {
        if (resultFilter == null || resultFilter.trim().isEmpty() || "ALL".equalsIgnoreCase(resultFilter)) {
            return true;
        }
        return resultFilter.equalsIgnoreCase(record.getResult());
    }

    private boolean filterBySearchKeyword(BuildParameterRecord record, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase();

        if (record.getJobName() != null && record.getJobName().toLowerCase().contains(lowerKeyword)) {
            return true;
        }
        if (record.getBuildId() != null && record.getBuildId().toLowerCase().contains(lowerKeyword)) {
            return true;
        }
        if (record.getResult() != null && record.getResult().toLowerCase().contains(lowerKeyword)) {
            return true;
        }
        if (record.getFormattedStartTime() != null && record.getFormattedStartTime().toLowerCase().contains(lowerKeyword)) {
            return true;
        }
        if (record.getFormattedEndTime() != null && record.getFormattedEndTime().toLowerCase().contains(lowerKeyword)) {
            return true;
        }

        if (record.getParameters() != null) {
            for (BuildParameterRecord.ParameterEntry param : record.getParameters()) {
                if (param.getName() != null && param.getName().toLowerCase().contains(lowerKeyword)) {
                    return true;
                }
                if (param.getValue() != null && param.getValue().toLowerCase().contains(lowerKeyword)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean filterByParameterName(BuildParameterRecord record, String parameterName) {
        if (parameterName == null || parameterName.trim().isEmpty()) {
            return true;
        }
        String lowerName = parameterName.toLowerCase();
        if (record.getParameters() != null) {
            for (BuildParameterRecord.ParameterEntry param : record.getParameters()) {
                if (param.getName() != null && param.getName().toLowerCase().contains(lowerName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean filterByParameterValue(BuildParameterRecord record, String parameterValue) {
        if (parameterValue == null || parameterValue.trim().isEmpty()) {
            return true;
        }
        String lowerValue = parameterValue.toLowerCase();
        if (record.getParameters() != null) {
            for (BuildParameterRecord.ParameterEntry param : record.getParameters()) {
                if (param.getValue() != null && param.getValue().toLowerCase().contains(lowerValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<String> getDistinctJobNames() {
        List<String> jobNames = new ArrayList<>();

        for (Job<?, ?> job : Jenkins.get().getAllItems(Job.class)) {
            File historyFile = new File(job.getRootDir(), HISTORY_FILE);
            if (historyFile.exists()) {
                jobNames.add(job.getFullName());
            }
        }

        Collections.sort(jobNames);
        return jobNames;
    }

    public List<String> getDistinctResults() {
        List<BuildParameterRecord> allRecords = getAllRecords();
        return allRecords.stream()
                .map(BuildParameterRecord::getResult)
                .filter(r -> r != null && !r.trim().isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public void deleteRecord(String jobName, String buildId) {
        File historyFile = getHistoryFile(jobName);
        if (!historyFile.exists()) {
            return;
        }

        try {
            BufferedReader reader = new BufferedReader(new FileReader(historyFile));
            StringWriter writer = new StringWriter();
            String line;
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 2);
                if (parts.length > 1 && !parts[1].startsWith(buildId + "|")) {
                    writer.write(line + "\n");
                }
            }
            reader.close();
            
            BufferedWriter bw = new BufferedWriter(new FileWriter(historyFile));
            bw.write(writer.toString());
            bw.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to delete record for " + jobName + " #" + buildId, e);
        }
    }

    public void clearRecordsForJob(String jobName) {
        File historyFile = getHistoryFile(jobName);
        if (historyFile != null && historyFile.exists()) {
            historyFile.delete();
        }
    }
}
