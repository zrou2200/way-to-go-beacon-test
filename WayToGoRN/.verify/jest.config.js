/** Isolated Jest config: runs the pure-TS core tests against ../src. */
const path = require('path');

module.exports = {
  rootDir: __dirname,
  roots: [path.resolve(__dirname, '..', 'src')],
  testEnvironment: 'node',
  testMatch: ['**/?(*.)+(test).ts'],
  moduleFileExtensions: ['ts', 'js', 'json', 'node'],
  transform: {
    '^.+\\.ts$': ['ts-jest', {tsconfig: path.resolve(__dirname, 'tsconfig.json')}],
  },
};

