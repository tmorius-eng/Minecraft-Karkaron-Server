package mn.suld.api.payment.qr;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A QR Code (ISO/IEC 18004) for a payment code, drawn on a map in the game (docs/PAYMENT_ARCHITECTURE.md §QR): byte
 * mode, versions 1–40, error correction L/M/Q/H, the mask with the lowest penalty. Pure Java; a map reads only
 * {@link #size()} and {@link #dark(int, int)}.
 */
public final class QrCode {

    /** Error correction: the share of the code that may be lost (L 7 %, M 15 %, Q 25 %, H 30 %). */
    public enum Ecc {
        L(1), M(0), Q(3), H(2);

        /** The two bits the format information carries. */
        final int bits;

        Ecc(int bits) {
            this.bits = bits;
        }
    }

    public static final int MIN_VERSION = 1, MAX_VERSION = 40;

    private final int version;
    private final int size;
    private final Ecc ecc;
    private final int mask;
    private final boolean[][] modules; // [y][x], true = dark
    private final boolean[][] function;

    private QrCode(int version, Ecc ecc, byte[] codewords, int forcedMask) {
        this.version = version;
        this.size = version * 4 + 17;
        this.ecc = ecc;
        this.modules = new boolean[size][size];
        this.function = new boolean[size][size];
        drawFunctionPatterns();
        drawCodewords(addEccAndInterleave(codewords));
        int chosen = forcedMask;
        if (chosen < 0) {
            int best = Integer.MAX_VALUE;
            for (int m = 0; m < 8; m++) {
                applyMask(m);
                drawFormatBits(m);
                int penalty = penalty();
                if (penalty < best) {
                    best = penalty;
                    chosen = m;
                }
                applyMask(m); // a mask is its own inverse
            }
        }
        this.mask = chosen;
        applyMask(chosen);
        drawFormatBits(chosen);
    }

    /** The smallest code holding {@code text} (UTF-8) at error correction {@code ecc}. */
    public static QrCode encode(String text, Ecc ecc) {
        return encode(text.getBytes(StandardCharsets.UTF_8), ecc, -1);
    }

    /** The smallest code holding {@code data} at {@code ecc}; {@code mask} 0–7, or −1 for the lowest penalty. */
    public static QrCode encode(byte[] data, Ecc ecc, int mask) {
        if (data == null || ecc == null) throw new IllegalArgumentException("no data");
        if (mask < -1 || mask > 7) throw new IllegalArgumentException("mask " + mask);
        int version = MIN_VERSION;
        int bits;
        while (true) {
            bits = 4 + (version <= 9 ? 8 : 16) + data.length * 8;
            if (bits <= dataCodewords(version, ecc) * 8) break;
            if (++version > MAX_VERSION) throw new IllegalArgumentException("too long for a QR code: " + data.length + " bytes");
        }
        int capacity = dataCodewords(version, ecc) * 8;
        BitBuffer bb = new BitBuffer(capacity);
        bb.append(0b0100, 4); // byte mode
        bb.append(data.length, version <= 9 ? 8 : 16);
        for (byte b : data) bb.append(b & 0xFF, 8);
        bb.append(0, Math.min(4, capacity - bb.length)); // terminator
        bb.append(0, (8 - bb.length % 8) % 8);
        for (int pad = 0xEC; bb.length < capacity; pad ^= 0xEC ^ 0x11) bb.append(pad, 8);
        return new QrCode(version, ecc, bb.bytes(), mask);
    }

    public int version() {
        return version;
    }

    /** Modules per side: 21 … 177. */
    public int size() {
        return size;
    }

    public Ecc ecc() {
        return ecc;
    }

    public int mask() {
        return mask;
    }

    /** True for a dark module at column {@code x}, row {@code y}; outside the code (the quiet zone) is light. */
    public boolean dark(int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size && modules[y][x];
    }

    // ------------------------------------------------------------------ function patterns

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {
            set(6, i, i % 2 == 0);
            set(i, 6, i % 2 == 0);
        }
        finder(3, 3);
        finder(size - 4, 3);
        finder(3, size - 4);
        int[] align = alignmentPositions(version);
        int n = align.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == 0 && j == 0 || i == 0 && j == n - 1 || i == n - 1 && j == 0) continue; // the finders' corners
                alignment(align[i], align[j]);
            }
        }
        drawFormatBits(0); // reserves the format areas; drawn for real once the mask is chosen
        drawVersion();
    }

    private void finder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x < 0 || y < 0 || x >= size || y >= size) continue;
                int d = Math.max(Math.abs(dx), Math.abs(dy));
                set(x, y, d != 2 && d != 4);
            }
        }
    }

    private void alignment(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) set(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
        }
    }

    private void drawFormatBits(int mask) {
        int data = ecc.bits << 3 | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = rem << 1 ^ (rem >>> 9) * 0x537;
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(8, i, bit(bits, i));
        set(8, 7, bit(bits, 6));
        set(8, 8, bit(bits, 7));
        set(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(8, size - 15 + i, bit(bits, i));
        set(8, size - 8, true); // the dark module
    }

    private void drawVersion() {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) rem = rem << 1 ^ (rem >>> 11) * 0x1F25;
        int bits = version << 12 | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = bit(bits, i);
            int a = size - 11 + i % 3, c = i / 3;
            set(a, c, b);
            set(c, a, b);
        }
    }

    /** Centres of the alignment patterns on each axis (the same list for x and y). */
    static int[] alignmentPositions(int version) {
        if (version == 1) return new int[0];
        int n = version / 7 + 2;
        int step = (version * 8 + n * 3 + 5) / (n * 4 - 4) * 2;
        int[] out = new int[n];
        out[0] = 6;
        for (int i = n - 1, pos = version * 4 + 17 - 7; i >= 1; i--, pos -= step) out[i] = pos;
        return out;
    }

    private void set(int x, int y, boolean dark) {
        modules[y][x] = dark;
        function[y][x] = true;
    }

    // ------------------------------------------------------------------ data

    private byte[] addEccAndInterleave(byte[] data) {
        int blocks = BLOCKS[ecc.ordinal()][version];
        int eccLen = ECC_PER_BLOCK[ecc.ordinal()][version];
        int raw = rawDataModules(version) / 8;
        int shortBlocks = blocks - raw % blocks;
        int shortLen = raw / blocks;
        byte[][] out = new byte[blocks][];
        byte[] divisor = rsDivisor(eccLen);
        for (int i = 0, k = 0; i < blocks; i++) {
            byte[] dat = Arrays.copyOfRange(data, k, k + shortLen - eccLen + (i < shortBlocks ? 0 : 1));
            k += dat.length;
            byte[] block = Arrays.copyOf(dat, shortLen + 1);
            byte[] e = rsRemainder(dat, divisor);
            System.arraycopy(e, 0, block, block.length - eccLen, e.length);
            out[i] = block;
        }
        byte[] result = new byte[raw];
        for (int i = 0, k = 0; i < out[0].length; i++) {
            for (int j = 0; j < blocks; j++) {
                if (i != shortLen - eccLen || j >= shortBlocks) result[k++] = out[j][i]; // short blocks have no byte there
            }
        }
        return result;
    }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5; // the vertical timing pattern
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = (right + 1 & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!function[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (function[y][x]) continue;
                boolean invert = switch (mask) {
                    case 0 -> (x + y) % 2 == 0;
                    case 1 -> y % 2 == 0;
                    case 2 -> x % 3 == 0;
                    case 3 -> (x + y) % 3 == 0;
                    case 4 -> (x / 3 + y / 2) % 2 == 0;
                    case 5 -> x * y % 2 + x * y % 3 == 0;
                    case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
                    case 7 -> ((x + y) % 2 + x * y % 3) % 2 == 0;
                    default -> throw new IllegalArgumentException("mask " + mask);
                };
                if (invert) modules[y][x] = !modules[y][x];
            }
        }
    }

    /**
     * The standard's penalty (ISO/IEC 18004 §7.8.3): runs of five or more, 2×2 blocks, finder-like 1:1:3:1:1 runs with
     * four light modules on a side (the quiet zone outside the symbol counts as light), and the dark balance. Lower
     * scans more easily.
     */
    private int penalty() {
        int score = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                boolean colour = false;
                int run = 0;
                int[] history = new int[7];
                for (int b = 0; b < size; b++) {
                    boolean m = pass == 0 ? modules[a][b] : modules[b][a];
                    if (m == colour) {
                        run++;
                        if (run == 5) score += 3;
                        else if (run > 5) score++;
                    } else {
                        addRun(run, history);
                        if (!colour) score += finderLike(history) * 40;
                        colour = m;
                        run = 1;
                    }
                }
                if (colour) { // close a dark run, then the light quiet zone
                    addRun(run, history);
                    run = 0;
                }
                addRun(run + size, history);
                score += finderLike(history) * 40;
            }
        }
        for (int y = 0; y + 1 < size; y++) {
            for (int x = 0; x + 1 < size; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) score += 3;
            }
        }
        int dark = 0;
        for (boolean[] row : modules) for (boolean m : row) if (m) dark++;
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        return score + Math.max(0, k) * 10;
    }

    /** Pushes a run length on the history (newest first); the first run of a line also counts the light zone before it. */
    private void addRun(int run, int[] history) {
        if (history[0] == 0) run += size;
        System.arraycopy(history, 0, history, 1, history.length - 1);
        history[0] = run;
    }

    /** Finder-like patterns ending at the newest light run: dark 1:1:3:1:1 with four light modules before or after. */
    private static int finderLike(int[] h) {
        int n = h[1];
        boolean core = n > 0 && h[2] == n && h[3] == n * 3 && h[4] == n && h[5] == n;
        return (core && h[0] >= n * 4 && h[6] >= n ? 1 : 0) + (core && h[6] >= n * 4 && h[0] >= n ? 1 : 0);
    }

    // ------------------------------------------------------------------ Reed–Solomon over GF(2^8), x^8+x^4+x^3+x^2+1

    static byte[] rsDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < result.length; j++) {
                result[j] = (byte) gfMultiply(result[j] & 0xFF, root);
                if (j + 1 < result.length) result[j] ^= result[j + 1];
            }
            root = gfMultiply(root, 0x02);
        }
        return result;
    }

    static byte[] rsRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) result[i] ^= (byte) gfMultiply(divisor[i] & 0xFF, factor);
        }
        return result;
    }

    static int gfMultiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = z << 1 ^ (z >>> 7) * 0x11D;
            z ^= (y >>> i & 1) * x;
        }
        return z;
    }

    // ------------------------------------------------------------------ capacity tables

    /** Modules left for data and error correction once the function patterns are drawn. */
    static int rawDataModules(int version) {
        int result = (16 * version + 128) * version + 64;
        if (version >= 2) {
            int n = version / 7 + 2;
            result -= (25 * n - 10) * n - 55;
            if (version >= 7) result -= 36;
        }
        return result;
    }

    /** Data codewords a version holds at a level. */
    static int dataCodewords(int version, Ecc ecc) {
        return rawDataModules(version) / 8 - ECC_PER_BLOCK[ecc.ordinal()][version] * BLOCKS[ecc.ordinal()][version];
    }

    /** Error-correction codewords per block, by level (L, M, Q, H) and version (index 0 unused). */
    private static final int[][] ECC_PER_BLOCK = {
            {-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
            {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28},
            {-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
            {-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
    };

    /** Error-correction blocks, by level (L, M, Q, H) and version (index 0 unused). */
    private static final int[][] BLOCKS = {
            {-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25},
            {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49},
            {-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68},
            {-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81},
    };

    private static boolean bit(int x, int i) {
        return (x >>> i & 1) != 0;
    }

    /** Bits appended most significant first, into whole bytes. */
    private static final class BitBuffer {
        private final byte[] data;
        int length;

        BitBuffer(int capacityBits) {
            data = new byte[(capacityBits + 7) / 8];
        }

        void append(int value, int bits) {
            for (int i = bits - 1; i >= 0; i--, length++) {
                if ((value >>> i & 1) != 0) data[length >>> 3] |= (byte) (0x80 >>> (length & 7));
            }
        }

        byte[] bytes() {
            return data;
        }
    }
}
