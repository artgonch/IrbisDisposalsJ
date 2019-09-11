package org.lplib.artem.irbisdisposalsj;

import org.artem.irbis.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Поток смены статуса экз-ров по списку ШК.
 */
public class ChangeStatusThread extends Thread {
    /** */
    private static final String PROGRESS_MESSAGE = "Обработано %d из %d";

    /** */
    private List<String> barcodeList;

    /** */
    private IrbisClient64 client;

    /** */
    private ProgressDialogCallback callback;

    /** */
    private String invNumPref;

    /** */
    private String newStatus = "0";

    /**
     *
     * @param barcodeList
     * @param client
     * @param callback
     * @param newStatus
     * @param invNumPref
     */
    public ChangeStatusThread(List<String> barcodeList,
                              IrbisClient64 client,
                              ProgressDialogCallback callback,
                              String newStatus,
                              String invNumPref) {
        this.barcodeList = barcodeList;
        this.callback = callback;
        this.client = client;
        this.invNumPref = invNumPref;
        this.newStatus = newStatus;
    } // ChangeStatusThread

    @Override
    public void run() {
        int n = barcodeList.size();
        final String sCurrDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        callback.init(String.format(PROGRESS_MESSAGE, 0, n), 0, n);

        try {
            for (int i = 0; i < n; i++) {
                if (isInterrupted()) {
                    return;
                } // if

                String barcode = barcodeList.get(i);

                List<Integer> result = client.search(String.format("\"%s%s\"", invNumPref, barcode));

                if (result.isEmpty()) {
                    continue;
                }

                IrbisRecord64 record = client.readRecord(result.get(0), false);

                IrbisField64 currField = null;

                List<IrbisField64> f910list = record.getFieldSet("910");
                for (IrbisField64 f910 : f910list) {
                    IrbisSubField64 sfH = f910.getSubFieldList().get('H');
                    IrbisSubField64 sfB = f910.getSubFieldList().get('B');
                    if (sfH != null && sfH.getText().equalsIgnoreCase(barcode) || sfB != null && sfB.getText().equalsIgnoreCase(barcode)) {
                        currField = f910;
                        break;
                    } // if
                } // for (IrbisClient64 f910 : f910list)

                if (currField == null) {
                    continue;
                }

                currField.getSubFieldList().get('A').setText(newStatus);
                // Добавить (или изменить) подполе G - дата модификации поля.
                IrbisSubField64 sfG = currField.getSubFieldList().get('G');
                if (sfG == null) {
                    currField.getSubFieldList().add('G', sCurrDate);
                }
                else {
                    sfG.setText(sCurrDate);
                } // else
                client.writeRecord(record, false, true);

                callback.progress(i + 1, String.format(PROGRESS_MESSAGE, i + 1, n));
            } // for (int i = 0; i < n; i++)
        } // try
        catch (IrbisClient64Exception ex) {
            callback.error(ex);
        }
        finally {
            callback.close();
        }
    } // run
} // ChangeStatusThread
