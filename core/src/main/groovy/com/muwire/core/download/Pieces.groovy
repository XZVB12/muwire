package com.muwire.core.download

class Pieces {
    private final BitSet done, claimed
    final int nPieces
    private final float ratio
    private final Random random = new Random()
    private final Map<Integer,Integer> partials = new HashMap<>()
    private int cachedDone;

    Pieces(int nPieces) {
        this(nPieces, 1.0f)
    }

    Pieces(int nPieces, float ratio) {
        this.nPieces = nPieces
        this.ratio = ratio
        done = new BitSet(nPieces)
        claimed = new BitSet(nPieces)
    }
    
    synchronized int[] claim() {
        int claimedCardinality = claimed.cardinality()
        if (claimedCardinality == nPieces) {
            // steal
            int downloadedCardinality = done.cardinality()
            if (downloadedCardinality == nPieces)
                return null
            int rv = done.nextClearBit(0)
            return [rv, partials.getOrDefault(rv, 0), 1]
        }

        // if fuller than ratio just do sequential
        if ( (1.0f * claimedCardinality) / nPieces >= ratio) {
            int rv = claimed.nextClearBit(0)
            claimed.set(rv)
            return [rv, partials.getOrDefault(rv, 0), 0]
        }

        while(true) {
            int start = random.nextInt(nPieces)
            if (claimed.get(start))
                continue
            claimed.set(start)
            return [start, partials.getOrDefault(start,0), 0]
        }
    }

    synchronized int[] claim(Set<Integer> available) {
        for (int i = done.nextSetBit(0); i >= 0; i = done.nextSetBit(i+1))
            available.remove(i)
        if (available.isEmpty())
            return null
        Set<Integer> availableCopy = new HashSet<>(available)
        for (int i = claimed.nextSetBit(0); i >= 0; i = claimed.nextSetBit(i+1))
            availableCopy.remove(i)
        if (availableCopy.isEmpty()) {
            // steal
            int rv = available.first()
            return [rv, partials.getOrDefault(rv, 0), 1]
        }
        List<Integer> toList = availableCopy.toList()
        if (ratio > 0f)
            Collections.shuffle(toList)
        int rv = toList[0]
        claimed.set(rv)
        [rv, partials.getOrDefault(rv, 0), 0]
    }

    synchronized def getDownloaded() {
        def rv = []
        for (int i = done.nextSetBit(0); i >= 0; i = done.nextSetBit(i+1)) {
            rv << i
        }
        rv
    }

    synchronized void markDownloaded(int piece) {
        if (piece >= nPieces)
            throw new IllegalArgumentException("invalid piece marked as downloaded? $piece/$nPieces")
        done.set(piece)
        cachedDone = done.cardinality();
        claimed.set(piece)
        partials.remove(piece)
    }
    
    synchronized void markPartial(int piece, int position) {
        partials.put(piece, position)
    }

    synchronized void unclaim(int piece) {
        claimed.clear(piece)
    }

    synchronized boolean isComplete() {
        cachedDone == nPieces
    }

    synchronized int donePieces() {
        cachedDone
    }

    synchronized boolean isDownloaded(int piece) {
        done.get(piece)
    }

    synchronized void clearAll() {
        done.clear()
        cachedDone = 0
        claimed.clear()
        partials.clear()
    }
    
    synchronized int firstIncomplete() {
        done.nextClearBit(0)
    }
    
    synchronized void write(PrintWriter writer) {
        for (int i = done.nextSetBit(0); i >= 0; i = done.nextSetBit(i+1)) {
            writer.println(i)
        }
        partials.each { piece, position ->
            writer.println("$piece,$position")
        }
    }
}
