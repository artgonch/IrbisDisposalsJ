package org.lplib.artem.irbisdisposalsj;

import org.artem.irbis.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Created by artem on 16.05.2018.
 * Поток, выполняющий списание экз-ров.
 */
public class DisposalProcessingThread extends Thread {
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
    private String mhrNew = "";

    /** */
    private String actNum = "";

    /**
     *
     * @param barcodeList
     * @param client
     * @param callback
     * @param invNumPref
     */
    public DisposalProcessingThread(List<String> barcodeList,
                               IrbisClient64 client,
                               ProgressDialogCallback callback,
                               String invNumPref,
                               String actNum) {
        this.barcodeList = barcodeList;
        this.callback = callback;
        this.client = client;
        this.invNumPref = invNumPref;
        this.actNum = actNum;
    } // VSPProcessingThread

    /**
     *
     */
    public void run() {
        final String sCurrDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        int n = barcodeList.size();

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
                    IrbisSubField64 sfA = f910.getSubFieldList().get('A');
                    if (sfH != null && sfH.getText().equalsIgnoreCase(barcode) || sfB != null && sfB.getText().equalsIgnoreCase(barcode)) {
                        // пропустить строки 910-го поля со статусом 6 (только 0, 4, 5, С)
                        if ("045C".contains(sfA.getText())) {
                            currField = f910;
                            break;
                        } // if
                    } // if
                } // for (IrbisClient64 f910 : f910list)

                if (currField == null) {
                    continue;
                }

                // Поменять статус экз-ра
                IrbisSubField64 sfA = currField.getSubFieldList().get('A');
                if (sfA == null) {
                    continue;
                }

                sfA.setText("6");

                // Добавить подполе номер акта списания
                IrbisSubField64 sfV = currField.getSubFieldList().get('V');
                if (sfV == null) {
                    currField.getSubFieldList().add('V', actNum);
                }
                else {
                    sfV.setText(actNum);
                }

                // Добавить (или изменить) подполе G - дата модификации поля.
                IrbisSubField64 sfG = currField.getSubFieldList().get('G');
                if (sfG == null) {
                    currField.getSubFieldList().add('G', sCurrDate);
                }
                else {
                    sfG.setText(sCurrDate);
                } // else

                // Сохранение записи
                client.writeRecord(record, false, true);
                callback.progress(i + 1, String.format(PROGRESS_MESSAGE, i + 1, n));
            } // for (int i = 0; i < n; i++)
        } // try
        catch (IrbisClient64Exception ex) {
            callback.error(ex);
        } // catch
        finally {
            callback.close();
        } // finally
    } // run
} // class DisposalProcessingThread
