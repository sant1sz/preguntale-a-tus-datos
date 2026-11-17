-- Mismas tablas que crea JPA en MySQL, para las pruebas con H2.
CREATE TABLE categoria (
    id BIGINT PRIMARY KEY,
    nombre VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE producto (
    id BIGINT PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL,
    categoria_id BIGINT NOT NULL REFERENCES categoria (id),
    precio BIGINT NOT NULL,
    costo BIGINT NOT NULL,
    stock_actual INT NOT NULL,
    stock_minimo INT NOT NULL,
    perecedero BOOLEAN NOT NULL
);

CREATE TABLE venta (
    id BIGINT PRIMARY KEY,
    fecha_hora TIMESTAMP NOT NULL,
    total BIGINT NOT NULL,
    medio_pago VARCHAR(20) NOT NULL
);

CREATE TABLE detalle_venta (
    id BIGINT PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES venta (id),
    producto_id BIGINT NOT NULL REFERENCES producto (id),
    cantidad INT NOT NULL,
    precio_unitario BIGINT NOT NULL,
    subtotal BIGINT NOT NULL
);

CREATE TABLE lote (
    id BIGINT PRIMARY KEY,
    producto_id BIGINT NOT NULL REFERENCES producto (id),
    codigo VARCHAR(20) NOT NULL,
    cantidad INT NOT NULL,
    fecha_vencimiento DATE NOT NULL
);
