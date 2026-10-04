import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Main {

    // ------------------------------------------------------------------
    // The diff engine: Myers' O(ND) algorithm, linear-space version.
    // It works on any int[] sequence (line ids for Part A, code points for Part B).
    // Result: del[i] is true if a[i] is deleted, ins[j] is true if b[j] is inserted.
    // ------------------------------------------------------------------
    static final class Diff {
        final int[] a, b;
        final boolean[] del, ins;
        final int[] vf, vb;   // V arrays: furthest x on each diagonal (forward / backward search)
        final int off;        // offset so that negative diagonals k can be array indexes
        int mx0, my0, mx1, my1; // the middle snake found by middleSnake()

        Diff(int[] a, int[] b) {
            this.a = a;
            this.b = b;
            del = new boolean[a.length];
            ins = new boolean[b.length];
            off = (a.length + b.length) / 2 + 2;
            vf = new int[2 * off + 3];
            vb = new int[2 * off + 3];
            solve(0, a.length, 0, b.length);
        }

        void solve(int aLo, int aHi, int bLo, int bHi) {
            while (aLo < aHi && bLo < bHi && a[aLo] == b[bLo]) { aLo++; bLo++; }      // common start
            while (aLo < aHi && bLo < bHi && a[aHi - 1] == b[bHi - 1]) { aHi--; bHi--; } // common end
            if (aLo == aHi) { for (int j = bLo; j < bHi; j++) ins[j] = true; return; }
            if (bLo == bHi) { for (int i = aLo; i < aHi; i++) del[i] = true; return; }
            middleSnake(aLo, aHi, bLo, bHi);
            int x0 = mx0, y0 = my0, x1 = mx1, y1 = my1;
            solve(aLo, x0, bLo, y0);   // part before the snake
            solve(x1, aHi, y1, bHi);   // part after the snake
        }

        // Search from the start (forward) and from the end (backward) at the same time,
        // until the two searches meet. The meeting snake lies on a shortest edit path.
        void middleSnake(int aLo, int aHi, int bLo, int bHi) {
            int n = aHi - aLo, m = bHi - bLo, delta = n - m;
            boolean odd = (delta & 1) != 0;
            int maxD = (n + m + 1) / 2;
            vf[off + 1] = 0;
            vb[off + 1] = 0;
            for (int d = 0; d <= maxD; d++) {
                // forward search
                for (int k = -d; k <= d; k += 2) {
                    int i = off + k, x;
                    if (k == -d || (k != d && vf[i - 1] < vf[i + 1])) x = vf[i + 1]; // insert
                    else x = vf[i - 1] + 1;                                          // delete
                    int y = x - k, xs = x, ys = y;
                    while (x < n && y < m && a[aLo + x] == b[bLo + y]) { x++; y++; } // snake
                    vf[i] = x;
                    int kr = delta - k;
                    if (odd && kr >= -(d - 1) && kr <= d - 1 && x + vb[off + kr] >= n) {
                        mx0 = aLo + xs; my0 = bLo + ys; mx1 = aLo + x; my1 = bLo + y;
                        return;
                    }
                }
                // backward search (the same idea on the reversed sequences)
                for (int k = -d; k <= d; k += 2) {
                    int i = off + k, x;
                    if (k == -d || (k != d && vb[i - 1] < vb[i + 1])) x = vb[i + 1];
                    else x = vb[i - 1] + 1;
                    int y = x - k, xs = x, ys = y;
                    while (x < n && y < m && a[aLo + n - 1 - x] == b[bLo + m - 1 - y]) { x++; y++; }
                    vb[i] = x;
                    int kf = delta - k;
                    if (!odd && kf >= -d && kf <= d && x + vf[off + kf] >= n) {
                        mx0 = aLo + n - x; my0 = bLo + m - y; mx1 = aLo + n - xs; my1 = bLo + m - ys;
                        return;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Reading files
    // ------------------------------------------------------------------

    // Raw bytes, split on '\n'. A final empty piece is dropped. '\r' stays in the line.
    // ISO_8859_1 maps every byte to one char, so line equality is exact byte equality.
    static String[] readLines(String path) throws IOException {
        byte[] data = Files.readAllBytes(Paths.get(path));
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                lines.add(new String(data, start, i - start, StandardCharsets.ISO_8859_1));
                start = i + 1;
            }
        }
        if (start < data.length) {
            lines.add(new String(data, start, data.length - start, StandardCharsets.ISO_8859_1));
        }
        return lines.toArray(new String[0]);
    }

    // ------------------------------------------------------------------
    // Part B helpers
    // ------------------------------------------------------------------

    static int[] codePoints(String isoLine) {
        return new String(isoLine.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)
                .codePoints().toArray();
    }

    // marked positions -> "3-5,9-12" (end not included), or "." if nothing is marked
    static String ranges(boolean[] marked) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < marked.length) {
            if (marked[i]) {
                int s = i;
                while (i < marked.length && marked[i]) i++;
                if (sb.length() > 0) sb.append(',');
                sb.append(s).append('-').append(i);
            } else {
                i++;
            }
        }
        return sb.length() == 0 ? "." : sb.toString();
    }

    static void put(OutputStream out, char prefix, String isoLine) throws IOException {
        out.write(prefix);
        out.write(isoLine.getBytes(StandardCharsets.ISO_8859_1));
        out.write('\n');
    }

    // ------------------------------------------------------------------
    // main
    // ------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        boolean known = args.length == 3 && (args[0].equals("lines") || args[0].equals("highlight"));
        if (!known) {
            System.err.println("usage: Main lines|highlight A_PATH B_PATH");
            System.exit(2);
        }
        boolean highlight = args[0].equals("highlight");

        String[] A, B;
        try {
            A = readLines(args[1]);
            B = readLines(args[2]);
        } catch (IOException | RuntimeException e) {
            System.err.println("cannot read input file: " + e);
            System.exit(2);
            return;
        }

        // Give every distinct line an integer id, so comparing lines is comparing ints.
        HashMap<String, Integer> ids = new HashMap<>();
        int[] a = new int[A.length], b = new int[B.length];
        for (int i = 0; i < A.length; i++) {
            Integer id = ids.get(A[i]);
            if (id == null) { id = ids.size(); ids.put(A[i], id); }
            a[i] = id;
        }
        for (int j = 0; j < B.length; j++) {
            Integer id = ids.get(B[j]);
            if (id == null) { id = ids.size(); ids.put(B[j], id); }
            b[j] = id;
        }

        Diff diff = new Diff(a, b);

        OutputStream out = new BufferedOutputStream(System.out, 1 << 16);
        int i = 0, j = 0, n = A.length, m = B.length;
        while (i < n || j < m) {
            boolean changed = (i < n && diff.del[i]) || (j < m && diff.ins[j]);
            if (!changed) {                       // keep line (a[i] equals b[j])
                put(out, ' ', A[i]);
                i++; j++;
                continue;
            }
            // one change block: all '-' first, then all '+'
            int di = i;
            while (i < n && diff.del[i]) { put(out, '-', A[i]); i++; }
            int ij = j;
            while (j < m && diff.ins[j]) j++;
            int pairs = Math.min(i - di, j - ij);   // 1st "-" pairs with 1st "+", and so on
            for (int p = ij; p < j; p++) {
                put(out, '+', B[p]);
                if (highlight && p - ij < pairs) {
                    Diff cd = new Diff(codePoints(A[di + (p - ij)]), codePoints(B[p]));
                    String line = "? " + ranges(cd.del) + " | " + ranges(cd.ins) + "\n";
                    out.write(line.getBytes(StandardCharsets.ISO_8859_1));
                }
            }
        }
        out.flush();
    }
}