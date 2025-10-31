
var exec = require('cordova/exec');

/**
 * Scan a QR/Barcode and automatically snapshot the same frame to JPEG.
 * @param {Object} opts - { jpegQuality?: number (default 80), facingBack?: boolean (default true) }
 * @param {(res: {scanText:string, format:string, imageContentBase64:string})=>void} success
 * @param {(err:any)=>void} error
 */
exports.scanAndGrabFrame = function (opts, success, error) {
  exec(success, error, 'OSBarcode', 'scanAndGrabFrame', [opts || {}]);
};
