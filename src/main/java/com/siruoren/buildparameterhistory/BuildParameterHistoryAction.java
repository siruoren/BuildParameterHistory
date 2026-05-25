package com.siruoren.buildparameterhistory;

import hudson.Util;
import hudson.model.Job;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class BuildParameterHistoryAction implements hudson.model.Action {

    private static final Logger LOGGER = Logger.getLogger(BuildParameterHistoryAction.class.getName());
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

    public Integer getJobMaxRecords() {
        BuildParameterHistoryJobProperty prop = job.getProperty(BuildParameterHistoryJobProperty.class);
        return prop != null ? prop.getMaxRecords() : null;
    }

    public int getEffectiveMaxRecords() {
        BuildParameterHistoryJobProperty prop = job.getProperty(BuildParameterHistoryJobProperty.class);
        if (prop != null && prop.isMaxRecordsSet()) {
            return prop.getEffectiveMaxRecords();
        }
        return BuildParameterHistoryGlobalConfiguration.get().getMaxRecords();
    }

    public boolean isUsingJobMaxRecords() {
        BuildParameterHistoryJobProperty prop = job.getProperty(BuildParameterHistoryJobProperty.class);
        return prop != null && prop.isMaxRecordsSet();
    }

    public int getGlobalMaxRecords() {
        return BuildParameterHistoryGlobalConfiguration.get().getMaxRecords();
    }

    public boolean hasConfigurePermission() {
        return job.hasPermission(BuildParameterHistoryPermissions.CONFIGURE);
    }

    public boolean hasDeletePermission() {
        return job.hasPermission(BuildParameterHistoryPermissions.DELETE_RECORDS);
    }

    public List<BuildParameterRecord> getRecords() {
        return BuildParameterHistoryService.getInstance().getRecordsForJob(job.getFullName());
    }

    public List<String> getDistinctResults() {
        return BuildParameterHistoryService.getInstance().getDistinctResults();
    }

    @RequirePOST
    public void doClearHistory(StaplerRequest req, StaplerResponse rsp) throws Exception {
        job.checkPermission(BuildParameterHistoryPermissions.DELETE_RECORDS);
        BuildParameterHistoryService.getInstance().clearRecordsForJob(job.getFullName());
        rsp.sendRedirect2(".");
    }

    @RequirePOST
    public void doDeleteRecord(StaplerRequest req, StaplerResponse rsp) throws Exception {
        job.checkPermission(BuildParameterHistoryPermissions.DELETE_RECORDS);
        String buildId = req.getParameter("buildId");
        if (buildId != null && !buildId.trim().isEmpty()) {
            BuildParameterHistoryService.getInstance().deleteRecord(job.getFullName(), buildId);
        }
        rsp.sendRedirect2(".");
    }

    @RequirePOST
    public void doDeleteRecords(StaplerRequest req, StaplerResponse rsp) throws Exception {
        job.checkPermission(BuildParameterHistoryPermissions.DELETE_RECORDS);

        String[] buildIds = req.getParameterValues("buildIds");
        if (buildIds == null || buildIds.length == 0) {
            String buildIdsStr = req.getParameter("buildIdsStr");
            if (buildIdsStr != null && !buildIdsStr.trim().isEmpty()) {
                buildIds = buildIdsStr.split(",");
            }
        }

        if (buildIds != null && buildIds.length > 0) {
            List<String> ids = Arrays.stream(buildIds)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            BuildParameterHistoryService.getInstance().deleteRecords(job.getFullName(), ids);
            LOGGER.log(Level.INFO, "Deleted {0} records for job {1}",
                    new Object[]{ids.size(), job.getFullName()});
        }

        rsp.sendRedirect2(".");
    }

    public String getJenkinsRootUrl() {
        return Jenkins.get().getRootUrl();
    }

    @RequirePOST
    public void doFilterResults(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String resultFilter = req.getParameter("resultFilter");
        String searchKeyword = req.getParameter("searchKeyword");
        String parameterName = req.getParameter("parameterName");
        String parameterValue = req.getParameter("parameterValue");

        StringBuilder redirectUrl = new StringBuilder("index");
        boolean hasParams = false;

        if (resultFilter != null && !resultFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(resultFilter)) {
            redirectUrl.append("?resultFilter=").append(Util.encode(resultFilter));
            hasParams = true;
        }
        if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
            redirectUrl.append(hasParams ? "&" : "?").append("searchKeyword=").append(Util.encode(searchKeyword));
            hasParams = true;
        }
        if (parameterName != null && !parameterName.trim().isEmpty()) {
            redirectUrl.append(hasParams ? "&" : "?").append("parameterName=").append(Util.encode(parameterName));
            hasParams = true;
        }
        if (parameterValue != null && !parameterValue.trim().isEmpty()) {
            redirectUrl.append(hasParams ? "&" : "?").append("parameterValue=").append(Util.encode(parameterValue));
        }

        String redirect = redirectUrl.toString();
        if (!isSafeRedirect(redirect)) {
            LOGGER.warning("Blocked potentially unsafe redirect: " + redirect);
            rsp.sendRedirect2(".");
            return;
        }

        rsp.sendRedirect2(redirect);
    }

    private boolean isSafeRedirect(String url) {
        if (url == null) {
            return false;
        }
        if (url.startsWith("index") || url.startsWith("./index") || url.startsWith("?") || url.startsWith("./?")) {
            return true;
        }
        try {
            URI uri = new URI(url);
            return uri.getScheme() == null && uri.getAuthority() == null;
        } catch (URISyntaxException e) {
            return false;
        }
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

        int page = 1;
        try {
            page = Integer.parseInt(pageParam);
        } catch (NumberFormatException e) {
            page = 1;
        }
        if (page < 1) page = 1;

        BuildParameterHistoryService service = BuildParameterHistoryService.getInstance();
        int totalRecords = service.getFilteredRecordCount(job.getFullName(), resultFilter, searchKeyword, parameterName, parameterValue);
        int totalPages = (int) Math.ceil((double) totalRecords / PAGE_SIZE);
        if (totalPages == 0) totalPages = 1;
        if (page > totalPages) page = totalPages;

        List<BuildParameterRecord> pageRecords = service.getFilteredRecordsForJob(
                job.getFullName(), resultFilter, searchKeyword, parameterName, parameterValue, page, PAGE_SIZE);

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
        req.setAttribute("bph.hasDeletePermission", hasDeletePermission());

        req.getView(this, "index.jelly").forward(req, rsp);
    }

    public String buildPageUrl(int page, String resultFilter, String searchKeyword, String parameterName, String parameterValue) {
        StringBuilder sb = new StringBuilder("?page=").append(page);
        if (resultFilter != null && !resultFilter.isEmpty() && !"ALL".equalsIgnoreCase(resultFilter)) {
            sb.append("&resultFilter=").append(Util.encode(resultFilter));
        }
        if (searchKeyword != null && !searchKeyword.isEmpty()) {
            sb.append("&searchKeyword=").append(Util.encode(searchKeyword));
        }
        if (parameterName != null && !parameterName.isEmpty()) {
            sb.append("&parameterName=").append(Util.encode(parameterName));
        }
        if (parameterValue != null && !parameterValue.isEmpty()) {
            sb.append("&parameterValue=").append(Util.encode(parameterValue));
        }
        return sb.toString();
    }

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
