# Catálogo de aplicaciones de Windows

Ares obtiene las aplicaciones lanzables con `Get-StartApps` y completa su identidad de proceso con los accesos directos
del menú Inicio y los paquetes AppX. El texto producido por el modelo sólo contiene una acción y un nombre; el AppID,
el comando de apertura y la identidad de proceso siempre proceden de este catálogo.

## Alias y procesos adicionales

`config/os-applications.json` se vuelve a leer al iniciar Ares y cada vez que se refresca el catálogo:

```json
{
  "aliases": {
    "code": "Microsoft.VisualStudioCode",
    "vs code": "Microsoft.VisualStudioCode"
  },
  "transcriptionAliases": {
    "estudio": "studio",
    "topas": "topaz"
  },
  "removeAliases": [
    "alias automático no deseado"
  ],
  "processNames": {
    "AppID.de.una.aplicacion": ["Application.exe"]
  },
  "trustedProcessNamesWhenPathUnavailable": {},
  "commandLineArgumentSets": {
    "AppID.de.una.webapp": [["--app-id=identificador-exacto"]]
  },
  "exactCommandLineArgumentSets": {
    "AppID.del.host": [[], ["-os-autostart"]]
  },
  "hostRelationships": {
    "AppID.de.una.webapp": "AppID.del.host"
  },
  "windowSignatures": {
    "AppID.del.host": [{
      "className": "MozillaWindowClass",
      "titlePattern": "(?i).*— Mozilla Firefox$"
    }]
  },
  "windowAssociationsEnabled": {
    "AppID.del.host": true
  }
}
```

- La clave de `aliases` es la frase que puede decir el usuario y el valor es el AppID mostrado por `Get-StartApps`.
- Un alias manual reemplaza cualquier resolución automática del mismo nombre.
- `transcriptionAliases` corrige términos que STT suele transcribir de otra forma. Se aplica únicamente a nombres y
  filtros de aplicaciones, por tokens completos y antes de resolver; no modifica la transcripción general. Sus claves
  se validan después de normalizar acentos y casing, por lo que variantes que colisionen rechazan el refresh completo.
- `removeAliases` desactiva aliases automáticos sin ocultar la aplicación del catálogo.
- `processNames` sólo localiza procesos candidatos; nunca basta para estado, cierre o focus.
- `commandLineArgumentSets` usa semántica `CONTAINS_ALL`: todos los argumentos configurados deben aparecer como tokens
  completos y la combinación debe identificar una sola aplicación del catálogo en runtime.
- `exactCommandLineArgumentSets` usa semántica `EXACT`: compara el vector tokenizado, en orden y sin incluir el
  ejecutable. `[]` coincide exclusivamente con una command line sin argumentos y nunca funciona como comodín.
- `hostRelationships` relaciona una aplicación hospedada con su host. El catálogo puede inferir una relación cuando hay
  un único candidato base y firmas hospedadas exclusivas, pero esa relación nunca crea identidad fuerte para el host.
- `windowSignatures` describe HWND mediante clase y/o un patrón de título. Sólo se usa si
  `windowAssociationsEnabled` está activado explícitamente para ese AppID.
- `trustedProcessNamesWhenPathUnavailable` es una excepción explícita para aplicaciones cuyo contrato permite confiar
  en el nombre cuando Windows no expone la ruta. Si Windows sí expone una ruta, ésta siempre se valida.

La resolución usa, en orden, el nombre canónico exacto, un alias exacto, una secuencia contigua de varios tokens y un
token completo en cualquier posición. Conserva equivalencias de separación como `Prime Video`/`Primevideo`, pero nunca
usa substrings: `studio` coincide con `Android Studio`, no con `GraphStudioNext`. Si quedan varios candidatos, conserva
el resultado ambiguo y solicita una selección; nunca elige el primero implícitamente.

Antes de ordenar, el catálogo deduplica por una clave estable. Un AppID se compara sin distinguir casing. Las entradas
sin AppID usan un fingerprint SHA-256 construido mediante una codificación canónica de todos sus atributos observables.
Si dos entradas comparten AppID y su definición de identidad runtime difiere, el refresh se rechaza atómicamente y se
conserva el último catálogo válido; diferencias sólo presentacionales se fusionan de forma determinista. Las opciones
se ordenan por nombre normalizado, nombre original y clave estable, de modo que los ordinales no dependan del orden de
`Get-StartApps`.

Una respuesta de voz ambigua, desconocida o ajena a las opciones visibles deja la interacción pendiente para otro
intento. Sólo una resolución única completa la selección. La aplicación elegida aporta identidad de catálogo; después
de seleccionar, las operaciones sobre procesos obtienen una observación runtime nueva y conservan todas las
revalidaciones anteriores a la acción.

Los cambios requieren reiniciar Ares o provocar un refresh mediante una búsqueda de aplicación que no pueda resolverse.
Las aplicaciones sin identidad runtime inequívoca todavía pueden abrirse y aparecer en listados, pero Ares rechazará
estado, close o focus antes que asociarlas mediante una heurística insegura.

La identidad runtime procede de `Win32_Process`: PID, `ParentProcessId`, `CreationDate`, ruta ejecutable y command line
se capturan juntos.
La exclusividad de command line se comprueba de nuevo contra todo el catálogo para cada proceso observado. Antes de
tomar foco, publicar cada lote de `WM_CLOSE` o ejecutar el fallback de terminación, Ares obtiene otro snapshot WMI y
revalida PID, `CreationDate` e identidad. `ProcessHandle` sólo se utiliza para comprobar vida y solicitar terminación.

`ParentProcessId` y las firmas de ventana son evidencia auxiliar. Una firma de ventana sólo puede asociar un HWND a
una aplicación dentro de un proceso host que ya tenga identidad fuerte. Nunca sustituye la firma del proceso ni
autoriza terminarlo. En un host compartido, focus y close operan exclusivamente sobre HWND revalidados; el fallback
`ProcessHandle.destroy()` queda deshabilitado aunque Windows conserve vivo el proceso host.

La consulta «qué aplicaciones están abiertas» obtiene un único snapshot WMI para todo el catálogo y enumera una sola
vez las ventanas visibles. Sólo publica aplicaciones `RUNNING_WITH_WINDOW`. Los candidatos no verificables se excluyen
de la lista y se informan mediante un contador, sin tratarlos como abiertos o cerrados.

## Evidencia Firefox y Prime Video

En la captura disponible de Firefox normal, el proceso raíz observado fue `firefox.exe -os-autostart`; sus hijos usan
`-contentproc`, y la ventana visible pertenece a la clase `MozillaWindowClass` con un título terminado en
`— Mozilla Firefox`. La configuración incluye también `EXACT []` para el inicio normal sin argumentos.

El acceso directo de Prime Video usa `-taskbar-tab 1cc3ede1-7346-471f-afe5-001115314c29`, `-new-window` y la URL de
Prime Video. Antes de habilitar una firma HWND para Prime deben repetirse las capturas con Firefox + Prime y con Prime
sin una ventana normal. Si el host reutiliza completamente proceso y ventanas sin una señal positiva estable, Prime
permanece `UNVERIFIABLE` para status, focus y close.

Una consulta WMI exitosa sin el PID solicitado produce `NOT_FOUND`. Timeout, acceso denegado, error COM o una respuesta
incompleta producen `OBSERVATION_FAILED`; estos fallos nunca se interpretan como ausencia ni como `NOT_RUNNING`.

El clasificador de comandos también conserva un contrato estricto de una sola línea. Si la primera salida no cumple el
contrato, se reintenta una vez con una corrección de formato. Un segundo fallo se contiene dentro de OS Skills y no
ejecuta ninguna acción.
