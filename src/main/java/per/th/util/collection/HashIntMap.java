package per.th.util.collection;

import org.apache.lucene.util.RamUsageEstimator;
import per.th.io.FileLineParser;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * @author th
 * @date 2023/10/8
 * @see
 * @since
 */
public class HashIntMap implements IntMap {

    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final int MAXIMUM_CAPACITY = 1 << 15;
    private static final int DEFAULT_INITIAL_CAPACITY = 1 << 4;
    private static final int DEFAULT_SEGMENT_SIZE = 1 << 8;
    private static final int MAXIMUM_SEGMENT_SIZE = 1 << 24;

    private final float loadFactor;
    private final int segmentSize;

    private int slot;
    private int size;
    private int threshold;

    private Segment[] table;

    public HashIntMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        this.segmentSize = DEFAULT_SEGMENT_SIZE;
    }

    public HashIntMap(int initialCapacity) {
        this(DEFAULT_SEGMENT_SIZE, initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public HashIntMap(int segmentSize, int initialCapacity, float loadFactor) {
        if (segmentSize < 0) {
            throw new IllegalArgumentException("Illegal initial segment size: " + segmentSize);
        }
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
        }
        if (segmentSize > MAXIMUM_SEGMENT_SIZE) {
            segmentSize = MAXIMUM_SEGMENT_SIZE;
        }
        if (initialCapacity > MAXIMUM_CAPACITY) {
            initialCapacity = MAXIMUM_CAPACITY;
        }
        this.loadFactor = loadFactor;
        this.segmentSize = sizeFor(segmentSize, DEFAULT_SEGMENT_SIZE, MAXIMUM_SEGMENT_SIZE);
        this.threshold = sizeFor(initialCapacity, DEFAULT_INITIAL_CAPACITY, MAXIMUM_CAPACITY);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean exist(int key) {
        Segment segment = find(key, false);
        return segment != null && segment.exist(key);
    }

    @Override
    public int get(int key) {
        Segment segment = find(key, false);
        if (segment == null) {
            throw new NoSuchElementException("No such element for " + key);
        }
        return segment.get(key);
    }

    @Override
    public int set(int key, int value) {
        Segment segment = find(key, true);
        assert segment != null;
        return segment.set(key, value);
    }

    private Segment find(int key, boolean autoCreateIfAbsent) {
        Segment[] tab = table;

        if (tab == null || tab.length == 0) {
            if (autoCreateIfAbsent) {
                tab = resize();
            } else {
                return null;
            }
        }

        int index; Segment p;

        if ((p = tab[index = x(key)]) == null) {
            if (autoCreateIfAbsent) {
                p = tab[index] = newSegment(key, null);
            } else {
                return null;
            }
        } else if (key < p.min()){
            if (autoCreateIfAbsent) {
                p = tab[index] = newSegment(key, p);
            } else {
                return null;
            }
        } else {
            for (;;) {
                if (key <= p.max()) {
                    return p;
                }
                if (p.next == null || key < p.next.min()) {
                    if (autoCreateIfAbsent) {
                        p = p.next = newSegment(key, p.next);
                        break;
                    } else {
                        return null;
                    }
                }
                p = p.next;
            }
        }

        if (++slot == threshold) {
            resize();
        }

        return p;
    }

    private Segment[] resize() {
        Segment[] oldTab = table;
        int oldCap = oldTab == null ? 0 : oldTab.length;
        int oldThr = threshold, newCap, newThr = 0;

        if (oldCap > 0) {
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }

            newCap = oldCap << 1;
            if (newCap < MAXIMUM_CAPACITY && oldCap >= DEFAULT_INITIAL_CAPACITY) {
                newThr = oldThr << 1;
            }
        } else if (oldThr > 0) {
            newCap = oldThr;
        } else {
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(loadFactor * newCap);
        }

        if (newThr == 0) {
            float ft = (float) newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float) MAXIMUM_CAPACITY ?
                    (int)ft : Integer.MAX_VALUE);
        }

        Segment[] newTab = new Segment[newCap];
        if (oldTab != null) {
            rehash(oldTab, newTab);
        }

        threshold = newThr;
        return table = newTab;
    }

    private void rehash(Segment[] oldTab, Segment[] newTab) {
        Segment e; int oldLen = oldTab.length, newLen = newTab.length;

        for (int i = 0; i < oldLen; i++) {
            if ((e = oldTab[i]) == null) {
                continue;
            }

            oldTab[i] = null;

            if (e.next == null) {
                newTab[x(e.from(), newLen)] = e;
                continue;
            }

            Segment next;
            Segment loHead = null, loTail = null;
            Segment hiHead = null, hiTail = null;

            do {
                next = e.next;
                if (x(e.from(), newLen) == i) {
                    if (loTail == null) {
                        loHead = e;
                    } else {
                        loTail.next = e;
                    }
                    loTail = e;
                } else {
                    if (hiTail == null) {
                        hiHead = e;
                    } else {
                        hiTail.next = e;
                    }
                    hiTail = e;
                }
            } while ((e = next) != null);

            if (loTail != null) {
                loTail.next = null;
                newTab[i] = loHead;
            }

            if (hiTail != null) {
                hiTail.next = null;
                newTab[i + oldLen] = hiHead;
            }
        }
    }

    private Segment newSegment(int from, Segment next) {
        Segment res = new Segment(from);
        res.next = next;
        return res;
    }

    private int x(int key) {
        return x(key, table.length);
    }

    private static int x(int key, int mod) {
        return hash(key) & (mod - 1);
    }

    private static int hash(int key) {
        key >>>= 8;
        key ^= key >>> 4;
        key ^= key >>> 4;
        key ^= key >>> 4;
        return key;
    }

    private static int sizeFor(int cap, int min, int max) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 3;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return n < 0 ? min : n >= max ? max : n + 1;
    }

    private class Segment {

        private int from;
        private int offset;
        private char[] chars;
        private Segment next;

        public Segment(int from) {
            this.offset = 1;
            this.from = from;
        }

        public boolean inRange(int key) {
            return min() <= key && key <= max();
        }

        public int min() {
            return -capacity() & from;
        }

        public int max() {
            return min() + (capacity() - 1);
        }

        public int from() {
            return from;
        }

        public int to() {
            return from() + length();
        }

        public int position() {
            return from() - min();
        }

        public int limit() {
            return to() - min();
        }

        public int capacity() {
            return segmentSize;
        }

        public int length() {
            return chars == null ? 0 : (offset > 0 ? chars.length - offset : chars.length >> 1);
        }

        public boolean isEmpty() {
            return chars == null || chars.length < 2;
        }

        public void from(int from) {
            if (from == from()) {
                return ;
            }

            int min = min(), to = to();
            if (from < min || from > to) {
                throw new IllegalArgumentException("Offset " + offset +
                        " not in range (" + min + "," + to + "]");
            }

            char[] oldArr = chars;
            int oldLen = oldArr == null ? 0 : oldArr.length;

            int newLen = offset > 0 ? (to - from) + offset : (to - from) << 1;
            char[] newArr = new char[newLen];

            if (newLen > 0 && oldLen > 0) {
                int srcPos, dstPos, copyLen;

                if (newLen > oldLen) {
                    srcPos = offset > 0 ? 1 : 0;
                    dstPos = newLen - oldLen + srcPos;
                    copyLen = oldLen - srcPos;
                } else {
                    dstPos = offset > 0 ? 1 : 0;
                    srcPos = oldLen - newLen + dstPos;
                    copyLen = newLen - dstPos;
                }

                if (offset > 0) {
                    newArr[0] = oldArr[0];
                }

                System.arraycopy(oldArr, srcPos, newArr, dstPos, copyLen);
            }

            this.from = from;
            this.chars = newArr;
        }

        public void to(int to) {
            if (to == to()) {
                return ;
            }

            int from = from(), max = max();
            if (to < from || to - 1 > max) {
                throw new IllegalArgumentException("To " + to +
                        " not in range (" + from + "," + max + ")");
            }

            char[] oldArr = chars;
            int oldLen = oldArr == null ? 0 : oldArr.length;

            int newLen = offset > 0 ? (to - from) + offset : (to - from) << 1;
            char[] newArr = new char[newLen];

            if (newLen > 0 && oldLen > 0) {
                int copyLen = Math.min(oldLen, newLen);
                System.arraycopy(oldArr, 0, newArr, 0, copyLen);
            }

            this.chars = newArr;
        }

        public void grow() {
            char[] oldArr = this.chars;
            int oldLen = oldArr == null ? 0 : oldArr.length;

            if (offset < 1) {
                return;
            }

            int newLen = oldLen == 0 ? offset + 1 : (oldLen - 1) << 1;
            char[] newArr = new char[newLen];

            char ch;
            for (int i=1, j; i < oldLen; i++) {
                j = (i - 1) << 1;
                ch = oldArr[i];
                newArr[j] = ch == 0 ? 0 : oldArr[0];
                newArr[j + 1] = ch;
            }

            this.offset = oldLen == 0 ? 1 : 0;
            this.chars = newArr;
        }

        public boolean exist(int key) {
            try {
                return get(key) != 0;
            } catch (NoSuchElementException e) {
                return false;
            }
        }

        public int get(int key) {
            char[] chars = this.chars;

            int length = chars == null ? 0 : chars.length;
            if (length == 0 || !inRange(key)) {
                throw new NoSuchElementException("No such element for " + key);
            }

            if (offset < 1) {
                int i = (key - from) << 1;
                if (i < chars.length) {
                    return chars[i] << 16 | chars[i + 1];
                } else {
                    throw new NoSuchElementException("No such element for " + key);
                }
            } else {
                int i = (key - from) + offset;
                if (i < chars.length) {
                    return chars[0] << 16 | chars[i];
                } else {
                    throw new NoSuchElementException("No such element for " + key);
                }
            }
        }

        public int set(int key, int value) {
            char high = (char) (value >>> 16);
            char low = (char) (value & 0xffff);

            ensureAdmissible(high, low);
            ensureCapacity(key);

            int res;
            if (offset < 1) {
                int i = (key - from) << 1;
                res = chars[i] << 16 | chars[i + 1];
                chars[i] = high;
                chars[i + 1] = low;
            } else {
                int i = (key - from) + offset;
                res = chars[0] << 16 | chars[i];
                chars[0] = high;
                chars[i] = low;
            }

            if (res == 0) {
                size++;
            }

            return res;
        }

        private void ensureAdmissible(char high, char low) {
            if (chars == null || (offset > 0 && (chars[0] != high || low == 0))) {
                grow();
            }
        }

        private void ensureCapacity(int key){
            if (!inRange(key)) {
                throw new IndexOutOfBoundsException("Index " + key
                        + " out of range (" + min() + "," + max() + ")");
            }

            if (key >= to()) {
                to(key + 1);
            } else if (key < from()) {
                from(key);
            } else {
                ;
            }
        }

        @Override
        public String toString() {
            int n = 1; Segment p = this;
            while ((p = p.next) != null) {
                n++;
            }
            return String.format("min:%s, max:%s, node: %s, range:(%s-%s]", min(), max(), n, from(), to());
        }
    }

    public static void main(String[] args) throws IOException {
        URL url = ClassLoader.getSystemResource("GB18030.txt");
        FileLineParser parser = new FileLineParser(url, ",");

        Map<Integer, Integer> hashMap = new HashMap<>();
        HashIntMap intMap = new HashIntMap();

        try {
            while (parser.next()) {
                int key = parser.getUnsignedInt(2, 16);
                int value = parser.getUnsignedInt(1, 16);
                hashMap.put(key, value);
                intMap.set(key, value);
            }
        } finally {
            parser.close();
        }

        hashMap.forEach((k, v) -> {
            if (intMap.get(k) != v) {
                throw new RuntimeException(String.format("%s=%s, expect %s", k, intMap.get(k), v));
            }
        });

        System.out.println("HashMap: " + RamUsageEstimator.humanSizeOf(hashMap));
        System.out.println("HashIntMap: " + RamUsageEstimator.humanSizeOf(intMap));
    }

}
