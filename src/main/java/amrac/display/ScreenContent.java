package amrac.display;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class ScreenContent {
    public enum Kind {
        NONE,
        CHART,
        MAP,
        GPS
    }

    public static final int MAP_RESOLUTION = 64;

    public static final int MAX_BLIPS = 128;

    public static final int ACTIVE_MISSILE_BLIP = 5;

    public record Blip(int kind, double x, double z, String name) {
    }

    private Kind kind = Kind.NONE;
    private ChartData chart = ChartData.EMPTY;
    private int[] map = new int[0];
    private double mapCentreX;
    private double mapCentreZ;
    private double mapSpan;
    private String title = "";
    private java.util.List<Blip> blips = java.util.List.of();

    public Kind kind() {
        return kind;
    }

    public ChartData chart() {
        return chart;
    }

    public int[] map() {
        return map;
    }

    public double mapCentreX() {
        return mapCentreX;
    }

    public double mapCentreZ() {
        return mapCentreZ;
    }

    public double mapSpan() {
        return mapSpan;
    }

    public String title() {
        return title;
    }

    public java.util.List<Blip> blips() {
        return blips;
    }

    public void clear() {
        kind = Kind.NONE;
        chart = ChartData.EMPTY;
        map = new int[0];
        blips = java.util.List.of();
        title = "";
    }

    public void showChart(ChartData data) {
        kind = Kind.CHART;
        chart = data;
        title = data.title();
    }

    public void showMap(int[] squares, double centreX, double centreZ,
                        double span, String title) {
        kind = Kind.MAP;
        map = squares;
        mapCentreX = centreX;
        mapCentreZ = centreZ;
        mapSpan = span;
        this.title = title;
    }

    public void showGps(String title, java.util.List<Blip> contacts,
                        double centreX, double centreZ, double span) {
        kind = Kind.GPS;
        blips = contacts.size() > MAX_BLIPS
            ? java.util.List.copyOf(contacts.subList(0, MAX_BLIPS))
            : java.util.List.copyOf(contacts);
        mapCentreX = centreX;
        mapCentreZ = centreZ;
        mapSpan = span;
        this.title = title;
    }

    public void save(ValueOutput output) {
        output.putString("Kind", kind.name());
        output.putString("Title", title);
        if (kind == Kind.CHART && !chart.isEmpty()) {
            output.putString("ChartTitle", chart.title());
            output.putDouble("ChartXMin", chart.xMin());
            output.putDouble("ChartXMax", chart.xMax());
            output.putDouble("ChartYMin", chart.yMin());
            output.putDouble("ChartYMax", chart.yMax());
            output.putString("ChartX", chart.xLabel());
            output.putString("ChartY", chart.yLabel());
            output.putString("ChartNote", chart.note());
            int count = chart.size();
            output.putInt("Points", count);
            for (int i = 0; i < count; i++) {
                output.putDouble("X" + i, chart.xs()[i]);
                output.putDouble("Y" + i, chart.ys()[i]);
            }
        }
        if (kind == Kind.MAP && map.length > 0) {
            output.putInt("MapSize", map.length);
            output.putDouble("MapX", mapCentreX);
            output.putDouble("MapZ", mapCentreZ);
            output.putDouble("MapSpan", mapSpan);
            for (int i = 0; i < map.length; i++) {
                output.putInt("M" + i, map[i]);
            }
        }
        if (kind == Kind.GPS) {
            output.putDouble("MapX", mapCentreX);
            output.putDouble("MapZ", mapCentreZ);
            output.putDouble("MapSpan", mapSpan);
            output.putInt("Blips", blips.size());
            for (int i = 0; i < blips.size(); i++) {
                Blip blip = blips.get(i);
                output.putInt("BK" + i, blip.kind());
                output.putDouble("BX" + i, blip.x());
                output.putDouble("BZ" + i, blip.z());
                output.putString("BN" + i, blip.name());
            }
        }
    }

    public void load(ValueInput input) {
        kind = kindOf(input.getStringOr("Kind", Kind.NONE.name()));
        title = input.getStringOr("Title", "");
        chart = ChartData.EMPTY;
        map = new int[0];
        blips = java.util.List.of();
        if (kind == Kind.CHART) {
            int count = Math.max(0, Math.min(input.getIntOr("Points", 0), 4096));
            double[] xs = new double[count];
            double[] ys = new double[count];
            for (int i = 0; i < count; i++) {
                xs[i] = input.getDoubleOr("X" + i, 0.0D);
                ys[i] = input.getDoubleOr("Y" + i, 0.0D);
            }
            String note = input.getStringOr("ChartNote", "");
            if (note.startsWith("launched at ")) {
                note = "";
            }
            chart = new ChartData(input.getStringOr("ChartTitle", ""),
                input.getStringOr("ChartX", ""), input.getStringOr("ChartY", ""),
                xs, ys, note)
                .pinned(input.getDoubleOr("ChartXMin", Double.NaN),
                    input.getDoubleOr("ChartXMax", Double.NaN),
                    input.getDoubleOr("ChartYMin", Double.NaN),
                    input.getDoubleOr("ChartYMax", Double.NaN));
        } else if (kind == Kind.MAP) {
            int size = Math.max(0, Math.min(input.getIntOr("MapSize", 0),
                MAP_RESOLUTION * MAP_RESOLUTION));
            map = new int[size];
            for (int i = 0; i < size; i++) {
                map[i] = input.getIntOr("M" + i, 0);
            }
            mapCentreX = input.getDoubleOr("MapX", 0.0D);
            mapCentreZ = input.getDoubleOr("MapZ", 0.0D);
            mapSpan = input.getDoubleOr("MapSpan", 1.0D);
        } else if (kind == Kind.GPS) {
            mapCentreX = input.getDoubleOr("MapX", 0.0D);
            mapCentreZ = input.getDoubleOr("MapZ", 0.0D);
            mapSpan = input.getDoubleOr("MapSpan", 1.0D);
            int count = Math.max(0, Math.min(input.getIntOr("Blips", 0),
                MAX_BLIPS));
            java.util.List<Blip> loaded = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                loaded.add(new Blip(input.getIntOr("BK" + i, 0),
                    input.getDoubleOr("BX" + i, 0.0D),
                    input.getDoubleOr("BZ" + i, 0.0D),
                    input.getStringOr("BN" + i, "")));
            }
            blips = java.util.List.copyOf(loaded);
        }
    }

    private static Kind kindOf(String name) {
        for (Kind value : Kind.values()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return Kind.NONE;
    }
}
