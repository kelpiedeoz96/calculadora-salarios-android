#!/usr/bin/env fish
# Compara, en orden, todas las cifras (montos, horas, fechas, porcentajes) del texto de dos PDF.
# Uso: herramientas/comparar_cifras_pdf.fish referencia.pdf generado.pdf
function cifras
    pdftotext -layout $argv[1] - | string match -ar -- '-?\$ ?[0-9][0-9.]*|[0-9]+:[0-9]{2}|[0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}/[0-9]{2}|[0-9]+(?:[.,][0-9]+)?%' | string replace -a ' ' ''
end
set -l a (cifras $argv[1])
set -l b (cifras $argv[2])
if test "$a" = "$b"
    echo "IGUALES: "(count $a)" cifras"
    exit 0
end
echo "DIFERENTES: referencia "(count $a)" cifras, generado "(count $b)
diff (printf '%s\n' $a | psub) (printf '%s\n' $b | psub) | head -20
exit 1
