module.exports.request = function(opts, callback) {
    if (!opts || typeof opts !== 'object') {
        throw new Error('First argument must be an object');
    }
    if (!(opts.method === 'GET' || opts.method === 'POST' || opts.method === 'PUT' || opts.method === 'DELETE' || opts.method === 'HEAD' || opts.method === 'OPTIONS' || opts.method === 'PATCH')) {
        throw new Error('Invalid method');
    }
    if (!opts.url || typeof opts.url !== 'string') {
        throw new Error('Missing url');
    }
    if (opts.headers && typeof opts.headers !== 'object') {
        throw new Error('Headers needs to be an object');
    }
    if (opts.data) {
        if (typeof opts.data === 'object') {
            opts.data = JSON.stringify(opts.data);
        }
        if (typeof opts.data !== 'string') {
            throw new Error('Data needs to be an object or string');
        }
    }
    if (callback && typeof callback !== 'function') {
        throw new Error('Second argument must be a function');
    }
    cordova.exec(
        function(res) {
            var isStringAndJson =
                res.data && res.headers && res.headers['Content-Type'] &&
                res.headers['Content-Type'].match(/application\/json/) &&
                typeof res.data === 'string';
            if (isStringAndJson) {
                res.data = JSON.parse(res.data);
            }
            callback(null, res);
        },
        function(err) {
            callback(err);
        },
        'HttpOverWifi',
        'request',
        [opts]
    );
};
