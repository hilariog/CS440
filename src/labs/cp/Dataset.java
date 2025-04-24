package src.labs.cp;


// SYSTEM IMPORTS
import java.util.Iterator;
import java.util.Random;


// JAVA PROJECT IMPORTS
import edu.bu.cp.linalg.Matrix;
import edu.bu.cp.utils.Pair;


public class Dataset
    extends Object
{
    public static class BatchIterator
        extends Object implements Iterator<Pair<Matrix, Matrix> >
    {
        private final Matrix X;
        private final Matrix YGt;
        private final long batchSize;
        private final long numBatches;
        private long currentBatchIdx;

        public BatchIterator(final Matrix X,
                             final Matrix YGt,
                             final long batchSize,
                             final long numBatches)
        {
            this.X = X;
            this.YGt = YGt;
            this.batchSize = batchSize;
            this.numBatches = numBatches;
            this.currentBatchIdx = 0l;
        }

        protected final Matrix getFullX() { return this.X; }
        protected final Matrix getFullYGt() { return this.YGt; }
        protected final long getBatchSize() { return this.batchSize; }
        protected final long getNumBatches() { return this.numBatches; }
        protected long getCurrentBatchIdx() { return this.currentBatchIdx; }

        private void setCurrentBatchIdx(long l) { this.currentBatchIdx = l; }

        @Override
        public boolean hasNext() { return this.getCurrentBatchIdx() < this.getNumBatches(); }

        @Override
        public Pair<Matrix, Matrix> next()
        {
            int rIdxStart = (int)(this.getBatchSize() * this.getCurrentBatchIdx());
            int rIdxEnd = (int)((this.getBatchSize() + 1) * this.getCurrentBatchIdx());
            if(rIdxEnd >= this.getFullX().getShape().getNumRows())
            {
                rIdxEnd = this.getFullX().getShape().getNumRows();
            }
            Matrix XBatch = null;
            Matrix YGtBatch = null;

            try
            {
                XBatch = this.getFullX().getSlice(rIdxStart, rIdxEnd,
                                                  0, this.getFullX().getShape().getNumCols());
                YGtBatch = this.getFullYGt().getSlice(rIdxStart, rIdxEnd,
                                                      0, this.getFullYGt().getShape().getNumCols());
            } catch(Exception e)
            {
                System.err.println("[ERROR] BatchIterator.next: caught");
                e.printStackTrace();
                System.exit(-1);
            }

            this.setCurrentBatchIdx(this.getCurrentBatchIdx() + 1);
            return new Pair<Matrix, Matrix>(XBatch, YGtBatch);
        }
    }

    private final Matrix    X;
    private final Matrix    YGt;

    private final long      batchSize;
    private final Random    rng;

    public Dataset(final Matrix X,
                   final Matrix YGt,
                   final long batchSize,
                   final Random rng)
    {
        this.X = X;
        this.YGt = YGt;
        this.batchSize = batchSize;
        this.rng = rng;
    }

    protected final Matrix getFullX() { return this.X; }
    protected final Matrix getFullYGt() { return this.YGt; }
    public final long getBatchSize() { return this.batchSize; }
    protected final Random getRandom() { return this.rng; }

    public BatchIterator iterator() { return new BatchIterator(this.getFullX(),
                                                               this.getFullYGt(),
                                                               this.getBatchSize(),
                                                               this.size()); }

    public void shuffle()
    {
        try
        {
            // shuffle elements
            int randIdx = -1;
            Matrix tmp = null;
            for(int idx = 0; idx < this.getFullX().getShape().getNumRows(); ++idx)
            {
                randIdx = this.getRandom().nextInt(idx + 1);

                // swap row in X
                tmp = this.getFullX().getRow(randIdx);
                this.getFullX().copySlice(randIdx, randIdx + 1, 0, this.getFullX().getShape().getNumCols(),
                                          this.getFullX().getRow(idx));
                this.getFullX().copySlice(idx, idx + 1, 0, this.getFullX().getShape().getNumCols(),
                                          tmp);

                // now swap row in YGt
                tmp = this.getFullYGt().getRow(randIdx);
                this.getFullYGt().copySlice(randIdx, randIdx + 1, 0, this.getFullYGt().getShape().getNumCols(),
                                            this.getFullYGt().getRow(idx));
                this.getFullYGt().copySlice(idx, idx + 1, 0, this.getFullYGt().getShape().getNumCols(),
                                            tmp);
            }
        } catch(Exception e)
        {
            System.err.println("[ERROR] Dataset.shuffle: caught");
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public long size()
    {
        final long numSamples = this.getFullX().getShape().getNumRows();

        long numBatches = numSamples / this.getBatchSize();
        if(numSamples % this.getBatchSize() != 0)
        {
            numBatches += 1;
        }
        return numBatches;
    }
}
