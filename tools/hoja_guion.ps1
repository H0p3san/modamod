# Junta en una hoja las fotos de un guion de debug (ver GuionDebug.java): una fila por variante, una columna por angulo.
# Uso: powershell -File tools\hoja_guion.ps1 <grupo> <variante1,variante2,...> [salida.png]
#   ej: tools\hoja_guion.ps1 cola redondo,cuadrado  ->  lee run\screenshots\guion_cola_redondo_frente.png, etc.
param([string]$grupo, [string]$variantes, [string]$salida = "")
Add-Type -AssemblyName System.Drawing
$dir = Join-Path $PSScriptRoot "..\run\screenshots"
if ($salida -eq "") { $salida = Join-Path $PSScriptRoot "..\run\modamod_debug\hoja_$grupo.png" }
$angulos = 'frente', 'atras', 'costado', 'abajo', 'arriba'
$filas = $variantes.Split(',')
$lado = 400
$hoja = New-Object Drawing.Bitmap ($lado * $angulos.Count), ($lado * $filas.Count)
$g = [Drawing.Graphics]::FromImage($hoja)
$g.Clear([Drawing.Color]::Black)
$fuente = New-Object Drawing.Font 'Arial', 12
for ($r = 0; $r -lt $filas.Count; $r++) {
    for ($c = 0; $c -lt $angulos.Count; $c++) {
        $f = Join-Path $dir ("guion_{0}_{1}_{2}.png" -f $grupo, $filas[$r], $angulos[$c])
        if (-not (Test-Path $f)) { continue }
        $i = [Drawing.Image]::FromFile($f)
        # El jugador queda en el centro: se recorta un cuadrado del medio.
        $m = [int]($i.Height * 0.8)
        $src = New-Object Drawing.Rectangle ([int](($i.Width - $m) / 2)), ([int](($i.Height - $m) / 2)), $m, $m
        $g.DrawImage($i, (New-Object Drawing.Rectangle ($c * $lado), ($r * $lado), $lado, $lado), $src, 'Pixel')
        $g.DrawString("$($filas[$r]) $($angulos[$c])", $fuente, [Drawing.Brushes]::Yellow, $c * $lado + 4, $r * $lado + 4)
        $i.Dispose()
    }
}
$hoja.Save($salida)
Write-Output $salida
