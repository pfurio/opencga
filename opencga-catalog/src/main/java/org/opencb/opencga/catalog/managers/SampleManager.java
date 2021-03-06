/*
 * Copyright 2015-2016 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.catalog.managers;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.catalog.audit.AuditManager;
import org.opencb.opencga.catalog.audit.AuditRecord;
import org.opencb.opencga.catalog.auth.authorization.AuthorizationManager;
import org.opencb.opencga.catalog.config.Configuration;
import org.opencb.opencga.catalog.db.DBAdaptorFactory;
import org.opencb.opencga.catalog.db.api.SampleDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogAuthorizationException;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.io.CatalogIOManagerFactory;
import org.opencb.opencga.catalog.managers.api.ISampleManager;
import org.opencb.opencga.catalog.managers.api.IUserManager;
import org.opencb.opencga.catalog.models.*;
import org.opencb.opencga.catalog.models.acls.permissions.SampleAclEntry;
import org.opencb.opencga.catalog.models.acls.permissions.StudyAclEntry;
import org.opencb.opencga.catalog.utils.AnnotationManager;
import org.opencb.opencga.catalog.utils.CatalogAnnotationsValidator;
import org.opencb.opencga.catalog.utils.ParamUtils;
import org.opencb.opencga.core.common.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class SampleManager extends AbstractManager implements ISampleManager {

    protected static Logger logger = LoggerFactory.getLogger(SampleManager.class);
    private IUserManager userManager;

    @Deprecated
    public SampleManager(AuthorizationManager authorizationManager, AuditManager auditManager,
                         DBAdaptorFactory catalogDBAdaptorFactory, CatalogIOManagerFactory ioManagerFactory,
                         Properties catalogProperties) {
        super(authorizationManager, auditManager, catalogDBAdaptorFactory, ioManagerFactory, catalogProperties);
    }

    public SampleManager(AuthorizationManager authorizationManager, AuditManager auditManager, CatalogManager catalogManager,
                         DBAdaptorFactory catalogDBAdaptorFactory, CatalogIOManagerFactory ioManagerFactory,
                         Configuration configuration) {
        super(authorizationManager, auditManager, catalogManager, catalogDBAdaptorFactory, ioManagerFactory,
                configuration);
        this.userManager = catalogManager.getUserManager();
    }

    @Override
    public Long getStudyId(long sampleId) throws CatalogException {
        return sampleDBAdaptor.getStudyId(sampleId);
    }

    @Override
    public Long getId(String userId, String sampleStr) throws CatalogException {
        if (StringUtils.isNumeric(sampleStr)) {
            return Long.parseLong(sampleStr);
        }

        ObjectMap parsedSampleStr = parseFeatureId(userId, sampleStr);
        List<Long> studyIds = getStudyIds(parsedSampleStr);
        String sampleName = parsedSampleStr.getString("featureName");

        Query query = new Query()
                .append(SampleDBAdaptor.QueryParams.STUDY_ID.key(), studyIds)
                .append(SampleDBAdaptor.QueryParams.NAME.key(), sampleName)
                .append(SampleDBAdaptor.QueryParams.STATUS_NAME.key(), "!=" + Status.DELETED);
        QueryOptions qOptions = new QueryOptions(QueryOptions.INCLUDE, "projects.studies.samples.id");
        QueryResult<Sample> queryResult = sampleDBAdaptor.get(query, qOptions);
        if (queryResult.getNumResults() > 1) {
            throw new CatalogException("Error: More than one sample id found based on " + sampleName);
        } else if (queryResult.getNumResults() == 0) {
            return -1L;
        } else {
            return queryResult.first().getId();
        }
    }

    @Deprecated
    @Override
    public Long getId(String id) throws CatalogException {
        if (StringUtils.isNumeric(id)) {
            return Long.parseLong(id);
        }

        Query query = new Query(SampleDBAdaptor.QueryParams.NAME.key(), id);
        QueryOptions options = new QueryOptions(QueryOptions.INCLUDE, SampleDBAdaptor.QueryParams.ID.key());

        QueryResult<Sample> sampleQueryResult = sampleDBAdaptor.get(query, options);
        if (sampleQueryResult.getNumResults() == 1) {
            return sampleQueryResult.first().getId();
        } else {
            return -1L;
        }
    }

    @Override
    @Deprecated
    public QueryResult<AnnotationSet> annotate(long sampleId, String annotationSetName, long variableSetId, Map<String, Object> annotations,
                                               Map<String, Object> attributes, boolean checkAnnotationSet,
                                               String sessionId) throws CatalogException {
        ParamUtils.checkParameter(annotationSetName, "annotationSetName");
        ParamUtils.checkObj(annotations, "annotations");
        attributes = ParamUtils.defaultObject(attributes, HashMap<String, Object>::new);

        String userId = userManager.getId(sessionId);
        authorizationManager.checkSamplePermission(sampleId, userId, SampleAclEntry.SamplePermissions.CREATE_ANNOTATIONS);

        VariableSet variableSet = studyDBAdaptor.getVariableSet(variableSetId, null).first();

        AnnotationSet annotationSet = new AnnotationSet(annotationSetName, variableSetId, new HashSet<>(), TimeUtils.getTime(), attributes);

        for (Map.Entry<String, Object> entry : annotations.entrySet()) {
            annotationSet.getAnnotations().add(new Annotation(entry.getKey(), entry.getValue()));
        }
        QueryResult<Sample> sampleQueryResult = sampleDBAdaptor.get(sampleId,
                new QueryOptions("include", Collections.singletonList("projects.studies.samples.annotationSets")));

        List<AnnotationSet> annotationSets = sampleQueryResult.first().getAnnotationSets();
        if (checkAnnotationSet) {
            CatalogAnnotationsValidator.checkAnnotationSet(variableSet, annotationSet, annotationSets);
        }

        QueryResult<AnnotationSet> queryResult = sampleDBAdaptor.annotate(sampleId, annotationSet, false);
        auditManager.recordUpdate(AuditRecord.Resource.sample, sampleId, userId,
                new ObjectMap("annotationSets", queryResult.first()), "annotate", null);
        return queryResult;
    }

    @Override
    public QueryResult<AnnotationSet> updateAnnotation(long sampleId, String annotationSetName, Map<String, Object> newAnnotations, String
            sessionId)
            throws CatalogException {

        ParamUtils.checkParameter(annotationSetName, "annotationSetName");
        ParamUtils.checkObj(newAnnotations, "newAnnotations");

        String userId = userManager.getId(sessionId);
        authorizationManager.checkSamplePermission(sampleId, userId, SampleAclEntry.SamplePermissions.UPDATE_ANNOTATIONS);

        // Get sample
        QueryOptions queryOptions = new QueryOptions("include", Collections.singletonList("projects.studies.samples.annotationSets"));
        Sample sample = sampleDBAdaptor.get(sampleId, queryOptions).first();

        List<AnnotationSet> annotationSets = sample.getAnnotationSets();

        // Get annotation set
        AnnotationSet annotationSet = null;
        for (AnnotationSet annotationSetAux : sample.getAnnotationSets()) {
            if (annotationSetAux.getName().equals(annotationSetName)) {
                annotationSet = annotationSetAux;
                sample.getAnnotationSets().remove(annotationSet);
                break;
            }
        }

        if (annotationSet == null) {
            throw CatalogDBException.idNotFound("AnnotationSet", annotationSetName);
        }

        // Get variable set
        VariableSet variableSet = studyDBAdaptor.getVariableSet(annotationSet.getVariableSetId(), null).first();

        // Update and validate annotations
        CatalogAnnotationsValidator.mergeNewAnnotations(annotationSet, newAnnotations);
        CatalogAnnotationsValidator.checkAnnotationSet(variableSet, annotationSet, annotationSets);

        // Commit changes
        QueryResult<AnnotationSet> queryResult = sampleDBAdaptor.annotate(sampleId, annotationSet, true);

        AnnotationSet annotationSetUpdate = new AnnotationSet(annotationSet.getName(), annotationSet.getVariableSetId(),
                newAnnotations.entrySet().stream()
                        .map(entry -> new Annotation(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toSet()), annotationSet.getCreationDate(), null);
        auditManager.recordUpdate(AuditRecord.Resource.sample, sampleId, userId, new ObjectMap("annotationSets",
                Collections.singletonList(annotationSetUpdate)), "update annotation", null);
        return queryResult;
    }

    @Override
    @Deprecated
    public QueryResult<AnnotationSet> deleteAnnotation(long sampleId, String annotationId, String sessionId) throws CatalogException {

        String userId = userManager.getId(sessionId);

        authorizationManager.checkSamplePermission(sampleId, userId, SampleAclEntry.SamplePermissions.DELETE_ANNOTATIONS);

        QueryResult<AnnotationSet> queryResult = sampleDBAdaptor.deleteAnnotation(sampleId, annotationId);
        auditManager.recordUpdate(AuditRecord.Resource.sample, sampleId, userId, new ObjectMap("annotationSets", queryResult.first()),
                "deleteAnnotation", null);
        return queryResult;
    }

    @Override
    public QueryResult<Annotation> load(File file) throws CatalogException {
        throw new UnsupportedOperationException();
    }

    @Override
    public QueryResult<Sample> create(String studyStr, String name, String source, String description,
                                      Map<String, Object> attributes, QueryOptions options, String sessionId)
            throws CatalogException {
        ParamUtils.checkParameter(name, "name");
        source = ParamUtils.defaultString(source, "");
        description = ParamUtils.defaultString(description, "");
        attributes = ParamUtils.defaultObject(attributes, Collections.<String, Object>emptyMap());

        String userId = userManager.getId(sessionId);
        long studyId = catalogManager.getStudyManager().getId(userId, studyStr);
        authorizationManager.checkStudyPermission(studyId, userId, StudyAclEntry.StudyPermissions.CREATE_SAMPLES);

        Sample sample = new Sample(-1, name, source, new Individual(), description, Collections.emptyList(), Collections.emptyList(),
                attributes);

        options = ParamUtils.defaultObject(options, QueryOptions::new);
        QueryResult<Sample> queryResult = sampleDBAdaptor.insert(sample, studyId, options);
//        auditManager.recordCreation(AuditRecord.Resource.sample, queryResult.first().getId(), userId, queryResult.first(), null, null);
        auditManager.recordAction(AuditRecord.Resource.sample, AuditRecord.Action.create, AuditRecord.Magnitude.low,
                queryResult.first().getId(), userId, null, queryResult.first(), null, null);
        return queryResult;
    }

    @Override
    public MyResourceId getId(String sampleStr, @Nullable String studyStr, String sessionId) throws CatalogException {
        if (StringUtils.isEmpty(sampleStr)) {
            throw new CatalogException("Missing sample parameter");
        }

        String userId;
        long studyId;
        long sampleId;

        if (StringUtils.isNumeric(sampleStr)) {
            sampleId = Long.parseLong(sampleStr);
            sampleDBAdaptor.exists(sampleId);
            studyId = sampleDBAdaptor.getStudyId(sampleId);
            userId = userManager.getId(sessionId);
        } else {
            if (sampleStr.contains(",")) {
                throw new CatalogException("More than one sample found");
            }

            userId = userManager.getId(sessionId);
            studyId = catalogManager.getStudyManager().getId(userId, studyStr);

            Query query = new Query()
                    .append(SampleDBAdaptor.QueryParams.STUDY_ID.key(), studyId)
                    .append(SampleDBAdaptor.QueryParams.NAME.key(), sampleStr);
            QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, SampleDBAdaptor.QueryParams.ID.key());
            QueryResult<Sample> sampleQueryResult = sampleDBAdaptor.get(query, queryOptions);
            if (sampleQueryResult.getNumResults() == 1) {
                sampleId = sampleQueryResult.first().getId();
            } else {
                if (sampleQueryResult.getNumResults() == 0) {
                    throw new CatalogException("Sample " + sampleStr + " not found in study " + studyStr);
                } else {
                    throw new CatalogException("More than one sample found under " + sampleStr + " in study " + studyStr);
                }
            }
        }

        return new MyResourceId(userId, studyId, sampleId);
    }

    @Override
    public MyResourceIds getIds(String sampleStr, @Nullable String studyStr, String sessionId) throws CatalogException {
        if (StringUtils.isEmpty(sampleStr)) {
            throw new CatalogException("Missing sample parameter");
        }

        String userId;
        long studyId;
        List<Long> sampleIds;

        if (StringUtils.isNumeric(sampleStr)) {
            sampleIds = Arrays.asList(Long.parseLong(sampleStr));
            sampleDBAdaptor.exists(sampleIds.get(0));
            studyId = sampleDBAdaptor.getStudyId(sampleIds.get(0));
            userId = userManager.getId(sessionId);
        } else {
            userId = userManager.getId(sessionId);
            studyId = catalogManager.getStudyManager().getId(userId, studyStr);

            List<String> sampleSplit = Arrays.asList(sampleStr.split(","));
            Query query = new Query()
                    .append(SampleDBAdaptor.QueryParams.STUDY_ID.key(), studyId)
                    .append(SampleDBAdaptor.QueryParams.NAME.key(), sampleSplit);
            QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, SampleDBAdaptor.QueryParams.ID.key());
            QueryResult<Sample> sampleQueryResult = sampleDBAdaptor.get(query, queryOptions);
            if (sampleQueryResult.getNumResults() == sampleSplit.size()) {
                sampleIds = sampleQueryResult.getResult().stream().map(sample -> sample.getId()).collect(Collectors.toList());
            } else {
                throw new CatalogException("Found only " + sampleQueryResult.getNumResults() + " out of the " + sampleSplit.size()
                        + " samples looked for in study " + studyStr);
            }
        }

        return new MyResourceIds(userId, studyId, sampleIds);
    }

    @Override
    public QueryResult<Sample> get(Long sampleId, QueryOptions options, String sessionId) throws CatalogException {

        String userId = userManager.getId(sessionId);
        authorizationManager.checkSamplePermission(sampleId, userId, SampleAclEntry.SamplePermissions.VIEW);
        QueryResult<Sample> sampleQueryResult = sampleDBAdaptor.get(sampleId, options);
        authorizationManager.filterSamples(userId, getStudyId(sampleId), sampleQueryResult.getResult());
        sampleQueryResult.setNumResults(sampleQueryResult.getResult().size());
        return sampleQueryResult;
    }

    @Override
    public QueryResult<Sample> get(long studyId, Query query, QueryOptions options, String sessionId) throws CatalogException {
        query = ParamUtils.defaultObject(query, Query::new);
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        String userId = userManager.getId(sessionId);

        if (!authorizationManager.memberHasPermissionsInStudy(studyId, userId)) {
            throw CatalogAuthorizationException.deny(userId, "view", "samples", studyId, null);
        }

        query.append(SampleDBAdaptor.QueryParams.STUDY_ID.key(), studyId);
        QueryResult<Sample> queryResult = sampleDBAdaptor.get(query, options);
        authorizationManager.filterSamples(userId, studyId, queryResult.getResult());
        queryResult.setNumResults(queryResult.getResult().size());

        return queryResult;
    }

    @Override
    public QueryResult<Sample> search(String studyStr, Query query, QueryOptions options, String sessionId) throws CatalogException {
        String userId = userManager.getId(sessionId);
        List<Long> studyIds = catalogManager.getStudyManager().getIds(userId, studyStr);

        // Check any permission in studies
        for (Long studyId : studyIds) {
            authorizationManager.memberHasPermissionsInStudy(studyId, userId);
        }

        // The individuals introduced could be either ids or names. As so, we should use the smart resolutor to do this.
        // FIXME: Although the search method is multi-study, we can only use the smart resolutor for one study at the moment.
        if (StringUtils.isNotEmpty(query.getString(SampleDBAdaptor.QueryParams.INDIVIDUAL_ID.key()))
                && studyIds.size() == 1) {
            MyResourceIds resourceIds = catalogManager.getIndividualManager().getIds(
                    query.getString(SampleDBAdaptor.QueryParams.INDIVIDUAL_ID.key()), Long.toString(studyIds.get(0)), sessionId);
            query.put(SampleDBAdaptor.QueryParams.INDIVIDUAL_ID.key(), resourceIds.getResourceIds());
        }

        QueryResult<Sample> queryResult = null;
        for (Long studyId : studyIds) {
            query.append(SampleDBAdaptor.QueryParams.STUDY_ID.key(), studyId);
            QueryResult<Sample> queryResultAux = sampleDBAdaptor.get(query, options);
            authorizationManager.filterSamples(userId, studyId, queryResultAux.getResult());

            if (queryResult == null) {
                queryResult = queryResultAux;
            } else {
                queryResult.getResult().addAll(queryResultAux.getResult());
                queryResult.setNumTotalResults(queryResult.getNumTotalResults() + queryResultAux.getNumTotalResults());
                queryResult.setDbTime(queryResult.getDbTime() + queryResultAux.getDbTime());
            }
        }
        queryResult.setNumResults(queryResult.getResult().size());

        return queryResult;
    }

    @Override
    public List<QueryResult<Sample>> delete(String sampleIdStr, @Nullable String studyStr, QueryOptions options, String sessionId)
            throws CatalogException, IOException {
        ParamUtils.checkParameter(sampleIdStr, "id");
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        MyResourceIds resourceId = getIds(sampleIdStr, studyStr, sessionId);

        List<QueryResult<Sample>> queryResultList = new ArrayList<>(resourceId.getResourceIds().size());
        for (Long sampleId : resourceId.getResourceIds()) {
            QueryResult<Sample> queryResult = null;
            try {
                authorizationManager.checkSamplePermission(sampleId, resourceId.getUser(), SampleAclEntry.SamplePermissions.DELETE);
                queryResult = sampleDBAdaptor.delete(sampleId, options);
                auditManager.recordDeletion(AuditRecord.Resource.sample, sampleId, resourceId.getUser(), queryResult.first(), null, null);
            } catch (CatalogAuthorizationException e) {
                auditManager.recordAction(AuditRecord.Resource.sample, AuditRecord.Action.delete, AuditRecord.Magnitude.high, sampleId,
                        resourceId.getUser(), null, null, e.getMessage(), null);
                queryResult = new QueryResult<>("Delete sample " + sampleId);
                queryResult.setErrorMsg(e.getMessage());
            } catch (CatalogException e) {
                e.printStackTrace();
                queryResult = new QueryResult<>("Delete sample " + sampleId);
                queryResult.setErrorMsg(e.getMessage());
            } finally {
                queryResultList.add(queryResult);
            }
        }

        return queryResultList;
    }

    @Override
    public List<QueryResult<Sample>> delete(Query query, QueryOptions options, String sessionId) throws CatalogException, IOException {
        QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, SampleDBAdaptor.QueryParams.ID.key());
        QueryResult<Sample> sampleQueryResult = sampleDBAdaptor.get(query, queryOptions);
        List<Long> sampleIds = sampleQueryResult.getResult().stream().map(Sample::getId).collect(Collectors.toList());
        String sampleIdStr = StringUtils.join(sampleIds, ",");
        return delete(sampleIdStr, null, options, sessionId);
    }

    @Override
    public List<QueryResult<Sample>> restore(String sampleIdStr, QueryOptions options, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(sampleIdStr, "id");
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        String userId = userManager.getId(sessionId);
        List<Long> sampleIds = getIds(userId, sampleIdStr);

        List<QueryResult<Sample>> queryResultList = new ArrayList<>(sampleIds.size());
        for (Long sampleId : sampleIds) {
            QueryResult<Sample> queryResult = null;
            try {
                authorizationManager.checkSamplePermission(sampleId, userId, SampleAclEntry.SamplePermissions.DELETE);
                queryResult = sampleDBAdaptor.restore(sampleId, options);
                auditManager.recordAction(AuditRecord.Resource.sample, AuditRecord.Action.restore, AuditRecord.Magnitude.medium, sampleId,
                        userId, Status.DELETED, Status.READY, "Sample restore", new ObjectMap());
            } catch (CatalogAuthorizationException e) {
                auditManager.recordAction(AuditRecord.Resource.sample, AuditRecord.Action.restore, AuditRecord.Magnitude.high, sampleId,
                        userId, null, null, e.getMessage(), null);
                queryResult = new QueryResult<>("Restore sample " + sampleId);
                queryResult.setErrorMsg(e.getMessage());
            } catch (CatalogException e) {
                e.printStackTrace();
                queryResult = new QueryResult<>("Restore sample " + sampleId);
                queryResult.setErrorMsg(e.getMessage());
            } finally {
                queryResultList.add(queryResult);
            }
        }

        return queryResultList;
    }

    @Override
    public List<QueryResult<Sample>> restore(Query query, QueryOptions options, String sessionId) throws CatalogException {
        QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, SampleDBAdaptor.QueryParams.ID.key());
        QueryResult<Sample> sampleQueryResult = sampleDBAdaptor.get(query, queryOptions);
        List<Long> sampleIds = sampleQueryResult.getResult().stream().map(Sample::getId).collect(Collectors.toList());
        String sampleIdStr = StringUtils.join(sampleIds, ",");
        return restore(sampleIdStr, options, sessionId);
    }

    @Override
    public void setStatus(String id, String status, String message, String sessionId) throws CatalogException {
        throw new NotImplementedException("Project: Operation not yet supported");
    }

    @Override
    public QueryResult<SampleAclEntry> getAcls(String sampleStr, List<String> members, String sessionId) throws CatalogException {
        long startTime = System.currentTimeMillis();
        String userId = userManager.getId(sessionId);
        Long sampleId = getId(userId, sampleStr);
        authorizationManager.checkSamplePermission(sampleId, userId, SampleAclEntry.SamplePermissions.SHARE);
        Long studyId = getStudyId(sampleId);

        // Split and obtain the set of members (users + groups), users and groups
        Set<String> memberSet = new HashSet<>();
        Set<String> userIds = new HashSet<>();
        Set<String> groupIds = new HashSet<>();
        for (String member: members) {
            memberSet.add(member);
            if (!member.startsWith("@")) {
                userIds.add(member);
            } else {
                groupIds.add(member);
            }
        }


        // Obtain the groups the user might belong to in order to be able to get the permissions properly
        // (the permissions might be given to the group instead of the user)
        // Map of group -> users
        Map<String, List<String>> groupUsers = new HashMap<>();

        if (userIds.size() > 0) {
            List<String> tmpUserIds = userIds.stream().collect(Collectors.toList());
            QueryResult<Group> groups = studyDBAdaptor.getGroup(studyId, null, tmpUserIds);
            // We add the groups where the users might belong to to the memberSet
            if (groups.getNumResults() > 0) {
                for (Group group : groups.getResult()) {
                    for (String tmpUserId : group.getUserIds()) {
                        if (userIds.contains(tmpUserId)) {
                            memberSet.add(group.getName());

                            if (!groupUsers.containsKey(group.getName())) {
                                groupUsers.put(group.getName(), new ArrayList<>());
                            }
                            groupUsers.get(group.getName()).add(tmpUserId);
                        }
                    }
                }
            }
        }
        List<String> memberList = memberSet.stream().collect(Collectors.toList());
        QueryResult<SampleAclEntry> sampleAclQueryResult = sampleDBAdaptor.getAcl(sampleId, memberList);

        if (members.size() == 0) {
            return sampleAclQueryResult;
        }

        // For the cases where the permissions were given at group level, we obtain the user and return it as if they were given to the user
        // instead of the group.
        // We loop over the results and recreate one sampleAcl per member
        Map<String, SampleAclEntry> sampleAclHashMap = new HashMap<>();
        for (SampleAclEntry sampleAcl : sampleAclQueryResult.getResult()) {
            if (memberList.contains(sampleAcl.getMember())) {
                if (sampleAcl.getMember().startsWith("@")) {
                    // Check if the user was demanding the group directly or a user belonging to the group
                    if (groupIds.contains(sampleAcl.getMember())) {
                        sampleAclHashMap.put(sampleAcl.getMember(), new SampleAclEntry(sampleAcl.getMember(), sampleAcl.getPermissions()));
                    } else {
                        // Obtain the user(s) belonging to that group whose permissions wanted the userId
                        if (groupUsers.containsKey(sampleAcl.getMember())) {
                            for (String tmpUserId : groupUsers.get(sampleAcl.getMember())) {
                                if (userIds.contains(tmpUserId)) {
                                    sampleAclHashMap.put(tmpUserId, new SampleAclEntry(tmpUserId, sampleAcl.getPermissions()));
                                }
                            }
                        }
                    }
                } else {
                    // Add the user
                    sampleAclHashMap.put(sampleAcl.getMember(), new SampleAclEntry(sampleAcl.getMember(), sampleAcl.getPermissions()));
                }
            }
        }

        // We recreate the output that is in sampleAclHashMap but in the same order the members were queried.
        List<SampleAclEntry> sampleAclList = new ArrayList<>(sampleAclHashMap.size());
        for (String member : members) {
            if (sampleAclHashMap.containsKey(member)) {
                sampleAclList.add(sampleAclHashMap.get(member));
            }
        }

        // Update queryResult info
        sampleAclQueryResult.setId(sampleStr);
        sampleAclQueryResult.setNumResults(sampleAclList.size());
        sampleAclQueryResult.setNumTotalResults(sampleAclList.size());
        sampleAclQueryResult.setDbTime((int) (System.currentTimeMillis() - startTime));
        sampleAclQueryResult.setResult(sampleAclList);

        return sampleAclQueryResult;

    }

    @Override
    public QueryResult<Sample> get(Query query, QueryOptions options, String sessionId) throws CatalogException {
        ParamUtils.checkObj(query, "query");
        QueryResult<Sample> result =
                get(query.getInt(SampleDBAdaptor.QueryParams.STUDY_ID.key(), -1), query, options, sessionId);
//        auditManager.recordRead(AuditRecord.Resource.sample, , userId, parameters, null, null);
        return result;
    }

    @Override
    public QueryResult<Sample> update(Long sampleId, ObjectMap parameters, QueryOptions options, String sessionId) throws
            CatalogException {
        ParamUtils.checkObj(parameters, "parameters");
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        String userId = userManager.getId(sessionId);

        authorizationManager.checkSamplePermission(sampleId, userId, SampleAclEntry.SamplePermissions.UPDATE);

        for (Map.Entry<String, Object> param : parameters.entrySet()) {
            SampleDBAdaptor.QueryParams queryParam = SampleDBAdaptor.QueryParams.getParam(param.getKey());
            switch (queryParam) {
                case SOURCE:
                case NAME:
                case INDIVIDUAL_ID:
                case DESCRIPTION:
                case ATTRIBUTES:
                    break;
                default:
                    throw new CatalogException("Cannot update " + queryParam);
            }
        }

        // TODO: Change this for ObjectMap
        options.putAll(parameters);
        QueryResult<Sample> queryResult = sampleDBAdaptor.update(sampleId, options);
        auditManager.recordUpdate(AuditRecord.Resource.sample, sampleId, userId, parameters, null, null);
        return queryResult;

    }

    @Override
    public QueryResult rank(long studyId, Query query, String field, int numResults, boolean asc, String sessionId)
            throws CatalogException {
        query = ParamUtils.defaultObject(query, Query::new);
        ParamUtils.checkObj(field, "field");
        ParamUtils.checkObj(studyId, "studyId");
        ParamUtils.checkObj(sessionId, "sessionId");

        String userId = userManager.getId(sessionId);
        authorizationManager.checkStudyPermission(studyId, userId, StudyAclEntry.StudyPermissions.VIEW_SAMPLES);

        // TODO: In next release, we will have to check the count parameter from the queryOptions object.
        boolean count = true;
        //query.append(CatalogSampleDBAdaptor.QueryParams.STUDY_ID.key(), studyId);
        QueryResult queryResult = null;
        if (count) {
            // We do not need to check for permissions when we show the count of files
            queryResult = sampleDBAdaptor.rank(query, field, numResults, asc);
        }

        return ParamUtils.defaultObject(queryResult, QueryResult::new);
    }

    @Override
    public QueryResult groupBy(@Nullable String studyStr, Query query, List<String> fields, QueryOptions options, String sessionId)
            throws CatalogException {
        query = ParamUtils.defaultObject(query, Query::new);
        options = ParamUtils.defaultObject(options, QueryOptions::new);
        if (fields == null || fields.size() == 0) {
            throw new CatalogException("Empty fields parameter.");
        }

        String userId = userManager.getId(sessionId);
        long studyId = catalogManager.getStudyManager().getId(userId, studyStr);
        authorizationManager.checkStudyPermission(studyId, userId, StudyAclEntry.StudyPermissions.VIEW_SAMPLES);

        // Add study id to the query
        query.put(SampleDBAdaptor.QueryParams.STUDY_ID.key(), studyId);

        // TODO: In next release, we will have to check the count parameter from the queryOptions object.
        boolean count = true;
        QueryResult queryResult = null;
        if (count) {
            // We do not need to check for permissions when we show the count of files
            queryResult = sampleDBAdaptor.groupBy(query, fields, options);
        }

        return ParamUtils.defaultObject(queryResult, QueryResult::new);
    }

    private long commonGetAllAnnotationSets(String id, @Nullable String studyStr, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(id, "id");
        MyResourceId resourceId = catalogManager.getSampleManager().getId(id, studyStr, sessionId);
        authorizationManager.checkSamplePermission(resourceId.getResourceId(), resourceId.getUser(),
                SampleAclEntry.SamplePermissions.VIEW_ANNOTATIONS);
        return resourceId.getResourceId();
    }

    private long commonGetAnnotationSet(String id, @Nullable String studyStr, String annotationSetName, String sessionId)
            throws CatalogException {
        ParamUtils.checkParameter(id, "id");
        ParamUtils.checkAlias(annotationSetName, "annotationSetName");
        MyResourceId resourceId = catalogManager.getSampleManager().getId(id, studyStr, sessionId);
        authorizationManager.checkSamplePermission(resourceId.getResourceId(), resourceId.getUser(),
                SampleAclEntry.SamplePermissions.VIEW_ANNOTATIONS);
        return resourceId.getResourceId();
    }

    @Override
    public QueryResult<AnnotationSet> createAnnotationSet(String id, @Nullable String studyStr, long variableSetId,
                                                          String annotationSetName, Map<String, Object> annotations,
                                                          Map<String, Object> attributes, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(annotationSetName, "annotationSetName");
        ParamUtils.checkObj(annotations, "annotations");
        attributes = ParamUtils.defaultObject(attributes, HashMap<String, Object>::new);

        MyResourceId resourceId = catalogManager.getSampleManager().getId(id, studyStr, sessionId);
        authorizationManager.checkSamplePermission(resourceId.getResourceId(), resourceId.getUser(),
                SampleAclEntry.SamplePermissions.CREATE_ANNOTATIONS);

        VariableSet variableSet = studyDBAdaptor.getVariableSet(variableSetId, null).first();

        QueryResult<AnnotationSet> annotationSet = AnnotationManager.createAnnotationSet(resourceId.getResourceId(), variableSet,
                annotationSetName, annotations, attributes, sampleDBAdaptor);

        auditManager.recordUpdate(AuditRecord.Resource.sample, resourceId.getResourceId(), resourceId.getUser(),
                new ObjectMap("annotationSets", annotationSet.first()), "annotate", null);

        return annotationSet;
    }

    @Override
    public QueryResult<AnnotationSet> getAllAnnotationSets(String id, @Nullable String studyStr, String sessionId) throws CatalogException {
        long sampleId = commonGetAllAnnotationSets(id, studyStr, sessionId);
        return sampleDBAdaptor.getAnnotationSet(sampleId, null);
    }

    @Override
    public QueryResult<ObjectMap> getAllAnnotationSetsAsMap(String id, @Nullable String studyStr, String sessionId)
            throws CatalogException {
        long sampleId = commonGetAllAnnotationSets(id, studyStr, sessionId);
        return sampleDBAdaptor.getAnnotationSetAsMap(sampleId, null);
    }

    @Override
    public QueryResult<AnnotationSet> getAnnotationSet(String id, @Nullable String studyStr, String annotationSetName, String sessionId)
            throws CatalogException {
        long sampleId = commonGetAnnotationSet(id, studyStr, annotationSetName, sessionId);
        return sampleDBAdaptor.getAnnotationSet(sampleId, annotationSetName);
    }

    @Override
    public QueryResult<ObjectMap> getAnnotationSetAsMap(String id, @Nullable String studyStr, String annotationSetName, String sessionId)
            throws CatalogException {
        long sampleId = commonGetAnnotationSet(id, studyStr, annotationSetName, sessionId);
        return sampleDBAdaptor.getAnnotationSetAsMap(sampleId, annotationSetName);
    }

    @Override
    public QueryResult<AnnotationSet> updateAnnotationSet(String id, @Nullable String studyStr, String annotationSetName,
                                                          Map<String, Object> newAnnotations, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(id, "id");
        ParamUtils.checkParameter(annotationSetName, "annotationSetName");
        ParamUtils.checkObj(newAnnotations, "newAnnotations");

        MyResourceId resourceId = catalogManager.getSampleManager().getId(id, studyStr, sessionId);
        authorizationManager.checkSamplePermission(resourceId.getResourceId(), resourceId.getUser(),
                SampleAclEntry.SamplePermissions.UPDATE_ANNOTATIONS);

        // Update the annotation
        QueryResult<AnnotationSet> queryResult =
                AnnotationManager.updateAnnotationSet(resourceId.getResourceId(), annotationSetName, newAnnotations, sampleDBAdaptor,
                        studyDBAdaptor);

        if (queryResult == null || queryResult.getNumResults() == 0) {
            throw new CatalogException("There was an error with the update");
        }

        AnnotationSet annotationSet = queryResult.first();

        // Audit the changes
        AnnotationSet annotationSetUpdate = new AnnotationSet(annotationSet.getName(), annotationSet.getVariableSetId(),
                newAnnotations.entrySet().stream()
                        .map(entry -> new Annotation(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toSet()), annotationSet.getCreationDate(), null);
        auditManager.recordUpdate(AuditRecord.Resource.sample, resourceId.getResourceId(), resourceId.getUser(),
                new ObjectMap("annotationSets", Collections.singletonList(annotationSetUpdate)), "update annotation", null);

        return queryResult;
    }

    @Override
    public QueryResult<AnnotationSet> deleteAnnotationSet(String id, @Nullable String studyStr, String annotationSetName, String
            sessionId) throws CatalogException {
        ParamUtils.checkParameter(id, "id");
        ParamUtils.checkParameter(annotationSetName, "annotationSetName");

        MyResourceId resourceId = catalogManager.getSampleManager().getId(id, studyStr, sessionId);
        authorizationManager.checkSamplePermission(resourceId.getResourceId(), resourceId.getUser(),
                SampleAclEntry.SamplePermissions.DELETE_ANNOTATIONS);

        QueryResult<AnnotationSet> annotationSet = sampleDBAdaptor.getAnnotationSet(resourceId.getResourceId(), annotationSetName);
        if (annotationSet == null || annotationSet.getNumResults() == 0) {
            throw new CatalogException("Could not delete annotation set. The annotation set with name " + annotationSetName + " could not "
                    + "be found in the database.");
        }

        sampleDBAdaptor.deleteAnnotationSet(resourceId.getResourceId(), annotationSetName);

        auditManager.recordDeletion(AuditRecord.Resource.sample, resourceId.getResourceId(), resourceId.getUser(),
                new ObjectMap("annotationSets", Collections.singletonList(annotationSet.first())), "delete annotation", null);

        return annotationSet;
    }

    @Override
    public QueryResult<ObjectMap> searchAnnotationSetAsMap(String id, @Nullable String studyStr, long variableSetId,
                                                           @Nullable String annotation, String sessionId) throws CatalogException {
        QueryResult<Sample> sampleQueryResult = commonSearchAnnotationSet(id, studyStr, variableSetId, annotation, sessionId);
        List<ObjectMap> annotationSets;

        if (sampleQueryResult == null || sampleQueryResult.getNumResults() == 0) {
            logger.debug("No samples found");
            annotationSets = Collections.emptyList();
        } else {
            logger.debug("Found {} sample with {} annotationSets", sampleQueryResult.getNumResults(),
                    sampleQueryResult.first().getAnnotationSets().size());
            annotationSets = sampleQueryResult.first().getAnnotationSetAsMap();
        }

        return new QueryResult<>("Search annotation sets", sampleQueryResult.getDbTime(), annotationSets.size(), annotationSets.size(),
                sampleQueryResult.getWarningMsg(), sampleQueryResult.getErrorMsg(), annotationSets);
    }

    @Override
    public QueryResult<AnnotationSet> searchAnnotationSet(String id, @Nullable String studyStr, long variableSetId,
                                                          @Nullable String annotation, String sessionId) throws CatalogException {
        QueryResult<Sample> sampleQueryResult = commonSearchAnnotationSet(id, null, variableSetId, annotation, sessionId);
        List<AnnotationSet> annotationSets;

        if (sampleQueryResult == null || sampleQueryResult.getNumResults() == 0) {
            logger.debug("No samples found");
            annotationSets = Collections.emptyList();
        } else {
            logger.debug("Found {} sample with {} annotationSets", sampleQueryResult.getNumResults(),
                    sampleQueryResult.first().getAnnotationSets().size());
            annotationSets = sampleQueryResult.first().getAnnotationSets();
        }

        return new QueryResult<>("Search annotation sets", sampleQueryResult.getDbTime(), annotationSets.size(), annotationSets.size(),
                sampleQueryResult.getWarningMsg(), sampleQueryResult.getErrorMsg(), annotationSets);
    }

    private QueryResult<Sample> commonSearchAnnotationSet(String id, @Nullable String studyStr, long variableSetId,
                                                          @Nullable String annotation, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(id, "id");

        AbstractManager.MyResourceId resourceId = catalogManager.getSampleManager().getId(id, studyStr, sessionId);
        authorizationManager.checkSamplePermission(resourceId.getResourceId(), resourceId.getUser(),
                SampleAclEntry.SamplePermissions.VIEW_ANNOTATIONS);

        Query query = new Query(SampleDBAdaptor.QueryParams.ID.key(), id);

        if (variableSetId > 0) {
            query.append(SampleDBAdaptor.QueryParams.VARIABLE_SET_ID.key(), variableSetId);
        }
        if (annotation != null) {
            query.append(SampleDBAdaptor.QueryParams.ANNOTATION.key(), annotation);
        }

        QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, SampleDBAdaptor.QueryParams.ANNOTATION_SETS.key());
        QueryResult<Sample> sampleQueryResult = sampleDBAdaptor.get(query, queryOptions);

        logger.debug("Query: {}, \t QueryOptions: {}", query.safeToString(), queryOptions.safeToString());
        return sampleQueryResult;
    }
}
