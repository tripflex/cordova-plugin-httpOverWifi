#!/usr/bin/env node

'use strict';

const Promise = require('bluebird');

const childProcess = require('child_process');
const path = require('path');
const tmp = Promise.promisifyAll(require('tmp'));

function runProcess(name, args, cwd) {
    return new Promise((resolve, reject) => {
        const p =
            childProcess.spawn(name, args, { cwd, stdio: 'inherit' });
        p.once('exit', exitCode => {
            if (exitCode !== 0) {
                reject(new Error(`Got exit code ${exitCode}`));
                return;
            }
            resolve();
        });
    });
}

var tempProjectPath;
tmp.dirAsync({ unsafeCleanup: false/*true*/ })
    .then(tmpPath => {
        tempProjectPath = path.join(tmpPath, 'testApp');
        console.log(`Creating temporary project in: ${tempProjectPath}`);
        return runProcess('cordova', ['create', 'testApp'], tmpPath);
    })
    .then(() => {
        return runProcess(
            'cordova',
            ['platform', 'add', '--save', 'android'],
            tempProjectPath
        );
    })
    .then(() => {
        return runProcess(
            'cordova',
            ['plugin', 'add', '--save', path.join(__dirname, '..')],
            tempProjectPath
        );
    })
    .then(() => {
        return runProcess('cordova', ['build', 'android'], tempProjectPath);
    });
