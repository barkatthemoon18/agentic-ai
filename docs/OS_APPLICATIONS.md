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
  "removeAliases": [
    "alias automático no deseado"
  ],
  "processNames": {
    "AppID.de.una.aplicacion": ["Application.exe"]
  }
}
```

- La clave de `aliases` es la frase que puede decir el usuario y el valor es el AppID mostrado por `Get-StartApps`.
- Un alias manual reemplaza cualquier resolución automática del mismo nombre.
- `removeAliases` desactiva aliases automáticos sin ocultar la aplicación del catálogo.
- `processNames` sólo es necesario cuando Windows no permite inferir una ruta o paquete. Debe usarse con nombres de
  ejecutable específicos, porque habilita las operaciones de estado, cierre y focus.

Los cambios requieren reiniciar Ares o provocar un refresh mediante una búsqueda de aplicación que no pueda resolverse.
Las aplicaciones sin identidad de proceso todavía pueden abrirse y aparecer en listados, pero Ares rechazará close o
focus antes que asociarlas mediante una heurística insegura.
