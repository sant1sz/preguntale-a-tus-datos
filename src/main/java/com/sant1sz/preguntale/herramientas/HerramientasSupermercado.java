package com.sant1sz.preguntale.herramientas;

import java.sql.Date;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Herramientas que el modelo de IA puede llamar para consultar los datos del supermercado.
 * Cada método devuelve datos reales de la base, con los totales, porcentajes y extremos ya calculados,
 * para que el modelo solo tenga que explicarlos y no haga cuentas.
 * Las fechas se reciben como texto AAAA-MM-DD y los rangos incluyen ambos extremos.
 * Los montos están en pesos colombianos, sin decimales.
 */
@Component
public class HerramientasSupermercado {

    private static final Logger log = LoggerFactory.getLogger(HerramientasSupermercado.class);
    private static final Locale ESPANOL = Locale.of("es", "CO");
    private static final int MAXIMO_DIAS_DETALLE = 120;
    private static final int DIAS_PROMEDIO_VENTAS = 28;

    private final JdbcClient jdbc;
    private final Clock reloj;

    public HerramientasSupermercado(JdbcClient jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // ---------- Fecha y catálogo ----------

    @Tool(description = """
            Devuelve la fecha y hora actuales del supermercado y el rango de fechas que tienen ventas registradas.
            Úsala antes de interpretar fechas relativas como 'hoy', 'ayer', 'esta semana' o 'el mes pasado'.""")
    public FechaActual fechaActual() {
        log.info("Herramienta fechaActual");
        LocalDateTime ahora = LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MINUTES);
        LocalDate[] rango = jdbc.sql("SELECT MIN(fecha_hora), MAX(fecha_hora) FROM venta")
                .query((rs, fila) -> new LocalDate[] { aFecha(rs.getTimestamp(1)), aFecha(rs.getTimestamp(2)) })
                .single();
        return new FechaActual(ahora.toLocalDate(), nombreDia(ahora.toLocalDate()), ahora.toLocalTime().toString(),
                rango[0], rango[1]);
    }

    @Tool(description = "Lista las categorías de productos del supermercado con cuántos productos tiene cada una.")
    public List<CategoriaInfo> listarCategorias() {
        log.info("Herramienta listarCategorias");
        return jdbc.sql("""
                SELECT c.nombre, COUNT(p.id)
                FROM categoria c LEFT JOIN producto p ON p.categoria_id = c.id
                GROUP BY c.nombre ORDER BY c.nombre""")
                .query((rs, fila) -> new CategoriaInfo(rs.getString(1), rs.getInt(2)))
                .list();
    }

    // ---------- Ventas ----------

    @Tool(description = """
            Resumen de ventas entre dos fechas: número de ventas, total vendido, ticket promedio,
            ganancia estimada (precio de venta menos costo actual), margen y desglose por medio de pago
            con su porcentaje del total.""")
    public ResumenVentas resumenVentas(
            @ToolParam(description = "Fecha inicial, formato AAAA-MM-DD") String desde,
            @ToolParam(description = "Fecha final (incluida), formato AAAA-MM-DD") String hasta) {
        log.info("Herramienta resumenVentas({}, {})", desde, hasta);
        Rango rango = rango(desde, hasta);

        record Fila(String medio, long ventas, long total) {
        }
        List<Fila> filas = jdbc.sql("""
                SELECT medio_pago, COUNT(*), COALESCE(SUM(total), 0)
                FROM venta WHERE fecha_hora >= ? AND fecha_hora < ?
                GROUP BY medio_pago ORDER BY 3 DESC""")
                .params(rango.inicio(), rango.fin())
                .query((rs, fila) -> new Fila(rs.getString(1), rs.getLong(2), rs.getLong(3)))
                .list();
        long numeroVentas = filas.stream().mapToLong(Fila::ventas).sum();
        long total = filas.stream().mapToLong(Fila::total).sum();
        long ganancia = jdbc.sql("""
                SELECT COALESCE(SUM(d.subtotal - d.cantidad * p.costo), 0)
                FROM detalle_venta d
                JOIN venta v ON v.id = d.venta_id
                JOIN producto p ON p.id = d.producto_id
                WHERE v.fecha_hora >= ? AND v.fecha_hora < ?""")
                .params(rango.inicio(), rango.fin())
                .query(Long.class).single();

        List<VentasMedioPago> porMedio = filas.stream()
                .map(f -> new VentasMedioPago(f.medio(), f.ventas(), f.total(), porcentaje(f.total(), total)))
                .toList();
        return new ResumenVentas(rango.desde(), rango.hasta(), numeroVentas, total,
                numeroVentas == 0 ? 0 : Math.round((double) total / numeroVentas), ganancia,
                porcentaje(ganancia, total), porMedio);
    }

    @Tool(description = """
            Ventas de cada día entre dos fechas (máximo 120 días), con el día de la semana, más el total
            del periodo, el promedio diario y el mejor y el peor día. El día de hoy, si está en el rango,
            va marcado como 'enCurso' y no cuenta para el promedio ni para el mejor y peor día.""")
    public ReporteVentasDiarias ventasPorDia(
            @ToolParam(description = "Fecha inicial, formato AAAA-MM-DD") String desde,
            @ToolParam(description = "Fecha final (incluida), formato AAAA-MM-DD") String hasta) {
        log.info("Herramienta ventasPorDia({}, {})", desde, hasta);
        Rango rango = rango(desde, hasta);
        if (ChronoUnit.DAYS.between(rango.desde(), rango.hasta()) >= MAXIMO_DIAS_DETALLE) {
            throw new IllegalArgumentException("El rango es muy largo: pide como máximo " + MAXIMO_DIAS_DETALLE
                    + " días, o usa resumenVentas para periodos largos.");
        }
        LocalDate hoy = LocalDate.now(reloj);
        List<VentasDia> dias = jdbc.sql("""
                SELECT CAST(fecha_hora AS DATE) AS dia, COUNT(*), SUM(total)
                FROM venta WHERE fecha_hora >= ? AND fecha_hora < ?
                GROUP BY CAST(fecha_hora AS DATE) ORDER BY dia""")
                .params(rango.inicio(), rango.fin())
                .query((rs, fila) -> {
                    LocalDate dia = rs.getDate(1).toLocalDate();
                    return new VentasDia(dia, nombreDia(dia), rs.getLong(2), rs.getLong(3), dia.equals(hoy));
                })
                .list();

        List<VentasDia> completos = dias.stream().filter(d -> !d.enCurso()).toList();
        long promedio = Math.round(completos.stream().mapToLong(VentasDia::total).average().orElse(0));
        return new ReporteVentasDiarias(rango.desde(), rango.hasta(),
                dias.stream().mapToLong(VentasDia::ventas).sum(),
                dias.stream().mapToLong(VentasDia::total).sum(),
                dias.size(), promedio,
                completos.stream().max(Comparator.comparingLong(VentasDia::total)).orElse(null),
                completos.stream().min(Comparator.comparingLong(VentasDia::total)).orElse(null),
                dias);
    }

    @Tool(description = """
            Ventas agrupadas por hora del día (0 a 23) entre dos fechas, con el porcentaje de cada hora,
            la hora pico y la hora más floja. Sirve para saber cuándo hay más clientes.""")
    public ReporteVentasPorHora ventasPorHora(
            @ToolParam(description = "Fecha inicial, formato AAAA-MM-DD") String desde,
            @ToolParam(description = "Fecha final (incluida), formato AAAA-MM-DD") String hasta) {
        log.info("Herramienta ventasPorHora({}, {})", desde, hasta);
        Rango rango = rango(desde, hasta);
        record Fila(int hora, long ventas, long total) {
        }
        List<Fila> filas = jdbc.sql("""
                SELECT EXTRACT(HOUR FROM fecha_hora) AS hora, COUNT(*), SUM(total)
                FROM venta WHERE fecha_hora >= ? AND fecha_hora < ?
                GROUP BY EXTRACT(HOUR FROM fecha_hora) ORDER BY hora""")
                .params(rango.inicio(), rango.fin())
                .query((rs, fila) -> new Fila(rs.getInt(1), rs.getLong(2), rs.getLong(3)))
                .list();
        long total = filas.stream().mapToLong(Fila::total).sum();
        List<VentasHora> horas = filas.stream()
                .map(f -> new VentasHora(f.hora(), f.ventas(), f.total(), porcentaje(f.total(), total)))
                .toList();
        return new ReporteVentasPorHora(rango.desde(), rango.hasta(), total,
                horas.stream().max(Comparator.comparingLong(VentasHora::total)).orElse(null),
                horas.stream().min(Comparator.comparingLong(VentasHora::total)).orElse(null),
                horas);
    }

    @Tool(description = """
            Ventas por categoría entre dos fechas: unidades, total vendido, ganancia estimada y porcentaje
            de las ventas del periodo, ordenadas de mayor a menor, más los totales del periodo.""")
    public ReporteCategorias ventasPorCategoria(
            @ToolParam(description = "Fecha inicial, formato AAAA-MM-DD") String desde,
            @ToolParam(description = "Fecha final (incluida), formato AAAA-MM-DD") String hasta) {
        log.info("Herramienta ventasPorCategoria({}, {})", desde, hasta);
        Rango rango = rango(desde, hasta);
        record Fila(String categoria, long unidades, long total, long ganancia) {
        }
        List<Fila> filas = jdbc.sql("""
                SELECT c.nombre, SUM(d.cantidad), SUM(d.subtotal), SUM(d.subtotal - d.cantidad * p.costo)
                FROM detalle_venta d
                JOIN venta v ON v.id = d.venta_id
                JOIN producto p ON p.id = d.producto_id
                JOIN categoria c ON c.id = p.categoria_id
                WHERE v.fecha_hora >= ? AND v.fecha_hora < ?
                GROUP BY c.nombre ORDER BY 3 DESC""")
                .params(rango.inicio(), rango.fin())
                .query((rs, fila) -> new Fila(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)))
                .list();
        long total = filas.stream().mapToLong(Fila::total).sum();
        return new ReporteCategorias(rango.desde(), rango.hasta(), total,
                filas.stream().mapToLong(Fila::ganancia).sum(),
                filas.stream().map(f -> new VentasCategoria(f.categoria(), f.unidades(), f.total(), f.ganancia(),
                        porcentaje(f.total(), total))).toList());
    }

    @Tool(description = """
            Ranking de productos entre dos fechas: los más vendidos o los menos vendidos
            (incluye productos que no se vendieron nada), por unidades, total vendido o ganancia.
            Cada producto trae su porcentaje de las ventas del periodo. Se puede filtrar por categoría.""")
    public RankingProductos rankingProductos(
            @ToolParam(description = "Fecha inicial, formato AAAA-MM-DD") String desde,
            @ToolParam(description = "Fecha final (incluida), formato AAAA-MM-DD") String hasta,
            @ToolParam(description = "Cómo ordenar: UNIDADES, INGRESOS o GANANCIA") CriterioRanking criterio,
            @ToolParam(description = "MAS_VENDIDOS o MENOS_VENDIDOS") OrdenRanking orden,
            @ToolParam(required = false, description = "Nombre exacto de la categoría para filtrar (opcional)") String categoria,
            @ToolParam(required = false, description = "Cuántos productos devolver, de 1 a 40 (por defecto 10)") Integer limite) {
        log.info("Herramienta rankingProductos({}, {}, {}, {}, {}, {})", desde, hasta, criterio, orden, categoria, limite);
        Rango rango = rango(desde, hasta);
        CriterioRanking porCriterio = criterio == null ? CriterioRanking.UNIDADES : criterio;
        OrdenRanking enOrden = orden == null ? OrdenRanking.MAS_VENDIDOS : orden;
        int cuantos = limite == null ? 10 : Math.clamp(limite, 1, 40);
        String filtroCategoria = categoria == null || categoria.isBlank() ? null : categoria.strip();
        if (filtroCategoria != null && !existeCategoria(filtroCategoria)) {
            throw new IllegalArgumentException("No existe la categoría '" + filtroCategoria
                    + "'. Usa listarCategorias para ver los nombres exactos.");
        }

        record Fila(String producto, String categoria, long unidades, long total, long ganancia) {
        }
        List<Fila> todos = jdbc.sql("""
                SELECT p.nombre, c.nombre, COALESCE(s.unidades, 0), COALESCE(s.total, 0),
                       COALESCE(s.total, 0) - COALESCE(s.unidades, 0) * p.costo
                FROM producto p
                JOIN categoria c ON c.id = p.categoria_id
                LEFT JOIN (
                    SELECT d.producto_id, SUM(d.cantidad) AS unidades, SUM(d.subtotal) AS total
                    FROM detalle_venta d JOIN venta v ON v.id = d.venta_id
                    WHERE v.fecha_hora >= ? AND v.fecha_hora < ?
                    GROUP BY d.producto_id
                ) s ON s.producto_id = p.id""")
                .params(rango.inicio(), rango.fin())
                .query((rs, fila) -> new Fila(rs.getString(1), rs.getString(2),
                        rs.getLong(3), rs.getLong(4), rs.getLong(5)))
                .list();
        long totalPeriodo = todos.stream().mapToLong(Fila::total).sum();

        Comparator<VentasProducto> comparador = Comparator.comparingLong(switch (porCriterio) {
            case UNIDADES -> VentasProducto::unidades;
            case INGRESOS -> VentasProducto::totalVendido;
            case GANANCIA -> VentasProducto::gananciaEstimada;
        });
        if (enOrden == OrdenRanking.MAS_VENDIDOS) {
            comparador = comparador.reversed();
        }
        List<VentasProducto> productos = todos.stream()
                .filter(f -> filtroCategoria == null || f.categoria().equals(filtroCategoria))
                .map(f -> new VentasProducto(f.producto(), f.categoria(), f.unidades(), f.total(), f.ganancia(),
                        porcentaje(f.total(), totalPeriodo)))
                .sorted(comparador.thenComparing(VentasProducto::producto))
                .limit(cuantos)
                .toList();
        return new RankingProductos(rango.desde(), rango.hasta(), porCriterio, enOrden, filtroCategoria,
                totalPeriodo, productos);
    }

    // ---------- Productos e inventario ----------

    @Tool(description = """
            Busca productos por nombre y/o categoría y devuelve precio, costo, margen, stock actual y stock mínimo.
            Sin filtros devuelve todo el catálogo. La búsqueda ignora mayúsculas y tildes.""")
    public List<InfoProducto> buscarProductos(
            @ToolParam(required = false, description = "Palabras del nombre del producto, por ejemplo 'leche' o 'cafe' (opcional)") String texto,
            @ToolParam(required = false, description = "Nombre de la categoría (opcional)") String categoria) {
        log.info("Herramienta buscarProductos({}, {})", texto, categoria);
        List<String> palabras = texto == null ? List.of()
                : Arrays.stream(normalizar(texto).split("\\s+")).filter(p -> !p.isBlank()).toList();
        String categoriaBuscada = categoria == null ? "" : normalizar(categoria);
        return todosLosProductos().stream()
                .filter(p -> palabras.stream().allMatch(normalizar(p.nombre())::contains))
                .filter(p -> categoriaBuscada.isBlank() || normalizar(p.categoria()).contains(categoriaBuscada))
                .toList();
    }

    @Tool(description = """
            Productos con stock por debajo del mínimo (incluye agotados), con su venta diaria promedio
            de los últimos 28 días, para cuántos días alcanza el stock y cuántas unidades faltan para
            llegar al mínimo. Trae también cuántos productos hay en alerta y cuántos están agotados.
            Sirve para saber qué pedir al proveedor.""")
    public ReporteStock productosBajoStock() {
        log.info("Herramienta productosBajoStock");
        LocalDate hoy = LocalDate.now(reloj);
        List<AlertaStock> productos = jdbc.sql("""
                SELECT p.nombre, c.nombre, p.stock_actual, p.stock_minimo, COALESCE(s.unidades, 0)
                FROM producto p
                JOIN categoria c ON c.id = p.categoria_id
                LEFT JOIN (
                    SELECT d.producto_id, SUM(d.cantidad) AS unidades
                    FROM detalle_venta d JOIN venta v ON v.id = d.venta_id
                    WHERE v.fecha_hora >= ? AND v.fecha_hora < ?
                    GROUP BY d.producto_id
                ) s ON s.producto_id = p.id
                WHERE p.stock_actual < p.stock_minimo
                ORDER BY p.stock_actual, p.nombre""")
                .params(Timestamp.valueOf(hoy.minusDays(DIAS_PROMEDIO_VENTAS).atStartOfDay()),
                        Timestamp.valueOf(hoy.atStartOfDay()))
                .query((rs, fila) -> {
                    int stock = rs.getInt(3);
                    int minimo = rs.getInt(4);
                    double promedioDiario = redondear(rs.getLong(5) / (double) DIAS_PROMEDIO_VENTAS);
                    Double diasParaAgotarse = promedioDiario == 0 ? null : redondear(stock / promedioDiario);
                    return new AlertaStock(rs.getString(1), rs.getString(2), stock, minimo, minimo - stock,
                            stock == 0, promedioDiario, diasParaAgotarse);
                })
                .list();
        return new ReporteStock(productos.size(), productos.stream().filter(AlertaStock::agotado).count(),
                productos.stream().mapToInt(AlertaStock::faltanParaElMinimo).sum(), productos);
    }

    @Tool(description = """
            Lotes ya vencidos y lotes que vencen en los próximos días, con unidades, días restantes
            (negativo = ya vencido) y el costo de la mercancía en riesgo. Trae los totales ya sumados
            (unidades y costo en riesgo, aparte lo ya vencido) y el resumen por producto.""")
    public ReporteVencimientos lotesPorVencer(
            @ToolParam(required = false, description = "Cuántos días hacia adelante revisar, de 0 a 60 (por defecto 7)") Integer dias) {
        log.info("Herramienta lotesPorVencer({})", dias);
        LocalDate hoy = LocalDate.now(reloj);
        int horizonte = dias == null ? 7 : Math.clamp(dias, 0, 60);
        List<LoteVencimiento> lotes = jdbc.sql("""
                SELECT p.nombre, l.codigo, l.cantidad, l.fecha_vencimiento, l.cantidad * p.costo
                FROM lote l JOIN producto p ON p.id = l.producto_id
                WHERE l.cantidad > 0 AND l.fecha_vencimiento <= ?
                ORDER BY l.fecha_vencimiento, p.nombre""")
                .param(Date.valueOf(hoy.plusDays(horizonte)))
                .query((rs, fila) -> {
                    LocalDate vence = rs.getDate(4).toLocalDate();
                    long diasRestantes = ChronoUnit.DAYS.between(hoy, vence);
                    return new LoteVencimiento(rs.getString(1), rs.getString(2), rs.getInt(3), vence,
                            diasRestantes, diasRestantes < 0, rs.getLong(5));
                })
                .list();

        Map<String, ProductoEnRiesgo> porProducto = new LinkedHashMap<>();
        for (LoteVencimiento lote : lotes) {
            porProducto.merge(lote.producto(),
                    new ProductoEnRiesgo(lote.producto(), 1, lote.unidades(), lote.costoEnRiesgo(), lote.fechaVencimiento()),
                    (a, b) -> new ProductoEnRiesgo(a.producto(), a.lotes() + 1, a.unidades() + b.unidades(),
                            a.costoEnRiesgo() + b.costoEnRiesgo(), a.primerVencimiento()));
        }
        List<LoteVencimiento> vencidos = lotes.stream().filter(LoteVencimiento::vencido).toList();
        return new ReporteVencimientos(hoy, horizonte, lotes.size(),
                lotes.stream().mapToLong(LoteVencimiento::unidades).sum(),
                lotes.stream().mapToLong(LoteVencimiento::costoEnRiesgo).sum(),
                vencidos.size(),
                vencidos.stream().mapToLong(LoteVencimiento::costoEnRiesgo).sum(),
                porProducto.values().stream()
                        .sorted(Comparator.comparingLong(ProductoEnRiesgo::costoEnRiesgo).reversed())
                        .toList(),
                lotes);
    }

    // ---------- Apoyo ----------

    private List<InfoProducto> todosLosProductos() {
        return jdbc.sql("""
                SELECT p.nombre, c.nombre, p.precio, p.costo, p.stock_actual, p.stock_minimo, p.perecedero
                FROM producto p JOIN categoria c ON c.id = p.categoria_id
                ORDER BY c.nombre, p.nombre""")
                .query((rs, fila) -> {
                    long precio = rs.getLong(3);
                    long costo = rs.getLong(4);
                    return new InfoProducto(rs.getString(1), rs.getString(2), precio, costo,
                            porcentaje(precio - costo, precio), rs.getInt(5), rs.getInt(6), rs.getBoolean(7));
                })
                .list();
    }

    private boolean existeCategoria(String nombre) {
        return jdbc.sql("SELECT COUNT(*) FROM categoria WHERE nombre = ?").param(nombre).query(Long.class).single() > 0;
    }

    private static Rango rango(String desde, String hasta) {
        LocalDate inicio = fecha(desde, "desde");
        LocalDate fin = fecha(hasta, "hasta");
        if (fin.isBefore(inicio)) {
            throw new IllegalArgumentException("La fecha 'hasta' (" + fin + ") es anterior a 'desde' (" + inicio + ").");
        }
        return new Rango(inicio, fin);
    }

    private static LocalDate fecha(String texto, String nombre) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Falta la fecha '" + nombre + "' (formato AAAA-MM-DD).");
        }
        try {
            return LocalDate.parse(texto.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha '" + nombre + "' inválida: '" + texto + "'. Usa el formato AAAA-MM-DD.");
        }
    }

    private static String nombreDia(LocalDate fecha) {
        return fecha.getDayOfWeek().getDisplayName(TextStyle.FULL, ESPANOL);
    }

    private static LocalDate aFecha(Timestamp momento) {
        return momento == null ? null : momento.toLocalDateTime().toLocalDate();
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .strip();
    }

    /** Porcentaje con un decimal; 0 si el total es 0. */
    private static double porcentaje(long parte, long total) {
        return total == 0 ? 0 : redondear(100.0 * parte / total);
    }

    private static double redondear(double valor) {
        return Math.round(valor * 10) / 10.0;
    }

    /** Rango de días [desde, hasta] convertido a [inicio, fin) para comparar con fecha_hora. */
    private record Rango(LocalDate desde, LocalDate hasta) {
        Timestamp inicio() { return Timestamp.valueOf(desde.atStartOfDay()); }
        Timestamp fin() { return Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()); }
    }

    // ---------- Resultados que recibe el modelo ----------

    public enum CriterioRanking { UNIDADES, INGRESOS, GANANCIA }

    public enum OrdenRanking { MAS_VENDIDOS, MENOS_VENDIDOS }

    public record FechaActual(LocalDate hoy, String diaSemana, String hora,
            LocalDate primerDiaConVentas, LocalDate ultimoDiaConVentas) {
    }

    public record CategoriaInfo(String categoria, int productos) {
    }

    public record ResumenVentas(LocalDate desde, LocalDate hasta, long numeroVentas, long totalVendido,
            long ticketPromedio, long gananciaEstimada, double margenPorcentaje, List<VentasMedioPago> porMedioPago) {
    }

    public record VentasMedioPago(String medioPago, long ventas, long total, double porcentajeDelTotal) {
    }

    public record ReporteVentasDiarias(LocalDate desde, LocalDate hasta, long numeroVentas, long totalVendido,
            int diasConVentas, long promedioDiarioDiasCompletos, VentasDia mejorDia, VentasDia peorDia,
            List<VentasDia> dias) {
    }

    public record VentasDia(LocalDate fecha, String diaSemana, long ventas, long total, boolean enCurso) {
    }

    public record ReporteVentasPorHora(LocalDate desde, LocalDate hasta, long totalVendido,
            VentasHora horaPico, VentasHora horaMasFloja, List<VentasHora> horas) {
    }

    public record VentasHora(int hora, long ventas, long total, double porcentajeDelTotal) {
    }

    public record ReporteCategorias(LocalDate desde, LocalDate hasta, long totalVendido, long gananciaEstimada,
            List<VentasCategoria> categorias) {
    }

    public record VentasCategoria(String categoria, long unidades, long totalVendido, long gananciaEstimada,
            double porcentajeDelTotal) {
    }

    public record RankingProductos(LocalDate desde, LocalDate hasta, CriterioRanking criterio, OrdenRanking orden,
            String categoria, long totalVendidoPeriodo, List<VentasProducto> productos) {
    }

    public record VentasProducto(String producto, String categoria, long unidades, long totalVendido,
            long gananciaEstimada, double porcentajeDelTotal) {
    }

    public record InfoProducto(String nombre, String categoria, long precio, long costo, double margenPorcentaje,
            int stockActual, int stockMinimo, boolean perecedero) {
    }

    public record ReporteStock(int productosEnAlerta, long productosAgotados, int unidadesFaltantesParaElMinimo,
            List<AlertaStock> productos) {
    }

    public record AlertaStock(String producto, String categoria, int stockActual, int stockMinimo,
            int faltanParaElMinimo, boolean agotado, double ventaDiariaPromedio, Double diasParaAgotarse) {
    }

    public record ReporteVencimientos(LocalDate hoy, int diasRevisados, int totalLotes, long totalUnidades,
            long costoTotalEnRiesgo, int lotesVencidos, long costoYaVencido, List<ProductoEnRiesgo> porProducto,
            List<LoteVencimiento> lotes) {
    }

    public record ProductoEnRiesgo(String producto, int lotes, long unidades, long costoEnRiesgo,
            LocalDate primerVencimiento) {
    }

    public record LoteVencimiento(String producto, String codigoLote, int unidades, LocalDate fechaVencimiento,
            long diasRestantes, boolean vencido, long costoEnRiesgo) {
    }
}
