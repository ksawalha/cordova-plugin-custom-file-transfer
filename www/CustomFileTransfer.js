const CustomFileTransfer = {
  uploadFiles: function(filePaths, options) {
    return new Promise((resolve, reject) => {
      cordova.exec(
        resolve,
        reject,
        "CustomFileTransfer",
        "uploadFiles",
        [filePaths, options]
      );
    });
  }
};
module.exports = CustomFileTransfer;
