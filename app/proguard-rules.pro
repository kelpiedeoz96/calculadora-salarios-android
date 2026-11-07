# Reglas de R8 para el release.
#
# No hacen falta reglas propias: kotlinx.serialization, Compose, AndroidX y las corrutinas traen sus
# reglas de consumidor dentro de sus librerías, y la app no usa reflexión. Las fuentes Lato y su licencia
# son recursos Java (fuentes/*.ttf, fuentes/OFL.txt) y R8 no los elimina.
#
# La prueba de humo de R8 (./gradlew :engine:humoR8Comparar, la corre "./build.fish release") aplica
# estas mismas reglas al motor y comprueba que el PDF, el Excel y data.json salen idénticos.
