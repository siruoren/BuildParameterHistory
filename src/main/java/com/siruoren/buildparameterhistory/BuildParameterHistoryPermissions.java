package com.siruoren.buildparameterhistory;

import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import jenkins.model.Jenkins;

public class BuildParameterHistoryPermissions {

    public static final PermissionGroup GROUP =
            new PermissionGroup(BuildParameterHistoryPermissions.class, Messages._BuildParameterHistoryPermissions_Group());

    public static final Permission CONFIGURE =
            new Permission(GROUP, "Configure",
                    Messages._BuildParameterHistoryPermissions_Configure_Description(),
                    Jenkins.ADMINISTER, PermissionScope.JENKINS);

    public static final Permission DELETE_RECORDS =
            new Permission(GROUP, "DeleteRecords",
                    Messages._BuildParameterHistoryPermissions_DeleteRecords_Description(),
                    CONFIGURE, PermissionScope.JENKINS);

    private BuildParameterHistoryPermissions() {
    }
}
