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
    private static final int MAX_RECORDS = 200;

    private static BuildParameterHistoryService instance;
    private final Object fileLock = new Object();

    private BuildParameterHistoryService() {
    }

    public static synchronized BuildParameterHistoryService getInstance() {
        if (instance == null) {
            instance = new BuildParameterHistoryService();
        }
        return instance;
    }

    public File getHistoryFile(String jobName) {
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
        synchronized (fileLock) {
            try {
                File historyFile = getHistoryFile(record.getJobName());
                if (historyFile == null) {
                    return;
                }

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

                // Auto-remove old records if exceeding max limit
                trimOldRecords(historyFile);

                LOGGER.log(Level.FINE, "Saved build parameter record for {0} #{1}",
                        new Object[]{record.getJobName(), record.getBuildId()});
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to save build parameter record for " + record.getJobName(), e);
            }
        }
    }

    public void updateRecord(@NonNull BuildParameterRecord record) {
        synchronized (fileLock) {
            try {
                File historyFile = getHistoryFile(record.getJobName());
                if (historyFile == null || !historyFile.exists()) {
                    saveRecord(record);
                    return;
                }

                String buildId = record.getBuildId();
                String newLine = formatRecord(record);
                boolean found = false;

                BufferedReader reader = new BufferedReader(new FileReader(historyFile));
                StringWriter writer = new StringWriter();

                String existingLine;
                while ((existingLine = reader.readLine()) != null) {
                    String[] parts = existingLine.split("\\|", 3);
                    if (parts.length >= 2 && parts[1].equals(buildId)) {
                        if (!found) {
                            writer.write(newLine + "\n");
                            found = true;
                        }
                    } else {
                        writer.write(existingLine + "\n");
                    }
                }
                reader.close();

                if (!found) {
                    StringWriter writer2 = new StringWriter();
                    writer2.write(newLine + "\n");
                    writer2.write(writer.toString());
                    writer = writer2;
                }

                BufferedWriter bw = new BufferedWriter(new FileWriter(historyFile));
                bw.write(writer.toString());
                bw.close();

                LOGGER.log(Level.FINE, "Updated build parameter record for {0} #{1}",
                        new Object[]{record.getJobName(), record.getBuildId()});
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to update build parameter record for " + record.getJobName(), e);
            }
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

    public List<BuildParameterRecord> getRecordsForJob(String jobName) {
        List<BuildParameterRecord> records = new ArrayList<>();

        synchronized (fileLock) {
            File historyFile = getHistoryFile(jobName);
            if (historyFile == null || !historyFile.exists()) {
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
        }

        records.sort(Comparator.comparingLong(BuildParameterRecord::getStartTime).reversed());
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
            String parsedJobName = parts[0];
            String buildId = parts[1];
            String buildUrl = parts[2];
            long startTime = Long.parseLong(parts[3]);
            long endTime = Long.parseLong(parts[4]);
            String result = parts[5];

            List<BuildParameterRecord.ParameterEntry> parameters = new ArrayList<>();

            if (parts.length > 6 && parts[6] != null && !parts[6].trim().isEmpty()) {
                String paramsStr = parts[6];
                String[] paramEntries = paramsStr.split("(?=参数\\d+:)");
                for (String entry : paramEntries) {
                    entry = entry.trim();
                    if (entry.isEmpty()) continue;
                    int colonIndex = entry.indexOf(":");
                    if (colonIndex > 0 && colonIndex < entry.length() - 1) {
                        String nameValue = entry.substring(colonIndex + 1);
                        int chineseColonIndex = nameValue.indexOf("：");
                        if (chineseColonIndex > 0) {
                            String name = nameValue.substring(0, chineseColonIndex);
                            String value = nameValue.substring(chineseColonIndex + 1);
                            parameters.add(new BuildParameterRecord.ParameterEntry(name, value));
                        }
                    }
                }
            }

            return new BuildParameterRecord(parsedJobName, buildId, buildUrl, startTime, endTime, result, parameters);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse record: " + line, e);
            return null;
        }
    }

    public List<BuildParameterRecord> getAllRecords() {
        List<BuildParameterRecord> allRecords = new ArrayList<>();

        synchronized (fileLock) {
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
        synchronized (fileLock) {
            File historyFile = getHistoryFile(jobName);
            if (historyFile == null || !historyFile.exists()) {
                return;
            }

            try {
                BufferedReader reader = new BufferedReader(new FileReader(historyFile));
                StringWriter writer = new StringWriter();
                String line;

                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|", 3);
                    if (parts.length < 2 || !parts[1].equals(buildId)) {
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
    }

    public void clearRecordsForJob(String jobName) {
        synchronized (fileLock) {
            File historyFile = getHistoryFile(jobName);
            if (historyFile != null && historyFile.exists()) {
                historyFile.delete();
            }
        }
    }

    private void trimOldRecords(File historyFile) {
        if (historyFile == null || !historyFile.exists()) {
            return;
        }

        try {
            List<String> allLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(historyFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        allLines.add(line);
                    }
                }
            }

            if (allLines.size() <= MAX_RECORDS) {
                return;
            }

            // Keep only the latest MAX_RECORDS (newest records are at the beginning)
            List<String> trimmedLines = allLines.subList(0, MAX_RECORDS);

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(historyFile))) {
                for (String line : trimmedLines) {
                    writer.write(line);
                    writer.newLine();
                }
            }

            LOGGER.log(Level.FINE, "Trimmed {0} old records from history file, kept latest {1}",
                    new Object[]{allLines.size() - MAX_RECORDS, MAX_RECORDS});
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to trim old records from history file", e);
        }
    }
}
