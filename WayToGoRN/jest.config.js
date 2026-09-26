/**
 * Jest config for the pure-TypeScript positioning core (Section 10.1).
 * Uses ts-jest with the core-only tsconfig so tests run on Node without the
 * React Native toolchain — mirroring the original `./gradlew test` (JVM) setup.
 */
module.exports = {
  testEnvironment: 'node',
  roots: ['<rootDir>/src'],
  testMatch: ['**/?(*.)+(test).ts'],
  transform: {
    '^.+\\.ts$': ['ts-jest', { tsconfig: 'tsconfig.core.json' }],
  },
};
