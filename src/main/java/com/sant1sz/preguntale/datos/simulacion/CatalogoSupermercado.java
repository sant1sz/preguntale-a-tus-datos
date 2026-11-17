package com.sant1sz.preguntale.datos.simulacion;

import static com.sant1sz.preguntale.datos.simulacion.ProductoBase.fresco;
import static com.sant1sz.preguntale.datos.simulacion.ProductoBase.seco;

import java.util.List;

/** Catálogo de un supermercado de barrio en Colombia. Precios en pesos (COP). */
public final class CatalogoSupermercado {

    private CatalogoSupermercado() {
    }

    public static List<ProductoBase> productos() {
        return List.of(
                // Lácteos y huevos
                fresco("Leche entera 1 L", "Lácteos y huevos", 4_600, 10, 12).venceEn(-1),
                fresco("Huevos AA x 30", "Lácteos y huevos", 21_900, 6, 25).conStock(EstadoStock.BAJO),
                fresco("Queso campesino 500 g", "Lácteos y huevos", 13_500, 3, 20),
                fresco("Yogur de fresa 1 L", "Lácteos y huevos", 8_900, 3, 25).venceEn(2),
                fresco("Mantequilla 250 g", "Lácteos y huevos", 9_800, 2, 60),

                // Panadería
                fresco("Pan tajado 500 g", "Panadería", 6_500, 7, 7).venceEn(1),
                fresco("Arepas de maíz x 5", "Panadería", 4_200, 8, 10),
                fresco("Pan de queso x 6", "Panadería", 5_500, 4, 4).conTendencia(Tendencia.BAJA),

                // Despensa
                seco("Arroz blanco 1 kg", "Despensa", 4_300, 9),
                seco("Fríjol rojo 500 g", "Despensa", 6_800, 3),
                seco("Lenteja 500 g", "Despensa", 3_900, 3),
                seco("Aceite vegetal 1 L", "Despensa", 11_500, 4).conStock(EstadoStock.BAJO),
                seco("Azúcar 1 kg", "Despensa", 4_800, 5),
                seco("Sal 500 g", "Despensa", 1_900, 2),
                seco("Pasta spaghetti 500 g", "Despensa", 3_600, 4),
                seco("Café molido 500 g", "Despensa", 18_900, 4),
                seco("Panela x 2", "Despensa", 4_500, 4),
                seco("Chocolate de mesa 500 g", "Despensa", 12_800, 2).conStock(EstadoStock.AGOTADO),

                // Frutas y verduras (se venden por kilo o por unidad)
                fresco("Banano (kg)", "Frutas y verduras", 3_200, 6, 7),
                fresco("Tomate (kg)", "Frutas y verduras", 4_500, 5, 8).venceEn(0),
                fresco("Cebolla cabezona (kg)", "Frutas y verduras", 3_800, 5, 20),
                fresco("Papa pastusa (kg)", "Frutas y verduras", 3_000, 7, 25),
                fresco("Aguacate (unidad)", "Frutas y verduras", 3_500, 4, 6).conTendencia(Tendencia.SUBE),
                fresco("Limón (kg)", "Frutas y verduras", 5_200, 3, 15),

                // Carnes
                fresco("Pechuga de pollo (kg)", "Carnes", 17_900, 4, 5).venceEn(3),
                fresco("Carne molida 500 g", "Carnes", 13_900, 3, 4),
                fresco("Salchichas x 10", "Carnes", 9_500, 3, 30),

                // Bebidas
                seco("Gaseosa cola 1,5 L", "Bebidas", 5_900, 6),
                seco("Agua 600 ml", "Bebidas", 1_800, 5),
                fresco("Jugo de naranja 1 L", "Bebidas", 6_200, 3, 30),
                seco("Cerveza lata 330 ml", "Bebidas", 3_200, 5),
                seco("Bebida energizante 250 ml", "Bebidas", 4_500, 2).conTendencia(Tendencia.SUBE),

                // Aseo y hogar
                seco("Detergente en polvo 1 kg", "Aseo y hogar", 12_500, 3),
                seco("Jabón lavaplatos 500 g", "Aseo y hogar", 5_400, 3),
                seco("Papel higiénico x 4", "Aseo y hogar", 9_900, 4).conStock(EstadoStock.BAJO),
                seco("Crema dental 100 ml", "Aseo y hogar", 7_800, 2),
                seco("Champú 400 ml", "Aseo y hogar", 15_900, 1.5),

                // Mecato
                seco("Papas fritas 115 g", "Mecato", 5_300, 4),
                seco("Galletas de chocolate", "Mecato", 4_100, 3),
                seco("Chocolatina", "Mecato", 2_500, 3));
    }
}
