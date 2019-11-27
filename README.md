# StagyBeeExtractor

## Actions

### Subscribe
/api/subscribe

#### Payload example
``` json
{
 "id":"abcdeg",
 "url":"127.0.0.1:50"
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

## Events
### Listeners
Broadcasts the listeners to all subscribers.
### Status
Broadcasts the status to all subscribers.
### Error
In case of an error this event is cast.