package com.siruoren.buildparameterhistory;

import hudson.model.Job;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class BuildParameterHistoryAction implements hudson.model.Action {

    private final Job<?, ?> job;
    private static final int PAGE_SIZE = 20;

    public BuildParameterHistoryAction(Job<?, ?> job) {
        this.job = job;
    }

    @Override
    public String getIconFileName() {
        return "symbol-search";
    }

    @Override
    public String getDisplayName() {
        return Messages.BuildParameterHistoryAction_DisplayName();
    }

    @Override
    public String getUrlName() {
        return "buildParameterHistory";
    }

    public Job<?, ?> getJob() {
        return job;
    }

    public String getJobName() {
        return job.getFullName();
    }

    public List<BuildParameterRecord> getRecords() {
        return BuildParameterHistoryService.getInstance().getRecordsForJob(job.getFullName());
    }

    public List<String> getDistinctResults() {
        return BuildParameterHistoryService.getInstance().getDistinctResults();
    }

    @RequirePOST
    public void doClearHistory(StaplerRequest req, StaplerResponse rsp) throws Exception {
        job.checkPermission(Job.DELETE);
        BuildParameterHistoryService.getInstance().clearRecordsForJob(job.getFullName());
        rsp.sendRedirect2(".");
    }

    @RequirePOST
    public void doDeleteRecord(StaplerRequest req, StaplerResponse rsp) throws Exception {
        job.checkPermission(Job.DELETE);
        String buildId = req.getParameter("buildId");
        if (buildId != null && !buildId.trim().isEmpty()) {
            BuildParameterHistoryService.getInstance().deleteRecord(job.getFullName(), buildId);
        }
        rsp.sendRedirect2(".");
    }

    public String getJenkinsRootUrl() {
        return Jenkins.get().getRootUrl();
    }

    public void doFilterResults(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String resultFilter = req.getParameter("resultFilter");
        String searchKeyword = req.getParameter("searchKeyword");
        String parameterName = req.getParameter("parameterName");
        String parameterValue = req.getParameter("parameterValue");

        StringBuilder redirectUrl = new StringBuilder(".");
        boolean hasParams = false;

        if (resultFilter != null && !resultFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(resultFilter)) {
            redirectUrl.append("?resultFilter=").append(resultFilter);
            hasParams = true;
        }
        if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
            redirectUrl.append(hasParams ? "&" : "?").append("searchKeyword=").append(searchKeyword);
            hasParams = true;
        }
        if (parameterName != null && !parameterName.trim().isEmpty()) {
            redirectUrl.append(hasParams ? "&" : "?").append("parameterName=").append(parameterName);
            hasParams = true;
        }
        if (parameterValue != null && !parameterValue.trim().isEmpty()) {
            redirectUrl.append(hasParams ? "&" : "?").append("parameterValue=").append(parameterValue);
        }

        rsp.sendRedirect2(redirectUrl.toString());
    }

    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String resultFilter = req.getParameter("resultFilter");
        String searchKeyword = req.getParameter("searchKeyword");
        String parameterName = req.getParameter("parameterName");
        String parameterValue = req.getParameter("parameterValue");
        String pageParam = req.getParameter("page");

        if (resultFilter == null) resultFilter = "";
        if (searchKeyword == null) searchKeyword = "";
        if (parameterName == null) parameterName = "";
        if (parameterValue == null) parameterValue = "";

        List<BuildParameterRecord> allRecords = getRecords();
        List<BuildParameterRecord> filteredRecords = filterRecords(allRecords, resultFilter, searchKeyword, parameterName, parameterValue);

        int page = 1;
        try {
            page = Integer.parseInt(pageParam);
        } catch (NumberFormatException e) {
            page = 1;
        }

        int totalRecords = filteredRecords.size();
        int totalPages = (int) Math.ceil((double) totalRecords / PAGE_SIZE);
        if (totalPages == 0) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, totalRecords);
        List<BuildParameterRecord> pageRecords = start < totalRecords ? new ArrayList<>(filteredRecords.subList(start, end)) : Collections.emptyList();

        boolean hasFilter = !resultFilter.isEmpty() || !searchKeyword.isEmpty() || !parameterName.isEmpty() || !parameterValue.isEmpty();

        req.setAttribute("bph.resultFilter", resultFilter);
        req.setAttribute("bph.searchKeyword", searchKeyword);
        req.setAttribute("bph.parameterName", parameterName);
        req.setAttribute("bph.parameterValue", parameterValue);
        req.setAttribute("bph.pageRecords", pageRecords);
        req.setAttribute("bph.currentPage", page);
        req.setAttribute("bph.totalPages", totalPages);
        req.setAttribute("bph.totalRecords", totalRecords);
        req.setAttribute("bph.hasFilter", hasFilter);
        req.setAttribute("bph.pageSize", PAGE_SIZE);

        req.getView(this, "index.jelly").forward(req, rsp);
    }

    private List<BuildParameterRecord> filterRecords(List<BuildParameterRecord> records, String resultFilter, String searchKeyword, String parameterName, String parameterValue) {
        List<BuildParameterRecord> filtered = new ArrayList<>(records);

        if (!resultFilter.isEmpty() && !"ALL".equalsIgnoreCase(resultFilter)) {
            filtered.removeIf(r -> !resultFilter.equalsIgnoreCase(r.getResult()));
        }

        if (!searchKeyword.isEmpty()) {
            String keyword = searchKeyword.toLowerCase();
            filtered.removeIf(r -> !matchesSearchKeyword(r, keyword));
        }

        if (!parameterName.isEmpty()) {
            String pName = parameterName.toLowerCase();
            filtered.removeIf(r -> !matchesParameterName(r, pName));
        }

        if (!parameterValue.isEmpty()) {
            String pValue = parameterValue.toLowerCase();
            filtered.removeIf(r -> !matchesParameterValue(r, pValue));
        }

        return filtered;
    }

    private boolean matchesSearchKeyword(BuildParameterRecord record, String keyword) {
        if (record.getJobName().toLowerCase().contains(keyword)) return true;
        if (record.getBuildId().toLowerCase().contains(keyword)) return true;
        if (record.getResult().toLowerCase().contains(keyword)) return true;
        if (record.getFormattedStartTime().toLowerCase().contains(keyword)) return true;
        if (record.getFormattedEndTime().toLowerCase().contains(keyword)) return true;
        if (record.getDuration().toLowerCase().contains(keyword)) return true;
        for (BuildParameterRecord.ParameterEntry param : record.getParameters()) {
            if (param.getName().toLowerCase().contains(keyword)) return true;
            if (param.getValue().toLowerCase().contains(keyword)) return true;
        }
        return false;
    }

    private boolean matchesParameterName(BuildParameterRecord record, String paramName) {
        for (BuildParameterRecord.ParameterEntry param : record.getParameters()) {
            if (param.getName().toLowerCase().contains(paramName)) return true;
        }
        return false;
    }

    private boolean matchesParameterValue(BuildParameterRecord record, String paramValue) {
        for (BuildParameterRecord.ParameterEntry param : record.getParameters()) {
            if (param.getValue().toLowerCase().contains(paramValue)) return true;
        }
        return false;
    }

    public String buildPageUrl(int page, String resultFilter, String searchKeyword, String parameterName, String parameterValue) {
        StringBuilder sb = new StringBuilder("?page=").append(page);
        if (resultFilter != null && !resultFilter.isEmpty() && !"ALL".equalsIgnoreCase(resultFilter)) {
            sb.append("&resultFilter=").append(resultFilter);
        }
        if (searchKeyword != null && !searchKeyword.isEmpty()) {
            sb.append("&searchKeyword=").append(searchKeyword);
        }
        if (parameterName != null && !parameterName.isEmpty()) {
            sb.append("&parameterName=").append(parameterName);
        }
        if (parameterValue != null && !parameterValue.isEmpty()) {
            sb.append("&parameterValue=").append(parameterValue);
        }
        return sb.toString();
    }

    /**
     * Download build parameter history file via API
     * API: /job/{job_name}/buildParameterHistory/downloadHistory
     */
    public void doDownloadHistory(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        job.checkPermission(Job.READ);

        File historyFile = BuildParameterHistoryService.getInstance().getHistoryFile(job.getFullName());
        if (historyFile == null || !historyFile.exists()) {
            rsp.setStatus(404);
            rsp.getWriter().write("No history file found for this job");
            return;
        }

        String fileName = job.getName() + "_param_history.txt";

        rsp.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        rsp.setContentType("text/plain; charset=UTF-8");
        rsp.setStatus(200);

        try (InputStream inputStream = new FileInputStream(historyFile)) {
            IOUtils.copy(inputStream, rsp.getOutputStream());
        }
        rsp.getOutputStream().flush();
    }
}
