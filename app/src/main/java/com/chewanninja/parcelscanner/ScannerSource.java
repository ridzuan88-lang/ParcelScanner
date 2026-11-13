package com.chewanninja.parcelscanner;

/** Any way of receiving scans (keyboard wedge, broadcast intent, vendor SDK...). */
public interface ScannerSource {
    void start();
    void stop();
}
