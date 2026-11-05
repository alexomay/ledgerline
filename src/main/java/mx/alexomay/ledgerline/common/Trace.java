package mx.alexomay.ledgerline.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Traza de una operación: qué pasos ejecutó el servidor y cuánto tardó cada
 * uno. Se devuelve al cliente para que la interfaz muestre lo que ocurrió por
 * dentro. Los tiempos son reales, medidos con {@link System#nanoTime()}.
 */
public final class Trace {

    public record Step(String name, String detail, double ms) {}

    private final List<Step> steps = new ArrayList<>();
    private final long start = System.nanoTime();
    private long last = start;

    public void mark(String name, String detail) {
        long now = System.nanoTime();
        steps.add(new Step(name, detail, round((now - last) / 1e6)));
        last = now;
    }

    /** Agrega un paso medido por fuera (por ejemplo, el commit, que ocurre al salir del servicio). */
    public void add(String name, String detail, double ms) {
        steps.add(new Step(name, detail, round(ms)));
    }

    public double elapsedMs() {
        return (System.nanoTime() - start) / 1e6;
    }

    public List<Step> steps() {
        return Collections.unmodifiableList(steps);
    }

    public double totalMs() {
        return round(steps.stream().mapToDouble(Step::ms).sum());
    }

    private static double round(double ms) {
        return Math.round(ms * 100) / 100.0;
    }
}
