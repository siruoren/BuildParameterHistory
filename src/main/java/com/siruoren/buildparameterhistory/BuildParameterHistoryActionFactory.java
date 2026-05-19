package com.siruoren.buildparameterhistory;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Project;
import hudson.model.AbstractProject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jenkins.model.TransientActionFactory;

@Extension
public class BuildParameterHistoryActionFactory extends TransientActionFactory<Job> {

    @Override
    public Class<Job> type() {
        return Job.class;
    }

    @Override
    public Collection<? extends Action> createFor(Job target) {
        if (target instanceof AbstractProject || target.isBuildable()) {
            return Collections.singletonList(new BuildParameterHistoryAction(target));
        }
        return Collections.emptyList();
    }
}
