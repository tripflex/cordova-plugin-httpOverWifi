# cordova-plugin-httpOverWifi

[![Circle CI](https://circleci.com/gh/CanTireInnovations/cordova-plugin-httpOverWifi.svg?style=svg)](https://circleci.com/gh/CanTireInnovations/cordova-plugin-httpOverWifi)

This is a Cordova plugin for making Angular-like HTTP requests over WiFi.

## Why is this a thing?

Android's Marshmallow operating system introduced a non-forward compatible
change that causes network traffic to sometimes use cellular data instead of
WiFi when connected to a WiFi access point that cannot reach Google's servers.
This is problematic for applications that need to talk to local devices on
that access point (i.e., anything in the 10.0.0.0/8 or 192.168.0.0/16 ranges).
This plugin makes HTTP requests that are guaranteed to use WiFi.

## Why not use `bindProcessToNetwork`?

As of Cordova 5.3.3, Cordova plugins do not support the runtime permission
model required when targeting API level 23 which introduced
`bindProcessToNetwork`.

## API

```JavaScript
httpOverWifi.request({
    method: 'GET', // or POST, PUT, DELETE, PATCH, OPTIONS
    url: 'http://someurl.com/',
    headers: { // optional property
        'X-Foo': 'bar'
    },
    data: { // optional, can also be a string, objects are serialized to JSON
        foo: 'bar'
    }
}, function callback(err, response) {
    // err is null if no error occurred
    // response has status, data, and headers
});
```

## License

MIT
