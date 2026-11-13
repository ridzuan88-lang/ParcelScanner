package com.chewanninja.parcelscanner;

public class ScanRecord {
    public long id;
    public String scanId;
    public String mode;
    public String tracking;
    /** PENDING, SYNCED, DUPLICATE, REJECTED */
    public String status;
    public String serverDate = "";
    public String serverDateTime = "";
    public String message = "";
    public int attempts;
    public long createdAt;
}
