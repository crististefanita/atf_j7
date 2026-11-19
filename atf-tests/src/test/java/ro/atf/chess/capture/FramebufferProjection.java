package ro.atf.chess.capture;

import java.util.HashSet;
import java.util.regex.Pattern;

/** Calibrates API 28 pixels from the primary viewport, including an explicitly observed quarter turn. */
public record FramebufferProjection(int logicalWidth, int logicalHeight, int physicalWidth, int physicalHeight,
                                    int left, int top, int right, int bottom) {
    private static final Pattern VIEWPORT = Pattern.compile("Viewport: displayId=0, orientation=(\\d), (?:uniqueId='[^']*', )?"
            + "logicalFrame=\\[(\\d+), (\\d+), (\\d+), (\\d+)\\], physicalFrame=\\[(\\d+), (\\d+), (\\d+), (\\d+)\\], "
            + "deviceSize=\\[(\\d+), (\\d+)\\]");

    public FramebufferProjection {
        if (logicalWidth <= 0 || logicalHeight <= 0 || physicalWidth <= 0 || physicalHeight <= 0) {
            throw new IllegalArgumentException("Positive display dimensions required");
        }
        boolean widthLimited = (long) physicalWidth * logicalHeight <= (long) physicalHeight * logicalWidth;
        int width = widthLimited ? physicalWidth : (int) ((long) physicalHeight * logicalWidth / logicalHeight);
        int height = widthLimited ? (int) ((long) physicalWidth * logicalHeight / logicalWidth) : physicalHeight;
        if (width <= 0 || height <= 0 || left != (physicalWidth - width) / 2 || top != (physicalHeight - height) / 2
                || right != left + width || bottom != top + height) {
            throw new IllegalArgumentException("The reported viewport is not the complete centered compositor projection");
        }
    }

    public static FramebufferProjection fromInputDump(String dump, int width, int height, int physicalWidth, int physicalHeight) {
        return fromInputDump(dump, width, height, physicalWidth, physicalHeight, 0);
    }

    public static FramebufferProjection fromInputDump(String dump, int width, int height, int physicalWidth, int physicalHeight,
                                                     int rotation) {
        if (rotation != 0 && rotation != 1) throw new IllegalArgumentException("Only observed rotations 0 and 90 are supported");
        var found = new HashSet<FramebufferProjection>();
        var matches = VIEWPORT.matcher(dump);
        while (matches.find()) {
            int[] value = new int[12];
            for (int group = 1; group <= 11; group++) value[group] = Integer.parseInt(matches.group(group));
            // DisplayDevice.populateViewportLocked reports dimensions and physicalFrame post-orientation.
            if (value[1] != rotation || value[2] != 0 || value[3] != 0 || value[4] != width || value[5] != height
                    || value[10] != physicalWidth || value[11] != physicalHeight) {
                throw new IllegalArgumentException("Input viewport does not match the observed display and rotation");
            }
            found.add(new FramebufferProjection(width, height, physicalWidth, physicalHeight, value[6], value[7], value[8], value[9]));
        }
        if (found.size() != 1) throw new IllegalArgumentException("Missing or ambiguous primary input viewport");
        return found.iterator().next();
    }

    public boolean accepts(int width, int height) { return width == physicalWidth && height == physicalHeight; }

    public int[] project(int x1, int y1, int x2, int y2) {
        if (x1 < 0 || y1 < 0 || x2 < x1 || y2 < y1 || x2 > logicalWidth || y2 > logicalHeight) {
            throw new IllegalArgumentException("Rectangle leaves the logical display");
        }
        return new int[]{left + (int) ((long) x1 * (right - left) / logicalWidth),
                top + (int) ((long) y1 * (bottom - top) / logicalHeight),
                left + (int) (((long) x2 * (right - left) + logicalWidth - 1) / logicalWidth),
                top + (int) (((long) y2 * (bottom - top) + logicalHeight - 1) / logicalHeight)};
    }
}
