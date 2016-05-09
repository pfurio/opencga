package org.opencb.opencga.app.cli.analysis;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.opencb.biodata.models.variant.VariantSource;
import org.opencb.commons.datastore.core.Query;
import org.opencb.opencga.analysis.storage.OpenCGATestExternalResource;
import org.opencb.opencga.catalog.CatalogManager;
import org.opencb.opencga.catalog.db.api.CatalogCohortDBAdaptor;
import org.opencb.opencga.catalog.db.api.CatalogJobDBAdaptor;
import org.opencb.opencga.catalog.models.*;
import org.opencb.opencga.storage.core.variant.VariantStorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Collections;

/**
 * Created on 09/05/16
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class AnalysisMainTest {


    public static final String STORAGE_ENGINE = "mongodb";
    @Rule
    public OpenCGATestExternalResource opencga = new OpenCGATestExternalResource();


    private CatalogManager catalogManager;
    private final String userId = "user";
    private final String dbName = "opencga_variants_test";
    private String sessionId;
    private long projectId;
    private long studyId;
    private long outdirId;
    private Logger logger = LoggerFactory.getLogger(AnalysisMainTest.class);
    private File file1;
    private File file2;
    private File file3;
    private File file4;
    private File file5;

    @Before
    public void setUp() throws Exception {
        catalogManager = opencga.getCatalogManager();


        opencga.clearStorageDB(STORAGE_ENGINE, dbName);

        User user = catalogManager.createUser(userId, "User", "user@email.org", "user", "ACME", null, null).first();
        sessionId = catalogManager.login(userId, "user", "localhost").first().getString("sessionId");
        projectId = catalogManager.createProject(userId, "p1", "p1", "Project 1", "ACME", null, sessionId).first().getId();
        studyId = catalogManager.createStudy(projectId, "s1", "s1", Study.Type.CASE_CONTROL, null, null, "Study 1", null,
                null, null, null, Collections.singletonMap(File.Bioformat.VARIANT, new DataStore(STORAGE_ENGINE, dbName)), null,
                Collections.singletonMap(VariantStorageManager.Options.AGGREGATED_TYPE.key(), VariantSource.Aggregation.NONE),
                null, sessionId).first().getId();
        outdirId = catalogManager.createFolder(studyId, Paths.get("data", "index"), false, null, sessionId).first().getId();

        file1 = opencga.createFile(studyId, "1000g_batches/1-500.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz", sessionId);
        file2 = opencga.createFile(studyId, "1000g_batches/501-1000.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz", sessionId);
        file3 = opencga.createFile(studyId, "1000g_batches/1001-1500.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz", sessionId);
        file4 = opencga.createFile(studyId, "1000g_batches/1501-2000.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz", sessionId);
        file5 = opencga.createFile(studyId, "1000g_batches/2001-2504.filtered.10k.chr22.phase3_shapeit2_mvncall_integrated_v5.20130502.genotypes.vcf.gz", sessionId);



    }

    @Test
    public void testIndex() throws Exception {
        AnalysisMain.privateMain(new String[]{"variant", "index", "--session-id", sessionId, "--file-id", "user@p1:s1:" + file1.getPath()});
        Assert.assertEquals(Index.IndexStatus.READY, catalogManager.getFile(file1.getId(), sessionId).first().getIndex().getStatus().getStatus());
        Assert.assertEquals(Cohort.CohortStatus.NONE, catalogManager.getAllCohorts(studyId, new Query(CatalogCohortDBAdaptor.QueryParams.NAME.key(), "ALL"), null, sessionId).first().getStatus().getStatus());
        Job job = catalogManager.getAllJobs(studyId, new Query(CatalogJobDBAdaptor.QueryParams.INPUT.key(), file1.getId()), null, sessionId).first();
        Assert.assertEquals(Job.JobStatus.READY, job.getStatus().getStatus());

        AnalysisMain.privateMain(new String[]{"variant", "index", "--session-id", sessionId, "--file-id", String.valueOf(file2.getId()), "--calculate-stats", "--outdir", String.valueOf(outdirId)});
        Assert.assertEquals(Index.IndexStatus.READY, catalogManager.getFile(file2.getId(), sessionId).first().getIndex().getStatus().getStatus());
        Assert.assertEquals(Cohort.CohortStatus.READY, catalogManager.getAllCohorts(studyId, new Query(CatalogCohortDBAdaptor.QueryParams.NAME.key(), "ALL"), null, sessionId).first().getStatus().getStatus());
        job = catalogManager.getAllJobs(studyId, new Query(CatalogJobDBAdaptor.QueryParams.INPUT.key(), file2.getId()), null, sessionId).first();
        Assert.assertEquals(Job.JobStatus.READY, job.getStatus().getStatus());
        Assert.assertEquals(outdirId, job.getOutDirId());

        AnalysisMain.privateMain(new String[]{"variant", "index", "--session-id", sessionId, "--file-id", String.valueOf(file3.getId())});
        Assert.assertEquals(Index.IndexStatus.READY, catalogManager.getFile(file3.getId(), sessionId).first().getIndex().getStatus().getStatus());
        Assert.assertEquals(Cohort.CohortStatus.INVALID, catalogManager.getAllCohorts(studyId, new Query(CatalogCohortDBAdaptor.QueryParams.NAME.key(), "ALL"), null, sessionId).first().getStatus().getStatus());
        job = catalogManager.getAllJobs(studyId, new Query(CatalogJobDBAdaptor.QueryParams.INPUT.key(), file3.getId()), null, sessionId).first();
        Assert.assertEquals(Job.JobStatus.READY, job.getStatus().getStatus());
        Assert.assertNotEquals(outdirId, job.getOutDirId());

        AnalysisMain.privateMain(new String[]{"variant", "index", "--session-id", sessionId, "--file-id", String.valueOf(file4.getId()), "--calculate-stats"});
        Assert.assertEquals(Index.IndexStatus.READY, catalogManager.getFile(file4.getId(), sessionId).first().getIndex().getStatus().getStatus());
        Assert.assertEquals(Cohort.CohortStatus.READY, catalogManager.getAllCohorts(studyId, new Query(CatalogCohortDBAdaptor.QueryParams.NAME.key(), "ALL"), null, sessionId).first().getStatus().getStatus());
        job = catalogManager.getAllJobs(studyId, new Query(CatalogJobDBAdaptor.QueryParams.INPUT.key(), file4.getId()), null, sessionId).first();
        Assert.assertEquals(Job.JobStatus.READY, job.getStatus().getStatus());

        AnalysisMain.privateMain(new String[]{"variant", "index", "--session-id", sessionId, "--file-id", String.valueOf(file5.getId())});
        Assert.assertEquals(Index.IndexStatus.READY, catalogManager.getFile(file5.getId(), sessionId).first().getIndex().getStatus().getStatus());
        Assert.assertEquals(Cohort.CohortStatus.INVALID, catalogManager.getAllCohorts(studyId, new Query(CatalogCohortDBAdaptor.QueryParams.NAME.key(), "ALL"), null, sessionId).first().getStatus().getStatus());
        job = catalogManager.getAllJobs(studyId, new Query(CatalogJobDBAdaptor.QueryParams.INPUT.key(), file5.getId()), null, sessionId).first();
        Assert.assertEquals(Job.JobStatus.READY, job.getStatus().getStatus());

        AnalysisMain.privateMain(new String[]{"variant", "stats", "--session-id", sessionId, "--study-id", String.valueOf(studyId), "--cohort-ids", "ALL", "--outdir-id", String.valueOf(outdirId)});
        Assert.assertEquals(Cohort.CohortStatus.READY, catalogManager.getAllCohorts(studyId, new Query(CatalogCohortDBAdaptor.QueryParams.NAME.key(), "ALL"), null, sessionId).first().getStatus().getStatus());

        catalogManager.createCohort(studyId, "coh1", Cohort.Type.CONTROL_SET, "", file1.getSampleIds(), null, sessionId);
        catalogManager.createCohort(studyId, "coh2", Cohort.Type.CONTROL_SET, "", file2.getSampleIds(), null, sessionId);

        AnalysisMain.privateMain(new String[]{"variant", "stats", "--session-id", sessionId, "--study-id", String.valueOf(studyId), "--cohort-ids", "coh1", "--outdir-id", String.valueOf(outdirId)});
        Assert.assertEquals(Cohort.CohortStatus.READY, catalogManager.getAllCohorts(studyId, new Query(CatalogCohortDBAdaptor.QueryParams.NAME.key(), "coh1"), null, sessionId).first().getStatus().getStatus());
        Assert.assertEquals(Cohort.CohortStatus.NONE, catalogManager.getAllCohorts(studyId, new Query(CatalogCohortDBAdaptor.QueryParams.NAME.key(), "coh2"), null, sessionId).first().getStatus().getStatus());



    }
}