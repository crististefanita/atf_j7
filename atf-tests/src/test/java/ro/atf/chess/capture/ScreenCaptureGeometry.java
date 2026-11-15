package ro.atf.chess.capture;

public record ScreenCaptureGeometry(int logicalWidth, int logicalHeight, int physicalWidth, int physicalHeight) {
    public ScreenCaptureGeometry {
        if (logicalWidth <= 0 || logicalHeight <= 0 || physicalWidth <= 0 || physicalHeight <= 0) {
            throw new IllegalArgumentException("Display dimensions must be positive");
        }
    }

    public boolean useFullDisplay(int sdk) {
        // API 28 crops the physical screenshot using the logical display bounds.
        return sdk == 28 && (logicalWidth != physicalWidth || logicalHeight != physicalHeight);
    }

    public boolean accepts(int sdk, int imageWidth, int imageHeight) {
        if (useFullDisplay(sdk)) {
            return imageWidth == physicalWidth && imageHeight == physicalHeight
                    && (long) logicalWidth * physicalHeight == (long) logicalHeight * physicalWidth;
        }
        return imageWidth == logicalWidth && imageHeight == logicalHeight;
    }
}
