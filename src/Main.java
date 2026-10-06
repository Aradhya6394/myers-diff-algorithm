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
    // I first check the command-line arguments before processing the files.
    public static void main(String[] args) throws IOException {
        boolean known = args.length == 3 && (args[0].equals("lines") || args[0].equals("highlight"));
        if (!known) {
            System.err.println("usage: Main lines|highlight A_PATH B_PATH");
            System.exit(2);
        }
        // "highlight" mode also shows which characters changed inside a line.
        boolean highlight = args[0].equals("highlight");
        String[] linesA;
        String[] linesB;
        try {
            linesA = readLines(args[1]);
            linesB = readLines(args[2]);
        } catch (IOException | RuntimeException e) {
            System.err.println("cannot read input file: " + e);
            System.exit(2);
            return;
        }
        // I give each different line a number, so Myers can compare integers instead of strings.
        HashMap<String, Integer> lineIds = new HashMap<>();
        int[] idsA = toIds(linesA, lineIds);
        int[] idsB = toIds(linesB, lineIds);
        Myers diff = new Myers(idsA, idsB);
        OutputStream out = new BufferedOutputStream(System.out, 1 << 16);
        printDiff(out, linesA, linesB, diff, highlight);
        out.flush();
    }
    // Reads the file and stores it line by line.
    static String[] readLines(String path) throws IOException {
        // I read the complete file as bytes so the original file content is kept.
        byte[] data = Files.readAllBytes(Paths.get(path));
        List<String> lines = new ArrayList<>();
        int start = 0;
        // I split the file at every newline and store each line separately.
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
    // Converts each line into its ID from the common map.
    static int[] toIds(String[] lines, HashMap<String, Integer> lineIds) {
        int[] ids = new int[lines.length];
        for (int i = 0; i < lines.length; i++) {
            Integer id = lineIds.get(lines[i]);
            if (id == null) {
                id = lineIds.size();
                lineIds.put(lines[i], id);
            }
            ids[i] = id;
        }
        return ids;
    }
    // Prints unchanged, deleted, and inserted lines using the usual diff symbols.
    static void printDiff(OutputStream out, String[] linesA, String[] linesB,
                          Myers diff, boolean highlight) throws IOException {
        // i and j keep track of the current positions in the two files.
        int i = 0;
        int j = 0;
        while (i < linesA.length || j < linesB.length) {
            boolean isChange = (i < linesA.length && diff.deleted[i])
                            || (j < linesB.length && diff.inserted[j]);
            // If both lines are unchanged, I print the line with a space.
            if (!isChange) {
                printLine(out, ' ', linesA[i]);
                i++;
                j++;
                continue;
            }
            int firstDeleted = i;
            while (i < linesA.length && diff.deleted[i]) {
                printLine(out, '-', linesA[i]);
                i++;
            }
            int firstInserted = j;
            while (j < linesB.length && diff.inserted[j]) {
                printLine(out, '+', linesB[j]);
                if (highlight) {
                    int pairNumber = j - firstInserted;
                    boolean hasPartner = firstDeleted + pairNumber < i;
                    if (hasPartner) {
                        printHighlight(out, linesA[firstDeleted + pairNumber], linesB[j]);
                    }
                }
                j++;
            }
        }
    }
    // In highlight mode, I compare the characters of a changed pair of lines.
    static void printHighlight(OutputStream out, String oldLine, String newLine) throws IOException {
        Myers charDiff = new Myers(toCodePoints(oldLine), toCodePoints(newLine));
        String text = "? " + toRanges(charDiff.deleted) + " | " + toRanges(charDiff.inserted) + "\n";
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
    }
    // I convert the line into Unicode code points so characters can be compared.
    static int[] toCodePoints(String line) {
        byte[] bytes = line.getBytes(StandardCharsets.ISO_8859_1);
        return new String(bytes, StandardCharsets.UTF_8).codePoints().toArray();
    }
    // Converts changed character positions into simple ranges such as 3-5.
    static String toRanges(boolean[] marked) {
        StringBuilder ranges = new StringBuilder();
        int i = 0;
        while (i < marked.length) {
            if (!marked[i]) {
                i++;
                continue;
            }
            int start = i;
            while (i < marked.length && marked[i]) {
                i++;
            }
            if (ranges.length() > 0) {
                ranges.append(',');
            }
            ranges.append(start).append('-').append(i);
        }
        return ranges.length() == 0 ? "." : ranges.toString();
    }
    // Prints one line with its diff prefix: space, - or +.
    static void printLine(OutputStream out, char prefix, String line) throws IOException {
        out.write(prefix);
        out.write(line.getBytes(StandardCharsets.ISO_8859_1));
        out.write('\n');
    }
}
// This class implements the Myers diff algorithm used to find the smallest set of changes.
// I use it once for complete lines and again for characters inside a changed line.
class Myers {
    final int[] a;
    final int[] b;
    final boolean[] deleted;
    final boolean[] inserted;
    private final int[] furthestFromStart;
    private final int[] furthestFromEnd;
    private final int offset;
    private int aStart;
    private int bStart;
    private int lengthA;
    private int lengthB;
    private int delta;
    private boolean deltaIsOdd;
    private int snakeStartX, snakeStartY, snakeEndX, snakeEndY;
    Myers(int[] a, int[] b) {
        this.a = a;
        this.b = b;
        this.deleted = new boolean[a.length];
        this.inserted = new boolean[b.length];
        this.offset = (a.length + b.length) / 2 + 2;
        this.furthestFromStart = new int[2 * offset + 3];
        this.furthestFromEnd = new int[2 * offset + 3];
        findEdits(0, a.length, 0, b.length);
    }
    // Finds which elements are deleted or inserted in the current part of the arrays.
    private void findEdits(int aFrom, int aTo, int bFrom, int bTo) {
        while (aFrom < aTo && bFrom < bTo && a[aFrom] == b[bFrom]) {
            aFrom++;
            bFrom++;
        }
        while (aFrom < aTo && bFrom < bTo && a[aTo - 1] == b[bTo - 1]) {
            aTo--;
            bTo--;
        }
        // If A is empty, all remaining elements in B are insertions.
        if (aFrom == aTo) {
            for (int j = bFrom; j < bTo; j++) {
                inserted[j] = true;
            }
            return;
        }
        // If B is empty, all remaining elements in A are deletions.
        if (bFrom == bTo) {
            for (int i = aFrom; i < aTo; i++) {
                deleted[i] = true;
            }
            return;
        }
        findMiddleSnake(aFrom, aTo, bFrom, bTo);
        int x1 = snakeStartX, y1 = snakeStartY, x2 = snakeEndX, y2 = snakeEndY;
        findEdits(aFrom, x1, bFrom, y1);
        findEdits(x2, aTo, y2, bTo);
    }
    // Searches from both sides to find the middle part where the sequences match.
    private void findMiddleSnake(int aFrom, int aTo, int bFrom, int bTo) {
        aStart = aFrom;
        bStart = bFrom;
        lengthA = aTo - aFrom;
        lengthB = bTo - bFrom;
        delta = lengthA - lengthB;
        deltaIsOdd = (delta & 1) != 0;
        furthestFromStart[offset + 1] = 0;
        furthestFromEnd[offset + 1] = 0;
        int maxRounds = (lengthA + lengthB + 1) / 2;
        for (int d = 0; d <= maxRounds; d++) {
            if (searchFromStart(d)) return;
            if (searchFromEnd(d)) return;
        }
    }
    private boolean searchFromStart(int d) {
        for (int k = -d; k <= d; k += 2) {
            int x = startingX(furthestFromStart, k, d);
            int y = x - k;
            int snakeFromX = x;
            int snakeFromY = y;
            // Matching elements can move diagonally without adding a change.
            while (x < lengthA && y < lengthB && a[aStart + x] == b[bStart + y]) {
                x++;
                y++;
            }
            furthestFromStart[offset + k] = x;
            int kFromEnd = delta - k;
            boolean otherSearchWasHere = kFromEnd >= -(d - 1) && kFromEnd <= d - 1;
            if (deltaIsOdd && otherSearchWasHere
                    && x + furthestFromEnd[offset + kFromEnd] >= lengthA) {
                snakeStartX = aStart + snakeFromX;
                snakeStartY = bStart + snakeFromY;
                snakeEndX = aStart + x;
                snakeEndY = bStart + y;
                return true;
            }
        }
        return false;
    }
    private boolean searchFromEnd(int d) {
        for (int k = -d; k <= d; k += 2) {
            int x = startingX(furthestFromEnd, k, d);
            int y = x - k;
            int snakeFromX = x;
            int snakeFromY = y;
            while (x < lengthA && y < lengthB
                    && a[aStart + lengthA - 1 - x] == b[bStart + lengthB - 1 - y]) {
                x++;
                y++;
            }
            furthestFromEnd[offset + k] = x;
            int kFromStart = delta - k;
            boolean otherSearchIsHere = kFromStart >= -d && kFromStart <= d;
            if (!deltaIsOdd && otherSearchIsHere
                    && x + furthestFromStart[offset + kFromStart] >= lengthA) {
                snakeStartX = aStart + lengthA - x;
                snakeStartY = bStart + lengthB - y;
                snakeEndX = aStart + lengthA - snakeFromX;
                snakeEndY = bStart + lengthB - snakeFromY;
                return true;
            }
        }
        return false;
    }
    // Chooses the better previous path for the current diagonal.
    private int startingX(int[] furthest, int k, int d) {
        boolean comesFromInsert = (k == -d)
                || (k != d && furthest[offset + k - 1] < furthest[offset + k + 1]);
        if (comesFromInsert) {
            return furthest[offset + k + 1];
        }
        return furthest[offset + k - 1] + 1;
    }
}
