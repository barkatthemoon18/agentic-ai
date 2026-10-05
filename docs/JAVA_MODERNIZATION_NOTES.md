# Modernización Java y trabajo manual pendiente

La migración elimina Lombok de producción y tests y convierte 26 objetos de datos a records.
Los servicios mantienen sus responsabilidades; OutputPresentationPolicy es una clase final explícita.
Los componentes usan accessors de record, sin getters puente. Se conservan constructores alternativos,
factories, campos opcionales, formatos JSON y validaciones.

AudioFrame, SpeechSegment, TtsAudio y PiperResponse transfieren la referencia de su float[] sin copiarla.
El propietario y los consumidores deben tratar el buffer como solo lectura. El aislamiento en una frontera
externa requiere una copia explícita allí. La igualdad de arrays en estos records sigue siendo por referencia.

## Ajustes menores reservados para revisión manual

- ToolsWorkspaceConfigLoader: añadir nombres de parámetros a los mensajes de requireNonNull.
- AudioPipeline.publishPlaybackSignal y AssistantOutputCoordinator.present: declarar variables cerca
  de su primera asignación en lugar de anticiparlas al inicio del método.
- SpeechProcessingService y clases existentes de vistas: revisar imports comodín y agrupación de helpers
  sin alterar la máquina de estados ni reordenar efectos de ejecución.
- WindowsAudioOutputProvider: uniformar mensajes de diagnóstico y espacios en expresiones JNA.
- WakeWordMatcher: uniformar espaciado en bucles y firmas. La migración solo cambia sus accesos a datos.

Para código nuevo: constantes/campos, constructores, factories, API pública y helpers privados;
imports explícitos y validación en el punto de construcción. No se incorpora un formateador global.
