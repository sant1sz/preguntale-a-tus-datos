package com.sant1sz.preguntale.datos.simulacion;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.sant1sz.preguntale.datos.modelo.MedioPago;
import com.sant1sz.preguntale.datos.simulacion.DatosSimulados.CategoriaFila;
import com.sant1sz.preguntale.datos.simulacion.DatosSimulados.DetalleFila;
import com.sant1sz.preguntale.datos.simulacion.DatosSimulados.LoteFila;
import com.sant1sz.preguntale.datos.simulacion.DatosSimulados.ProductoFila;
import com.sant1sz.preguntale.datos.simulacion.DatosSimulados.VentaFila;

/**
 * Genera la historia de un supermercado de barrio: ventas por día y hora, inventario y lotes.
 *
 * <p>Reglas que siguen los datos, para que las preguntas al asistente tengan respuestas interesantes:
 * <ul>
 *   <li>Los sábados y domingos se vende más que entre semana.</li>
 *   <li>En días de quincena (1, 2, 15, 16, 30 y 31) se vende más.</li>
 *   <li>Hay horas pico en la mañana, al mediodía y entre 6 y 7 de la noche.</li>
 *   <li>Algunos productos suben o bajan de ventas con el tiempo ({@link Tendencia}).</li>
 *   <li>Algunos productos quedan con stock bajo o agotados, y hay lotes vencidos o por vencer.</li>
 * </ul>
 *
 * <p>Con la misma semilla siempre produce los mismos datos.
 */
public class SimuladorSupermercado {

    static final int VENTAS_BASE_POR_DIA = 55;

    /** Peso de cada hora del día (0 = cerrado). Abre a las 7:00 y cierra a las 22:00. */
    private static final int[] PESO_HORA = {
            0, 0, 0, 0, 0, 0, 0, 3, 6, 5, 4, 5, 7, 7, 4, 3, 4, 6, 8, 8, 5, 2, 0, 0 };

    private final Random random;
    private final List<ProductoBase> catalogo;

    public SimuladorSupermercado(long semilla, List<ProductoBase> catalogo) {
        this.random = new Random(semilla);
        this.catalogo = List.copyOf(catalogo);
    }

    /**
     * @param ahora         momento actual: no se generan ventas después de él
     * @param diasHistorial cuántos días hacia atrás se simulan
     */
    public DatosSimulados simular(LocalDateTime ahora, int diasHistorial) {
        if (diasHistorial < 1) {
            throw new IllegalArgumentException("diasHistorial debe ser al menos 1");
        }
        List<CategoriaFila> categorias = crearCategorias();
        List<ProductoFila> productos = new ArrayList<>();
        List<VentaFila> ventas = new ArrayList<>();
        List<DetalleFila> detalles = new ArrayList<>();

        long[] costos = new long[catalogo.size()];
        for (int i = 0; i < catalogo.size(); i++) {
            double margen = 0.68 + random.nextDouble() * 0.14;
            costos[i] = Math.round(catalogo.get(i).precio() * margen / 10.0) * 10;
        }

        LocalDate hoy = ahora.toLocalDate();
        LocalDate inicio = hoy.minusDays(diasHistorial);
        for (LocalDate dia = inicio; !dia.isAfter(hoy); dia = dia.plusDays(1)) {
            double progreso = (double) ChronoUnit.DAYS.between(inicio, dia) / diasHistorial;
            double[] pesos = pesosDelDia(progreso);
            for (LocalDateTime momento : momentosDeVenta(dia)) {
                if (momento.isAfter(ahora)) {
                    continue;
                }
                long ventaId = ventas.size() + 1L;
                long total = 0;
                for (Map.Entry<Integer, Integer> item : armarCanasta(pesos).entrySet()) {
                    ProductoBase producto = catalogo.get(item.getKey());
                    int cantidad = item.getValue();
                    long subtotal = producto.precio() * cantidad;
                    total += subtotal;
                    detalles.add(new DetalleFila(detalles.size() + 1L, ventaId, item.getKey() + 1L,
                            cantidad, producto.precio(), subtotal));
                }
                ventas.add(new VentaFila(ventaId, momento, total, medioDePago()));
            }
        }

        List<LoteFila> lotes = new ArrayList<>();
        for (int i = 0; i < catalogo.size(); i++) {
            ProductoBase base = catalogo.get(i);
            long productoId = i + 1L;
            int minimo = Math.max(5, (int) Math.round(base.popularidad() * 4));
            int stock = switch (base.estadoStock()) {
                case AGOTADO -> 0;
                case BAJO -> 1 + random.nextInt(minimo - 1);
                case NORMAL -> minimo + random.nextInt(minimo * 3 + 1);
            };
            productos.add(new ProductoFila(productoId, base.nombre(), idCategoria(categorias, base.categoria()),
                    base.precio(), costos[i], stock, minimo, base.perecedero()));
            crearLotes(base, productoId, stock, hoy, lotes);
        }

        return new DatosSimulados(categorias, productos, ventas, detalles, lotes);
    }

    static boolean esQuincena(LocalDate dia) {
        int d = dia.getDayOfMonth();
        return d == 1 || d == 2 || d == 15 || d == 16 || d >= 30;
    }

    static double factorDiaSemana(DayOfWeek dia) {
        return switch (dia) {
            case MONDAY, TUESDAY -> 0.85;
            case WEDNESDAY -> 0.9;
            case THURSDAY -> 0.95;
            case FRIDAY -> 1.15;
            case SATURDAY -> 1.4;
            case SUNDAY -> 1.25;
        };
    }

    private List<CategoriaFila> crearCategorias() {
        List<CategoriaFila> categorias = new ArrayList<>();
        for (ProductoBase p : catalogo) {
            if (categorias.stream().noneMatch(c -> c.nombre().equals(p.categoria()))) {
                categorias.add(new CategoriaFila(categorias.size() + 1L, p.categoria()));
            }
        }
        return categorias;
    }

    private static long idCategoria(List<CategoriaFila> categorias, String nombre) {
        return categorias.stream().filter(c -> c.nombre().equals(nombre)).findFirst().orElseThrow().id();
    }

    private double[] pesosDelDia(double progreso) {
        double[] pesos = new double[catalogo.size()];
        for (int i = 0; i < pesos.length; i++) {
            ProductoBase p = catalogo.get(i);
            pesos[i] = p.popularidad() * p.tendencia().factor(progreso);
        }
        return pesos;
    }

    private List<LocalDateTime> momentosDeVenta(LocalDate dia) {
        double factor = factorDiaSemana(dia.getDayOfWeek()) * (esQuincena(dia) ? 1.3 : 1.0)
                * (0.9 + random.nextDouble() * 0.2);
        int cantidad = (int) Math.round(VENTAS_BASE_POR_DIA * factor);
        List<LocalDateTime> momentos = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) {
            momentos.add(dia.atTime(LocalTime.of(horaAleatoria(), random.nextInt(60), random.nextInt(60))));
        }
        momentos.sort(null);
        return momentos;
    }

    private int horaAleatoria() {
        int total = 0;
        for (int peso : PESO_HORA) {
            total += peso;
        }
        int r = random.nextInt(total);
        for (int hora = 0; hora < PESO_HORA.length; hora++) {
            r -= PESO_HORA[hora];
            if (r < 0) {
                return hora;
            }
        }
        throw new IllegalStateException("Pesos por hora inválidos");
    }

    /** @return índice del producto en el catálogo → unidades */
    private Map<Integer, Integer> armarCanasta(double[] pesos) {
        int productosDistintos = 1 + random.nextInt(3) + (random.nextDouble() < 0.35 ? random.nextInt(4) : 0);
        Map<Integer, Integer> canasta = new LinkedHashMap<>();
        for (int i = 0; i < productosDistintos; i++) {
            double r = random.nextDouble();
            int unidades = r < 0.7 ? 1 : r < 0.92 ? 2 : 3;
            canasta.merge(elegirProducto(pesos), unidades, Integer::sum);
        }
        return canasta;
    }

    private int elegirProducto(double[] pesos) {
        double total = 0;
        for (double peso : pesos) {
            total += peso;
        }
        double r = random.nextDouble() * total;
        for (int i = 0; i < pesos.length; i++) {
            r -= pesos[i];
            if (r < 0) {
                return i;
            }
        }
        return pesos.length - 1;
    }

    private MedioPago medioDePago() {
        double r = random.nextDouble();
        return r < 0.45 ? MedioPago.EFECTIVO : r < 0.75 ? MedioPago.NEQUI : MedioPago.TARJETA;
    }

    private void crearLotes(ProductoBase base, long productoId, int stock, LocalDate hoy, List<LoteFila> lotes) {
        if (stock == 0) {
            return;
        }
        int cantidadLotes = stock < 3 ? 1 : 1 + random.nextInt(base.perecedero() ? 3 : 2);
        int[] partes = repartir(stock, cantidadLotes);
        for (int k = 0; k < cantidadLotes; k++) {
            int dias;
            if (k == 0 && base.vencimientoForzado() != null) {
                dias = base.vencimientoForzado();
            } else if (base.perecedero()) {
                dias = 1 + random.nextInt(base.vidaUtilDias());
            } else {
                dias = 90 + random.nextInt(276);
            }
            String codigo = "L%03d-%d".formatted(productoId, k + 1);
            lotes.add(new LoteFila(lotes.size() + 1L, productoId, codigo, partes[k], hoy.plusDays(dias)));
        }
    }

    /** Reparte {@code total} unidades en {@code partes} grupos de al menos 1. */
    private int[] repartir(int total, int partes) {
        int[] resultado = new int[partes];
        int restante = total;
        for (int i = 0; i < partes - 1; i++) {
            int maximo = restante - (partes - 1 - i);
            resultado[i] = 1 + random.nextInt(Math.max(1, maximo / 2));
            restante -= resultado[i];
        }
        resultado[partes - 1] = restante;
        return resultado;
    }
}
