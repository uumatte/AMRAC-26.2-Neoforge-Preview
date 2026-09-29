package amrac.display;

public final class ConsoleLayout {
    private ConsoleLayout() {
    }

    public static final int WIDTH = 256;
    public static final int HEIGHT = 240;

    public static final int MARGIN = 8;
    public static final int COLUMN_TWO = 132;
    public static final int COLUMN_TWO_WIDTH = WIDTH - MARGIN - COLUMN_TWO;

    public static final int TITLE_Y = 5;

    public static final int TABS_Y = 15;
    public static final int TAB_HEIGHT = 14;
    public static final int TAB_GAP = 2;

    public static final int SLOT_X = 9;
    public static final int SLOT_Y = 34;
    public static final int SLOT_WELL_X = SLOT_X - 1;
    public static final int SLOT_WELL_Y = SLOT_Y - 1;
    public static final int SLOT_WELL_SIZE = 18;

    public static final int CONNECT_X = 32;
    public static final int CONNECT_Y = 34;
    public static final int CONNECT_WIDTH = 58;
    public static final int CONNECT_HEIGHT = 14;

    public static final int SCREEN_TEXT_X = 96;
    public static final int SCREEN_TEXT_Y = 38;
    public static final int DOCUMENT_TEXT_Y = 48;

    public static final int PARAM_LABEL_Y = 58;
    public static final int PARAM_BOX_X = MARGIN;
    public static final int PARAM_BOX_Y = 68;
    public static final int PARAM_BOX_WIDTH = 76;
    public static final int BOX_HEIGHT = 14;

    public static final int ENTER_X = 88;
    public static final int ENTER_Y = 67;
    public static final int ENTER_WIDTH = 36;
    public static final int BUTTON_HEIGHT = 16;

    public static final int VALUE_LABEL_Y = 86;

    public static final int VALUE_BOX_X = MARGIN;
    public static final int VALUE_BOX_Y = 96;
    public static final int VALUE_BOX_WIDTH = 76;
    public static final int VALUE_BOX_ROWS = 5;
    public static final int VALUE_ROW_HEIGHT = 9;
    public static final int VALUE_BOX_HEIGHT =
        VALUE_BOX_ROWS * VALUE_ROW_HEIGHT + 8;

    public static final int APPLY_X = 88;
    public static final int APPLY_Y = VALUE_BOX_Y;
    public static final int APPLY_WIDTH = 36;
    public static final int CANCEL_X = APPLY_X;
    public static final int CANCEL_Y = VALUE_BOX_Y + BUTTON_HEIGHT + 3;
    public static final int CANCEL_WIDTH = APPLY_WIDTH;

    public static final int CHART_LABEL_Y = 58;
    public static final int CHART_BUTTON_X = COLUMN_TWO;
    public static final int CHART_BUTTON_Y = 68;
    public static final int CHART_BUTTON_WIDTH = COLUMN_TWO_WIDTH - 40;

    public static final int AXIS_BUTTON_X = COLUMN_TWO + CHART_BUTTON_WIDTH + 4;
    public static final int AXIS_BUTTON_Y = CHART_BUTTON_Y;
    public static final int AXIS_BUTTON_WIDTH = 36;

    public static final int AXIS_MIN_X = COLUMN_TWO;
    public static final int AXIS_MAX_X = 192;
    public static final int AXIS_X_ROW_Y = 87;
    public static final int AXIS_Y_ROW_Y = 107;
    public static final int AXIS_BOX_WIDTH = 56;

    public static final int ALTITUDE_LABEL_X = COLUMN_TWO;
    public static final int ALTITUDE_LABEL_Y = 90;
    public static final int ALTITUDE_BOX_X = 192;
    public static final int ALTITUDE_BOX_Y = 87;
    public static final int NUMBER_BOX_WIDTH = 56;

    public static final int TARGET_LABEL_X = COLUMN_TWO;
    public static final int TARGET_LABEL_Y = 110;
    public static final int TARGET_BOX_X = 192;
    public static final int TARGET_BOX_Y = 107;

    public static final int DRAW_X = COLUMN_TWO;
    public static final int DRAW_Y = 124;
    public static final int DRAW_WIDTH = COLUMN_TWO_WIDTH;

    public static final int STATUS_X = COLUMN_TWO;
    public static final int STATUS_Y = 144;

    public static final int INVENTORY_X = (WIDTH - 9 * 18) / 2;
    public static final int INVENTORY_Y = 156;
    public static final int HOTBAR_Y = 216;
}
