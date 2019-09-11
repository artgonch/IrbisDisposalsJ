package org.lplib.artem.irbisdisposalsj;

/**
 * Created by artem on 14.02.2018.
 */
public interface LoadBarcodeListThreadCallBack {
    void start();
    void stop();
    void addElement(String barcode, String status);
    boolean error(Exception exception);
} // LoadBarcodeListThreadCallBack
