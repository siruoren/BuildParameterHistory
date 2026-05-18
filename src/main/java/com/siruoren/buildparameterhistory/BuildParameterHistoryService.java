package com.siruoren.buildparameterhistory;

import com.thoughtworks.xstream.XStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.XmlFile;
import java.io.File;
import java.io.IOException;
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
    private static final String STORAGE_DIR = "build-parameter-history";

    private static BuildParameterHistoryService instance;

    private BuildParameterHistoryService() {
    }

    public static synchronized BuildParameterHistoryService getInstance() {
        if (instance == null) {
            instance = new BuildParameterHistoryService();
        }
        return instance;
    }

    private File getStorageRoot() {
        return new File(Jenkins.get().getRootDir(), STORAGE_DIR);
    }

    private File getJobStorageDir(String jobName) {
        String safeName = jobName.replace("/", "_").replace("\\", "_").replace(" ", "_");
        return new File(getStorageRoot(), safeName);
    }

    private XmlFile getRecordFile(String jobName, String buildId) {
        File dir = getJobStorageDir(jobName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new XmlFile(getXStream(), new File(dir, buildId + ".xml"));
    }

    private XStream getXStream() {
        XStream xs = new XStream();
        xs.alias("buildParameterRecord", BuildParameterRecord.class);
        xs.alias("parameterEntry", BuildParameterRecord.ParameterEntry.class);
        xs.allowTypes(new Class[]{BuildParameterRecord.class, BuildParameterRecord.ParameterEntry.class});
        return xs;
    }

    public void saveRecord(@NonNull BuildParameterRecord record) {
        try {
            XmlFile file = getRecordFile(record.getJobName(), record.getBuildId());
            file.write(record);
            fixXmlVersion(file.getFile());
            LOGGER.log(Level.FINE, "Saved build parameter record for {0} #{1}",
                    new Object[]{record.getJobName(), record.getBuildId()});
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save build parameter record for " + record.getJobName(), e);
        }
    }

    private void fixXmlVersion(File file) {
        try {
            java.nio.file.Path path = file.toPath();
            String content = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            if (content.startsWith("<?xml version=\"1.1\"")) {
                content = content.replace("<?xml version=\"1.1\"", "<?xml version=\"1.0\"");
                java.nio.file.Files.write(path, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to fix XML version for " + file.getName(), e);
        }
    }

    public void updateRecord(@NonNull BuildParameterRecord record) {
        saveRecord(record);
    }

    public List<BuildParameterRecord> getRecordsForJob(String jobName) {
        File dir = getJobStorageDir(jobName);
        List<BuildParameterRecord> records = new ArrayList<>();

        if (!dir.exists() || !dir.isDirectory()) {
            return records;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".xml"));
        if (files == null) {
            return records;
        }

        XStream xs = getXStream();
        for (File file : files) {
            try {
                if (!file.exists() || file.length() == 0) {
                    continue;
                }
                
                XmlFile xmlFile = new XmlFile(xs, file);
                Object obj = xmlFile.read();
                if (obj instanceof BuildParameterRecord) {
                    records.add((BuildParameterRecord) obj);
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to read build parameter record from " + file.getName(), e);
            }
        }

        records.sort(Comparator.comparingLong(BuildParameterRecord::getStartTime).reversed());
        return records;
    }

    public List<BuildParameterRecord> getAllRecords() {
        File root = getStorageRoot();
        List<BuildParameterRecord> allRecords = new ArrayList<>();

        if (!root.exists() || !root.isDirectory()) {
            return allRecords;
        }

        File[] jobDirs = root.listFiles(File::isDirectory);
        if (jobDirs == null) {
            return allRecords;
        }

        for (File jobDir : jobDirs) {
            File[] files = jobDir.listFiles((d, name) -> name.endsWith(".xml"));
            if (files == null) {
                continue;
            }

            XStream xs = getXStream();
            for (File file : files) {
                try {
                    if (!file.exists() || file.length() == 0) {
                        continue;
                    }
                    
                    XmlFile xmlFile = new XmlFile(xs, file);
                    Object obj = xmlFile.read();
                    if (obj instanceof BuildParameterRecord) {
                        allRecords.add((BuildParameterRecord) obj);
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to read build parameter record from " + file.getName(), e);
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
        File root = getStorageRoot();
        List<String> jobNames = new ArrayList<>();

        if (!root.exists() || !root.isDirectory()) {
            return jobNames;
        }

        File[] jobDirs = root.listFiles(File::isDirectory);
        if (jobDirs == null) {
            return jobNames;
        }

        for (File jobDir : jobDirs) {
            File[] xmlFiles = jobDir.listFiles((d, name) -> name.endsWith(".xml"));
            if (xmlFiles != null && xmlFiles.length > 0) {
                XStream xs = getXStream();
                for (File xmlFile : xmlFiles) {
                    try {
                        if (!xmlFile.exists() || xmlFile.length() == 0) {
                            continue;
                        }
                        
                        XmlFile xf = new XmlFile(xs, xmlFile);
                        Object obj = xf.read();
                        if (obj instanceof BuildParameterRecord) {
                            String jn = ((BuildParameterRecord) obj).getJobName();
                            if (!jobNames.contains(jn)) {
                                jobNames.add(jn);
                            }
                            break;
                        }
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to read record from " + xmlFile.getName(), e);
                    }
                }
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
        File dir = getJobStorageDir(jobName);
        File file = new File(dir, buildId + ".xml");
        if (file.exists()) {
            file.delete();
        }
    }

    public void clearRecordsForJob(String jobName) {
        File dir = getJobStorageDir(jobName);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            dir.delete();
        }
    }
}
