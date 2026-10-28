package btools.routingapp;

/** AIDL interface of the independently installed BRouter app (MIT license). */
interface IBRouterService {
    String getTrackFromParams(in Bundle params);
}
