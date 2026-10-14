package mn.suld.api.payment.qr;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrCodeTest {

    @Test
    void theCapacityTablesHoldTheStandardsNumbers() {
        // data codewords per version and level (ISO/IEC 18004 table 7)
        assertEquals(19, QrCode.dataCodewords(1, QrCode.Ecc.L));
        assertEquals(16, QrCode.dataCodewords(1, QrCode.Ecc.M));
        assertEquals(13, QrCode.dataCodewords(1, QrCode.Ecc.Q));
        assertEquals(9, QrCode.dataCodewords(1, QrCode.Ecc.H));
        assertEquals(62, QrCode.dataCodewords(5, QrCode.Ecc.Q));
        assertEquals(124, QrCode.dataCodewords(7, QrCode.Ecc.M));
        assertEquals(274, QrCode.dataCodewords(10, QrCode.Ecc.L));
        assertEquals(216, QrCode.dataCodewords(10, QrCode.Ecc.M));
        assertEquals(523, QrCode.dataCodewords(15, QrCode.Ecc.L));
        assertEquals(2956, QrCode.dataCodewords(40, QrCode.Ecc.L));
        assertEquals(1276, QrCode.dataCodewords(40, QrCode.Ecc.H));
        assertEquals(26, QrCode.rawDataModules(1) / 8);
        assertEquals(3706, QrCode.rawDataModules(40) / 8);
    }

    @Test
    void alignmentPatternsSitWhereTheStandardPutsThem() {
        assertArrayEquals(new int[0], QrCode.alignmentPositions(1));
        assertArrayEquals(new int[]{6, 18}, QrCode.alignmentPositions(2));
        assertArrayEquals(new int[]{6, 22, 38}, QrCode.alignmentPositions(7));
        assertArrayEquals(new int[]{6, 34, 60, 86, 112, 138}, QrCode.alignmentPositions(32));
        assertArrayEquals(new int[]{6, 30, 58, 86, 114, 142, 170}, QrCode.alignmentPositions(40));
    }

    @Test
    void reedSolomonMatchesTheWorkedExample() {
        // «HELLO WORLD», version 1-M: the data codewords and their ten error-correction codewords
        int[] data = {32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236, 17, 236, 17};
        int[] ecc = {196, 35, 39, 119, 235, 215, 231, 226, 93, 23};
        byte[] d = new byte[data.length];
        for (int i = 0; i < data.length; i++) d[i] = (byte) data[i];
        byte[] got = QrCode.rsRemainder(d, QrCode.rsDivisor(10));
        for (int i = 0; i < ecc.length; i++) assertEquals(ecc[i], got[i] & 0xFF, "ecc " + i);
    }

    @Test
    void theSmallestVersionThatHoldsTheDataIsChosen() {
        assertEquals(1, QrCode.encode(new byte[17], QrCode.Ecc.L, -1).version()); // 4 + 8 + 136 bits ≤ 152
        assertEquals(2, QrCode.encode(new byte[18], QrCode.Ecc.L, -1).version());
        assertEquals(21, QrCode.encode(new byte[17], QrCode.Ecc.L, -1).size());
        assertEquals(9, QrCode.encode(new byte[230], QrCode.Ecc.L, -1).version());
        assertEquals(10, QrCode.encode(new byte[231], QrCode.Ecc.L, -1).version()); // past 9 the count takes 16 bits
        assertEquals(40, QrCode.encode(new byte[2953], QrCode.Ecc.L, -1).version()); // the largest a code holds
        assertThrows(IllegalArgumentException.class, () -> QrCode.encode(new byte[2954], QrCode.Ecc.L, -1));
    }

    @Test
    void finderTimingAndTheDarkModuleAreInPlace() {
        QrCode q = QrCode.encode("00020101021232190015mn.bonum.test", QrCode.Ecc.M);
        int n = q.size();
        for (int[] c : new int[][]{{0, 0}, {n - 7, 0}, {0, n - 7}}) {
            for (int dy = 0; dy < 7; dy++) {
                for (int dx = 0; dx < 7; dx++) {
                    int d = Math.max(Math.abs(dx - 3), Math.abs(dy - 3));
                    assertEquals(d != 2, q.dark(c[0] + dx, c[1] + dy), "finder at " + c[0] + "," + c[1]);
                }
            }
        }
        for (int i = 8; i < n - 8; i++) {
            assertEquals(i % 2 == 0, q.dark(i, 6), "timing row");
            assertEquals(i % 2 == 0, q.dark(6, i), "timing column");
        }
        assertTrue(q.dark(8, n - 8), "the dark module");
        assertFalse(q.dark(-1, 0));
        assertFalse(q.dark(n, n));
    }

    @Test
    void bothCopiesOfTheFormatInformationAgreeAndAreValid() {
        for (QrCode.Ecc e : QrCode.Ecc.values()) {
            for (int mask = 0; mask < 8; mask++) {
                QrCode q = QrCode.encode("SULD".getBytes(StandardCharsets.UTF_8), e, mask);
                int n = q.size();
                int a = 0, b = 0;
                for (int i = 0; i <= 5; i++) a |= bit(q.dark(8, i)) << i;
                a |= bit(q.dark(8, 7)) << 6;
                a |= bit(q.dark(8, 8)) << 7;
                a |= bit(q.dark(7, 8)) << 8;
                for (int i = 9; i < 15; i++) a |= bit(q.dark(14 - i, 8)) << i;
                for (int i = 0; i < 8; i++) b |= bit(q.dark(n - 1 - i, 8)) << i;
                for (int i = 8; i < 15; i++) b |= bit(q.dark(8, n - 15 + i)) << i;
                assertEquals(a, b, e + " mask " + mask);
                int word = a ^ 0x5412;
                assertEquals(e.bits << 3 | mask, word >>> 10, "the level and mask it names");
                int rem = word;
                for (int i = 14; i >= 10; i--) if ((rem >>> i & 1) != 0) rem ^= 0x537 << (i - 10);
                assertEquals(0, rem, "a BCH(15,5) codeword");
            }
        }
    }

    @Test
    void theVersionInformationIsDrawnFromSeven() {
        QrCode q = QrCode.encode(new byte[110], QrCode.Ecc.M, -1); // version 7
        assertEquals(7, q.version());
        int n = q.size(), bits = 0, mirror = 0;
        for (int i = 0; i < 18; i++) {
            bits |= bit(q.dark(n - 11 + i % 3, i / 3)) << i;
            mirror |= bit(q.dark(i / 3, n - 11 + i % 3)) << i;
        }
        assertEquals(0x07C94, bits, "version 7's BCH(18,6) word");
        assertEquals(bits, mirror);
    }

    @Test
    void theSameTextGivesTheSameCode() {
        QrCode a = QrCode.encode("0002010102121531279404962794049600022310027138152045812530349654031005802MN", QrCode.Ecc.M);
        QrCode b = QrCode.encode("0002010102121531279404962794049600022310027138152045812530349654031005802MN", QrCode.Ecc.M);
        assertEquals(a.version(), b.version());
        assertEquals(a.mask(), b.mask());
        for (int y = 0; y < a.size(); y++) for (int x = 0; x < a.size(); x++) assertEquals(a.dark(x, y), b.dark(x, y));
    }

    @Test
    void aMapShowsTheCodeAtAWholeScaleWithAQuietZone() {
        QrCode q = QrCode.encode(new byte[200], QrCode.Ecc.L, -1); // version 9: 53 modules
        QrRaster r = QrRaster.fit(q, QrRaster.MAP);
        assertEquals(2, r.scale());
        assertTrue(r.margin() >= QrRaster.QUIET * r.scale());
        assertEquals(q.dark(0, 0), r.dark(r.margin(), r.margin()));
        assertEquals(q.dark(0, 0), r.dark(r.margin() + 1, r.margin() + 1));
        assertFalse(r.dark(r.margin() - 1, r.margin()));
        assertFalse(r.dark(127, 127));
        QrRaster small = QrRaster.fit(QrCode.encode("SULD", QrCode.Ecc.M), QrRaster.MAP);
        assertEquals(5, small.scale()); // 21 modules + 4 quiet → 128 / 25
        assertThrows(IllegalArgumentException.class, () -> QrRaster.fit(QrCode.encode(new byte[1700], QrCode.Ecc.L, -1), QrRaster.MAP));
    }

    private static int bit(boolean b) {
        return b ? 1 : 0;
    }
}
