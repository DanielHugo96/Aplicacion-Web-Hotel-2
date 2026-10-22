/**
 * Exportación PDF/Excel generada en el frontend sobre las filas autorizadas
 * visibles (`reporting/api.md` § Alcance: «El frontend puede exportar los
 * resultados autorizados a PDF/Excel con librerías existentes; no añade
 * endpoints de archivo»).
 *
 * No se añade ninguna dependencia: el PDF se compone con objetos PDF 1.4 y las
 * fuentes base Helvetica/Helvetica-Bold (sin incrustar), y el Excel es
 * SpreadsheetML 2003 (`.xls`), que Excel y LibreOffice abren. Ambos llevan
 * encabezados legibles en escala de grises (`DESIGN-admin-web.md`, componentes:
 * las tablas exportadas no dependen del color) y exactamente las columnas y
 * filas que la tabla muestra.
 */

export interface ColumnaReporte {
  clave: string;
  titulo: string;
  /** Importes y cifras: alineadas a la derecha con cifras tabulares. */
  numerica?: boolean;
  ancho?: number;
}

export type FilaReporte = Record<string, string | number | null | undefined>;

export interface OpcionesExportacion {
  titulo: string;
  subtitulo?: string;
  columnas: ColumnaReporte[];
  filas: FilaReporte[];
}

const ANCHO_PAGINA = 595;
const ALTO_PAGINA = 842;
const MARGEN = 40;
const ALTO_FILA = 14;
const TAM_FUENTE = 8;
const TAM_TITULO = 14;

function normalizar(valor: string | number | null | undefined): string {
  if (valor === null || valor === undefined) return '';
  return String(valor);
}

/** Matriz de texto con encabezado, en el mismo orden que `columnas`. */
export function matrizReporte(opciones: OpcionesExportacion): string[][] {
  const filas = opciones.filas.map((fila) =>
    opciones.columnas.map((columna) => normalizar(fila[columna.clave]))
  );
  return [opciones.columnas.map((c) => c.titulo), ...filas];
}

function escaparXml(valor: string): string {
  return valor
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * Excel: SpreadsheetML 2003. Una hoja con la fila de encabezado en negrita,
 * juntas hairline y celdas numéricas a la derecha.
 */
export function generarExcel(opciones: OpcionesExportacion): Blob {
  const xml: string[] = [];
  xml.push('<?xml version="1.0" encoding="UTF-8"?>');
  xml.push('<?mso-application progid="Excel.Sheet"?>');
  xml.push(
    '<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" ' +
      'xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">'
  );
  xml.push('<Styles>');
  xml.push(
    '<Style ss:ID="titulo"><Font ss:Bold="1" ss:Size="14"/>' +
      '<Alignment ss:Vertical="Bottom"/></Style>'
  );
  xml.push(
    '<Style ss:ID="encabezado"><Font ss:Bold="1" ss:Size="10"/>' +
      '<Interior ss:Color="#EFEDE6" ss:Pattern="Solid"/>' +
      '<Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ' +
      'ss:Color="#DCD8CC"/></Borders></Style>'
  );
  xml.push(
    '<Style ss:ID="texto"><Font ss:Size="10"/>' +
      '<Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ' +
      'ss:Color="#DCD8CC"/></Borders></Style>'
  );
  xml.push(
    '<Style ss:ID="numero"><Font ss:Size="10"/>' +
      '<NumberFormat ss:Format="#,##0.00"/>' +
      '<Alignment ss:Horizontal="Right"/>' +
      '<Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1" ' +
      'ss:Color="#DCD8CC"/></Borders></Style>'
  );
  xml.push('</Styles>');
  xml.push('<Worksheet ss:Name="Reporte"><Table>');
  for (const columna of opciones.columnas) {
    xml.push(`<Column ss:Width="${(columna.ancho ?? 120) * 0.75}"/>`);
  }
  xml.push(
    `<Row><Cell ss:StyleID="titulo"><Data ss:Type="String">${escaparXml(
      opciones.titulo
    )}</Data></Cell></Row>`
  );
  if (opciones.subtitulo) {
    xml.push(
      `<Row><Cell><Data ss:Type="String">${escaparXml(opciones.subtitulo)}</Data></Cell></Row>`
    );
  }
  xml.push('<Row></Row>');
  const [cabecera, ...filas] = matrizReporte(opciones);
  xml.push(
    '<Row>' +
      cabecera
        .map(
          (texto) =>
            `<Cell ss:StyleID="encabezado"><Data ss:Type="String">${escaparXml(
              texto
            )}</Data></Cell>`
        )
        .join('') +
      '</Row>'
  );
  for (let i = 0; i < filas.length; i += 1) {
    const fila = filas[i];
    const celdas = fila.map((texto, j) => {
      const columna = opciones.columnas[j];
      const crudo = opciones.filas[i][columna.clave];
      if (columna.numerica && typeof crudo === 'number' && Number.isFinite(crudo)) {
        return `<Cell ss:StyleID="numero"><Data ss:Type="Number">${crudo}</Data></Cell>`;
      }
      return `<Cell ss:StyleID="texto"><Data ss:Type="String">${escaparXml(
        texto
      )}</Data></Cell>`;
    });
    xml.push(`<Row>${celdas.join('')}</Row>`);
  }
  xml.push('</Table></Worksheet></Workbook>');
  return new Blob(['\ufeff' + xml.join('')], { type: 'application/vnd.ms-excel' });
}

// --------------------------------------------------------------------- PDF

function escaparPdf(texto: string): string {
  return texto.replace(/[\\()\u0080-\u00ff]/g, (caracter) => {
    if (caracter === '\\') return '\\\\';
    if (caracter === '(') return '\\(';
    if (caracter === ')') return '\\)';
    // Fuera de ASCII: escape octal de WinAnsi (p. ej. «á» → \341).
    return '\\' + caracter.charCodeAt(0).toString(8).padStart(3, '0');
  });
}

/** Trunca al ancho de columna con una aproximación de ancho de glifo Helvetica (0.5 em). */
function recortar(texto: string, ancho: number, tam: number): string {
  const maximo = Math.max(1, Math.floor(ancho / (tam * 0.5)));
  if (texto.length <= maximo) return texto;
  if (maximo <= 3) return texto.slice(0, maximo);
  return texto.slice(0, maximo - 3) + '...';
}

/**
 * Anchos de columna y tamaño de fuente de la tabla.
 *
 * El ancho de cada columna se calcula con el texto más largo que va a llevar
 * (título y celdas), no solo con el `ancho` declarado: si no, un nombre de
 * producto largo se recortaba con puntos suspensivos y la exportación dejaba de
 * contener las mismas filas que la tabla. Cuando el contenido no cabe a 8 pt,
 * se reduce la fuente (hasta 4 pt) antes que recortar.
 */
function medidasTabla(opciones: OpcionesExportacion): { anchos: number[]; tam: number } {
  const disponible = ANCHO_PAGINA - MARGEN * 2;
  const columnas = opciones.columnas;
  const matriz = matrizReporte(opciones);
  const masLargo = columnas.map((columna, i) =>
    matriz.reduce((maximo, fila) => Math.max(maximo, (fila[i] ?? '').length), columna.titulo.length)
  );
  // Ancho de glifo Helvetica ≈ 0,5 em; 8 pt de aire por celda.
  const sumaCaracteres = masLargo.reduce((a, b) => a + b, 0) || 1;
  const tamCalculado = (disponible - 8 * columnas.length) / (0.5 * sumaCaracteres);
  const tam = Math.min(TAM_FUENTE, Math.max(4, tamCalculado));
  const necesarios = masLargo.map((largo, i) =>
    Math.max(columnas[i].ancho ?? 120, largo * tam * 0.5 + 8)
  );
  const total = necesarios.reduce((a, b) => a + b, 0) || 1;
  return { anchos: necesarios.map((n) => (n / total) * disponible), tam };
}


function filasPorPagina(): number {
  return Math.floor((ALTO_PAGINA - MARGEN * 2 - 60) / ALTO_FILA);
}

function contenidoPagina(
  opciones: OpcionesExportacion,
  filas: string[][],
  pagina: number,
  totalPaginas: number
): string {
  const { anchos, tam } = medidasTabla(opciones);
  const lineas: string[] = [];
  let y = ALTO_PAGINA - MARGEN;
  lineas.push('0.15 g');
  if (pagina === 0) {
    lineas.push(`BT /F2 ${TAM_TITULO} Tf ${MARGEN} ${y} Td (${escaparPdf(opciones.titulo)}) Tj ET`);
    y -= 18;
    if (opciones.subtitulo) {
      lineas.push(`BT /F1 9 Tf ${MARGEN} ${y} Td (${escaparPdf(opciones.subtitulo)}) Tj ET`);
      y -= 14;
    }
    y -= 6;
  } else {
    lineas.push(
      `BT /F2 10 Tf ${MARGEN} ${y} Td (${escaparPdf(opciones.titulo)}) Tj ET`
    );
    y -= 18;
  }
  // Encabezado en negrita sobre junta gris clara.
  lineas.push('0.94 g');
  const anchoTotal = anchos.reduce((a, b) => a + b, 0);
  lineas.push(`${MARGEN} ${y - 3} ${anchoTotal} ${ALTO_FILA} re f`);
  lineas.push('0.15 g');
  let x = MARGEN;
  lineas.push(`BT /F2 ${tam} Tf`);
  opciones.columnas.forEach((columna, i) => {
    const texto = recortar(columna.titulo, anchos[i] - 6, tam);
    const posicion = columna.numerica ? x + anchos[i] - 3 : x + 3;
    lineas.push(`1 0 0 1 ${posicion} ${y} Tm (${escaparPdf(texto)}) Tj`);
    x += anchos[i];
  });
  lineas.push('ET');
  y -= ALTO_FILA;
  // Juntas hairline grises y filas.
  lineas.push('0.85 G 0.5 w');
  for (const fila of filas) {
    lineas.push(`${MARGEN} ${y - 3} m ${MARGEN + anchoTotal} ${y - 3} l S`);
    x = MARGEN;
    lineas.push('BT /F1 ' + tam + ' Tf');
    opciones.columnas.forEach((columna, i) => {
      const texto = recortar(fila[i] ?? '', anchos[i] - 6, tam);
      const posicion = columna.numerica ? x + anchos[i] - 3 : x + 3;
      lineas.push(`1 0 0 1 ${posicion} ${y} Tm (${escaparPdf(texto)}) Tj`);
      x += anchos[i];
    });
    lineas.push('ET');
    y -= ALTO_FILA;
  }
  lineas.push('0.85 G');
  lineas.push(`${MARGEN} ${y - 3} m ${MARGEN + anchoTotal} ${y - 3} l S`);
  // El pie también pasa por el escape WinAnsi: «á» debe ir como `\341` y los
  // paréntesis como `\(` `\)`, o el lector rompe la cadena del operador `Tj`.
  lineas.push(
    `BT /F1 8 Tf ${MARGEN} ${MARGEN - 10} Td (${escaparPdf(
      `Página ${pagina + 1} de ${totalPaginas}`
    )}) Tj ET`
  );
  return lineas.join('\n');
}

function aBytes(texto: string): Uint8Array<ArrayBuffer> {
  const bytes = new Uint8Array(new ArrayBuffer(texto.length));
  for (let i = 0; i < texto.length; i += 1) bytes[i] = texto.charCodeAt(i) & 0xff;
  return bytes;
}

/**
 * PDF 1.4 de una sola columna de páginas: encabezado, filas y número de
 * página. Fuentes base Helvetica (sin incrustar) y juntas hairline grises.
 */
export function generarPdf(opciones: OpcionesExportacion): Blob {
  const matriz = matrizReporte(opciones);
  const cuerpo = matriz.slice(1);
  const porPagina = filasPorPagina();
  const paginas: string[][][] = [];
  if (cuerpo.length === 0) {
    paginas.push([]);
  } else {
    for (let i = 0; i < cuerpo.length; i += porPagina) {
      paginas.push(cuerpo.slice(i, i + porPagina));
    }
  }
  const totalPaginas = paginas.length;
  const contenidos = paginas.map((filas, i) =>
    contenidoPagina(opciones, filas, i, totalPaginas)
  );

  const objetos: string[] = [];
  const idsPagina: number[] = [];
  const idCatalogo = 1;
  const idPages = 2;
  const idFuenteNormal = 3;
  const idFuenteNegrita = 4;
  let siguiente = 5;
  for (let i = 0; i < contenidos.length; i += 1) {
    idsPagina.push(siguiente);
    siguiente += 2; // página + contenido
  }
  objetos[idCatalogo] = `<< /Type /Catalog /Pages ${idPages} 0 R >>`;
  objetos[idPages] =
    `<< /Type /Pages /Kids [${idsPagina.map((id) => `${id} 0 R`).join(' ')}] ` +
    `/Count ${idsPagina.length} >>`;
  objetos[idFuenteNormal] = '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>';
  objetos[idFuenteNegrita] = '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>';
  contenidos.forEach((contenido, i) => {
    const idPagina = idsPagina[i];
    const idContenido = idPagina + 1;
    objetos[idPagina] =
      `<< /Type /Page /Parent ${idPages} 0 R /MediaBox [0 0 ${ANCHO_PAGINA} ${ALTO_PAGINA}] ` +
      `/Resources << /Font << /F1 ${idFuenteNormal} 0 R /F2 ${idFuenteNegrita} 0 R >> >> ` +
      `/Contents ${idContenido} 0 R >>`;
    objetos[idContenido] = `<< /Length ${aBytes(contenido).length} >>\nstream\n${contenido}\nendstream`;
  });

  const partes: string[] = ['%PDF-1.4\n'];
  const offsets: number[] = [];
  let posicion = aBytes(partes[0]).length;
  for (let id = 1; id < objetos.length; id += 1) {
    const cuerpoObjeto = `${id} 0 obj\n${objetos[id]}\nendobj\n`;
    offsets[id] = posicion;
    partes.push(cuerpoObjeto);
    posicion += aBytes(cuerpoObjeto).length;
  }
  const inicioXref = posicion;
  const totalObjetos = objetos.length;
  let xref = `xref\n0 ${totalObjetos}\n0000000000 65535 f \n`;
  for (let id = 1; id < totalObjetos; id += 1) {
    xref += `${String(offsets[id]).padStart(10, '0')} 00000 n \n`;
  }
  partes.push(xref);
  partes.push(
    `trailer\n<< /Size ${totalObjetos} /Root ${idCatalogo} 0 R >>\nstartxref\n${inicioXref}\n%%EOF\n`
  );
  return new Blob([aBytes(partes.join(''))], { type: 'application/pdf' });
}

/** Descarga el blob con el nombre indicado, sin tocar el backend. */
export function descargar(blob: Blob, nombre: string): void {
  if (typeof document === 'undefined' || typeof URL === 'undefined') return;
  const url = URL.createObjectURL(blob);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  enlace.rel = 'noopener';
  document.body.appendChild(enlace);
  enlace.click();
  document.body.removeChild(enlace);
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
