package amrac.platform.client;

import amrac.platform.Event;

public final class LevelExtractionEvents {
    public static final Event<EndExtraction> END_EXTRACTION = new Event<>();

    private LevelExtractionEvents() {
    }

    @FunctionalInterface
    public interface EndExtraction {
        void endExtraction(LevelExtractionContext context);
    }
}
