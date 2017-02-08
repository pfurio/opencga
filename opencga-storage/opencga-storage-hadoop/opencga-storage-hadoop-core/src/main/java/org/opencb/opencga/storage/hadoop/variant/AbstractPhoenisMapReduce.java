package org.opencb.opencga.storage.hadoop.variant;

import com.google.common.collect.BiMap;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.opencb.opencga.storage.core.metadata.StudyConfiguration;
import org.opencb.opencga.storage.hadoop.variant.converters.HBaseToVariantConverter;
import org.opencb.opencga.storage.hadoop.variant.index.VariantTableHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by mh719 on 22/12/2016.
 */
public class AbstractPhoenisMapReduce<PHOENIXIN, KEYOUT, VALUEOUT> extends Mapper<NullWritable, PHOENIXIN, KEYOUT, VALUEOUT> {
    private Logger LOG = LoggerFactory.getLogger(this.getClass());
    private final AtomicReference<OpencgaMapReduceHelper> mrHelper = new AtomicReference<>();


    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        super.setup(context);
        mrHelper.set(new OpencgaMapReduceHelper(context));
    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
        super.cleanup(context);
        getMrHelper().cleanup();
    }

    public Logger getLog() {
        return LOG;
    }

    public OpencgaMapReduceHelper getMrHelper() {
        return mrHelper.get();
    }

    public VariantTableHelper getHelper() {
        return getMrHelper().getHelper();
    }

    public long getTimestamp() {
        return getMrHelper().getTimestamp();
    }

    public void setTimestamp(long timestamp) {
        getMrHelper().setTimestamp(timestamp);
    }

    public BiMap<String, Integer> getIndexedSamples() {
        return getMrHelper().getIndexedSamples();
    }

    public void setIndexedSamples(BiMap<String, Integer> indexedSamples) {
        getMrHelper().setIndexedSamples(indexedSamples);
    }

    public StudyConfiguration getStudyConfiguration() {
        return getMrHelper().getStudyConfiguration();
    }

    public void setStudyConfiguration(StudyConfiguration studyConfiguration) {
        getMrHelper().setStudyConfiguration(studyConfiguration);
    }

    public HBaseToVariantConverter getHbaseToVariantConverter() {
        return getMrHelper().getHbaseToVariantConverter();
    }

    public void setHbaseToVariantConverter(HBaseToVariantConverter hbaseToVariantConverter) {
        getMrHelper().setHbaseToVariantConverter(hbaseToVariantConverter);
    }

    public void startTime() {
        getMrHelper().startTime();
    }

    public void endTime(String name) {
        getMrHelper().endTime(name);
    }

    public Map<String, Long> getTimes() {
        return getMrHelper().getTimes();
    }
}
