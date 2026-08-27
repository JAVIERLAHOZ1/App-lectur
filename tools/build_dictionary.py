#!/usr/bin/env python3
"""
Construye el diccionario offline de Lectur a partir del volcado del Wikcionario
en espanol (es.wiktionary), que se publica con licencia CC BY-SA.

Genera un SQLite con una fila por acepcion agrupada:

    entradas(clave, palabra, categoria, acepciones)

  clave       palabra en minusculas y sin tildes, que es por donde se busca
  palabra     la palabra tal cual aparece en el diccionario
  categoria   sustantivo, verbo, adjetivo...
  acepciones  las definiciones, separadas por saltos de linea

Uso:
    python3 tools/build_dictionary.py --salida diccionario.db
    python3 tools/build_dictionary.py --autotest      # prueba el parseo, sin red
"""

from __future__ import annotations

import argparse
import bz2
import os
import re
import sqlite3
import sys
import time
import unicodedata
import urllib.request
from xml.etree import ElementTree

DUMP_URL = (
    "https://dumps.wikimedia.org/eswiktionary/latest/"
    "eswiktionary-latest-pages-articles.xml.bz2"
)

# Wikimedia exige un User-Agent que diga quien eres; con el de Python da 403.
USER_AGENT = (
    "LecturDictionaryBuilder/1.0 "
    "(https://github.com/JAVIERLAHOZ1/App-lectur) Python-urllib"
)

MAX_DEFINICIONES = 3
MAX_LARGO_DEFINICION = 320

# Secciones que no son definiciones y hay que saltarse.
SECCIONES_IGNORADAS = {
    "etimologia",
    "etimologia 1",
    "etimologia 2",
    "pronunciacion",
    "pronunciacion y escritura",
    "traducciones",
    "traduccion",
    "vease tambien",
    "referencias",
    "referencias y notas",
    "conjugacion",
    "informacion adicional",
    "derivados",
    "sinonimos",
    "antonimos",
    "hiperonimos",
    "hiponimos",
    "locuciones",
    "refranes",
    "expresiones",
    "notas",
    "anagramas",
}

SECCION_ESPANOL = re.compile(
    r"==\s*\{\{\s*lengua\s*\|\s*es\s*\}\}\s*==(.*?)(?=\n==\s*[^=]|\Z)",
    re.S | re.I,
)
ENCABEZADO = re.compile(r"^(={3,})\s*(.+?)\s*\1\s*$", re.M)
LINEA_DEFINICION = re.compile(r"^;\s*\d+[a-z]?\s*[:.]\s*(.+?)\s*$", re.M)

# Plantillas de "esto es una forma de otra palabra", que son definicion en si mismas.
PLANTILLAS_FORMA = {
    "f.v": "Forma verbal de {0}.",
    "forma verbo": "Forma verbal de {0}.",
    "forma verbal": "Forma verbal de {0}.",
    "f.adj": "Forma del adjetivo {0}.",
    "f.adj2": "Forma del adjetivo {0}.",
    "f.s": "Forma de {0}.",
    "f.s.p": "Plural de {0}.",
    "f.p": "Plural de {0}.",
    "plural": "Plural de {0}.",
    "gerundio": "Gerundio de {0}.",
    "participio": "Participio de {0}.",
    "diminutivo": "Diminutivo de {0}.",
    "aumentativo": "Aumentativo de {0}.",
    "superlativo": "Superlativo de {0}.",
    "despectivo": "Despectivo de {0}.",
    "sustantivo de verbo": "Accion o efecto de {0}.",
    "sustantivo de adjetivo": "Cualidad de {0}.",
}

# Plantillas que solo aportan una marca entre parentesis.
PLANTILLAS_MARCA = {
    "ambito": True,
    "uso": True,
    "csem": True,
}

MARCAS_SIMPLES = {
    "coloquial": "coloquial",
    "vulgar": "vulgar",
    "figurado": "figurado",
    "anticuado": "anticuado",
    "desusado": "desusado",
    "poetico": "poetico",
    "malsonante": "malsonante",
    "jergal": "jergal",
}


def sin_tildes(texto: str) -> str:
    """Quita tildes y dieresis, para poder buscar 'corrio' y encontrar 'corrio'."""
    descompuesto = unicodedata.normalize("NFD", texto)
    return "".join(c for c in descompuesto if unicodedata.category(c) != "Mn")


def clave_de(palabra: str) -> str:
    return sin_tildes(palabra.strip().lower())


def normaliza_seccion(titulo: str) -> str:
    limpio = re.sub(r"\{\{([^}|]*)[^}]*\}\}", r"\1", titulo)
    limpio = re.sub(r"[^\w\s]", " ", limpio, flags=re.UNICODE)
    limpio = re.sub(r"\s+", " ", limpio).strip().lower()
    return sin_tildes(limpio)


def separa_argumentos(cuerpo: str) -> tuple[str, list[str], dict[str, str]]:
    """Parte el interior de una plantilla en nombre, argumentos y parametros."""
    partes = cuerpo.split("|")
    nombre = sin_tildes(partes[0].strip().lower())
    posicionales: list[str] = []
    nombrados: dict[str, str] = {}
    for parte in partes[1:]:
        if "=" in parte:
            clave, _, valor = parte.partition("=")
            nombrados[clave.strip()] = valor.strip()
        else:
            posicionales.append(parte.strip())
    return nombre, posicionales, nombrados


def render_plantilla(cuerpo: str) -> str:
    """Convierte una plantilla de wikitexto en el texto que ve el lector."""
    nombre, posicionales, nombrados = separa_argumentos(cuerpo)

    if nombre in PLANTILLAS_FORMA and posicionales:
        return PLANTILLAS_FORMA[nombre].format(posicionales[0])

    if nombre in MARCAS_SIMPLES:
        return f"({MARCAS_SIMPLES[nombre]})"

    if nombre in PLANTILLAS_MARCA:
        valores = [v for v in posicionales if v and not v.startswith("leng")]
        return f"({', '.join(valores)})" if valores else ""

    # Plantillas que solo enlazan o resaltan una palabra.
    if nombre in {"plm", "l", "enlace", "w"} and posicionales:
        return posicionales[-1] if nombre != "l" else posicionales[-1]

    if nombre in {"variante", "variantes"} and posicionales:
        return f"Variante de {posicionales[0]}."

    if nombre in {"sinonimo", "sinonimos"} and posicionales:
        return f"Sinonimo de {posicionales[0]}."

    # Cualquier otra plantilla (formato, conjugaciones, marcas raras) se descarta.
    return ""


def quita_plantillas(texto: str) -> str:
    """Sustituye {{...}} respetando plantillas anidadas."""
    resultado = []
    i = 0
    largo = len(texto)
    while i < largo:
        if texto.startswith("{{", i):
            profundidad = 0
            j = i
            while j < largo:
                if texto.startswith("{{", j):
                    profundidad += 1
                    j += 2
                elif texto.startswith("}}", j):
                    profundidad -= 1
                    j += 2
                    if profundidad == 0:
                        break
                else:
                    j += 1
            if profundidad != 0:
                # Plantilla sin cerrar: se descarta el resto.
                break
            interior = texto[i + 2 : j - 2]
            # Las plantillas anidadas de dentro no nos interesan.
            interior_plano = re.sub(r"\{\{.*?\}\}", "", interior, flags=re.S)
            resultado.append(render_plantilla(interior_plano))
            i = j
        else:
            resultado.append(texto[i])
            i += 1
    return "".join(resultado)


def limpia_wikitexto(texto: str) -> str:
    texto = re.sub(r"<!--.*?-->", "", texto, flags=re.S)
    texto = re.sub(r"<ref[^>]*>.*?</ref>", "", texto, flags=re.S | re.I)
    texto = re.sub(r"<ref[^>]*/>", "", texto, flags=re.I)
    texto = quita_plantillas(texto)
    texto = re.sub(r"\[\[[^\]|]*\|([^\]]*)\]\]", r"\1", texto)
    texto = re.sub(r"\[\[([^\]]*)\]\]", r"\1", texto)
    texto = re.sub(r"<[^>]+>", "", texto)
    texto = texto.replace("'''", "").replace("''", "")
    texto = re.sub(r"\s+", " ", texto)
    texto = re.sub(r"\(\s*\)", "", texto)
    texto = re.sub(r"\s+([,.;:])", r"\1", texto)
    return texto.strip(" .,;:").strip()


def parsea_entrada(titulo: str, wikitexto: str) -> list[tuple[str, list[str]]]:
    """Saca [(categoria, [definiciones])] de la seccion en espanol de una pagina."""
    seccion = SECCION_ESPANOL.search(wikitexto)
    if not seccion:
        return []

    cuerpo = seccion.group(1)
    encabezados = list(ENCABEZADO.finditer(cuerpo))
    if not encabezados:
        return []

    bloques: list[tuple[str, list[str]]] = []
    for indice, encabezado in enumerate(encabezados):
        titulo_seccion = encabezado.group(2)
        nombre = normaliza_seccion(titulo_seccion)
        if not nombre or nombre in SECCIONES_IGNORADAS:
            continue

        inicio = encabezado.end()
        fin = encabezados[indice + 1].start() if indice + 1 < len(encabezados) else len(cuerpo)
        contenido = cuerpo[inicio:fin]

        definiciones = []
        for cruda in LINEA_DEFINICION.findall(contenido):
            limpia = limpia_wikitexto(cruda)
            if len(limpia) < 2:
                continue
            if len(limpia) > MAX_LARGO_DEFINICION:
                limpia = limpia[:MAX_LARGO_DEFINICION].rstrip() + "..."
            definiciones.append(limpia)
            if len(definiciones) >= MAX_DEFINICIONES:
                break

        if definiciones:
            categoria = re.sub(r"\s*\|.*$", "", nombre).strip()
            bloques.append((categoria, definiciones))

    return bloques


def crea_base(ruta: str) -> sqlite3.Connection:
    if os.path.exists(ruta):
        os.remove(ruta)
    conexion = sqlite3.connect(ruta)
    conexion.executescript(
        """
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        CREATE TABLE entradas (
            clave      TEXT NOT NULL,
            palabra    TEXT NOT NULL,
            categoria  TEXT NOT NULL,
            acepciones TEXT NOT NULL
        );
        """
    )
    return conexion


def paginas(origen) -> "iter":
    """Recorre el XML del volcado devolviendo (titulo, texto) de los articulos."""
    contexto = ElementTree.iterparse(origen, events=("end",))
    for _, elemento in contexto:
        etiqueta = elemento.tag.rsplit("}", 1)[-1]
        if etiqueta != "page":
            continue
        namespace = elemento.findtext("./{*}ns")
        titulo = elemento.findtext("./{*}title") or ""
        texto = elemento.findtext("./{*}revision/{*}text") or ""
        elemento.clear()
        if namespace != "0" or not texto or ":" in titulo:
            continue
        yield titulo, texto


def descarga(url: str, destino: str, intentos: int = 4) -> str:
    """
    Baja el volcado. Wikimedia rechaza los agentes genericos (da 403), asi que
    hay que identificarse como manda su politica de User-Agent.
    """
    if os.path.exists(destino) and os.path.getsize(destino) > 1_000_000:
        print(f"Volcado ya descargado: {destino}")
        return destino

    peticion = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept-Encoding": "identity",
        },
    )

    ultimo_error: Exception | None = None
    for intento in range(1, intentos + 1):
        print(f"Descargando {url} (intento {intento}/{intentos})", flush=True)
        inicio = time.time()
        try:
            with urllib.request.urlopen(peticion, timeout=120) as respuesta:
                with open(destino, "wb") as salida:
                    while True:
                        trozo = respuesta.read(1 << 20)
                        if not trozo:
                            break
                        salida.write(trozo)
            megas = os.path.getsize(destino) / (1024 * 1024)
            print(f"Descargado {megas:.0f} MB en {time.time() - inicio:.0f} s")
            return destino
        except Exception as error:  # noqa: BLE001 - se reintenta con cualquier fallo
            ultimo_error = error
            print(f"  fallo: {error}", flush=True)
            if os.path.exists(destino):
                os.remove(destino)
            if intento < intentos:
                espera = 5 * (2 ** (intento - 1))
                print(f"  reintentando en {espera} s", flush=True)
                time.sleep(espera)

    raise SystemExit(f"No se ha podido descargar el volcado: {ultimo_error}")


def construye(volcado: str, salida: str, limite: int = 0) -> None:
    conexion = crea_base(salida)
    lote: list[tuple[str, str, str, str]] = []
    leidas = 0
    guardadas = 0
    inicio = time.time()

    with bz2.open(volcado, "rb") as flujo:
        for titulo, texto in paginas(flujo):
            leidas += 1
            if leidas % 100_000 == 0:
                print(
                    f"  {leidas:>9} paginas leidas, {guardadas:>7} entradas "
                    f"({time.time() - inicio:.0f} s)",
                    flush=True,
                )

            bloques = parsea_entrada(titulo, texto)
            if not bloques:
                continue

            clave = clave_de(titulo)
            if not clave:
                continue

            for categoria, definiciones in bloques:
                lote.append((clave, titulo, categoria, "\n".join(definiciones)))
                guardadas += 1

            if len(lote) >= 5000:
                conexion.executemany("INSERT INTO entradas VALUES (?, ?, ?, ?)", lote)
                lote.clear()

            if limite and guardadas >= limite:
                break

    if lote:
        conexion.executemany("INSERT INTO entradas VALUES (?, ?, ?, ?)", lote)

    print("Creando indice...")
    conexion.execute("CREATE INDEX idx_clave ON entradas(clave)")
    conexion.commit()
    print("Compactando...")
    conexion.execute("VACUUM")
    conexion.commit()
    conexion.close()

    megas = os.path.getsize(salida) / (1024 * 1024)
    print(
        f"Listo: {guardadas} entradas de {leidas} paginas -> {salida} ({megas:.1f} MB)"
    )

    if not limite:
        verifica(salida)


PALABRAS_CONTROL = [
    "casa",
    "decir",
    "libro",
    "libros",
    "dijo",
    "agua",
    "tiempo",
    "correr",
    "hermoso",
    "melancolia",
]


def verifica(ruta: str) -> None:
    """Comprueba que el diccionario no ha salido vacio ni roto."""
    conexion = sqlite3.connect(ruta)
    total = conexion.execute("SELECT COUNT(*) FROM entradas").fetchone()[0]
    distintas = conexion.execute("SELECT COUNT(DISTINCT clave) FROM entradas").fetchone()[0]
    print(f"\nComprobacion: {total} acepciones, {distintas} palabras distintas")

    faltan = []
    for palabra in PALABRAS_CONTROL:
        fila = conexion.execute(
            "SELECT palabra, acepciones FROM entradas WHERE clave = ? LIMIT 1",
            (clave_de(palabra),),
        ).fetchone()
        if fila:
            primera = fila[1].splitlines()[0]
            print(f"  {palabra:>12} -> {primera[:88]}")
        else:
            faltan.append(palabra)
            print(f"  {palabra:>12} -> NO ESTA")
    conexion.close()

    if distintas < 50_000:
        raise SystemExit(
            f"El diccionario tiene solo {distintas} palabras: el parseo ha fallado."
        )
    if len(faltan) > 2:
        raise SystemExit(f"Faltan palabras basicas ({faltan}): el parseo ha fallado.")


MUESTRAS = [
    (
        "casa",
        """== {{lengua|es}} ==
=== Etimología ===
Del latin ''casa''.

=== {{sustantivo femenino|es}} ===
;1: Edificio de una o pocas plantas destinado a [[vivienda]].
;2: {{ámbito|España}} Conjunto de personas que viven juntas.
;3: {{coloquial}} Establecimiento comercial.

=== Traducciones ===
{{trad-arriba}}
""",
    ),
    (
        "dijo",
        """== {{lengua|es}} ==
=== Forma verbal ===
;1: {{f.v|decir|p=3|n=s|t=pret}}
""",
    ),
    (
        "libros",
        """== {{lengua|es}} ==
=== Forma sustantiva ===
;1: {{f.s.p|libro}}
""",
    ),
]


def autotest() -> int:
    fallos = 0
    for titulo, wikitexto in MUESTRAS:
        bloques = parsea_entrada(titulo, wikitexto)
        print(f"\n== {titulo} ==")
        if not bloques:
            print("  SIN RESULTADOS  <-- fallo")
            fallos += 1
            continue
        for categoria, definiciones in bloques:
            print(f"  [{categoria}]")
            for definicion in definiciones:
                print(f"    - {definicion}")

    # Comprobaciones minimas de que el parseo hace lo esperado.
    casa = parsea_entrada(*MUESTRAS[0])
    if not casa or "vivienda" not in casa[0][1][0]:
        print("\nfallo: la primera acepcion de 'casa' no se ha limpiado bien")
        fallos += 1
    if len(casa) != 1 or not casa[0][0].startswith("sustantivo"):
        print("\nfallo: categoria incorrecta para 'casa'")
        fallos += 1
    if any("Traducciones" in c for c, _ in casa):
        print("\nfallo: se ha colado la seccion de traducciones")
        fallos += 1

    dijo = parsea_entrada(*MUESTRAS[1])
    if not dijo or "decir" not in dijo[0][1][0]:
        print("\nfallo: no se resuelve la forma verbal de 'dijo'")
        fallos += 1

    libros = parsea_entrada(*MUESTRAS[2])
    if not libros or "libro" not in libros[0][1][0]:
        print("\nfallo: no se resuelve el plural de 'libros'")
        fallos += 1

    if clave_de("Corrió") != "corrio":
        print("\nfallo: las tildes no se estan quitando en la clave")
        fallos += 1

    print("\nAutotest: " + ("OK" if fallos == 0 else f"{fallos} fallos"))
    return fallos


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--salida", default="diccionario.db")
    parser.add_argument("--volcado", default="eswiktionary-latest.xml.bz2")
    parser.add_argument("--url", default=DUMP_URL)
    parser.add_argument("--limite", type=int, default=0, help="para pruebas rapidas")
    parser.add_argument("--autotest", action="store_true")
    argumentos = parser.parse_args()

    if argumentos.autotest:
        return autotest()

    volcado = descarga(argumentos.url, argumentos.volcado)
    construye(volcado, argumentos.salida, argumentos.limite)
    return 0


if __name__ == "__main__":
    sys.exit(main())
