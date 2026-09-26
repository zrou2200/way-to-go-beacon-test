#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

// Exposes the Swift WayToGoBeacon class + methods to the React Native runtime.
@interface RCT_EXTERN_MODULE(WayToGoBeacon, RCTEventEmitter)

RCT_EXTERN_METHOD(startScan:(NSString *)uuid
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(stopScan)

@end
