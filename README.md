# StagyBeeExtractor

![Java CI](https://github.com/zigellsn/StagyBeeExtractor/workflows/Java%20CI/badge.svg)

## Actions

### Subscribe
/api/subscribe

#### Payload example
``` json
{
 "id":"abcdeg",
 "url":"https://127.0.0.1:50"
}
```

#### Response example
``` json
{
 "success": "true",
 "sessionId": "hijklm"
}
```

### Unsubscribe
/api/unsubscribe/:sessionId

### Status
/api/status/:sessionId

### Meta
/api/meta

Returns the content of meta.json

## Events
### Listeners
Broadcasts the listeners to all subscribers.
### Status
Broadcasts the status to all subscribers.
### Error
In case of an error the extractor casts this event.