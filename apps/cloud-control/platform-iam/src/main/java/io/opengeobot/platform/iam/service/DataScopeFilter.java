/*
 * Function: Data scope filter — resolves org IDs a user can access based on org tree
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.service;

import io.opengeobot.platform.iam.domain.Org;
import io.opengeobot.platform.iam.domain.UserOrg;
import io.opengeobot.platform.iam.repository.OrgRepository;
import io.opengeobot.platform.iam.repository.UserOrgRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the set of organization IDs that a user can access, based on the
 * organizations the user belongs to and their subtrees in the org tree. The
 * materialized {@code path} column is used for efficient descendant lookups.
 */
@Component
public class DataScopeFilter {

    private static final Logger log = LoggerFactory.getLogger(DataScopeFilter.class);

    private final UserOrgRepository userOrgRepository;
    private final OrgRepository orgRepository;

    public DataScopeFilter(UserOrgRepository userOrgRepository, OrgRepository orgRepository) {
        this.userOrgRepository = userOrgRepository;
        this.orgRepository = orgRepository;
    }

    /**
     * Return the set of org IDs that the given user can access. This includes
     * all organizations the user is directly assigned to and all descendants
     * of those organizations in the org tree.
     *
     * @param userId stable public user identifier
     * @return set of accessible org IDs; empty if the user has no org assignments
     */
    public Set<String> getOrgFilter(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptySet();
        }

        List<UserOrg> userOrgs = userOrgRepository.findByUserId(userId);
        if (userOrgs.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> orgIds = new HashSet<>();
        for (UserOrg userOrg : userOrgs) {
            String orgId = userOrg.getOrgId();
            orgIds.add(orgId);

            Org org = orgRepository.findByOrgId(orgId);
            if (org != null && org.getPath() != null) {
                List<Org> descendants = orgRepository.findDescendantsByPath(org.getPath());
                for (Org descendant : descendants) {
                    orgIds.add(descendant.getOrgId());
                }
            }
        }

        log.debug("Data scope for userId={}: {} org IDs", userId, orgIds.size());
        return orgIds;
    }

    /**
     * Return the set of org IDs as a list, suitable for use in SQL IN clauses.
     *
     * @param userId stable public user identifier
     * @return list of accessible org IDs; empty list if none
     */
    public List<String> getOrgFilterList(String userId) {
        return new ArrayList<>(getOrgFilter(userId));
    }
}
