// www/advanced.js
var exec = require('cordova/exec');
exports.scanAndGrabFrame = function (opts, success, error) {
  exec(success, error, 'OSBarcodeAdvanced', 'scanAndGrabFrame', [opts || {}]);
};
