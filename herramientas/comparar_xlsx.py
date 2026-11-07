"""Abre dos .xlsx con openpyxl y compara hojas, celdas (tipo y valor), anchos y encabezado.
Uso: python -I herramientas/comparar_xlsx.py referencia.xlsx generado.xlsx"""
import sys
import openpyxl

ref = openpyxl.load_workbook(sys.argv[1])
gen = openpyxl.load_workbook(sys.argv[2])
errores = []
if ref.sheetnames != gen.sheetnames:
    errores.append(f"hojas: {ref.sheetnames} != {gen.sheetnames}")
celdas = 0
for nombre in ref.sheetnames:
    a, b = ref[nombre], gen[nombre]
    if (a.max_row, a.max_column) != (b.max_row, b.max_column):
        errores.append(f"{nombre}: tamaño {a.max_row}x{a.max_column} != {b.max_row}x{b.max_column}")
    for fila in a.iter_rows():
        for c in fila:
            d = b[c.coordinate]
            celdas += 1
            if type(c.value) is not type(d.value) and not (isinstance(c.value, (int, float)) and isinstance(d.value, (int, float))):
                errores.append(f"{nombre}!{c.coordinate}: tipo {type(c.value).__name__} != {type(d.value).__name__}")
            elif isinstance(c.value, (int, float)) and abs(c.value - d.value) > 1e-9:
                errores.append(f"{nombre}!{c.coordinate}: {c.value} != {d.value}")
            elif not isinstance(c.value, (int, float)) and c.value != d.value:
                errores.append(f"{nombre}!{c.coordinate}: {c.value!r} != {d.value!r}")
    if not b["A1"].font.b:
        errores.append(f"{nombre}: encabezado sin negrita")
    if b["A1"].fill.fgColor.rgb != "FFE2EFEE":
        errores.append(f"{nombre}: fondo del encabezado {b['A1'].fill.fgColor.rgb}")
print(("OK" if not errores else "ERRORES") + f": {len(ref.sheetnames)} hojas, {celdas} celdas")
for e in errores[:15]:
    print("  ", e)
sys.exit(1 if errores else 0)
