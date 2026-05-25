package com.siruoren.buildparameterhistory;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Job;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;

public class BuildParameterHistoryService {

    private static final Logger LOGGER = Logger.getLogger(BuildParameterHistoryService.class.getName());
    private static final String HISTORY_FILE = "param_history";
    private static final int DEFAULT_MAX_RECORDS = 200;
    private static final long CACHE_TTL_MS = 30_000;

    private static BuildParameterHistoryService instance;
    private final Object fileLock = new Object();

    private final Map<String, CachedRecords> recordsCache = new HashMap<>();

    private static class CachedRecords {
        final List<BuildParameterRecord> records;
        final long timestamp;
        final long fileLastModified;

        CachedRecords(List<BuildParameterRecord> records, long fileLastModified) {
            this.records = records;
            this.fileLastModified = fileLastModified;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }

        boolean isFileChanged(File historyFile) {
            if (historyFile == null || !historyFile.exists()) {
                return true;
            }
            return historyFile.lastModified() != fileLastModified;
        }
    }

    private BuildParameterHistoryService() {
    }

    public static synchronized BuildParameterHistoryService getInstance() {
        if (instance == null) {
            instance = new BuildParameterHistoryService();
        }
        return instance;
    }

    public int getMaxRecords() {
        BuildParameterHistoryGlobalConfiguration config = BuildParameterHistoryGlobalConfiguration.get();
        int maxRecords;
        if (config != null && config.getMaxRecords() > 0) {
            maxRecords = config.getMaxRecords();
            LOGGER.log(Level.FINE, "BuildParameterHistoryService getMaxRecords from config={0}", maxRecords);
        } else {
            maxRecords = DEFAULT_MAX_RECORDS;
            LOGGER.log(Level.FINE, "BuildParameterHistoryService getMaxRecords using default={0}", maxRecords);
        }
        return maxRecords;
    }

    public int getMaxRecords(Job<?, ?> job) {
        if (job != null) {
            BuildParameterHistoryJobProperty prop = job.getProperty(BuildParameterHistoryJobProperty.class);
            if (prop != null && prop.isMaxRecordsSet()) {
                int jobMaxRecords = prop.getEffectiveMaxRecords();
                LOGGER.log(Level.FINE, "BuildParameterHistoryService getMaxRecords from job config={0}", jobMaxRecords);
                return jobMaxRecords;
            }
        }
        return getMaxRecords();
    }

    public File getHistoryFile(String jobName) {
        Job<?, ?> job = Jenkins.get().getItemByFullName(jobName, Job.class);
        if (job == null) {
            LOGGER.log(Level.WARNING, "Job not found: " + jobName);
            return null;
        }
        return resolveHistoryFile(job);
    }

    public File resolveHistoryFile(Job<?, ?> job) {
        File jobDir = job.getRootDir();
        if (!jobDir.exists()) {
            jobDir.mkdirs();
        }
        return new File(jobDir, HISTORY_FILE);
    }

    public void saveRecord(@NonNull BuildParameterRecord record) {
        synchronized (fileLock) {
            File historyFile = getHistoryFile(record.getJobName());
            if (historyFile == null) {
                return;
            }
            int maxRecords = getMaxRecords();
            doSaveRecord(historyFile, record, maxRecords);
        }
    }

    public void saveRecord(@NonNull File historyFile, @NonNull BuildParameterRecord record) {
        synchronized (fileLock) {
            doSaveRecord(historyFile, record, getMaxRecords());
        }
    }

    public void saveRecord(@NonNull File historyFile, @NonNull BuildParameterRecord record, int maxRecords) {
        synchronized (fileLock) {
            doSaveRecord(historyFile, record, maxRecords);
        }
    }

    private void doSaveRecord(File historyFile, BuildParameterRecord record, int maxRecords) {
        try {
            String line = formatRecord(record);

            if (historyFile.exists()) {
                StringWriter writer = new StringWriter();
                writer.write(line);
                writer.write("\n");

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(historyFile), StandardCharsets.UTF_8))) {
                    String existingLine;
                    while ((existingLine = reader.readLine()) != null) {
                        writer.write(existingLine);
                        writer.write("\n");
                    }
                }

                writeWithFileLock(historyFile, writer.toString());
            } else {
                writeWithFileLock(historyFile, line + "\n");
            }

            trimOldRecords(historyFile, maxRecords);
            invalidateCache(record.getJobName());

            LOGGER.log(Level.FINE, "Saved build parameter record for {0} #{1}",
                    new Object[]{record.getJobName(), record.getBuildId()});
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save build parameter record for " + record.getJobName(), e);
        }
    }

    public void updateRecord(@NonNull BuildParameterRecord record) {
        synchronized (fileLock) {
            File historyFile = getHistoryFile(record.getJobName());
            if (historyFile == null || !historyFile.exists()) {
                doSaveRecord(historyFile != null ? historyFile : getOrCreateHistoryFile(record.getJobName()), record, getMaxRecords());
                return;
            }
            doUpdateRecord(historyFile, record, getMaxRecords());
        }
    }

    public void updateRecord(@NonNull File historyFile, @NonNull BuildParameterRecord record) {
        synchronized (fileLock) {
            if (!historyFile.exists()) {
                doSaveRecord(historyFile, record, getMaxRecords());
                return;
            }
            doUpdateRecord(historyFile, record, getMaxRecords());
        }
    }

    public void updateRecord(@NonNull File historyFile, @NonNull BuildParameterRecord record, int maxRecords) {
        synchronized (fileLock) {
            if (!historyFile.exists()) {
                doSaveRecord(historyFile, record, maxRecords);
                return;
            }
            doUpdateRecord(historyFile, record, maxRecords);
        }
    }

    private File getOrCreateHistoryFile(String jobName) {
        File f = getHistoryFile(jobName);
        if (f == null) {
            LOGGER.log(Level.WARNING, "Cannot resolve history file for job: " + jobName);
        }
        return f;
    }

    private void doUpdateRecord(File historyFile, BuildParameterRecord record, int maxRecords) {
        try {
            String buildId = record.getBuildId();
            String newLine = formatRecord(record);
            boolean found = false;

            StringWriter writer = new StringWriter();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(historyFile), StandardCharsets.UTF_8))) {
                String existingLine;
                while ((existingLine = reader.readLine()) != null) {
                    String[] parts = existingLine.split("\\|", 3);
                    if (parts.length >= 2 && parts[1].equals(buildId)) {
                        if (!found) {
                            writer.write(newLine);
                            writer.write("\n");
                            found = true;
                        }
                    } else {
                        writer.write(existingLine);
                        writer.write("\n");
                    }
                }
            }

            if (!found) {
                StringWriter writer2 = new StringWriter();
                writer2.write(newLine);
                writer2.write("\n");
                writer2.write(writer.toString());
                writer = writer2;
            }

            writeWithFileLock(historyFile, writer.toString());
            trimOldRecords(historyFile, maxRecords);
            invalidateCache(record.getJobName());

            LOGGER.log(Level.FINE, "Updated build parameter record for {0} #{1}",
                    new Object[]{record.getJobName(), record.getBuildId()});
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to update build parameter record for " + record.getJobName(), e);
        }
    }

    private void writeWithFileLock(File file, String content) throws IOException {
        FileOutputStream fos = null;
        FileChannel channel = null;
        FileLock lock = null;
        BufferedWriter bw = null;
        try {
            fos = new FileOutputStream(file);
            channel = fos.getChannel();
            lock = channel.lock();
            bw = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8));
            bw.write(content);
            bw.flush();
        } finally {
            if (bw != null) {
                try { bw.close(); } catch (IOException ignored) {}
            }
            if (lock != null) {
                try { lock.release(); } catch (IOException ignored) {}
            }
            if (channel != null) {
                try { channel.close(); } catch (IOException ignored) {}
            }
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) {}
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

    private void invalidateCache(String jobName) {
        synchronized (recordsCache) {
            recordsCache.remove(jobName);
        }
    }

    private void invalidateAllCache() {
        synchronized (recordsCache) {
            recordsCache.clear();
        }
    }

    public List<BuildParameterRecord> getRecordsForJob(String jobName) {
        File historyFile = getHistoryFile(jobName);
        return getRecordsForJob(historyFile, jobName);
    }

    public List<BuildParameterRecord> getRecordsForJob(File historyFile, String jobName) {
        if (historyFile == null || !historyFile.exists()) {
            return new ArrayList<>();
        }

        List<BuildParameterRecord> records = readAllRecordsFromFile(historyFile, jobName);
        return records;
    }

    public List<BuildParameterRecord> getRecordsForJob(String jobName, int page, int pageSize) {
        File historyFile = getHistoryFile(jobName);
        if (historyFile == null || !historyFile.exists()) {
            return new ArrayList<>();
        }

        synchronized (recordsCache) {
            CachedRecords cached = recordsCache.get(jobName);
            if (cached != null && !cached.isExpired() && !cached.isFileChanged(historyFile)) {
                int start = (page - 1) * pageSize;
                int end = Math.min(start + pageSize, cached.records.size());
                if (start >= cached.records.size()) {
                    return new ArrayList<>();
                }
                return new ArrayList<>(cached.records.subList(start, end));
            }
        }

        List<BuildParameterRecord> records = readAllRecordsFromFile(historyFile, jobName);

        synchronized (recordsCache) {
            recordsCache.put(jobName, new CachedRecords(records, historyFile.lastModified()));
        }

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, records.size());
        if (start >= records.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(records.subList(start, end));
    }

    public int getRecordCountForJob(String jobName) {
        File historyFile = getHistoryFile(jobName);
        if (historyFile == null || !historyFile.exists()) {
            return 0;
        }

        synchronized (recordsCache) {
            CachedRecords cached = recordsCache.get(jobName);
            if (cached != null && !cached.isExpired() && !cached.isFileChanged(historyFile)) {
                return cached.records.size();
            }
        }

        List<BuildParameterRecord> records = readAllRecordsFromFile(historyFile, jobName);

        synchronized (recordsCache) {
            recordsCache.put(jobName, new CachedRecords(records, historyFile.lastModified()));
        }

        return records.size();
    }

    public int getFilteredRecordCount(String jobName, String resultFilter, String searchKeyword, String parameterName, String parameterValue) {
        List<BuildParameterRecord> records;
        if (jobName != null && !jobName.trim().isEmpty()) {
            records = getRecordsForJob(jobName);
        } else {
            records = getAllRecords();
        }

        return (int) records.stream()
                .filter(r -> filterByResult(r, resultFilter))
                .filter(r -> filterBySearchKeyword(r, searchKeyword))
                .filter(r -> filterByParameterName(r, parameterName))
                .filter(r -> filterByParameterValue(r, parameterValue))
                .count();
    }

    public List<BuildParameterRecord> getFilteredRecordsForJob(String jobName, String resultFilter, String searchKeyword, String parameterName, String parameterValue, int page, int pageSize) {
        List<BuildParameterRecord> records;
        if (jobName != null && !jobName.trim().isEmpty()) {
            records = getRecordsForJob(jobName);
        } else {
            records = getAllRecords();
        }

        List<BuildParameterRecord> filtered = records.stream()
                .filter(r -> filterByResult(r, resultFilter))
                .filter(r -> filterBySearchKeyword(r, searchKeyword))
                .filter(r -> filterByParameterName(r, parameterName))
                .filter(r -> filterByParameterValue(r, parameterValue))
                .collect(Collectors.toList());

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, filtered.size());
        if (start >= filtered.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(filtered.subList(start, end));
    }

    private List<BuildParameterRecord> readAllRecordsFromFile(File historyFile, String jobName) {
        List<BuildParameterRecord> records = new ArrayList<>();

        synchronized (fileLock) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(historyFile), StandardCharsets.UTF_8))) {
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

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(historyFile), StandardCharsets.UTF_8))) {
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
        return getFilteredRecordsForJob(jobName, resultFilter, searchKeyword, parameterName, parameterValue, 1, Integer.MAX_VALUE);
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
                StringWriter writer = new StringWriter();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(historyFile), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("\\|", 3);
                        if (parts.length < 2 || !parts[1].equals(buildId)) {
                            writer.write(line);
                            writer.write("\n");
                        }
                    }
                }

                writeWithFileLock(historyFile, writer.toString());
                invalidateCache(jobName);
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
            invalidateCache(jobName);
        }
    }

    public void deleteRecords(String jobName, List<String> buildIds) {
        if (buildIds == null || buildIds.isEmpty()) {
            return;
        }

        synchronized (fileLock) {
            File historyFile = getHistoryFile(jobName);
            if (historyFile == null || !historyFile.exists()) {
                return;
            }

            try {
                java.util.Set<String> idSet = new java.util.HashSet<>(buildIds);
                StringWriter writer = new StringWriter();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(historyFile), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split("\\|", 3);
                        if (parts.length < 2 || !idSet.contains(parts[1])) {
                            writer.write(line);
                            writer.write("\n");
                        }
                    }
                }

                writeWithFileLock(historyFile, writer.toString());
                invalidateCache(jobName);

                LOGGER.log(Level.FINE, "Deleted {0} records for job {1}",
                        new Object[]{buildIds.size(), jobName});
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to delete records for " + jobName, e);
            }
        }
    }

    private void trimOldRecords(File historyFile, int maxRecords) {
        if (historyFile == null || !historyFile.exists()) {
            return;
        }

        try {
            List<String> allLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(historyFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        allLines.add(line);
                    }
                }
            }

            LOGGER.log(Level.INFO, "trimOldRecords: allLines.size={0}, maxRecords={1}", new Object[]{allLines.size(), maxRecords});
            if (allLines.size() <= maxRecords) {
                return;
            }

            List<String> trimmedLines = allLines.subList(0, maxRecords);

            StringWriter writer = new StringWriter();
            for (String line : trimmedLines) {
                writer.write(line);
                writer.write("\n");
            }

            writeWithFileLock(historyFile, writer.toString());

            LOGGER.log(Level.FINE, "Trimmed {0} old records from history file, kept latest {1}",
                    new Object[]{allLines.size() - maxRecords, maxRecords});
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to trim old records from history file", e);
        }
    }
}
