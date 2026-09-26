const {getDefaultConfig, mergeConfig} = require('@react-native/metro-config');

/**
 * Metro configuration. `csv` is added to assetExts so bundled scan logs can be
 * shipped; the sample walk is also embedded as a TS string for hardware-free replay.
 */
const config = {
  resolver: {
    assetExts: ['png', 'jpg', 'jpeg', 'csv'],
  },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
