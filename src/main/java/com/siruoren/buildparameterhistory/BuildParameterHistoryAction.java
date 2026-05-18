package com.siruoren.buildparameterhistory;

import hudson.model.Job;
import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

public class BuildParameterHistoryAction implements hudson.model.Action {

    private final Job<?, ?> job;

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

    public List<BuildParameterRecord> getFilteredRecords(String resultFilter, String searchKeyword,
                                                          String parameterName, String parameterValue) {
        return BuildParameterHistoryService.getInstance()
                .filterRecords(job.getFullName(), resultFilter, searchKeyword, parameterName, parameterValue);
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

        List<BuildParameterRecord> filteredRecords = getFilteredRecords(resultFilter, searchKeyword, parameterName, parameterValue);

        req.setAttribute("filteredRecords", filteredRecords);
        req.setAttribute("resultFilter", resultFilter != null ? resultFilter : "");
        req.setAttribute("searchKeyword", searchKeyword != null ? searchKeyword : "");
        req.setAttribute("parameterName", parameterName != null ? parameterName : "");
        req.setAttribute("parameterValue", parameterValue != null ? parameterValue : "");
        req.setAttribute("hasActiveFilters",
                (resultFilter != null && !resultFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(resultFilter))
                || (searchKeyword != null && !searchKeyword.trim().isEmpty())
                || (parameterName != null && !parameterName.trim().isEmpty())
                || (parameterValue != null && !parameterValue.trim().isEmpty()));

        req.getView(this, "filterResults.jelly").forward(req, rsp);
    }
}
