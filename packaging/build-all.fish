#!/usr/bin/env fish
set -l aqui (path resolve (dirname (status filename)))
fish $aqui/build-linux.fish $argv; or exit 1
fish $aqui/build-windows.fish $argv; or exit 1
