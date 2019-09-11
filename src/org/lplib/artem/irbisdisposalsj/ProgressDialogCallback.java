package org.lplib.artem.irbisdisposalsj;

/**
 * Created by artem on 14.02.2018.
 */
public interface ProgressDialogCallback {
    void init(String text, int min, int max);
    void progress(int value, String text);
    void error(Exception ex);
    void close();
} // ProgressDialogCallback
