# Protocolo de Conexión: Túnel A2A (E.M.M.A. Mobile -> Servidor Hermes)

Este documento contiene la especificación final implementada en la App Móvil de E.M.M.A. Es la contraparte necesaria para programar la recepción del lado del Servidor/Matriz.

## 1. El Puente Principal (WebSocket)
La aplicación móvil intentará conectarse mediante un WebSocket persistente, con `PingInterval` de 30 segundos usando `OkHttp`.

**Endpoint Esperado en el Servidor:**
`ws://<IP-DEL-SERVIDOR>:<PUERTO>/mobile/ws`

**Ejemplo si es en Python (FastAPI):**
```python
@app.websocket("/mobile/ws")
async def websocket_endpoint(websocket: WebSocket):
    # La validación de credenciales vendrá en los headers HTTP de conexión inicial
    await websocket.accept()
    # Esperando paquetes móviles...
```

### Cabeceras de Autenticación Mínimas que manda la App:
- `Authorization`: Bearer `<Token capturado en la Forja / UI>`
- `X-Device-ID`: `<ID encriptado del celular Android>`

---

## 2. Tipos de Paquetes (Formatos JSON Bidireccionales)

### A. La App manda un ping manual
```json
{
  "type": "ping"
}
```
*Es buena práctica que tu servidor responda con un payload `pong` para medir latencia, aunque internamente OkHttp maneja sus tramas secretas de salud.*

### B. Notificación de Estado / Sensores
Cuando el túnel abre (e intermitentemente después), el celular emitirá un update de la batería o bioma local:
```json
{
  "type": "status",
  "state": {
    "battery": 87,
    "network": "Wi-Fi local"
  }
}
```

### C. La App Despacha una Tarea (El Swarm A2A Handoff)
Cuando un Agente en el celular esté forjado bajo el motor "Hermes A2A", ¡la app congelará el teléfono y le tirará la carga total al servidor mediante este formato!
```json
{
  "type": "a2a",
  "task": {
    "id": "task_17000001234",
    "type": "text_generation",
    "content": "[
       {\"role\": \"system\", \"content\": \"Tu ADN y directrices\"}, 
       {\"role\": \"user\", \"content\": \"El prompt final encapsulado enviado por Emma\"}
    ]"
  }
}
```

---

## 3. ¿Cómo le hablo de regreso a E.M.M.A? (The Return)

Cuando termine tu procesamiento pesado o quieras mandar una interrupción proactiva, Mándale este JSON por el mismo canal Websocket:

### Responder una Tarea (Return to Sender):
```json
{
  "type": "message",
  "event": "task_complete",
  "task_id": "task_17000001234",
  "result": "Aquí tienes el mega reporte analizado desde el rack central..."
}
```
*Este es el comando clave. Cuando el celular reciba esto con `task_complete`, resolverá la promesa asincrónica (`CompletableDeferred`) que bloqueaba el Swarm y el LLM pintará este resultado en la pantalla como si lo hubiese pensado localmente.*

### Lanzar Notificación Push Inteligente (Desde el Servidor):
```json
{
  "type": "message",
  "event": "notification",
  "text": "Llamada detectada, abriendo cámaras..."
}
```
*(Ideal para lanzar comandos Proactivos tipo Jarvis sin que el usuario te haya pedido nada).*

---
> **Nota para el Creador de la Skill (Hermes):**
> Ya no es necesario que implementes SSH, el protocolo de Handshake Websocket maneja mucho mejor las desconexiones a nivel celular (cambio de red 4G a WiFi). El dispositivo móvil re-intentará conectar cada 5 segundos si el socket crashea.
